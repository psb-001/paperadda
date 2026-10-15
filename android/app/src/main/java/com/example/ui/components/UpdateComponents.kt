package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppRelease
import com.example.data.repository.UpdateInstaller

/**
 * Shown when the backend reports a build newer than the one installed.
 * A required release hides "Later" — that is the escape hatch for a fix the
 * user genuinely cannot keep going without.
 *
 * The button is driven by the real [UpdateInstaller.DownloadState] rather than
 * by "did we start a download". That distinction is the whole point: showing
 * "Install update" beside a spinner while bytes were still arriving made the app
 * look stuck on the network, and tapping it only produced "isn't finished yet".
 */
@Composable
fun UpdateDialog(
    release: AppRelease,
    installedVersionName: String,
    isRequired: Boolean,
    downloadState: UpdateInstaller.DownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onDismiss: () -> Unit
) {
    val running = downloadState as? UpdateInstaller.DownloadState.Running
    val ready = downloadState is UpdateInstaller.DownloadState.Ready
    val failed = downloadState as? UpdateInstaller.DownloadState.Failed

    AlertDialog(
        onDismissRequest = { if (!isRequired) onDismiss() },
        confirmButton = {
            Button(
                onClick = when {
                    ready -> onInstall
                    failed != null -> onRetry
                    else -> onDownload
                },
                // While bytes are still moving there is nothing useful to press.
                enabled = running == null,
                shape = MaterialTheme.shapes.large
            ) {
                when {
                    ready -> {
                        Icon(
                            Icons.Filled.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Install update")
                    }
                    failed != null -> {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Try again")
                    }
                    running != null -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // A real percentage when the server sent a size, so a
                        // slow download visibly advances instead of looking hung.
                        Text(running.percent?.let { "Downloading $it%" } ?: "Downloading…")
                    }
                    else -> {
                        Icon(
                            Icons.Filled.SystemUpdateAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update now")
                    }
                }
            }
        },
        dismissButton = {
            if (isRequired) {
                TextButton(onClick = onOpenInBrowser) { Text("Open link") }
            } else {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SystemUpdateAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRequired) "Update required" else "Update available",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Version ${release.version} is out" +
                        if (isRequired) " and is needed to keep using PaperAdda."
                        else ". You have $installedVersionName.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (ready) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Download finished. Tap Install update to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (failed != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = failed.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (release.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "What's new",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    )
}

/**
 * Quiet, always-visible entry point on Home. A dialog on launch is easy to
 * dismiss and then forget, so the offer stays visible until it is handled.
 */
@Composable
fun UpdateBanner(
    release: AppRelease,
    isRequired: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isRequired) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
            )
            .padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.SystemUpdateAlt,
            contentDescription = null,
            tint = if (isRequired) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isRequired) "UPDATE REQUIRED" else "UPDATE",
                style = MaterialTheme.typography.labelSmall,
                color = if (isRequired) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = if (isRequired) "Update PaperAdda to continue"
                else "Version ${release.version} is available",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isRequired) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Open update",
            tint = if (isRequired) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
