package com.microblink.blinkidverify.sample.ui.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import com.microblink.blinkidverify.core.data.model.result.BlinkIdVerifyV3EndpointResponse
import kotlinx.serialization.json.Json

object BlinkIDVerifyCustomNavType {
    val BlinkIDVerifyResultType = object : NavType<BlinkIdVerifyV3EndpointResponse>(
        isNullableAllowed = true
    ) {
        override fun get(bundle: Bundle, key: String): BlinkIdVerifyV3EndpointResponse? {
            return Json.decodeFromString(bundle.getString(key) ?: return null)
        }

        override fun put(bundle: Bundle, key: String, value: BlinkIdVerifyV3EndpointResponse) {
            bundle.putString(key, Json.encodeToString(value))
        }

        override fun parseValue(value: String): BlinkIdVerifyV3EndpointResponse {
            return Json.decodeFromString(Uri.decode(value))
        }

        override fun serializeAsValue(value: BlinkIdVerifyV3EndpointResponse): String {
            return Uri.encode(Json.encodeToString(value))
        }
    }
}