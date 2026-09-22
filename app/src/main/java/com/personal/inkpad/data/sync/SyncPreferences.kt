package com.personal.inkpad.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncDataStore by preferencesDataStore("inkpad_sync")

data class SyncSettings(
    val enabled: Boolean = false,
    val autoSync: Boolean = true,
    val supabaseUrl: String = "",
    val anonKey: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val email: String = "",
    val lastPushAt: Long = 0L,
    val lastPullAt: Long = 0L,
    val lastError: String = "",
    val themeMode: String = "SYSTEM"
) {
    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && anonKey.isNotBlank()

    val isLoggedIn: Boolean
        get() = userId.isNotBlank() && accessToken.isNotBlank()
}

class SyncPreferences(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val autoSyncKey = booleanPreferencesKey("auto_sync")
    private val urlKey = stringPreferencesKey("url")
    private val anonKey = stringPreferencesKey("anon")
    private val accessKey = stringPreferencesKey("access")
    private val refreshKey = stringPreferencesKey("refresh")
    private val userKey = stringPreferencesKey("user")
    private val emailKey = stringPreferencesKey("email")
    private val lastPushKey = longPreferencesKey("last_push")
    private val lastPullKey = longPreferencesKey("last_pull")
    private val lastErrorKey = stringPreferencesKey("last_error")
    private val themeKey = stringPreferencesKey("theme_mode")

    val settings: Flow<SyncSettings> = context.syncDataStore.data.map { prefs ->
        SyncSettings(
            enabled = prefs[enabledKey] ?: false,
            autoSync = prefs[autoSyncKey] ?: true,
            supabaseUrl = prefs[urlKey].orEmpty().trimEnd('/'),
            anonKey = prefs[anonKey].orEmpty().trim(),
            accessToken = prefs[accessKey].orEmpty(),
            refreshToken = prefs[refreshKey].orEmpty(),
            userId = prefs[userKey].orEmpty(),
            email = prefs[emailKey].orEmpty(),
            lastPushAt = prefs[lastPushKey] ?: 0L,
            lastPullAt = prefs[lastPullKey] ?: 0L,
            lastError = prefs[lastErrorKey].orEmpty(),
            themeMode = prefs[themeKey] ?: "SYSTEM"
        )
    }

    suspend fun current(): SyncSettings = settings.first()

    suspend fun saveProjectConfig(url: String, key: String, enabled: Boolean = true) {
        context.syncDataStore.edit { prefs ->
            prefs[urlKey] = url.trim().trimEnd('/')
            prefs[anonKey] = key.trim()
            prefs[enabledKey] = enabled
        }
    }

    suspend fun saveSession(userId: String, accessToken: String, refreshToken: String, email: String) {
        context.syncDataStore.edit { prefs ->
            prefs[userKey] = userId
            prefs[accessKey] = accessToken
            prefs[refreshKey] = refreshToken
            prefs[emailKey] = email
            prefs[lastErrorKey] = ""
        }
    }

    suspend fun clearSession() {
        context.syncDataStore.edit { prefs ->
            prefs.remove(userKey)
            prefs.remove(accessKey)
            prefs.remove(refreshKey)
            prefs.remove(emailKey)
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.syncDataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setAutoSync(autoSync: Boolean) {
        context.syncDataStore.edit { it[autoSyncKey] = autoSync }
    }

    suspend fun setThemeMode(mode: String) {
        context.syncDataStore.edit { it[themeKey] = mode }
    }

    suspend fun markPush(at: Long = System.currentTimeMillis()) {
        context.syncDataStore.edit {
            it[lastPushKey] = at
            it[lastErrorKey] = ""
        }
    }

    suspend fun markPull(at: Long = System.currentTimeMillis()) {
        context.syncDataStore.edit {
            it[lastPullKey] = at
            it[lastErrorKey] = ""
        }
    }

    suspend fun markError(message: String) {
        context.syncDataStore.edit { it[lastErrorKey] = message.take(200) }
    }
}
