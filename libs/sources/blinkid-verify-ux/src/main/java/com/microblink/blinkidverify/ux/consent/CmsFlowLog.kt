/*
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.consent

import com.microblink.blinkidverify.core.consent.model.ExternalConsentObject

/**
 * UX-layer log tag and safe summaries for the CMS (consent → payload → v3) flow.
 */
internal object CmsFlowLog {
    const val TAG: String = "BlinkIdVerifyCMS"

    fun consentSummary(consent: ExternalConsentObject?): String {
        if (consent == null) return "consent=absent"
        return buildString {
            append("consent=present")
            append(" userIdLen=${consent.userId.length}")
            append(" durationDays=${consent.durationDays}")
            append(" givenOn=${consent.givenOn ?: "unset"}")
            append(" noteLen=${consent.note?.length ?: 0}")
            consent.customerContext?.customerId?.let { append(" customerIdLen=${it.length}") }
            consent.customerContext?.transactionId?.let { append(" transactionIdLen=${it.length}") }
        }
    }
}
