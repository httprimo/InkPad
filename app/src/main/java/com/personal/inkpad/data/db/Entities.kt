package com.personal.inkpad.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "notebooks",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folderId"), Index("updatedAt"), Index("favorite")]
)
data class NotebookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val coverColor: Long,
    val folderId: String? = null,
    val paperTemplate: String,
    val pageSize: String,
    val orientation: String,
    val backgroundColor: Long = 0xFFFFFFFF,
    val favorite: Boolean = false,
    val tagsJson: String = "[]",
    val thumbnailPath: String? = null,
    val pdfPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notebookId"), Index(value = ["notebookId", "pageIndex"], unique = true)]
)
data class PageEntity(
    @PrimaryKey val id: String,
    val notebookId: String,
    val pageIndex: Int,
    val width: Float,
    val height: Float,
    val backgroundColor: Long = 0xFFFFFFFF,
    val template: String,
    val pdfPageIndex: Int? = null
)

@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class StrokeEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val pointsJson: String,
    val color: Long,
    val width: Float,
    val opacity: Float,
    val brushType: String,
    val createdAt: Long
)

@Entity(
    tableName = "text_objects",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class TextObjectEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val color: Long,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val alignment: String = "START",
    /** Freenotes-style beautified handwriting → script font. */
    val scriptStyle: Boolean = false
)

@Entity(
    tableName = "image_objects",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class ImageObjectEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val path: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f
)

@Entity(
    tableName = "shape_objects",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class ShapeObjectEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val shapeType: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fillColor: Long,
    val strokeColor: Long,
    val strokeWidth: Float,
    val opacity: Float = 1f,
    val rotation: Float = 0f
)

@Entity(
    tableName = "tape_masks",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class TapeMaskEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val revealed: Boolean = false
)
