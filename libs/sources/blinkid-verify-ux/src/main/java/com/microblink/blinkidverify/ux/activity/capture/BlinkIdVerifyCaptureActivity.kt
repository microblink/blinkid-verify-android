/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.activity.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.microblink.blinkidverify.ux.VerifyCameraScanningScreen
import com.microblink.blinkidverify.ux.result.contract.BlinkIdVerifyActivitySettings
import com.microblink.blinkidverify.ux.result.contract.BlinkIdVerifyCaptureResultHolder
import com.microblink.blinkidverify.ux.result.contract.MbBlinkIdVerifyCapture
import com.microblink.blinkidverify.ux.theme.BlinkIdVerifySdkTheme
import com.microblink.blinkidverify.core.ping.util.PingletTracker
import com.microblink.blinkidverify.ux.components.LoadingScreen
import com.microblink.blinkidverify.ux.contract.CancelReason
import com.microblink.blinkidverify.ux.createUiSettings
import kotlinx.coroutines.launch

internal class BlinkIdVerifyCaptureActivity : AppCompatActivity() {

    private val activityViewModel: BlinkIdVerifyCaptureActivityViewModel by viewModels()
    private lateinit var verifyActivitySettings: BlinkIdVerifyActivitySettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        verifyActivitySettings = BlinkIdVerifyActivitySettings.loadFromIntent(intent)
        if (verifyActivitySettings.enableEdgeToEdge) enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onCancel(CancelReason.UserRequested)
            }
        })

        activityViewModel.viewModelScope.launch {
            activityViewModel.initializeLocalSdk(
                context = this@BlinkIdVerifyCaptureActivity,
                blinkIdVerifySdkSettings = verifyActivitySettings.blinkIdVerifySdkSettings,
                onInitFailed = {
                    onCancel(CancelReason.ErrorSdkInit)
                }
            )
        }

        PingletTracker.Log.trackInfo(
            context = this,
            logMessage = "Using BlinkIdVerifyCaptureActivity"
        )

        setContent {

            val verifyUiSettings = createUiSettings(verifyActivitySettings)

            BlinkIdVerifySdkTheme(verifyUiSettings) {
                val displayLoading = activityViewModel.displayLoading.collectAsStateWithLifecycle()
                if (displayLoading.value) {
                    LoadingScreen()
                } else {
                    activityViewModel.localSdk?.let {
                        VerifyCameraScanningScreen(
                            blinkIdVerifySdk = it,
                            uxSettings = verifyActivitySettings.uxSettings,
                            uiSettings = verifyUiSettings,
                            cameraSettings = verifyActivitySettings.cameraSettings,
                            sessionSettings = verifyActivitySettings.sessionSettings,
                            consentUxConfig = verifyActivitySettings.consentUxConfig,
                            onCaptureSuccess = { result ->
                                BlinkIdVerifyCaptureResultHolder.blinkIdVerifyCaptureResult = result
                                this.setResult(RESULT_OK)
                                this.finish()
                            },
                            onCaptureCanceled = {
                                onCancel(CancelReason.UserRequested)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (verifyActivitySettings.deleteCachedAssetsAfterUse) {
            activityViewModel.unloadSdkAndDeleteCachedAssets()
        } else {
            activityViewModel.unloadSdk()
        }
        super.onDestroy()
    }

    fun onCancel(cancelReason: CancelReason) {
        val extras = Intent()
        extras.putExtra(MbBlinkIdVerifyCapture.EXTRA_CANCEL_REASON, cancelReason)
        this.setResult(RESULT_CANCELED, extras)
        this.finish()
    }

}