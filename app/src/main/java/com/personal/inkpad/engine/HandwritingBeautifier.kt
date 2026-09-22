package com.personal.inkpad.engine

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.personal.inkpad.domain.model.StrokeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Freenotes-style real-time handwriting beautification:
 * ink strokes → recognized text rendered in a script font.
 */
class HandwritingBeautifier(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var recognizer: DigitalInkRecognizer? = null
    @Volatile private var modelReady = false

    data class Result(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val fontSize: Float
    )

    suspend fun ensureModel(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (modelReady && recognizer != null) return@withLock true
            val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en")
                ?: DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
                ?: return@withLock false
            val model = DigitalInkRecognitionModel.builder(id).build()
            val downloaded = suspendCancellableCoroutine { cont ->
                RemoteModelManager.getInstance()
                    .download(model, DownloadConditions.Builder().build())
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { cont.resume(false) }
            }
            if (!downloaded) return@withLock false
            recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build()
            )
            modelReady = true
            true
        }
    }

    suspend fun recognize(strokes: List<StrokeData>): Result? = withContext(Dispatchers.Default) {
        if (strokes.isEmpty()) return@withContext null
        if (!ensureModel()) return@withContext null
        val client = recognizer ?: return@withContext null

        val inkBuilder = Ink.builder()
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val sb = Ink.Stroke.builder()
            val t0 = stroke.points.first().timestamp
            stroke.points.forEach { p ->
                val t = (p.timestamp - t0).coerceAtLeast(0L)
                sb.addPoint(Ink.Point.create(p.x, p.y, t))
            }
            inkBuilder.addStroke(sb.build())
        }
        val ink = inkBuilder.build()

        val candidates = suspendCancellableCoroutine { cont ->
            client.recognize(ink)
                .addOnSuccessListener { cont.resume(it.candidates) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
        val best = candidates.firstOrNull()?.text?.trim().orEmpty()
        if (best.isBlank() || best.length > 48) return@withContext null
        // Reject pure punctuation / garbage
        if (best.all { !it.isLetterOrDigit() }) return@withContext null

        val allPts = strokes.flatMap { it.points }
        val minX = allPts.minOf { it.x }
        val maxX = allPts.maxOf { it.x }
        val minY = allPts.minOf { it.y }
        val maxY = allPts.maxOf { it.y }
        val w = (maxX - minX).coerceAtLeast(24f)
        val h = (maxY - minY).coerceAtLeast(18f)
        val fontSize = max(h * 0.92f, 22f).coerceIn(18f, 96f)

        Result(
            text = best,
            x = minX,
            y = minY,
            width = max(w, fontSize * best.length * 0.45f),
            height = max(h, fontSize * 1.25f),
            fontSize = fontSize
        )
    }
}
