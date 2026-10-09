/*
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.consent

import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifySessionSettings
import com.microblink.blinkidverify.core.consent.domain.ConsentPolicy
import com.microblink.blinkidverify.core.consent.model.ExternalConsentObject
import com.microblink.blinkidverify.core.utils.MbLog

/**
 * Presentation adapter: applies domain consent decisions to session configuration.
 *
 * Keeps [ConsentManager] free of session-settings mutation rules — those live in [ConsentPolicy].
 */
internal class ConsentManager(
    consentUxConfig: BlinkIdVerifyConsentUxConfig,
) {

    private val configuration = consentUxConfig.toDomain()

    private val requireConsentConfig: BlinkIdVerifyConsentUxConfig.RequireConsent? =
        consentUxConfig as? BlinkIdVerifyConsentUxConfig.RequireConsent

    val initialConsentUxState: ConsentUxState =
        ConsentPolicy.initialState(configuration).toConsentUxState()

    init {
        MbLog.i(CmsFlowLog.TAG) {
            "[CMS] Consent UX configured mode=${configuration::class.simpleName}, " +
                "initialUxState=$initialConsentUxState, " +
                CmsFlowLog.consentSummary(ConsentPolicy.preKnownConsent(configuration))
        }
    }

    fun applyTo(sessionSettings: BlinkIdVerifySessionSettings): BlinkIdVerifySessionSettings {
        val preKnownConsent = ConsentPolicy.preKnownConsent(configuration)
        MbLog.i(CmsFlowLog.TAG) {
            "[CMS] Applying consent to session settings: ${CmsFlowLog.consentSummary(preKnownConsent)}"
        }
        return sessionSettings.copy(consent = preKnownConsent)
    }

    fun consentFromUserAcceptance(consentNote: String?): ExternalConsentObject? =
        ConsentPolicy.consentFromUserAcceptance(configuration, consentNote)

    /**
     * Exposed for UI copy customization on [MicroblinkConsentScreen] only.
     */
    val customConsentNote: String? = requireConsentConfig?.note
}
