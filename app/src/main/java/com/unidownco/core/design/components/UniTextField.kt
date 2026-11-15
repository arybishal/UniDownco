package com.unidownco.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniShapes
import com.unidownco.core.design.theme.UniSpacing

/**
 * Editorial URL Input with 1.5dp structural border, 10dp radius, and inline Paste / Clear actions.
 */
@Composable
fun UniUrlInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onClearClick: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Paste video URL (YouTube, TikTok, Instagram, X, Reddit)..."
) {
    val colors = UniDownTheme.colors
    val hasText = value.isNotBlank()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, UniShapes.InputShape)
            .border(
                width = UniSpacing.borderStrokeThick,
                color = colors.primaryBorder,
                shape = UniShapes.InputShape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = AppTypography.bodyMedium,
                        color = colors.tertiaryText,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = AppTypography.bodyMedium.copy(color = colors.primaryText),
                    cursorBrush = SolidColor(if (colors.isDark) UniColors.AccentYellow else colors.primaryText),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(UniSpacing.xs))

            if (hasText) {
                UniCompactButton(
                    text = "CLEAR",
                    onClick = onClearClick,
                    isPrimary = false
                )
            } else {
                UniCompactButton(
                    text = "PASTE",
                    onClick = onPasteClick,
                    isPrimary = true
                )
            }
        }
    }
}
