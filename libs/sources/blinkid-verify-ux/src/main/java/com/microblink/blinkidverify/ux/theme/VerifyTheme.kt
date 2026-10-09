/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.microblink.blinkidverify.ux.theme.VerifyTheme.sdkStrings
import com.microblink.blinkidverify.ux.theme.VerifyTheme.sdkTheme
import com.microblink.blinkidverify.ux.UiSettings
import com.microblink.blinkidverify.ux.theme.DarkColorScheme
import com.microblink.blinkidverify.ux.theme.LightColorScheme
import com.microblink.blinkidverify.ux.theme.LocalBaseSdkStrings
import com.microblink.blinkidverify.ux.theme.LocalBaseUiColors
import com.microblink.blinkidverify.ux.theme.LocalTheme
import com.microblink.blinkidverify.ux.theme.LocalTypography
import com.microblink.blinkidverify.ux.theme.SdkTypography
import com.microblink.blinkidverify.ux.theme.UiColors
import com.microblink.blinkidverify.ux.theme.UiTypography

/**
 * Theme used in the entire SDK.
 * Defines colors and strings that are used for all the elements.
 * The theme can be updated by defining [UiSettings] which customizes
 * all the visual elements.
 *
 * @property verifyUiSettings Class that defines all of the visual elements of the SDK.
 * @property darkTheme Defines whether dark theme should be used or not. By default, the
 *                     current OS theme setting will be used.
 *
 */
@Composable
fun BlinkIdVerifySdkTheme(
    verifyUiSettings: UiSettings,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val resolvedSdkStrings = verifyUiSettings.sdkStrings as? VerifySdkStrings ?: sdkStrings
    CompositionLocalProvider(
        LocalBaseUiColors provides (verifyUiSettings.uiColors
            ?: if (darkTheme) UiColors.DefaultDark else UiColors.Default),
        LocalBaseVerifySdkStrings provides resolvedSdkStrings,
        LocalBaseSdkStrings provides resolvedSdkStrings,
        LocalTheme provides if (darkTheme) DarkColorScheme else LightColorScheme,
        LocalTypography provides (verifyUiSettings.typography ?: SdkTypography(null))

    ) {
        MaterialTheme(
            colorScheme = verifyUiSettings.colorScheme ?: sdkTheme,
            content = content
        )
    }
}

internal object VerifyTheme {
    val uiColors: UiColors
        @Composable
        get() = LocalBaseUiColors.current

    val sdkStrings: VerifySdkStrings
        @Composable
        get() = LocalBaseVerifySdkStrings.current

    val sdkTheme: ColorScheme
        @Composable
        get() = LocalTheme.current

    val sdkTypography: UiTypography
        @Composable
        get() = LocalTypography.current
}