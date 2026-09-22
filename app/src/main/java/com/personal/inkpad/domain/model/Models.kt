package com.personal.inkpad.domain.model

enum class PaperTemplate {
    BLANK, RULED, GRID, DOTTED, CORNELL, PLANNER, MUSIC, CHECKLIST, CUSTOM
}

enum class PageSize(val widthMm: Float, val heightMm: Float) {
    A4(210f, 297f),
    LETTER(215.9f, 279.4f),
    A5(148f, 210f),
    A3(297f, 420f),
    CUSTOM(210f, 297f);

    fun pixels(dpi: Float = 160f, landscape: Boolean = false): Pair<Float, Float> {
        val w = widthMm / 25.4f * dpi
        val h = heightMm / 25.4f * dpi
        return if (landscape) h to w else w to h
    }
}

enum class Orientation { PORTRAIT, LANDSCAPE }

enum class BrushType {
    BALLPOINT, FOUNTAIN, PENCIL, TECHNICAL, HIGHLIGHTER, ERASER
}

enum class EditorTool {
    PEN, HIGHLIGHTER, ERASER, LASSO, TEXT, IMAGE, SHAPE, LASER, TAPE, SELECT
}

enum class ShapeType {
    RECTANGLE, ROUNDED_RECT, CIRCLE, ELLIPSE, TRIANGLE, LINE, ARROW, POLYGON
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timestamp: Long = 0L
)

data class StrokeData(
    val id: String,
    val points: List<StrokePoint>,
    val color: Long,
    val width: Float,
    val opacity: Float = 1f,
    val brushType: BrushType = BrushType.BALLPOINT
)
