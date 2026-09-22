package com.personal.inkpad.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.personal.inkpad.data.db.StrokeJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupExportService(
    private val context: Context,
    private val repo: InkPadRepository
) {
    suspend fun createBackup(): File = withContext(Dispatchers.IO) {
        val db = repo.getDb()
        val root = JSONObject()
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())

        suspend fun listToArray(block: suspend (JSONArray) -> Unit): JSONArray {
            val arr = JSONArray()
            block(arr)
            return arr
        }

        root.put("folders", listToArray { arr ->
            db.folderDao().getAll().forEach { f ->
                arr.put(
                    JSONObject()
                        .put("id", f.id)
                        .put("name", f.name)
                        .put("parentId", f.parentId)
                        .put("createdAt", f.createdAt)
                        .put("updatedAt", f.updatedAt)
                )
            }
        })
        root.put("notebooks", listToArray { arr ->
            db.notebookDao().getAll().forEach { n ->
                arr.put(
                    JSONObject()
                        .put("id", n.id)
                        .put("title", n.title)
                        .put("coverColor", n.coverColor)
                        .put("folderId", n.folderId)
                        .put("paperTemplate", n.paperTemplate)
                        .put("pageSize", n.pageSize)
                        .put("orientation", n.orientation)
                        .put("backgroundColor", n.backgroundColor)
                        .put("favorite", n.favorite)
                        .put("tagsJson", n.tagsJson)
                        .put("pdfPath", n.pdfPath)
                        .put("createdAt", n.createdAt)
                        .put("updatedAt", n.updatedAt)
                )
            }
        })
        root.put("pages", listToArray { arr ->
            db.pageDao().getAll().forEach { p ->
                arr.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("notebookId", p.notebookId)
                        .put("pageIndex", p.pageIndex)
                        .put("width", p.width.toDouble())
                        .put("height", p.height.toDouble())
                        .put("backgroundColor", p.backgroundColor)
                        .put("template", p.template)
                        .put("pdfPageIndex", p.pdfPageIndex)
                )
            }
        })
        root.put("strokes", listToArray { arr ->
            db.strokeDao().getAll().forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("pageId", s.pageId)
                        .put("pointsJson", s.pointsJson)
                        .put("color", s.color)
                        .put("width", s.width.toDouble())
                        .put("opacity", s.opacity.toDouble())
                        .put("brushType", s.brushType)
                        .put("createdAt", s.createdAt)
                )
            }
        })
        root.put("texts", listToArray { arr ->
            db.textObjectDao().getAll().forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("pageId", t.pageId)
                        .put("text", t.text)
                        .put("x", t.x.toDouble())
                        .put("y", t.y.toDouble())
                        .put("width", t.width.toDouble())
                        .put("height", t.height.toDouble())
                        .put("fontSize", t.fontSize.toDouble())
                        .put("color", t.color)
                        .put("bold", t.bold)
                        .put("italic", t.italic)
                        .put("underline", t.underline)
                        .put("alignment", t.alignment)
                        .put("scriptStyle", t.scriptStyle)
                )
            }
        })
        root.put("images", listToArray { arr ->
            db.imageObjectDao().getAll().forEach { i ->
                arr.put(
                    JSONObject()
                        .put("id", i.id)
                        .put("pageId", i.pageId)
                        .put("path", i.path)
                        .put("x", i.x.toDouble())
                        .put("y", i.y.toDouble())
                        .put("width", i.width.toDouble())
                        .put("height", i.height.toDouble())
                        .put("rotation", i.rotation.toDouble())
                )
            }
        })
        root.put("shapes", listToArray { arr ->
            db.shapeObjectDao().getAll().forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("pageId", s.pageId)
                        .put("shapeType", s.shapeType)
                        .put("x", s.x.toDouble())
                        .put("y", s.y.toDouble())
                        .put("width", s.width.toDouble())
                        .put("height", s.height.toDouble())
                        .put("fillColor", s.fillColor)
                        .put("strokeColor", s.strokeColor)
                        .put("strokeWidth", s.strokeWidth.toDouble())
                        .put("opacity", s.opacity.toDouble())
                        .put("rotation", s.rotation.toDouble())
                )
            }
        })

        val outDir = repo.filesDir("backups")
        val zipFile = File(outDir, "inkpad-backup-${System.currentTimeMillis()}.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            fun addDir(subdir: String) {
                val dir = File(context.filesDir, subdir)
                if (!dir.exists()) return
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = "$subdir/${file.relativeTo(dir).invariantSeparatorsPath}"
                    zip.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            addDir("images")
            addDir("pdf")
            addDir("audio")
        }
        zipFile
    }

    suspend fun restoreBackup(zipFile: File) = withContext(Dispatchers.IO) {
        val db = repo.getDb()
        var manifest: JSONObject? = null
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") {
                    manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                } else if (!entry.isDirectory) {
                    val dest = File(context.filesDir, entry.name)
                    dest.parentFile?.mkdirs()
                    FileOutputStream(dest).use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val root = manifest ?: error("Invalid backup")
        db.clearAllTables()

        val folderDao = db.folderDao()
        val notebookDao = db.notebookDao()
        val pageDao = db.pageDao()
        val strokeDao = db.strokeDao()
        val textDao = db.textObjectDao()
        val imageDao = db.imageObjectDao()
        val shapeDao = db.shapeObjectDao()

        root.optJSONArray("folders")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                folderDao.upsert(
                    com.personal.inkpad.data.db.FolderEntity(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        parentId = o.nullableString("parentId"),
                        createdAt = o.getLong("createdAt"),
                        updatedAt = o.getLong("updatedAt")
                    )
                )
            }
        }
        root.optJSONArray("notebooks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                notebookDao.upsert(
                    com.personal.inkpad.data.db.NotebookEntity(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        coverColor = o.getLong("coverColor"),
                        folderId = o.nullableString("folderId"),
                        paperTemplate = o.getString("paperTemplate"),
                        pageSize = o.getString("pageSize"),
                        orientation = o.getString("orientation"),
                        backgroundColor = o.optLong("backgroundColor", 0xFFFFFFFF),
                        favorite = o.optBoolean("favorite", false),
                        tagsJson = o.optString("tagsJson", "[]"),
                        pdfPath = o.nullableString("pdfPath"),
                        createdAt = o.getLong("createdAt"),
                        updatedAt = o.getLong("updatedAt")
                    )
                )
            }
        }
        root.optJSONArray("pages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                pageDao.upsert(
                    com.personal.inkpad.data.db.PageEntity(
                        id = o.getString("id"),
                        notebookId = o.getString("notebookId"),
                        pageIndex = o.getInt("pageIndex"),
                        width = o.getDouble("width").toFloat(),
                        height = o.getDouble("height").toFloat(),
                        backgroundColor = o.optLong("backgroundColor", 0xFFFFFFFF),
                        template = o.getString("template"),
                        pdfPageIndex = if (o.has("pdfPageIndex") && !o.isNull("pdfPageIndex")) o.getInt("pdfPageIndex") else null
                    )
                )
            }
        }
        root.optJSONArray("strokes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                strokeDao.upsert(
                    com.personal.inkpad.data.db.StrokeEntity(
                        id = o.getString("id"),
                        pageId = o.getString("pageId"),
                        pointsJson = o.getString("pointsJson"),
                        color = o.getLong("color"),
                        width = o.getDouble("width").toFloat(),
                        opacity = o.getDouble("opacity").toFloat(),
                        brushType = o.getString("brushType"),
                        createdAt = o.getLong("createdAt")
                    )
                )
            }
        }
        root.optJSONArray("texts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                textDao.upsert(
                    com.personal.inkpad.data.db.TextObjectEntity(
                        id = o.getString("id"),
                        pageId = o.getString("pageId"),
                        text = o.getString("text"),
                        x = o.getDouble("x").toFloat(),
                        y = o.getDouble("y").toFloat(),
                        width = o.getDouble("width").toFloat(),
                        height = o.getDouble("height").toFloat(),
                        fontSize = o.getDouble("fontSize").toFloat(),
                        color = o.getLong("color"),
                        bold = o.optBoolean("bold"),
                        italic = o.optBoolean("italic"),
                        underline = o.optBoolean("underline"),
                        alignment = o.optString("alignment", "START"),
                        scriptStyle = o.optBoolean("scriptStyle")
                    )
                )
            }
        }
        root.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                imageDao.upsert(
                    com.personal.inkpad.data.db.ImageObjectEntity(
                        id = o.getString("id"),
                        pageId = o.getString("pageId"),
                        path = o.getString("path"),
                        x = o.getDouble("x").toFloat(),
                        y = o.getDouble("y").toFloat(),
                        width = o.getDouble("width").toFloat(),
                        height = o.getDouble("height").toFloat(),
                        rotation = o.optDouble("rotation", 0.0).toFloat()
                    )
                )
            }
        }
        root.optJSONArray("shapes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                shapeDao.upsert(
                    com.personal.inkpad.data.db.ShapeObjectEntity(
                        id = o.getString("id"),
                        pageId = o.getString("pageId"),
                        shapeType = o.getString("shapeType"),
                        x = o.getDouble("x").toFloat(),
                        y = o.getDouble("y").toFloat(),
                        width = o.getDouble("width").toFloat(),
                        height = o.getDouble("height").toFloat(),
                        fillColor = o.getLong("fillColor"),
                        strokeColor = o.getLong("strokeColor"),
                        strokeWidth = o.getDouble("strokeWidth").toFloat(),
                        opacity = o.optDouble("opacity", 1.0).toFloat(),
                        rotation = o.optDouble("rotation", 0.0).toFloat()
                    )
                )
            }
        }
    }

    suspend fun exportNotebookPdf(notebookId: String): File = withContext(Dispatchers.IO) {
        val notebook = repo.getNotebook(notebookId) ?: error("Notebook not found")
        val pageList = repo.getDb().pageDao().getForNotebook(notebookId)
        val document = PdfDocument()

        pageList.forEachIndexed { index, page ->
            val pageInfo = PdfDocument.PageInfo.Builder(
                page.width.toInt().coerceAtLeast(1),
                page.height.toInt().coerceAtLeast(1),
                index + 1
            ).create()
            val pdfPage = document.startPage(pageInfo)
            val canvas = pdfPage.canvas
            val db = repo.getDb()
            val strokes = db.strokeDao().getForPage(page.id).map(StrokeJson::fromEntity)
            val shapes = db.shapeObjectDao().getForPage(page.id)
            val texts = db.textObjectDao().getForPage(page.id)
            val images = db.imageObjectDao().getForPage(page.id)
            com.personal.inkpad.engine.PageExportRenderer.draw(
                context = context,
                canvas = canvas,
                pageWidth = page.width,
                pageHeight = page.height,
                backgroundColor = page.backgroundColor,
                templateName = page.template,
                strokes = strokes,
                shapes = shapes,
                texts = texts,
                images = images,
                underlay = notebook.pdfPath?.let { path ->
                    { c ->
                        val pdfIndex = page.pdfPageIndex ?: index
                        renderPdfPage(path, pdfIndex, c, page.width, page.height)
                    }
                }
            )
            document.finishPage(pdfPage)
        }

        val out = File(repo.filesDir("exports"), "${sanitize(notebook.title)}-${System.currentTimeMillis()}.pdf")
        FileOutputStream(out).use { document.writeTo(it) }
        document.close()
        out
    }

    suspend fun exportPagePng(pageId: String): File = withContext(Dispatchers.IO) {
        val page = repo.getDb().pageDao().getById(pageId) ?: error("Page missing")
        val notebook = repo.getNotebook(page.notebookId)
        val db = repo.getDb()
        val strokes = db.strokeDao().getForPage(pageId).map(StrokeJson::fromEntity)
        val shapes = db.shapeObjectDao().getForPage(pageId)
        val texts = db.textObjectDao().getForPage(pageId)
        val images = db.imageObjectDao().getForPage(pageId)
        val bitmap = com.personal.inkpad.engine.PageExportRenderer.renderToBitmap(
            context = context,
            pageWidth = page.width,
            pageHeight = page.height,
            backgroundColor = page.backgroundColor,
            templateName = page.template,
            strokes = strokes,
            shapes = shapes,
            texts = texts,
            images = images,
            underlay = notebook?.pdfPath?.let { path ->
                { c ->
                    renderPdfPage(path, page.pdfPageIndex ?: page.pageIndex, c, page.width, page.height)
                }
            }
        )
        val out = File(repo.filesDir("exports"), "page-$pageId-${System.currentTimeMillis()}.png")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        out
    }

    fun pdfPageCount(path: String): Int {
        val file = File(path)
        if (!file.exists()) return 1
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                return renderer.pageCount.coerceAtLeast(1)
            }
        }
    }

    private fun renderPdfPage(path: String, index: Int, canvas: Canvas, width: Float, height: Float) {
        val file = File(path)
        if (!file.exists()) return
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (index !in 0 until renderer.pageCount) return
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        width.toInt().coerceAtLeast(1),
                        height.toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    bitmap.recycle()
                }
            }
        }
    }

    private fun sanitize(name: String) = name.replace(Regex("[^a-zA-Z0-9-_ ]"), "").ifBlank { "notebook" }
}

object ColorUtils {
    fun withOpacity(color: Int, opacity: Float): Int {
        val a = (opacity.coerceIn(0f, 1f) * 255).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}

private fun JSONObject.nullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key)
    return value.takeIf { it.isNotBlank() && it != "null" }
}
