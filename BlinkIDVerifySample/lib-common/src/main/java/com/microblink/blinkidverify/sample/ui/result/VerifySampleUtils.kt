package com.microblink.blinkidverify.sample.ui.result

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.microblink.blinkidverify.core.data.model.checks.VerifyCheck

data class ResultItem(
    @StringRes val title: Int,
    val value: String?,
    val children: List<ResultItem> = emptyList(),
    @StringRes val description: Int? = null
)

sealed class Result {
    abstract val title: String?
    abstract val children: List<Any>?
    abstract val description: String?
}

data class ResultVerifyCheck(
    val verifyCheck: VerifyCheck,
    override val title: String? = verifyCheck.name,
    override val children: List<VerifyCheck>? = verifyCheck.checks,
    override val description: String? = null,
) : Result()

fun VerifyCheck.toResultVerifyCheck(): ResultVerifyCheck {
    return ResultVerifyCheck(this)
}

data class SampleResult(
    override val title: String?,
    override val children: List<SampleResult>? = null,
    override val description: String? = null,
    val value: String,
    val subValue: String? = null,
) : Result()

val ResultTitleText = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 6.sp
)

val ResultValueText = TextStyle(
    color = Color.Black,
    fontWeight = FontWeight.Medium,
    fontSize = 20.sp,
    lineHeight = 24.sp,
)