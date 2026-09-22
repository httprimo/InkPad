package com.personal.inkpad.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.personal.inkpad.domain.model.PaperTemplate
import kotlin.math.ceil

@Composable
fun PaperBackground(
    modifier: Modifier = Modifier,
    template: PaperTemplate,
    backgroundColor: Color,
    pageWidth: Float,
    pageHeight: Float,
    scale: Float,
    offset: Offset
) {
    Canvas(
        modifier = modifier
            .background(Color(0xFFE8E6E1))
    ) {
        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawRect(backgroundColor, size = androidx.compose.ui.geometry.Size(pageWidth, pageHeight))
            val lineColor = Color(0xFFB8C0C8).copy(alpha = 0.65f)
            val marginColor = Color(0xFFE07A5F).copy(alpha = 0.45f)
            when (template) {
                PaperTemplate.RULED, PaperTemplate.PLANNER, PaperTemplate.CHECKLIST -> {
                    val spacing = 28f
                    var y = 48f
                    while (y < pageHeight) {
                        drawLine(lineColor, Offset(24f, y), Offset(pageWidth - 24f, y), strokeWidth = 1f)
                        y += spacing
                    }
                    drawLine(marginColor, Offset(72f, 24f), Offset(72f, pageHeight - 24f), strokeWidth = 1.5f)
                }
                PaperTemplate.GRID -> {
                    val spacing = 28f
                    var x = 24f
                    while (x < pageWidth) {
                        drawLine(lineColor, Offset(x, 24f), Offset(x, pageHeight - 24f), 1f)
                        x += spacing
                    }
                    var y = 24f
                    while (y < pageHeight) {
                        drawLine(lineColor, Offset(24f, y), Offset(pageWidth - 24f, y), 1f)
                        y += spacing
                    }
                }
                PaperTemplate.DOTTED -> {
                    val spacing = 28f
                    var y = 32f
                    while (y < pageHeight) {
                        var x = 32f
                        while (x < pageWidth) {
                            drawCircle(lineColor, radius = 1.6f, center = Offset(x, y))
                            x += spacing
                        }
                        y += spacing
                    }
                }
                PaperTemplate.CORNELL -> {
                    drawLine(lineColor, Offset(pageWidth * 0.28f, 40f), Offset(pageWidth * 0.28f, pageHeight * 0.78f), 1.5f)
                    drawLine(lineColor, Offset(24f, pageHeight * 0.78f), Offset(pageWidth - 24f, pageHeight * 0.78f), 1.5f)
                    var y = 64f
                    while (y < pageHeight * 0.78f) {
                        drawLine(lineColor.copy(alpha = 0.35f), Offset(pageWidth * 0.3f, y), Offset(pageWidth - 24f, y), 1f)
                        y += 28f
                    }
                }
                PaperTemplate.MUSIC -> {
                    var y = 80f
                    while (y < pageHeight - 80f) {
                        repeat(5) { i ->
                            drawLine(lineColor, Offset(40f, y + i * 12f), Offset(pageWidth - 40f, y + i * 12f), 1.2f)
                        }
                        y += 90f
                    }
                }
                PaperTemplate.BLANK, PaperTemplate.CUSTOM -> Unit
            }
            drawRect(
                color = Color(0x22000000),
                topLeft = Offset(pageWidth, 4f),
                size = androidx.compose.ui.geometry.Size(6f, pageHeight)
            )
            drawRect(
                color = Color(0x22000000),
                topLeft = Offset(4f, pageHeight),
                size = androidx.compose.ui.geometry.Size(pageWidth, 6f)
            )
        }
    }
}

fun fitScale(container: IntSize, pageWidth: Float, pageHeight: Float, padding: Float = 32f): Float {
    if (container.width == 0 || container.height == 0) return 1f
    val sx = (container.width - padding) / pageWidth
    val sy = (container.height - padding) / pageHeight
    return minOf(sx, sy).coerceIn(0.1f, 4f)
}

fun zoomPresets(): List<Pair<String, Float>> = listOf(
    "25%" to 0.25f,
    "50%" to 0.5f,
    "75%" to 0.75f,
    "100%" to 1f,
    "125%" to 1.25f,
    "150%" to 1.5f,
    "200%" to 2f,
    "300%" to 3f,
    "400%" to 4f,
    "600%" to 6f,
    "800%" to 8f,
    "1000%" to 10f
)
