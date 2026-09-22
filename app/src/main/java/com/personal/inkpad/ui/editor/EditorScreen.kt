package com.personal.inkpad.ui.editor

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.personal.inkpad.R
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.inkpad.domain.model.BrushType
import com.personal.inkpad.domain.model.EditorTool
import com.personal.inkpad.domain.model.ShapeType
import com.personal.inkpad.domain.model.StrokeData
import com.personal.inkpad.engine.PalmRejection.isFinger
import com.personal.inkpad.engine.PalmRejection.isInkPointer
import com.personal.inkpad.engine.PaperBackground
import com.personal.inkpad.engine.drawStrokeData
import com.personal.inkpad.engine.fitScale
import com.personal.inkpad.engine.zoomPresets
import com.personal.inkpad.ui.theme.CoverPalette
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun EditorScreen(
    notebookId: String,
    onBack: () -> Unit,
    vm: EditorViewModel = viewModel()
) {
    LaunchedEffect(notebookId) { vm.init(notebookId) }
    val state by vm.state.collectAsState()
    val texts by vm.texts.collectAsState()
    val images by vm.images.collectAsState()
    val shapes by vm.shapes.collectAsState()
    val tapes by vm.tapes.collectAsState()
    val pdfBitmap by vm.pdfBitmap.collectAsState()
    val selected by vm.selectedStrokeIds.collectAsState()
    val selectedShapes by vm.selectedShapeIds.collectAsState()
    val selectedImages by vm.selectedImageIds.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fitApplied by remember { mutableStateOf(false) }
    var drawTick by remember { mutableIntStateOf(0) }
    var showZoomMenu by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showShapeMenu by remember { mutableStateOf(false) }
    var lassoPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val page = state.pages.getOrNull(state.currentPageIndex)
    val pageWidth = page?.width ?: 794f
    val pageHeight = page?.height ?: 1123f
    val pageId = page?.id

    val toolState = rememberUpdatedState(state.tool)
    val selectedState = rememberUpdatedState(selected)
    val penColorState = rememberUpdatedState(state.penColor)
    val penWidthState = rememberUpdatedState(state.penWidth)
    val highlighterState = rememberUpdatedState(state.highlighterColor)
    val brushState = rememberUpdatedState(state.brushType)
    val scaleState = rememberUpdatedState(scale)
    val offsetState = rememberUpdatedState(offset)

    fun invalidate() {
        drawTick++
    }

    fun applyZoom(newScale: Float, anchor: Offset = Offset(containerSize.width / 2f, containerSize.height / 2f)) {
        // Freenotes-style close-up: allow ~12x (1200%)
        val clamped = newScale.coerceIn(0.15f, 12f)
        val pagePoint = Offset(
            (anchor.x - offset.x) / scale.coerceAtLeast(0.01f),
            (anchor.y - offset.y) / scale.coerceAtLeast(0.01f)
        )
        scale = clamped
        offset = Offset(anchor.x - pagePoint.x * clamped, anchor.y - pagePoint.y * clamped)
    }

    fun fitPage() {
        if (containerSize.width == 0) return
        scale = fitScale(containerSize, pageWidth, pageHeight)
        offset = Offset(
            (containerSize.width - pageWidth * scale) / 2f,
            (containerSize.height - pageHeight * scale) / 2f
        )
        vm.setZoomLabel("Fit")
        fitApplied = true
    }

    LaunchedEffect(containerSize, pageId) {
        if (containerSize.width > 0 && (!fitApplied || pageId != null)) {
            if (!fitApplied) fitPage()
        }
    }

    LaunchedEffect(state.strokeVersion) { invalidate() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    LaunchedEffect(state.pendingShareUri) {
        val uriString = state.pendingShareUri ?: return@LaunchedEffect
        val mime = state.pendingShareMime ?: "*/*"
        val uri = Uri.parse(uriString)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "Open export"))
        }
        vm.clearPendingShare()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addImage(uri)
    }

    var showAddPage by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFFF2F4F7),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                title = { Text(state.notebook?.title ?: "Notebook", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = vm::togglePageRail) {
                        Icon(Icons.Default.GridView, contentDescription = "Thumbnails")
                    }
                    IconButton(onClick = vm::undo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = vm::redo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    Box {
                        IconButton(onClick = { showAddPage = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add page")
                        }
                        DropdownMenu(showAddPage, { showAddPage = false }) {
                            DropdownMenuItem(
                                text = { Text("Blank page") },
                                onClick = {
                                    showAddPage = false
                                    fitApplied = false
                                    vm.addPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate page") },
                                onClick = {
                                    showAddPage = false
                                    vm.duplicatePage()
                                }
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(showMore, { showMore = false }) {
                            DropdownMenuItem(text = { Text("Export PDF") }, onClick = { showMore = false; vm.exportPdf() })
                            DropdownMenuItem(text = { Text("Export PNG") }, onClick = { showMore = false; vm.exportPng() })
                            DropdownMenuItem(text = { Text("Reveal all tape") }, onClick = { showMore = false; vm.revealAllTape() })
                            DropdownMenuItem(text = { Text("Delete selection") }, onClick = { showMore = false; vm.deleteSelected() })
                            DropdownMenuItem(text = { Text("Delete page") }, onClick = { showMore = false; vm.deletePage() })
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFE8ECF1))
                    .onSizeChanged { containerSize = it }
            ) {
                PaperBackground(
                        modifier = Modifier.fillMaxSize(),
                        template = vm.templateOf(state.notebook),
                        backgroundColor = Color((page?.backgroundColor ?: 0xFFFFFFFF).toInt()),
                        pageWidth = pageWidth,
                        pageHeight = pageHeight,
                        scale = scale,
                        offset = offset
                    )

                    val textMeasurer = rememberTextMeasurer()
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            // IMPORTANT: do not key on scale/offset/strokeVersion — that cancels writing mid-stroke
                            .pointerInput(toolState.value, pageId) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                                    val tool = toolState.value
                                    val isEraserTool = tool == EditorTool.ERASER
                                    val writing = tool == EditorTool.PEN ||
                                        tool == EditorTool.HIGHLIGHTER ||
                                        isEraserTool
                                    val strokeUsesPenHardware = down.isInkPointer()

                                    fun toPage(pos: Offset): Offset {
                                        val s = scaleState.value.coerceAtLeast(0.01f)
                                        val o = offsetState.value
                                        return Offset((pos.x - o.x) / s, (pos.y - o.y) / s)
                                    }
                                    fun toPageClipped(pos: Offset): Offset {
                                        val p = toPage(pos)
                                        return Offset(
                                            p.x.coerceIn(0f, pageWidth),
                                            p.y.coerceIn(0f, pageHeight)
                                        )
                                    }

                                    fun handlePinch(pressed: List<androidx.compose.ui.input.pointer.PointerInputChange>) {
                                        if (pressed.size < 2) return
                                        val c0 = pressed[0]
                                        val c1 = pressed[1]
                                        val pan = ((c0.position - c0.previousPosition) +
                                            (c1.position - c1.previousPosition)) / 2f
                                        val prevDist = hypot(
                                            (c0.previousPosition.x - c1.previousPosition.x).toDouble(),
                                            (c0.previousPosition.y - c1.previousPosition.y).toDouble()
                                        ).toFloat().coerceAtLeast(1f)
                                        val dist = hypot(
                                            (c0.position.x - c1.position.x).toDouble(),
                                            (c0.position.y - c1.position.y).toDouble()
                                        ).toFloat().coerceAtLeast(1f)
                                        val zoom = (dist / prevDist).coerceIn(0.8f, 1.25f)
                                        val centroid = (c0.position + c1.position) / 2f
                                        applyZoom(scaleState.value * zoom, centroid)
                                        offset = offsetState.value + pan
                                        vm.setZoomLabel("${(scaleState.value * 100).toInt()}%")
                                    }

                                    if (tool == EditorTool.LASSO) {
                                        val pts = mutableListOf(toPage(down.position))
                                        lassoPoints = pts.toList()
                                        do {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.count { it.pressed }
                                            if (pressed >= 2) {
                                                val c = event.changes.filter { it.pressed }
                                                if (c.size >= 2) {
                                                    val pan = ((c[0].position - c[0].previousPosition) +
                                                        (c[1].position - c[1].previousPosition)) / 2f
                                                    offset += pan
                                                }
                                                event.changes.forEach { it.consume() }
                                            } else {
                                                event.changes.forEach { change ->
                                                    if (change.pressed) {
                                                        pts.add(toPage(change.position))
                                                        lassoPoints = pts.toList()
                                                        invalidate()
                                                        change.consume()
                                                    }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                        vm.selectedStrokeIds.value = vm.engine.strokesInLasso(pts)
                                        lassoPoints = emptyList()
                                        invalidate()
                                        return@awaitEachGesture
                                    }

                                    if (tool == EditorTool.TAPE) {
                                        val p = toPage(down.position)
                                        vm.addTape(p.x, p.y)
                                        return@awaitEachGesture
                                    }

                                    if (tool == EditorTool.SELECT) {
                                        val pagePoint = toPage(down.position)
                                        val handlePad = 28f / scaleState.value.coerceAtLeast(0.2f)

                                        // Delete button on currently selected shape/image
                                        val selShape = vm.shapes.value.firstOrNull { it.id in vm.selectedShapeIds.value }
                                        val selImage = vm.images.value.firstOrNull { it.id in vm.selectedImageIds.value }
                                        val selBounds = when {
                                            selShape != null ->
                                                SelectionBounds(selShape.x, selShape.y, selShape.width, selShape.height.coerceAtLeast(24f))
                                            selImage != null ->
                                                SelectionBounds(selImage.x, selImage.y, selImage.width, selImage.height)
                                            else -> null
                                        }
                                        if (selBounds != null) {
                                            val del = selBounds.deleteCenter()
                                            if (hypot(pagePoint.x - del.x, pagePoint.y - del.y) <= handlePad) {
                                                selShape?.let { vm.deleteShape(it.id) }
                                                selImage?.let { vm.deleteImage(it.id) }
                                                invalidate()
                                                return@awaitEachGesture
                                            }
                                            val resize = selBounds.resizeCenter()
                                            if (hypot(pagePoint.x - resize.x, pagePoint.y - resize.y) <= handlePad) {
                                                do {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull() ?: break
                                                    if (change.pressed) {
                                                        val p = toPage(change.position)
                                                        val newW = (p.x - selBounds.x).coerceAtLeast(24f)
                                                        val newH = (p.y - selBounds.y).coerceAtLeast(24f)
                                                        selShape?.let { vm.resizeShape(it.id, newW, newH) }
                                                        selImage?.let { vm.resizeImage(it.id, newW, newH) }
                                                        invalidate()
                                                        change.consume()
                                                    }
                                                } while (event.changes.any { it.pressed })
                                                return@awaitEachGesture
                                            }
                                        }

                                        val hitImage = vm.imageAt(pagePoint.x, pagePoint.y)
                                        if (hitImage != null) {
                                            vm.selectImage(hitImage.id)
                                            var last = down.position
                                            do {
                                                val event = awaitPointerEvent()
                                                val pressed = event.changes.filter { it.pressed }
                                                if (pressed.size >= 2) {
                                                    handlePinch(pressed)
                                                    event.changes.forEach { it.consume() }
                                                    continue
                                                }
                                                val change = event.changes.firstOrNull() ?: break
                                                if (change.pressed) {
                                                    val s = scaleState.value.coerceAtLeast(0.01f)
                                                    val delta = (change.position - last) / s
                                                    vm.moveSelectedImage(hitImage.id, delta.x, delta.y)
                                                    last = change.position
                                                    invalidate()
                                                    change.consume()
                                                }
                                            } while (event.changes.any { it.pressed })
                                            return@awaitEachGesture
                                        }

                                        val hitShape = vm.shapeAt(pagePoint.x, pagePoint.y)
                                        if (hitShape != null) {
                                            vm.selectShape(hitShape.id)
                                            var last = down.position
                                            do {
                                                val event = awaitPointerEvent()
                                                val pressed = event.changes.filter { it.pressed }
                                                if (pressed.size >= 2) {
                                                    handlePinch(pressed)
                                                    event.changes.forEach { it.consume() }
                                                    continue
                                                }
                                                val change = event.changes.firstOrNull() ?: break
                                                if (change.pressed) {
                                                    val s = scaleState.value.coerceAtLeast(0.01f)
                                                    val delta = (change.position - last) / s
                                                    vm.moveSelectedShape(hitShape.id, delta.x, delta.y)
                                                    last = change.position
                                                    invalidate()
                                                    change.consume()
                                                }
                                            } while (event.changes.any { it.pressed })
                                            return@awaitEachGesture
                                        }

                                        // Empty space: clear selection and freely pan the page
                                        vm.clearObjectSelection()
                                        var last = down.position
                                        var panOffset = offsetState.value
                                        do {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.size >= 2) {
                                                handlePinch(pressed)
                                                panOffset = offsetState.value
                                                event.changes.forEach { it.consume() }
                                                continue
                                            }
                                            val change = event.changes.firstOrNull() ?: break
                                            if (change.pressed) {
                                                panOffset += (change.position - last)
                                                offset = panOffset
                                                last = change.position
                                                invalidate()
                                                change.consume()
                                            }
                                        } while (event.changes.any { it.pressed })
                                        return@awaitEachGesture
                                    }

                                    // Palm: never permanently block finger writing (many styluses report as Touch).
                                    // While inking with real stylus hardware, ignore extra finger contacts for ink.

                                    if (isEraserTool) {
                                        val pagePoint = toPage(down.position)
                                        val hitImage = vm.imageAt(pagePoint.x, pagePoint.y)
                                        if (hitImage != null) {
                                            vm.deleteImage(hitImage.id)
                                            invalidate()
                                            return@awaitEachGesture
                                        }
                                        val hitShape = vm.shapeAt(pagePoint.x, pagePoint.y)
                                        if (hitShape != null) {
                                            vm.deleteShape(hitShape.id)
                                            invalidate()
                                            return@awaitEachGesture
                                        }
                                    }

                                    var lastPos = down.position
                                    var startedStroke = false
                                    val strokePointerId = down.id

                                    val start = if (writing) toPageClipped(down.position) else toPage(down.position)
                                    if (writing) {
                                        if (isEraserTool) {
                                            vm.engine.eraseAt(start.x, start.y)
                                            invalidate()
                                        } else {
                                            val pressure = down.pressure.let { if (it in 0.01f..1f) it else 1f }
                                            vm.engine.beginStroke(start.x, start.y, pressure, System.currentTimeMillis())
                                            startedStroke = true
                                            invalidate()
                                        }
                                    }

                                    do {
                                        val event = awaitPointerEvent()
                                        val pressedChanges = event.changes.filter { it.pressed }
                                        if (pressedChanges.size >= 2) {
                                            if (startedStroke) {
                                                val stroke = vm.engine.endStroke()
                                                vm.onStrokeFinished(stroke)
                                                startedStroke = false
                                                invalidate()
                                            }
                                            handlePinch(pressedChanges)
                                            event.changes.forEach { it.consume() }
                                        } else if (writing) {
                                            pressedChanges
                                                .filter { change ->
                                                    if (change.id != strokePointerId) return@filter false
                                                    // If this stroke started on stylus hardware, skip palm/finger
                                                    if (strokeUsesPenHardware && change.isFinger()) return@filter false
                                                    true
                                                }
                                                .forEach { change ->
                                                    val p = toPageClipped(change.position)
                                                    if (isEraserTool) {
                                                        vm.engine.eraseAt(p.x, p.y)
                                                    } else if (startedStroke) {
                                                        val pressure = change.pressure.let { if (it in 0.01f..1f) it else 1f }
                                                        vm.engine.appendStroke(p.x, p.y, pressure, System.currentTimeMillis())
                                                    }
                                                    invalidate()
                                                    change.consume()
                                                }
                                            // Swallow extra palm contacts so they don't start other gestures
                                            if (strokeUsesPenHardware) {
                                                event.changes.filter { it.isFinger() && it.id != strokePointerId }
                                                    .forEach { it.consume() }
                                            }
                                        } else {
                                            val change = pressedChanges.firstOrNull()
                                            if (change != null) {
                                                offset += change.position - lastPos
                                                lastPos = change.position
                                                change.consume()
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    if (startedStroke) {
                                        val stroke = vm.engine.endStroke()
                                        vm.onStrokeFinished(stroke)
                                        invalidate()
                                    } else if (isEraserTool) {
                                        vm.onErase()
                                        invalidate()
                                    }
                                }
                            }
                    ) {
                        @Suppress("UNUSED_EXPRESSION")
                        drawTick
                        @Suppress("UNUSED_EXPRESSION")
                        state.strokeVersion

                        withTransform({
                            translate(offset.x, offset.y)
                            scale(scale, scale, pivot = Offset.Zero)
                        }) {
                            clipRect(0f, 0f, pageWidth, pageHeight) {
                            pdfBitmap?.let { drawImage(it) }

                            vm.engine.strokes
                                .filter { it.brushType == BrushType.HIGHLIGHTER }
                                .forEach { drawStrokeData(it) }
                            vm.engine.strokes
                                .filter { it.brushType != BrushType.HIGHLIGHTER }
                                .forEach { drawStrokeData(it) }

                            val preview = vm.engine.activePreview()
                            if (preview.size >= 2) {
                                drawStrokeData(
                                    StrokeData(
                                        id = "preview",
                                        points = preview,
                                        color = if (toolState.value == EditorTool.HIGHLIGHTER) highlighterState.value else penColorState.value,
                                        width = penWidthState.value * if (toolState.value == EditorTool.HIGHLIGHTER) 4.5f else 1f,
                                        opacity = if (toolState.value == EditorTool.HIGHLIGHTER) 0.35f else 1f,
                                        brushType = if (toolState.value == EditorTool.HIGHLIGHTER) BrushType.HIGHLIGHTER else brushState.value
                                    )
                                )
                            }

                            shapes.forEach { shape ->
                                val color = Color(shape.strokeColor.toInt())
                                val fill = Color(shape.fillColor.toInt())
                                when (shape.shapeType) {
                                    ShapeType.CIRCLE.name, ShapeType.ELLIPSE.name -> {
                                        drawOval(fill, Offset(shape.x, shape.y), Size(shape.width, shape.height))
                                        drawOval(color, Offset(shape.x, shape.y), Size(shape.width, shape.height), style = Stroke(shape.strokeWidth))
                                    }
                                    ShapeType.LINE.name, ShapeType.ARROW.name -> {
                                        val midY = shape.y + shape.height / 2f
                                        drawLine(
                                            color,
                                            Offset(shape.x, midY),
                                            Offset(shape.x + shape.width, midY),
                                            shape.strokeWidth
                                        )
                                    }
                                    ShapeType.TRIANGLE.name -> {
                                        val path = Path().apply {
                                            moveTo(shape.x + shape.width / 2f, shape.y)
                                            lineTo(shape.x + shape.width, shape.y + shape.height)
                                            lineTo(shape.x, shape.y + shape.height)
                                            close()
                                        }
                                        drawPath(path, fill)
                                        drawPath(path, color, style = Stroke(shape.strokeWidth))
                                    }
                                    else -> {
                                        drawRect(fill, Offset(shape.x, shape.y), Size(shape.width, shape.height))
                                        drawRect(color, Offset(shape.x, shape.y), Size(shape.width, shape.height), style = Stroke(shape.strokeWidth))
                                    }
                                }
                            }

                            images.forEach { img ->
                                BitmapFactory.decodeFile(img.path)?.asImageBitmap()?.let { bmp ->
                                    drawImage(
                                        bmp,
                                        dstOffset = IntOffset(img.x.toInt(), img.y.toInt()),
                                        dstSize = IntSize(img.width.toInt().coerceAtLeast(1), img.height.toInt().coerceAtLeast(1))
                                    )
                                }
                            }

                            texts.forEach { t ->
                                val layout = textMeasurer.measure(
                                    t.text,
                                    style = TextStyle(
                                        color = Color(t.color.toInt()),
                                        fontSize = t.fontSize.sp,
                                        fontFamily = if (t.scriptStyle) InkScriptFont else FontFamily.Default,
                                        fontWeight = if (t.bold) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                                drawText(layout, topLeft = Offset(t.x, t.y))
                            }

                            tapes.forEach { tape ->
                                if (!tape.revealed) {
                                    drawRect(Color(0xFFE9C46A), Offset(tape.x, tape.y), Size(tape.width, tape.height))
                                } else {
                                    drawRect(
                                        Color(0x33E9C46A),
                                        Offset(tape.x, tape.y),
                                        Size(tape.width, tape.height),
                                        style = Stroke(2f)
                                    )
                                }
                            }

                            if (lassoPoints.size >= 2) {
                                val path = Path().apply {
                                    moveTo(lassoPoints.first().x, lassoPoints.first().y)
                                    lassoPoints.drop(1).forEach { lineTo(it.x, it.y) }
                                    close()
                                }
                                drawPath(path, Color(0x442A9D8F))
                                drawPath(path, Color(0xFF2A9D8F), style = Stroke(2f))
                            }

                            selected.forEach { id ->
                                val stroke = vm.engine.strokes.firstOrNull { it.id == id } ?: return@forEach
                                val minX = stroke.points.minOf { it.x }
                                val maxX = stroke.points.maxOf { it.x }
                                val minY = stroke.points.minOf { it.y }
                                val maxY = stroke.points.maxOf { it.y }
                                drawRect(
                                    Color(0xFF2A9D8F),
                                    Offset(minX - 4f, minY - 4f),
                                    Size(maxX - minX + 8f, maxY - minY + 8f),
                                    style = Stroke(2f)
                                )
                            }
                            } // clipRect

                            // Selection chrome outside clip so delete/resize stay visible at edges
                            shapes.filter { it.id in selectedShapes }.forEach { shape ->
                                drawObjectSelection(
                                    SelectionBounds(shape.x, shape.y, shape.width, shape.height)
                                )
                            }
                            images.filter { it.id in selectedImages }.forEach { img ->
                                drawObjectSelection(
                                    SelectionBounds(img.x, img.y, img.width, img.height)
                                )
                            }
                        }
                    }

                // Floating zoom pill
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        Modifier
                            .shadow(2.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.92f))
                            .clickable { showZoomMenu = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.zoomLabel, style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(showZoomMenu, { showZoomMenu = false }) {
                        DropdownMenuItem(text = { Text("Fit page") }, onClick = {
                            fitPage()
                            showZoomMenu = false
                        })
                        zoomPresets().forEach { (label, value) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                applyZoom(value)
                                vm.setZoomLabel(label)
                                showZoomMenu = false
                            })
                        }
                        DropdownMenuItem(text = { Text("Zoom out") }, onClick = {
                            applyZoom(scale / 1.25f)
                            vm.setZoomLabel("${(scale / 1.25f * 100).toInt()}%")
                            showZoomMenu = false
                        })
                        DropdownMenuItem(text = { Text("Zoom in") }, onClick = {
                            applyZoom(scale * 1.25f)
                            vm.setZoomLabel("${(scale * 1.25f * 100).toInt()}%")
                            showZoomMenu = false
                        })
                    }
                }

                // Floating page pill
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .shadow(2.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (state.currentPageIndex > 0) {
                                fitApplied = false
                                vm.setPageIndex(state.currentPageIndex - 1)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.size(18.dp))
                    }
                    Text(
                        "${state.currentPageIndex + 1}/${state.pages.size.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    IconButton(
                        onClick = {
                            if (state.currentPageIndex < state.pages.lastIndex) {
                                fitApplied = false
                                vm.setPageIndex(state.currentPageIndex + 1)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
                    }
                }

                FloatingToolRail(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp),
                    tool = state.tool,
                    brush = state.brushType,
                    penColor = state.penColor,
                    penWidth = state.penWidth,
                    pencraftEnabled = state.pencraftEnabled,
                    shapeSnapEnabled = state.shapeSnapEnabled,
                    handwritingBeautifyEnabled = state.handwritingBeautifyEnabled,
                    onTool = vm::setTool,
                    onBrush = vm::setBrush,
                    onColor = vm::setPenColor,
                    onWidth = vm::setPenWidth,
                    onTogglePencraft = vm::togglePencraft,
                    onToggleShapeSnap = vm::toggleShapeSnap,
                    onToggleHandwritingBeautify = vm::toggleHandwritingBeautify,
                    onText = { showTextDialog = true },
                    onImage = { imagePicker.launch(arrayOf("image/*")) },
                    onShape = { showShapeMenu = true },
                    showShapeMenu = showShapeMenu,
                    onDismissShape = { showShapeMenu = false },
                    onShapePicked = { type ->
                        showShapeMenu = false
                        vm.addShape(type, pageWidth * 0.3f, pageHeight * 0.3f)
                    }
                )
            }

            if (state.showPageRail) {
                PageRail(
                    pages = state.pages,
                    current = state.currentPageIndex,
                    onSelect = {
                        fitApplied = false
                        vm.setPageIndex(it)
                    },
                    onAdd = {
                        fitApplied = false
                        vm.addPage()
                    },
                    onDuplicate = vm::duplicatePage,
                    onDelete = vm::deletePage,
                    onClose = vm::togglePageRail
                )
            }
        }
    }

    if (showTextDialog) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Insert text") },
            text = { OutlinedTextField(value = value, onValueChange = { value = it }) },
            confirmButton = {
                TextButton(onClick = {
                    if (value.isNotBlank()) vm.addText(value, 80f, 120f)
                    showTextDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FloatingToolRail(
    modifier: Modifier = Modifier,
    tool: EditorTool,
    brush: BrushType,
    penColor: Long,
    penWidth: Float,
    pencraftEnabled: Boolean,
    shapeSnapEnabled: Boolean,
    handwritingBeautifyEnabled: Boolean,
    onTool: (EditorTool) -> Unit,
    onBrush: (BrushType) -> Unit,
    onColor: (Long) -> Unit,
    onWidth: (Float) -> Unit,
    onTogglePencraft: () -> Unit,
    onToggleShapeSnap: () -> Unit,
    onToggleHandwritingBeautify: () -> Unit,
    onText: () -> Unit,
    onImage: () -> Unit,
    onShape: () -> Unit,
    showShapeMenu: Boolean,
    onDismissShape: () -> Unit,
    onShapePicked: (ShapeType) -> Unit
) {
    Column(
        modifier
            .shadow(6.dp, RoundedCornerShape(28.dp), clip = false)
            .width(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ToolIcon(Icons.Default.Edit, tool == EditorTool.PEN) { onTool(EditorTool.PEN) }
        ToolIcon(Icons.Default.Highlight, tool == EditorTool.HIGHLIGHTER) { onTool(EditorTool.HIGHLIGHTER) }
        ToolIcon(Icons.Default.Gesture, tool == EditorTool.ERASER) { onTool(EditorTool.ERASER) }
        ToolIcon(Icons.Default.Brush, tool == EditorTool.LASSO) { onTool(EditorTool.LASSO) }
        ToolIcon(Icons.Default.OpenWith, tool == EditorTool.SELECT) { onTool(EditorTool.SELECT) }
        ToolIcon(Icons.Default.TextFields, tool == EditorTool.TEXT, onClick = onText)
        ToolIcon(Icons.Default.Image, tool == EditorTool.IMAGE, onClick = onImage)
        Box {
            ToolIcon(Icons.Default.Category, tool == EditorTool.SHAPE, onClick = onShape)
            DropdownMenu(showShapeMenu, onDismissShape) {
                ShapeType.entries
                    .filter { it != ShapeType.POLYGON && it != ShapeType.ROUNDED_RECT }
                    .forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (type) {
                                        ShapeType.ELLIPSE -> "Oval"
                                        ShapeType.RECTANGLE -> "Rectangle"
                                        ShapeType.CIRCLE -> "Circle"
                                        ShapeType.TRIANGLE -> "Triangle"
                                        ShapeType.LINE -> "Line"
                                        ShapeType.ARROW -> "Arrow"
                                        else -> type.name
                                    }
                                )
                            },
                            onClick = { onShapePicked(type) }
                        )
                    }
            }
        }
        ToolIcon(Icons.Default.CropSquare, tool == EditorTool.TAPE) { onTool(EditorTool.TAPE) }
        ToolIcon(Icons.Default.Draw, handwritingBeautifyEnabled, showOnBadge = true, onClick = onToggleHandwritingBeautify)
        ToolIcon(Icons.Default.AutoAwesome, pencraftEnabled, onClick = onTogglePencraft)
        ToolIcon(Icons.Default.AutoFixHigh, shapeSnapEnabled, onClick = onToggleShapeSnap)

        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(28.dp)
                .height(1.dp)
                .background(Color(0xFFE0E4EA))
        )
        Spacer(Modifier.height(6.dp))

        listOf(BrushType.BALLPOINT, BrushType.FOUNTAIN, BrushType.PENCIL).forEach { b ->
            val selected = brush == b
            Text(
                b.name.take(1),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onBrush(b) }
                    .padding(8.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }

        CoverPalette.take(5).forEach { color ->
            Box(
                Modifier
                    .padding(vertical = 3.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(color.toInt()))
                    .border(
                        if (penColor == color) 2.dp else 1.dp,
                        if (penColor == color) MaterialTheme.colorScheme.primary else Color(0x22000000),
                        CircleShape
                    )
                    .clickable { onColor(color) }
            )
        }

        listOf(2f, 4f, 8f, 14f).forEach { w ->
            Box(
                Modifier
                    .padding(vertical = 3.dp)
                    .size((10 + w).dp)
                    .clip(CircleShape)
                    .background(if (penWidth == w) Color(0xFF2F80ED) else Color(0xFFB0B7C3))
                    .clickable { onWidth(w) }
            )
        }
    }
}

@Composable
private fun ToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    showOnBadge: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF2F80ED).copy(alpha = 0.16f) else Color.Transparent)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color(0xFF2F80ED) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
        if (showOnBadge && selected) {
            Text(
                "ON",
                color = Color(0xFF2F80ED),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

private val InkScriptFont = FontFamily(Font(R.font.caveat_medium))

@Composable
private fun PageRail(
    pages: List<com.personal.inkpad.data.db.PageEntity>,
    current: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .width(148.dp)
            .fillMaxHeight()
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E6EC))
            .padding(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Thumbnails", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
            }
        }
        Row {
            IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add page", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
        ) {
            itemsIndexed(pages) { index, _ ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFFBFF))
                            .border(
                                if (index == current) 2.dp else 1.dp,
                                if (index == current) Color(0xFF2F80ED) else Color(0xFFD5DAE2),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelect(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = Color(0xFF8A93A3))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private data class SelectionBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    fun deleteCenter() = Offset(x + width + 10f, y - 10f)
    fun resizeCenter() = Offset(x + width, y + height)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawObjectSelection(bounds: SelectionBounds) {
    val frameColor = Color(0xFF2F80ED)
    drawRect(
        color = frameColor,
        topLeft = Offset(bounds.x - 3f, bounds.y - 3f),
        size = Size(bounds.width + 6f, bounds.height + 6f),
        style = Stroke(width = 2.5f)
    )
    // Corner resize handle (bottom-right)
    val resize = bounds.resizeCenter()
    drawCircle(Color.White, radius = 9f, center = resize)
    drawCircle(frameColor, radius = 9f, center = resize, style = Stroke(2f))
    drawCircle(frameColor, radius = 3.5f, center = resize)

    // Delete "X" button (top-right)
    val del = bounds.deleteCenter()
    drawCircle(Color(0xFFE53935), radius = 14f, center = del)
    val xPad = 5.5f
    drawLine(Color.White, Offset(del.x - xPad, del.y - xPad), Offset(del.x + xPad, del.y + xPad), strokeWidth = 2.5f)
    drawLine(Color.White, Offset(del.x + xPad, del.y - xPad), Offset(del.x - xPad, del.y + xPad), strokeWidth = 2.5f)
}
