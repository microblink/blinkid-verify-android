/**
 * Copyright (c) Microblink. All rights reserved. This code is provided for
 * use as-is and may not be copied, modified, or redistributed.
 */

package com.microblink.blinkidverify.ux.capture.scanning

import com.microblink.blinkidverify.core.result.ProcessingStatus
import com.microblink.blinkidverify.core.result.classinfo.CountryId
import com.microblink.blinkidverify.core.result.classinfo.DocumentTypeId
import com.microblink.blinkidverify.ux.scanning.RequestPassportPage
import com.microblink.blinkidverify.ux.scanning.ScanningWrongPassportPage
import com.microblink.blinkidverify.ux.state.PassportPage
import com.microblink.blinkidverify.ux.state.PassportType
import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifyProcessResult
import com.microblink.blinkidverify.core.data.model.result.VerifyScanningStatus
import com.microblink.blinkidverify.core.image.InputImage
import com.microblink.blinkidverify.core.session.DetectionStatus
import com.microblink.blinkidverify.ux.ScanningUxEvent
import com.microblink.blinkidverify.ux.state.UiScanningSide
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Translates [BlinkIdVerifyProcessResult] and other scanning session information into a
 * list of [ScanningUxEvent] objects.
 *
 * This class is responsible for interpreting the results of the document
 * scanning process and generating user experience-related events that can be
 * used to update the UI or provide feedback to the user. It handles logic
 * related to document sides, timeouts, and various detection statuses.
 *
 */
class VerifyScanningUxTranslator : VerifyUxTranslator {

    private val backToBarcodeTimeout = 3.seconds
    private var barcodeDispatched = false

    private var passportType: PassportType? = null

    private var currentSide = UiScanningSide.First

    private var firstBackRequestedTimestamp: Long? = null

    private val unsupportedDocumentTimeout = 1.5.seconds
    private var firstUnsupportedDocumentTimestamp: Long? = null

    /**
     * Translates the given [BlinkIdVerifyProcessResult], [InputImage]
     * into a list of [ScanningUxEvent] objects.
     *
     * This function analyzes the current state of the scanning session and the
     * results of the last image processing step to determine which UX events
     * should be generated.
     *
     * @param processResult The [BlinkIdVerifyProcessResult] from the scanning session.
     * @param inputImage The [InputImage] used for the process. Can be `null`.
     * @return A list of [ScanningUxEvent] objects representing the user
     *         experience events that should be dispatched.
     */
    override suspend fun translate(
        processResult: BlinkIdVerifyProcessResult,
        inputImage: InputImage?,
    ): List<ScanningUxEvent> {
        val events = mutableListOf<ScanningUxEvent>()

        val inputImageAnalysisResult = processResult.inputImageAnalysisResult
        val extractionImageAnalysisResult = inputImageAnalysisResult.extractionInputImageAnalysisResult

        if (processResult.resultCompleteness.isComplete()) {
            events.add(ScanningUxEvent.ScanningDone)
            return events
        }

        extractionImageAnalysisResult.documentClassInfo?.documentType?.id
            ?.takeIf { it == DocumentTypeId.Passport }
            ?.let {
                passportType = if (
                    extractionImageAnalysisResult.documentClassInfo?.country?.id == CountryId.Usa ||
                    extractionImageAnalysisResult.documentClassInfo?.country?.id == CountryId.India
                ) {
                    PassportType.BackSideBarcode
                } else {
                    PassportType.Regular
                }
            }

        if (currentSide == UiScanningSide.First) {
            if (processResult.resultCompleteness.scanningStatus == VerifyScanningStatus.ScannedFirst) {
                currentSide = UiScanningSide.Second
                if (passportType != null) {
                    events.add(
                        RequestPassportPage(
                            documentRotation = extractionImageAnalysisResult.documentRotation,
                            isBarcodePageRequested = passportType == PassportType.BackSideBarcode
                        )
                    )
                } else {
                    events.add(ScanningUxEvent.RequestSide(UiScanningSide.Second))
                }
            }
        } else if (currentSide == UiScanningSide.Second) {
            if (processResult.resultCompleteness.scanningStatus != VerifyScanningStatus.ScanningSecond) {
                currentSide = UiScanningSide.First
            } else if (firstBackRequestedTimestamp == null) {
                firstBackRequestedTimestamp = System.nanoTime()
            } else {
                if (shouldRequestBarcode(processResult)) {
                    barcodeDispatched = true
                    events.add(ScanningUxEvent.RequestSide(UiScanningSide.Barcode))
                }
            }
        }
        if (events.isNotEmpty()) return events

        if (extractionImageAnalysisResult.documentLocation != null) {
            events.add(
                if (inputImage != null) {
                    BlinkIdVerifyDocumentLocatedLocation(
                        location = extractionImageAnalysisResult.documentLocation!!,
                        inputImage = inputImage
                    )
                } else {
                    ScanningUxEvent.DocumentLocated
                }
            )
        } else {
            events.add(ScanningUxEvent.DocumentNotFound)
        }

        // below just one event can be generated, by following priorities
        var hasEvents = false

        val previousUnsupportedTimestamp = firstUnsupportedDocumentTimestamp
        firstUnsupportedDocumentTimestamp = null

        when (extractionImageAnalysisResult.processingStatus) {
            ProcessingStatus.UnsupportedDocument -> {
                firstUnsupportedDocumentTimestamp = previousUnsupportedTimestamp ?: System.nanoTime()
                if (shouldShowUnsupportedDocument()) {
                    events.add(ScanningUxEvent.UnsupportedDocument)
                }
                hasEvents = true
            }

            ProcessingStatus.AwaitingOtherSide -> {
                when (passportType) {
                    PassportType.Regular -> events.add(
                        RequestPassportPage(
                            documentRotation = extractionImageAnalysisResult.documentRotation,
                            isBarcodePageRequested = false
                        )
                    )

                    PassportType.BackSideBarcode -> events.add(
                        RequestPassportPage(
                            documentRotation = extractionImageAnalysisResult.documentRotation,
                            isBarcodePageRequested = true
                        )
                    )

                    null -> events.add(ScanningUxEvent.RequestSide(side = currentSide))
                }
                hasEvents = true
            }

            ProcessingStatus.ScanningWrongSide -> {
                val isScanningDataPage = currentSide == UiScanningSide.First
                events.add(
                    when (passportType) {
                        PassportType.Regular -> ScanningWrongPassportPage(
                            activePassportPage = if (isScanningDataPage) PassportPage.Data else null,
                            documentRotation = extractionImageAnalysisResult.documentRotation
                        )

                        PassportType.BackSideBarcode -> ScanningWrongPassportPage(
                            activePassportPage = if (isScanningDataPage) PassportPage.Data else PassportPage.Barcode,
                            documentRotation = extractionImageAnalysisResult.documentRotation
                        )

                        else -> ScanningUxEvent.ScanningWrongSide
                    }
                )
                hasEvents = true
            }

            ProcessingStatus.MandatoryFieldMissing,
            ProcessingStatus.MrzParsingFailed,
            ProcessingStatus.InvalidCharactersFound -> {
                events.add(ScanningUxEvent.DocumentNotFullyVisible)
                hasEvents = true
            }

            else -> { }
        }

        if (hasEvents) {
            events.add(VerifyDocumentImageAnalysisResult(extractionImageAnalysisResult = extractionImageAnalysisResult))
            return events
        }

        hasEvents = true

        when (extractionImageAnalysisResult.documentDetectionStatus) {
            DetectionStatus.CameraTooFar -> events.add(ScanningUxEvent.DocumentTooFar)
            DetectionStatus.CameraTooClose,
            DetectionStatus.DocumentTooCloseToCameraEdge -> events.add(ScanningUxEvent.DocumentTooClose)
            DetectionStatus.DocumentPartiallyVisible -> events.add(ScanningUxEvent.DocumentNotFullyVisible)
            DetectionStatus.CameraAngleTooSteep -> events.add(ScanningUxEvent.DocumentTooTilted)
            else -> {
                hasEvents = false
            }
        }

        if (hasEvents) {
            events.add(VerifyDocumentImageAnalysisResult(extractionImageAnalysisResult = extractionImageAnalysisResult))
            return events
        }

        hasEvents = true

        if (
            inputImageAnalysisResult.screenPresenceDetected &&
            extractionImageAnalysisResult.documentDetectionStatus == DetectionStatus.Success &&
            currentSide != UiScanningSide.Barcode
        ) {
            events.add(ScreenPresenceDetected)
        } else if (inputImageAnalysisResult.glareDetected) events.add(ScanningUxEvent.GlareDetected)
        else if (inputImageAnalysisResult.blurDetected) events.add(ScanningUxEvent.BlurDetected)
        else if (inputImageAnalysisResult.occlusionDetected) events.add(ScanningUxEvent.DocumentNotFullyVisible)
        else if (inputImageAnalysisResult.tiltDetected) events.add(ScanningUxEvent.DocumentTooTilted)
        else hasEvents = false

        if (hasEvents) {
            events.add(VerifyDocumentImageAnalysisResult(extractionImageAnalysisResult = extractionImageAnalysisResult))
            return events
        }

        events.add(ScanningUxEvent.DocumentNotFound)
        events.add(ScanningUxEvent.RequestSide(side = currentSide))
        events.add(VerifyDocumentImageAnalysisResult(extractionImageAnalysisResult = extractionImageAnalysisResult))
        return events
    }

    fun resetSession() {
        firstBackRequestedTimestamp = null
        barcodeDispatched = false
        passportType = null
        firstUnsupportedDocumentTimestamp = null
    }

    private fun shouldShowUnsupportedDocument(): Boolean {
        return (System.nanoTime() - firstUnsupportedDocumentTimestamp!!).nanoseconds > unsupportedDocumentTimeout
    }

    private fun shouldRequestBarcode(processResult: BlinkIdVerifyProcessResult): Boolean {
        return (System.nanoTime() - firstBackRequestedTimestamp!!).nanoseconds > backToBarcodeTimeout && processResult.inputImageAnalysisResult.hasBarcodeReadingIssue && !barcodeDispatched
    }
}