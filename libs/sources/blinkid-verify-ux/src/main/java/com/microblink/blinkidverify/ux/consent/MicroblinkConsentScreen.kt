/*
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.consent

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.microblink.blinkidverify.core.consent.model.ConsentDefaults
import com.microblink.blinkidverify.core.utils.MbLog
import com.microblink.blinkidverify.ux.R
import com.microblink.blinkidverify.ux.UiSettings
import com.microblink.blinkidverify.ux.components.drawScrollbar
import com.microblink.blinkidverify.ux.theme.BlinkIdVerifySdkTheme
import com.microblink.blinkidverify.ux.theme.SdkTheme

private const val TAG = "MicroblinkConsentScreen"

private val ConsentDialogHorizontalMargin = 24.dp
private val ConsentDialogMaxWidth = 560.dp
private val ConsentDialogCornerRadius = 28.dp
private val ConsentContentHorizontalPadding = 24.dp
private val ConsentActionsPadding = 24.dp
private val ConsentCloseButtonInset = 8.dp
private val ConsentCloseToHeadlineSpacing = 12.dp
private val ConsentHeadlineToMessageSpacing = 24.dp
private val ConsentMessageToInfoPanelSpacing = 55.dp
private val ConsentInfoPanelToToggleSpacing = 54.dp
private val ConsentInfoPanelCornerRadius = 4.dp
private val ConsentInfoPanelPadding = 12.dp
private val ConsentToggleLabelSpacing = 16.dp
private val ConsentSwitchTrackWidth = 32.dp
private val ConsentSwitchTrackHeight = 20.dp
private val ConsentSwitchThumbSize = 14.dp
private val ConsentSwitchThumbInset =
    (ConsentSwitchTrackHeight - ConsentSwitchThumbSize) / 2
private val ConsentButtonSpacing = 8.dp
private val ConsentButtonCornerRadius = 100.dp
private val ConsentButtonBorderWidth = 1.dp
private const val ConsentDialogMaxHeightRatio = 0.8

/**
 * Copy and legal document displayed by [MicroblinkConsentScreen].
 *
 * @property title Title of the consent screen.
 * @property message Primary legal text presented to the end user.
 * @property customMessage Integrator-provided legal text that replaces [message] when set.
 * @property infoMessage Supporting text shown in the info box. `%1$s` is replaced with
 *                      [documentLinkLabel].
 * @property documentLinkLabel Label of the link that opens [documentUrl].
 * @property optionalToggleMessage Text shown next to the optional consent toggle.
 * @property optionalToggleHint Italic suffix appended to [optionalToggleMessage].
 * @property acceptButton Label of the button that grants consent.
 * @property declineButton Label of the button that declines consent.
 * @property documentUrl Location of the full legal text.
 */
internal data class ConsentScreenContent(
    @param:StringRes val title: Int = R.string.mb_blinkidverify_consent_title,
    @param:StringRes val message: Int = R.string.mb_blinkidverify_consent_description,
    val customMessage: String? = null,
    @param:StringRes val infoMessage: Int = R.string.mb_blinkidverify_consent_privacy_notice,
    @param:StringRes val documentLinkLabel: Int = R.string.mb_blinkidverify_consent_privacy_link,
    @param:StringRes val optionalToggleMessage: Int = R.string.mb_blinkidverify_consent_privacy_switch,
    @param:StringRes val optionalToggleHint: Int = R.string.mb_blinkidverify_optional,
    @param:StringRes val acceptButton: Int = R.string.mb_blinkidverify_consent_yes,
    @param:StringRes val declineButton: Int = R.string.mb_blinkidverify_consent_no,
    val documentUrl: String = ConsentDefaults.CONSENT_DOCUMENT_URL,
)

/**
 * Text styles of the consent dialog.
 *
 * The design uses the Material 3 type scale, which the SDK typography does not cover, so the styles
 * are derived from the closest SDK style to keep an integrator-provided font family applied.
 */
private object ConsentTypography {
    val title: TextStyle
        @Composable
        get() = SdkTheme.sdkTypography.onboardingTitle.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        )

    val message: TextStyle
        @Composable
        get() = SdkTheme.sdkTypography.onboardingText.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        )

    val supportingText: TextStyle
        @Composable
        get() = SdkTheme.sdkTypography.helpTooltip.copy(
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        )

    val toggleLabel: TextStyle
        @Composable
        get() = supportingText.copy(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.Both,
            ),
        )

    val button: TextStyle
        @Composable
        get() = SdkTheme.sdkTypography.onboardingButton.copy(letterSpacing = 0.1.sp)
}

/**
 * Embeddable consent UI of the Microblink UI consent flow.
 *
 * Displayed by the scanning screen when the session is configured with
 * [BlinkIdVerifyConsentUxConfig.RequireConsent] and no consent has been granted yet. Image analysis
 * is paused for as long as this screen is displayed.
 *
 * The composable is intentionally self-contained: it only renders the provided [content] and reports
 * the end user's decision, so it can be moved to the shared UX library and reused by other SDKs.
 *
 * @param onConsentAccepted Invoked with the legal text the end user agreed to.
 * @param onConsentDeclined Invoked when the end user declines the consent.
 */
@Composable
internal fun MicroblinkConsentScreen(
    onConsentAccepted: (consentNote: String) -> Unit,
    onConsentDeclined: () -> Unit,
    modifier: Modifier = Modifier,
    content: ConsentScreenContent = ConsentScreenContent(),
) {
    val context = LocalContext.current
    val title = stringResource(content.title)
    val message = content.customMessage ?: stringResource(content.message)
    val documentLinkLabel = stringResource(content.documentLinkLabel)
    val infoMessageTemplate = stringResource(content.infoMessage)
    val infoMessage = formatMessageWithLink(infoMessageTemplate, documentLinkLabel)
    val optionalToggleMessage = stringResource(content.optionalToggleMessage)
    val optionalToggleHint = stringResource(content.optionalToggleHint)
    var optionalConsentGiven by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onConsentDeclined,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            // The dialog is sized by its margins instead of the platform dialog width.
            usePlatformDefaultWidth = false,
        )
    ) {
        val density = LocalResources.current.displayMetrics.density
        val maxHeight =
            (LocalWindowInfo.current.containerSize.height / density * ConsentDialogMaxHeightRatio).dp
        Card(
            modifier = modifier
                .padding(horizontal = ConsentDialogHorizontalMargin)
                .widthIn(max = ConsentDialogMaxWidth)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            shape = RoundedCornerShape(ConsentDialogCornerRadius)
        ) {
            val contentColor = MaterialTheme.colorScheme.onBackground
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ConsentCloseButtonInset, end = ConsentCloseButtonInset),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onConsentDeclined) {
                        Icon(
                            painter = painterResource(R.drawable.mb_blinkidverify_icon_exit),
                            contentDescription = stringResource(R.string.mb_blinkidverify_close),
                            modifier = Modifier.size(36.dp),
                            tint = contentColor,
                        )
                    }
                }

                Spacer(Modifier.height(ConsentCloseToHeadlineSpacing))

                Column(
                    Modifier
                        .drawScrollbar(rememberScrollState())
                        .weight(1f, fill = false)
                        .padding(horizontal = ConsentContentHorizontalPadding)
                ) {
                    Text(
                        modifier = Modifier.semantics { heading() },
                        text = title,
                        style = ConsentTypography.title,
                        color = contentColor,
                    )
                    Spacer(Modifier.height(ConsentHeadlineToMessageSpacing))
                    Text(
                        text = message,
                        color = contentColor,
                        style = ConsentTypography.message,
                    )
                    Spacer(Modifier.height(ConsentMessageToInfoPanelSpacing))
                    ConsentInfoPanel(
                        messageTemplate = infoMessageTemplate,
                        linkLabel = documentLinkLabel,
                        onLinkClick = { openConsentDocument(context, content.documentUrl) },
                    )
                    Spacer(Modifier.height(ConsentInfoPanelToToggleSpacing))
                    ConsentOptionalToggleRow(
                        checked = optionalConsentGiven,
                        onCheckedChange = { optionalConsentGiven = it },
                        message = optionalToggleMessage,
                        hint = optionalToggleHint,
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(ConsentActionsPadding),
                    verticalArrangement = Arrangement.spacedBy(ConsentButtonSpacing),
                ) {
                    ConsentOutlinedActionButton(
                        text = stringResource(content.acceptButton),
                        onClick = {
                            onConsentAccepted(
                                buildConsentNote(
                                    title = title,
                                    message = message,
                                    infoMessage = infoMessage,
                                    optionalToggleMessage = optionalToggleMessage,
                                    optionalConsentGiven = optionalConsentGiven,
                                )
                            )
                        },
                    )
                    ConsentOutlinedActionButton(
                        text = stringResource(content.declineButton),
                        onClick = onConsentDeclined,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsentInfoPanel(
    messageTemplate: String,
    linkLabel: String,
    onLinkClick: () -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedText = buildClickableLinkAnnotatedString(
        messageTemplate = messageTemplate,
        linkLabel = linkLabel,
        linkColor = linkColor,
        linkTag = "privacy_notice",
        onLinkClick = onLinkClick,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(ConsentInfoPanelCornerRadius),
    ) {
        Text(
            modifier = Modifier.padding(ConsentInfoPanelPadding),
            text = annotatedText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = ConsentTypography.supportingText,
        )
    }
}

@Composable
private fun ConsentSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbOffsetX by animateDpAsState(
        targetValue = if (checked) {
            ConsentSwitchTrackWidth - ConsentSwitchThumbSize - ConsentSwitchThumbInset
        } else {
            ConsentSwitchThumbInset
        },
        label = "consentSwitchThumbOffset",
    )
    val trackShape = RoundedCornerShape(percent = 50)
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val thumbColor = if (checked) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .size(ConsentSwitchTrackWidth, ConsentSwitchTrackHeight)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .clip(trackShape)
            .then(
                if (!checked) {
                    Modifier.border(
                        ConsentButtonBorderWidth,
                        MaterialTheme.colorScheme.outline,
                        trackShape,
                    )
                } else {
                    Modifier
                }
            )
            .background(trackColor),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset(thumbOffsetX.roundToPx(), 0) }
                .size(ConsentSwitchThumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
private fun ConsentOptionalToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    message: String,
    hint: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        ConsentSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Spacer(Modifier.width(ConsentToggleLabelSpacing))
        Text(
            modifier = Modifier.weight(1f),
            text = buildAnnotatedString {
                append(message)
                append(' ')
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append('(')
                    append(hint)
                    append(')')
                }
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = ConsentTypography.toggleLabel,
        )
    }
}

@Composable
private fun ConsentOutlinedActionButton(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(ConsentButtonCornerRadius),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(ConsentButtonBorderWidth, MaterialTheme.colorScheme.primary),
    ) {
        Text(
            text = text,
            style = ConsentTypography.button,
        )
    }
}

private fun buildConsentNote(
    title: String,
    message: String,
    infoMessage: String,
    optionalToggleMessage: String,
    optionalConsentGiven: Boolean,
): String = buildString {
    append(title)
    append("\n\n")
    append(message)
    append("\n\n")
    append(infoMessage)
    if (optionalConsentGiven) {
        append("\n\n")
        append(optionalToggleMessage)
    }
}

private fun openConsentDocument(context: Context, documentUrl: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, documentUrl.toUri()))
    } catch (e: Exception) {
        MbLog.w(TAG, e) { "Cannot open the consent document" }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun MicroblinkConsentScreenPreviewLight() {
    BlinkIdVerifySdkTheme(verifyUiSettings = UiSettings(), darkTheme = false) {
        MicroblinkConsentScreen(
            onConsentAccepted = {},
            onConsentDeclined = {},
        )
    }
}

@Preview(name = "Dark Mode", showBackground = true)
@Composable
private fun MicroblinkConsentScreenPreviewDark() {
    BlinkIdVerifySdkTheme(verifyUiSettings = UiSettings(), darkTheme = true) {
        MicroblinkConsentScreen(
            onConsentAccepted = {},
            onConsentDeclined = {},
        )
    }
}
