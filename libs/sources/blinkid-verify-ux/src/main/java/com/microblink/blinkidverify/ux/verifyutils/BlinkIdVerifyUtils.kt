/**
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.verifyutils

import com.microblink.blinkidverify.ux.utils.UxPingletTracker
import com.microblink.blinkidverify.core.BlinkIdVerifySdk
import com.microblink.blinkidverify.core.utils.ping.sendPingletsIfAllowed
import com.microblink.blinkidverify.core.ping.config.PingSendTriggerPoint

fun onCameraPermissionCheck(sessionNumber: Int) {
    UxPingletTracker.CameraPermission.trackCameraPermissionCheck(sessionNumber)
    BlinkIdVerifySdk.sendPingletsIfAllowed(PingSendTriggerPoint.CameraPermissionCheck)
}

fun onCameraPermissionRequest(sessionNumber: Int) {
    UxPingletTracker.CameraPermission.trackCameraPermissionRequest(sessionNumber)
}

fun onCameraPermissionUserResponse(sessionNumber: Int, cameraPermissionGranted: Boolean) {
    UxPingletTracker.CameraPermission.trackCameraPermissionUserResponse(
        cameraPermissionGranted,
        sessionNumber
    )
}

fun onCameraPreviewStarted(sessionNumber: Int) {
    UxPingletTracker.UxEvent.trackSimpleEvent(
        UxPingletTracker.UxEvent.SimpleUxEventType.CameraStarted,
        sessionNumber
    )
    BlinkIdVerifySdk.sendPingletsIfAllowed(PingSendTriggerPoint.CameraStarted)
}

fun onCameraPreviewStopped(sessionNumber: Int) {
    UxPingletTracker.UxEvent.trackSimpleEvent(
        UxPingletTracker.UxEvent.SimpleUxEventType.CameraClosed,
        sessionNumber
    )
}

fun onCloseButtonClicked(sessionNumber: Int) {
    UxPingletTracker.UxEvent.trackSimpleEvent(
        UxPingletTracker.UxEvent.SimpleUxEventType.CloseButtonClicked,
        sessionNumber
    )
}

fun onAppMovedToBackground(sessionNumber: Int) {
    UxPingletTracker.UxEvent.trackSimpleEvent(
        UxPingletTracker.UxEvent.SimpleUxEventType.AppMovedToBackground,
        sessionNumber
    )
}
