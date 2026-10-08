/**
 * Copyright (c) Microblink. All rights reserved. This code is provided for
 * use as-is and may not be copied, modified, or redistributed.
 */

package com.microblink.blinkidverify.ux.capture.scanning

import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifyProcessResult
import com.microblink.blinkidverify.core.image.InputImage
import com.microblink.blinkidverify.ux.ScanningUxEvent

/**
 * An interface that represents the translation process from [ScanningUxEvent] to the UX.
 */
interface VerifyUxTranslator {
    suspend fun translate(
        processResult: BlinkIdVerifyProcessResult,
        inputImage: InputImage?,
    ): List<ScanningUxEvent>
}