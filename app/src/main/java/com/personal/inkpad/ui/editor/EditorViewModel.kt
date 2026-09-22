package com.personal.inkpad.ui.editor

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.inkpad.InkPadApp
import com.personal.inkpad.data.db.ImageObjectEntity
import com.personal.inkpad.data.db.NotebookEntity
import com.personal.inkpad.data.db.PageEntity
import com.personal.inkpad.data.db.ShapeObjectEntity
import com.personal.inkpad.data.db.StrokeJson
import com.personal.inkpad.data.db.TapeMaskEntity
import com.personal.inkpad.data.db.TextObjectEntity
import com.personal.inkpad.domain.model.BrushType
import com.personal.inkpad.domain.model.EditorTool
import com.personal.inkpad.domain.model.PaperTemplate
import com.personal.inkpad.domain.model.ShapeType
import com.personal.inkpad.domain.model.StrokeData
import com.personal.inkpad.engine.HandwritingBeautifier
import com.personal.inkpad.engine.ShapeRecognizer
import com.personal.inkpad.engine.StrokeEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class EditorUiState(
    val notebook: NotebookEntity? = null,
    val pages: List<PageEntity> = emptyList(),
    val currentPageIndex: Int = 0,
    val tool: EditorTool = EditorTool.PEN,
    val brushType: BrushType = BrushType.BALLPOINT,
    val penColor: Long = 0xFF1B1B1B,
    val penWidth: Float = 3.5f,
    val highlighterColor: Long = 0xFFFFFF00,
    val showPageRail: Boolean = true,
    val zoomLabel: String = "Fit",
    val message: String? = null,
    val strokeVersion: Int = 0,
    val pencraftEnabled: Boolean = true,
    /** When on, closed freehand strokes may snap to shapes. Off by default so writing stays ink. */
    val shapeSnapEnabled: Boolean = false,
    /** Freenotes real-time handwriting → script font. */
    val handwritingBeautifyEnabled: Boolean = false,
    val pendingShareUri: String? = null,
    val pendingShareMime: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = InkPadApp.instance.repository
    private val backup = InkPadApp.instance.backupExport

    val engine = StrokeEngine()
    private val beautifier = HandwritingBeautifier(application)

    private val notebookId = MutableStateFlow<String?>(null)
    private val ui = MutableStateFlow(EditorUiState())
    private var persistJob: Job? = null
    private var loadedPageId: String? = null
    private var pageWatcher: Job? = null
    private val beautifyStrokeIds = mutableListOf<String>()
    private var beautifyJob: Job? = null

    val texts = MutableStateFlow<List<TextObjectEntity>>(emptyList())
    val images = MutableStateFlow<List<ImageObjectEntity>>(emptyList())
    val shapes = MutableStateFlow<List<ShapeObjectEntity>>(emptyList())
    val tapes = MutableStateFlow<List<TapeMaskEntity>>(emptyList())
    val pdfBitmap = MutableStateFlow<ImageBitmap?>(null)
    val selectedStrokeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedShapeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedImageIds = MutableStateFlow<Set<String>>(emptySet())

    fun clearObjectSelection() {
        selectedShapeIds.value = emptySet()
        selectedImageIds.value = emptySet()
    }

    fun shapeAt(x: Float, y: Float): ShapeObjectEntity? =
        shapes.value.asReversed().firstOrNull { shape ->
            // Pad thin lines/arrows so they stay selectable
            val pad = maxOf(16f, shape.strokeWidth * 4f)
            val h = shape.height.coerceAtLeast(1f)
            val top = if (h <= 8f) shape.y - pad else shape.y
            val bottom = if (h <= 8f) shape.y + h + pad else shape.y + h
            x in (shape.x - 4f)..(shape.x + shape.width + 4f) && y in top..bottom
        }

    fun imageAt(x: Float, y: Float): ImageObjectEntity? =
        images.value.asReversed().firstOrNull { img ->
            x in img.x..(img.x + img.width) && y in img.y..(img.y + img.height)
        }

    fun selectShape(id: String?) {
        selectedShapeIds.value = if (id == null) emptySet() else setOf(id)
        if (id != null) {
            selectedImageIds.value = emptySet()
            selectedStrokeIds.value = emptySet()
        }
    }

    fun selectImage(id: String?) {
        selectedImageIds.value = if (id == null) emptySet() else setOf(id)
        if (id != null) {
            selectedShapeIds.value = emptySet()
            selectedStrokeIds.value = emptySet()
        }
    }

    fun deleteShape(id: String) = viewModelScope.launch {
        repo.deleteShape(id)
        shapes.value = shapes.value.filterNot { it.id == id }
        selectedShapeIds.value = selectedShapeIds.value - id
    }

    fun deleteImage(id: String) = viewModelScope.launch {
        repo.deleteImage(id)
        images.value = images.value.filterNot { it.id == id }
        selectedImageIds.value = selectedImageIds.value - id
    }

    fun moveSelectedShape(id: String, dx: Float, dy: Float) = viewModelScope.launch {
        val current = shapes.value.firstOrNull { it.id == id } ?: return@launch
        val page = currentPage()
        // Allow free drag with a soft edge pad so objects stay reachable
        val updated = current.copy(
            x = (current.x + dx).let {
                if (page != null) it.coerceIn(-current.width * 0.85f, page.width - current.width * 0.15f) else it
            },
            y = (current.y + dy).let {
                if (page != null) it.coerceIn(-current.height * 0.85f, page.height - current.height * 0.15f) else it
            }
        )
        repo.upsertShape(updated)
        shapes.value = shapes.value.map { if (it.id == id) updated else it }
    }

    fun moveSelectedImage(id: String, dx: Float, dy: Float) = viewModelScope.launch {
        val current = images.value.firstOrNull { it.id == id } ?: return@launch
        val updated = current.copy(x = current.x + dx, y = current.y + dy)
        repo.upsertImage(updated)
        images.value = images.value.map { if (it.id == id) updated else it }
    }

    fun resizeShape(id: String, width: Float, height: Float) = viewModelScope.launch {
        val current = shapes.value.firstOrNull { it.id == id } ?: return@launch
        val w = width.coerceAtLeast(24f)
        val h = if (current.shapeType == ShapeType.CIRCLE.name) w else height.coerceAtLeast(24f)
        val updated = current.copy(width = w, height = h)
        repo.upsertShape(updated)
        shapes.value = shapes.value.map { if (it.id == id) updated else it }
    }

    fun resizeImage(id: String, width: Float, height: Float) = viewModelScope.launch {
        val current = images.value.firstOrNull { it.id == id } ?: return@launch
        val updated = current.copy(width = width.coerceAtLeast(40f), height = height.coerceAtLeast(40f))
        repo.upsertImage(updated)
        images.value = images.value.map { if (it.id == id) updated else it }
    }

    fun addRecognizedShape(result: ShapeRecognizer.Result) = viewModelScope.launch {
        val page = currentPage() ?: return@launch
        val obj = ShapeObjectEntity(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            shapeType = result.type.name,
            x = result.x.coerceIn(0f, page.width - 8f),
            y = result.y.coerceIn(0f, page.height - 8f),
            width = result.width.coerceAtMost(page.width),
            height = result.height.coerceAtMost(page.height),
            fillColor = 0x222F80ED,
            strokeColor = ui.value.penColor,
            strokeWidth = ui.value.penWidth.coerceAtLeast(2f)
        )
        repo.upsertShape(obj)
        shapes.value = shapes.value + obj
        selectShape(obj.id)
        setTool(EditorTool.SELECT)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<EditorUiState> = combine(
        notebookId.filterNotNull().flatMapLatest { repo.observeNotebook(it) },
        notebookId.filterNotNull().flatMapLatest { repo.observePages(it) },
        ui
    ) { notebook, pages, local ->
        local.copy(
            notebook = notebook,
            pages = pages,
            currentPageIndex = local.currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    fun init(id: String) {
        if (notebookId.value == id) return
        notebookId.value = id
        ui.update { it.copy(currentPageIndex = 0) }
        pageWatcher?.cancel()
        pageWatcher = viewModelScope.launch {
            repo.observePages(id).collect { pages ->
                val idx = ui.value.currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                pages.getOrNull(idx)?.let { page ->
                    if (loadedPageId != page.id) loadPageContent(page)
                }
            }
        }
    }

    private suspend fun loadPageContent(page: PageEntity) {
        loadedPageId = page.id
        engine.load(repo.getDb().strokeDao().getForPage(page.id).map(StrokeJson::fromEntity))
        bumpStrokes()
        texts.value = repo.getDb().textObjectDao().getForPage(page.id)
        images.value = repo.getDb().imageObjectDao().getForPage(page.id)
        shapes.value = repo.getDb().shapeObjectDao().getForPage(page.id)
        tapes.value = repo.getDb().tapeMaskDao().getForPage(page.id)
        loadPdfPreview(page)
        applyToolSettings()
    }

    private fun loadPdfPreview(page: PageEntity) {
        viewModelScope.launch {
            val notebook = repo.getNotebook(page.notebookId)
            val path = notebook?.pdfPath
            if (path == null || !File(path).exists()) {
                pdfBitmap.value = null
                return@launch
            }
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val index = page.pdfPageIndex ?: page.pageIndex
                    if (index !in 0 until renderer.pageCount) {
                        pdfBitmap.value = null
                        return@use
                    }
                    renderer.openPage(index).use { pdfPage ->
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            page.width.toInt().coerceAtLeast(1),
                            page.height.toInt().coerceAtLeast(1),
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pdfBitmap.value = bitmap.asImageBitmap()
                    }
                }
            }
        }
    }

    fun currentPage(): PageEntity? = state.value.pages.getOrNull(state.value.currentPageIndex)

    fun setPageIndex(index: Int) {
        val pages = state.value.pages
        if (pages.isEmpty()) return
        val coerced = index.coerceIn(0, pages.lastIndex)
        ui.update { it.copy(currentPageIndex = coerced) }
        viewModelScope.launch { pages.getOrNull(coerced)?.let { loadPageContent(it) } }
    }

    fun setTool(value: EditorTool) {
        ui.update { it.copy(tool = value) }
        applyToolSettings()
    }

    fun setBrush(value: BrushType) {
        ui.update { it.copy(brushType = value) }
        applyToolSettings()
    }

    fun setPenColor(value: Long) {
        ui.update { it.copy(penColor = value) }
        applyToolSettings()
    }

    fun setPenWidth(value: Float) {
        ui.update { it.copy(penWidth = value) }
        applyToolSettings()
    }

    fun setHighlighterColor(value: Long) {
        ui.update { it.copy(highlighterColor = value) }
        applyToolSettings()
    }

    fun togglePencraft() {
        ui.update { it.copy(pencraftEnabled = !it.pencraftEnabled) }
        engine.pencraftEnabled = ui.value.pencraftEnabled
    }

    fun toggleShapeSnap() {
        ui.update {
            val next = !it.shapeSnapEnabled
            it.copy(
                shapeSnapEnabled = next,
                handwritingBeautifyEnabled = if (next) false else it.handwritingBeautifyEnabled,
                message = if (next) "Shape snap on" else "Shape snap off"
            )
        }
        engine.shapeSnapEnabled = ui.value.shapeSnapEnabled
        engine.beautifyEnabled = ui.value.handwritingBeautifyEnabled
    }

    fun toggleHandwritingBeautify() {
        ui.update {
            val next = !it.handwritingBeautifyEnabled
            it.copy(
                handwritingBeautifyEnabled = next,
                shapeSnapEnabled = if (next) false else it.shapeSnapEnabled,
                message = if (next) {
                    "Real-time handwriting beautification"
                } else {
                    "Handwriting beautification off"
                }
            )
        }
        engine.beautifyEnabled = ui.value.handwritingBeautifyEnabled
        engine.shapeSnapEnabled = ui.value.shapeSnapEnabled
        if (ui.value.handwritingBeautifyEnabled) {
            viewModelScope.launch {
                val ok = beautifier.ensureModel()
                if (!ok) {
                    ui.update { it.copy(message = "Couldn’t download handwriting model — check network") }
                }
            }
        } else {
            beautifyJob?.cancel()
            beautifyStrokeIds.clear()
        }
    }

    fun setZoomLabel(label: String) = ui.update { it.copy(zoomLabel = label) }

    fun togglePageRail() = ui.update { it.copy(showPageRail = !it.showPageRail) }

    private fun applyToolSettings() {
        val s = ui.value
        engine.pencraftEnabled = s.pencraftEnabled
        engine.shapeSnapEnabled = s.shapeSnapEnabled
        engine.beautifyEnabled = s.handwritingBeautifyEnabled
        when (s.tool) {
            EditorTool.HIGHLIGHTER -> {
                engine.brushType = BrushType.HIGHLIGHTER
                engine.penColor = s.highlighterColor
                engine.penWidth = s.penWidth
                engine.penOpacity = 0.35f
            }
            EditorTool.ERASER -> engine.brushType = BrushType.ERASER
            else -> {
                engine.brushType = s.brushType
                engine.penColor = s.penColor
                engine.penWidth = s.penWidth
                engine.penOpacity = 1f
            }
        }
    }

    fun onStrokeFinished(stroke: StrokeData?) {
        val page = currentPage()
        if (
            stroke != null &&
            page != null &&
            ui.value.tool == EditorTool.PEN &&
            ui.value.shapeSnapEnabled
        ) {
            val recognized = engine.consumeLastShapeResult()
                ?: ShapeRecognizer.recognize(stroke.points)
            if (recognized != null) {
                engine.deleteStrokes(setOf(stroke.id))
                bumpStrokes()
                addRecognizedShape(recognized)
                schedulePersist(page.id)
                return
            }
        } else {
            engine.consumeLastShapeResult()
        }

        if (
            stroke != null &&
            page != null &&
            ui.value.tool == EditorTool.PEN &&
            ui.value.handwritingBeautifyEnabled
        ) {
            beautifyStrokeIds.add(stroke.id)
            bumpStrokes()
            schedulePersist(page.id)
            beautifyJob?.cancel()
            beautifyJob = viewModelScope.launch {
                delay(700)
                commitBeautifyBuffer()
            }
            return
        }

        bumpStrokes()
        if (stroke != null && page != null) schedulePersist(page.id)
    }

    private suspend fun commitBeautifyBuffer() {
        val page = currentPage() ?: return
        val ids = beautifyStrokeIds.toList()
        beautifyStrokeIds.clear()
        if (ids.isEmpty()) return
        val strokes = engine.strokes.filter { it.id in ids }
        if (strokes.isEmpty()) return
        val result = beautifier.recognize(strokes) ?: return
        engine.deleteStrokes(ids.toSet())
        bumpStrokes()
        val obj = TextObjectEntity(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            text = result.text,
            x = result.x.coerceIn(0f, page.width - 8f),
            y = result.y.coerceIn(0f, page.height - 8f),
            width = result.width.coerceAtMost(page.width),
            height = result.height.coerceAtMost(page.height),
            fontSize = result.fontSize,
            color = ui.value.penColor,
            scriptStyle = true
        )
        repo.upsertText(obj)
        texts.value = texts.value + obj
        schedulePersist(page.id)
    }

    fun onErase() {
        bumpStrokes()
        currentPage()?.let { schedulePersist(it.id) }
    }

    fun undo() {
        if (engine.undo()) {
            bumpStrokes()
            currentPage()?.let { schedulePersist(it.id) }
        }
    }

    fun redo() {
        if (engine.redo()) {
            bumpStrokes()
            currentPage()?.let { schedulePersist(it.id) }
        }
    }

    private fun schedulePersist(pageId: String) {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(250)
            repo.replacePageStrokes(pageId, engine.strokes)
        }
    }

    /** Write pending ink to DB immediately so export sees the latest page. */
    private suspend fun flushStrokes() {
        persistJob?.cancel()
        persistJob = null
        val page = currentPage() ?: return
        repo.replacePageStrokes(page.id, engine.strokes)
    }

    fun addPage() = viewModelScope.launch {
        val id = notebookId.value ?: return@launch
        repo.addPage(id)
        delay(80)
        val last = state.value.pages.lastIndex.coerceAtLeast(0)
        setPageIndex(last)
    }

    fun duplicatePage() = viewModelScope.launch {
        currentPage()?.let { repo.duplicatePage(it.id) }
    }

    fun deletePage() = viewModelScope.launch {
        val page = currentPage() ?: return@launch
        if (state.value.pages.size <= 1) {
            ui.update { it.copy(message = "Keep at least one page") }
            return@launch
        }
        val newIndex = (ui.value.currentPageIndex - 1).coerceAtLeast(0)
        repo.deletePage(page.id)
        ui.update { it.copy(currentPageIndex = newIndex) }
        loadedPageId = null
    }

    fun addText(text: String, x: Float, y: Float) = viewModelScope.launch {
        val page = currentPage() ?: return@launch
        val obj = TextObjectEntity(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            text = text,
            x = x,
            y = y,
            width = 280f,
            height = 80f,
            fontSize = 22f,
            color = 0xFF1B1B1B
        )
        repo.upsertText(obj)
        texts.value = texts.value + obj
    }

    fun addShape(type: ShapeType, x: Float, y: Float) = viewModelScope.launch {
        val page = currentPage() ?: return@launch
        val (w, h) = when (type) {
            ShapeType.TRIANGLE, ShapeType.CIRCLE -> 140f to 140f
            ShapeType.ELLIPSE -> 180f to 110f
            // Keep a hit target tall enough to select/delete
            ShapeType.LINE, ShapeType.ARROW -> 180f to 28f
            else -> 160f to 100f
        }
        val obj = ShapeObjectEntity(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            shapeType = type.name,
            x = x,
            y = y,
            width = w,
            height = h,
            fillColor = 0x332A9D8F,
            strokeColor = 0xFF1B3A4B,
            strokeWidth = 3f
        )
        repo.upsertShape(obj)
        shapes.value = shapes.value + obj
        selectShape(obj.id)
        setTool(EditorTool.SELECT)
    }

    fun addImage(uri: Uri) = viewModelScope.launch {
        val page = currentPage() ?: return@launch
        val path = repo.copyUriToImages(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val srcW = bounds.outWidth.takeIf { it > 0 } ?: 400
        val srcH = bounds.outHeight.takeIf { it > 0 } ?: 300
        val w = srcW.toFloat().coerceAtMost(page.width * 0.6f)
        val h = srcH * (w / srcW)
        val obj = ImageObjectEntity(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            path = path,
            x = 80f,
            y = 120f,
            width = w,
            height = h
        )
        repo.upsertImage(obj)
        images.value = images.value + obj
        selectImage(obj.id)
        setTool(EditorTool.SELECT)
    }

    fun addTape(x: Float, y: Float) = viewModelScope.launch {
        val page = currentPage() ?: return@launch
        val mask = TapeMaskEntity(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            x = x,
            y = y,
            width = 180f,
            height = 36f,
            revealed = false
        )
        repo.upsertTape(mask)
        tapes.value = tapes.value + mask
    }

    fun toggleTape(id: String) = viewModelScope.launch {
        val current = tapes.value.firstOrNull { it.id == id } ?: return@launch
        val updated = current.copy(revealed = !current.revealed)
        repo.upsertTape(updated)
        tapes.value = tapes.value.map { if (it.id == id) updated else it }
    }

    fun revealAllTape() = viewModelScope.launch {
        val page = currentPage() ?: return@launch
        repo.revealAllTape(page.id)
        tapes.value = tapes.value.map { it.copy(revealed = true) }
    }

    fun exportPdf() = viewModelScope.launch {
        val id = notebookId.value ?: return@launch
        try {
            flushStrokes()
            val file = backup.exportNotebookPdf(id)
            val published = com.personal.inkpad.data.repo.PublicExportWriter.publish(
                getApplication(),
                file,
                "application/pdf",
                file.name
            )
            ui.update {
                it.copy(
                    message = "Saved to ${published.locationHint}",
                    pendingShareUri = published.uri.toString(),
                    pendingShareMime = published.mimeType
                )
            }
        } catch (e: Exception) {
            ui.update { it.copy(message = e.message ?: "Export failed") }
        }
    }

    fun exportPng() = viewModelScope.launch {
        val page = currentPage() ?: return@launch
        try {
            flushStrokes()
            val file = backup.exportPagePng(page.id)
            val published = com.personal.inkpad.data.repo.PublicExportWriter.publish(
                getApplication(),
                file,
                "image/png",
                file.name
            )
            ui.update {
                it.copy(
                    message = "Saved to ${published.locationHint}",
                    pendingShareUri = published.uri.toString(),
                    pendingShareMime = published.mimeType
                )
            }
        } catch (e: Exception) {
            ui.update { it.copy(message = e.message ?: "Export failed") }
        }
    }

    fun clearPendingShare() = ui.update {
        it.copy(pendingShareUri = null, pendingShareMime = null)
    }

    fun clearMessage() = ui.update { it.copy(message = null) }

    fun templateOf(notebook: NotebookEntity?): PaperTemplate =
        runCatching { PaperTemplate.valueOf(notebook?.paperTemplate ?: "BLANK") }
            .getOrDefault(PaperTemplate.BLANK)

    fun bumpStrokes() = ui.update { it.copy(strokeVersion = it.strokeVersion + 1) }

    fun moveSelected(dx: Float, dy: Float) {
        val ids = selectedStrokeIds.value
        if (ids.isEmpty()) return
        engine.moveStrokes(ids, dx, dy)
        bumpStrokes()
        currentPage()?.let { schedulePersist(it.id) }
    }

    fun deleteSelected() {
        val strokeIds = selectedStrokeIds.value
        if (strokeIds.isNotEmpty()) {
            engine.deleteStrokes(strokeIds)
            selectedStrokeIds.value = emptySet()
            bumpStrokes()
            currentPage()?.let { schedulePersist(it.id) }
        }
        val shapeIds = selectedShapeIds.value.toList()
        val imageIds = selectedImageIds.value.toList()
        if (shapeIds.isNotEmpty() || imageIds.isNotEmpty()) {
            viewModelScope.launch {
                shapeIds.forEach { repo.deleteShape(it) }
                imageIds.forEach { repo.deleteImage(it) }
                if (shapeIds.isNotEmpty()) {
                    shapes.value = shapes.value.filterNot { it.id in shapeIds.toSet() }
                    selectedShapeIds.value = emptySet()
                }
                if (imageIds.isNotEmpty()) {
                    images.value = images.value.filterNot { it.id in imageIds.toSet() }
                    selectedImageIds.value = emptySet()
                }
            }
        }
    }
}
