package io.github.eslamasabry.oc.ui.design.color

/** One Accent swatch: its stored id and its colour in each brightness (Dart `AppearanceAccent`). */
public data class AccentSwatch(val id: String, val dark: Argb, val light: Argb) {
    public fun of(dark: Boolean): Argb = if (dark) this.dark else light
}

/**
 * Settings › Appearance › Accent (plan 06 §4.2; Dart `appearanceAccents`, `graphiteAccents`). Stored by id in
 * `oc.appearanceAccent`; absent means "Match theme" (the pack's own partner). The swatch drives `partner` through
 * [withPartner]; `accent` stays the pack's own colour. None sits near amber (LOOK-39).
 */
public object Accents {
    public val swatches: List<AccentSwatch> =
        listOf(
            AccentSwatch("mint", argb(0xFF3DDC8A), argb(0xFF087F43)),
            AccentSwatch("teal", argb(0xFF3CCFCF), argb(0xFF0D7377)),
            AccentSwatch("blue", argb(0xFF5AB0FF), argb(0xFF1F6FEB)),
            AccentSwatch("violet", argb(0xFFC7A6FF), argb(0xFF6D4AFF)),
            AccentSwatch("pink", argb(0xFFF2A7E6), argb(0xFFAD2E99)),
            AccentSwatch("mono", argb(0xFFF1F2F4), argb(0xFF262B36)),
            AccentSwatch("silver", argb(0xFFB9BDC6), argb(0xFF5A606C)),
        )

    /** Graphite's own main-accent choices: green (the default), blue, teal, violet. */
    public val graphite: List<AccentSwatch> =
        listOf(
            AccentSwatch("green", argb(0xFF3DDC8A), argb(0xFF087F43)),
            AccentSwatch("blue", argb(0xFF5AB0FF), argb(0xFF1F6FEB)),
            AccentSwatch("teal", argb(0xFF3CCFCF), argb(0xFF0D7377)),
            AccentSwatch("violet", argb(0xFFC7A6FF), argb(0xFF6D4AFF)),
        )

    /** The swatch stored as [id]; null for "Match theme" or an unknown id (Dart `appearanceAccentById`). */
    public fun byId(id: String?): AccentSwatch? = swatches.firstOrNull { it.id == id }
}
