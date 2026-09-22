package com.personal.inkpad.ui.library

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.inkpad.InkPadApp
import com.personal.inkpad.data.db.FolderEntity
import com.personal.inkpad.data.db.NotebookEntity
import com.personal.inkpad.domain.model.Orientation
import com.personal.inkpad.domain.model.PageSize
import com.personal.inkpad.domain.model.PaperTemplate
import com.personal.inkpad.ui.theme.CoverPalette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class LibraryFilter {
    data object All : LibraryFilter()
    data object Unfiled : LibraryFilter()
    data object Favorites : LibraryFilter()
    data class Folder(val id: String, val name: String) : LibraryFilter()
}

data class LibraryUiState(
    val folders: List<FolderEntity> = emptyList(),
    val notebooks: List<NotebookEntity> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.All,
    val query: String = "",
    val grid: Boolean = true,
    val title: String = "All notebooks"
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = InkPadApp.instance.repository
    private val backup = InkPadApp.instance.backupExport

    private val filter = MutableStateFlow<LibraryFilter>(LibraryFilter.All)
    private val query = MutableStateFlow("")
    private val grid = MutableStateFlow(true)

    var pendingFolderRename by mutableStateOf<FolderEntity?>(null)

    private val notebookFlow = combine(filter, query) { f, q -> f to q }.flatMapLatest { (f, q) ->
        when {
            q.isNotBlank() -> repo.searchNotebooks(q.trim())
            f is LibraryFilter.Unfiled -> repo.observeUnfiled()
            f is LibraryFilter.Folder -> repo.observeInFolder(f.id)
            else -> repo.observeAllNotebooks()
        }
    }

    val state = combine(
        repo.observeFolders(),
        notebookFlow,
        filter,
        query,
        grid
    ) { folders, notebooks, f, q, g ->
        val filtered = when (f) {
            LibraryFilter.Favorites -> notebooks.filter { it.favorite }
            else -> notebooks
        }
        LibraryUiState(
            folders = folders,
            notebooks = filtered,
            filter = f,
            query = q,
            grid = g,
            title = when (f) {
                LibraryFilter.All -> "All notebooks"
                LibraryFilter.Unfiled -> "Unfiled"
                LibraryFilter.Favorites -> "Favorites"
                is LibraryFilter.Folder -> f.name
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleViewMode() {
        grid.value = !grid.value
    }

    fun createFolder(name: String) = viewModelScope.launch {
        repo.createFolder(name)
    }

    fun renameFolder(id: String, name: String) = viewModelScope.launch {
        repo.renameFolder(id, name)
    }

    fun deleteFolder(id: String) = viewModelScope.launch {
        repo.deleteFolder(id)
        if (filter.value is LibraryFilter.Folder && (filter.value as LibraryFilter.Folder).id == id) {
            filter.value = LibraryFilter.All
        }
    }

    fun renameNotebook(id: String, title: String) = viewModelScope.launch {
        repo.renameNotebook(id, title)
    }

    fun duplicate(id: String) = viewModelScope.launch {
        repo.duplicateNotebook(id)
    }

    fun deleteNotebook(id: String) = viewModelScope.launch {
        repo.deleteNotebook(id)
    }

    fun moveNotebook(id: String, folderId: String?) = viewModelScope.launch {
        repo.moveNotebook(id, folderId)
    }

    fun toggleFavorite(notebook: NotebookEntity) = viewModelScope.launch {
        repo.setFavorite(notebook.id, !notebook.favorite)
    }

    fun changeCover(id: String, color: Long) = viewModelScope.launch {
        repo.changeCover(id, color)
    }

    fun importPdf(uri: Uri, onOpen: (String) -> Unit) = viewModelScope.launch {
        val path = repo.copyUriToPdf(uri)
        val count = backup.pdfPageCount(path)
        val id = repo.createNotebook(
            title = "Imported PDF",
            template = PaperTemplate.BLANK,
            pageSize = PageSize.A4,
            orientation = Orientation.PORTRAIT,
            coverColor = CoverPalette[4],
            backgroundColor = 0xFFFFFFFF,
            folderId = (filter.value as? LibraryFilter.Folder)?.id,
            pdfPath = path,
            pdfPageCount = count
        )
        onOpen(id)
    }
}
