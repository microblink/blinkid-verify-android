/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux

import android.content.Context
import android.os.CountDownTimer
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.microblink.blinkidverify.ux.scanning.RequestPassportPage
import com.microblink.blinkidverify.ux.scanning.ScanningWrongPassportPage
import com.microblink.blinkidverify.ux.state.BlinkIdStatusMessage
import com.microblink.blinkidverify.ux.state.PassportPage
import com.microblink.blinkidverify.ux.state.ShowPassportMoveToBarcode
import com.microblink.blinkidverify.ux.state.ShowPassportMoveToLeft
import com.microblink.blinkidverify.ux.state.ShowPassportMoveToRight
import com.microblink.blinkidverify.ux.state.ShowPassportMoveToTop
import com.microblink.blinkidverify.ux.utils.UxPingletTracker
import com.microblink.blinkidverify.ux.utils.getCorrectedDocumentRotation
import com.microblink.blinkidverify.ux.utils.getPassportPageFromRotation
import com.microblink.blinkidverify.core.BlinkIdVerifySdk
import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifySessionSettings
import com.microblink.blinkidverify.core.data.model.result.BlinkIdVerifyCaptureResult
import com.microblink.blinkidverify.ux.consent.CmsFlowLog
import com.microblink.blinkidverify.ux.consent.BlinkIdVerifyConsentUxConfig
import com.microblink.blinkidverify.ux.consent.ConsentManager
import com.microblink.blinkidverify.ux.consent.ConsentUxState
import com.microblink.blinkidverify.core.utils.ping.sendPingletsIfAllowed
import com.microblink.blinkidverify.ux.capture.scanning.BlinkIdVerifyDocumentLocatedLocation
import com.microblink.blinkidverify.ux.capture.scanning.BlinkIdVerifyAnalyzer
import com.microblink.blinkidverify.ux.capture.scanning.VerifyDocumentImageAnalysisResult
import com.microblink.blinkidverify.ux.capture.scanning.VerifyScanningDoneHandler
import com.microblink.blinkidverify.ux.capture.settings.VerifyUxSettings
import com.microblink.blinkidverify.ux.state.VerifyUiState
import com.microblink.blinkidverify.ux.ScanningUxEvent
import com.microblink.blinkidverify.ux.ScanningUxEventHandler
import com.microblink.blinkidverify.ux.R
import com.microblink.blinkidverify.ux.UiSettings
import com.microblink.blinkidverify.ux.camera.CameraHardwareInfoHelper
import com.microblink.blinkidverify.ux.camera.CameraInputDetails
import com.microblink.blinkidverify.ux.camera.CameraViewModel
import com.microblink.blinkidverify.ux.camera.TimeoutCause
import com.microblink.blinkidverify.ux.components.uiCountingWindowDurationMs
import com.microblink.blinkidverify.ux.state.CardAnimationState
import com.microblink.blinkidverify.ux.state.CardAnimationState.ShowFlipLandscape
import com.microblink.blinkidverify.ux.capture.scanning.ScreenPresenceDetected
import com.microblink.blinkidverify.ux.state.CommonStatusMessage
import com.microblink.blinkidverify.ux.state.VerifyStatusMessage
import com.microblink.blinkidverify.ux.state.ErrorState
import com.microblink.blinkidverify.ux.state.HapticFeedbackState
import com.microblink.blinkidverify.ux.state.ScanSoundState
import com.microblink.blinkidverify.ux.state.MbTorchState
import com.microblink.blinkidverify.ux.state.ProcessingState
import com.microblink.blinkidverify.ux.state.ReticleState
import com.microblink.blinkidverify.ux.state.StatusMessage
import com.microblink.blinkidverify.ux.state.StatusMessageCounter
import com.microblink.blinkidverify.ux.utils.ErrorReason
import com.microblink.blinkidverify.core.ping.config.PingSendTriggerPoint
import com.microblink.blinkidverify.core.ping.pinglets.UxEvent
import com.microblink.blinkidverify.ux.state.UiScanningSide
import com.microblink.blinkidverify.core.utils.MbLog
import com.microblink.blinkidverify.ux.utils.ScreenOrientation
import com.microblink.blinkidverify.ux.utils.toErrorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val pingletOrientationDelayMs = 1000L

internal class BlinkIdVerifyUxViewModel(
    blinkIdVerifySdkInstance: BlinkIdVerifySdk,
    sessionSettings: BlinkIdVerifySessionSettings,
    uxSettings: VerifyUxSettings,
    consentUxConfig: BlinkIdVerifyConsentUxConfig,
    applicationContext: Context
) : CameraViewModel() {
    private var imageAnalyzer: BlinkIdVerifyAnalyzer? = null

    private val consentManager = ConsentManager(consentUxConfig)

    /**
     * Onboarding is deferred until consent is resolved, so the end user is not presented with two
     * dialogs at the same time.
     */
    private var showOnboardingDialogAfterConsent = false

    private var firstImageTimestamp: Long? = null
    /**
     * Monotonic timestamp from which inactivity is measured. It is cleared whenever the scanning
     * makes progress, so the inactivity timeout starts again on the next UX event.
     *
     * Progress means either a change of the processing state or status message, or a UX event
     * reporting that the document is being located and processed. The latter matters because this UI
     * implementation does not surface a distinct state while the document is located, so relying on
     * UI state changes alone would time out a scan that is actually advancing.
     */
    private var inactivityTimeoutStartTimestamp: Long? = null

    private var stepTimeoutDurationBeforePause: Long? = null

    private val stepTimeoutDuration: Duration? =
        if (uxSettings.stepTimeoutDuration == Duration.ZERO) null else uxSettings.stepTimeoutDuration
    private val inactivityTimeoutDuration: Duration? =
        if (uxSettings.inactivityTimeoutDuration == Duration.ZERO) null else uxSettings.inactivityTimeoutDuration

    private var isStateTimeoutActive: Boolean = true
    private var isAnalysisPaused: Boolean = false

    private val _uiState =
        MutableStateFlow(VerifyUiState(consentUxState = consentManager.initialConsentUxState))
    val uiState: StateFlow<VerifyUiState> = _uiState.asStateFlow()

    var uiStateStartTime: Duration = Duration.ZERO
    val countingWindowDuration: Duration = uiCountingWindowDurationMs.milliseconds
    private var lastScreenOrientationPingTime: Long = 0L

    private val statusCounter: StatusMessageCounter = StatusMessageCounter()
    private val appearanceCounter: StatusMessageCounter = StatusMessageCounter()

    private var currentScreenOrientation: ScreenOrientation? = null

    private var lastTrackedErrorType: UxEvent.ErrorMessageType? = null

    private var cameraHardwareInfoReported = false

    val helpTooltipTimeToDisplayInMs = uxSettings.helpTooltipShowDelay.inWholeMilliseconds

    // the help tooltip is never shown automatically if the show delay is not positive
    private val helpTooltipTimer = if (!uxSettings.helpTooltipShowDelay.isPositive()) null else
        object : CountDownTimer(helpTooltipTimeToDisplayInMs, helpTooltipTimeToDisplayInMs) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                UxPingletTracker.UxEvent.trackSimpleEvent(
                    UxPingletTracker.UxEvent.SimpleUxEventType.HelpTooltipDisplayed,
                    getSessionNumber()
                )
                changeHelpTooltipVisibility(true)
            }

        }

    init {
        MbLog.i(CmsFlowLog.TAG) {
            "[CMS] Starting scanning session UX with consentUxState=${consentManager.initialConsentUxState}"
        }
        imageAnalyzer = BlinkIdVerifyAnalyzer(
            verifySdk = blinkIdVerifySdkInstance,
            sessionSettings = consentManager.applyTo(sessionSettings),
            verifyScanningDoneHandler = object : VerifyScanningDoneHandler {
                override fun onScanningFinished(result: BlinkIdVerifyCaptureResult) {
                    MbLog.d(TAG) { "Scanning finished successfully" }
                    _uiState.update {
                        it.copy(blinkIdVerifyCaptureResult = result)
                    }
                }

                override fun onError(error: ErrorReason) {
                    MbLog.d(TAG) { "Scanning finished with an error: $error" }
                    showErrorDialog(
                        alertType = when (error) {
                            ErrorReason.ErrorInvalidLicense -> UxEvent.AlertType.INVALIDLICENSEKEY
                            ErrorReason.ErrorStepTimeoutExpired -> UxEvent.AlertType.STEPTIMEOUT
                            ErrorReason.ErrorInactivityTimeoutExpired -> UxEvent.AlertType.INACTIVITYTIMEOUT
                            ErrorReason.ErrorNetworkError -> UxEvent.AlertType.NETWORKERROR
                            ErrorReason.ErrorDocumentClassFiltered -> UxEvent.AlertType.DOCUMENTCLASSNOTALLOWED
                            // TODO: add new AlertTypes
                            ErrorReason.ErrorSettingsValidationFailed -> UxEvent.AlertType.NETWORKERROR
                            ErrorReason.ErrorGetResultFailed -> UxEvent.AlertType.NETWORKERROR
                        },
                        errorState = error.toErrorState()
                    )
                }

                override fun onScanningCanceled() {}
            },
            uxEventHandler = object : ScanningUxEventHandler {
                override fun onUxEvents(events: List<ScanningUxEvent>) {
                    var newStatusMessage: StatusMessage? = null
                    var newProcessingState: ProcessingState? = null
                    var newActivePassportPage: PassportPage? = null
                    var newCurrentSide: UiScanningSide? = null
                    var scanningProgressed = false
                    stepTimeoutDuration?.let {
                        if (firstImageTimestamp == null) {
                            firstImageTimestamp = System.nanoTime()
                        }
                        firstImageTimestamp?.let { timestamp ->
                            val currentDuration =
                                (System.nanoTime() - timestamp).toDuration(DurationUnit.NANOSECONDS)
                            if (currentDuration > stepTimeoutDuration) {
                                imageAnalyzer?.timeoutAnalysis(TimeoutCause.Step)
                                firstImageTimestamp = null
                                inactivityTimeoutStartTimestamp = null
                            }
                        }
                    }
                    inactivityTimeoutDuration?.let {
                        if (isStateTimeoutActive) {
                            if (inactivityTimeoutStartTimestamp == null) {
                                inactivityTimeoutStartTimestamp = System.nanoTime()
                            }
                            inactivityTimeoutStartTimestamp?.let { timestamp ->
                                val currentDuration =
                                    (System.nanoTime() - timestamp).toDuration(DurationUnit.NANOSECONDS)
                                if (currentDuration > inactivityTimeoutDuration) {
                                    imageAnalyzer?.timeoutAnalysis(TimeoutCause.Inactivity)
                                    firstImageTimestamp = null
                                    inactivityTimeoutStartTimestamp = null
                                }
                            }
                        }
                    }
                    for (event in events) {
                        MbLog.d(TAG) { "Received UX event: $event" }
                        when (event) {
                            is ScanningUxEvent.ScanningDone -> {
                                firstImageTimestamp = null
                                lifecyclePauseAnalysis()
                                newStatusMessage = CommonStatusMessage.Empty
                                newProcessingState = ProcessingState.SuccessAnimation(false)
                            }

                            is ScanningUxEvent.DocumentNotFound -> {
                                newProcessingState = ProcessingState.Sensing

                                newStatusMessage =
                                    if (uiState.value.activePassportPage != null) {
                                        when (uiState.value.activePassportPage) {
                                            PassportPage.Top -> BlinkIdStatusMessage.PassportScanTopPage
                                            PassportPage.Right -> BlinkIdStatusMessage.PassportScanRightPage
                                            PassportPage.Left -> BlinkIdStatusMessage.PassportScanLeftPage
                                            PassportPage.Barcode -> BlinkIdStatusMessage.PassportScanBarcodePage
                                            else -> BlinkIdStatusMessage.PassportScanTopPage
                                        }
                                    } else {
                                        when (uiState.value.currentSide) {
                                            UiScanningSide.First -> CommonStatusMessage.ScanFirstSide
                                            UiScanningSide.Second -> CommonStatusMessage.ScanSecondSide
                                            UiScanningSide.Barcode -> BlinkIdStatusMessage.ScanBarcode
                                        }
                                    }
                            }

                            is ScanningUxEvent.DocumentLocated, is BlinkIdVerifyDocumentLocatedLocation -> {
                                // newProcessingState = ProcessingState.Processing
                                // Not used in this UI implementation.
                                // Can be used for processing state.
                                // The document is located and being processed, so the scanning is
                                // advancing even though the UI state stays the same.
                                scanningProgressed = true
                            }


                            is ScanningUxEvent.BlurDetected -> {
                                newProcessingState = ProcessingState.Error
                                newStatusMessage = CommonStatusMessage.EliminateBlur
                            }

                            is ScanningUxEvent.DocumentNotFullyVisible -> {
                                newProcessingState = ProcessingState.Error
                                newStatusMessage = CommonStatusMessage.KeepVisible
                            }

                            is ScanningUxEvent.DocumentTooClose -> {
                                newProcessingState = ProcessingState.Error
                                newStatusMessage = CommonStatusMessage.MoveFarther
                            }

                            is ScanningUxEvent.DocumentTooFar -> {
                                newProcessingState = ProcessingState.Error
                                newStatusMessage = CommonStatusMessage.MoveCloser
                            }

                            is ScanningUxEvent.DocumentTooTilted -> {
                                newProcessingState = ProcessingState.Error
                                newStatusMessage = CommonStatusMessage.Align
                            }

                            is ScanningUxEvent.GlareDetected -> {
                                newProcessingState = ProcessingState.Error
                                newStatusMessage = BlinkIdStatusMessage.EliminateGlare
                            }

                            is ScreenPresenceDetected -> {
                                newProcessingState = ProcessingState.Error
                                newStatusMessage = VerifyStatusMessage.MoveToPlainBackground
                            }

                            is ScanningUxEvent.ScanningWrongSide -> {
                                newProcessingState = ProcessingState.Error
                                newStatusMessage = CommonStatusMessage.ScanningWrongSide
                            }

                            is ScanningWrongPassportPage -> {
                                val page =
                                    if (event.activePassportPage == PassportPage.Data) {
                                        PassportPage.Data
                                    } else if (event.activePassportPage == PassportPage.Barcode) {
                                        PassportPage.Barcode
                                    } else {
                                        getPassportPageFromRotation(
                                            getCorrectedDocumentRotation(
                                                event.documentRotation,
                                                uiState.value.screenOrientation
                                            )
                                        )
                                    }
                                newStatusMessage =
                                    when (page) {
                                        PassportPage.Top -> BlinkIdStatusMessage.PassportWrongPageTop
                                        PassportPage.Right -> BlinkIdStatusMessage.PassportWrongPageRight
                                        PassportPage.Left -> BlinkIdStatusMessage.PassportWrongPageLeft
                                        PassportPage.Data -> BlinkIdStatusMessage.ScanPassportDataPage
                                        PassportPage.Barcode -> BlinkIdStatusMessage.PassportWrongPageBarcode
                                    }
                                newProcessingState = ProcessingState.Error
                                newActivePassportPage = page
                            }

                            is RequestPassportPage -> {
                                lifecyclePauseAnalysis()
                                newActivePassportPage = if (event.isBarcodePageRequested) {
                                    PassportPage.Barcode
                                } else {
                                    getPassportPageFromRotation(
                                        getCorrectedDocumentRotation(
                                            event.documentRotation,
                                            uiState.value.screenOrientation
                                        )
                                    )
                                }
                                newProcessingState = ProcessingState.SuccessAnimation(true)
                                newStatusMessage = CommonStatusMessage.Empty
                            }

                            is ScanningUxEvent.RequestSide -> {
                                var currentSide: UiScanningSide = uiState.value.currentSide
                                when (uiState.value.currentSide) {
                                    UiScanningSide.First -> {
                                        when (event.side) {
                                            UiScanningSide.First -> {
                                            }

                                            UiScanningSide.Second -> {
                                                // TODO: support for portrait animations
                                                newProcessingState =
                                                    ProcessingState.SuccessAnimation(true)
                                                newStatusMessage = CommonStatusMessage.Empty
                                                firstImageTimestamp = null
                                                lifecyclePauseAnalysis()
                                            }

                                            UiScanningSide.Barcode -> {
                                                newProcessingState = ProcessingState.Sensing
                                                newStatusMessage =
                                                    BlinkIdStatusMessage.ScanBarcode
                                                newCurrentSide = UiScanningSide.Barcode
                                                firstImageTimestamp = null
                                                isStateTimeoutActive = false
                                                imageAnalyzer?.pauseAnalysis()
                                                imageAnalyzer?.resumeAnalysis()
                                            }
                                        }
                                    }

                                    UiScanningSide.Second -> {
                                        when (event.side) {
                                            UiScanningSide.First -> {}

                                            UiScanningSide.Second -> {}

                                            UiScanningSide.Barcode -> {
                                                newProcessingState = ProcessingState.Sensing
                                                newStatusMessage =
                                                    BlinkIdStatusMessage.ScanBarcode
                                                newCurrentSide = UiScanningSide.Barcode
                                                firstImageTimestamp = null
                                                isStateTimeoutActive = false
                                                imageAnalyzer?.pauseAnalysis()
                                                imageAnalyzer?.resumeAnalysis()
                                            }
                                        }
                                    }

                                    UiScanningSide.Barcode -> {
                                        // Impossible to reach anything else other than barcode.
                                        currentSide = UiScanningSide.Barcode
                                        newStatusMessage = BlinkIdStatusMessage.ScanBarcode
                                    }
                                }

                            }

                            is VerifyDocumentImageAnalysisResult -> {
                                // Not used in this UI implementation.
                                // Can be used for additional frame debugging.
                            }

                            is ScanningUxEvent.UnsupportedDocument -> {
                                showErrorDialog(
                                    alertType = UxEvent.AlertType.DOCUMENTNOTSUPPORTED,
                                    errorState = ErrorState.ErrorUnsupportedDocument
                                )
                            }
                        }
                    }

                    val processingStateBeforeUpdate = _uiState.value.processingState
                    val statusMessageBeforeUpdate = _uiState.value.statusMessage
                    updateUiState(
                        newProcessingState,
                        newStatusMessage,
                        newActivePassportPage,
                        newCurrentSide
                    )
                    if (scanningProgressed ||
                        processingStateBeforeUpdate != _uiState.value.processingState ||
                        statusMessageBeforeUpdate != _uiState.value.statusMessage
                    ) {
                        inactivityTimeoutStartTimestamp = null
                    }
                }
            }
        )

        if (isConsentPending()) {
            // No frame may be processed before consent is resolved.
            lifecyclePauseAnalysis()
        }

        viewModelScope.launch {
            isTorchSupported.collect { isTorchSupported ->
                _uiState.update {
                    it.copy(
                        torchState = if (isTorchSupported) MbTorchState.Off else MbTorchState.NotSupportedByCamera
                    )
                }
            }
        }
    }

    private fun updateUiState(
        newProcessingState: ProcessingState?,
        newStatusMessage: StatusMessage?,
        newActivePassportPage: PassportPage?,
        newCurrentSide: UiScanningSide?
    ) {
        newProcessingState?.let {
            if (newProcessingState is ProcessingState.SuccessAnimation || newStatusMessage == BlinkIdStatusMessage.ScanBarcode) {
                isStateTimeoutActive = false
                runBlocking {
                    waitForMinimumStateDuration(newProcessingState)
                }
            } else if (isStateTimeoutActive || shouldStartCounting(uiState.value.processingState)) {
                newStatusMessage?.let {
                    statusCounter.increment(it)
                }
            }

            newStatusMessage?.let {
                val stateRemDur = remainingStateDuration(
                    uiState.value.processingState,
                    newProcessingState,
                    newStatusMessage
                )
                if (stateRemDur <= Duration.ZERO || newProcessingState is ProcessingState.SuccessAnimation || newStatusMessage == BlinkIdStatusMessage.ScanBarcode) {
                    val (selectedProcessingState, selectedStatusMessage) =
                        if (newProcessingState.reticleState == ReticleState.Success || newProcessingState.reticleState == ReticleState.SuccessFirstSide || newStatusMessage == BlinkIdStatusMessage.ScanBarcode) {
                            Pair(
                                newProcessingState,
                                newStatusMessage
                            )
                        } else if (uiState.value.statusMessage == BlinkIdStatusMessage.ScanBarcode) {
                            Pair(
                                null,
                                null
                            )
                        } else pickNewState()
                    selectedProcessingState?.let {
                        val newHapticFeedbackState = when (selectedProcessingState) {
                            ProcessingState.Error -> HapticFeedbackState.VibrationOneTimeShort
                            is ProcessingState.SuccessAnimation -> {
                                if (selectedProcessingState.isFirstSide) {
                                    HapticFeedbackState.VibrationOneTimeShort
                                } else {
                                    HapticFeedbackState.VibrationOneTimeLong
                                }
                            }

                            else -> null
                        }
                        val newScanSoundState = when (selectedProcessingState) {
                            is ProcessingState.SuccessAnimation -> ScanSoundState.PlayScanBeep
                            else -> null
                        }
                        selectedStatusMessage?.let {
                            if (selectedProcessingState == ProcessingState.Error) {
                                val errorType: UxEvent.ErrorMessageType? =
                                    when (selectedStatusMessage) {
                                        is CommonStatusMessage -> when (selectedStatusMessage) {
                                            CommonStatusMessage.MoveCloser -> UxEvent.ErrorMessageType.MOVECLOSER
                                            CommonStatusMessage.MoveFarther -> UxEvent.ErrorMessageType.MOVEFARTHER
                                            CommonStatusMessage.KeepVisible -> UxEvent.ErrorMessageType.KEEPVISIBLE
                                            CommonStatusMessage.ScanningWrongSide -> UxEvent.ErrorMessageType.FLIPSIDE
                                            CommonStatusMessage.Align -> UxEvent.ErrorMessageType.ALIGNDOCUMENT
                                            CommonStatusMessage.EliminateBlur -> UxEvent.ErrorMessageType.ELIMINATEBLUR
                                            else -> null
                                        }

                                        is BlinkIdStatusMessage -> when (selectedStatusMessage) {
                                            BlinkIdStatusMessage.KeepFacePhotoVisible -> UxEvent.ErrorMessageType.KEEPVISIBLE
                                            BlinkIdStatusMessage.IncreaseLightingIntensity -> UxEvent.ErrorMessageType.INCREASELIGHTING
                                            BlinkIdStatusMessage.DecreaseLightingIntensity -> UxEvent.ErrorMessageType.DECREASELIGHTING
                                            BlinkIdStatusMessage.EliminateGlare -> UxEvent.ErrorMessageType.ELIMINATEGLARE
                                            else -> null
                                        }

                                        is VerifyStatusMessage -> when (selectedStatusMessage) {
                                            VerifyStatusMessage.MoveToPlainBackground ->
                                                UxEvent.ErrorMessageType.KEEPVISIBLE
                                        }

                                        else -> null
                                    }

                                errorType?.let { trackedErrorType ->
                                    // Track only transitions to avoid repeated pinglets for the same error message.
                                    if (trackedErrorType != lastTrackedErrorType) {
                                        lastTrackedErrorType = trackedErrorType
                                        UxPingletTracker.UxEvent.trackErrorMessageEvent(
                                            trackedErrorType,
                                            getSessionNumber()
                                        )
                                    }
                                }
                            }
                            _uiState.update {
                                it.copy(
                                    reticleState = selectedProcessingState.reticleState,
                                    processingState = selectedProcessingState,
                                    statusMessage = selectedStatusMessage,
                                    hapticFeedbackState = newHapticFeedbackState
                                        ?: it.hapticFeedbackState,
                                    scanSoundState = newScanSoundState
                                        ?: it.scanSoundState,
                                    activePassportPage = newActivePassportPage ?: it.activePassportPage,
                                    currentSide = newCurrentSide ?: it.currentSide
                                )
                            }
                            appearanceCounter.incrementIfNotPresent(selectedStatusMessage)
                            updateStateStartTime()
                        }
                    }
                }
            }
        }
    }

    fun setInitialUiStateFromUiSettings(uiSettings: UiSettings) {
        val deferOnboardingDialog = isConsentPending()
        showOnboardingDialogAfterConsent = deferOnboardingDialog && uiSettings.showOnboardingDialog
        _uiState.update {
            it.copy(
                helpButtonDisplayed = uiSettings.showHelpButton,
                onboardingDialogDisplayed = uiSettings.showOnboardingDialog && !deferOnboardingDialog
            )
        }
        if (_uiState.value.onboardingDialogDisplayed) {
            changeOnboardingDialogVisibility(true)
        } else {
            changeOnboardingDialogVisibility(false)
        }
    }

    /**
     * Called when the end user accepts the consent on [com.microblink.blinkidverify.ux.consent.MicroblinkConsentScreen].
     *
     * Builds the consent object, stores it for native `getResult`, and only then allows scanning to
     * start.
     *
     * @param consentNote Legal text the end user agreed to, recorded with the consent.
     */
    fun onConsentAccepted(consentNote: String?) {
        viewModelScope.launch {
            val consent = consentManager.consentFromUserAcceptance(consentNote) ?: return@launch
            val consentSet = imageAnalyzer?.setConsent(consent) == true
            if (consentSet) {
                MbLog.i(CmsFlowLog.TAG) {
                    "[CMS] End-user consent accepted — scanning may proceed: " +
                        CmsFlowLog.consentSummary(consent)
                }
                _uiState.update { it.copy(consentUxState = ConsentUxState.ConsentGranted) }
                onConsentResolved()
            } else {
                MbLog.e(CmsFlowLog.TAG) {
                    "[CMS] Consent could not be applied to the scanning session"
                }
                onConsentDeclined()
            }
        }
    }

    /**
     * Called when the end user declines the consent. Scanning cannot proceed, so the scanning screen
     * reports cancellation to the integrator.
     */
    fun onConsentDeclined() {
        MbLog.i(CmsFlowLog.TAG) { "[CMS] End-user consent declined — scanning canceled" }
        lifecyclePauseAnalysis()
        _uiState.update { it.copy(consentUxState = ConsentUxState.ConsentDeclined) }
    }

    private fun onConsentResolved() {
        if (showOnboardingDialogAfterConsent) {
            showOnboardingDialogAfterConsent = false
            changeOnboardingDialogVisibility(true)
        } else {
            lifecycleResumeAnalysis()
        }
    }

    private fun isConsentPending(): Boolean =
        _uiState.value.consentUxState == ConsentUxState.ConsentRequired

    private fun isConsentBlockingScanning(): Boolean = when (_uiState.value.consentUxState) {
        ConsentUxState.ConsentRequired,
        ConsentUxState.ConsentDeclined -> true

        ConsentUxState.ConsentGranted,
        ConsentUxState.ConsentNotRequired -> false
    }

    fun pickNewState(): Pair<ProcessingState?, StatusMessage?> {
        val max = statusCounter.getAllCounts().values.maxOrNull()
        val mostFrequent = statusCounter.getAllCounts().filterValues { it == max }.keys.toList()
        statusCounter.reset()
        return if (mostFrequent.isNotEmpty()) {
            when (mostFrequent[0]) {
                BlinkIdStatusMessage.RotateDocument, CommonStatusMessage.ScanFirstSide, CommonStatusMessage.ScanSecondSide, BlinkIdStatusMessage.ScanBarcode, BlinkIdStatusMessage.PassportScanTopPage, BlinkIdStatusMessage.PassportScanLeftPage, BlinkIdStatusMessage.PassportScanRightPage, BlinkIdStatusMessage.PassportScanBarcodePage -> {
                    Pair(ProcessingState.Sensing, mostFrequent[0])
                }

                else -> {
                    Pair(ProcessingState.Error, mostFrequent[0])
                }

            }
        } else Pair(null, null)
    }


    fun setScreenOrientation(screenOrientation: ScreenOrientation) {
        val currentTime = System.currentTimeMillis()
        if (currentScreenOrientation != screenOrientation) {
            currentScreenOrientation = screenOrientation
            // track pinglet only when screen orientation changes (with a delay)
            if (currentTime - lastScreenOrientationPingTime >= pingletOrientationDelayMs) {
                UxPingletTracker.ScanningConditions.trackScreenOrientationChange(
                    screenOrientation = screenOrientation,
                    sessionNumber = getSessionNumber()
                )
                lastScreenOrientationPingTime = currentTime
            }
        }
        _uiState.update {
            it.copy(
                screenOrientation = screenOrientation
            )
        }
    }

    override fun analyzeImage(image: ImageProxy) {
        image.use {
            imageAnalyzer?.analyze(it)
        }
    }

    private fun showErrorDialog(alertType: UxEvent.AlertType, errorState: ErrorState) {
        firstImageTimestamp = null
        lifecyclePauseAnalysis()
        appearanceCounter.reset()
        UxPingletTracker.UxEvent.trackAlertDisplayedEvent(
            alertType = alertType,
            sessionNumber = getSessionNumber()
        )
        _uiState.update {
            it.copy(
                errorState = errorState,
                processingState = ProcessingState.ErrorDialog,
                hapticFeedbackState = HapticFeedbackState.VibrationOneTimeLong,
                activePassportPage = null
            )
        }
    }

    fun lifecyclePauseAnalysis() {
        if (!isAnalysisPaused) {
            stepTimeoutDurationBeforePause =
                firstImageTimestamp?.let { System.nanoTime() - it }
            isAnalysisPaused = true
        }
        imageAnalyzer?.pauseAnalysis()
        inactivityTimeoutStartTimestamp = null
        firstImageTimestamp = null
        helpTooltipTimer?.cancel()
        statusCounter.reset()
        isStateTimeoutActive = false
    }

    fun lifecycleResumeAnalysis() {
        if (isConsentBlockingScanning()) {
            MbLog.d(TAG) { "Analysis stays paused until consent is granted" }
            return
        }
        if (!_uiState.value.onboardingDialogDisplayed && !_uiState.value.helpDisplayed && _uiState.value.errorState == ErrorState.NoError) {
            imageAnalyzer?.resumeAnalysis()
            helpTooltipTimer?.start()
            if (isAnalysisPaused) {
                stepTimeoutDurationBeforePause?.let { elapsedDuration ->
                    firstImageTimestamp = System.nanoTime() - elapsedDuration
                }
                stepTimeoutDurationBeforePause = null
                isAnalysisPaused = false
            }
            inactivityTimeoutStartTimestamp = null
            isStateTimeoutActive = true
        }
    }

    suspend fun waitForMinimumStateDuration(newProcessingState: ProcessingState) {
        var remainingDuration: Duration
        do {
            remainingDuration = remainingStateDuration(
                uiState.value.processingState,
                newProcessingState,
                uiState.value.statusMessage
            )
            if (remainingDuration > Duration.ZERO) {
                delay(remainingDuration.inWholeMilliseconds)
            }
        } while (remainingDuration > Duration.ZERO)
    }

    fun remainingStateDuration(
        currentState: ProcessingState,
        newState: ProcessingState,
        newStatusMessage: StatusMessage
    ): Duration {
        if ((newState.reticleState == ReticleState.Success || newState.reticleState == ReticleState.SuccessFirstSide) && appearanceCounter.getAllCounts()
                .contains(newStatusMessage)
        ) {
            val remainingDuration =
                System.nanoTime().nanoseconds - uiStateStartTime - currentState.minDuration
            return -remainingDuration
        } else {
            val remainingDuration =
                System.nanoTime().nanoseconds - uiStateStartTime - currentState.duration
            return -remainingDuration
        }
    }

    fun shouldStartCounting(currentState: ProcessingState): Boolean {
        if ((System.nanoTime().nanoseconds - uiStateStartTime + countingWindowDuration) >= currentState.duration) {
            isStateTimeoutActive = true
            return true
        } else {
            return false
        }
    }

    fun onFlipAnimationCompleted() {
        _uiState.update {
            it.copy(
                processingState = ProcessingState.Sensing,
                cardAnimationState = CardAnimationState.Hidden,
                statusMessage =
                    if (uiState.value.activePassportPage != null) {
                        when (uiState.value.activePassportPage) {
                            PassportPage.Top -> BlinkIdStatusMessage.PassportScanTopPage
                            PassportPage.Right -> BlinkIdStatusMessage.PassportScanRightPage
                            PassportPage.Left -> BlinkIdStatusMessage.PassportScanLeftPage
                            PassportPage.Barcode -> BlinkIdStatusMessage.PassportScanBarcodePage
                            else -> BlinkIdStatusMessage.PassportScanTopPage
                        }
                    } else {
                        when (it.currentSide) {
                            UiScanningSide.First -> CommonStatusMessage.ScanFirstSide
                            UiScanningSide.Second -> CommonStatusMessage.ScanSecondSide
                            UiScanningSide.Barcode -> BlinkIdStatusMessage.ScanBarcode
                        }
                    }
            )
        }
        updateStateStartTime()
        if (!_uiState.value.onboardingDialogDisplayed && !_uiState.value.helpDisplayed) {
            lifecycleResumeAnalysis()
        }
    }

    private fun updateStateStartTime() {
        uiStateStartTime =
            System.nanoTime()
                .toDuration(DurationUnit.NANOSECONDS)
    }

    fun changeTorchState() {
        val (newTorchState, hapticState) = when (_uiState.value.torchState) {
            MbTorchState.On -> MbTorchState.Off to HapticFeedbackState.VibrationOff
            MbTorchState.Off -> MbTorchState.On to HapticFeedbackState.VibrationOneTimeShort
            MbTorchState.NotSupportedByCamera -> return
        }
        _torchOn.value = newTorchState == MbTorchState.On
        _uiState.update {
            it.copy(
                torchState = newTorchState,
                hapticFeedbackState = hapticState
            )
        }
        UxPingletTracker.ScanningConditions.trackTorchStateUpdate(
            newTorchState == MbTorchState.On,
            getSessionNumber()
        )
    }

    fun changeHelpTooltipVisibility(show: Boolean) {
        if (_uiState.value.helpButtonDisplayed) {
            if (show) {
                helpTooltipTimer?.cancel()
            } else {
                helpTooltipTimer?.start()
            }
            _uiState.update {
                it.copy(helpTooltipDisplayed = show)
            }
        }
    }

    fun changeOnboardingDialogVisibility(show: Boolean) {
        _uiState.update {
            it.copy(onboardingDialogDisplayed = show)
        }
        if (show) {
            UxPingletTracker.UxEvent.trackSimpleEvent(
                UxPingletTracker.UxEvent.SimpleUxEventType.OnboardingInfoDisplayed,
                getSessionNumber()
            )
            lifecyclePauseAnalysis()
        } else {
            lifecycleResumeAnalysis()
        }
    }

    fun onHelpScreensDisplayRequested() {
        UxPingletTracker.UxEvent.trackSimpleEvent(
            UxPingletTracker.UxEvent.SimpleUxEventType.HelpOpened,
            getSessionNumber()
        )
        _uiState.update {
            it.copy(helpDisplayed = true)
        }
        lifecyclePauseAnalysis()
    }

    fun onHelpScreensCloseRequested(allPagesVisited: Boolean) {
        UxPingletTracker.UxEvent.trackHelpCloseEvent(
            helpCloseType = if (allPagesVisited) {
                UxEvent.HelpCloseType.CONTENTFULLYVIEWED
            } else {
                UxEvent.HelpCloseType.CONTENTSKIPPED
            },
            sessionNumber = getSessionNumber()
        )
        _uiState.update {
            it.copy(helpDisplayed = false)
        }
        lifecycleResumeAnalysis()
    }

    fun onRetryTimeout() {
        helpTooltipTimer?.cancel()
        firstImageTimestamp = null
        stepTimeoutDurationBeforePause = null
        inactivityTimeoutStartTimestamp = null
        isAnalysisPaused = false
        isStateTimeoutActive = true
        _uiState.update {
            it.copy(
                errorState = ErrorState.NoError,
                processingState = ProcessingState.Sensing,
                statusMessage = CommonStatusMessage.ScanFirstSide,
                currentSide = UiScanningSide.First,
                activePassportPage = null
            )
        }
        updateStateStartTime()
        viewModelScope.launch {
            imageAnalyzer?.restartAnalysis()
        }
        helpTooltipTimer?.start()
    }

    fun onHapticFeedbackCompleted() {
        _uiState.update {
            it.copy(
                hapticFeedbackState = HapticFeedbackState.VibrationOff
            )
        }
    }

    fun onScanSoundCompleted() {
        _uiState.update {
            it.copy(
                scanSoundState = ScanSoundState.SoundOff
            )
        }
    }

    fun onReticleSuccessAnimationCompleted() {
        if (_uiState.value.processingState is ProcessingState.SuccessAnimation && (_uiState.value.processingState as ProcessingState.SuccessAnimation).isFirstSide) {
            if (_uiState.value.activePassportPage != null) {
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.CardAnimation,
                        statusMessage = when (it.activePassportPage) {
                            PassportPage.Top -> BlinkIdStatusMessage.PassportMoveToTop
                            PassportPage.Right -> BlinkIdStatusMessage.PassportMoveToRight
                            PassportPage.Left -> BlinkIdStatusMessage.PassportMoveToLeft
                            PassportPage.Barcode -> BlinkIdStatusMessage.PassportMoveToBarcode
                            else -> BlinkIdStatusMessage.ScanPassportDataPage
                        },
                        currentSide = UiScanningSide.Second,
                        cardAnimationState =
                            when (it.activePassportPage) {
                                PassportPage.Top -> ShowPassportMoveToTop
                                PassportPage.Right -> ShowPassportMoveToRight
                                PassportPage.Left -> ShowPassportMoveToLeft
                                PassportPage.Barcode -> ShowPassportMoveToBarcode
                                else -> ShowPassportMoveToTop
                            }
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        processingState =
                            ProcessingState.CardAnimation,
                        statusMessage = CommonStatusMessage.Flip,
                        currentSide = UiScanningSide.Second,
                        cardAnimationState = ShowFlipLandscape(
                            firstSideDrawable = R.drawable.mb_blinkidverify_card_front,
                            secondSideDrawable = R.drawable.mb_blinkidverify_card_back
                        )
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    processingState = ProcessingState.Success
                )
            }
        }
    }

    fun onCameraInputInfoAvailable(
        context: Context,
        cameraInputDetails: CameraInputDetails
    ) {
        getSessionNumber().takeIf { it > 0 }
            ?.let { sessionNumber ->
                UxPingletTracker.CameraInfo.trackCameraInputInfo(
                    cameraInputDetails,
                    sessionNumber
                )
                if (!cameraHardwareInfoReported) {
                    cameraHardwareInfoReported = true
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            val cameraDetailsList = CameraHardwareInfoHelper.getCameraHardwareInfo(context)
                            UxPingletTracker.CameraInfo.trackCameraHardwareInfo(
                                cameraDetailsList,
                                sessionNumber
                            )
                        }
                    }
                }
            }
    }

    fun getSessionNumber(): Int = imageAnalyzer?.getSessionNumber() ?: 0

    override fun onCleared() {
        super.onCleared()
        BlinkIdVerifySdk.sendPingletsIfAllowed(PingSendTriggerPoint.CameraScreenClosed)
        firstImageTimestamp = null
        lifecyclePauseAnalysis()
        imageAnalyzer?.cancel()
        imageAnalyzer?.close()
        imageAnalyzer = null
    }

    companion object {
        private const val TAG = "BlinkIdVerifyUxViewModel"

        // Define a custom key for your dependency
        val BLINK_ID_VERIFY_SDK =
            object : CreationExtras.Key<BlinkIdVerifySdk> {}
        val BLINK_ID_VERIFY_CAPTURE_SETTINGS =
            object : CreationExtras.Key<BlinkIdVerifySessionSettings> {}
        val BLINK_ID_VERIFY_UX_SETTINGS =
            object : CreationExtras.Key<VerifyUxSettings> {}
        val BLINK_ID_VERIFY_CONSENT_UX_CONFIG =
            object : CreationExtras.Key<BlinkIdVerifyConsentUxConfig> {}
        val BLINK_ID_VERIFY_APPLICATION_CONTEXT =
            object : CreationExtras.Key<Context> {}
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BlinkIdVerifyUxViewModel(
                    this[BLINK_ID_VERIFY_SDK] as BlinkIdVerifySdk,
                    this[BLINK_ID_VERIFY_CAPTURE_SETTINGS] as BlinkIdVerifySessionSettings,
                    this[BLINK_ID_VERIFY_UX_SETTINGS] as VerifyUxSettings,
                    this[BLINK_ID_VERIFY_CONSENT_UX_CONFIG] as BlinkIdVerifyConsentUxConfig,
                    this[BLINK_ID_VERIFY_APPLICATION_CONTEXT] as Context
                )
            }
        }
    }
}