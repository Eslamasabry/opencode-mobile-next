package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.type.TextTone

/** One Compose colour per role (plan 06 §5.3). Kit parts read it; features never read colours. */
@Immutable
public data class KitColors(
    val dark: Boolean,
    val ground: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val hairline: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val onAccent: Color,
    val partner: Color,
    val onPartner: Color,
    val attention: Color,
    val attentionFill: Color,
    val onAttentionFill: Color,
    val attentionSurface: Color,
    val attentionLine: Color,
    val success: Color,
    val scrim: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeType: Color,
    val elevationShadow: Color,
) {
    /**
     * The colour of live things (the running dot and ring, the message box glow, the live line). Flutter draws them in
     * the accent, so this is the accent: the exact rebuild comes first (owner, 2026-11-05). A brighter live colour on
     * light themes was approved as a later improvement and is parked until the rebuild is done.
     */
    val live: Color
        get() = accent

    public fun tone(t: TextTone): Color =
        when (t) {
            TextTone.Primary -> text1
            TextTone.Secondary -> text2
            TextTone.Tertiary -> text3
            TextTone.Accent -> accent
            TextTone.OnAccent -> onAccent
            TextTone.Attention -> attention
            TextTone.Success -> success
        }
}

internal fun Argb.toColor(): Color = Color(v)

public fun Roles.toKitColors(): KitColors =
    KitColors(
        dark = dark,
        ground = ground.toColor(),
        surface1 = surface1.toColor(),
        surface2 = surface2.toColor(),
        surface3 = surface3.toColor(),
        hairline = hairline.toColor(),
        text1 = text1.toColor(),
        text2 = text2.toColor(),
        text3 = text3.toColor(),
        accent = accent.toColor(),
        onAccent = onAccent.toColor(),
        partner = partner.toColor(),
        onPartner = onPartner.toColor(),
        attention = attention.toColor(),
        attentionFill = attentionFill.toColor(),
        onAttentionFill = onAttentionFill.toColor(),
        attentionSurface = attentionSurface.toColor(),
        attentionLine = attentionLine.toColor(),
        success = success.toColor(),
        scrim = scrim.toColor(),
        codeKeyword = codeKeyword.toColor(),
        codeString = codeString.toColor(),
        codeType = codeType.toColor(),
        elevationShadow = elevationShadow.toColor(),
    )
