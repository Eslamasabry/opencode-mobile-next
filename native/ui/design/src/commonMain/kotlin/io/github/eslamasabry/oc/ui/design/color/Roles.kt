package io.github.eslamasabry.oc.ui.design.color

/**
 * The 24 colour roles (plan 06 §4.1; Dart `ThemeRoles`, `lib/ui/theme_roles.dart`). The truth for every colour the kit
 * draws; the Material scheme is derived from it (kit `MaterialBridge`). There is no danger or error role: nothing in
 * the interface is red (owner rule R2).
 */
public data class Roles(
    val dark: Boolean,
    val ground: Argb,
    val surface1: Argb,
    val surface2: Argb,
    val surface3: Argb,
    /** Translucent; never re-alpha'd. */
    val hairline: Argb,
    val text1: Argb,
    val text2: Argb,
    val text3: Argb,
    val accent: Argb,
    val onAccent: Argb,
    val partner: Argb,
    val onPartner: Argb,
    val attention: Argb,
    val attentionFill: Argb,
    val onAttentionFill: Argb,
    val attentionSurface: Argb,
    val attentionLine: Argb,
    val success: Argb,
    val scrim: Argb,
    val codeKeyword: Argb,
    val codeString: Argb,
    val codeType: Argb,
    /** Black 30 % in both brightnesses. */
    val elevationShadow: Argb,
) {
    val codeAdded: Argb get() = success
    val codeRemoved: Argb get() = text2
    val codeAddedSurface: Argb get() = success.withAlpha(0.10f)
    val codeRemovedSurface: Argb get() = text2.withAlpha(0.10f)

    /**
     * Saver (true black): a dark theme on #000000 with its surfaces moved a third of the way to black; every other
     * role kept. A light theme is returned as is (Saver is always dark). Dart `ThemeRoles.saver`.
     */
    public val saver: Roles
        get() {
            if (!dark) return this
            val black = argb(0xFF000000)
            return copy(
                ground = black,
                surface1 = lerp(surface1, black, 0.3f),
                surface2 = lerp(surface2, black, 0.3f),
                surface3 = lerp(surface3, black, 0.3f),
            )
        }
}

/** Graphite, the default pack (stored as `opencode`). Values copied from `lib/ui/theme_roles.dart` (graphiteDark/Light). */
public object Graphite {
    public val dark: Roles =
        Roles(
            dark = true,
            ground = argb(0xFF0B0C0E),
            surface1 = argb(0xFF141518),
            surface2 = argb(0xFF1B1C20),
            surface3 = argb(0xFF26282D),
            hairline = argb(0x12FFFFFF), // rgba(255,255,255,.07)
            text1 = argb(0xFFF3F3F1),
            text2 = argb(0xFFA3A5AB),
            text3 = argb(0xFF8C8F96),
            accent = argb(0xFF3DDC8A),
            onAccent = argb(0xFF03140B),
            partner = argb(0xFFC7A6FF),
            onPartner = argb(0xFF111214),
            attention = argb(0xFFFFB547),
            attentionFill = argb(0xFFFFB547),
            onAttentionFill = argb(0xFF1A1000),
            attentionSurface = argb(0x17FFB547), // .09
            attentionLine = argb(0x4DFFB547), // .30
            success = argb(0xFF3DDC8A),
            scrim = argb(0x94000000), // .58
            codeKeyword = argb(0xFFC7A6FF),
            codeString = argb(0xFFFFB88A),
            codeType = argb(0xFF7FD1FF),
            elevationShadow = argb(0x4D000000), // black .30
        )

    public val light: Roles =
        Roles(
            dark = false,
            ground = argb(0xFFF3F3F1),
            surface1 = argb(0xFFFAFAF8),
            surface2 = argb(0xFFFFFFFF),
            surface3 = argb(0xFFE9E9E6),
            hairline = argb(0x14000000), // rgba(0,0,0,.08)
            text1 = argb(0xFF111214),
            text2 = argb(0xFF50535A),
            text3 = argb(0xFF62656C),
            accent = argb(0xFF087F43),
            onAccent = argb(0xFFFFFFFF),
            partner = argb(0xFF6D4AFF),
            onPartner = argb(0xFFFFFFFF),
            attention = argb(0xFF9A5700),
            attentionFill = argb(0xFFFFB547),
            onAttentionFill = argb(0xFF1A1000),
            attentionSurface = argb(0x1FFFA600), // rgba(255,166,0,.12)
            attentionLine = argb(0x4DBE6E00), // rgba(190,110,0,.30)
            success = argb(0xFF087F43),
            scrim = argb(0x94000000),
            codeKeyword = argb(0xFF6D4AFF),
            codeString = argb(0xFFB4480B),
            codeType = argb(0xFF0B6BA8),
            elevationShadow = argb(0x4D000000),
        )

    public fun of(dark: Boolean): Roles = if (dark) this.dark else light
}
