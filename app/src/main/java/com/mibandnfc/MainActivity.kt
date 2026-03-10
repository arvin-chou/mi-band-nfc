package com.mibandnfc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mibandnfc.ui.nav.AppNav
import com.mibandnfc.ui.theme.MiBandNfcTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiBandNfcTheme {
                AppNav()
            }
        }
    }
}
