<p align="center" >
  <img src="https://raw.githubusercontent.com/wiki/microblink/blinkid-android/images/logo-microblink.png" alt="Microblink" title="Microblink">
</p>

# BlinkID Verify SDK for Android

The _BlinkID Verify_ Android SDK is a comprehensive solution for implementing secure document scanning and verification on Android. It offers powerful capabilities for capturing, analyzing, and verifying a wide range of identification documents.

The list of all supported documents and result fields can be found [here](#supported-docs).


# Table of contents
* [Quick Start](#quick-start)
  * [Quick start with the sample app](#quick-sample)
  * [SDK integration](#sdk-integration)
* [Device requirements](#device-requirements)
  * [Android version](#android-version-req)
  * [Camera](#camera-req)
  * [Processor architecture](#processor-arch-req)
* [Pre-bundling the SDK resources in your app](#pre-bundling-resources)
* [Customizing the look and UX](#customizing-the-look)
  * [Simple customizations](#simple-customizations)
  * [Advanced customizations](#advanced-customizations)
* [Changing default strings and localization](#changing-strings-and-localization)
  * [Defining your own string resources for UI elements](#using-own-string-resources)
* [Using SDK through `BlinkIdVerifyCaptureActivity`](#using-capture-activity)
* [Completely custom UX (advanced)](#low-level-api)
  * [The `BlinkIdVerifySdk` and `BlinkIdVerifyScanningSession`](#core-api-sdk-and-session)
* [Troubleshooting](#troubleshoot)
* [Additional info](#additional-info)
  * [BlinkID Verify SDK size](#sdk-size)
  * [Supported documents](#supported-docs)
  * [API documentation](#api-documentation)
  * [Contact](#contact)


# <a name="quick-start"></a> Quick Start

## <a name="quick-sample"></a> Quick start with the sample app

1. Open Android Studio.
2. In the `Quick Start` dialog, choose _Open project_.
3. In the file dialog, select the **`BlinkIDVerifySample`** folder in this repository (not the repo root).
4. Wait for Gradle sync to finish. If Android Studio asks to reload the project, select `Yes`.
5. Before running, set your credentials in [`BlinkIdVerifyConfig`](BlinkIDVerifySample/lib-common/src/main/java/com/microblink/blinkidverify/sample/config/Config.kt):
   - `licenseKey` — trial or production license for your app ID (`com.microblink.blinkidverify.sample` for the sample).
   - `verificationServiceBaseUrl` — Verify Cloud **v3** base URL (default: `https://us-east.verify.microblink.com/api/v3`).
   - `verificationServiceToken` — Basic auth token for Verify Cloud.

The **`app`** module demonstrates capture with `VerifyCameraScanningScreen`, submits the native v3 multipart payload to Verify Cloud, and shows a `BlinkIdVerifyV3EndpointResponse` result screen.

The sample consumes **`blinkid-verify-ux`** from Maven Central (see `BlinkIDVerifySample/gradle/libs.versions.toml`). An optional local `:blinkid-verify-ux` module (UX sources under `libs/sources/`) is included for advanced UX customization; switch dependencies in `app/build.gradle.kts` if you want to build UX from source instead of Maven.


## <a name="sdk-integration"></a> SDK integration

### Adding _BlinkID Verify_ SDK dependency

The `BlinkID Verify` library is available on Maven Central repository.

In your project root, add `mavenCentral()` repository to the repositories list, if not already present:

```
repositories {
    // ... other repositories
    mavenCentral()
}
```

Add _BlinkID Verify_ as a dependency in module level `build.gradle(.kts)`:

```
dependencies {
    implementation("com.microblink:blinkid-verify-ux:4000.0.0")
}
```

### Launching the document capture session and obtaining the results

1. A valid license key is required to initialize the document capture process. You can request a free trial license key, after you register, at [Microblink Developer Hub](https://developer.microblink.com/). The license is bound to the [application ID](https://developer.android.com/studio/build/configure-app-module#set-application-id) of your app, so please ensure you enter the correct application ID when asked.


2. You first need to initialize the SDK and obtain the `BlinkIdVerifySdk` instance:
```kotlin
val maybeInstance = BlinkIdVerifySdk.initializeSdk(
    context = context,
    BlinkIdVerifySdkSettings(
        licenseKey = "your_license_key",
    )
)
when {
    maybeInstance.isSuccess -> {
        val sdkInstance = maybeInstance.getOrNull()
        // use the SDK instance
    }

    maybeInstance.isFailure -> {
        val exception = maybeInstance.exceptionOrNull()
        Log.e(TAG, "Initialization failed", exception)
    }
}
```
`BlinkIdVerifySdk.initializeSdk` is a suspend function that should be called from a coroutine.

3. Use `VerifyCameraScanningScreen` composable to launch document capture UX and obtain results:
```kotlin
VerifyCameraScanningScreen(
    sdkInstance,
    uxSettings = VerifyUxSettings(),
    uiSettings = UiSettings(),
    cameraSettings = CameraSettings(),
    sessionSettings = BlinkIdVerifySessionSettings(),
    consentUxConfig = BlinkIdVerifyConsentUxConfig.NoConsentNeeded,
    onCaptureSuccess = { captureResult ->
        // captureResult is BlinkIdVerifyCaptureResult
    },
    onCaptureCanceled = {
        // user canceled the capture
    }
)
```

Configure verification-related options on `BlinkIdVerifySessionSettings.scanningSettings` (match levels, return images, redaction, use case, and so on). Those settings are embedded in `captureResult.serializedVerifyPayload` for Verify Cloud v3.

### Document capture session result

After the document capture session is finished, the SDK returns an object of type [BlinkIdVerifyCaptureResult](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core.data.model.result/-blink-id-verify-capture-result/index.html).
The object contains images of the front and back sides of the document. Additionally, if the barcode is present on the document, the camera frame containing a visible barcode will also be available.

After capture, use the native multipart payload on [BlinkIdVerifyCaptureResult](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core.data.model.result/-blink-id-verify-capture-result/index.html) (`serializedVerifyPayload`). It is built from the same [BlinkIdVerifySessionSettings](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core.capture.session/-blink-id-verify-session-settings/index.html) used during scanning, so on-device and cloud configuration stay aligned.

### Launching the document verification API call and obtaining the results

1. Read the v3 payload from the capture result:
```kotlin
val payload = captureResult.serializedVerifyPayload
    ?: error("Native verify payload missing from capture result")
```

2. Create a [BlinkIdVerifyClient](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core/-blink-id-verify-client/index.html) for Verify Cloud (v3) with your API token:
```kotlin
val client = BlinkIdVerifyClient(
    BlinkIdVerifyServiceSettings(
        // if using self-hosted solution, set appropriate base URL
        verificationServiceBaseUrl = "https://us-east.verify.microblink.com/api/v3",
        token = "your_API_token",
    )
)
```
If you don't already have the API token, contact us at [help.microblink.com](https://help.microblink.com/).

3. Submit the payload with `client.verify(payload)`:
```kotlin
CoroutineScope(Dispatchers.IO).launch {
    when (val response = client.verify(payload)) {
        is Response.Error -> {
            // check `response.errorReason` to check why the request failed
        }
        is Response.Success -> {
            // check `response.endpointResponse` for the v3 result
        }
    }
}
```

> **Note:** `BlinkIdVerifyClient.verify(BlinkIdVerifyRequest)` (legacy v2 JSON `/docver`) and [BlinkIdVerifyEndpointResponse](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core.data.model.result/-blink-id-verify-endpoint-response/index.html) are deprecated. New integrations must use the v3 multipart flow above.

### Document verification results

The final result from Verify Cloud v3 is of type [BlinkIdVerifyV3EndpointResponse](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core.data.model.result/-blink-id-verify-v3-endpoint-response/index.html), and it contains pipeline status, verification verdict, extraction JSON, and related metadata. Helper extensions such as `verifyVerdictOrRaw()` and `extractionProcessingStatus()` are available on this type.


# <a name="device-requirements"></a> Device requirements

## <a name="android-version-req"></a> Android version

_BlinkID Verify_ SDK requires Android API level **24** or newer.

## <a name="camera-req"></a> Camera

To perform successful scans, the camera preview resolution must be at least **1080p**. Note that the camera preview resolution is not the same as the video recording resolution.

## <a name="processor-arch-req"></a> Processor architecture

_BlinkID Verify_ SDK is distributed with **ARMv7** and **ARM64** native library binaries.

_BlinkID Verify_ is a native library written in C++ and available for multiple platforms. Because of this, _BlinkID Verify_ cannot work on devices with obscure hardware architectures. We have compiled the SDK's native code only for the most popular Android [ABIs](https://en.wikipedia.org/wiki/Application_binary_interface).

If you are combining _BlinkID Verify_ library with other libraries that contain native code in your application, make sure to match the architectures of all native libraries. For example, if the third-party library has only ARMv7 version, you must use exactly ARMv7 version of _BlinkID Verify_ with that library, but not ARM64. Using different architectures will crash your app at the initialization step because JVM will try to load all its native dependencies in the same preferred architecture and fail with `UnsatisfiedLinkError`.

To avoid this issue and ensure that only architectures supported by the _BlinkID Verify_ library are packaged in the final application, add the following statement to your `android/defaultConfig` block inside `build.gradle.kts`:

```
android {
    ...
    defaultConfig {
        ...
        ndk {
            // Tells Gradle to package the following ABIs into your application
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }
}
```

# <a name="pre-bundling-resources"></a> Pre-bundling the SDK resources into your app

If you want to reduce the SDK startup time and network traffic, you have the option to pre-bundle the SDK resources as assets into your application. All required resources are located in [libs/resources/assets/microblink/blinkidverify](https://github.com/microblink/blinkid-verify-android/tree/main/libs/resources/assets/microblink/blinkidverify) folder. You can bundle it into your application by including the mentioned folder in your application's assets. Copy mentioned `libs/resources/assets/microblink` directory to `src/main/assets` folder of your application module (or appropriate folder for desired app flavor).

Use `BlinkIdVerifySdkSettings` to set the following options when instantiating the SDK:

```kotlin
BlinkIdVerifySdk.initializeSdk(
    context = context,
    BlinkIdVerifySdkSettings(
        licenseKey = "your_license_key",
        // disable resource download when assets are pre-bundled
        downloadResources = false,
        // define path if you are not using the default: "microblink/blinkidverify"
        // resourceLocalFolder = "path_within_app_assets"
    )
)
```

# <a name="customizing-the-look"></a> Customizing the look and the UX

## <a name="simple-customizations"></a> Simple customizations

You can use basic customization options in our default `VerifyCameraScanningScreen` composable:

```kotlin
VerifyCameraScanningScreen(
    sdkInstance,
    uxSettings = VerifyUxSettings(),
    // ui settings options
    uiSettings = UiSettings(
        typography = yourTypography,
        colorScheme = yourColorScheme,
        uiColors = yourUiColors,
        sdkStrings = yourSdkStrings,
        showOnboardingDialog = true, // or false
        showHelpButton = true // or false
    ),
    cameraSettings = CameraSettings(),
    sessionSettings = BlinkIdVerifySessionSettings(),
    consentUxConfig = BlinkIdVerifyConsentUxConfig.NoConsentNeeded,
    onCaptureSuccess = { captureResult ->
        // result is BlinkIdVerifyCaptureResult
    },
    onCaptureCanceled = {
        // user canceled the capture
    }
)
```

For a complete reference on available customization options, see [UiSettings](https://microblink.github.io/blinkid-verify-android/microblink-ux/com.microblink.ux/-ui-settings/index.html) API docs.

## <a name="advanced-customizations"></a> Advanced customizations

### Implementing scanning Composable

It is possible to use completely custom UI elements by implementing your own Composable.

Create your implementation of scanning ViewModel (which must be a subclass of our `CameraViewModel`) to handle UX events that come from our SDK:

```kotlin
class YourBlinkIdVerifyScanningUxViewModel(
    blinkIdVerifySdkInstance: BlinkIdVerifySdk,
    sessionSettings: BlinkIdVerifySessionSettings
) : CameraViewModel() {

    val imageAnalyzer = BlinkIdVerifyAnalyzer(
        verifySdk = blinkIdVerifySdkInstance,
        sessionSettings = sessionSettings,
        verifyScanningDoneHandler = object : VerifyScanningDoneHandler {
            override fun onScanningFinished(result: BlinkIdVerifyCaptureResult) {
                // TODO use capture result
            }

            override fun onScanningCanceled() {
                // user canceled the scanning
            }

            override fun onError(error: ErrorReason) {
                // handle error
            }
        },
        uxEventHandler = object : ScanningUxEventHandler {
            override fun onUxEvents(events: List<ScanningUxEvent>) {
                // handle scanning UX events to update UI state
                for (event in events) {
                    when (event) {
                        is ScanningUxEvent.ScanningDone -> {
                            // TODO
                        }

                        is ScanningUxEvent.DocumentNotFound -> {
                            // TODO
                        }

                        is ScanningUxEvent.DocumentNotFullyVisible -> {
                            // TODO
                        }

                        is ScanningUxEvent.DocumentTooClose -> {
                            // TODO
                        }

                        is VerifyDocumentImageAnalysisResult -> {
                            // TODO
                        }

                        is BlinkIdVerifyDocumentLocatedLocation -> {
                            // TODO
                        }
                        // TODO ... handle other events, `when` must be exhaustive, omitted for brevity
                    }
                }
            }
        }
    )

    override fun analyzeImage(image: ImageProxy) {
        // image has to be closed after processing
        image.use {
            imageAnalyzer?.analyze(it)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // cancel and close image analyzer when view model is cleared
        imageAnalyzer.cancel()
        imageAnalyzer.close()
    }
}
```

Implement your camera scanning screen Composable by using our `CameraScreen` Composable which is responsible for camera management:

```kotlin
@Composable
fun YourVerifyCameraScanningScreen(
    viewModel: YourBlinkIdVerifyScanningUxViewModel
    //... other required parameters for your UI
) {
    // ...
    CameraScreen(
        cameraViewModel = viewModel,
    ) {
        // TODO your camera overlay Compose content
    }

}
``` 

### Modifying our ux libraries source code

For larger control over the UX, you can use the open-source `blinkid-verify-ux` and `microblink-ux` libraries and perform certain modifications. **Only the source files that specifically allow for modification by the license header** can be modified.

To do so, you can include the source code of our library directly in your application.
They are located in `libs/sources/blinkid-verify-ux` and `libs/sources/microblink-ux` modules.

**Please keep in mind that we will regularly make changes and update the source code with each release.**

# <a name="changing-strings-and-localization"></a> Changing default strings and localization

You can modify strings and add another language. For more information on how localization works in Android, check out the [official Android documentation](https://developer.android.com/guide/topics/resources/localization).

## <a name="using-own-string-resources"></a> Defining your own string resources for UI elements

You can define string resources that will be used instead of predefined ones by using the custom [SdkStrings](https://microblink.github.io/blinkid-verify-android/microblink-ux/com.microblink.ux.theme/-sdk-strings/index.html) while creating the `UiSettings`.

## <a name="using-capture-activity"></a> Using SDK through `BlinkIdVerifyCaptureActivity`

The simplest way of using BlinkID SDK is through our integrated activity.
This eliminates the need for Compose integration and allows for quick and easy access to results. By using this integration method, customization is reduced, although most UI elements can still be customized.

Activity is accessed through `rememberLauncherForActivityResult` by using [MbBlinkIdVerifyCapture](https://microblink.github.io/blinkid-verify-android/blinkid-verify-ux/com.microblink.blinkidverify.ux.result.contract/-mb-blink-id-verify-capture/index.html) contract.
```kotlin
    val captureLauncher = rememberLauncherForActivityResult(
        contract = MbBlinkIdVerifyCapture(),
        onResult = { captureResult ->
            if (captureResult.status == ScanActivityResultStatus.Scanned) {
                // use captureResult.result (BlinkIdVerifyCaptureResult)
            }
        }
    )
```
When launching the contract, [BlinkIdVerifyActivitySettings](https://microblink.github.io/blinkid-verify-android/blinkid-verify-ux/com.microblink.blinkidverify.ux.result.contract/-blink-id-verify-activity-settings/index.html) need to be defined. These settings include basic SDK information such as license key and additional settings for customizing the capture experience.
```kotlin
    captureLauncher.launch(
        BlinkIdVerifyActivitySettings(
            BlinkIdVerifySdkSettings(
                licenseKey = "<your_license_key>"
            ), 
            // define additional settings here
        )
    )
```
[BlinkIdVerifyActivitySettings](https://microblink.github.io/blinkid-verify-android/blinkid-verify-ux/com.microblink.blinkidverify.ux.result.contract/-blink-id-verify-activity-settings/index.html) contain the following:
```kotlin
    data class BlinkIdVerifyActivitySettings(
        val blinkIdVerifySdkSettings: BlinkIdVerifySdkSettings,
        val sessionSettings: BlinkIdVerifySessionSettings = BlinkIdVerifySessionSettings(),
        val uxSettings: VerifyUxSettings = VerifyUxSettings(),
        val cameraSettings: CameraSettings = CameraSettings(),
        val scanActivityUiColors: ScanActivityColors? = null,
        val scanActivityUiStrings: SdkStrings = SdkStrings.Default,
        val scanActivityTypography: ParcelableUiTypography = ParcelableUiTypography.Default(null),
        val showOnboardingDialog: Boolean = DefaultShowOnboardingDialog,
        val showHelpButton: Boolean = DefaultShowHelpButton,
        val enableEdgeToEdge: Boolean = true,
        val deleteCachedAssetsAfterUse: Boolean = false
    )
```
Most customizations regarding the UI are handled in the same way as with the Composable component.
The main difference can be found in how `Typography` is set.

Customizing SDK `Typography` is still available through `scanActivityTypography` which is [ParcelableUiTypography](https://microblink.github.io/blinkid-verify-android/microblink-ux/com.microblink.ux.utils/-parcelable-ui-typography/index.html) type. This class offers only the most important `TextStyle` and `Font` parameters.

While `Colors` are fully customizable, the client needs to make sure that `Dark` and `Light` themes follow the current system state. In the Compose implementation, this is handled directly by the SDK.

# <a name="low-level-api"></a> Completely custom UX (advanced)

When using the low-level API, you are responsible for preparing the input image stream (or static images) for analysis as well as building a completely custom UX from scratch based on the image-by-image feedback from the SDK.

Low-level API gives you more flexibility with the cost of a significantly larger integration effort. For example, if you need a camera, you will be responsible for camera management and displaying real-time user guidance.

### Adding _BlinkID Verify_ Core SDK dependency for low-level API

For low-level API integration, only _BlinkID Verify_ SDK core library: **blinkid-verify-core** is needed.
Both `blinkid-verify-ux` and `microblink-ux` are not needed.

In your project root, add `mavenCentral()` repository to the repositories list, if not already present:

```
repositories {
    // ... other repositories
    mavenCentral()
}
```

Add _blinkid-verify-core_ library as a dependency in module level `build.gradle(.kts)`:

```
dependencies {
    implementation("com.microblink:blinkid-verify-core:4000.0.0")
}
```

## <a name="core-api-sdk-and-session"></a> The `BlinkIdVerifySdk` and `BlinkIdVerifyScanningSession`

[BlinkIdVerifySdk](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core/-blink-id-verify-sdk/index.html) is a singleton that is the main entry point to the _BlinkID Verify_ SDK. It manages the global state of the SDK. This involves managing the main processing, unlocking the SDK, ensuring that the license check is up-to-date, downloading resources, and performing all necessary synchronization for the processing operations.

Once you obtain an instance of the `BlinkIdVerifySdk` class after SDK initialization is completed, you can use it to start a document scanning session.

[BlinkIdVerifyScanningSession](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core.capture.session/-blink-id-verify-scanning-session/index.html) is the main object that accepts images and camera frames, processes them, and returns frame-by-frame results and the final result when it becomes available.

### <a name="analyzing-image-stream"></a> Analyzing the stream of images

1. First initialize the SDK to obtain `BlinkIdVerifySdk` instance by calling `BlinkIdVerifySdk.initializeSdk` suspend function from a Coroutine:
```kotlin
val maybeInstance = BlinkIdVerifySdk.initializeSdk(
    context = context,
    BlinkIdVerifySdkSettings(
        licenseKey = "your_license_key",
    )
)
when {
    maybeInstance.isSuccess -> {
        val sdkInstance = maybeInstance.getOrNull()
        // use the SDK instance
    }

    maybeInstance.isFailure -> {
        val exception = maybeInstance.exceptionOrNull()
        Log.e(TAG, "Initialization failed", exception)
    }
}
```
2. Create `BlinkIdVerifyScanningSession` by calling the suspend function `BlinkIdVerifySdk.createScanningSession(BlinkIdVerifySessionSettings)`:
```kotlin
val scanningSession = blinkIdVerifySdk.createScanningSession(BlinkIdVerifySessionSettings(
    // use InputImageSource.Video to analyze a stream of images; if you have
    // a few images (e.g. from gallery) use InputImageSource.Photo
    inputImageSource = InputImageSource.Video,
    // update other options if required
))
```

3. To process each image (camera frame), call the suspend function `BlinkIdVerifyScanningSession.process(InputImage): Result<BlinkIdVerifyProcessResult>`:
```kotlin
val processResult = scanningSession.process(inputImage)
```

There are helper methods for creating [InputImage](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.core.image/-input-image/index.html) from `android.media.Image`, `androidx.camera.core.ImageProxy`, and standard Android Bitmap.

Processing of a single frame returns [BlinkIdVerifyProcessResult](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core.capture.session/-blink-id-verify-process-result/index.html) (wrapped in a `Result`) which contains:

- Detailed analysis of the frame, including various detection statuses and potential issues that should be used for frame-by-frame UX updates.
- Completeness status of the overall process.

You should keep calling the process function until the result completeness indicates that the result is complete, but you could have custom logic for cancellation and timeouts.

### <a name="core-api-obtaining-results"></a> Obtaining capture results

If after analysis of some image the completeness status of `BlinkIdVerifyProcessResult` indicates that document capture is complete, only then should you get the final result from the `BlinkIdVerifyScanningSession`:

```kotlin
if (processResult.resultCompleteness.isComplete()) {
    val captureResult = session.getResult()
    // do something with the final result
}
```

You will get [BlinkIdVerifyCaptureResult](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core.data.model.result/-blink-id-verify-capture-result/index.html) with document images and `serializedVerifyPayload` for Verify Cloud v3. Submit that payload with [BlinkIdVerifyClient](https://microblink.github.io/blinkid-verify-android/blinkid-verify-core/com.microblink.blinkidverify.core/-blink-id-verify-client/index.html) as described in [Launching the document verification API call](#launching-the-document-verification-api-call-and-obtaining-the-results).

**After scanning is completed, it is important to terminate the scanning session**

To terminate the scanning session, ensure that `BlinkIdVerifyScanningSession.close()` is called.

**If you are finished with the SDK processing, terminate the SDK to free up resources** by invoking `BlinkIdVerifySdk.closeAndDeleteCachedAssets()` on the SDK instance. If you just wish to close the SDK but may need to use it in the future, you can eliminate the need for re-downloading the resources by calling `BlinkIdVerifySdk.close()`.

Note that `BlinkIdVerifyScanningSession.close()`, `BlinkIdVerifySdk.close()` and `BlinkIdVerifySdk.closeAndDeleteCachedAssets()` are blocking calls. Do not call them on the main/UI thread; run them on a background dispatcher/thread (for example `Dispatchers.IO`).

# <a name="troubleshoot"></a> Troubleshooting

### Integration difficulties
In case of problems with SDK integration, make sure that you have followed [integration instructions](#sdk-integration) and [device requirements](#device-requirements). If you're still having problems, please contact us at [help.microblink.com](https://help.microblink.com) describing your problem and provide the following information:

* high-resolution scan/photo of the item that you are trying to read
* information about the device that you are using - we need the exact model name of the device. You can obtain that information with any app like [this one](https://play.google.com/store/apps/details?id=ru.andr7e.deviceinfohw)
* please stress that you are reporting a problem related to the Android version of _BlinkID Verify_ SDK

# <a name="additional-info"></a> Additional info

## <a name="sdk-size"></a> BlinkID Verify SDK size

We recommend that you distribute your app using [App Bundle](https://developer.android.com/platform/technology/app-bundle). This will defer APK generation to Google Play, allowing it to generate minimal APK for each specific device that downloads your app, including only required processor architecture support.


Here is the SDK size, calculated for supported ABIs:

| ABI | Download size | Install size |
| --- |:-------------:|:------------:|
| armeabi-v7a |    5.45 MB    |   8.11 MB    |
| arm64-v8a |    5.83 MB    |   10.19 MB   |

SDK size is calculated as application size increases when _BlinkID Verify_ SDK is added, with all its dependencies included.

## <a name="supported-docs"></a> Supported documents 

BlinkID Verify SDK uses BlinkID SDK for document scanning and extraction. The list of supported documents and result fields is maintained for BlinkID SDK.

To determine what is supported in a specific Verify SDK version:

1. Find your Verify SDK version in the table below.
2. Note the corresponding BlinkID SDK version.
3. Check the [supported documents documentation](https://docs.microblink.com/blinkid/supported-documents) for that BlinkID version.

Version mapping:

| Verify SDK | BlinkID SDK |
|:----------:|:-----------:|
| v4000.0.0  |  v8002.0.0  |
|  v3.21.0   |    v7.8     |
|  v3.20.0   |    v7.7     |
|  v3.14.1   |    v7.4     |
|  v3.14.0   |    v7.4     |
|   v3.9.0   |    v7.0     |


## <a name="api-documentation"></a> API documentation
You can find the BlinkID Verify SDK **KDoc** documentation [here](https://microblink.github.io/blinkid-verify-android/index.html).

Full BlinkID Verify API can be found [here](https://docs.microblink.com/verify).

## <a name="contact"></a> Contact
For any other questions, feel free to contact us at [help.microblink.com](https://help.microblink.com).
