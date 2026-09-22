package com.personal.inkpad.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutoSyncCoordinator(
    private val scope: CoroutineScope,
    private val networkMonitor: NetworkMonitor,
    private val syncProvider: SupabaseSyncProvider
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            var wasOnline = false
            var first = true
            networkMonitor.onlineUpdates().collectLatest { online ->
                val shouldSync = online && (first || !wasOnline)
                first = false
                wasOnline = online
                if (shouldSync) {
                    delay(1200)
                    syncProvider.pushIfPossible()
                }
            }
        }
    }

    fun syncNowInBackground() {
        scope.launch {
            if (networkMonitor.isOnline) {
                syncProvider.pushIfPossible()
            }
        }
    }
}
