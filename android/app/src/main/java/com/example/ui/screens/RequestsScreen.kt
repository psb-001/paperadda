package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AcademicYear
import com.example.data.model.BRANCH_COMMON
import com.example.data.model.branchDisplayName
import com.example.data.repository.ContentRequest
import com.example.ui.MainViewModel
import com.example.ui.components.M3EmptyState
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination

@Composable
fun RequestsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val requests by viewModel.contentRequests.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf("paper") }
    var branchCode by rememberSaveable { mutableStateOf("ENTC") }
    var academicYear by rememberSaveable { mutableStateOf(2) }
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var branchExpanded by rememberSaveable { mutableStateOf(false) }
    var yearExpanded by rememberSaveable { mutableStateOf(false) }
    var requestToDelete by remember { mutableStateOf<ContentRequest?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadContentRequests()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = "Requests",
                actionIcon = Icons.Filled.Refresh,
                actionIconContentDescription = "Refresh requests",
                onActionClick = { viewModel.loadContentRequests() },
                testTag = "requests_top_bar"
            )
        },
        bottomBar = {
            M3NavBar(
                currentDestination = NavDestination.REQUESTS,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Request missing content",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tell us which paper or note is missing. No sign-in or personal details are required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "What do you need?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = kind == "paper",
                            onClick = { kind = "paper" },
                            label = { Text("Question Paper") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = kind == "note",
                            onClick = { kind = "note" },
                            label = { Text("Study Note") }
                        )
                    }

                    RequestDropdown(
                        label = "Branch",
                        value = branchDisplayName(branchCode),
                        expanded = branchExpanded,
                        onExpandedChange = { branchExpanded = it },
                        options = listOf(BRANCH_COMMON, "ENTC", "AIML", "CE", "IT"),
                        optionLabel = { branchDisplayName(it) },
                        onSelected = {
                            branchCode = it
                            if (it == BRANCH_COMMON) academicYear = 1
                            else if (academicYear == 1) academicYear = 2
                        }
                    )

                    RequestDropdown(
                        label = "Year",
                        value = AcademicYear.labelFor(academicYear),
                        expanded = yearExpanded,
                        onExpandedChange = { yearExpanded = it },
                        options = listOf(1).map(Int::toString),
                        optionLabel = { AcademicYear.labelFor(it.toInt()) },
                        onSelected = {
                            academicYear = it.toInt()
                            yearExpanded = false
                        }
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 120) title = it },
                        label = { Text(if (kind == "paper") "Subject or paper title" else "Note title") },
                        placeholder = { Text("e.g. Engineering Mathematics II") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_title")
                    )

                    OutlinedTextField(
                        value = details,
                        onValueChange = { if (it.length <= 500) details = it },
                        label = { Text("Additional details (optional)") },
                        placeholder = { Text("Year, branch, or topic") },
                        minLines = 3,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        supportingText = { Text("${details.length}/500") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_details")
                    )

                    Button(
                        onClick = {
                            viewModel.submitContentRequest(
                                kind = kind,
                                branchCode = branchCode,
                                academicYear = academicYear,
                                title = title,
                                details = details
                            ) {
                                title = ""
                                details = ""
                            }
                        },
                        enabled = !viewModel.isSubmittingRequest,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_request")
                    ) {
                        if (viewModel.isSubmittingRequest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sending…")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Request")
                        }
                    }

                    Text(
                        text = "Your request is saved on this device with a random ID, not linked to " +
                            "your name or number. See the Privacy Policy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            viewModel.requestFeedback?.let { message ->
                val container = if (viewModel.requestFeedbackIsError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
                val content = if (viewModel.requestFeedbackIsError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = container),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = content,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearRequestFeedback() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = content)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Your Requests",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = requests.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (viewModel.isLoadingRequests && requests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (requests.isEmpty()) {
                M3EmptyState(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "No requests yet",
                    message = "Requests you send will appear here with their status.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
            } else {
                requests.forEach { request ->
                    RequestStatusCard(request) { viewModel.deleteContentRequest(it) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    requestToDelete?.let { request ->
        AlertDialog(
            onDismissRequest = { requestToDelete = null },
            title = { Text("Delete request?") },
            text = { Text("\"${request.title}\" will be removed from your request history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteContentRequest(request)
                        requestToDelete = null
                    },
                    enabled = viewModel.deletingRequestId == null
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { requestToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RequestDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    optionLabel: (String) -> String,
    onSelected: (String) -> Unit
) {
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { onExpandedChange(true) }) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = "Choose $label"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun RequestStatusCard(
    request: ContentRequest,
    onDelete: (ContentRequest) -> Unit
) {
    val statusColors = when (request.status) {
        "fulfilled" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "rejected" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (request.kind == "paper") "Question Paper" else "Study Note",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = statusColors.first,
                    contentColor = statusColors.second,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = request.status.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${branchDisplayName(request.branchCode)} · ${AcademicYear.labelFor(request.academicYear)} · ${request.createdAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (request.details.isNotBlank()) {
                Text(
                    text = request.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (request.adminNote.isNotBlank()) {
                Text(
                    text = "Update: ${request.adminNote}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusColors.second
                )
            }
            if (request.status == "pending") {
                TextButton(onClick = { onDelete(request) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete request")
                }
            }
        }
    }
}
