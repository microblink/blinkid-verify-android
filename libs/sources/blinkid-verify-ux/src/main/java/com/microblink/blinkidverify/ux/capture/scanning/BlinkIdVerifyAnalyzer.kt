/**
 * Copyright (c) Microblink. All rights reserved. This code is provided for
 * use as-is and may not be copied, modified, or redistributed.
 */

package com.microblink.blinkidverify.ux.capture.scanning

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.microblink.blinkidverify.core.BlinkIdVerifySdk
import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifyScanningSession
import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifySessionSettings
import com.microblink.blinkidverify.ux.consent.CmsFlowLog
import com.microblink.blinkidverify.core.consent.model.ExternalConsentObject
import com.microblink.blinkidverify.core.RemoteLicenseCheckException
import com.microblink.blinkidverify.core.image.InputImage
import com.microblink.blinkidverify.core.utils.MbLog
import com.microblink.blinkidverify.ux.ScanningUxEvent
import com.microblink.blinkidverify.ux.ScanningUxEventHandler
import com.microblink.blinkidverify.ux.camera.ImageAnalyzer
import com.microblink.blinkidverify.ux.camera.TimeoutCause
import com.microblink.blinkidverify.ux.state.UiScanningSide
import com.microblink.blinkidverify.ux.utils.ErrorReason
import com.microblink.blinkidverify.ux.utils.UxPingletTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val TAG = "BlinkIdVerifyAnalyzer"

/**
 * Analyzes images from the camera and processes them using the BlinkID Verify SDK.
 *
 * This class implements the [ImageAnalyzer] interface and is responsible for
 * receiving image frames from the camera, sending them to the BlinkID Verify
 * SDK for processing and results handling. It also manages the scanning
 * session, timeouts, and dispatches UI events.
 *
 * @property verifySdk An instance of the [BlinkIdVerifySdk] used for processing images.
 * @property sessionSettings The [BlinkIdVerifySessionSettings] used to configure the capture session.
 * @property verifyScanningDoneHandler A [VerifyScanningDoneHandler] to handle the completion
 *                                of the scanning process.
 * @property uxEventHandler An optional [ScanningUxEventHandler] to handle UI events.
 *
 */
class BlinkIdVerifyAnalyzer(
    verifySdk: BlinkIdVerifySdk,
    sessionSettings: BlinkIdVerifySessionSettings,
    private val verifyScanningDoneHandler: VerifyScanningDoneHandler,
    private val uxEventHandler: ScanningUxEventHandler? = null,
) : ImageAnalyzer {

    private var session: BlinkIdVerifyScanningSession? =
        runBlocking { verifySdk.createScanningSession(sessionSettings) }

    @Volatile
    private var analysisPaused = false
    private val verifyScanningUxTranslator = VerifyScanningUxTranslator()

    /**
     * Analyzes an image from the camera.
     *
     * This function is called for each frame captured by the camera. It sends the
     * image to the BlinkID Verify SDK for processing and handles the results,
     * timeouts and cancellations.
     *
     * Timeout handling is driven by [com.microblink.blinkidverify.ux.capture.settings.VerifyUxSettings.stepTimeoutDuration]
     * and [com.microblink.blinkidverify.ux.capture.settings.VerifyUxSettings.inactivityTimeoutDuration].
     *
     * @param image The [ImageProxy] containing the image to be analyzed.
     *
     */
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        if (analysisPaused) return
        runBlocking {
            val inputImage = InputImage.createFromCameraXImageProxy(image)
            inputImage.use {
                session?.let { session ->
                    try {
                        val sessionProcessResult = session.process(inputImage)
                        if (session.isCanceled) {
                            MbLog.w(TAG) { "processing has been canceled" }
                        } else {
                            sessionProcessResult.getOrNull()?.let { processResult ->
                                val events = verifyScanningUxTranslator.translate(
                                    processResult,
                                    inputImage
                                )

                                if (events.any { it is ScanningUxEvent.RequestSide && it.side == UiScanningSide.Barcode }) {
                                    session.setAllowBarcodeStep(true)
                                }

                                uxEventHandler?.onUxEvents(events)

                                if (processResult.resultCompleteness.isComplete()) {
                                    try {
                                        MbLog.i(CmsFlowLog.TAG) {
                                            "[CMS] Scan complete — invoking session.getResult() for v3 payload"
                                        }
                                        val sessionResult = session.getResult()
                                        MbLog.i(CmsFlowLog.TAG) {
                                            "[CMS] Capture result ready — serialized payload present=" +
                                                "${sessionResult.serializedVerifyPayload != null}"
                                        }
                                        pauseAnalysis()
                                        verifyScanningDoneHandler.onScanningFinished(sessionResult)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        MbLog.e(TAG, e) { "getResult failed after scanning completed" }
                                        pauseAnalysis()
                                        verifyScanningDoneHandler.onError(ErrorReason.ErrorGetResultFailed)
                                    }
                                } else {
                                    MbLog.v(TAG) { "Neither complete nor timeout, continuing..." }
                                }
                            }
                        }
                    } catch (_: RemoteLicenseCheckException) {
                        verifyScanningDoneHandler.onError(ErrorReason.ErrorInvalidLicense)
                    }
                }
            }
        }
    }

    override fun pauseAnalysis() {
        analysisPaused = true
    }

    override fun resumeAnalysis() {
        analysisPaused = false
    }

    override fun timeoutAnalysis(cause: TimeoutCause) {
        MbLog.e(TAG) { "processing timeout occurred: $cause" }

        // TODO now that this is called in restartAnalysis, maybe we don't need it here?
        verifyScanningUxTranslator.resetSession()

        val timeoutEvent = when (cause) {
            TimeoutCause.Step -> UxPingletTracker.UxEvent.SimpleUxEventType.StepTimeout
            TimeoutCause.Inactivity -> UxPingletTracker.UxEvent.SimpleUxEventType.InactivityTimeout
        }
        getSessionNumber()?.let { sessionNumber ->
            UxPingletTracker.UxEvent.trackSimpleEvent(timeoutEvent, sessionNumber)
        }

        onErrorAnalysis(
            when (cause) {
                TimeoutCause.Step -> ErrorReason.ErrorStepTimeoutExpired
                TimeoutCause.Inactivity -> ErrorReason.ErrorInactivityTimeoutExpired
            }
        )
    }

    fun getSessionNumber(): Int? {
        return session?.sessionNumber
    }

    private fun onErrorAnalysis(errorReason: ErrorReason) {
        pauseAnalysis()
        verifyScanningDoneHandler.onError(errorReason)
    }

    /**
     * Sets the end-user [consent] on the scanning session. Native `getResult` receives this
     * consent when generating the request payload.
     *
     * @return `true` when consent has been accepted for later payload generation.
     */
    suspend fun setConsent(consent: ExternalConsentObject): Boolean {
        MbLog.i(CmsFlowLog.TAG) {
            "[CMS] Analyzer forwarding consent to session: ${CmsFlowLog.consentSummary(consent)}"
        }
        return session?.setConsent(consent) ?: false
    }

    override fun cancel() {
        session?.cancelActiveProcess()
        verifyScanningDoneHandler.onScanningCanceled()
    }

    override suspend fun restartAnalysis() {
        analysisPaused = true
        // this was added for the Unsupported Dialog, so it can be reset properly
        verifyScanningUxTranslator.resetSession()
        try {
            withContext(Default) {
                session?.restartSession()
            }
        } finally {
            analysisPaused = false
        }
    }

    override fun close() {
        session?.also { s ->
            session = null
            CoroutineScope(IO).launch {
                s.close()
            }
        }
    }
}
