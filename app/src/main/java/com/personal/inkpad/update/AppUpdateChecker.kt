package com.personal.inkpad.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.personal.inkpad.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer APK and installs it (sideload flow).
 *
 * Release convention:
 * - Tag: `v2`, `v3`, … matching [BuildConfig.VERSION_CODE]
 * - Attach an `.apk` asset (e.g. InkPad-1.0.1.apk)
 */
object AppUpdateChecker {
    private const val OWNER = "httprimo"
    private const val REPO = "InkPad"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val apkName: String,
        val releaseNotes: String
    )

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        data object UpToDate : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "InkPad-Android")
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 404) {
                    return@withContext CheckResult.Failed("No releases yet on GitHub")
                }
                if (!resp.isSuccessful) {
                    return@withContext CheckResult.Failed("Update check failed (${resp.code})")
                }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val tag = json.optString("tag_name").removePrefix("v").trim()
                val remoteCode = tag.toIntOrNull()
                    ?: return@withContext CheckResult.Failed("Release tag should be like v2 (got ${json.optString("tag_name")})")
                if (remoteCode <= BuildConfig.VERSION_CODE) {
                    return@withContext CheckResult.UpToDate
                }
                val assets = json.optJSONArray("assets")
                    ?: return@withContext CheckResult.Failed("Release has no APK attached")
                var apkUrl: String? = null
                var apkName = "InkPad-update.apk"
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        apkName = name
                        break
                    }
                }
                if (apkUrl.isNullOrBlank()) {
                    return@withContext CheckResult.Failed("Release has no .apk file")
                }
                CheckResult.Available(
                    UpdateInfo(
                        versionCode = remoteCode,
                        versionName = json.optString("name").ifBlank { "v$remoteCode" },
                        apkUrl = apkUrl,
                        apkName = apkName,
                        releaseNotes = json.optString("body").orEmpty()
                    )
                )
            }
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: "Update check failed")
        }
    }

    suspend fun downloadApk(context: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, info.apkName)
        val req = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "InkPad-Android")
            .header("Accept", "application/octet-stream")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download failed (${resp.code})")
            val body = resp.body ?: error("Empty download")
            out.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
        out
    }

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Android 8+: may need “Install unknown apps” for this package
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
        }
        context.startActivity(intent)
    }
}
