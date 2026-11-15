package com.unidownco.core.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniMonoTypography
import com.unidownco.core.design.theme.UniShapes

/**
 * Editorial progress bar with signature yellow fill, black frame, speed & ETA
 */
@Composable
fun UniProgressBar(
    progressPercent: Int,
    speedText: String? = null,
    etaText: String? = null,
    sizeText: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = UniDownTheme.colors
    val progressClamped = (progressPercent.coerceIn(0, 100) / 100f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressClamped,
        label = "UniProgressAnimation"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Track and Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(UniShapes.PillShape)
                .background(colors.surfaceSubtle, UniShapes.PillShape)
                .border(1.dp, colors.primaryBorder, UniShapes.PillShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(UniColors.AccentYellow)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress Metrics Line
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$progressPercent%",
                style = UniMonoTypography.TechnicalMedium,
                color = colors.primaryText
            )

            Spacer(modifier = Modifier.weight(1f))

            if (!speedText.isNullOrBlank()) {
                Text(
                    text = speedText,
                    style = UniMonoTypography.TechnicalSmall,
                    color = colors.secondaryText
                )
            }

            if (!etaText.isNullOrBlank()) {
                Text(
                    text = " • $etaText",
                    style = UniMonoTypography.TechnicalSmall,
                    color = colors.tertiaryText
                )
            }

            if (!sizeText.isNullOrBlank()) {
                Text(
                    text = " • $sizeText",
                    style = UniMonoTypography.TechnicalSmall,
                    color = colors.tertiaryText
                )
            }
        }
    }
}
