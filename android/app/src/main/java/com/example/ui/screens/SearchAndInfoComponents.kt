package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.branchDisplayName
import com.example.ui.MainViewModel
import com.example.ui.LegalLinks
import com.example.ui.components.M3StackedListItem
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Compact bottom sheet instead of a fullscreen takeover: the current
    // screen stays visible behind it, and system back dismisses the sheet
    // before touching app navigation.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("search_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search papers or subjects…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("search_text_input")
            )

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results list (capped height so the sheet stays compact).
            if (viewModel.searchQuery.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Search across ENTC, AIML, CE and IT",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (viewModel.searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No papers found for '${viewModel.searchQuery}'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    viewModel.searchResults.forEachIndexed { index, paper ->
                        M3StackedListItem(
                            title = paper.title,
                            supportingText = "${branchDisplayName(paper.branchCode)} · ${paper.subjectName} · ${paper.year}",
                            leadingIcon = Icons.Filled.Description,
                            index = index,
                            totalCount = viewModel.searchResults.size,
                            testTag = "search_result_${paper.id}",
                            onClick = {
                                onDismiss()
                                viewModel.navigateTo(
                                    AppScreen.PaperDetails(paper.id),
                                    NavigationDirection.FORWARD
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.paperadda_splash_icon),
                    contentDescription = "PaperAdda logo",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "About PaperAdda",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Engineering Examination Papers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PaperAdda allows students in ENTC, AIML, CE, and IT branches to browse and download genuine previous year question papers in PDF format, read admin-published study notes, and request missing content.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "• ENTC: Electronics and Telecommunication\n• AIML: Artificial Intelligence & Machine Learning\n• CE: Computer Engineering\n• IT: Information Technology",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Built by",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Lead — prominent card so the founder stands out.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Prathamesh Bhujbal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Founder & Lead Developer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { uriHandler.openUri("https://github.com/psb-001") }) {
                            Text("GitHub")
                        }
                        TextButton(onClick = {
                            uriHandler.openUri("https://www.linkedin.com/in/prathamesh-bhujbal-psb?utm_source=share_via&utm_content=profile&utm_medium=member_android")
                        }) {
                            Text("LinkedIn")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TeamMemberRow(
                name = "Atharv Chougule",
                role = "Contributor",
                githubUrl = "https://github.com/Atharv-052007",
                linkedinUrl = "https://www.linkedin.com/in/atharv-chougule-626452393?utm_source=share_via&utm_content=profile&utm_medium=member_android"
            )
            TeamMemberRow(
                name = "Aayush Kandhare",
                role = "Contributor",
                githubUrl = "https://github.com/Imaayushh",
                linkedinUrl = "https://www.linkedin.com/in/aayush-kandhare-32646538b?utm_source=share_via&utm_content=profile&utm_medium=member_android"
            )
            TeamMemberRow(
                name = "Ayush Nimbhare",
                role = "Contributor",
                githubUrl = "https://github.com/AyushNimbhare",
                linkedinUrl = "https://www.linkedin.com/in/ayush-nimbhare-5770532bb?utm_source=share_via&utm_content=profile&utm_medium=member_android"
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Legal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = LegalLinks.COPYRIGHT_NOTICE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { uriHandler.openUri(LegalLinks.PRIVACY_POLICY) }) {
                    Text("Privacy Policy")
                }
                TextButton(onClick = { uriHandler.openUri(LegalLinks.TERMS_AND_COPYRIGHT) }) {
                    Text("Terms & Copyright")
                }
                TextButton(onClick = { uriHandler.openUri(LegalLinks.DELETE_DATA) }) {
                    Text("Delete my data")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Spacer(modifier = Modifier.height(8.dp))

            // PaperAdda is shared as an APK, so nothing tells a user's phone a
            // newer build exists. This is the manual escape hatch for that.
            Text(
                text = "Version ${viewModel.installedVersionName} " +
                    "(build ${viewModel.installedVersionCode})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = { viewModel.checkForUpdateManually() },
                enabled = !viewModel.isCheckingForUpdate
            ) {
                if (viewModel.isCheckingForUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for updates")
                }
            }

            viewModel.updateMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            viewModel.availableUpdate?.let { release ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Version ${release.version} is available",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.openUpdateDialog() }) {
                        Text("Update")
                    }
                }
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Got It")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Compact contributor row: name + role with GitHub/LinkedIn connect links.
 * Opens in the system browser via [LocalUriHandler] (no extra permissions).
 */
@Composable
private fun TeamMemberRow(
    name: String,
    role: String,
    githubUrl: String,
    linkedinUrl: String,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { uriHandler.openUri(githubUrl) }) {
            Text("GitHub")
        }
        TextButton(onClick = { uriHandler.openUri(linkedinUrl) }) {
            Text("LinkedIn")
        }
    }
}
