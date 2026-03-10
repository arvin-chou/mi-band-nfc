package com.mibandnfc.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class BlePermissionState(
    val allGranted: Boolean,
    val shouldShowRationale: Boolean,
    val launchPermissionRequest: () -> Unit,
)

private fun blePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun allPermissionsGranted(context: Context): Boolean =
    blePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun rememberBlePermissionState(
    onGranted: () -> Unit = {},
    onDenied: () -> Unit = {},
): BlePermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(allPermissionsGranted(context)) }
    var rationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = results.values.all { it }
        if (granted) onGranted() else onDenied()
    }

    return BlePermissionState(
        allGranted = granted,
        shouldShowRationale = rationale,
        launchPermissionRequest = {
            if (allPermissionsGranted(context)) {
                granted = true
                onGranted()
            } else {
                launcher.launch(blePermissions())
            }
        },
    )
}

@Composable
fun BlePermissionGate(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var permanentlyDenied by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    val permissionState = rememberBlePermissionState(
        onDenied = { permanentlyDenied = true },
    )

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("需要藍牙權限") },
            text = {
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        "此應用需要藍牙掃描和連接權限才能與手環通訊。"
                    else
                        "此應用需要位置權限才能掃描附近的藍牙裝置。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionState.launchPermissionRequest()
                }) { Text("授予權限") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("取消") }
            },
        )
    }

    if (permissionState.allGranted) {
        content()
    } else if (permanentlyDenied) {
        PermissionDeniedScreen(onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            )
        })
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("需要藍牙權限才能使用", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { showRationale = true }) {
                Text("授予權限")
            }
        }
    }
}

@Composable
private fun PermissionDeniedScreen(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("藍牙權限已被拒絕", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "請在系統設定中手動開啟權限",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings) {
            Text("前往設定")
        }
    }
}
