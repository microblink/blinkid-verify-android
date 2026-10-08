/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.microblink.blinkidverify.core.BlinkIdVerifySdk
import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifySessionSettings
import com.microblink.blinkidverify.core.data.model.result.BlinkIdVerifyCaptureResult
import com.microblink.blinkidverify.ux.capture.settings.VerifyUxSettings
import com.microblink.blinkidverify.ux.consent.BlinkIdVerifyConsentUxConfig
import com.microblink.blinkidverify.ux.consent.ConsentScreenContent
import com.microblink.blinkidverify.ux.consent.ConsentUxState
import com.microblink.blinkidverify.ux.consent.MicroblinkConsentScreen
import com.microblink.blinkidverify.ux.theme.BlinkIdVerifySdkTheme
import com.microblink.blinkidverify.ux.theme.VerifyTheme
import com.microblink.blinkidverify.ux.verifyutils.fillErrorDialogs
import com.microblink.blinkidverify.ux.verifyutils.fillHelpScreens
import com.microblink.blinkidverify.ux.verifyutils.onAppMovedToBackground
import com.microblink.blinkidverify.ux.verifyutils.onCameraPermissionCheck
import com.microblink.blinkidverify.ux.verifyutils.onCameraPermissionRequest
import com.microblink.blinkidverify.ux.verifyutils.onCameraPermissionUserResponse
import com.microblink.blinkidverify.ux.verifyutils.onCameraPreviewStarted
import com.microblink.blinkidverify.ux.verifyutils.onCameraPreviewStopped
import com.microblink.blinkidverify.ux.verifyutils.onCloseButtonClicked
import com.microblink.blinkidverify.ux.ScanningUx
import com.microblink.blinkidverify.ux.UiSettings
import com.microblink.blinkidverify.ux.camera.CameraInputDetails
import com.microblink.blinkidverify.ux.camera.CameraSettings
import com.microblink.blinkidverify.ux.camera.compose.CameraInputDetailsCallback
import com.microblink.blinkidverify.ux.camera.compose.CameraPermissionCallbacks
import com.microblink.blinkidverify.ux.camera.compose.CameraPreviewCallbacks
import com.microblink.blinkidverify.ux.camera.compose.CameraScreen
import com.microblink.blinkidverify.ux.state.MbTorchState
import com.microblink.blinkidverify.ux.state.ProcessingState
import com.microblink.blinkidverify.ux.utils.DeviceOrientationListener
import kotlinx.coroutines.launch

private const val TAG = "VerifyCameraScanningScreen"

/**
 * Composable function that provides a complete camera scanning screen using
 * the BlinkID Verify SDK.
 *
 * This composable function sets up and manages the entire camera scanning
 * process, including UI elements, camera interaction, and result handling. It
 * uses the provided [BlinkIdVerifySdk] and [BlinkIdVerifySessionSettings] to
 * configure the scanning session and provides callbacks for handling
 * successful capture and cancellation.
 *
 * @param blinkIdVerifySdk The [BlinkIdVerifySdk] instance used for document verification.
 * @param uiSettings The [UiSettings] used to customize the UI. Defaults to [UiSettings] with default values.
 * @param cameraSettings The [CameraSettings] used for document scanning. Defaults to [CameraSettings] with default values.
 * @param sessionSettings The [BlinkIdVerifySessionSettings] used to configure the capture session. Defaults to [BlinkIdVerifySessionSettings] with default values.
 * @param consentUxConfig The [BlinkIdVerifyConsentUxConfig] that defines how end-user consent for
 *                        cloud processing is obtained.
 * @param onCaptureSuccess A callback function invoked when a document is successfully captured. Receives the [BlinkIdVerifyCaptureResult] as a parameter.
 * @param onCaptureCanceled A callback function invoked when the user cancels the scanning process,
 *                          which also happens when the user declines the consent.
 */
@Composable
fun VerifyCameraScanningScreen(
    blinkIdVerifySdk: BlinkIdVerifySdk,
    uxSettings: VerifyUxSettings = VerifyUxSettings(),
    uiSettings: UiSettings = UiSettings(),
    cameraSettings: CameraSettings = CameraSettings(),
    sessionSettings: BlinkIdVerifySessionSettings = BlinkIdVerifySessionSettings(),
    consentUxConfig: BlinkIdVerifyConsentUxConfig,
    onCaptureSuccess: (BlinkIdVerifyCaptureResult) -> Unit,
    onCaptureCanceled: () -> Unit,
) {
    val applicationContext = LocalContext.current.applicationContext

    val viewModel: BlinkIdVerifyUxViewModel = viewModel(
        factory = BlinkIdVerifyUxViewModel.Factory,
        extras = MutableCreationExtras().apply {
            set(
                BlinkIdVerifyUxViewModel.BLINK_ID_VERIFY_SDK,
                blinkIdVerifySdk
            )
            set(
                BlinkIdVerifyUxViewModel.BLINK_ID_VERIFY_CAPTURE_SETTINGS,
                sessionSettings
            )
            set(
                BlinkIdVerifyUxViewModel.BLINK_ID_VERIFY_UX_SETTINGS,
                uxSettings
            )
            set(
                BlinkIdVerifyUxViewModel.BLINK_ID_VERIFY_CONSENT_UX_CONFIG,
                consentUxConfig
            )
            set(
                BlinkIdVerifyUxViewModel.BLINK_ID_VERIFY_APPLICATION_CONTEXT,
                applicationContext
            )
        }
    )

    DeviceOrientationListener(applicationContext) {
        viewModel.setScreenOrientation(it)
    }

    var initialUiStateSet by rememberSaveable { mutableStateOf(false) }
    if (!initialUiStateSet) {
        viewModel.setInitialUiStateFromUiSettings(uiSettings)
        initialUiStateSet = true
    }

    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        val observer = getDefaultLifecycleObserver(viewModel)
        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
        processLifecycle.addObserver(observer)

        onDispose {
            processLifecycle.removeObserver(observer)
        }
    }

    BlinkIdVerifySdkTheme(uiSettings) {
        val snackbarWarningMessage =
            stringResource(VerifyTheme.sdkStrings.scanningStrings.snackbarFlashlightWarning)
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->

            CameraScreen(
                cameraViewModel = viewModel,
                cameraSettings = cameraSettings,
                cameraPermissionCallbacks = rememberCameraPermissionCallbacks(viewModel),
                cameraPreviewCallbacks = rememberCameraPreviewCallbacks(viewModel),
                cameraInputDetailsCallback = rememberCameraInputDetailsCallback(
                    viewModel,
                    applicationContext
                ),
                onCameraScreenLongPress = { viewModel.changeHelpTooltipVisibility(true) }
            ) {
                val overlayUiState = viewModel.uiState.collectAsStateWithLifecycle()

                if (overlayUiState.value.processingState == ProcessingState.Success) {
                    overlayUiState.value.blinkIdVerifyCaptureResult?.let {
                        onCaptureSuccess(it)
                    }
                }
                BackHandler {
                    onCaptureCanceled()
                }
                ConsentUx(
                    consentUxConfig = consentUxConfig,
                    consentUxState = overlayUiState.value.consentUxState,
                    onConsentAccepted = viewModel::onConsentAccepted,
                    onConsentDeclined = viewModel::onConsentDeclined,
                    onConsentUnavailable = onCaptureCanceled
                )
                ScanningUx(
                    modifier = Modifier.padding(paddingValues),
                    uiState = overlayUiState.value,
                    onExitScanning = {
                        onCloseButtonClicked(viewModel.getSessionNumber())
                        onCaptureCanceled()
                    },
                    uiSettings = uiSettings,
                    helpScreens = fillHelpScreens(),
                    errorStateDialogs = fillErrorDialogs(viewModel::onRetryTimeout, onCaptureCanceled),
                    allowHapticFeedback = uxSettings.allowHapticFeedback,
                    allowScanSound = uxSettings.allowScanSound,
                    showProductionOverlay = !blinkIdVerifySdk.getLicenseToken().licenseRights.allowRemoveProductionOverlay,
                    showDemoOverlay = !blinkIdVerifySdk.getLicenseToken().licenseRights.allowRemoveDemoOverlay,
                    onTorchStateChange = {
                        viewModel.changeTorchState()
                        viewModel.viewModelScope.launch {
                            if (viewModel.uiState.value.torchState == MbTorchState.On) {
                                snackbarHostState.showSnackbar(
                                    snackbarWarningMessage,
                                    null,
                                    false,
                                    SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onFlipDocumentAnimationCompleted = viewModel::onFlipAnimationCompleted,
                    onReticleSuccessAnimationCompleted = viewModel::onReticleSuccessAnimationCompleted,
                    onHapticFeedbackCompleted = viewModel::onHapticFeedbackCompleted,
                    onScanSoundCompleted = viewModel::onScanSoundCompleted,
                    onChangeOnboardingDialogVisibility = viewModel::changeOnboardingDialogVisibility,
                    onHelpScreensDisplayRequested = viewModel::onHelpScreensDisplayRequested,
                    onHelpScreensCloseRequested = viewModel::onHelpScreensCloseRequested,
                    onChangeHelpTooltipVisibility = viewModel::changeHelpTooltipVisibility,
                    helpTooltipHideDelay = uxSettings.helpTooltipHideDelay
                )
            }
        }
    }
}

/**
 * Displays the built-in consent UI while consent is being collected and translates a declined
 * consent into scanning screen cancellation.
 */
@Composable
private fun ConsentUx(
    consentUxConfig: BlinkIdVerifyConsentUxConfig,
    consentUxState: ConsentUxState,
    onConsentAccepted: (consentNote: String) -> Unit,
    onConsentDeclined: () -> Unit,
    onConsentUnavailable: () -> Unit,
) {
    val customConsentNote = (consentUxConfig as? BlinkIdVerifyConsentUxConfig.RequireConsent)?.note
    when (consentUxState) {
        ConsentUxState.ConsentRequired -> MicroblinkConsentScreen(
            onConsentAccepted = onConsentAccepted,
            onConsentDeclined = onConsentDeclined,
            content = ConsentScreenContent(customMessage = customConsentNote),
        )

        ConsentUxState.ConsentDeclined -> LaunchedEffect(consentUxState) {
            onConsentUnavailable()
        }

        ConsentUxState.ConsentGranted,
        ConsentUxState.ConsentNotRequired -> Unit
    }
}

@Composable
private fun rememberCameraPreviewCallbacks(viewModel: BlinkIdVerifyUxViewModel) =
    remember(viewModel) {
        object : CameraPreviewCallbacks {
            override fun onCameraPreviewStarted() {
                onCameraPreviewStarted(viewModel.getSessionNumber())
            }

            override fun onCameraPreviewStopped() {
                onCameraPreviewStopped(viewModel.getSessionNumber())
            }
        }
    }

@Composable
private fun rememberCameraPermissionCallbacks(viewModel: BlinkIdVerifyUxViewModel) =
    remember(viewModel) {
        object : CameraPermissionCallbacks {
            override fun onCameraPermissionCheck() {
                onCameraPermissionCheck(viewModel.getSessionNumber())
            }

            override fun onCameraPermissionRequested() {
                onCameraPermissionRequest(viewModel.getSessionNumber())
            }

            override fun onCameraPermissionUserResponse(cameraPermissionGranted: Boolean) {
                onCameraPermissionUserResponse(
                    viewModel.getSessionNumber(),
                    cameraPermissionGranted
                )
            }

        }
    }

@Composable
private fun rememberCameraInputDetailsCallback(
    viewModel: BlinkIdVerifyUxViewModel,
    applicationContext: Context
) = remember(viewModel) {
    object : CameraInputDetailsCallback {
        override fun onCameraInputDetailsAvailable(cameraInputDetails: CameraInputDetails) {
            viewModel.onCameraInputInfoAvailable(
                applicationContext.applicationContext,
                cameraInputDetails
            )
        }
    }
}

private fun getDefaultLifecycleObserver(viewModel: BlinkIdVerifyUxViewModel): LifecycleObserver {
    val observer = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            onAppMovedToBackground(viewModel.getSessionNumber())
        }

        override fun onPause(owner: LifecycleOwner) {
            viewModel.lifecyclePauseAnalysis()
        }

        override fun onResume(owner: LifecycleOwner) {
            viewModel.lifecycleResumeAnalysis()
        }
    }
    return observer
}