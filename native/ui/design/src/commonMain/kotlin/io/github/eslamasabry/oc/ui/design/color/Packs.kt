package io.github.eslamasabry.oc.ui.design.color

/**
 * The 31 theme packs (plan 06 §4.2; Dart `ThemePackId`, `lib/state/profiles.dart`). Stored by [storageName] in
 * `flutter.oc.themePack`, which equals the Dart enum name; an unknown name reads Graphite (`opencode`).
 */
public enum class PackId(public val storageName: String) {
    Opencode("opencode"),
    Catppuccin("catppuccin"),
    Gruvbox("gruvbox"),
    Solarized("solarized"),
    Dracula("dracula"),
    Nord("nord"),
    TokyoNight("tokyoNight"),
    OneDark("oneDark"),
    Monokai("monokai"),
    RosePine("rosePine"),
    Everforest("everforest"),
    Kanagawa("kanagawa"),
    Ayu("ayu"),
    NightOwl("nightOwl"),
    Github("github"),
    Palenight("palenight"),
    Synthwave("synthwave"),
    Cobalt("cobalt"),
    Midnight("midnight"),
    Paper("paper"),
    Forest("forest"),
    Ocean("ocean"),
    Sunset("sunset"),
    Sakura("sakura"),
    Lavender("lavender"),
    Mint("mint"),
    Coffee("coffee"),
    Slate("slate"),
    Amber("amber"),
    HighContrast("highContrast"),
    Dynamic("dynamic"),
    ;

    public companion object {
        /** The stored name → pack; null, empty or unknown → Graphite (Dart reads unknown names as the default). */
        public fun fromStorage(name: String?): PackId = entries.firstOrNull { it.storageName == name } ?: Opencode
    }
}

/**
 * One brightness of a pack: the colours the role derivation reads (Dart `ThemePalette`: the scheme's primary,
 * secondary, tertiary and onSurface, the background, navigation and success) or, for Graphite, the roles spelled out.
 * The other Material slots of the Dart scheme are not ported: no native part reads them (the kit's Material bridge
 * derives from the roles).
 */
public class PackPalette internal constructor(
    internal val primaryD: DColor,
    internal val secondaryD: DColor,
    internal val tertiaryD: DColor,
    internal val onSurfaceD: DColor,
    internal val backgroundD: DColor,
    internal val navigationD: DColor,
    internal val successD: DColor,
    public val dark: Boolean,
    private val ownRoles: Roles? = null,
) {
    public val primary: Argb get() = primaryD.toArgb()
    public val secondary: Argb get() = secondaryD.toArgb()
    public val tertiary: Argb get() = tertiaryD.toArgb()
    public val onSurface: Argb get() = onSurfaceD.toArgb()
    public val background: Argb get() = backgroundD.toArgb()
    public val navigation: Argb get() = navigationD.toArgb()
    public val success: Argb get() = successD.toArgb()

    /** The whole role set: the pack's own, or derived with the contrast floors built in (Dart `themeRoles`). */
    public val roles: Roles by lazy {
        ownRoles ?: RoleDerivation.deriveRoles(
            accent = primary,
            ground = background,
            dark = dark,
            text = onSurface,
            success = success,
            partners = listOf(secondary, tertiary),
        )
    }

    public companion object {
        public fun of(
            dark: Boolean,
            primary: Argb,
            secondary: Argb,
            tertiary: Argb,
            onSurface: Argb,
            background: Argb,
            navigation: Argb,
            success: Argb,
        ): PackPalette =
            PackPalette(
                primary.toD(), secondary.toD(), tertiary.toD(), onSurface.toD(), background.toD(), navigation.toD(),
                success.toD(), dark,
            )

        internal fun fromRoles(roles: Roles): PackPalette =
            PackPalette(
                roles.accent.toD(), roles.partner.toD(), roles.codeType.toD(), roles.text1.toD(), roles.ground.toD(),
                roles.surface2.toD(), roles.success.toD(), roles.dark, roles,
            )
    }
}

/** A pack: its id, its brand name and both brightnesses (Dart `ThemePack`). */
public class Pack(public val id: PackId, public val label: String, public val dark: PackPalette, public val light: PackPalette) {
    public fun palette(dark: Boolean): PackPalette = if (dark) this.dark else light
}

public object Packs {
    /** The packs spelled out by hand; they carry goldens. The generated ones are held to the floors by test. */
    public val curated: List<PackId> = listOf(PackId.Opencode, PackId.Catppuccin, PackId.Gruvbox, PackId.Solarized)

    /** The picker's "most used" first row (plan 06 §4.2); the rest follow under "See all". */
    public val mostUsed: List<PackId> =
        listOf(
            PackId.Opencode, PackId.Nord, PackId.Catppuccin, PackId.Dracula, PackId.TokyoNight, PackId.Gruvbox,
            PackId.RosePine, PackId.Solarized,
        )

    /** Every static pack (all but Material You, which needs the platform's tones). */
    public val static: List<PackId> = PackId.entries.filter { it != PackId.Dynamic }

    public val graphite: Pack by lazy {
        Pack(PackId.Opencode, "Graphite", PackPalette.fromRoles(Graphite.dark), PackPalette.fromRoles(Graphite.light))
    }

    private val built: Map<PackId, Pack> by lazy {
        PacksGenerated.specs.mapValues { (id, spec) -> PackRecipe.generate(id, spec) }
    }

    /**
     * The pack for [id]. Material You resolves from [dynamic] (the platform's tones, API 31+); without them Graphite
     * stands in, as Dart `effectiveThemePack` does.
     */
    public fun of(id: PackId, dynamic: DynamicSource? = null): Pack =
        when (id) {
            PackId.Opencode -> graphite
            PackId.Dynamic -> dynamic?.toPack() ?: graphite
            PackId.Catppuccin, PackId.Gruvbox, PackId.Solarized -> CuratedPacks.of(id)
            else -> built.getValue(id)
        }
}
