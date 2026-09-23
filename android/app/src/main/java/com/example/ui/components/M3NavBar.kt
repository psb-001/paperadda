package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

enum class NavDestination(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, "nav_home"),
    SAVED("Saved", Icons.Filled.Bookmark, "nav_saved")
}

@Composable
fun M3NavBar(
    currentDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .height(80.dp)
            .testTag("m3_navigation_bar"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavDestination.values().forEach { destination ->
                val isSelected = destination == currentDestination
                val pillColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                    label = "pillColor"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                    label = "iconColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                    label = "textColor"
                )

                Column(
                    modifier = Modifier
                        .testTag(destination.testTag)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 40.dp)
                        ) {
                            onDestinationSelected(destination)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Pill indicator: 64x32dp
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 32.dp)
                            .clip(CircleShape)
                            .background(pillColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.title,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = destination.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor
                    )
                }
            }
        }
    }
}
