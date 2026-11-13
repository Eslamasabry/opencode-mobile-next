package io.github.eslamasabry.oc.ui.design.effects

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The Glass lab's numbers (owner 2026-11-13: "Why don't you build a page with all the sliders for me to tweak it?"). Every
 * value that changes how a liquid pane looks is one field of [GlassKnobs], one set per pane kind and theme. The shipped
 * set ([GlassTuning.shipped]) is read from today's recipes and constants, so a tuning nobody touched changes nothing: the
 * kit applies a knob only where it differs from the shipped one ([GlassTuning.Shipped] reproduces the app exactly).
 */

/** The kinds of glass pane the lab tunes one by one. */
public enum class GlassPaneKind(public val key: String, public val label: String) {
    Bar("bar", "Bar"),
    MessageBox("box", "Message box"),
    Menu("menu", "Menu"),
    Sheet("sheet", "Sheet"),
    Dialog("dialog", "Dialog"),
    Chip("chip", "Chip"),
    Button("button", "Round button"),
    ;

    /** The liquid role whose recipe this kind's shipped values come from. */
    public val role: LiquidRole
        get() =
            when (this) {
                Bar -> LiquidRole.Bar
                MessageBox -> LiquidRole.Composer
                Menu -> LiquidRole.Menu
                Sheet, Dialog -> LiquidRole.Sheet
                Chip -> LiquidRole.Pill
                Button -> LiquidRole.Action
            }

    public companion object {
        /** The kind a [role]'s pane is tuned as; a dialog is drawn with the sheet's role and says so with [dialog]. */
        public fun of(role: LiquidRole, dialog: Boolean = false): GlassPaneKind =
            when (role) {
                LiquidRole.Bar, LiquidRole.Dock -> Bar
                LiquidRole.Composer -> MessageBox
                LiquidRole.Menu -> Menu
                LiquidRole.Sheet -> if (dialog) Dialog else Sheet
                LiquidRole.Pill, LiquidRole.Status -> Chip
                LiquidRole.Action -> Button
            }

        public fun byKey(key: String): GlassPaneKind? = entries.firstOrNull { it.key == key }
    }
}

/** Every number of one pane kind's glass in one theme. Flags (lens to the middle, softening) are 0 = off, 1 = on. */
public data class GlassKnobs(
    val blurDp: Float,
    val tint: Float,
    /** -1 (the tint mixed fully to black) .. 0 (the theme's own tint colour) .. 1 (fully to white). */
    val tintLightness: Float,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    /** The lens rim: at most this many dp in from the edge ... */
    val lensWidthDp: Float,
    /** ... and at most this share of the pane's shorter side. */
    val lensShare: Float,
    val lensStrengthDp: Float,
    val lensMiddle: Float,
    val dispersion: Float,
    /** The rim light's strength at its peak (dark theme: added white light; light theme: the dark edge line). */
    val rimBrightness: Float,
    val rimWidthDp: Float,
    val rimFloor: Float,
    val rimFocus: Float,
    val lightDeg: Float,
    val innerShadow: Float,
    val innerShadowDp: Float,
    val sheen: Float,
    val soften: Float,
    val softenKnee: Float,
    val softenKeep: Float,
    val colourLimit: Float,
    val shadow: Float,
    /** The bend's profile: -1 a soft dome, 0 the shipped circle, 1 a sharp bevel at the rim. */
    val bendShape: Float,
    /** The specular glint on the rim where the light falls: its strength (white, alpha), its glint line's share of it,
     * and how far its bright segment reaches from the corner (dp). The shipped glass draws none. */
    val specular: Float,
    val specularLine: Float,
    val specularReachDp: Float,
    /**
     * How much of the page's own light and colour comes through: 1 = the page shows fully (no softening), lower values
     * hold its light and colour back more and more, down to soft light at 0 ([GlassTuning.passThrough]).
     */
    val passThrough: Float,
)

/** One slider of the lab: its words, its unit, its range and step, and the knob it moves. */
public enum class GlassKnob(
    public val id: String,
    public val label: String,
    public val unit: String,
    public val min: Float,
    public val max: Float,
    public val step: Float,
    public val group: String,
    internal val get: (GlassKnobs) -> Float,
    internal val put: (GlassKnobs, Float) -> GlassKnobs,
) {
    // Light refraction: what makes glass read as liquid (the bend at the edge, its glint and how much page comes through).
    LensStrength("lenss", "Refraction strength", "dp the page is bent", 0f, 120f, 1f, GLASS_REFRACTION_GROUP, { it.lensStrengthDp }, { k, v -> k.copy(lensStrengthDp = v) }),
    LensWidth("lensw", "Refraction depth", "dp in from the edge", 0f, 80f, 1f, GLASS_REFRACTION_GROUP, { it.lensWidthDp }, { k, v -> k.copy(lensWidthDp = v) }),
    LensShare("lenscap", "Refraction depth cap", "share of the short side", 0f, .5f, .01f, GLASS_REFRACTION_GROUP, { it.lensShare }, { k, v -> k.copy(lensShare = v) }),
    BendShape("shape", "Bend shape", "-1 soft dome, 0 round, +1 sharp bevel", -1f, 1f, .05f, GLASS_REFRACTION_GROUP, { it.bendShape }, { k, v -> k.copy(bendShape = v) }),
    LensMiddle("lensmid", "Bend to the middle", "0 rim only, 1 whole pane", 0f, 1f, 1f, GLASS_REFRACTION_GROUP, { it.lensMiddle }, { k, v -> k.copy(lensMiddle = v) }),
    Dispersion("split", "Colour split", "rainbow at the bent edge, 0-1", 0f, 1f, .05f, GLASS_REFRACTION_GROUP, { it.dispersion }, { k, v -> k.copy(dispersion = v) }),
    Specular("glint", "Glint strength", "alpha", 0f, 1f, .01f, GLASS_REFRACTION_GROUP, { it.specular }, { k, v -> k.copy(specular = v) }),
    SpecularReach("glintw", "Glint width", "dp along the rim", 10f, 300f, 5f, GLASS_REFRACTION_GROUP, { it.specularReachDp }, { k, v -> k.copy(specularReachDp = v) }),
    SpecularLine("glintline", "Glint line", "top line, share of the glint", 0f, 1f, .05f, GLASS_REFRACTION_GROUP, { it.specularLine }, { k, v -> k.copy(specularLine = v) }),
    LightDirection("light", "Light direction", "degrees (rim light)", 0f, 180f, 5f, GLASS_REFRACTION_GROUP, { it.lightDeg }, { k, v -> k.copy(lightDeg = v) }),
    PassThrough("through", "Light passing through", "1 page shows fully, 0 soft light", 0f, 1f, .01f, GLASS_REFRACTION_GROUP, { it.passThrough }, { k, v -> k.copy(passThrough = v) }),
    // Clarity: frost and tint.
    Blur("blur", "Blur", "dp", 0f, 30f, .5f, "Clarity", { it.blurDp }, { k, v -> k.copy(blurDp = v) }),
    Tint("tint", "Tint strength", "alpha", 0f, 1f, .01f, "Clarity", { it.tint }, { k, v -> k.copy(tint = v) }),
    TintLightness("lightness", "Tint lightness", "-1 black, +1 white", -1f, 1f, .05f, "Clarity", { it.tintLightness }, { k, v -> k.copy(tintLightness = v) }),
    Saturation("sat", "Saturation", "x", 0f, 3f, .05f, "Clarity", { it.saturation }, { k, v -> k.copy(saturation = v) }),
    Contrast("contrast", "Contrast", "x", .5f, 1.5f, .02f, "Clarity", { it.contrast }, { k, v -> k.copy(contrast = v) }),
    Brightness("bright", "Brightness", "shift", -.3f, .3f, .01f, "Clarity", { it.brightness }, { k, v -> k.copy(brightness = v) }),
    // Rim light.
    RimBrightness("rim", "Rim brightness", "alpha", 0f, 1f, .01f, "Rim", { it.rimBrightness }, { k, v -> k.copy(rimBrightness = v) }),
    RimWidth("rimw", "Rim width", "dp", 0f, 4f, .25f, "Rim", { it.rimWidthDp }, { k, v -> k.copy(rimWidthDp = v) }),
    RimFloor("rimfloor", "Rim floor", "share of peak at the dark corners", 0f, 1f, .01f, "Rim", { it.rimFloor }, { k, v -> k.copy(rimFloor = v) }),
    RimFocus("rimfocus", "Rim focus", "falloff power", .2f, 6f, .1f, "Rim", { it.rimFocus }, { k, v -> k.copy(rimFocus = v) }),
    Sheen("sheen", "Top sheen", "alpha", 0f, .5f, .01f, "Rim", { it.sheen }, { k, v -> k.copy(sheen = v) }),
    // Depth.
    InnerShadow("inner", "Inner shadow", "alpha", 0f, 1f, .01f, "Depth", { it.innerShadow }, { k, v -> k.copy(innerShadow = v) }),
    InnerShadowSize("innerdp", "Inner shadow size", "dp", 0f, 48f, 1f, "Depth", { it.innerShadowDp }, { k, v -> k.copy(innerShadowDp = v) }),
    Shadow("shadow", "Pane shadow", "alpha", 0f, 1f, .01f, "Depth", { it.shadow }, { k, v -> k.copy(shadow = v) }),
    // Words: the fine controls behind "Light passing through" (they win where moved).
    Soften("soften", "Detail softening", "0 off, 1 on", 0f, 1f, 1f, "Words", { it.soften }, { k, v -> k.copy(soften = v) }),
    SoftenKnee("knee", "Softening knee", "light level", 0f, 1f, .01f, "Words", { it.softenKnee }, { k, v -> k.copy(softenKnee = v) }),
    SoftenKeep("slope", "Softening slope", "share of light kept", 0f, 1f, .01f, "Words", { it.softenKeep }, { k, v -> k.copy(softenKeep = v) }),
    ColourLimit("colour", "Colour limit", "most chroma, 0 = none", 0f, 1f, .01f, "Words", { it.colourLimit }, { k, v -> k.copy(colourLimit = v) }),
    ;

    public fun of(k: GlassKnobs): Float = get(k)

    public fun on(k: GlassKnobs, value: Float): GlassKnobs = put(k, snap(value))

    /** [value] clamped to the range and rounded to the step (so a typed 3.14159 reads 3.15 on a .05 step). */
    public fun snap(value: Float): Float {
        val v = value.coerceIn(min, max)
        val n = ((v - min) / step).roundToInt()
        return (min + n * step).coerceIn(min, max).let { (it * 1000f).roundToInt() / 1000f }
    }

    public companion object {
        public fun byId(id: String): GlassKnob? = entries.firstOrNull { it.id == id }
    }
}

/** The lab's first group of sliders (the coordinator's "Light refraction", 2026-11-13). */
public const val GLASS_REFRACTION_GROUP: String = "Light refraction"

/** A pane kind in one theme. */
public data class GlassPaneKey(val kind: GlassPaneKind, val dark: Boolean)

/** The three starting points the lab offers. */
public enum class GlassPreset(public val label: String) {
    Shipped("Shipped"),
    ClearLiquid("Clear liquid"),
    Frost("Frost"),
}

/**
 * The tuned glass: [panes] holds the kinds and themes someone moved; every other one is [shipped]. [inApp]: the
 * person's "Use in the whole app" switch (the app wears this tuning only while it is on).
 */
public data class GlassTuning(val panes: Map<GlassPaneKey, GlassKnobs> = emptyMap(), val inApp: Boolean = false) {
    public fun knobs(kind: GlassPaneKind, dark: Boolean): GlassKnobs = panes[GlassPaneKey(kind, dark)] ?: shipped(kind, dark)

    /** [knob] of [kind] in a [dark] or light theme set to [value] (snapped to its step). */
    public fun with(kind: GlassPaneKind, dark: Boolean, knob: GlassKnob, value: Float): GlassTuning =
        copy(panes = panes + (GlassPaneKey(kind, dark) to knob.on(knobs(kind, dark), value)))

    /**
     * "All linked": every kind moves by the same step as [from] did when it went to [value] (each kept in its range),
     * so the kinds keep their differences.
     */
    public fun linked(from: GlassPaneKind, dark: Boolean, knob: GlassKnob, value: Float): GlassTuning {
        val delta = knob.snap(value) - knob.of(knobs(from, dark))
        var t = this
        for (kind in GlassPaneKind.entries) {
            val v = if (kind == from) value else knob.of(knobs(kind, dark)) + delta
            t = t.with(kind, dark, knob, v)
        }
        return t
    }

    /** Every pane of the [dark] or light theme at [preset]'s numbers (the other theme is kept). */
    public fun preset(preset: GlassPreset, dark: Boolean): GlassTuning =
        copy(panes = panes.filterKeys { it.dark != dark } + GlassPaneKind.entries.associate { GlassPaneKey(it, dark) to presetKnobs(preset, it, dark) })

    /** True when every pane is at its shipped numbers. */
    public val isShipped: Boolean
        get() = GlassPaneKind.entries.all { k -> listOf(true, false).all { d -> knobs(k, d) == shipped(k, d) } }

    public companion object {
        /** Nothing moved: the app as it ships. */
        public val Shipped: GlassTuning = GlassTuning()

        /** The light theme's glass edge: a faint dark line (the kit's `LIGHT_EDGE_ALPHA`). */
        public const val LIGHT_EDGE_ALPHA: Float = .10f

        /** The rim light's shipped width, dp (the kit's `RIM_LIGHT_DP`). */
        public const val RIM_WIDTH_DP: Float = 1f

        /** The rim light's shipped direction: along the top-start / bottom-end diagonal. */
        public const val LIGHT_DEG: Float = 45f

        /** The inner shadow's size when someone turns it on (the backdrop library's default). */
        public const val INNER_SHADOW_DP: Float = 24f

        /** [kind]'s numbers as the app ships them today, in a [dark] or light theme, under the default look. */
        public fun shipped(kind: GlassPaneKind, dark: Boolean): GlassKnobs {
            val r = LiquidEdge.recipe(kind.role, dark)
            return GlassKnobs(
                blurDp = r.blurDp,
                tint = r.tint,
                tintLightness = 0f,
                saturation = r.saturation,
                contrast = r.contrast,
                brightness = r.brightness,
                lensWidthDp = r.rimMaxDp,
                lensShare = r.rimShare,
                lensStrengthDp = r.refractionDp,
                lensMiddle = if (r.depth) 1f else 0f,
                dispersion = 0f,
                rimBrightness = if (dark) io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.liquidRimDark else LIGHT_EDGE_ALPHA,
                rimWidthDp = RIM_WIDTH_DP,
                rimFloor = RimLight.FLOOR,
                rimFocus = RimLight.FALLOFF,
                lightDeg = LIGHT_DEG,
                innerShadow = 0f,
                innerShadowDp = INNER_SHADOW_DP,
                sheen = 0f,
                soften = if (r.hold.knee) 1f else 0f,
                softenKnee = LiquidJoin.KNEE,
                softenKeep = LiquidJoin.KNEE_SLOPE,
                colourLimit = r.hold.chroma,
                shadow = r.shadowAlpha,
                bendShape = 0f,
                specular = 0f,
                specularLine = io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.glintShare,
                specularReachDp = SPECULAR_REACH_DP,
                passThrough = if (r.hold.knee) PASS_SHIPPED_HOLD else 1f,
            )
        }

        /** The specular's bright segment's shipped reach, dp (the kit's `SPECULAR_REACH_DP`). */
        public const val SPECULAR_REACH_DP: Float = 120f

        /** "Light passing through" where the shipped glass softens (menus, sheets, dialogs): its knee, slope and limit. */
        public const val PASS_SHIPPED_HOLD: Float = .5f

        /**
         * What "Light passing through" at [p] means for the softening (on, knee, share kept, colour limit): continuous,
         * the menus' and sheets' shipped softening at [PASS_SHIPPED_HOLD] (.36, .22, .26), all light through at 1 (off).
         */
        public fun passThrough(p: Float): FloatArray {
            val v = p.coerceIn(0f, 1f)
            if (v >= 1f) return floatArrayOf(0f, LiquidJoin.KNEE, 1f, 0f)
            val knee = .08f + .56f * v
            val keep = if (v < .5f) .44f * v else .22f + .78f * (v - .5f) * 2f
            val chroma = if (v < .5f) .52f * v else .26f + .74f * (v - .5f) * 2f
            return floatArrayOf(1f, knee, keep, chroma)
        }

        /** [preset]'s numbers for [kind] in a [dark] or light theme. */
        public fun presetKnobs(preset: GlassPreset, kind: GlassPaneKind, dark: Boolean): GlassKnobs {
            val s = shipped(kind, dark)
            return when (preset) {
                GlassPreset.Shipped -> s
                GlassPreset.ClearLiquid -> clearLiquid(s, kind, dark)
                GlassPreset.Frost -> frost(s, kind, dark)
            }
        }

        /**
         * Apple-style liquid glass, honestly: almost no blur (the page behind stays sharp and alive), a thin light tint,
         * a wide rim that bends the page hard at the edge while the middle stays calm, and a crafted rim (a brighter,
         * slightly wider line, higher floor so no corner goes dark, a hint of top sheen). Words are held by softening
         * the bright spots under them, not by frost.
         */
        private fun clearLiquid(s: GlassKnobs, kind: GlassPaneKind, dark: Boolean): GlassKnobs {
            val small = kind == GlassPaneKind.Chip || kind == GlassPaneKind.Button
            val modal = kind == GlassPaneKind.Menu || kind == GlassPaneKind.Sheet || kind == GlassPaneKind.Dialog
            return s.copy(
                blurDp = when { small -> 0f; modal -> 2f; else -> .5f },
                tint = if (kind == GlassPaneKind.Button) s.tint else if (dark) .10f else .08f,
                saturation = 1.5f,
                brightness = if (dark) -.02f else 0f,
                lensWidthDp = when { small -> 16f; modal -> 34f; else -> 30f },
                lensShare = if (small) .45f else .42f,
                lensStrengthDp = when { kind == GlassPaneKind.Chip -> 20f; kind == GlassPaneKind.Button -> 30f; modal -> 64f; else -> 56f },
                rimBrightness = if (dark) .85f else .16f,
                rimWidthDp = 1.25f,
                rimFloor = .45f,
                rimFocus = 1.3f,
                sheen = if (dark) .06f else .10f,
                bendShape = .25f,
                specular = if (dark) .55f else .70f,
                specularReachDp = if (small) 40f else 140f,
                specularLine = .6f,
                passThrough = if (modal) .6f else if (small) 1f else .8f,
            )
        }

        /** Calm frosted glass: more blur and a fuller tint, a short soft bend, the rim kept. */
        private fun frost(s: GlassKnobs, kind: GlassPaneKind, dark: Boolean): GlassKnobs {
            val small = kind == GlassPaneKind.Chip || kind == GlassPaneKind.Button
            val modal = kind == GlassPaneKind.Menu || kind == GlassPaneKind.Sheet || kind == GlassPaneKind.Dialog
            return s.copy(
                blurDp = when { small -> 4f; modal -> 20f; else -> 12f },
                tint = if (kind == GlassPaneKind.Button) s.tint else if (dark) .42f else .40f,
                saturation = 1.4f,
                lensWidthDp = if (small) 8f else 14f,
                lensStrengthDp = if (small) 6f else 14f,
                rimBrightness = if (dark) .55f else .10f,
                rimFloor = .40f,
                bendShape = -.5f,
                passThrough = 1f,
                soften = 0f,
                colourLimit = 0f,
            )
        }

        /** The block the lab copies: a header with [build], then one line per pane kind and theme, every number. */
        public fun encode(t: GlassTuning, build: String? = null): String = buildString {
            append(HEADER)
            if (build != null) append(" · build ").append(build)
            append(" · in app: ").append(if (t.inApp) "on" else "off")
            for (kind in GlassPaneKind.entries) {
                for (dark in listOf(true, false)) {
                    val k = t.knobs(kind, dark)
                    append('\n').append(kind.key).append('.').append(if (dark) "dark" else "light")
                    for (knob in GlassKnob.entries) append(' ').append(knob.id).append('=').append(number(knob.of(k)))
                }
            }
        }

        /**
         * [text] read back: every `kind.theme key=value` it holds, the rest shipped; null when it holds no tuning line
         * at all (not a glass block). Unknown kinds, keys and numbers are skipped.
         */
        public fun decode(text: String): GlassTuning? {
            var t = GlassTuning()
            var any = false
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.startsWith(HEADER)) {
                    t = t.copy(inApp = line.contains("in app: on"))
                    continue
                }
                val parts = line.split(' ').filter { it.isNotEmpty() }
                val head = parts.firstOrNull() ?: continue
                val kind = GlassPaneKind.byKey(head.substringBefore('.')) ?: continue
                val dark =
                    when (head.substringAfter('.', "")) {
                        "dark" -> true
                        "light" -> false
                        else -> continue
                    }
                any = true
                var k = t.knobs(kind, dark)
                for (p in parts.drop(1)) {
                    val knob = GlassKnob.byId(p.substringBefore('=')) ?: continue
                    val v = p.substringAfter('=', "").toFloatOrNull() ?: continue
                    k = knob.on(k, v)
                }
                t = t.copy(panes = t.panes + (GlassPaneKey(kind, dark) to k))
            }
            return if (any) t.trimmed() else null
        }

        /** The block's first words. */
        public const val HEADER: String = "OpenCode glass tuning v1"

        /** [v] with at most three decimals and no trailing zeros, no exponent. */
        public fun number(v: Float): String {
            val m = (abs(v) * 1000f).roundToInt()
            val whole = m / 1000
            val frac = (m % 1000).toString().padStart(3, '0').trimEnd('0')
            val sign = if (v < 0f && m != 0) "-" else ""
            return if (frac.isEmpty()) "$sign$whole" else "$sign$whole.$frac"
        }
    }

    /** The same tuning with every pane that is at its shipped numbers dropped (an untouched block reads as shipped). */
    public fun trimmed(): GlassTuning = copy(panes = panes.filter { (key, k) -> k != shipped(key.kind, key.dark) })
}
