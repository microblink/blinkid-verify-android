/**
 * Copyright (c) Microblink. All rights reserved. This code is provided for
 * use as-is and may not be copied, modified, or redistributed.
 */

package com.microblink.blinkidverify.ux.state

import androidx.compose.runtime.Composable
import com.microblink.blinkidverify.ux.R

/**
 * Instruction messages shown only during BlinkID Verify scanning sessions.
 */
enum class VerifyStatusMessage : StatusMessage {
    MoveToPlainBackground;

    @Composable
    override fun statusMessageToStringRes(): Int? = when (this) {
        MoveToPlainBackground -> R.string.mb_blinkidverify_screen_detected
    }
}
