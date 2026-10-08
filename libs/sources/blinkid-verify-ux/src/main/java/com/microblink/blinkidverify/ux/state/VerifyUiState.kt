/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.state

import com.microblink.blinkidverify.ux.state.PassportPage
import com.microblink.blinkidverify.core.data.model.result.BlinkIdVerifyCaptureResult
import com.microblink.blinkidverify.ux.consent.ConsentUxState
import com.microblink.blinkidverify.ux.DefaultShowHelpButton
import com.microblink.blinkidverify.ux.DefaultShowOnboardingDialog
import com.microblink.blinkidverify.ux.state.BaseUiState
import com.microblink.blinkidverify.ux.state.CancelRequestState
import com.microblink.blinkidverify.ux.state.CardAnimationState
import com.microblink.blinkidverify.ux.state.CommonStatusMessage
import com.microblink.blinkidverify.ux.state.ErrorState
import com.microblink.blinkidverify.ux.state.HapticFeedbackState
import com.microblink.blinkidverify.ux.state.ScanSoundState
import com.microblink.blinkidverify.ux.state.MbTorchState
import com.microblink.blinkidverify.ux.state.ProcessingState
import com.microblink.blinkidverify.ux.state.ReticleState
import com.microblink.blinkidverify.ux.state.StatusMessage
import com.microblink.blinkidverify.ux.state.UiScanningSide
import com.microblink.blinkidverify.ux.utils.ScreenOrientation

data class VerifyUiState(
    val blinkIdVerifyCaptureResult: BlinkIdVerifyCaptureResult? = null,
    override val reticleState: ReticleState = ReticleState.Hidden,
    override val processingState: ProcessingState = ProcessingState.Sensing,
    override val cardAnimationState: CardAnimationState = CardAnimationState.Hidden,
    override val statusMessage: StatusMessage = CommonStatusMessage.ScanFirstSide,
    override val currentSide: UiScanningSide = UiScanningSide.First,
    override val torchState: MbTorchState = MbTorchState.Off,
    override val cancelRequestState: CancelRequestState = CancelRequestState.CancelNotRequested,
    override val helpButtonDisplayed: Boolean = DefaultShowHelpButton,
    override val helpDisplayed: Boolean = false,
    override val helpTooltipDisplayed: Boolean = false,
    override val onboardingDialogDisplayed: Boolean = DefaultShowOnboardingDialog,
    override val errorState: ErrorState = ErrorState.NoError,
    override val hapticFeedbackState: HapticFeedbackState = HapticFeedbackState.VibrationOff,
    override val scanSoundState: ScanSoundState = ScanSoundState.SoundOff,
    val screenOrientation: ScreenOrientation = ScreenOrientation.Unknown,
    val activePassportPage: PassportPage? = null,
    val consentUxState: ConsentUxState = ConsentUxState.ConsentNotRequired
) : BaseUiState
