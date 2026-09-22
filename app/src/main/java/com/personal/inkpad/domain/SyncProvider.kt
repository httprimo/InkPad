package com.personal.inkpad.domain

/**
 * Optional cloud sync — core handwriting stays offline-first.
 */
interface SyncProvider {
    val isEnabled: Boolean
    suspend fun push()
    suspend fun pull()
}

object NoOpSyncProvider : SyncProvider {
    override val isEnabled: Boolean = false
    override suspend fun push() = Unit
    override suspend fun pull() = Unit
}
