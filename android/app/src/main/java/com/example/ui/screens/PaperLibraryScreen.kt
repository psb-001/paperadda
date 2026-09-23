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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AcademicYear
import com.example.data.model.Subject
import com.example.data.model.branchDisplayName
import com.example.ui.MainViewModel
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3StackedListItem
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination
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
    val subjects = viewModel.getSubjectsForBranchAndYear(branchCode, academicYear)
    val yearLabel = AcademicYear.labelFor(academicYear)

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Near the top: fixed scope + year header (no tabs — the scope is
            // already chosen, so it cannot be switched by accident here).
            Text(
                text = branchDisplayName(branchCode),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("subject_branch_title")
            )
            Text(
                text = "$yearLabel · ${if (subjects.size == 1) "1 subject" else "${subjects.size} subjects"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // In the middle: list of items with 3dp gaps (M3 stacked list)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                subjects.forEachIndexed { index, subject ->
                    val icon: ImageVector = when (subject.iconName) {
                        "graphic_eq" -> Icons.Filled.GraphicEq
                        "memory" -> Icons.Filled.Memory
                        "cell_tower" -> Icons.Filled.CellTower
                        "smart_toy" -> Icons.Filled.SmartToy
                        "computer" -> Icons.Filled.Computer
                        "language" -> Icons.Filled.Language
                        else -> Icons.Filled.GraphicEq
                    }

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
