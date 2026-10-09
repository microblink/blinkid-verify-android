package com.microblink.blinkidverify.sample.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.microblink.blinkidverify.core.BlinkIdVerifyClient
import com.microblink.blinkidverify.core.BlinkIdVerifySdk
import com.microblink.blinkidverify.core.BlinkIdVerifySdkSettings
import com.microblink.blinkidverify.core.Response
import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifyScanningSettings
import com.microblink.blinkidverify.core.capture.session.BlinkIdVerifySessionSettings
import com.microblink.blinkidverify.core.capture.session.ImageQualitySettings
import com.microblink.blinkidverify.core.data.model.request.BlinkIdVerifyProcessingRequestOptions
import com.microblink.blinkidverify.core.data.model.request.BlinkIdVerifyProcessingUseCase
import com.microblink.blinkidverify.core.data.model.result.BlinkIdVerifyCaptureResult
import com.microblink.blinkidverify.core.data.model.result.BlinkIdVerifyV3EndpointResponse
import com.microblink.blinkidverify.core.data.model.result.extractionProcessingStatus
import com.microblink.blinkidverify.core.data.model.result.verifyVerdictOrRaw
import com.microblink.blinkidverify.core.settings.BlinkIdVerifyServiceSettings
import com.microblink.blinkidverify.sample.config.BlinkIdVerifyConfig
import com.microblink.blinkidverify.core.session.InputImageSource
import com.microblink.blinkidverify.ux.UiSettings
import com.microblink.blinkidverify.ux.camera.CameraSettings
import com.microblink.blinkidverify.ux.capture.settings.VerifyUxSettings
import com.microblink.blinkidverify.ux.consent.BlinkIdVerifyConsentUxConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "MainViewModel"

@Serializable
data class MainState(
    val blinkidVerifyResult: BlinkIdVerifyV3EndpointResponse? = null,
    val error: String? = null,
)

data class UiState(
    val displayLoading: Boolean = false,
    val captureResult: BlinkIdVerifyCaptureResult? = null,
)

class MainViewModel : ViewModel() {
    private val _mainState = MutableStateFlow(MainState())
    var mainState = _mainState.asStateFlow()

    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState())
    var uiState = _uiState.asStateFlow()

    val blinkIDVerifyRequestOptionsConfig = BlinkIdVerifyProcessingRequestOptions()

    val blinkIDVerifyRequestUseCase = BlinkIdVerifyProcessingUseCase()

    // TODO use settings options
    val blinkIDVerifyUiSettings = UiSettings()

    // TODO use camera settings
    val cameraSettings = CameraSettings()

    var stepTimeoutDuration: MutableState<Duration> = mutableStateOf(10000.milliseconds)
        private set

    val blinkIDVerifyUxSettings = VerifyUxSettings(
        stepTimeoutDuration = stepTimeoutDuration.value
    )

    val consentUxConfig = BlinkIdVerifyConsentUxConfig.RequireConsent(
        userId = "sample-user-id",
        durationDays = 365,
    )

    var localSdk: BlinkIdVerifySdk? = null
        private set

    val sessionSettings = BlinkIdVerifySessionSettings(
        inputImageSource = InputImageSource.Video,
        scanningSettings = BlinkIdVerifyScanningSettings(
            treatExpirationAsFraud = blinkIDVerifyRequestOptionsConfig.treatExpirationAsFraud,
            screenAnalysisMatchLevel = blinkIDVerifyRequestOptionsConfig.screenMatchLevel,
            barcodeAnomalyMatchLevel = blinkIDVerifyRequestOptionsConfig.barcodeAnomalyMatchLevel,
            staticSecurityFeaturesMatchLevel = blinkIDVerifyRequestOptionsConfig.staticSecurityFeaturesMatchLevel,
            dataMatchMatchLevel = blinkIDVerifyRequestOptionsConfig.dataMatchMatchLevel,
            photocopyMatchLevel = blinkIDVerifyRequestOptionsConfig.photocopyMatchLevel,
            photoForgeryMatchLevel = blinkIDVerifyRequestOptionsConfig.photoForgeryMatchLevel,
            generativeAiMatchLevel = blinkIDVerifyRequestOptionsConfig.generativeAiMatchLevel,
            returnFullDocumentImage = blinkIDVerifyRequestOptionsConfig.returnFullDocumentImage,
            returnFaceImage = blinkIDVerifyRequestOptionsConfig.returnFaceImage,
            returnSignatureImage = blinkIDVerifyRequestOptionsConfig.returnSignatureImage,
            redactionMode = blinkIDVerifyRequestOptionsConfig.redactionMode,
            imageQualitySettings = ImageQualitySettings(
                blurMatchLevel = blinkIDVerifyRequestOptionsConfig.blurMatchLevel,
                glareMatchLevel = blinkIDVerifyRequestOptionsConfig.glareMatchLevel,
                lightingMatchLevel = blinkIDVerifyRequestOptionsConfig.lightingMatchLevel,
                sharpnessMatchLevel = blinkIDVerifyRequestOptionsConfig.sharpnessMatchLevel,
                handOcclusionMatchLevel = blinkIDVerifyRequestOptionsConfig.handOcclusionMatchLevel,
                dpiMatchLevel = blinkIDVerifyRequestOptionsConfig.dpiMatchLevel,
                tiltMatchLevel = blinkIDVerifyRequestOptionsConfig.tiltMatchLevel,
                imageQualityInterpretation = blinkIDVerifyRequestOptionsConfig.imageQualityInterpretation
            ),
            useCase = blinkIDVerifyRequestUseCase
        ),
    )

    fun sendVerifyRequestsFromCaptureResult(captureResult: BlinkIdVerifyCaptureResult) {
        _uiState.update {
            it.copy(displayLoading = true)
        }
        viewModelScope.launch {
            invokeServerProcessing(captureResult)
        }
    }

    private suspend fun invokeServerProcessing(captureResult: BlinkIdVerifyCaptureResult) {
        _uiState.update {
            it.copy(displayLoading = true)
        }
        val payload = captureResult.serializedVerifyPayload
        if (payload == null) {
            Log.e(
                TAG,
                "Native verify payload missing from capture result. " +
                    "Check logcat for getResult / NativeSerializedVerifyPayload errors.",
            )
            _mainState.update {
                it.copy(error = "Native verify payload missing from capture result")
            }
            _uiState.update { it.copy(displayLoading = false) }
            return
        }

        val client = BlinkIdVerifyClient(
            BlinkIdVerifyServiceSettings(
                verificationServiceBaseUrl = BlinkIdVerifyConfig.verificationServiceBaseUrl,
                token = BlinkIdVerifyConfig.verificationServiceToken,
            )
        )
        when (val response = client.verify(payload)) {
            is Response.Error -> {
                Log.w(TAG, "Response is error: ${response.errorReason.name}")
                response.exception?.printStackTrace()
                _mainState.update {
                    it.copy(error = response.errorReason.name)
                }
            }

            is Response.Success -> {
                val endpoint = response.endpointResponse
                Log.i(
                    TAG,
                    "Verify complete: verdict=${endpoint.verification.verifyVerdictOrRaw()}, " +
                        "extractionStatus=${endpoint.extractionProcessingStatus()}, " +
                        "pipeline=${endpoint.pipeline.extraction?.status}/" +
                        "${endpoint.pipeline.verification?.status}",
                )
                _mainState.update {
                    it.copy(blinkidVerifyResult = endpoint)
                }
            }
        }
        _uiState.update { it.copy(displayLoading = false) }
    }

    suspend fun initializeLocalSdk(context: Context) {
        _uiState.update {
            it.copy(displayLoading = true)
        }
        val maybeInstance = BlinkIdVerifySdk.initializeSdk(
            context = context,
            BlinkIdVerifySdkSettings(
                licenseKey = BlinkIdVerifyConfig.licenseKey,
            )
        )
        when {
            maybeInstance.isSuccess -> {
                localSdk = maybeInstance.getOrNull()
            }

            maybeInstance.isFailure -> {
                val exception = maybeInstance.exceptionOrNull()
                Log.e(TAG, "Initialization failed", exception)
                _mainState.update {
                    it.copy(error = "Initialization failed: ${exception?.message}")
                }
            }
        }
        _uiState.update {
            it.copy(displayLoading = false)
        }
    }

    fun onCaptureResultAvailable(captureResult: BlinkIdVerifyCaptureResult) {
        _uiState.update {
            it.copy(
                captureResult = captureResult
            )
        }
        // unload the SDK when not needed anymore to free up resources
        unloadSdk()
    }

    fun onCaptureCanceled() {
        unloadSdk()
    }

    fun resetState() {
        _mainState.update { MainState() }
        _uiState.update { UiState() }
    }

    private fun unloadSdk() {
        val sdkToClose = localSdk
        localSdk = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sdkToClose?.close()
            } catch (_: Exception) {
                Log.w(TAG, "SDK is already closed")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unloadSdk()
    }
}