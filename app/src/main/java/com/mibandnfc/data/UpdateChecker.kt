package com.mibandnfc.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val downloadUrl: String,
    )

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
            val url = URL("https://api.github.com/repos/arvin-chou/mi-band-nfc/releases/latest")
            val json = url.readText()
            val tagRegex = """"tag_name"\s*:\s*"v?([^"]+)"""".toRegex()
            val urlRegex = """"browser_download_url"\s*:\s*"([^"]*\.apk)"""".toRegex()
            val tag = tagRegex.find(json)?.groupValues?.get(1) ?: return@withContext null
            val dlUrl = urlRegex.find(json)?.groupValues?.get(1) ?: ""
            UpdateInfo(tag != currentVersion, tag, dlUrl)
        } catch (_: Exception) {
            null
        }
    }
}
