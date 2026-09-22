package com.personal.inkpad.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.inkpad.InkPadApp
import com.personal.inkpad.R
import com.personal.inkpad.data.db.FolderEntity
import com.personal.inkpad.data.db.NotebookEntity
import com.personal.inkpad.ui.theme.CoverPalette
import com.personal.inkpad.ui.theme.isAppDarkTheme
import com.personal.inkpad.ui.theme.libraryBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenNotebook: (String) -> Unit,
    onCreateNotebook: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    vm: LibraryViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showNewFolder by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<NotebookEntity?>(null) }
    var renameFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var moveTarget by remember { mutableStateOf<NotebookEntity?>(null) }
    var coverTarget by remember { mutableStateOf<NotebookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<NotebookEntity?>(null) }
    val dark = isAppDarkTheme
    val online = InkPadApp.instance.networkMonitor.isOnline
    val loggedIn = InkPadApp.instance.syncPreferences.settings.collectAsState(
        initial = com.personal.inkpad.data.sync.SyncSettings()
    ).value.isLoggedIn

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri, onOpenNotebook)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(312.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.inkpad_logo),
                        contentDescription = "InkPad",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your private writing desk",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))

                    val itemColors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    )
                    NavigationDrawerItem(
                        label = { Text("All notebooks") },
                        selected = state.filter == LibraryFilter.All,
                        onClick = {
                            vm.setFilter(LibraryFilter.All)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.GridView, null) },
                        colors = itemColors,
                        shape = RoundedCornerShape(14.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Unfiled") },
                        selected = state.filter == LibraryFilter.Unfiled,
                        onClick = {
                            vm.setFilter(LibraryFilter.Unfiled)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Folder, null) },
                        colors = itemColors,
                        shape = RoundedCornerShape(14.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Favorites") },
                        selected = state.filter == LibraryFilter.Favorites,
                        onClick = {
                            vm.setFilter(LibraryFilter.Favorites)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Favorite, null) },
                        colors = itemColors,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "FOLDERS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(state.folders, key = { it.id }) { folder ->
                            NavigationDrawerItem(
                                label = { Text(folder.name) },
                                selected = state.filter is LibraryFilter.Folder &&
                                    (state.filter as LibraryFilter.Folder).id == folder.id,
                                onClick = {
                                    vm.setFilter(LibraryFilter.Folder(folder.id, folder.name))
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.Folder, null) },
                                colors = itemColors,
                                shape = RoundedCornerShape(14.dp),
                                badge = {
                                    IconButton(onClick = { renameFolderTarget = folder }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Folder options")
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Default.CreateNewFolder, null)
                        Spacer(Modifier.width(8.dp))
                        Text("New folder")
                    }
                    TextButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Settings")
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(state.title, style = MaterialTheme.typography.headlineMedium)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        InkPadApp.instance.syncProvider.pushIfPossible()
                                    }
                                }
                            }
                        }) {
                            Icon(
                                if (online && loggedIn) Icons.Default.CloudUpload else Icons.Default.CloudOff,
                                contentDescription = "Sync",
                                tint = if (online && loggedIn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { vm.toggleViewMode() }) {
                            Icon(
                                if (state.grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle view"
                            )
                        }
                        IconButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Import PDF")
                        }
                        IconButton(onClick = {
                            val folderId = (state.filter as? LibraryFilter.Folder)?.id
                            onCreateNotebook(folderId)
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "New notebook")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(libraryBackdrop(dark))
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                ) {
                    if (showSearch) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = vm::setQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp)),
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text("Search notebooks") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    if (state.notebooks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(R.drawable.inkpad_logo),
                                    contentDescription = "InkPad",
                                    modifier = Modifier
                                        .fillMaxWidth(0.45f)
                                        .height(120.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Create a notebook to begin writing.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (state.grid) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(168.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.notebooks, key = { it.id }) { notebook ->
                                NotebookCard(
                                    notebook = notebook,
                                    folderName = state.folders.firstOrNull { it.id == notebook.folderId }?.name,
                                    synced = loggedIn,
                                    onOpen = { onOpenNotebook(notebook.id) },
                                    onRename = { renameTarget = notebook },
                                    onDuplicate = { vm.duplicate(notebook.id) },
                                    onDelete = { deleteTarget = notebook },
                                    onMove = { moveTarget = notebook },
                                    onFavorite = { vm.toggleFavorite(notebook) },
                                    onCover = { coverTarget = notebook }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.notebooks, key = { it.id }) { notebook ->
                                NotebookListRow(
                                    notebook = notebook,
                                    synced = loggedIn,
                                    onOpen = { onOpenNotebook(notebook.id) },
                                    onRename = { renameTarget = notebook },
                                    onDuplicate = { vm.duplicate(notebook.id) },
                                    onDelete = { deleteTarget = notebook },
                                    onMove = { moveTarget = notebook },
                                    onFavorite = { vm.toggleFavorite(notebook) },
                                    onCover = { coverTarget = notebook }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        NameDialog("New folder", "", { showNewFolder = false }) {
            vm.createFolder(it)
            showNewFolder = false
        }
    }
    renameTarget?.let { nb ->
        NameDialog("Rename notebook", nb.title, { renameTarget = null }) {
            vm.renameNotebook(nb.id, it)
            renameTarget = null
        }
    }
    renameFolderTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { renameFolderTarget = null },
            title = { Text(folder.name) },
            text = { Text("Rename or delete this folder.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.pendingFolderRename = folder
                    renameFolderTarget = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deleteFolder(folder.id)
                    renameFolderTarget = null
                }) { Text("Delete") }
            }
        )
    }
    vm.pendingFolderRename?.let { folder ->
        NameDialog("Rename folder", folder.name, { vm.pendingFolderRename = null }) {
            vm.renameFolder(folder.id, it)
            vm.pendingFolderRename = null
        }
    }
    moveTarget?.let { nb ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move notebook") },
            text = {
                Column {
                    TextButton(onClick = {
                        vm.moveNotebook(nb.id, null)
                        moveTarget = null
                    }) { Text("Unfiled") }
                    state.folders.forEach { folder ->
                        TextButton(onClick = {
                            vm.moveNotebook(nb.id, folder.id)
                            moveTarget = null
                        }) { Text(folder.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } }
        )
    }
    coverTarget?.let { nb ->
        AlertDialog(
            onDismissRequest = { coverTarget = null },
            title = { Text("Cover color") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoverPalette.forEach { color ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(color.toInt()))
                                .clickable {
                                    vm.changeCover(nb.id, color)
                                    coverTarget = null
                                }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { coverTarget = null }) { Text("Close") } }
        )
    }
    deleteTarget?.let { nb ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete notebook?") },
            text = { Text("“${nb.title}” will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNotebook(nb.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NotebookCard(
    notebook: NotebookEntity,
    folderName: String?,
    synced: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onFavorite: () -> Unit,
    onCover: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val cover = Color(notebook.coverColor.toInt())
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .shadow(3.dp, RoundedCornerShape(10.dp), clip = false)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFFFBFF))
                .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
        ) {
            // faint ruled paper
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(7) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE6EAF0))
                    )
                }
            }
            // cover accent strip
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(cover)
            )
            if (synced) {
                Icon(
                    Icons.Default.CloudDone,
                    contentDescription = "Synced",
                    tint = Color(0xFF2F80ED),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
            IconButton(
                onClick = onFavorite,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
            ) {
                Icon(
                    if (notebook.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (notebook.favorite) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                notebook.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(18.dp))
                }
                NotebookMenu(menu, { menu = false }, onRename, onDuplicate, onDelete, onMove, onCover)
            }
        }
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notebook.updatedAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (folderName != null) {
            Spacer(Modifier.height(2.dp))
            Text(folderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NotebookListRow(
    notebook: NotebookEntity,
    synced: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onFavorite: () -> Unit,
    onCover: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFFBFF))
                .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
        ) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(notebook.coverColor.toInt()))
            )
            if (synced) {
                Icon(
                    Icons.Default.CloudDone,
                    null,
                    tint = Color(0xFF2F80ED),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(14.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(notebook.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notebook.updatedAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(if (notebook.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
            NotebookMenu(menu, { menu = false }, onRename, onDuplicate, onDelete, onMove, onCover)
        }
    }
}

@Composable
private fun NotebookMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onCover: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Rename") }, onClick = { onDismiss(); onRename() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { onDismiss(); onDuplicate() }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
        DropdownMenuItem(text = { Text("Move") }, onClick = { onDismiss(); onMove() }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) })
        DropdownMenuItem(text = { Text("Cover") }, onClick = { onDismiss(); onCover() }, leadingIcon = { Icon(Icons.Default.Palette, null) })
        DropdownMenuItem(text = { Text("Delete") }, onClick = { onDismiss(); onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
    }
}
