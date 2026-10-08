/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.capture.settings

import android.os.Parcelable
import com.microblink.blinkidverify.ux.components.needHelpTooltipDefaultDurationMs
import com.microblink.blinkidverify.ux.components.needHelpTooltipDefaultTimeToAppearMs
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val DEFAULT_STEP_TIMEOUT_DURATION_MS = 60000
private const val DEFAULT_INACTIVITY_TIMEOUT_DURATION_MS = 10000

/**
 * Configuration settings for the scanning UX.
 *
 * @param stepTimeoutDuration Duration of the scanning session step before a timeout is triggered.
 * Resets on side changes, pauses when onboarding and help screen dialogs appear. If set to [Duration.ZERO], the scanning will not time out.
 * @param inactivityTimeoutDuration Duration of the current UI state in a scanning session before a timeout is triggered.
 * Resets every time the UI state changes (reticle type or message). If set to [Duration.ZERO], the scanning will not time out.
 * @param allowHapticFeedback Whether haptic feedback is allowed during the scanning process. Defaults to true.
 * @param allowScanSound Whether scan success sounds are allowed during the scanning process. Defaults to true.
 * @param helpTooltipShowDelay Duration before the help tooltip is shown.
 * If less than or equal to [Duration.ZERO], the help tooltip won't be shown automatically.
 * @param helpTooltipHideDelay Duration before the help tooltip is hidden.
 * If less than or equal to [Duration.ZERO], the help tooltip won't be hidden automatically. Defaults to 5 seconds.
 */
@Parcelize
data class VerifyUxSettings(
    val stepTimeoutDuration: Duration = DEFAULT_STEP_TIMEOUT_DURATION_MS.milliseconds,
    val inactivityTimeoutDuration: Duration = DEFAULT_INACTIVITY_TIMEOUT_DURATION_MS.milliseconds,
    val allowHapticFeedback: Boolean = true,
    val allowScanSound: Boolean = true,
    val helpTooltipShowDelay: Duration = needHelpTooltipDefaultTimeToAppearMs.milliseconds,
    val helpTooltipHideDelay: Duration = needHelpTooltipDefaultDurationMs.milliseconds,
) : Parcelable {
    /**
     * Constructor retaining the original Java API.
     */
    @JvmOverloads
    constructor(
        stepTimeoutDurationMs: Int,
        allowHapticFeedback: Boolean = true,
        allowScanSound: Boolean = true,
    ) : this(
        stepTimeoutDuration = stepTimeoutDurationMs.milliseconds,
        inactivityTimeoutDuration = DEFAULT_INACTIVITY_TIMEOUT_DURATION_MS.milliseconds,
        allowHapticFeedback = allowHapticFeedback,
        allowScanSound = allowScanSound,
    )

    /**
     * Constructor for easier Java implementation.
     *
     * This secondary constructor allows Java developers to create a [VerifyUxSettings]
     * instance by providing the timeout durations as an `Int` in milliseconds.
     *
     * @param stepTimeoutDurationMs Duration of the scanning session step before a timeout is triggered
     * in milliseconds. Resets on side changes, pauses when onboarding and help screen dialogs appear. If set to 0, the scanning will not time out.
     * @param inactivityTimeoutDurationMs Duration of the current UI state in a scanning session before a timeout is triggered
     * in milliseconds. Resets every time the UI state changes (reticle type or message). If set to 0, the scanning will not time out.
     * @param allowHapticFeedback Whether haptic feedback is allowed during the scanning process. Defaults to true.
     * @param allowScanSound Whether scan success sounds are allowed during the scanning process. Defaults to true.
     */
    @JvmOverloads
    constructor(
        stepTimeoutDurationMs: Int,
        inactivityTimeoutDurationMs: Int,
        allowHapticFeedback: Boolean = true,
        allowScanSound: Boolean = true,
    ) : this(
        stepTimeoutDuration = stepTimeoutDurationMs.milliseconds,
        inactivityTimeoutDuration = inactivityTimeoutDurationMs.milliseconds,
        allowHapticFeedback = allowHapticFeedback,
        allowScanSound = allowScanSound,
    )

    /**
     * Constructor for easier Java implementation with configurable help tooltip delays.
     */
    constructor(
        stepTimeoutDurationMs: Int,
        allowHapticFeedback: Boolean,
        helpTooltipShowDelayMs: Int,
        helpTooltipHideDelayMs: Int,
    ) : this(
        stepTimeoutDuration = stepTimeoutDurationMs.milliseconds,
        allowHapticFeedback = allowHapticFeedback,
        helpTooltipShowDelay = helpTooltipShowDelayMs.milliseconds,
        helpTooltipHideDelay = helpTooltipHideDelayMs.milliseconds,
    )
}
