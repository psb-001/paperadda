package com.example.ui.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.components.rememberLegacyStoragePermissionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Built-in PDF reader. Renders pages with [PdfDocument] so a student can read a
 * question paper without installing any third-party viewer, and can keep the
 * file by saving it to the public Downloads folder.
 *
 * Three things this screen gets right that a naive implementation does not:
 *
 *  - Zoomed pages are re-rendered at twice the width, so text is sharp instead
 *    of being an upscaled blur.
 *  - Panning is clamped to the page, so it can never be dragged out of sight.
 *  - Single-finger drags are only consumed while zoomed in, so the list still
 *    scrolls normally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    file: File,
    title: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val document = remember(file) { PdfDocument(file) }
    val listState = rememberLazyListState()

    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scopeSearch = rememberCoroutineScope()
    val scope = rememberCoroutineScope()
    // No-op on Android 10+; asks once on Android 9 and below.
    val ensureStoragePermission = rememberLegacyStoragePermissionRequest()

    var pageCount by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var aspects by remember { mutableStateOf<List<Float>>(emptyList()) }
    var showPageJump by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<Int>>(emptyList()) }
    var hitIndex by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var searchedOnce by remember { mutableStateOf(false) }
    var containerWidthPx by remember { mutableStateOf(0) }

    // One zoom level for the whole document, the way a normal reader behaves.
    var scale by remember { mutableFloatStateOf(1f) }

    // One renderer for the screen's lifetime. It must be closed on *dispose* —
    // a LaunchedEffect(Unit) would close it the moment the screen first
    // composes, and every page would then fail with "Document is closed".
    val closeScope = remember(document) { CoroutineScope(Dispatchers.IO) }
    DisposableEffect(document) {
        onDispose {
            closeScope.launch {
                document.close()
                PdfBitmapCache.clear()
            }
        }
    }

    LaunchedEffect(file) {
        try {
            document.open()
            pageCount = document.pageCount
            aspects = document.pageAspects()
            if (pageCount == 0) loadError = "This PDF has no pages."
        } catch (e: PdfDocument.PasswordProtectedException) {
            loadError = "This PDF is password protected, so it cannot be opened here. " +
                "Try sharing it to another app."
        } catch (e: Exception) {
            loadError = e.message ?: "This PDF could not be opened."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Page $currentPage of $pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search this paper"
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val text = document.textOfPage(currentPage - 1)
                                if (text.isBlank()) {
                                    snackbar.showSnackbar("This page has no selectable text (it may be a scan).")
                                } else {
                                    clipboard.setText(AnnotatedString(text))
                                    snackbar.showSnackbar("Page ${currentPage} text copied.")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy this page's text")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val msg = viewModel.sharePdf(file)
                                snackbar.showSnackbar(msg)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                val msg = if (ensureStoragePermission()) {
                                    viewModel.saveToPublicDownloads(file)
                                } else {
                                    "Storage permission is needed to save to Downloads"
                                }
                                busy = false
                                snackbar.showSnackbar(msg)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Save to Downloads")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { if (it.width > 0) containerWidthPx = it.width },
            contentAlignment = Alignment.Center
        ) {
            if (searchOpen) {
                PdfSearchPanel(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    searching = searching,
                    searchedOnce = searchedOnce,
                    hits = searchHits,
                    hitIndex = hitIndex,
                    onSubmit = {
                        if (searchQuery.isBlank()) return@PdfSearchPanel
                        searching = true
                        searchedOnce = true
                        scopeSearch.launch {
                            val found = document.search(searchQuery)
                            searchHits = found
                            hitIndex = 0
                            searching = false
                            found.firstOrNull()?.let { first ->
                                scale = 1f
                                listState.scrollToItem(first)
                            }
                        }
                    },
                    onNext = {
                        if (searchHits.isEmpty()) return@PdfSearchPanel
                        hitIndex = (hitIndex + 1) % searchHits.size
                        scale = 1f
                        scopeSearch.launch { listState.scrollToItem(searchHits[hitIndex]) }
                    },
                    onPrevious = {
                        if (searchHits.isEmpty()) return@PdfSearchPanel
                        hitIndex = (hitIndex - 1 + searchHits.size) % searchHits.size
                        scale = 1f
                        scopeSearch.launch { listState.scrollToItem(searchHits[hitIndex]) }
                    },
                    onDismiss = {
                        searchOpen = false
                        searchQuery = ""
                        searchHits = emptyList()
                        searchedOnce = false
                    }
                )
            }

            when {
                loadError != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = loadError!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go back") }
                }

                pageCount == 0 -> CircularProgressIndicator(color = Color.White)

                else -> PdfDocumentStrip(
                    document = document,
                    pageCount = pageCount,
                    listState = listState,
                    containerWidthPx = containerWidthPx,
                    aspects = aspects,
                    scale = scale,
                    onScaleChange = { scale = it },
                    onPageChanged = { currentPage = it }
                )
            }

            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Color.White) }
            }

            // Zoom and page controls live at the bottom, not the top bar: with
            // them in the actions row the paper's own title and page number were
            // squeezed out of the bar entirely.
            PdfControlBar(
                scale = scale,
                currentPage = currentPage,
                pageCount = pageCount,
                onZoomIn = { scale = (scale * ZOOM_STEP).coerceAtMost(MAX_SCALE) },
                onZoomOut = { scale = (scale / ZOOM_STEP).coerceAtLeast(MIN_SCALE) },
                onPageClick = { showPageJump = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }

    if (showPageJump) {
        PageJumpDialog(
            currentPage = currentPage,
            pageCount = pageCount,
            onDismiss = { showPageJump = false },
            onGo = { target ->
                showPageJump = false
                scale = 1f
                scope.launch {
                    listState.scrollToItem((target - 1).coerceIn(0, pageCount - 1))
                }
            }
        )
    }
}

/**
 * The document is drawn as one continuous vertical strip, the way a paper
 * actually reads.
 *
 * The earlier version made every page its own card in a list: rounded corners,
 * 12dp gaps, horizontal margins and a per-page zoom that clipped each page
 * inside its own slot. That reads as a stack of separate screens rather than a
 * document, and zooming a page just cropped it.
 *
 * Here the pages are butted together with no gaps at all, and zoom applies to
 * the whole strip. When the document is unzoomed the list scrolls; when it is
 * magnified the list locks and the same gesture pans the document, so a single
 * finger never has to choose between the two.
 */
@Composable
private fun PdfDocumentStrip(
    document: PdfDocument,
    pageCount: Int,
    listState: LazyListState,
    containerWidthPx: Int,
    aspects: List<Float>,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onPageChanged: (Int) -> Unit
) {
    val firstVisible = listState.firstVisibleItemIndex
    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: firstVisible
    // Clamped: the old code let the preload window run past either end, which
    // asked the engine for page -1 and painted a "Page out of range" error.
    val start = (firstVisible - PRELOAD).coerceAtLeast(0)
    val end = (lastVisible + PRELOAD).coerceAtMost(pageCount - 1)

    LaunchedEffect(firstVisible) { onPageChanged(firstVisible + 1) }

    val width = containerWidthPx.takeIf { it > 0 } ?: FALLBACK_WIDTH_PX
    val zoomed = scale > 1.001f

    // Render a little ahead so a fast scroll does not outrun the decoder.
    val bucket = bucketFor(scale)
    LaunchedEffect(firstVisible, width, bucket) {
        for (ahead in 1..PREFETCH) {
            val target = firstVisible + ahead
            if (target in 0 until pageCount) {
                val w = (width * bucket).roundToInt()
                if (PdfBitmapCache.get(target, w) == null) {
                    runCatching { document.renderPage(target, w) }
                        .onSuccess { PdfBitmapCache.put(target, w, it) }
                }
            }
        }
    }

    // Unzoomed document height, used to clamp panning at high zoom.
    val docHeightPx = remember(aspects, width) {
        aspects.fold(0f) { acc: Float, aspect: Float -> acc + width.toFloat() * aspect }
            .coerceAtLeast(1f)
    }
    val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val maxPanX = ((width * scale) - width).coerceAtLeast(0f) / 2f
    val maxPanY = ((docHeightPx * scale) - viewportHeight).coerceAtLeast(0f) / 2f

    val panState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        onScaleChange(next)
        if (next > 1.001f) {
            panX = (panX + panChange.x).coerceIn(-maxPanX, maxPanX)
            panY = (panY + panChange.y).coerceIn(-maxPanY, maxPanY)
        } else {
            panX = 0f
            panY = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            // When magnified the list must not also scroll, or one vertical
            // drag would fight the pan.
            .transformable(panState, canPan = { zoomed })
            .testTag("pdf_document")
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !zoomed,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panX
                    translationY = panY
                }
                // Tap anywhere to reset, matching every other reader.
                .combinedClickable(
                    onClick = {},
                    onDoubleClick = {
                        if (zoomed) {
                            onScaleChange(1f)
                            panX = 0f
                            panY = 0f
                        } else {
                            onScaleChange(2f)
                        }
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(count = end - start + 1, key = { start + it }) { offset ->
                PdfPage(
                    document = document,
                    pageIndex = start + offset,
                    containerWidthPx = width
                )
            }
        }
    }
}

/**
 * A single page, full-bleed: no margins, no corner rounding, no per-page clip.
 * That is what makes consecutive pages read as one continuous sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfPage(
    document: PdfDocument,
    pageIndex: Int,
    containerWidthPx: Int
) {
    val renderWidth = containerWidthPx.coerceAtLeast(MIN_RENDER_PX)
    var bitmap by remember(pageIndex, renderWidth) {
        mutableStateOf(PdfBitmapCache.get(pageIndex, renderWidth))
    }
    var renderError by remember(pageIndex, renderWidth) { mutableStateOf<String?>(null) }

    LaunchedEffect(pageIndex, renderWidth) {
        if (bitmap != null) return@LaunchedEffect
        renderError = null
        runCatching { document.renderPage(pageIndex, renderWidth) }
            .onSuccess {
                PdfBitmapCache.put(pageIndex, renderWidth, it)
                bitmap = it
            }
            .onFailure { renderError = it.message ?: it.javaClass.simpleName }
    }

    when (val bmp = bitmap) {
        null -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentAlignment = Alignment.Center
        ) {
            val error = renderError
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }

        else -> Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${pageIndex + 1}",
            // Fit the natural page box so the strip keeps the paper's shape.
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pdf_page_$pageIndex")
        )
    }
}

/** Bottom overlay with zoom and page controls, so the top bar can show the title. */
@Composable
private fun PdfControlBar(
    scale: Float,
    currentPage: Int,
    pageCount: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onPageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        IconButton(onClick = onZoomOut, enabled = scale > MIN_SCALE) {
            Icon(Icons.Filled.Remove, contentDescription = "Zoom out", tint = Color.White)
        }
        Text(
            text = "${(scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.width(52.dp)
        )
        IconButton(onClick = onZoomIn, enabled = scale < MAX_SCALE) {
            Icon(Icons.Filled.Add, contentDescription = "Zoom in", tint = Color.White)
        }
        if (pageCount > 1) {
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onPageClick) {
                Text(
                    text = "$currentPage / $pageCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun PdfSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    searchedOnce: Boolean,
    hits: List<Int>,
    hitIndex: Int,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text("Find in this paper") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSubmit() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("pdf_search_field")
            )
            IconButton(onClick = onSubmit, enabled = !searching && query.isNotBlank()) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
        }

        if (searching) {
            Text(
                text = "Searching…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (searchedOnce) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        hits.isEmpty() -> "Not found. Scanned papers have no text to search."
                        else -> "Page ${hits[hitIndex] + 1} of ${hits.size} found"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hits.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                if (hits.isNotEmpty()) {
                    TextButton(onClick = onPrevious) { Text("Prev") }
                    TextButton(onClick = onNext) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun PageJumpDialog(
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentPage.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    isError = text.toIntOrNull() !in 1..pageCount,
                    supportingText = {
                        Text("This paper has $pageCount page${if (pageCount == 1) "" else "s"}.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("page_jump_field")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toIntOrNull()?.let(onGo) },
                enabled = text.toIntOrNull() in 1..pageCount
            ) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** 2x resolution once the page is magnified enough to show it. */
private fun bucketFor(scale: Float): Float = if (scale > HI_RES_THRESHOLD) 2f else 1f

private const val PRELOAD = 1
private const val PREFETCH = 2
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f
private const val ZOOM_STEP = 1.4f
private const val HI_RES_THRESHOLD = 1.15f
private const val MIN_RENDER_PX = 320
private const val FALLBACK_WIDTH_PX = 1080
