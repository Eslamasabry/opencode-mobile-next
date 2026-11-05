package io.github.eslamasabry.oc.ui.design.color

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitRoles

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
    /** `graphiteDark` (lib/ui/theme_roles.dart), from the generated [FlutterKitRoles]. */
    public val dark: Roles get() = FlutterKitRoles.graphiteDark

    /** `graphiteLight` (lib/ui/theme_roles.dart), from the generated [FlutterKitRoles]. */
    public val light: Roles get() = FlutterKitRoles.graphiteLight

    public fun of(dark: Boolean): Roles = if (dark) this.dark else light
}
