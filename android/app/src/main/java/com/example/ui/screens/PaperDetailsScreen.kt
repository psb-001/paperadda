package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.data.model.QuestionPaper
import com.example.data.model.branchDisplayName
import com.example.ui.MainViewModel
import com.example.ui.components.rememberLegacyStoragePermissionRequest
import com.example.ui.LegalLinks
import com.example.ui.components.M3EmptyState
import com.example.ui.components.M3TopBar
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection

@Composable
fun PaperDetailsScreen(
    viewModel: MainViewModel,
    paperId: String,
    modifier: Modifier = Modifier
) {
    // Re-read the catalog when a sync lands.
    val catalogRev by viewModel.catalogRevision.collectAsStateWithLifecycle()
    val paper = viewModel.getPaperById(paperId)
    if (paper == null) {
        PaperUnavailableScreen(viewModel)
        return
    }
    val isDownloading = viewModel.downloadingPaperId == paper.id
    val uriHandler = LocalUriHandler.current
    val hasPdf = paper.storagePath.isNotBlank()
    val downloadedFile = viewModel.downloadedFileFor(paper)
    val isOnDevice = downloadedFile != null
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by rememberSaveable { mutableStateOf(false) }

    // No-op on Android 10+; asks once on Android 9 and below.
    val ensureStoragePermission = rememberLegacyStoragePermissionRequest()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = "Paper Details",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = "Go back",
                onNavigationClick = {
                    viewModel.navigateBack()
                },
                testTag = "paper_details_top_bar"
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        // Content starts at the top (never vertically centered: centered +
        // scrollable content gets its top cut off on small screens).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Paper Information Card (20dp rounded corners as per M3 Expressive card spec)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(20.dp)
                        .testTag("paper_info_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = paper.displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${branchDisplayName(paper.branchCode)} · ${paper.subjectName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metadata pills wrap onto multiple lines on narrow
                        // screens instead of squeezing into one cramped row.
                        MetadataPills(paper = paper)

                        if (paper.sampleQuestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Sample Questions Preview:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            paper.sampleQuestions.take(2).forEach { question ->
                                Text(
                                    text = question,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Two clear actions (the old 4-button group had a dead
                // "exam title" button and a confusing branch shortcut).
                // Read in PaperAdda's own reader — no third-party PDF app needed.
                ElevatedButton(
                    onClick = { viewModel.openPaperInReader(paper) },
                    enabled = !isDownloading && hasPdf,
                    colors = ButtonDefaults.elevatedButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_open_paper")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Article,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isDownloading -> "Downloading…"
                            isOnDevice -> "Read question paper"
                            else -> "Download & read"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (!hasPdf) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No PDF has been uploaded for this paper yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isOnDevice) {
                    // The file is already on the device, so let the student keep
                    // it: the app's own folder is hidden from file managers.
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val msg = if (ensureStoragePermission()) {
                                        viewModel.saveToPublicDownloads(downloadedFile)
                                    } else {
                                        "Storage permission is needed to save to Downloads"
                                    }
                                    snackbar.showSnackbar(msg)
                                }
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_save_downloads")
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = { scope.launch { snackbar.showSnackbar(viewModel.sharePdf(downloadedFile)) } },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_share_paper")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Saved copies live in Downloads/PaperAdda so you can find them in any file manager.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.navigateTo(
                            AppScreen.QuestionPapers(
                                subjectName = paper.subjectName,
                                branchCode = paper.branchCode
                            ),
                            NavigationDirection.FORWARD
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_all_subject_papers")
                ) {
                    Text(
                        text = "All ${paper.subjectName} papers",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = LegalLinks.COPYRIGHT_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = { uriHandler.openUri(LegalLinks.TERMS_AND_COPYRIGHT) }) {
                    Text("Terms & Copyright Notice")
                }
        }
}
}

@Composable
private fun PaperUnavailableScreen(viewModel: MainViewModel) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = "Paper Details",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = "Go back",
                onNavigationClick = { viewModel.navigateBack() },
                testTag = "paper_details_top_bar"
            )
        }
    ) { innerPadding ->
        M3EmptyState(
            icon = Icons.Filled.Description,
            title = "Paper unavailable",
            message = "This paper is no longer published. Go back and choose another paper.",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            actionLabel = "Go back",
            onAction = { viewModel.navigateBack() }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataPills(
    paper: QuestionPaper,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetadataPill(label = "Year", value = paper.year)
        MetadataPill(label = "Format", value = paper.fileFormat)
        // Only show what the admin actually recorded. A blank duration or an
        // unset marks value must not be rendered as a made-up number.
        if (paper.duration.isNotBlank()) {
            MetadataPill(label = "Time", value = paper.duration)
        }
        if (paper.maxMarks > 0) {
            MetadataPill(label = "Marks", value = paper.maxMarks.toString())
        }
    }
}

@Composable
private fun MetadataPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
