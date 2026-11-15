package com.unidownco.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniSpacing

/**
 * Editorial Top Bar: clean background, sharp border, brand or title.
 */
@Composable
fun UniTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = UniDownTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .border(
                width = UniSpacing.borderStrokeWidth,
                color = colors.subtleBorder
            )
            .padding(horizontal = UniSpacing.screenHorizontalPadding, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTypography.headlineMedium,
                    color = colors.primaryText
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = AppTypography.bodySmall,
                        color = colors.secondaryText
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

enum class UniNavTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    QUEUE("Queue", Icons.Default.Download),
    LIBRARY("Library", Icons.Default.VideoLibrary),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Editorial Bottom Navigation Bar with structural top border and signature yellow selection.
 */
@Composable
fun UniBottomNavBar(
    selectedTab: UniNavTab,
    onTabSelected: (UniNavTab) -> Unit,
    modifier: Modifier = Modifier,
    queueBadgeCount: Int = 0
) {
    val colors = UniDownTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(
                width = UniSpacing.borderStrokeWidth,
                color = colors.subtleBorder
            )
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UniNavTab.values().forEach { tab ->
                val isSelected = tab == selectedTab
                val itemBg = if (isSelected) UniColors.AccentYellow else Color.Transparent
                val itemFg = if (isSelected) Color.Black else colors.tertiaryText

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(itemBg)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = itemFg,
                            modifier = Modifier.size(20.dp)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = tab.title,
                                style = AppTypography.labelMedium,
                                color = itemFg
                            )
                        } else if (tab == UniNavTab.QUEUE && queueBadgeCount > 0) {
                            Spacer(modifier = Modifier.size(4.dp))
                            UniTechBadge(
                                label = queueBadgeCount.toString(),
                                isHighlighted = true
                            )
                        }
                    }
                }
            }
        }
    }
}
