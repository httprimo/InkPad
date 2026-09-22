package com.personal.inkpad.data.db

import com.personal.inkpad.domain.model.StrokeData
import com.personal.inkpad.domain.model.StrokePoint
import com.personal.inkpad.domain.model.BrushType
import org.json.JSONArray
import org.json.JSONObject

object StrokeJson {
    fun encode(points: List<StrokePoint>): String {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(
                JSONObject()
                    .put("x", p.x.toDouble())
                    .put("y", p.y.toDouble())
                    .put("p", p.pressure.toDouble())
                    .put("t", p.timestamp)
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<StrokePoint> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    StrokePoint(
                        x = o.getDouble("x").toFloat(),
                        y = o.getDouble("y").toFloat(),
                        pressure = o.optDouble("p", 1.0).toFloat(),
                        timestamp = o.optLong("t", 0L)
                    )
                )
            }
        }
    }

    fun toEntity(pageId: String, stroke: StrokeData, createdAt: Long = System.currentTimeMillis()) =
        StrokeEntity(
            id = stroke.id,
            pageId = pageId,
            pointsJson = encode(stroke.points),
            color = stroke.color,
            width = stroke.width,
            opacity = stroke.opacity,
            brushType = stroke.brushType.name,
            createdAt = createdAt
        )

    fun fromEntity(entity: StrokeEntity) = StrokeData(
        id = entity.id,
        points = decode(entity.pointsJson),
        color = entity.color,
        width = entity.width,
        opacity = entity.opacity,
        brushType = runCatching { BrushType.valueOf(entity.brushType) }.getOrDefault(BrushType.BALLPOINT)
    )
}
