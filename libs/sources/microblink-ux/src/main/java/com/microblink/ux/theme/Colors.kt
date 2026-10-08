/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.ux.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.microblink.ux.theme.UiColors.Companion.Default

val Cobalt = Color(0xFF0062F2)
val CobaltLight = Color(0xFF6FA9FF)
val CobaltDark = Color(0xFF142641)
val Gray = Color(0xFF666666)
val DarkGray = Color(0xFF1E1E1E)
val ElevatedDarkGray = Color(0xFF3B3B3B)
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val ErrorRed = Color(0x99FB7185)

val BrandGray50 = Color(0xFFF9FAFB)
val BrandGray200 = Color(0xFFE5E7EB)
val BrandGray400 = Color(0xFF9CA3AF)
val BrandGray500 = Color(0xFF6B7280)
val BrandGray600 = Color(0xFF4B5563)
val BrandGray700 = Color(0xFF374151)
val BrandGray800 = Color(0xFF1F2937)
val BrandGray900 = Color(0xFF111827)

/**
 * Data class contains all the text, button, and background colors used
 * throughout the SDK scanning session. [Default] can be used to keep
 * the original theme colors if only some of the elements are to be changed.
 *
 * This class shouldn't be modified, but rather a new instance should be
 * created and used in [com.microblink.ux.UiSettings.uiColors].
 *
 */
@Immutable
data class UiColors(
    val helpButtonBackground: Color,
    val helpButton: Color,
    val helpTooltipBackground: Color,
    val helpTooltipText: Color
) {
    companion object {
        val Default: UiColors =
            UiColors(
                helpButtonBackground = Color.White,
                helpButton = Cobalt,
                helpTooltipBackground = Cobalt,
                helpTooltipText = Color.White
            )
        val DefaultDark: UiColors =
            UiColors(
                helpButtonBackground = CobaltDark,
                helpButton = CobaltLight,
                helpTooltipBackground = CobaltDark,
                helpTooltipText = Color.White
            )
    }
}

val LightColorScheme = lightColorScheme(
    primary = Cobalt,
    onBackground = Color.Black,
    background = Color.White,
    onSurface = BrandGray900,
    onSurfaceVariant = BrandGray600,
    surfaceContainerLowest = BrandGray50,
    surfaceContainerHigh = BrandGray50,
    surfaceContainerHighest = BrandGray200,
    outline = BrandGray400,
    outlineVariant = BrandGray200
)

val DarkColorScheme = darkColorScheme(
    primary = CobaltLight,
    onBackground = Color.White,
    background = DarkGray,
    onSurface = Color.White,
    onSurfaceVariant = BrandGray200,
    surfaceContainerLowest = BrandGray900,
    surfaceContainerHigh = ElevatedDarkGray,
    surfaceContainerHighest = BrandGray700,
    outline = BrandGray500,
    outlineVariant = BrandGray700
)

var LocalTheme = staticCompositionLocalOf {
    LightColorScheme
}

var LocalBaseUiColors = staticCompositionLocalOf {
    UiColors.Default
}