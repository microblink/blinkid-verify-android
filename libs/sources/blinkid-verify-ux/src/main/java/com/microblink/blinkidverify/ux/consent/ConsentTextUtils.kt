/*
 * Copyright (c) Microblink. Modifications are allowed under the terms of the
 * license for files located in the UX/UI lib folder.
 */

package com.microblink.blinkidverify.ux.consent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Positional placeholder used in CMS / Android string resources for an inline link label.
 *
 * Use with [formatMessageWithLink] for plain text and [buildClickableLinkAnnotatedString] for UI.
 */
internal const val CONSENT_LINK_PLACEHOLDER = "%1\$s"

/**
 * Replaces the link placeholder in [messageTemplate] with [linkLabel].
 *
 * This mirrors Android's `getString(templateRes, linkLabel)` behavior without requiring a
 * [android.content.Context].
 */
internal fun formatMessageWithLink(messageTemplate: String, linkLabel: String): String =
    messageTemplate.replace(CONSENT_LINK_PLACEHOLDER, linkLabel)

/**
 * Builds an [AnnotatedString] from a CMS template that contains [CONSENT_LINK_PLACEHOLDER].
 *
 * The placeholder is rendered as a styled, clickable [linkLabel]. Styling and click handling stay
 * in code so string resources remain plain text suitable for translation and CMS export.
 */
internal fun buildClickableLinkAnnotatedString(
    messageTemplate: String,
    linkLabel: String,
    linkColor: Color,
    linkTag: String,
    onLinkClick: () -> Unit,
): AnnotatedString = buildAnnotatedString {
    val placeholderIndex = messageTemplate.indexOf(CONSENT_LINK_PLACEHOLDER)
    if (placeholderIndex < 0) {
        append(messageTemplate)
        return@buildAnnotatedString
    }

    append(messageTemplate.substring(0, placeholderIndex))
    withLink(
        LinkAnnotation.Clickable(
            tag = linkTag,
            linkInteractionListener = { onLinkClick() },
        )
    ) {
        withStyle(SpanStyle(color = linkColor)) {
            append(linkLabel)
        }
    }
    append(messageTemplate.substring(placeholderIndex + CONSENT_LINK_PLACEHOLDER.length))
}
