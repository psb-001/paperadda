package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.branchDisplayName
import com.example.ui.MainViewModel
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3StackedListItem
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection

@Composable
fun SavedScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val savedPapers by viewModel.savedPapers.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = "Saved Papers",
                actionIcon = Icons.Filled.Search,
                actionIconContentDescription = "Search",
                onActionClick = {
                    viewModel.isSearchOpen = true
                },
                testTag = "saved_top_bar"
            )
        },
        bottomBar = {
            M3NavBar(
                currentDestination = NavDestination.SAVED,
                onDestinationSelected = { destination ->
                    viewModel.selectBottomNav(destination)
                }
            )
        }
    ) { innerPadding ->
        if (savedPapers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No saved question papers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bookmark papers to access them offline anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                savedPapers.forEachIndexed { index, paperEntity ->
                    M3StackedListItem(
                        title = paperEntity.title,
                        supportingText = "${branchDisplayName(paperEntity.branchCode)} · ${paperEntity.subjectName} · ${paperEntity.year}",
                        leadingIcon = Icons.Filled.Description,
                        index = index,
                        totalCount = savedPapers.size,
                        testTag = "saved_item_${paperEntity.paperId}",
                        onClick = {
                            viewModel.navigateTo(
                                AppScreen.PaperDetails(paperEntity.paperId),
                                NavigationDirection.FORWARD
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
