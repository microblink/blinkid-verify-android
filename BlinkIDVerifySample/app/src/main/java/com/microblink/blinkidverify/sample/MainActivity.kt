package com.microblink.blinkidverify.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.microblink.blinkidverify.core.data.model.result.BlinkIdVerifyV3EndpointResponse
import com.microblink.blinkidverify.sample.ui.MainScreen
import com.microblink.blinkidverify.sample.ui.navigation.BlinkIDVerifyCustomNavType
import com.microblink.blinkidverify.sample.ui.result.VerifySampleResultScreen
import com.microblink.blinkidverify.sample.ui.theme.BlinkIDVerifySampleTheme
import com.microblink.blinkidverify.sample.viewmodels.MainViewModel
import com.microblink.blinkidverify.sample.ui.navigation.Destination
import com.microblink.blinkidverify.ux.VerifyCameraScanningScreen
import kotlinx.coroutines.launch
import kotlin.reflect.typeOf

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlinkIDVerifySampleTheme {
                val navController = rememberNavController()
                MainNavHost(navController = navController)
                val mainState by viewModel.mainState.collectAsStateWithLifecycle()
                mainState.blinkidVerifyResult?.let { verifyResult ->
                    navController.navigate(Destination.VerifyResult(verifyResult))
                }
            }
        }
    }

    @Composable
    fun MainNavHost(
        modifier: Modifier = Modifier,
        navController: NavHostController = rememberNavController(),
        startDestination: Destination = Destination.Main,
    ) {
        val context = LocalContext.current
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = startDestination
        ) {
            composable<Destination.Main> {
                val mainState by viewModel.mainState.collectAsStateWithLifecycle()

                mainState.error?.let {
                    Toast.makeText(this@MainActivity, "Error: $it", Toast.LENGTH_LONG).show()
                    viewModel.resetState()
                }

                MainScreen(
                    viewModel,
                    launchCameraCapture = {
                        viewModel.viewModelScope.launch {
                            viewModel.initializeLocalSdk(context)
                            navController.navigate(Destination.DocumentCapture)
                        }
                    }
                )
            }
            composable<Destination.DocumentCapture> {
                // It is important that camera scanning screen is a separate route to enable
                // proper resource release when scanning is done (navigating back to main screen)
                // - this ensures that ViewModel.onCleared will be called.
                val sdk = viewModel.localSdk
                if (sdk != null) {
                    VerifyCameraScanningScreen(
                        sdk,
                        uxSettings = viewModel.blinkIDVerifyUxSettings,
                        uiSettings = viewModel.blinkIDVerifyUiSettings,
                        cameraSettings = viewModel.cameraSettings,
                        sessionSettings = viewModel.sessionSettings,
                        consentUxConfig = viewModel.consentUxConfig,
                        onCaptureSuccess = { result ->
                            viewModel.onCaptureResultAvailable(result)
                            navController.popBackStack(
                                route = Destination.Main,
                                inclusive = false
                            )
                        },
                        onCaptureCanceled = {
                            navController.popBackStack(
                                route = Destination.Main,
                                inclusive = false
                            )
                            viewModel.onCaptureCanceled()
                        }
                    )
                } else {
                    navController.popBackStack(
                        route = Destination.Main,
                        inclusive = false
                    )
                }
            }
            composable<Destination.VerifyResult>(
                typeMap = mapOf(
                    typeOf<BlinkIdVerifyV3EndpointResponse>() to BlinkIDVerifyCustomNavType.BlinkIDVerifyResultType
                )
            ) { backStackEntry ->
                VerifySampleResultScreen(
                    result = backStackEntry.toRoute<Destination.VerifyResult>().documentVerificationResult,
                    onNavigateUp = {
                        viewModel.resetState()
                        navController.popBackStack(
                            route = Destination.Main,
                            inclusive = false
                        )
                    }
                )
            }
        }
    }
}