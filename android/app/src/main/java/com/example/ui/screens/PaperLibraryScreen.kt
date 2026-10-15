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
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AcademicYear
import com.example.data.model.BRANCH_COMMON
import com.example.data.model.Subject
import com.example.data.model.branchDisplayName
import com.example.ui.MainViewModel
import com.example.ui.components.M3EmptyState
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3StackedListItem
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination
import com.example.ui.components.SubjectIcons
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection

@Composable
fun PaperLibraryScreen(
    viewModel: MainViewModel,
    branchCode: String = "ENTC",
    academicYear: Int = 1,
    modifier: Modifier = Modifier
) {
    // Re-read the catalog when a sync lands.
    val catalogRev by viewModel.catalogRevision.collectAsStateWithLifecycle()
    // Branch is already fixed by the previous screen (year selection), so there
    // is deliberately NO branch tab row here — switching branches mid-list
    // caused users to open/download papers from the wrong branch.
    // Re-filtering the whole catalogue on every recomposition was pure waste:
    // this list only changes when the catalogue revision does.
    val subjects = remember(branchCode, academicYear, catalogRev) {
        viewModel.getSubjectsForBranchAndYear(branchCode, academicYear)
    }
    val yearLabel = AcademicYear.labelFor(academicYear)
    val isCommon = branchCode.equals(BRANCH_COMMON, ignoreCase = true)
    val scopeTitle = if (isCommon) yearLabel else branchDisplayName(branchCode)
    val countText = if (subjects.size == 1) "1 subject" else "${subjects.size} subjects"
    val scopeSubtitle = if (isCommon) {
        "Common to all branches · $countText"
    } else {
        "$yearLabel · $countText"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = "Paper Library",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = "Go back",
                onNavigationClick = {
                    viewModel.navigateBack()
                },
                actionIcon = Icons.Filled.Search,
                actionIconContentDescription = "Search subjects",
                onActionClick = {
                    viewModel.isSearchOpen = true
                },
                testTag = "paper_library_top_bar"
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
        // Pull down to re-sync the admin catalog (same path as launch sync).
        PullToRefreshBox(
            isRefreshing = viewModel.isSyncing,
            onRefresh = { viewModel.retrySync() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Near the top: fixed scope + year header (no tabs — the scope is
            // already chosen, so it cannot be switched by accident here).
            Text(
                text = scopeTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("subject_branch_title")
            )
            Text(
                text = scopeSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (subjects.isEmpty()) {
                M3EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "No subjects published",
                    message = "Subjects for $scopeTitle will appear here as soon as an admin publishes them.",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    actionLabel = "Go Back",
                    onAction = { viewModel.navigateBack() }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    subjects.forEachIndexed { index, subject ->
                        val icon: ImageVector = SubjectIcons.resolve(
                            subject.iconName,
                            subject.name
                        )

                        M3StackedListItem(
                            title = subject.name,
                            supportingText = subject.supportingText,
                            leadingIcon = icon,
                            index = index,
                            totalCount = subjects.size,
                            testTag = "subject_item_${subject.id}",
                            onClick = {
                                viewModel.navigateTo(
                                    AppScreen.QuestionPapers(
                                        subjectName = subject.name,
                                        branchCode = subject.branchCode
                                    ),
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
    }
}
