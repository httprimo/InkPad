package com.personal.inkpad

import android.app.Application
import com.personal.inkpad.BuildConfig
import com.personal.inkpad.data.repo.BackupExportService
import com.personal.inkpad.data.repo.InkPadRepository
import com.personal.inkpad.data.sync.AutoSyncCoordinator
import com.personal.inkpad.data.sync.NetworkMonitor
import com.personal.inkpad.data.sync.SupabaseSyncProvider
import com.personal.inkpad.data.sync.SyncPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InkPadApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: InkPadRepository
        private set
    lateinit var backupExport: BackupExportService
        private set
    lateinit var syncPreferences: SyncPreferences
        private set
    lateinit var syncProvider: SupabaseSyncProvider
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var autoSync: AutoSyncCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = InkPadRepository(this)
        backupExport = BackupExportService(this, repository)
        syncPreferences = SyncPreferences(this)
        syncProvider = SupabaseSyncProvider(syncPreferences, backupExport, cacheDir)
        networkMonitor = NetworkMonitor(this)
        autoSync = AutoSyncCoordinator(appScope, networkMonitor, syncProvider)
        listOf("pdf", "images", "audio", "exports", "backups").forEach { name ->
            getDir(name, MODE_PRIVATE)
            repository.filesDir(name)
        }
        appScope.launch {
            seedCloudConfig()
            syncProvider.refreshEnabledFlag()
            autoSync.start()
        }
    }

    private suspend fun seedCloudConfig() {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (url.isBlank() || key.isBlank()) return
        val current = syncPreferences.current()
        if (current.supabaseUrl != url || current.anonKey != key) {
            syncPreferences.saveProjectConfig(url, key, enabled = current.isLoggedIn || current.enabled)
        }
    }

    companion object {
        lateinit var instance: InkPadApp
            private set
    }
}
