package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.LegalLinks
import com.example.ui.MainViewModel
import com.example.ui.components.M3NavBar
import com.example.ui.components.M3TopBar
import com.example.ui.components.NavDestination

@Composable
fun FeedbackScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var category by rememberSaveable { mutableStateOf("bug") }
    var message by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.clearFeedbackMessage()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            M3TopBar(
                title = "Send feedback",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = "Go back",
                onNavigationClick = { viewModel.navigateBack() },
                testTag = "feedback_top_bar"
            )
        },
        bottomBar = {
            M3NavBar(
                currentDestination = NavDestination.FEEDBACK,
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
                text = "Help us improve PaperAdda",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tell us what is working or what is not. Feedback is anonymous unless you add an email for a reply.",
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
                        text = "What is this about?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedbackCategoryChip("bug", "Bug", category) { category = it }
                        FeedbackCategoryChip("content", "Content", category) { category = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedbackCategoryChip("feature", "Feature", category) { category = it }
                        FeedbackCategoryChip("other", "Other", category) { category = it }
                    }

                    OutlinedTextField(
                        value = message,
                        onValueChange = { if (it.length <= 2000) message = it },
                        label = { Text("Your feedback") },
                        placeholder = { Text("What happened? What did you expect?") },
                        minLines = 6,
                        maxLines = 10,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Default
                        ),
                        supportingText = { Text("${message.length}/2000 · at least 10 characters") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feedback_message")
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { if (it.length <= 254) email = it },
                        label = { Text("Email (optional)") },
                        placeholder = { Text("Only if you want a reply") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        supportingText = { Text("No sign-in is required; leave blank to stay anonymous.") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feedback_email")
                    )

                    Button(
                        onClick = {
                            viewModel.submitFeedback(category, message, email) {
                                message = ""
                                email = ""
                            }
                        },
                        enabled = !viewModel.isSubmittingFeedback && message.trim().length >= 10,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_feedback")
                    ) {
                        if (viewModel.isSubmittingFeedback) {
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
                            Text("Send feedback")
                        }
                    }
                }
            }

            viewModel.feedbackMessage?.let { messageText ->
                val container = if (viewModel.feedbackMessageIsError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
                val content = if (viewModel.feedbackMessageIsError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = container),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Text(
                text = "Your app version is included automatically so we can reproduce issues. " +
                    "By sending feedback you agree that we may store it to reply and improve the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = { uriHandler.openUri(LegalLinks.PRIVACY_POLICY) }) {
                Text("Read the Privacy Policy")
            }
        }
    }
}

@Composable
private fun FeedbackCategoryChip(
    value: String,
    label: String,
    selected: String,
    onSelected: (String) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelected(value) },
        label = { Text(label) }
    )
}
