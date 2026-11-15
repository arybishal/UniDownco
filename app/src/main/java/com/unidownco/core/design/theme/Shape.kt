package com.unidownco.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape foundations as defined in DESIGN.md:
 * - Buttons and inputs: ~10dp corners
 * - Cards: ~12dp corners
 * - Dialogs: ~16dp corners
 * - Badges: ~6dp corners
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

object UniShapes {
    val ButtonShape = RoundedCornerShape(10.dp)
    val InputShape = RoundedCornerShape(10.dp)
    val CardShape = RoundedCornerShape(12.dp)
    val DialogShape = RoundedCornerShape(16.dp)
    val BottomSheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val BadgeShape = RoundedCornerShape(6.dp)
    val PillShape = RoundedCornerShape(100.dp)
}
