package com.personal.inkpad.engine

import com.personal.inkpad.domain.model.StrokePoint
import kotlin.math.hypot

/**
 * Freenotes-style “Pencraft” / auto handwriting enhance.
 * Softens jitter and rounds paths after the stylus lifts, without flattening letter shapes.
 */
object PencraftEnhancer {
    fun enhance(points: List<StrokePoint>, intensity: Float = 0.7f): List<StrokePoint> {
        if (points.size < 4) return points
        val strength = intensity.coerceIn(0.15f, 1f)
        val simplified = ramerDouglasPeucker(points, epsilon = 0.55f + (1f - strength) * 1.2f)
        val base = if (simplified.size >= 3) simplified else points
        val passes = if (strength > 0.75f) 2 else 1
        var smoothed = base
        repeat(passes) { smoothed = chaikin(smoothed) }
        // Blend with original so characters keep identity
        return blendToward(points, resampleTo(smoothed, points.size.coerceAtMost(smoothed.size * 3)), strength)
    }

    private fun chaikin(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 3) return points
        val out = ArrayList<StrokePoint>(points.size * 2)
        out.add(points.first())
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            out.add(
                StrokePoint(
                    x = 0.75f * a.x + 0.25f * b.x,
                    y = 0.75f * a.y + 0.25f * b.y,
                    pressure = 0.75f * a.pressure + 0.25f * b.pressure,
                    timestamp = a.timestamp
                )
            )
            out.add(
                StrokePoint(
                    x = 0.25f * a.x + 0.75f * b.x,
                    y = 0.25f * a.y + 0.75f * b.y,
                    pressure = 0.25f * a.pressure + 0.75f * b.pressure,
                    timestamp = b.timestamp
                )
            )
        }
        out.add(points.last())
        return out
    }

    private fun ramerDouglasPeucker(points: List<StrokePoint>, epsilon: Float): List<StrokePoint> {
        if (points.size < 3) return points
        var maxDist = 0f
        var index = 0
        val start = points.first()
        val end = points.last()
        for (i in 1 until points.lastIndex) {
            val d = perpendicularDistance(points[i], start, end)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        if (maxDist > epsilon) {
            val left = ramerDouglasPeucker(points.subList(0, index + 1), epsilon)
            val right = ramerDouglasPeucker(points.subList(index, points.size), epsilon)
            return left.dropLast(1) + right
        }
        return listOf(start, end)
    }

    private fun perpendicularDistance(p: StrokePoint, a: StrokePoint, b: StrokePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx == 0f && dy == 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return hypot(p.x - projX, p.y - projY)
    }

    private fun resampleTo(points: List<StrokePoint>, targetCount: Int): List<StrokePoint> {
        if (points.size < 2 || targetCount <= 2) return points
        val total = pathLength(points).coerceAtLeast(0.001f)
        val step = total / (targetCount - 1)
        val out = ArrayList<StrokePoint>(targetCount)
        out.add(points.first())
        var traveled = 0f
        var i = 0
        var nextDist = step
        while (out.size < targetCount - 1 && i < points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val seg = hypot(b.x - a.x, b.y - a.y)
            if (traveled + seg >= nextDist && seg > 0f) {
                val t = ((nextDist - traveled) / seg).coerceIn(0f, 1f)
                out.add(
                    StrokePoint(
                        x = a.x + (b.x - a.x) * t,
                        y = a.y + (b.y - a.y) * t,
                        pressure = a.pressure + (b.pressure - a.pressure) * t,
                        timestamp = a.timestamp + ((b.timestamp - a.timestamp) * t).toLong()
                    )
                )
                nextDist += step
            } else {
                traveled += seg
                i++
            }
        }
        out.add(points.last())
        return out
    }

    private fun pathLength(points: List<StrokePoint>): Float {
        var len = 0f
        for (i in 1 until points.size) {
            len += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        return len
    }

    private fun blendToward(
        original: List<StrokePoint>,
        enhanced: List<StrokePoint>,
        strength: Float
    ): List<StrokePoint> {
        if (enhanced.isEmpty()) return original
        val n = minOf(original.size, enhanced.size)
        if (n < 2) return enhanced
        val out = ArrayList<StrokePoint>(original.size)
        // Map enhanced onto original timing by index ratio
        for (i in original.indices) {
            val t = i.toFloat() / original.lastIndex.coerceAtLeast(1)
            val j = (t * enhanced.lastIndex).toInt().coerceIn(0, enhanced.lastIndex)
            val o = original[i]
            val e = enhanced[j]
            val s = strength
            out.add(
                StrokePoint(
                    x = o.x * (1f - s) + e.x * s,
                    y = o.y * (1f - s) + e.y * s,
                    pressure = o.pressure * (1f - s * 0.35f) + e.pressure * (s * 0.35f),
                    timestamp = o.timestamp
                )
            )
        }
        // Mild pressure smoothing
        if (out.size >= 3) {
            for (i in 1 until out.lastIndex) {
                val p = (out[i - 1].pressure + out[i].pressure + out[i + 1].pressure) / 3f
                out[i] = out[i].copy(pressure = p)
            }
        }
        return out
    }
}
