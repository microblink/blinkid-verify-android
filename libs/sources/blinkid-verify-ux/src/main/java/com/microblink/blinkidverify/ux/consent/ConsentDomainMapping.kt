/*
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.consent

import com.microblink.blinkidverify.core.consent.domain.ConsentConfiguration
import com.microblink.blinkidverify.core.consent.domain.ConsentInitialState

/**
 * Maps presentation consent config to domain types (ADR #043 clean-architecture boundary).
 */
internal fun BlinkIdVerifyConsentUxConfig.toDomain(): ConsentConfiguration =
    when (this) {
        is BlinkIdVerifyConsentUxConfig.RequireConsent -> ConsentConfiguration.RequireConsent(
            userId = userId,
            durationDays = durationDays,
            note = note,
            customerContext = customerContext,
        )

        is BlinkIdVerifyConsentUxConfig.ProvideExternalConsent ->
            ConsentConfiguration.ProvideExternal(consent = consent)

        BlinkIdVerifyConsentUxConfig.NoConsentNeeded -> ConsentConfiguration.NotNeeded
    }

internal fun ConsentInitialState.toConsentUxState(): ConsentUxState =
    when (this) {
        ConsentInitialState.Required -> ConsentUxState.ConsentRequired
        ConsentInitialState.Granted -> ConsentUxState.ConsentGranted
        ConsentInitialState.NotRequired -> ConsentUxState.ConsentNotRequired
    }
