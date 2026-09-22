package com.personal.inkpad.engine

import com.personal.inkpad.domain.model.ShapeType
import com.personal.inkpad.domain.model.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Freehand → shape snap via best-match confidence.
 * Always picks the strongest of rect / triangle / circle / oval when
 * confidence clears a modest bar — avoids brittle cascading if/else misses.
 */
object ShapeRecognizer {
    data class Result(
        val type: ShapeType,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    private data class Candidate(val type: ShapeType, val score: Float, val result: Result)

    fun recognize(points: List<StrokePoint>): Result? {
        if (points.size < 10) return null
        val sampled = resample(points, target = 64)
        val minX = sampled.minOf { it.x }
        val maxX = sampled.maxOf { it.x }
        val minY = sampled.minOf { it.y }
        val maxY = sampled.maxOf { it.y }
        val w = maxX - minX
        val h = maxY - minY
        // Letter-sized marks (≈1 ruled line = 28px) must stay ink; require ~2.5+ lines
        if (min(w, h) < 72f || max(w, h) < 80f) return null

        val closure = closureScore(sampled, w, h)
        if (closure < 0.42f) return null

        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val rx = (w / 2f).coerceAtLeast(1f)
        val ry = (h / 2f).coerceAtLeast(1f)
        val aspect = w / h.coerceAtLeast(1f)

        val corners = approximateCorners(sampled, epsilon = min(w, h) * 0.065f)
        val cornerCount = corners.size
        val rightAngles = corners.count { isNearRightAngle(sampled, nearestIndex(sampled, it)) }

        val dists = sampled.map { hypot(it.x - cx, it.y - cy) }.sorted()
        val meanR = dists.average().toFloat().coerceAtLeast(1f)
        val p10 = dists[(dists.size * 0.10f).toInt().coerceIn(0, dists.lastIndex)].coerceAtLeast(1f)
        val p90 = dists[(dists.size * 0.90f).toInt().coerceIn(0, dists.lastIndex)]
        val robustRatio = (p90 / p10).coerceAtLeast(1f)
        val circCv = sqrt(dists.map { val d = it - meanR; d * d }.average().toFloat()) / meanR
        val circScore = (1f - circCv.coerceIn(0f, 1f))

        val ellipseResiduals = sampled.map { p ->
            val nx = (p.x - cx) / rx
            val ny = (p.y - cy) / ry
            abs(nx * nx + ny * ny - 1f)
        }
        val ellipseScore = 1f - (ellipseResiduals.average().toFloat() / 0.55f).coerceIn(0f, 1f)

        val rectFit = rectangleFitScore(sampled, minX, maxX, minY, maxY)
        val triFit = max(
            bestTriangleFitScore(sampled, minX, maxX, minY, maxY),
            cornerBasedTriangleScore(sampled, corners)
        )

        // --- confidences (0..1), with feature bonuses/penalties ---
        var rectConf = rectFit
        if (cornerCount in 4..6) rectConf += 0.08f
        if (rightAngles >= 3) rectConf += 0.14f
        else if (rightAngles >= 2) rectConf += 0.07f
        if (cornerCount == 3) rectConf -= 0.12f
        rectConf = (rectConf * (0.7f + 0.3f * closure)).coerceIn(0f, 1f)

        var triConf = triFit
        if (cornerCount == 3) triConf += 0.16f
        else if (cornerCount == 4) triConf += 0.04f
        if (rightAngles >= 3) triConf -= 0.25f
        else if (rightAngles >= 2) triConf -= 0.1f
        if (cornerCount <= 2) triConf -= 0.15f
        triConf = (triConf * (0.75f + 0.25f * closure)).coerceIn(0f, 1f)

        var circleConf = (circScore * 0.45f + ellipseScore * 0.55f)
        // Roundness from robust radius
        circleConf += when {
            robustRatio <= 1.18f -> 0.12f
            robustRatio <= 1.28f -> 0.06f
            robustRatio <= 1.4f -> 0f
            else -> -0.2f
        }
        if (aspect !in 0.78f..1.28f) circleConf -= 0.2f
        if (rightAngles >= 2) circleConf -= 0.3f
        if (cornerCount >= 4 && rightAngles >= 2) circleConf -= 0.2f
        if (cornerCount <= 2) circleConf += 0.08f
        if (rectFit >= 0.55f) circleConf -= 0.22f
        circleConf = (circleConf * (0.65f + 0.35f * closure)).coerceIn(0f, 1f)

        var ovalConf = ellipseScore
        if (aspect in 0.78f..1.28f) ovalConf -= 0.25f // prefer circle when nearly square bbox
        else ovalConf += 0.1f
        if (robustRatio > 1.5f) ovalConf -= 0.15f
        if (rightAngles >= 2) ovalConf -= 0.25f
        if (rectFit >= 0.55f) ovalConf -= 0.2f
        ovalConf = (ovalConf * (0.65f + 0.35f * closure)).coerceIn(0f, 1f)

        val squareResult = run {
            val side = max(w, h)
            Result(ShapeType.RECTANGLE, cx - side / 2f, cy - side / 2f, side, side)
        }
        val rectResult = Result(ShapeType.RECTANGLE, minX, minY, w, h)
        val triResult = Result(ShapeType.TRIANGLE, minX, minY, w, h)
        val circResult = Result(ShapeType.CIRCLE, cx - meanR, cy - meanR, meanR * 2f, meanR * 2f)
        val ovalResult = Result(ShapeType.ELLIPSE, minX, minY, w, h)

        val candidates = listOf(
            Candidate(
                ShapeType.RECTANGLE,
                rectConf,
                if (aspect in 0.88f..1.12f) squareResult else rectResult
            ),
            Candidate(ShapeType.TRIANGLE, triConf, triResult),
            Candidate(ShapeType.CIRCLE, circleConf, circResult),
            Candidate(ShapeType.ELLIPSE, ovalConf, ovalResult)
        ).sortedByDescending { it.score }

        val best = candidates[0]
        val second = candidates[1].score
        // Accept when clearly a shape; allow closer races if top score is strong
        val accept =
            best.score >= 0.48f &&
                (best.score >= second + 0.06f || best.score >= 0.6f)
        return if (accept) best.result else null
    }

    /** 1 = ends meet; lower as gap grows. Still usable with a noticeable gap. */
    private fun closureScore(points: List<StrokePoint>, w: Float, h: Float): Float {
        val first = points.first()
        val last = points.last()
        val gap = hypot(first.x - last.x, first.y - last.y)
        val scale = min(w, h).coerceAtLeast(1f)
        var bestGap = gap
        val tailStart = (points.size * 0.7f).toInt().coerceAtLeast(0)
        for (i in tailStart until points.size) {
            bestGap = min(bestGap, hypot(points[i].x - first.x, points[i].y - first.y))
        }
        // gap of 0 → 1.0; gap of 0.55*scale → ~0.35; gap of scale → 0
        return (1f - (bestGap / (scale * 0.85f))).coerceIn(0f, 1f)
    }

    private fun resample(points: List<StrokePoint>, target: Int): List<StrokePoint> {
        if (points.size <= target) return points
        val out = ArrayList<StrokePoint>(target)
        val last = (target - 1).toFloat()
        for (i in 0 until target) {
            val t = i / last
            val idx = (t * points.lastIndex).toInt().coerceIn(0, points.lastIndex)
            out.add(points[idx])
        }
        return out
    }

    private fun nearestIndex(points: List<StrokePoint>, corner: StrokePoint): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        points.forEachIndexed { i, p ->
            val d = hypot(p.x - corner.x, p.y - corner.y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun isNearRightAngle(points: List<StrokePoint>, index: Int): Boolean {
        val n = points.size
        if (n < 6) return false
        val step = (n / 12).coerceIn(2, 8)
        val prev = points[(index - step + n) % n]
        val cur = points[index]
        val next = points[(index + step) % n]
        return turnAngle(prev, cur, next) in 50f..130f
    }

    private fun approximateCorners(points: List<StrokePoint>, epsilon: Float): List<StrokePoint> {
        val simplified = rdp(points, epsilon.coerceAtLeast(3.5f))
        val closed = simplified.toMutableList()
        if (closed.size >= 2) {
            val a = closed.first()
            val b = closed.last()
            if (hypot(a.x - b.x, a.y - b.y) < epsilon * 1.3f) {
                closed.removeAt(closed.lastIndex)
            }
        }
        if (closed.size < 3) return closed
        val sharp = mutableListOf<StrokePoint>()
        val n = closed.size
        for (i in closed.indices) {
            val prev = closed[(i - 1 + n) % n]
            val cur = closed[i]
            val next = closed[(i + 1) % n]
            if (turnAngle(prev, cur, next) > 28f) sharp.add(cur)
        }
        return sharp.ifEmpty { closed }
    }

    private fun turnAngle(a: StrokePoint, b: StrokePoint, c: StrokePoint): Float {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val n1 = hypot(v1x, v1y).coerceAtLeast(0.001f)
        val n2 = hypot(v2x, v2y).coerceAtLeast(0.001f)
        val dot = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1f, 1f)
        return 180f - Math.toDegrees(kotlin.math.acos(dot.toDouble())).toFloat()
    }

    private fun rdp(points: List<StrokePoint>, epsilon: Float): List<StrokePoint> {
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
            val left = rdp(points.subList(0, index + 1), epsilon)
            val right = rdp(points.subList(index, points.size), epsilon)
            return left.dropLast(1) + right
        }
        return listOf(start, end)
    }

    private fun perpendicularDistance(p: StrokePoint, a: StrokePoint, b: StrokePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx == 0f && dy == 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    private fun cornerBasedTriangleScore(
        points: List<StrokePoint>,
        corners: List<StrokePoint>
    ): Float {
        if (corners.size < 3) return 0f
        if (corners.size == 3) return edgeProximityScore(points, corners, 0.14f)
        var best = 0f
        for (drop in corners.indices) {
            val trio = corners.filterIndexed { i, _ -> i != drop }
            if (trio.size == 3) best = max(best, edgeProximityScore(points, trio, 0.14f))
        }
        return best
    }

    private fun bestTriangleFitScore(
        points: List<StrokePoint>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Float {
        val templates = listOf(
            listOf(
                StrokePoint((minX + maxX) / 2f, minY),
                StrokePoint(maxX, maxY),
                StrokePoint(minX, maxY)
            ),
            listOf(
                StrokePoint(minX, minY),
                StrokePoint(maxX, minY),
                StrokePoint((minX + maxX) / 2f, maxY)
            ),
            listOf(
                StrokePoint(minX, minY),
                StrokePoint(maxX, (minY + maxY) / 2f),
                StrokePoint(minX, maxY)
            ),
            listOf(
                StrokePoint(maxX, minY),
                StrokePoint(minX, (minY + maxY) / 2f),
                StrokePoint(maxX, maxY)
            )
        )
        return templates.maxOf { edgeProximityScore(points, it, 0.13f) }
    }

    private fun rectangleFitScore(
        points: List<StrokePoint>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Float {
        val edgeTol = min(maxX - minX, maxY - minY) * 0.15f
        val top = points.count { abs(it.y - minY) <= edgeTol }.toFloat()
        val bottom = points.count { abs(it.y - maxY) <= edgeTol }.toFloat()
        val left = points.count { abs(it.x - minX) <= edgeTol }.toFloat()
        val right = points.count { abs(it.x - maxX) <= edgeTol }.toFloat()
        val n = points.size.toFloat().coerceAtLeast(1f)
        val fracs = listOf(top, bottom, left, right).map { it / n }
        val sidesHit = fracs.count { it >= 0.07f }
        if (sidesHit < 3) return 0.18f * (sidesHit / 4f)
        if (sidesHit == 3) return (0.32f + 0.28f * fracs.average().toFloat()).coerceIn(0f, 0.5f)

        val cornerTol = edgeTol * 1.55f
        val cornerHits = listOf(
            StrokePoint(minX, minY),
            StrokePoint(maxX, minY),
            StrokePoint(maxX, maxY),
            StrokePoint(minX, maxY)
        ).count { c -> points.any { hypot(it.x - c.x, it.y - c.y) <= cornerTol } }

        return (0.38f + 0.37f * fracs.average().toFloat() + 0.25f * (cornerHits / 4f))
            .coerceIn(0f, 1f)
    }

    private fun edgeProximityScore(
        points: List<StrokePoint>,
        vertices: List<StrokePoint>,
        tolFrac: Float
    ): Float {
        if (vertices.size < 2) return 0f
        val edges = vertices.indices.map { i -> vertices[i] to vertices[(i + 1) % vertices.size] }
        val size = hypot(
            vertices.maxOf { it.x } - vertices.minOf { it.x },
            vertices.maxOf { it.y } - vertices.minOf { it.y }
        ).coerceAtLeast(1f)
        val tol = size * tolFrac
        val near = points.count { p ->
            edges.any { (a, b) -> perpendicularDistance(p, a, b) <= tol }
        }
        return near.toFloat() / points.size
    }
}
