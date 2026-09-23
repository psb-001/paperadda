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
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AcademicYear
import com.example.data.model.branchDisplayName
import com.example.ui.MainViewModel
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3StackedListItem
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection

/**
 * After picking first year (COMMON) or branch + year: choose what to open —
 * question papers or study notes for exactly this scope and year.
 */
@Composable
fun YearMenuScreen(
    viewModel: MainViewModel,
    branchCode: String = "ENTC",
    academicYear: Int = 1,
    modifier: Modifier = Modifier
) {
    val yearLabel = AcademicYear.labelFor(academicYear)
    val scopeLabel = branchDisplayName(branchCode)
    val subjectCount = viewModel.getSubjectsForBranchAndYear(branchCode, academicYear).size
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    // Refresh counts when the synced catalog lands.
    val catalogRev by viewModel.catalogRevision.collectAsStateWithLifecycle()
    val noteCount = notes.count { n ->
        n.academicYear == academicYear &&
            (
                academicYear == 1 ||
                    n.branchCode.equals(branchCode, ignoreCase = true)
                )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = scopeLabel,
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
                testTag = "year_menu_top_bar"
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

            Text(
                text = yearLabel,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 32.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("year_menu_title")
            )
            Text(
                text = if (branchCode.equals("COMMON", ignoreCase = true)) {
                    "Common to all branches"
                } else {
                    viewModel.getBranch(branchCode)?.fullName ?: branchCode
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                M3StackedListItem(
                    title = "Question Papers",
                    supportingText = "$subjectCount ${if (subjectCount == 1) "subject" else "subjects"} · previous year papers",
                    leadingIcon = Icons.Filled.LibraryBooks,
                    index = 0,
                    totalCount = 2,
                    testTag = "year_menu_papers",
                    onClick = {
                        viewModel.navigateTo(
                            AppScreen.PaperLibrary(
                                branchCode = branchCode,
                                academicYear = academicYear
                            ),
                            NavigationDirection.FORWARD
                        )
                    }
                )
                M3StackedListItem(
                    title = "Study Notes",
                    supportingText = "$noteCount ${if (noteCount == 1) "note" else "notes"} · admin published",
                    leadingIcon = Icons.Filled.NoteAlt,
                    index = 1,
                    totalCount = 2,
                    testTag = "year_menu_notes",
                    onClick = {
                        viewModel.navigateTo(
                            AppScreen.YearNotes(
                                branchCode = branchCode,
                                academicYear = academicYear
                            ),
                            NavigationDirection.FORWARD
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
