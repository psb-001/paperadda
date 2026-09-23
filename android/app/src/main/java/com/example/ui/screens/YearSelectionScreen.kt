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
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AcademicYear
import com.example.ui.MainViewModel
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3StackedListItem
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection

@Composable
fun YearSelectionScreen(
    viewModel: MainViewModel,
    branchCode: String = "ENTC",
    modifier: Modifier = Modifier
) {
    // Refresh counts when the synced catalog lands.
    val catalogRev by viewModel.catalogRevision.collectAsStateWithLifecycle()
    val branch = viewModel.getBranch(branchCode)
    val branchTitle = branch?.code ?: branchCode
    val branchSubtitle = branch?.fullName ?: branchCode

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = branchTitle,
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
                testTag = "year_selection_top_bar"
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Near top, aligned left: bold text "Choose your year" at 24sp
            Text(
                text = "Choose your year",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 32.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("choose_year_title")
            )

            Text(
                text = branchSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )

            Text(
                text = "First Year is common to all branches — open it from Home.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Years 2–4 only: first year lives on the dedicated Home card.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                AcademicYear.BRANCH_ONLY.forEachIndexed { index, academicYear ->
                    val subjectCount =
                        viewModel.getSubjectsForBranchAndYear(branchCode, academicYear.year).size
                    val countText =
                        if (subjectCount == 1) "1 subject" else "$subjectCount subjects"

                    M3StackedListItem(
                        title = academicYear.label,
                        supportingText = "$branchTitle · $countText",
                        leadingIcon = Icons.Filled.School,
                        index = index,
                        totalCount = AcademicYear.BRANCH_ONLY.size,
                        testTag = "year_item_${academicYear.year}",
                        onClick = {
                            viewModel.navigateTo(
                                AppScreen.YearMenu(
                                    branchCode = branchCode,
                                    academicYear = academicYear.year
                                ),
                                NavigationDirection.FORWARD
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
