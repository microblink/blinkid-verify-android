/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.verifyutils

import androidx.compose.runtime.Composable
import com.microblink.blinkidverify.ux.theme.VerifyTheme
import com.microblink.blinkidverify.ux.R
import com.microblink.blinkidverify.ux.components.ErrorDialog
import com.microblink.blinkidverify.ux.components.HelpScreenPage
import com.microblink.blinkidverify.ux.components.HelpScreens
import com.microblink.blinkidverify.ux.state.ErrorState

@Composable
fun fillHelpScreens(): HelpScreens {
    val helpDialogStrings = VerifyTheme.sdkStrings.verifyHelpDialogsStrings
    return HelpScreens(
        onboardingDialogPage = HelpScreenPage(
            pageImage = R.drawable.mb_blinkidverify_onboarding_id,
            pageTitle = helpDialogStrings.onboardingTitle,
            pageMessage = helpDialogStrings.onboardingMessage
        ),
        helpDialogPages = listOf(
            HelpScreenPage(
                pageImage = R.drawable.mb_blinkidverify_help_id_page_one,
                pageTitle = helpDialogStrings.helpTitles[0],
                pageMessage = helpDialogStrings.helpMessages[0]
            ),
            HelpScreenPage(
                pageImage = R.drawable.mb_blinkidverify_help_id_page_two,
                pageTitle = helpDialogStrings.helpTitles[1],
                pageMessage = helpDialogStrings.helpMessages[1]
            ),
            HelpScreenPage(
                pageImage = R.drawable.mb_blinkidverify_help_id_page_three,
                pageTitle = helpDialogStrings.helpTitles[2],
                pageMessage = helpDialogStrings.helpMessages[2]
            )
        )
    )
}

@Composable
fun fillErrorDialogs(
    onRetry: () -> Unit,
    onDoneError: () -> Unit
): Map<ErrorState, @Composable () -> Unit> {
    return mapOf(
        ErrorState.NoError to {},
        ErrorState.ErrorInvalidLicense to {
            ErrorDialog(
                R.string.mb_blinkidverify_license_locked,
                null,
                R.string.mb_blinkidverify_close,
                onButtonClick = onDoneError
            )
        },
        ErrorState.ErrorNetworkError to {
            ErrorDialog(
                R.string.mb_blinkidverify_license_locked,
                null,
                R.string.mb_blinkidverify_close,
                onButtonClick = onDoneError
            )
        },
        ErrorState.ErrorTimeoutExpired to {
            ErrorDialog(
                R.string.mb_blinkidverify_recognition_timeout_dialog_title,
                R.string.mb_blinkidverify_recognition_timeout_dialog_message,
                R.string.mb_blinkidverify_recognition_timeout_dialog_retry_button,
                onButtonClick = onRetry
            )
        },
        ErrorState.ErrorDocumentClassFiltered to {
            ErrorDialog(
                R.string.mb_blinkidverify_document_class_filtered_dialog_title,
                R.string.mb_blinkidverify_document_class_filtered_dialog_message,
                R.string.mb_blinkidverify_recognition_timeout_dialog_retry_button,
                onButtonClick = onRetry
            )
        },
        ErrorState.ErrorUnsupportedDocument to {
            ErrorDialog(
                R.string.mb_blinkidverify_unsupported_document_title,
                R.string.mb_blinkidverify_unsupported_document_message,
                R.string.mb_blinkidverify_recognition_timeout_dialog_retry_button,
                onButtonClick = onRetry
            )
        }
    )
}
