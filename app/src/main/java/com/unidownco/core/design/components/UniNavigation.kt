package com.unidownco.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UniSpacing.screenHorizontalPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTypography.headlineMedium,
                    color = colors.primaryText
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
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
        // Hairline bottom divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.subtleBorder)
        )
    }
}

enum class UniNavTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    QUEUE("Queue", Icons.Default.Download),
    LIBRARY("Library", Icons.Default.VideoLibrary),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Editorial Bottom Navigation Bar.
 * - Hairline top divider
 * - Fixed-width tab slots (no layout shifts or icon repositioning)
 * - Persistent readable labels
 * - Yellow indicator line on active tab
 * - Android 15/16 system navigation bar padding support
 */
@Composable
fun UniBottomNavBar(
    selectedTab: UniNavTab,
    onTabSelected: (UniNavTab) -> Unit,
    modifier: Modifier = Modifier,
    queueBadgeCount: Int = 0
) {
    val colors = UniDownTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
    ) {
        // Single hairline top divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.subtleBorder)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UniNavTab.values().forEach { tab ->
                val isSelected = tab == selectedTab
                val itemFg = if (isSelected) colors.primaryText else colors.tertiaryText
                val fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = UniColors.AccentYellow)
                        ) { onTabSelected(tab) },
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Active top indicator bar (strictly static layout, zero shifting)
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(if (isSelected) UniColors.AccentYellow else Color.Transparent)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Icon with badge container
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = itemFg,
                                modifier = Modifier.size(22.dp)
                            )

                            // Queue badge
                            if (tab == UniNavTab.QUEUE && queueBadgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 10.dp, y = (-6).dp)
                                        .clip(CircleShape)
                                        .background(UniColors.AccentYellow)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (queueBadgeCount > 99) "99+" else queueBadgeCount.toString(),
                                        style = AppTypography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = tab.title,
                            style = AppTypography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = fontWeight,
                            color = itemFg,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
