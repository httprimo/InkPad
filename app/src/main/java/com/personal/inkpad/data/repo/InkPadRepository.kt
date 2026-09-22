package com.personal.inkpad.data.repo

import android.content.Context
import com.personal.inkpad.data.db.AppDatabase
import com.personal.inkpad.data.db.FolderEntity
import com.personal.inkpad.data.db.ImageObjectEntity
import com.personal.inkpad.data.db.NotebookEntity
import com.personal.inkpad.data.db.PageEntity
import com.personal.inkpad.data.db.ShapeObjectEntity
import com.personal.inkpad.data.db.StrokeJson
import com.personal.inkpad.data.db.TapeMaskEntity
import com.personal.inkpad.data.db.TextObjectEntity
import com.personal.inkpad.domain.model.BrushType
import com.personal.inkpad.domain.model.Orientation
import com.personal.inkpad.domain.model.PageSize
import com.personal.inkpad.domain.model.PaperTemplate
import com.personal.inkpad.domain.model.StrokeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class InkPadRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val folders = db.folderDao()
    private val notebooks = db.notebookDao()
    private val pages = db.pageDao()
    private val strokes = db.strokeDao()
    private val texts = db.textObjectDao()
    private val images = db.imageObjectDao()
    private val shapes = db.shapeObjectDao()
    private val tapes = db.tapeMaskDao()

    fun observeFolders(): Flow<List<FolderEntity>> = folders.observeAll()
    fun observeAllNotebooks(): Flow<List<NotebookEntity>> = notebooks.observeAll()
    fun observeUnfiled(): Flow<List<NotebookEntity>> = notebooks.observeUnfiled()
    fun observeInFolder(folderId: String): Flow<List<NotebookEntity>> = notebooks.observeInFolder(folderId)
    fun searchNotebooks(query: String): Flow<List<NotebookEntity>> = notebooks.search(query)
    fun observeNotebook(id: String): Flow<NotebookEntity?> = notebooks.observeById(id)
    fun observePages(notebookId: String): Flow<List<PageEntity>> = pages.observeForNotebook(notebookId)
    fun observeStrokes(pageId: String): Flow<List<StrokeData>> =
        strokes.observeForPage(pageId).map { list -> list.map(StrokeJson::fromEntity) }

    fun observeTexts(pageId: String) = texts.observeForPage(pageId)
    fun observeImages(pageId: String) = images.observeForPage(pageId)
    fun observeShapes(pageId: String) = shapes.observeForPage(pageId)
    fun observeTapes(pageId: String) = tapes.observeForPage(pageId)

    suspend fun createFolder(name: String, parentId: String? = null): FolderEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Folder" },
            parentId = parentId,
            createdAt = now,
            updatedAt = now
        )
        folders.upsert(folder)
        folder
    }

    suspend fun renameFolder(id: String, name: String) = withContext(Dispatchers.IO) {
        val existing = folders.getById(id) ?: return@withContext
        folders.update(existing.copy(name = name.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        val all = notebooks.getAll()
        all.filter { it.folderId == id }.forEach {
            notebooks.update(it.copy(folderId = null, updatedAt = System.currentTimeMillis()))
        }
        folders.deleteById(id)
    }

    suspend fun createNotebook(
        title: String,
        template: PaperTemplate,
        pageSize: PageSize,
        orientation: Orientation,
        coverColor: Long,
        backgroundColor: Long,
        folderId: String? = null,
        pdfPath: String? = null,
        pdfPageCount: Int = 1
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val landscape = orientation == Orientation.LANDSCAPE
        val (w, h) = pageSize.pixels(dpi = 160f, landscape = landscape)
        notebooks.upsert(
            NotebookEntity(
                id = id,
                title = title.trim().ifBlank { "Untitled" },
                coverColor = coverColor,
                folderId = folderId,
                paperTemplate = template.name,
                pageSize = pageSize.name,
                orientation = orientation.name,
                backgroundColor = backgroundColor,
                pdfPath = pdfPath,
                createdAt = now,
                updatedAt = now
            )
        )
        val count = pdfPageCount.coerceAtLeast(1)
        val pageList = (0 until count).map { index ->
            PageEntity(
                id = UUID.randomUUID().toString(),
                notebookId = id,
                pageIndex = index,
                width = w,
                height = h,
                backgroundColor = backgroundColor,
                template = template.name,
                pdfPageIndex = if (pdfPath != null) index else null
            )
        }
        pages.upsertAll(pageList)
        id
    }

    suspend fun renameNotebook(id: String, title: String) = withContext(Dispatchers.IO) {
        val n = notebooks.getById(id) ?: return@withContext
        notebooks.update(n.copy(title = title.trim().ifBlank { n.title }, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        val n = notebooks.getById(id) ?: return@withContext
        notebooks.update(n.copy(favorite = favorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun changeCover(id: String, color: Long) = withContext(Dispatchers.IO) {
        val n = notebooks.getById(id) ?: return@withContext
        notebooks.update(n.copy(coverColor = color, updatedAt = System.currentTimeMillis()))
    }

    suspend fun moveNotebook(id: String, folderId: String?) = withContext(Dispatchers.IO) {
        val n = notebooks.getById(id) ?: return@withContext
        notebooks.update(n.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicateNotebook(id: String): String? = withContext(Dispatchers.IO) {
        val source = notebooks.getById(id) ?: return@withContext null
        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        notebooks.upsert(
            source.copy(
                id = newId,
                title = "${source.title} (copy)",
                createdAt = now,
                updatedAt = now,
                thumbnailPath = null
            )
        )
        val sourcePages = pages.getForNotebook(id)
        sourcePages.forEach { page ->
            val newPageId = UUID.randomUUID().toString()
            pages.upsert(page.copy(id = newPageId, notebookId = newId))
            strokes.getForPage(page.id).forEach { s ->
                strokes.upsert(s.copy(id = UUID.randomUUID().toString(), pageId = newPageId))
            }
            texts.getForPage(page.id).forEach { t ->
                texts.upsert(t.copy(id = UUID.randomUUID().toString(), pageId = newPageId))
            }
            images.getForPage(page.id).forEach { img ->
                images.upsert(img.copy(id = UUID.randomUUID().toString(), pageId = newPageId))
            }
            shapes.getForPage(page.id).forEach { sh ->
                shapes.upsert(sh.copy(id = UUID.randomUUID().toString(), pageId = newPageId))
            }
        }
        newId
    }

    suspend fun deleteNotebook(id: String) = withContext(Dispatchers.IO) {
        notebooks.deleteById(id)
    }

    suspend fun touchNotebook(id: String) = withContext(Dispatchers.IO) {
        val n = notebooks.getById(id) ?: return@withContext
        notebooks.update(n.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun getNotebook(id: String) = withContext(Dispatchers.IO) { notebooks.getById(id) }

    suspend fun addPage(notebookId: String): PageEntity = withContext(Dispatchers.IO) {
        val notebook = notebooks.getById(notebookId) ?: error("Notebook missing")
        val size = runCatching { PageSize.valueOf(notebook.pageSize) }.getOrDefault(PageSize.A4)
        val orientation = runCatching { Orientation.valueOf(notebook.orientation) }.getOrDefault(Orientation.PORTRAIT)
        val (w, h) = size.pixels(landscape = orientation == Orientation.LANDSCAPE)
        val index = pages.maxIndex(notebookId) + 1
        val page = PageEntity(
            id = UUID.randomUUID().toString(),
            notebookId = notebookId,
            pageIndex = index,
            width = w,
            height = h,
            backgroundColor = notebook.backgroundColor,
            template = notebook.paperTemplate
        )
        pages.upsert(page)
        touchNotebook(notebookId)
        page
    }

    suspend fun duplicatePage(pageId: String): PageEntity? = withContext(Dispatchers.IO) {
        val page = pages.getById(pageId) ?: return@withContext null
        val newIndex = pages.maxIndex(page.notebookId) + 1
        val newPage = page.copy(id = UUID.randomUUID().toString(), pageIndex = newIndex)
        pages.upsert(newPage)
        strokes.getForPage(pageId).forEach {
            strokes.upsert(it.copy(id = UUID.randomUUID().toString(), pageId = newPage.id))
        }
        texts.getForPage(pageId).forEach {
            texts.upsert(it.copy(id = UUID.randomUUID().toString(), pageId = newPage.id))
        }
        images.getForPage(pageId).forEach {
            images.upsert(it.copy(id = UUID.randomUUID().toString(), pageId = newPage.id))
        }
        shapes.getForPage(pageId).forEach {
            shapes.upsert(it.copy(id = UUID.randomUUID().toString(), pageId = newPage.id))
        }
        touchNotebook(page.notebookId)
        newPage
    }

    suspend fun deletePage(pageId: String) = withContext(Dispatchers.IO) {
        val page = pages.getById(pageId) ?: return@withContext
        val remaining = pages.getForNotebook(page.notebookId).filter { it.id != pageId }
        if (remaining.isEmpty()) return@withContext
        pages.deleteById(pageId)
        remaining.sortedBy { it.pageIndex }.forEachIndexed { index, p ->
            pages.update(p.copy(pageIndex = index))
        }
        touchNotebook(page.notebookId)
    }

    suspend fun reorderPages(notebookId: String, orderedIds: List<String>) = withContext(Dispatchers.IO) {
        orderedIds.forEachIndexed { index, id ->
            val page = pages.getById(id) ?: return@forEachIndexed
            if (page.pageIndex != index) pages.update(page.copy(pageIndex = index))
        }
        touchNotebook(notebookId)
    }

    suspend fun saveStroke(pageId: String, stroke: StrokeData) = withContext(Dispatchers.IO) {
        strokes.upsert(StrokeJson.toEntity(pageId, stroke))
        val page = pages.getById(pageId) ?: return@withContext
        touchNotebook(page.notebookId)
    }

    suspend fun deleteStrokes(ids: List<String>, pageId: String) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        strokes.deleteByIds(ids)
        val page = pages.getById(pageId) ?: return@withContext
        touchNotebook(page.notebookId)
    }

    suspend fun replacePageStrokes(pageId: String, strokeList: List<StrokeData>) = withContext(Dispatchers.IO) {
        strokes.deleteForPage(pageId)
        strokes.upsertAll(strokeList.map { StrokeJson.toEntity(pageId, it) })
        val page = pages.getById(pageId) ?: return@withContext
        touchNotebook(page.notebookId)
    }

    suspend fun upsertText(obj: TextObjectEntity) = withContext(Dispatchers.IO) {
        texts.upsert(obj)
        pages.getById(obj.pageId)?.let { touchNotebook(it.notebookId) }
    }

    suspend fun deleteText(id: String) = withContext(Dispatchers.IO) { texts.deleteById(id) }

    suspend fun upsertImage(obj: ImageObjectEntity) = withContext(Dispatchers.IO) {
        images.upsert(obj)
        pages.getById(obj.pageId)?.let { touchNotebook(it.notebookId) }
    }

    suspend fun deleteImage(id: String) = withContext(Dispatchers.IO) { images.deleteById(id) }

    suspend fun upsertShape(obj: ShapeObjectEntity) = withContext(Dispatchers.IO) {
        shapes.upsert(obj)
        pages.getById(obj.pageId)?.let { touchNotebook(it.notebookId) }
    }

    suspend fun deleteShape(id: String) = withContext(Dispatchers.IO) { shapes.deleteById(id) }

    suspend fun upsertTape(mask: TapeMaskEntity) = withContext(Dispatchers.IO) {
        tapes.upsert(mask)
    }

    suspend fun deleteTape(id: String) = withContext(Dispatchers.IO) { tapes.deleteById(id) }

    suspend fun revealAllTape(pageId: String) = withContext(Dispatchers.IO) { tapes.revealAll(pageId) }

    fun filesDir(subdir: String): File {
        val dir = File(context.filesDir, subdir)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun copyUriToImages(uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val dest = File(filesDir("images"), "${UUID.randomUUID()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read image")
        dest.absolutePath
    }

    suspend fun copyUriToPdf(uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val dest = File(filesDir("pdf"), "${UUID.randomUUID()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read PDF")
        dest.absolutePath
    }

    fun getDb() = db
}
