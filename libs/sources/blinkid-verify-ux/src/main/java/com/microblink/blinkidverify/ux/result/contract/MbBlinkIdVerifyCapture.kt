/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.result.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.os.BundleCompat
import com.microblink.blinkidverify.core.BlinkIdVerifySdkSettings
import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifySessionSettings
import com.microblink.blinkidverify.core.data.model.result.BlinkIdVerifyCaptureResult
import com.microblink.blinkidverify.ux.activity.capture.BlinkIdVerifyCaptureActivity
import com.microblink.blinkidverify.ux.capture.settings.VerifyUxSettings
import com.microblink.blinkidverify.ux.consent.BlinkIdVerifyConsentUxConfig
import com.microblink.blinkidverify.ux.DefaultShowHelpButton
import com.microblink.blinkidverify.ux.DefaultShowOnboardingDialog
import com.microblink.blinkidverify.ux.camera.CameraSettings
import com.microblink.blinkidverify.ux.contract.CancelReason
import com.microblink.blinkidverify.ux.contract.ScanActivityColors
import com.microblink.blinkidverify.ux.contract.ScanActivityResultStatus
import com.microblink.blinkidverify.ux.contract.ScanActivitySettings
import com.microblink.blinkidverify.ux.theme.SdkStrings
import com.microblink.blinkidverify.ux.utils.ParcelableUiTypography
import kotlinx.parcelize.Parcelize

/**
 * Android activity result contract for launching the Microblink's BlinkID Verify Capture session and obtaining capture
 * results.
 *
 * To launch the scanning session, [BlinkIdVerifyActivitySettings] are required.
 * As a result, [BlinkIdVerifyCaptureActivityResult] is returned, which contains [ScanActivityResultStatus] and [BlinkIdVerifyCaptureResult].
 *
 */
class MbBlinkIdVerifyCapture :
    ActivityResultContract<BlinkIdVerifyActivitySettings, BlinkIdVerifyCaptureActivityResult>() {
    override fun createIntent(context: Context, input: BlinkIdVerifyActivitySettings): Intent {
        return Intent(context, BlinkIdVerifyCaptureActivity::class.java).also {
            input.saveToIntent(it)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): BlinkIdVerifyCaptureActivityResult {
        if (resultCode == Activity.RESULT_OK) {
            BlinkIdVerifyCaptureResultHolder.blinkIdVerifyCaptureResult?.let { result ->
                return BlinkIdVerifyCaptureActivityResult(
                    status = ScanActivityResultStatus.Scanned,
                    result = result
                )
            }
            throw IllegalStateException("Activity was completed successfully, but the result is empty!")
        } else {
            val cancelReason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getSerializableExtra(EXTRA_CANCEL_REASON, CancelReason::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getSerializableExtra(EXTRA_CANCEL_REASON) as? CancelReason
            }
            return when (cancelReason) {
                CancelReason.ErrorSdkInit ->
                    BlinkIdVerifyCaptureActivityResult(
                        status = ScanActivityResultStatus.ErrorSdkInit,
                        result = null
                    )

                CancelReason.UserRequested -> BlinkIdVerifyCaptureActivityResult(
                    status = ScanActivityResultStatus.Canceled,
                    result = null
                )

                else -> BlinkIdVerifyCaptureActivityResult(
                    status = ScanActivityResultStatus.Canceled,
                    result = null
                )
            }
        }
    }

    companion object {
        const val EXTRA_CANCEL_REASON = "ExtraCancelReason"
    }

}

/**
 * Configuration settings for the [BlinkIdVerifyCaptureActivity].
 *
 * This data class encapsulates the various settings that control the behavior and appearance of
 * the [BlinkIdVerifyCaptureActivity], including the SDK settings, capture session configurations,
 * UI customization options, and other miscellaneous preferences.
 *
 * @property blinkIdVerifySdkSettings The core SDK settings required for initializing and
 *           running the BlinkID Verify SDK. This is a mandatory parameter.
 * @property cameraSettings The [CameraSettings] used for document scanning. Defaults to [CameraSettings] with default values.
 * @property sessionSettings Configuration options for the document capture session. This
 *           allows you to customize aspects of the capture process, such as certain visual check strictness
 *           and timeout duration. Defaults to `CaptureSessionSettings()`.
 * @property uxSettings The [com.microblink.blinkidverify.ux.capture.settings.VerifyUxSettings] used to customize the UX.
 * @property consentUxConfig The [BlinkIdVerifyConsentUxConfig] that defines how end-user consent for
 *           cloud processing is obtained. Declining the consent cancels the activity with
 *           [CancelReason.UserRequested].
 * @property scanActivityUiColors Custom colors for the `BlinkIdVerifyActivity` user interface.
 *           If set to `null`, the default colors will be used. Defaults to `null`.
 * @property scanActivityUiStrings Custom strings for the `BlinkIdVerifyActivity` user
 *           interface. Defaults to [SdkStrings.Default].
 * @property scanActivityTypography Custom typography for the `BlinkIdVerifyActivity` user
 *           interface. Due to a limitation of [Typography] class, [ParcelableUiTypography] mimics
 *           [com.microblink.ux.theme.UiTypography] by allowing the customization of all the elements to a lesser extent.
 *           The most important [TextStyle] and [Font] customizations are still available through this class.
 *           Defaults to [ParcelableUiTypography.Default].
 * @property showOnboardingDialog Determines whether an onboarding dialog should be displayed to
 *           the user when the activity is first launched. Defaults to [DefaultShowOnboardingDialog].
 * @property showHelpButton Determines whether a help button should be displayed in the activity.
 *           The button allows the user to open help screens during scanning. Defaults to [DefaultShowHelpButton].
 * @property enableEdgeToEdge Enables edge-to-edge display for the activity. This is the default behavior for
 *           all Android 15 devices (and above) and cannot be changed by this setting. In order to have this behavior
 *           on older OS versions, this setting should be set to `true`. Padding and window insets are
 *           handled automatically. Defaults to `true`.
 * @property deleteCachedAssetsAfterUse Indicates whether cached SDK assets should be deleted after
 *           they are used. If the scanning session is used only once (e.g. for user onboarding), the setting
 *           should be set to `true`. Otherwise, the setting should be set to `true` to avoid assets being re-downloaded.
 *           This only applies if `DOWNLOAD_RESOURCES` variant of the SDK is used. Defaults to `false`.
 *
 */
@Parcelize
data class BlinkIdVerifyActivitySettings @JvmOverloads constructor(
    val blinkIdVerifySdkSettings: BlinkIdVerifySdkSettings,
    val sessionSettings: BlinkIdVerifySessionSettings = BlinkIdVerifySessionSettings(),
    val uxSettings: VerifyUxSettings = VerifyUxSettings(),
    val consentUxConfig: BlinkIdVerifyConsentUxConfig,
    override val cameraSettings: CameraSettings = CameraSettings(),
    override val scanActivityUiColors: ScanActivityColors? = null,
    override val scanActivityUiStrings: SdkStrings = SdkStrings.Default,
    override val scanActivityTypography: ParcelableUiTypography = ParcelableUiTypography.Default(null),
    override val showOnboardingDialog: Boolean = DefaultShowOnboardingDialog,
    override val showHelpButton: Boolean = DefaultShowHelpButton,
    override val enableEdgeToEdge: Boolean = true,
    override val deleteCachedAssetsAfterUse: Boolean = false
) : ScanActivitySettings {
    internal fun saveToIntent(intent: Intent) {
        intent.putExtra(INTENT_EXTRAS_BLINKID_VERIFY_SETTINGS, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    internal companion object {
        internal fun loadFromIntent(intent: Intent): BlinkIdVerifyActivitySettings {
            return intent.extras?.let {
                BundleCompat.getParcelable(
                    it, INTENT_EXTRAS_BLINKID_VERIFY_SETTINGS,
                    BlinkIdVerifyActivitySettings::class.java
                )
            }
                ?: throw java.lang.IllegalStateException("Intent does not contain expected BlinkIdVerifyActivitySettings!")
        }

        private const val INTENT_EXTRAS_BLINKID_VERIFY_SETTINGS = "MbBlinkIdVerifySettings"
    }
}

/**
 * Class containing results of the BlinkID Verify Capture activity.
 *
 * @property status Represents the status of the activity result and shows whether the activity completed
 *                  successfully or what caused it not to complete.
 * @property result Result of the activity containing one or more images captured during the scanning process.
 *                  Result is present only if [status] is [ActivityResultStatus.DocumentCaptured], otherwise it is null.
 *
 */
public data class BlinkIdVerifyCaptureActivityResult(
    val status: ScanActivityResultStatus,
    val result: BlinkIdVerifyCaptureResult?
)