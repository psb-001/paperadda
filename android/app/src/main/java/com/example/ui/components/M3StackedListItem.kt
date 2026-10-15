package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun M3StackedListItem(
    title: String,
    supportingText: String,
    leadingIcon: ImageVector,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector = Icons.Filled.ChevronRight,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    testTag: String = ""
) {
    val cornerShape = when {
        totalCount <= 1 -> RoundedCornerShape(28.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
            bottomStart = 8.dp,
            bottomEnd = 8.dp
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 28.dp,
            bottomEnd = 28.dp
        )
        else -> RoundedCornerShape(8.dp)
    }

    val resolvedBackground = if (enabled) {
        backgroundColor
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val supportingColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.outline
    }
    val leadingBackground = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val leadingTint = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(cornerShape)
            .background(resolvedBackground)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .bounceClickable(
                enabled = enabled,
                onClickLabel = if (enabled) "Open $title" else null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading icon in a 40dp primaryContainer circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(leadingBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = leadingTint
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Headline & Supporting Text
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = supportingColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (enabled) {
            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
