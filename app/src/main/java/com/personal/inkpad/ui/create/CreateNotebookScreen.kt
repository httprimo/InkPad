package com.personal.inkpad.ui.create

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.inkpad.InkPadApp
import com.personal.inkpad.domain.model.Orientation
import com.personal.inkpad.domain.model.PageSize
import com.personal.inkpad.domain.model.PaperTemplate
import com.personal.inkpad.ui.theme.CoverPalette
import com.personal.inkpad.ui.theme.isAppDarkTheme
import com.personal.inkpad.ui.theme.libraryBackdrop
import kotlinx.coroutines.launch

private data class PaperColorOption(val label: String, val argb: Long)

private val PaperColors = listOf(
    PaperColorOption("White", 0xFFFFFFFF),
    PaperColorOption("Ivory", 0xFFFFFDF8),
    PaperColorOption("Cream", 0xFFFFF4D6),
    PaperColorOption("Soft blue", 0xFFE8F1F8),
    PaperColorOption("Sage", 0xFFE6F0EA),
    PaperColorOption("Blush", 0xFFFCEEEE)
)

class CreateNotebookViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = InkPadApp.instance.repository

    suspend fun create(
        title: String,
        template: PaperTemplate,
        pageSize: PageSize,
        orientation: Orientation,
        cover: Long,
        background: Long,
        folderId: String?
    ): String = repo.createNotebook(title, template, pageSize, orientation, cover, background, folderId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNotebookScreen(
    folderId: String?,
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
    vm: CreateNotebookViewModel = viewModel()
) {
    var title by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(PaperTemplate.RULED) }
    var pageSize by remember { mutableStateOf(PageSize.A4) }
    var orientation by remember { mutableStateOf(Orientation.PORTRAIT) }
    var cover by remember { mutableLongStateOf(CoverPalette.first()) }
    var background by remember { mutableLongStateOf(PaperColors.first().argb) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("New notebook", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(libraryBackdrop(isAppDarkTheme))
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Paper template", style = MaterialTheme.typography.titleLarge)
            FlowChips(
                items = PaperTemplate.entries.map { it.name.lowercase().replaceFirstChar(Char::titlecase) to it },
                selected = template,
                onSelect = { template = it }
            )

            Text("Page size", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PageSize.A4, PageSize.LETTER, PageSize.A5, PageSize.A3).forEach { size ->
                    FilterChip(
                        selected = pageSize == size,
                        onClick = { pageSize = size },
                        label = { Text(size.name) }
                    )
                }
            }

            Text("Orientation", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = orientation == Orientation.PORTRAIT,
                    onClick = { orientation = Orientation.PORTRAIT },
                    label = { Text("Portrait") }
                )
                FilterChip(
                    selected = orientation == Orientation.LANDSCAPE,
                    onClick = { orientation = Orientation.LANDSCAPE },
                    label = { Text("Landscape") }
                )
            }

            Text("Cover", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoverPalette.forEach { color ->
                    val selected = cover == color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(color.toInt()))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { cover = color }
                    )
                }
            }

            Text("Paper color", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperColors.forEach { option ->
                    val selected = background == option.argb
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(option.argb.toInt()))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { background = option.argb }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        val id = vm.create(title, template, pageSize, orientation, cover, background, folderId)
                        onCreated(id)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Create notebook", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FlowChips(
    items: List<Pair<String, PaperTemplate>>,
    selected: PaperTemplate,
    onSelect: (PaperTemplate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}
