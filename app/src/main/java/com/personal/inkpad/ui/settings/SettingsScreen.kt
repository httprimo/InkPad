package com.personal.inkpad.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.inkpad.InkPadApp
import com.personal.inkpad.ui.theme.ThemeMode
import com.personal.inkpad.ui.theme.isAppDarkTheme
import com.personal.inkpad.ui.theme.libraryBackdrop
import androidx.compose.foundation.background
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onSignedOut: () -> Unit = {}
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backup = InkPadApp.instance.backupExport
    val syncPrefs = InkPadApp.instance.syncPreferences
    val sync = InkPadApp.instance.syncProvider
    val online = InkPadApp.instance.networkMonitor.isOnline
    val settings by syncPrefs.settings.collectAsState(
        initial = com.personal.inkpad.data.sync.SyncSettings()
    )

    var busy by remember { mutableStateOf(false) }
    var autoSync by remember(settings.autoSync) { mutableStateOf(settings.autoSync) }

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val tmp = File(InkPadApp.instance.cacheDir, "restore.zip")
                InkPadApp.instance.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.IO) { backup.restoreBackup(tmp) }
                snackbar.showSnackbar("Restored")
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Restore failed")
            }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(libraryBackdrop(isAppDarkTheme))
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.SYSTEM -> "System"
                                }
                            )
                        }
                    )
                }
            }

            Text("Account", style = MaterialTheme.typography.titleLarge)
            if (settings.isLoggedIn) {
                Text(settings.email.ifBlank { "Signed in" }, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                withContext(Dispatchers.IO) { sync.signOut() }
                                onSignedOut()
                            } catch (e: Exception) {
                                snackbar.showSnackbar(e.message ?: "Sign out failed")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) { Text("Sign out") }
            } else {
                Text(
                    "You’re signed out. Restart or return to the welcome screen to sign in.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Cloud sync", style = MaterialTheme.typography.titleLarge)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto sync when online")
                    Text(
                        if (online) "Online" else "Offline — saving on this device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoSync,
                    onCheckedChange = {
                        autoSync = it
                        scope.launch { syncPrefs.setAutoSync(it) }
                    },
                    enabled = settings.isLoggedIn
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) { sync.push() }
                            snackbar.showSnackbar("Uploaded")
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "Upload failed")
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && settings.isLoggedIn && online
            ) { Text("Upload now") }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) { sync.pull() }
                            snackbar.showSnackbar("Downloaded")
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "Download failed")
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && settings.isLoggedIn && online
            ) { Text("Download now") }

            Text("This device", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) { backup.createBackup() }
                            snackbar.showSnackbar("Saved ${file.name}")
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "Backup failed")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Local backup") }
            OutlinedButton(
                onClick = { restorePicker.launch(arrayOf("application/zip", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore local backup") }

            Text("Updates", style = MaterialTheme.typography.titleLarge)
            Text(
                "Version ${com.personal.inkpad.BuildConfig.VERSION_NAME} (${com.personal.inkpad.BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            when (val result = withContext(Dispatchers.IO) {
                                com.personal.inkpad.update.AppUpdateChecker.check()
                            }) {
                                is com.personal.inkpad.update.AppUpdateChecker.CheckResult.UpToDate ->
                                    snackbar.showSnackbar("You’re on the latest version")
                                is com.personal.inkpad.update.AppUpdateChecker.CheckResult.Failed ->
                                    snackbar.showSnackbar(result.message)
                                is com.personal.inkpad.update.AppUpdateChecker.CheckResult.Available -> {
                                    snackbar.showSnackbar("Downloading ${result.info.versionName}…")
                                    val apk = withContext(Dispatchers.IO) {
                                        com.personal.inkpad.update.AppUpdateChecker.downloadApk(
                                            InkPadApp.instance,
                                            result.info
                                        )
                                    }
                                    com.personal.inkpad.update.AppUpdateChecker.installApk(
                                        InkPadApp.instance,
                                        apk
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "Update failed")
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && online
            ) { Text(if (busy) "Working…" else "Check for update") }
        }
    }
}
