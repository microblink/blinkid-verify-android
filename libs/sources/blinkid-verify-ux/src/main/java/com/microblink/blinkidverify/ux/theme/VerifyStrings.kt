/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.theme

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.microblink.blinkidverify.ux.R
import com.microblink.blinkidverify.ux.theme.AccessibilityStrings
import com.microblink.blinkidverify.ux.theme.HelpDialogsStrings
import com.microblink.blinkidverify.ux.theme.ScanningStrings
import com.microblink.blinkidverify.ux.theme.SdkStrings
import kotlinx.parcelize.Parcelize

/**
 * Data class contains all the strings used throughout the SDK.
 * [Default] can be used to keep the original strings if only some of the elements are to be changed.
 *
 * This class shouldn't be modified, but rather a new instance should be
 * created and used in [com.microblink.ux.UiSettings.sdkStrings].
 *
 * @property verifyScanningStrings Strings that appear as instruction messages during the scanning session.
 *           These instructions are triggered by specific UX events and will appear on screen accordingly.
 *           Includes both BlinkID specific and common SDK strings.
 * @property verifyHelpDialogsStrings Strings used in onboarding and help dialogs. These strings shouldn't
 *           be customized as they provide adequate instructions tailored specifically to our scanning experience.
 *           However, if the scanning experience is changed in any way, onboarding and help screen instructions
 *           may also be adjusted.
 * @property verifyAccessibilityStrings Strings that are used by accessibility TalkBack service for specific
 *           buttons, labels, and actions.
 */
@Immutable
@Parcelize
data class VerifySdkStrings(
    val verifyScanningStrings: ScanningStrings,
    val verifyHelpDialogsStrings: HelpDialogsStrings,
    val verifyAccessibilityStrings: AccessibilityStrings
) : Parcelable, SdkStrings(
    verifyScanningStrings,
    verifyAccessibilityStrings
) {
    companion object {
        /**
         * Default onboarding and help dialog strings.
         *
         * Can be used as a base when customizing [verifyHelpDialogsStrings].
         */
        @JvmStatic
        val HelpDialogsDefaults = HelpDialogsStrings(
            onboardingTitle = R.string.mb_blinkidverify_onboarding_dialog_title,
            onboardingMessage = R.string.mb_blinkidverify_onboarding_dialog_message,
            helpTitles = listOf(
                R.string.mb_blinkidverify_help_screen_title1,
                R.string.mb_blinkidverify_help_screen_title2,
                R.string.mb_blinkidverify_help_screen_title3
            ),
            helpMessages = listOf(
                R.string.mb_blinkidverify_help_screen_msg1,
                R.string.mb_blinkidverify_help_screen_msg2,
                R.string.mb_blinkidverify_help_screen_msg3
            )
        )

        @JvmStatic
        val Default: VerifySdkStrings =
            VerifySdkStrings(
                verifyScanningStrings = ScanningStrings.VerifyDefault,
                verifyHelpDialogsStrings = HelpDialogsDefaults,
                verifyAccessibilityStrings = AccessibilityStrings.Default
            )
    }
}

val ScanningStrings.Companion.VerifyDefault: ScanningStrings
    get() = ScanningStrings(
        instructionsFirstSide = R.string.mb_blinkidverify_front_instructions,
        instructionsSecondSide = R.string.mb_blinkidverify_back_instructions,
        instructionsFlip = R.string.mb_blinkidverify_camera_flip_document,
        instructionsNotFullyVisible = R.string.mb_blinkidverify_document_not_fully_visible,
        instructionsTilted = R.string.mb_blinkidverify_keep_document_parallel,
        instructionsScanningWrongSide = R.string.mb_blinkidverify_scanning_wrong_side,
        instructionsBlurDetected = R.string.mb_blinkidverify_blur_detected,
        instructionsMoveFarther = R.string.mb_blinkidverify_move_farther,
        instructionsMoveCloser = R.string.mb_blinkidverify_move_closer,
        snackbarFlashlightWarning = R.string.mb_blinkidverify_flashlight_warning_message
    )

var LocalBaseVerifySdkStrings = staticCompositionLocalOf {
    VerifySdkStrings.Default
}
