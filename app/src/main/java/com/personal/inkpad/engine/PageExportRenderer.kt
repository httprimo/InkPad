package com.personal.inkpad.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.personal.inkpad.R
import com.personal.inkpad.data.db.ImageObjectEntity
import com.personal.inkpad.data.db.ShapeObjectEntity
import com.personal.inkpad.data.db.TextObjectEntity
import com.personal.inkpad.domain.model.PaperTemplate
import com.personal.inkpad.domain.model.ShapeType
import com.personal.inkpad.domain.model.StrokeData

/**
 * Renders a full page (paper + ink + shapes + images + text) onto an Android [Canvas]
 * for PNG/PDF export. Mirrors what the editor shows, minus selection UI.
 */
object PageExportRenderer {

    fun draw(
        context: Context,
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        backgroundColor: Long,
        templateName: String,
        strokes: List<StrokeData>,
        shapes: List<ShapeObjectEntity>,
        texts: List<TextObjectEntity>,
        images: List<ImageObjectEntity>,
        underlay: ((Canvas) -> Unit)? = null
    ) {
        canvas.drawColor(backgroundColor.toInt())
        drawPaper(canvas, pageWidth, pageHeight, templateName)
        underlay?.invoke(canvas)

        images.forEach { img ->
            val bmp = BitmapFactory.decodeFile(img.path) ?: return@forEach
            val dest = RectF(img.x, img.y, img.x + img.width, img.y + img.height)
            canvas.drawBitmap(bmp, null, dest, null)
            bmp.recycle()
        }

        shapes.forEach { drawShape(canvas, it) }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            strokePaint.color = withOpacity(stroke.color.toInt(), stroke.opacity)
            strokePaint.strokeWidth = stroke.width.coerceAtLeast(1f)
            val path = Path()
            path.moveTo(stroke.points[0].x, stroke.points[0].y)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x, stroke.points[i].y)
            }
            canvas.drawPath(path, strokePaint)
        }

        val scriptFace = ResourcesCompat.getFont(context, R.font.caveat_medium)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        texts.forEach { t ->
            textPaint.color = t.color.toInt()
            textPaint.textSize = t.fontSize
            textPaint.typeface = when {
                t.scriptStyle && scriptFace != null -> scriptFace
                t.bold -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
            canvas.drawText(t.text, t.x, t.y + t.fontSize, textPaint)
        }
    }

    private fun drawPaper(canvas: Canvas, pageWidth: Float, pageHeight: Float, templateName: String) {
        val template = runCatching { PaperTemplate.valueOf(templateName) }.getOrDefault(PaperTemplate.RULED)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xA6B8C0C8.toInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x73E07A5F.toInt()
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        val fillDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xA6B8C0C8.toInt()
            style = Paint.Style.FILL
        }

        when (template) {
            PaperTemplate.RULED, PaperTemplate.PLANNER, PaperTemplate.CHECKLIST -> {
                var y = 48f
                while (y < pageHeight) {
                    canvas.drawLine(24f, y, pageWidth - 24f, y, linePaint)
                    y += 28f
                }
                canvas.drawLine(72f, 24f, 72f, pageHeight - 24f, marginPaint)
            }
            PaperTemplate.GRID -> {
                var x = 24f
                while (x < pageWidth) {
                    canvas.drawLine(x, 24f, x, pageHeight - 24f, linePaint)
                    x += 28f
                }
                var y = 24f
                while (y < pageHeight) {
                    canvas.drawLine(24f, y, pageWidth - 24f, y, linePaint)
                    y += 28f
                }
            }
            PaperTemplate.DOTTED -> {
                var y = 32f
                while (y < pageHeight) {
                    var x = 32f
                    while (x < pageWidth) {
                        canvas.drawCircle(x, y, 1.6f, fillDot)
                        x += 28f
                    }
                    y += 28f
                }
            }
            PaperTemplate.CORNELL -> {
                canvas.drawLine(pageWidth * 0.28f, 40f, pageWidth * 0.28f, pageHeight * 0.78f, linePaint)
                canvas.drawLine(24f, pageHeight * 0.78f, pageWidth - 24f, pageHeight * 0.78f, linePaint)
                var y = 64f
                val soft = Paint(linePaint).apply { alpha = 90 }
                while (y < pageHeight * 0.78f) {
                    canvas.drawLine(pageWidth * 0.3f, y, pageWidth - 24f, y, soft)
                    y += 28f
                }
            }
            PaperTemplate.MUSIC -> {
                var y = 80f
                while (y < pageHeight - 80f) {
                    repeat(5) { i ->
                        canvas.drawLine(40f, y + i * 12f, pageWidth - 40f, y + i * 12f, linePaint)
                    }
                    y += 90f
                }
            }
            PaperTemplate.BLANK, PaperTemplate.CUSTOM -> Unit
        }
    }

    private fun drawShape(canvas: Canvas, shape: ShapeObjectEntity) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = shape.fillColor.toInt()
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = shape.strokeColor.toInt()
            strokeWidth = shape.strokeWidth.coerceAtLeast(1f)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val left = shape.x
        val top = shape.y
        val right = shape.x + shape.width
        val bottom = shape.y + shape.height
        when (shape.shapeType) {
            ShapeType.CIRCLE.name, ShapeType.ELLIPSE.name -> {
                val oval = RectF(left, top, right, bottom)
                canvas.drawOval(oval, fill)
                canvas.drawOval(oval, stroke)
            }
            ShapeType.LINE.name, ShapeType.ARROW.name -> {
                val midY = shape.y + shape.height / 2f
                canvas.drawLine(left, midY, right, midY, stroke)
            }
            ShapeType.TRIANGLE.name -> {
                val path = Path().apply {
                    moveTo(left + shape.width / 2f, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                canvas.drawPath(path, fill)
                canvas.drawPath(path, stroke)
            }
            ShapeType.ROUNDED_RECT.name -> {
                val rect = RectF(left, top, right, bottom)
                val r = minOf(shape.width, shape.height) * 0.12f
                canvas.drawRoundRect(rect, r, r, fill)
                canvas.drawRoundRect(rect, r, r, stroke)
            }
            else -> {
                val rect = RectF(left, top, right, bottom)
                canvas.drawRect(rect, fill)
                canvas.drawRect(rect, stroke)
            }
        }
    }

    private fun withOpacity(color: Int, opacity: Float): Int {
        val a = (opacity.coerceIn(0f, 1f) * 255).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    fun renderToBitmap(
        context: Context,
        pageWidth: Float,
        pageHeight: Float,
        backgroundColor: Long,
        templateName: String,
        strokes: List<StrokeData>,
        shapes: List<ShapeObjectEntity>,
        texts: List<TextObjectEntity>,
        images: List<ImageObjectEntity>,
        underlay: ((Canvas) -> Unit)? = null
    ): Bitmap {
        val w = pageWidth.toInt().coerceAtLeast(1)
        val h = pageHeight.toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(
            context = context,
            canvas = canvas,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            backgroundColor = backgroundColor,
            templateName = templateName,
            strokes = strokes,
            shapes = shapes,
            texts = texts,
            images = images,
            underlay = underlay
        )
        return bitmap
    }
}
