package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NoteEntity
import com.example.data.model.AcademicYear
import com.example.data.model.BRANCH_COMMON
import com.example.data.model.branchDisplayName
import com.example.ui.MainViewModel
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3StackedListItem
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin-published study notes for exactly one scope (COMMON first year or a
 * branch) + year. The note body is a PDF on Supabase Storage — tapping a row
 * downloads and opens it. [NoteEntity.content] is only a short list subtitle.
 * Read-only: publishing happens on the admin website / backend.
 */
@Composable
fun YearNotesScreen(
    viewModel: MainViewModel,
    branchCode: String = "ENTC",
    academicYear: Int = 1,
    modifier: Modifier = Modifier
) {
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
    val notes = allNotes.filter { n ->
        n.academicYear == academicYear &&
            (
                // Year 1 content is shared: match COMMON regardless of the
                // branch the navigation route carried.
                academicYear == 1 ||
                    n.branchCode.equals(branchCode, ignoreCase = true)
                )
    }
    val yearLabel = AcademicYear.labelFor(academicYear)
    val scopeLabel = branchDisplayName(branchCode)
    // COMMON scope is already "First Year" — don't print "First Year · First Year".
    val isCommon = branchCode.equals(BRANCH_COMMON, ignoreCase = true)
    val topBarTitle = if (isCommon) yearLabel else "$scopeLabel · $yearLabel"
    val emptyScopeLabel = if (isCommon) yearLabel else "$scopeLabel $yearLabel"
    val openingNoteId = viewModel.openingNoteId

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = topBarTitle,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = "Go back",
                onNavigationClick = {
                    viewModel.navigateBack()
                },
                actionIcon = Icons.Filled.Search,
                actionIconContentDescription = "Search notes and papers",
                onActionClick = { viewModel.isSearchOpen = true },
                testTag = "year_notes_top_bar"
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
                text = "Study notes",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 32.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("year_notes_title")
            )
            Text(
                text = if (notes.size == 1) "1 note" else "${notes.size} notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No notes published for $emptyScopeLabel yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val grouped = notes
                    .groupBy { n -> n.subjectName.ifBlank { "General" } }
                    .toSortedMap(String.CASE_INSENSITIVE_ORDER)

                grouped.forEach { (subject, subjectNotes) ->
                    Text(
                        text = subject,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp)
                            .testTag("notes_subject_${subject.replace(Regex("[^A-Za-z0-9]+"), "_")}")
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        subjectNotes.forEachIndexed { index, note ->
                            val isOpening = openingNoteId == note.id
                            M3StackedListItem(
                                title = note.title,
                                supportingText = buildString {
                                    append(noteSupportingText(note))
                                    if (note.content.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append(note.content)
                                    }
                                    if (isOpening) append(" · Opening…")
                                },
                                leadingIcon = Icons.Filled.EditNote,
                                index = index,
                                totalCount = subjectNotes.size,
                                testTag = "note_item_${note.id}",
                                onClick = { viewModel.openNotePdf(note) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun noteSupportingText(note: NoteEntity): String {
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(note.updatedAt))
    return listOf(note.subjectName, date)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}
