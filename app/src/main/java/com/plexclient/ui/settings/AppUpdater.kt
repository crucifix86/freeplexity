package com.plexclient.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String
)

class AppUpdater(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(VERSION_URL)
                .addHeader("Cache-Control", "no-cache")
                .build()

            val response = http.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JsonParser.parseString(body).asJsonObject

            val remoteCode = json.get("versionCode")?.asInt ?: return@withContext null
            val currentCode = getCurrentVersionCode()

            if (remoteCode > currentCode) {
                UpdateInfo(
                    versionCode = remoteCode,
                    versionName = json.get("versionName")?.asString ?: "?",
                    apkUrl = json.get("apkUrl")?.asString ?: return@withContext null,
                    changelog = json.get("changelog")?.asString ?: ""
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadUpdate(apkUrl: String, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(apkUrl).build()
            val response = http.newCall(request).execute()
            val responseBody = response.body ?: return@withContext null

            val updateDir = File(context.cacheDir, "updates")
            updateDir.mkdirs()
            val apkFile = File(updateDir, "freeplexity-update.apk")

            val totalBytes = responseBody.contentLength()
            var downloadedBytes = 0L

            FileOutputStream(apkFile).use { out ->
                responseBody.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress(((downloadedBytes * 100) / totalBytes).toInt())
                        }
                    }
                }
            }

            apkFile
        } catch (_: Exception) {
            null
        }
    }

    fun installApk(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            pInfo.versionCode
        } catch (_: Exception) {
            0
        }
    }

    companion object {
        private const val VERSION_URL =
            "https://raw.githubusercontent.com/crucifix86/freeplexity/main/version.json"
    }
}
