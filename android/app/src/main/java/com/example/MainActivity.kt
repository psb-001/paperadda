package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.pdf.PdfDocument
import com.example.ui.components.M3EmptyState
import com.example.ui.components.UpdateDialog
import com.example.ui.pdf.PdfReaderScreen
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection
import com.example.ui.screens.FeedbackScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InfoBottomSheet
import com.example.ui.screens.PaperDetailsScreen
import com.example.ui.screens.PaperLibraryScreen
import com.example.ui.screens.QuestionPapersScreen
import com.example.ui.screens.RequestsScreen
import com.example.ui.screens.SearchDialog
import com.example.ui.screens.YearMenuScreen
import com.example.ui.screens.YearNotesScreen
import com.example.ui.screens.YearSelectionScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // PDFBox needs a Context to load its bundled fonts before any page can
        // be drawn, and the reader renders off the main thread.
        PdfDocument.init(applicationContext)
        setContent {
            MyApplicationTheme {
                PaperAddaApp()
            }
        }
    }
}

@Composable
fun PaperAddaApp(
    viewModel: MainViewModel = viewModel()
) {
    // System back button / gesture: walk back inside the app first.
    // Only the Home screen lets the system exit the app.
    BackHandler(enabled = viewModel.backStack.size > 1 || viewModel.currentScreen !is AppScreen.Home) {
        viewModel.navigateBack()
    }

    // Global sync-error banner: catalog refresh failures are surfaced here
    // (with a Retry action) instead of failing silently.
    val snackbarHostState = remember { SnackbarHostState() }
    val syncError = viewModel.syncErrorMessage
    LaunchedEffect(syncError) {
        if (syncError != null) {
            val result = snackbarHostState.showSnackbar(
                message = syncError,
                actionLabel = "Retry",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retrySync()
            } else {
                viewModel.dismissSyncError()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AnimatedContent(
            targetState = viewModel.currentScreen,
            transitionSpec = {
                val springSpec = spring<Float>(dampingRatio = 0.75f, stiffness = 380f)
                val intOffsetSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.75f, stiffness = 380f)

                when (viewModel.navDirection) {
                    NavigationDirection.FORWARD -> {
                        // Slide in from the right when tapped
                        (slideInHorizontally(
                            animationSpec = intOffsetSpring,
                            initialOffsetX = { fullWidth -> fullWidth }
                        ) + fadeIn(animationSpec = springSpec)) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = intOffsetSpring,
                                    targetOffsetX = { fullWidth -> -fullWidth / 3 }
                                ) + fadeOut(animationSpec = springSpec))
                    }
                    NavigationDirection.BACK -> {
                        // Playing the entry transition in reverse (slide back to right)
                        (slideInHorizontally(
                            animationSpec = intOffsetSpring,
                            initialOffsetX = { fullWidth -> -fullWidth / 3 }
                        ) + fadeIn(animationSpec = springSpec)) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = intOffsetSpring,
                                    targetOffsetX = { fullWidth -> fullWidth }
                                ) + fadeOut(animationSpec = springSpec))
                    }
                    NavigationDirection.TAB_SWITCH -> {
                        // Opens with a fade
                        fadeIn(animationSpec = springSpec) togetherWith fadeOut(animationSpec = springSpec)
                    }
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                is AppScreen.Home -> {
                    HomeScreen(viewModel = viewModel)
                }
                is AppScreen.BranchYears -> {
                    YearSelectionScreen(
                        viewModel = viewModel,
                        branchCode = screen.branchCode
                    )
                }
                is AppScreen.YearMenu -> {
                    YearMenuScreen(
                        viewModel = viewModel,
                        branchCode = screen.branchCode,
                        academicYear = screen.academicYear
                    )
                }
                is AppScreen.YearNotes -> {
                    YearNotesScreen(
                        viewModel = viewModel,
                        branchCode = screen.branchCode,
                        academicYear = screen.academicYear
                    )
                }
                is AppScreen.PaperLibrary -> {
                    PaperLibraryScreen(
                        viewModel = viewModel,
                        branchCode = screen.branchCode,
                        academicYear = screen.academicYear
                    )
                }
                is AppScreen.QuestionPapers -> {
                    QuestionPapersScreen(
                        viewModel = viewModel,
                        subjectName = screen.subjectName,
                        branchCode = screen.branchCode
                    )
                }
                is AppScreen.PaperDetails -> {
                    PaperDetailsScreen(
                        viewModel = viewModel,
                        paperId = screen.paperId
                    )
                }
                is AppScreen.Requests -> {
                    RequestsScreen(viewModel = viewModel)
                }
                is AppScreen.PdfReader -> {
                    val file = java.io.File(screen.filePath)
                    if (file.exists()) {
                        PdfReaderScreen(
                            file = file,
                            title = screen.title,
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                    } else {
                        LaunchedEffect(screen.filePath) { viewModel.navigateBack() }
                        M3EmptyState(
                            icon = Icons.Filled.Description,
                            title = "File not found",
                            message = "This PDF is no longer on the device.",
                            actionLabel = "Go back",
                            onAction = { viewModel.navigateBack() }
                        )
                    }
                }
                is AppScreen.Feedback -> {
                    FeedbackScreen(viewModel = viewModel)
                }
            }
        }

        // Global Search Dialog
        if (viewModel.isSearchOpen) {
            SearchDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.isSearchOpen = false }
            )
        }

        // About / Info bottom sheet
        if (viewModel.isInfoSheetOpen) {
            InfoBottomSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.isInfoSheetOpen = false }
            )
        }

        // Update prompt. Shown above everything because an out-of-date build can
        // be a required release, in which case it has no dismiss button.
        val update = viewModel.availableUpdate
        if (update != null && viewModel.showUpdateDialog) {
            UpdateDialog(
                release = update,
                installedVersionName = viewModel.installedVersionName,
                isRequired = viewModel.isUpdateRequired,
                downloadState = viewModel.updateDownloadState,
                onDownload = { viewModel.downloadUpdate() },
                onInstall = { viewModel.installDownloadedUpdate() },
                onRetry = { viewModel.retryUpdateDownload() },
                onOpenInBrowser = { viewModel.openUpdateInBrowser() },
                onDismiss = { viewModel.dismissUpdateDialog() }
            )
        }

        // Sync-error snackbar, above the bottom nav on every screen.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
