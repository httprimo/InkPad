package com.personal.inkpad.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.personal.inkpad.domain.model.BrushType
import com.personal.inkpad.domain.model.StrokeData
import com.personal.inkpad.domain.model.StrokePoint
import java.util.UUID
import kotlin.math.hypot

/**
 * Vector handwriting engine — isolated from UI and persistence.
 * Stores strokes as polylines with pressure; never as full-page bitmaps.
 */
class StrokeEngine {
    private val _strokes = mutableListOf<StrokeData>()
    val strokes: List<StrokeData> get() = _strokes

    private val undoStack = ArrayDeque<HistoryOp>()
    private val redoStack = ArrayDeque<HistoryOp>()

    private var activePoints = mutableListOf<StrokePoint>()
    private var activeId: String? = null

    var penColor: Long = 0xFF1B1B1B
    var penWidth: Float = 3.5f
    var penOpacity: Float = 1f
    var brushType: BrushType = BrushType.BALLPOINT
    var eraserRadius: Float = 18f
    var strokeEraser: Boolean = true
    /** Freenotes-style auto handwriting enhance after stroke ends. */
    var pencraftEnabled: Boolean = true
    var pencraftIntensity: Float = 0.7f
    /** When true, closed freehand strokes may snap to geometry. Default off for writing. */
    var shapeSnapEnabled: Boolean = false

    fun load(strokes: List<StrokeData>) {
        _strokes.clear()
        _strokes.addAll(strokes)
        undoStack.clear()
        redoStack.clear()
        activePoints.clear()
        activeId = null
    }

    fun beginStroke(x: Float, y: Float, pressure: Float, timestamp: Long) {
        activeId = UUID.randomUUID().toString()
        activePoints = mutableListOf(StrokePoint(x, y, pressure.coerceIn(0.05f, 1f), timestamp))
    }

    fun appendStroke(x: Float, y: Float, pressure: Float, timestamp: Long) {
        val last = activePoints.lastOrNull()
        if (last != null) {
            val dist = hypot(x - last.x, y - last.y)
            if (dist < 0.4f) return
        }
        // Simple smoothing: average with previous mid-point
        val smoothed = if (last != null) {
            StrokePoint(
                x = (last.x + x) / 2f,
                y = (last.y + y) / 2f,
                pressure = pressure.coerceIn(0.05f, 1f),
                timestamp = timestamp
            )
        } else {
            StrokePoint(x, y, pressure.coerceIn(0.05f, 1f), timestamp)
        }
        activePoints.add(smoothed)
        activePoints.add(StrokePoint(x, y, pressure.coerceIn(0.05f, 1f), timestamp))
    }

    /** When true, skip Pencraft — raw ink feeds handwriting→font recognition. */
    var beautifyEnabled: Boolean = false

    /** Set when the just-finished stroke matched a shape (from raw points, pre-Pencraft). */
    var lastShapeResult: ShapeRecognizer.Result? = null
        private set

    fun consumeLastShapeResult(): ShapeRecognizer.Result? {
        val r = lastShapeResult
        lastShapeResult = null
        return r
    }

    fun endStroke(): StrokeData? {
        val id = activeId ?: return null
        var points = activePoints.toList()
        activeId = null
        activePoints = mutableListOf()
        if (points.size < 2) return null

        // Recognize on raw ink first — Pencraft must not change geometry before snap
        lastShapeResult = if (
            shapeSnapEnabled &&
            brushType != BrushType.ERASER &&
            brushType != BrushType.HIGHLIGHTER
        ) {
            ShapeRecognizer.recognize(points)
        } else {
            null
        }

        if (
            lastShapeResult == null &&
            !beautifyEnabled &&
            pencraftEnabled &&
            brushType != BrushType.HIGHLIGHTER &&
            brushType != BrushType.ERASER
        ) {
            points = PencraftEnhancer.enhance(points, pencraftIntensity)
        }
        val widthFactor = when (brushType) {
            BrushType.FOUNTAIN -> 1.2f
            BrushType.PENCIL -> 0.9f
            BrushType.TECHNICAL -> 0.75f
            BrushType.HIGHLIGHTER -> 4.5f
            else -> 1f
        }
        val opacity = if (brushType == BrushType.HIGHLIGHTER) 0.35f else penOpacity
        val stroke = StrokeData(
            id = id,
            points = points,
            color = penColor,
            width = penWidth * widthFactor,
            opacity = opacity,
            brushType = brushType
        )
        _strokes.add(stroke)
        undoStack.addLast(HistoryOp.Add(stroke))
        redoStack.clear()
        return stroke
    }

    /** Drop an in-progress stroke (palm rejection / canceled touch). */
    fun cancelStroke() {
        activeId = null
        activePoints = mutableListOf()
        lastShapeResult = null
    }

    fun hasActiveStroke(): Boolean = activeId != null

    fun activePreview(): List<StrokePoint> = activePoints

    fun eraseAt(x: Float, y: Float): List<String> {
        val removed = mutableListOf<StrokeData>()
        val kept = mutableListOf<StrokeData>()
        for (stroke in _strokes) {
            val hit = stroke.points.any { hypot(it.x - x, it.y - y) <= eraserRadius + stroke.width }
            if (hit) {
                if (strokeEraser) {
                    removed.add(stroke)
                } else {
                    val split = splitStroke(stroke, x, y, eraserRadius)
                    if (split.isEmpty()) removed.add(stroke) else kept.addAll(split)
                }
            } else {
                kept.add(stroke)
            }
        }
        if (removed.isEmpty() && kept.size == _strokes.size) return emptyList()
        val before = _strokes.toList()
        _strokes.clear()
        _strokes.addAll(kept)
        undoStack.addLast(HistoryOp.Replace(before, kept.toList()))
        redoStack.clear()
        return removed.map { it.id } + (before.map { it.id } - kept.map { it.id }.toSet())
    }

    private fun splitStroke(stroke: StrokeData, x: Float, y: Float, radius: Float): List<StrokeData> {
        val segments = mutableListOf<MutableList<StrokePoint>>()
        var current = mutableListOf<StrokePoint>()
        stroke.points.forEach { p ->
            if (hypot(p.x - x, p.y - y) <= radius + stroke.width) {
                if (current.size >= 2) segments.add(current)
                current = mutableListOf()
            } else {
                current.add(p)
            }
        }
        if (current.size >= 2) segments.add(current)
        return segments.map {
            stroke.copy(id = UUID.randomUUID().toString(), points = it)
        }
    }

    fun deleteStrokes(ids: Set<String>) {
        if (ids.isEmpty()) return
        val before = _strokes.toList()
        _strokes.removeAll { it.id in ids }
        undoStack.addLast(HistoryOp.Replace(before, _strokes.toList()))
        redoStack.clear()
    }

    fun moveStrokes(ids: Set<String>, dx: Float, dy: Float) {
        if (ids.isEmpty()) return
        val before = _strokes.toList()
        _strokes.replaceAll { stroke ->
            if (stroke.id in ids) {
                stroke.copy(points = stroke.points.map { it.copy(x = it.x + dx, y = it.y + dy) })
            } else stroke
        }
        undoStack.addLast(HistoryOp.Replace(before, _strokes.toList()))
        redoStack.clear()
    }

    fun strokesInLasso(lasso: List<Offset>): Set<String> {
        if (lasso.size < 3) return emptySet()
        val bounds = Rect(
            left = lasso.minOf { it.x },
            top = lasso.minOf { it.y },
            right = lasso.maxOf { it.x },
            bottom = lasso.maxOf { it.y }
        )
        return _strokes.filter { stroke ->
            stroke.points.any { p ->
                p.x in bounds.left..bounds.right && p.y in bounds.top..bounds.bottom &&
                    pointInPolygon(Offset(p.x, p.y), lasso)
            }
        }.map { it.id }.toSet()
    }

    fun undo(): Boolean {
        val op = undoStack.removeLastOrNull() ?: return false
        when (op) {
            is HistoryOp.Add -> {
                _strokes.removeAll { it.id == op.stroke.id }
                redoStack.addLast(op)
            }
            is HistoryOp.Replace -> {
                _strokes.clear()
                _strokes.addAll(op.before)
                redoStack.addLast(op)
            }
        }
        return true
    }

    fun redo(): Boolean {
        val op = redoStack.removeLastOrNull() ?: return false
        when (op) {
            is HistoryOp.Add -> {
                _strokes.add(op.stroke)
                undoStack.addLast(op)
            }
            is HistoryOp.Replace -> {
                _strokes.clear()
                _strokes.addAll(op.after)
                undoStack.addLast(op)
            }
        }
        return true
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    sealed class HistoryOp {
        data class Add(val stroke: StrokeData) : HistoryOp()
        data class Replace(val before: List<StrokeData>, val after: List<StrokeData>) : HistoryOp()
    }
}

fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        val intersect = ((pi.y > point.y) != (pj.y > point.y)) &&
            (point.x < (pj.x - pi.x) * (point.y - pi.y) / ((pj.y - pi.y).takeIf { it != 0f } ?: 0.0001f) + pi.x)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

fun DrawScope.drawStrokeData(stroke: StrokeData, viewport: Rect? = null) {
    if (stroke.points.size < 2) return
    if (viewport != null) {
        val minX = stroke.points.minOf { it.x }
        val maxX = stroke.points.maxOf { it.x }
        val minY = stroke.points.minOf { it.y }
        val maxY = stroke.points.maxOf { it.y }
        if (maxX < viewport.left || minX > viewport.right || maxY < viewport.top || minY > viewport.bottom) {
            return
        }
    }
    val path = Path()
    path.moveTo(stroke.points[0].x, stroke.points[0].y)
    for (i in 1 until stroke.points.size) {
        val prev = stroke.points[i - 1]
        val cur = stroke.points[i]
        val midX = (prev.x + cur.x) / 2f
        val midY = (prev.y + cur.y) / 2f
        path.quadraticTo(prev.x, prev.y, midX, midY)
    }
    val last = stroke.points.last()
    path.lineTo(last.x, last.y)

    val avgPressure = stroke.points.map { it.pressure }.average().toFloat().coerceIn(0.2f, 1f)
    val width = when (stroke.brushType) {
        BrushType.FOUNTAIN -> stroke.width * (0.6f + avgPressure * 0.8f)
        BrushType.PENCIL -> stroke.width * (0.8f + avgPressure * 0.4f)
        else -> stroke.width * (0.85f + avgPressure * 0.3f)
    }
    drawPath(
        path = path,
        color = Color(stroke.color).copy(alpha = stroke.opacity),
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
