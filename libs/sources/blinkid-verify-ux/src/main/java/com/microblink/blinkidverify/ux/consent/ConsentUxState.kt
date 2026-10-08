/*
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.consent

/**
 * State of the consent step of the scanning screen.
 *
 * Image analysis is paused while consent is [ConsentRequired], so no document is processed before
 * consent is resolved.
 */
public sealed interface ConsentUxState {

    /**
     * Consent is not handled by the SDK for this session, either because the integrator provides it
     * or because consent handling is disabled.
     */
    public data object ConsentNotRequired : ConsentUxState

    /**
     * The built-in consent screen is displayed and the SDK is waiting for the end user's decision.
     */
    public data object ConsentRequired : ConsentUxState

    /**
     * Consent is available and set on the scanning session. Scanning can proceed.
     */
    public data object ConsentGranted : ConsentUxState

    /**
     * The end user declined the consent. Scanning is canceled.
     */
    public data object ConsentDeclined : ConsentUxState
}
