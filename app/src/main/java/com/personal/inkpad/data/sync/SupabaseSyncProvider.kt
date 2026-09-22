package com.personal.inkpad.data.sync

import com.personal.inkpad.data.repo.BackupExportService
import com.personal.inkpad.domain.SyncProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SupabaseSyncProvider(
    private val prefs: SyncPreferences,
    private val backupExport: BackupExportService,
    private val cacheDir: File
) : SyncProvider {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var enabledCache = false

    override val isEnabled: Boolean
        get() = enabledCache

    suspend fun refreshEnabledFlag() {
        val s = prefs.current()
        enabledCache = s.enabled && s.isConfigured && s.isLoggedIn
    }

    suspend fun signUp(email: String, password: String) = withContext(Dispatchers.IO) {
        val settings = prefs.current()
        require(settings.isConfigured) { "Cloud is not configured." }
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${settings.supabaseUrl}/auth/v1/signup")
            .addHeader("apikey", settings.anonKey)
            .addHeader("Authorization", "Bearer ${settings.anonKey}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(friendlyAuthError(response.code, raw))
            val session = parseSession(raw)
            // Some projects require email confirm — session may be empty
            if (session.accessToken.isBlank()) {
                error("Account created. Confirm your email, then log in.")
            }
            prefs.saveSession(session.userId, session.accessToken, session.refreshToken, email.trim())
            prefs.setEnabled(true)
            refreshEnabledFlag()
        }
    }

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        val settings = prefs.current()
        require(settings.isConfigured) { "Cloud is not configured." }
        val body = FormBody.Builder()
            .add("grant_type", "password")
            .add("email", email.trim())
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url("${settings.supabaseUrl}/auth/v1/token?grant_type=password")
            .addHeader("apikey", settings.anonKey)
            .addHeader("Authorization", "Bearer ${settings.anonKey}")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(friendlyAuthError(response.code, raw))
            val session = parseSession(raw)
            prefs.saveSession(session.userId, session.accessToken, session.refreshToken, email.trim())
            prefs.setEnabled(true)
            refreshEnabledFlag()
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String? = null) = withContext(Dispatchers.IO) {
        val settings = prefs.current()
        require(settings.isConfigured) { "Cloud is not configured." }
        require(idToken.isNotBlank()) { "Missing Google token." }
        val body = JSONObject()
            .put("provider", "google")
            .put("id_token", idToken)
        if (!nonce.isNullOrBlank()) body.put("nonce", nonce)
        val request = Request.Builder()
            .url("${settings.supabaseUrl}/auth/v1/token?grant_type=id_token")
            .addHeader("apikey", settings.anonKey)
            .addHeader("Authorization", "Bearer ${settings.anonKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(friendlyAuthError(response.code, raw))
            val session = parseSession(raw)
            require(session.accessToken.isNotBlank() && session.userId.isNotBlank()) {
                "Google sign-in failed."
            }
            prefs.saveSession(session.userId, session.accessToken, session.refreshToken, session.email)
            prefs.setEnabled(true)
            refreshEnabledFlag()
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val settings = prefs.current()
        if (settings.accessToken.isNotBlank() && settings.isConfigured) {
            val request = Request.Builder()
                .url("${settings.supabaseUrl}/auth/v1/logout")
                .addHeader("apikey", settings.anonKey)
                .addHeader("Authorization", "Bearer ${settings.accessToken}")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            runCatching { http.newCall(request).execute().close() }
        }
        prefs.clearSession()
        refreshEnabledFlag()
    }

    override suspend fun push() = withContext(Dispatchers.IO) {
        val settings = prefs.current()
        require(settings.isConfigured) { "Cloud is not configured." }
        require(settings.isLoggedIn) { "Log in to sync." }
        try {
            val session = ensureSession(settings)
            val zip = backupExport.createBackup()
            upload(settings, session.accessToken, "${session.userId}/backups/latest.zip", zip)
            upload(
                settings,
                session.accessToken,
                "${session.userId}/backups/inkpad-${System.currentTimeMillis()}.zip",
                zip
            )
            prefs.markPush()
            refreshEnabledFlag()
        } catch (e: Exception) {
            prefs.markError(e.message ?: "Upload failed")
            throw e
        }
    }

    override suspend fun pull() = withContext(Dispatchers.IO) {
        val settings = prefs.current()
        require(settings.isConfigured) { "Cloud is not configured." }
        require(settings.isLoggedIn) { "Log in to sync." }
        try {
            val session = ensureSession(settings)
            val dest = File(cacheDir, "cloud-restore.zip")
            download(settings, session.accessToken, "${session.userId}/backups/latest.zip", dest)
            backupExport.restoreBackup(dest)
            prefs.markPull()
            refreshEnabledFlag()
        } catch (e: Exception) {
            prefs.markError(e.message ?: "Download failed")
            throw e
        }
    }

    /** Safe push used by auto-sync — no-op if offline prerequisites fail. */
    suspend fun pushIfPossible(): Boolean = withContext(Dispatchers.IO) {
        val settings = prefs.current()
        if (!settings.enabled || !settings.autoSync || !settings.isConfigured || !settings.isLoggedIn) {
            return@withContext false
        }
        return@withContext try {
            push()
            true
        } catch (_: Exception) {
            false
        }
    }

    private data class Session(
        val userId: String,
        val accessToken: String,
        val refreshToken: String,
        val email: String = ""
    )

    private suspend fun ensureSession(settings: SyncSettings): Session {
        require(settings.isLoggedIn) { "Log in to sync." }
        val refreshed = refresh(settings)
        val session = refreshed ?: Session(
            settings.userId,
            settings.accessToken,
            settings.refreshToken,
            settings.email
        )
        prefs.saveSession(
            session.userId,
            session.accessToken,
            session.refreshToken,
            session.email.ifBlank { settings.email }
        )
        return session
    }

    private fun refresh(settings: SyncSettings): Session? {
        if (settings.refreshToken.isBlank()) return null
        val body = JSONObject()
            .put("refresh_token", settings.refreshToken)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${settings.supabaseUrl}/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", settings.anonKey)
            .addHeader("Authorization", "Bearer ${settings.anonKey}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null
            return parseSession(raw)
        }
    }

    private fun parseSession(raw: String): Session {
        val json = JSONObject(raw)
        val access = json.optString("access_token").ifBlank {
            json.optJSONObject("session")?.optString("access_token").orEmpty()
        }
        val refresh = json.optString("refresh_token").ifBlank {
            json.optJSONObject("session")?.optString("refresh_token").orEmpty()
        }
        val user = json.optJSONObject("user")
            ?: json.optJSONObject("session")?.optJSONObject("user")
        val userId = user?.optString("id").orEmpty()
        val email = user?.optString("email").orEmpty()
        return Session(userId, access, refresh, email)
    }

    private fun friendlyAuthError(code: Int, raw: String): String {
        val msg = runCatching {
            JSONObject(raw).optString("msg").ifBlank {
                JSONObject(raw).optString("error_description").ifBlank {
                    JSONObject(raw).optString("error")
                }
            }
        }.getOrDefault("")
        return when {
            msg.contains("Invalid login", true) -> "Wrong email or password."
            msg.contains("already registered", true) -> "Email already registered. Log in instead."
            msg.contains("Email not confirmed", true) -> "Confirm your email, then log in."
            msg.contains("Unsupported provider", true) || msg.contains("provider is not enabled", true) ->
                "Enable Google under Supabase Authentication → Providers."
            else -> msg.ifBlank { "Request failed ($code)" }
        }
    }

    private fun encodePath(path: String): String =
        path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }

    private fun upload(settings: SyncSettings, accessToken: String, objectPath: String, file: File) {
        val url = "${settings.supabaseUrl}/storage/v1/object/inkpad/${encodePath(objectPath)}"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", settings.anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("x-upsert", "true")
            .addHeader("Content-Type", "application/zip")
            .put(file.asRequestBody("application/zip".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Upload failed (${response.code})")
        }
    }

    private fun download(settings: SyncSettings, accessToken: String, objectPath: String, dest: File) {
        val url = "${settings.supabaseUrl}/storage/v1/object/inkpad/${encodePath(objectPath)}"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", settings.anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Nothing to download yet. Upload first.")
            }
            dest.outputStream().use { out ->
                response.body!!.byteStream().copyTo(out)
            }
        }
    }
}
