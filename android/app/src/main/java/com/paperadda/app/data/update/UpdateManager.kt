package com.paperadda.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.paperadda.app.data.model.AppRelease
import com.paperadda.app.data.repository.UpdateInstaller
import com.paperadda.app.data.repository.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Everything about in-app updates, owned outside the ViewModel so it can be
 * unit-tested without a screen attached.
 *
 * PaperAdda ships as a sideloaded APK, so the OS never says a new build exists:
 * the app asks GitHub Releases instead. A release can be marked required
 * (`<!-- min_supported: 1.5 -->` in the release body), which makes the update
 * dialog impossible to dismiss.
 *
 * State is exposed as [StateFlow]s; the ViewModel mirrors them into Compose
 * state so existing screens keep working unchanged.
 */
class UpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: UpdateRepository = UpdateRepository(context),
    private val installer: UpdateInstaller = UpdateInstaller(context)
) {
    private val _availableUpdate = MutableStateFlow<AppRelease?>(null)
    val availableUpdate: StateFlow<AppRelease?> = _availableUpdate.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateInstaller.DownloadState>(
        UpdateInstaller.DownloadState.Idle
    )
    val downloadState: StateFlow<UpdateInstaller.DownloadState> = _downloadState.asStateFlow()

    private val _isRequired = MutableStateFlow(false)
    val isRequired: StateFlow<Boolean> = _isRequired.asStateFlow()

    private var downloadWatch: Job? = null
    private var downloadId: Long? = null

    val installedVersionName: String get() = repository.installedVersionName()
    val installedVersionCode: Int get() = repository.installedVersionCode()

    /** Called on launch; throttled so it does not hit the network every start. */
    fun checkInBackground() {
        if (_isChecking.value) return
        scope.launch {
            if (_isChecking.value) return@launch
            _isChecking.value = true
            try {
                _availableUpdate.value = repository.checkForUpdate(force = false)
            } finally {
                _isChecking.value = false
            }
            syncRequiredFlagAndShow()
        }
    }

    /** Manual "Check for updates" — always asks, and always answers. */
    fun checkManually() {
        if (_isChecking.value) return
        scope.launch {
            _isChecking.value = true
            _message.value = null
            val found = try {
                repository.checkForUpdate(force = true)
            } finally {
                _isChecking.value = false
            }
            if (found == null) {
                _message.value = "You're on the latest version (${repository.installedVersionName()})."
            } else {
                _availableUpdate.value = found
                syncRequiredFlagAndShow()
            }
        }
    }

    fun openDialog() {
        if (_availableUpdate.value != null) _showDialog.value = true
    }

    /** A required release cannot be waved away; the user must update first. */
    fun dismissDialog() {
        if (!_isRequired.value) _showDialog.value = false
    }

    fun dismissMessage() {
        _message.value = null
    }

    /**
     * Starts the APK download, or routes to the install-permission screen when
     * it is blocked. Tapping again while a download runs is a no-op, so a
     * double tap cannot queue two copies of the same APK.
     */
    fun download() {
        val release = _availableUpdate.value ?: return
        if (_downloadState.value is UpdateInstaller.DownloadState.Running) return
        if (!installer.canInstallPackages()) {
            _message.value = "Allow PaperAdda to install apps, then try again."
            installer.openInstallPermissionSettings()
            return
        }
        // Clear a previous failure before starting over.
        downloadId?.let { installer.clear(it) }
        downloadId = null
        clearQueuedDownload()
        _message.value = null

        when (val result = installer.startDownload(release.apkUrl, apkFileName(release))) {
            is UpdateInstaller.Result.Started -> {
                downloadId = result.downloadId
                _downloadState.value = UpdateInstaller.DownloadState.Running(0, -1)
                watchDownload(result.downloadId)
            }
            is UpdateInstaller.Result.Failed ->
                _downloadState.value = UpdateInstaller.DownloadState.Failed(result.message)
        }
    }

    /** Abandons the current download and starts again. */
    fun retryDownload() {
        downloadWatch?.cancel()
        downloadId?.let { installer.clear(it) }
        downloadId = null
        clearQueuedDownload()
        download()
    }

    private fun clearQueuedDownload() {
        downloadWatch?.cancel()
        downloadWatch = null
        _downloadState.value = UpdateInstaller.DownloadState.Idle
    }

    /**
     * Polls the system download queue until the file is ready or has failed, so
     * the dialog can never sit on a spinner that means nothing.
     */
    private fun watchDownload(downloadId: Long) {
        downloadWatch?.cancel()
        downloadWatch = scope.launch {
            while (isActive) {
                when (val state = installer.query(downloadId)) {
                    is UpdateInstaller.DownloadState.Ready -> {
                        _downloadState.value = UpdateInstaller.DownloadState.Ready
                        return@launch
                    }
                    is UpdateInstaller.DownloadState.Failed -> {
                        _downloadState.value = state
                        _message.value = state.message
                        return@launch
                    }
                    // An unknown id means the system dropped it (reboot,
                    // cleared downloads); stop rather than spin on Idle.
                    is UpdateInstaller.DownloadState.Idle -> {
                        _downloadState.value = UpdateInstaller.DownloadState.Failed(
                            "The download was cancelled. Try again."
                        )
                        return@launch
                    }
                    is UpdateInstaller.DownloadState.Running ->
                        _downloadState.value = state
                }
                delay(400)
            }
        }
    }

    /** Hands the finished download to the system installer. */
    fun installDownloadedUpdate() {
        val release = _availableUpdate.value ?: return
        val id = downloadId ?: return
        val state = _downloadState.value
        // Only attempt this once the system has reported the file ready, or the
        // failure looks like a network fault.
        if (state !is UpdateInstaller.DownloadState.Ready) {
            _message.value = if (state is UpdateInstaller.DownloadState.Failed) {
                state.message
            } else {
                "Still downloading. This will take a moment on a slow connection."
            }
            return
        }
        if (installer.install(id, apkFileName(release))) {
            _message.value = "Opening the installer…"
        } else {
            val failure = "The downloaded file could not be opened. Try downloading it again."
            _downloadState.value = UpdateInstaller.DownloadState.Failed(failure)
            _message.value = failure
        }
    }

    fun openInBrowser() {
        val release = _availableUpdate.value ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            _message.value = "No browser available to open the download link."
        }
    }

    private fun syncRequiredFlagAndShow() {
        val release = _availableUpdate.value ?: return
        _isRequired.value = release.blocks(repository.installedVersionName())
        _showDialog.value = true
    }
}

/**
 * The APK file name for [release]. The version comes from a GitHub release
 * (not from us) and is written straight into a DownloadManager destination
 * path, so every character outside the safe set is replaced. Top-level +
 * internal so the rule can be tested without Android.
 */
internal fun apkFileName(release: AppRelease): String =
    "paperadda-${release.version.replace(Regex("[^A-Za-z0-9._-]"), "-")}.apk"
