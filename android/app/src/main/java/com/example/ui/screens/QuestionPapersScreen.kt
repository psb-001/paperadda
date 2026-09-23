package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3StackedListItem
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection

@Composable
fun QuestionPapersScreen(
    viewModel: MainViewModel,
    subjectName: String,
    branchCode: String,
    modifier: Modifier = Modifier
) {
    // Re-read the catalog when a sync lands.
    val catalogRev by viewModel.catalogRevision.collectAsStateWithLifecycle()
    val papers = viewModel.getPapersForSubject(subjectName, branchCode)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = subjectName,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = "Go back",
                onNavigationClick = {
                    viewModel.navigateBack()
                },
                actionIcon = Icons.Filled.Search,
                actionIconContentDescription = "Search papers",
                onActionClick = {
                    viewModel.isSearchOpen = true
                },
                testTag = "question_papers_top_bar"
            )
        },
        bottomBar = {
            M3NavBar(
                currentDestination = NavDestination.HOME,
                onDestinationSelected = { destination ->
                    viewModel.selectBottomNav(destination)
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            papers.forEachIndexed { index, paper ->
                M3StackedListItem(
                    title = paper.title,
                    supportingText = paper.supportingText,
                    leadingIcon = Icons.Filled.Description,
                    index = index,
                    totalCount = papers.size,
                    testTag = "paper_item_${paper.id}",
                    onClick = {
                        viewModel.navigateTo(
                            AppScreen.PaperDetails(paper.id),
                            NavigationDirection.FORWARD
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
