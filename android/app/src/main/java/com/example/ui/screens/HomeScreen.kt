package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BRANCH_COMMON
import com.example.ui.MainViewModel
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3StackedListItem
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = "PaperAdda",
                navigationIcon = Icons.Filled.Menu,
                navigationIconContentDescription = "Open Menu",
                onNavigationClick = {
                    viewModel.isInfoSheetOpen = true
                },
                actionIcon = Icons.Filled.Search,
                actionIconContentDescription = "Search papers",
                onActionClick = {
                    viewModel.isSearchOpen = true
                },
                testTag = "home_top_bar"
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

            // First Year is shared by every branch — one dedicated entry so
            // students never pick a branch just to reach common content.
            Text(
                text = "Start here",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 32.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("choose_entry_title")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                M3StackedListItem(
                    title = "First Year",
                    supportingText = "Common to all branches · papers & notes",
                    leadingIcon = Icons.Filled.School,
                    index = 0,
                    totalCount = 5,
                    testTag = "first_year_item",
                    onClick = {
                        viewModel.navigateTo(
                            AppScreen.YearMenu(
                                branchCode = BRANCH_COMMON,
                                academicYear = 1
                            ),
                            NavigationDirection.FORWARD
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Choose your branch",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 32.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("choose_branch_title")
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Years 2–4 are branch-specific",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Middle: list of 4 items with 3dp gaps (M3 stacked list)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // 1. ENTC
                M3StackedListItem(
                    title = "ENTC",
                    supportingText = "Electronics and Telecommunication Engineering",
                    leadingIcon = Icons.Filled.Memory,
                    index = 1,
                    totalCount = 5,
                    testTag = "branch_item_ENTC",
                    onClick = {
                        viewModel.navigateTo(
                            AppScreen.BranchYears("ENTC"),
                            NavigationDirection.FORWARD
                        )
                    }
                )

                // 2. AIML
                M3StackedListItem(
                    title = "AIML",
                    supportingText = "Artificial Intelligence and Machine Learning",
                    leadingIcon = Icons.Filled.SmartToy,
                    index = 2,
                    totalCount = 5,
                    testTag = "branch_item_AIML",
                    onClick = {
                        viewModel.navigateTo(
                            AppScreen.BranchYears("AIML"),
                            NavigationDirection.FORWARD
                        )
                    }
                )

                // 3. CE
                M3StackedListItem(
                    title = "CE",
                    supportingText = "Computer Engineering",
                    leadingIcon = Icons.Filled.Computer,
                    index = 3,
                    totalCount = 5,
                    testTag = "branch_item_CE",
                    onClick = {
                        viewModel.navigateTo(
                            AppScreen.BranchYears("CE"),
                            NavigationDirection.FORWARD
                        )
                    }
                )

                // 4. IT
                M3StackedListItem(
                    title = "IT",
                    supportingText = "Information Technology",
                    leadingIcon = Icons.Filled.Language,
                    index = 4,
                    totalCount = 5,
                    testTag = "branch_item_IT",
                    onClick = {
                        viewModel.navigateTo(
                            AppScreen.BranchYears("IT"),
                            NavigationDirection.FORWARD
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
