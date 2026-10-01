package com.example.ui.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.repository.FileExporter
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

private fun Context.isLegacyWritePermissionGranted(): Boolean =
    ContextCompat.checkSelfPermission(this, FileExporter.LEGACY_WRITE_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Returns a suspend function that makes sure the app may write to the public
 * Downloads folder, asking the user if it has to.
 *
 * Android 10 added MediaStore.Downloads, so saving needs no permission there and
 * this resolves immediately. On Android 9 and below there is no equivalent, so
 * the copy has to be written to the public directory, which needs
 * WRITE_EXTERNAL_STORAGE. The manifest caps that permission at API 28, so no
 * modern device is ever prompted.
 */
@Composable
fun rememberLegacyStoragePermissionRequest(): suspend () -> Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.isLegacyWritePermissionGranted()) }
    var pending by remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        pending?.resume(result)
        pending = null
    }

    return remember(context, granted) {
        suspend {
            when {
                granted -> true
                // Android 10+ saves through MediaStore and needs no permission.
                !FileExporter.needsLegacyWritePermission() -> true
                else -> suspendCancellableCoroutine { cont ->
                    pending = cont
                    // If the caller is cancelled (screen closed mid-prompt) the
                    // continuation is released instead of leaking a hang.
                    cont.invokeOnCancellation { pending = null }
                    launcher.launch(FileExporter.LEGACY_WRITE_PERMISSION)
                }
            }
        }
    }
}
