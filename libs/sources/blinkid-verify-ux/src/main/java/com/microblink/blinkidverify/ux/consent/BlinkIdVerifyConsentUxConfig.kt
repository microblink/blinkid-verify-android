/*
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.consent

import android.os.Parcelable
import com.microblink.blinkidverify.core.consent.model.CustomerContext
import com.microblink.blinkidverify.core.consent.model.ExternalConsentObject
import kotlinx.parcelize.Parcelize

/**
 * Consent configuration of the scanning screen.
 *
 * BlinkID Verify Cloud requires explicit end-user consent for every transaction. This configuration
 * decides how the scanning session obtains it:
 *
 * - [RequireConsent] - the SDK shows the built-in consent screen and builds an
 *   [ExternalConsentObject] when the end user accepts. The integrator must supply [RequireConsent.userId].
 *   [ExternalConsentObject.givenOn] is set by the SDK at acceptance time and is not configurable here.
 * - [ProvideExternalConsent] - the integrator collected consent on their own UI and hands over a
 *   complete [ExternalConsentObject], so no consent screen is shown.
 * - [NoConsentNeeded] - no consent screen and no consent object in the generated request payload.
 *   Use it only with self-hosted deployments, which do not require consent.
 *
 * Whichever variant is used, the resolved consent object is passed into native `getResult` when
 * the capture result is retrieved, and is also available on the capture session result's consent
 * field.
 *
 * The SDK does not persist consent. To reuse consent across sessions, persist the consent object of
 * a previous capture result and pass it back through [ProvideExternalConsent].
 */
public sealed interface BlinkIdVerifyConsentUxConfig : Parcelable {

    /**
     * Enables the SDK consent UI and provides customization points for both the UI and the generated
     * consent payload.
     *
     * Consent is collected on the built-in consent screen, which is shown before scanning starts.
     * Declining the consent cancels the scanning session.
     *
     * @property userId Identifier of the end user giving consent. Must be set by the integrator so
     *                  consent records can be correlated and revoked in the backend.
     * @property durationDays Validity of the collected consent, in days. When `null`, the SDK default
     *                        registered for the BlinkID Verify consent source is used.
     * @property note Legal text displayed on the consent screen and recorded in the consent object.
     *                When `null`, the SDK default copy is shown.
     * @property customerContext Optional business context stored alongside the consent record.
     */
    @Parcelize
    public data class RequireConsent @JvmOverloads constructor(
        val userId: String,
        val durationDays: Int? = null,
        val note: String? = null,
        val customerContext: CustomerContext? = null,
    ) : BlinkIdVerifyConsentUxConfig

    /**
     * Skips the SDK consent UI. The provided [consent] is added to the generated request payload as-is.
     *
     * The [consent] object must match the Verify v3 consent model, including [ExternalConsentObject.givenOn],
     * which the integrator sets when consent was collected on their own UI.
     *
     * @property consent Complete consent record collected on the integrator's own UI, or restored
     *                   from a previous session of the same end user.
     */
    @Parcelize
    public data class ProvideExternalConsent(
        val consent: ExternalConsentObject,
    ) : BlinkIdVerifyConsentUxConfig

    /**
     * Consent handling is disabled: no consent screen is shown and the generated request payload
     * contains no consent object.
     *
     * Intended for self-hosted deployments. BlinkID Verify Cloud rejects transactions without
     * consent.
     */
    @Parcelize
    public data object NoConsentNeeded : BlinkIdVerifyConsentUxConfig
}
