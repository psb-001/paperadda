package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ConnectedButtonItem(
    val label: String,
    val icon: ImageVector? = null,
    val testTag: String = "",
    val isLoading: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun M3ConnectedButtonGroup(
    items: List<ConnectedButtonItem>,
    modifier: Modifier = Modifier,
    buttonHeight: Int = 56
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .testTag("connected_button_group"),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val totalCount = items.size
        items.forEachIndexed { index, item ->
            val shape = when {
                totalCount <= 1 -> RoundedCornerShape(28.dp)
                index == 0 -> RoundedCornerShape(
                    topStart = 28.dp,
                    bottomStart = 28.dp,
                    topEnd = 8.dp,
                    bottomEnd = 8.dp
                )
                index == totalCount - 1 -> RoundedCornerShape(
                    topStart = 8.dp,
                    bottomStart = 8.dp,
                    topEnd = 28.dp,
                    bottomEnd = 28.dp
                )
                else -> RoundedCornerShape(8.dp)
            }

            Box(
                modifier = Modifier
                    .height(buttonHeight.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primary)
                    .then(if (item.testTag.isNotEmpty()) Modifier.testTag(item.testTag) else Modifier)
                    .bounceClickable(
                        role = Role.Button,
                        onClick = item.onClick
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (item.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (item.icon != null) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
