package com.mibandnfc.ui.common

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.mibandnfc.data.prefs.AppPrefs

object AdConfig {
    // Google's test ad unit — replace with real ID before release
    const val BANNER_UNIT_ID = "ca-app-pub-1885267558624288/8579028555"

    const val KOFI_URL = "https://ko-fi.com/arvinchou"
    const val GITHUB_REPO_URL = "https://github.com/arvin-chou/mi-band-nfc"
}

/**
 * Adaptive banner ad that respects the supporter flag.
 * Hidden when user is a supporter (paid to remove ads).
 */
@Composable
fun AdBanner(prefs: AppPrefs, modifier: Modifier = Modifier) {
    val isSupporter by prefs.isSupporter.collectAsState(initial = false)
    if (isSupporter) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdConfig.BANNER_UNIT_ID
                adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("AdBanner", "Ad failed: ${error.code} ${error.message}")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
