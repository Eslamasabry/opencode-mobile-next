package io.github.eslamasabry.oc.engine.settings.prefs

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape

/**
 * The device preference keys this engine owns (plan 05 Appendix A, `:engine:settings` rows; logical names, exactly
 * as Flutter stores them). All are app-wide: none carries a profile id and the profile sweep never takes them.
 */
public object DevicePreferenceKeys {
    public const val APPEARANCE: String = "oc.appearance"
    public const val THEME_PACK: String = "oc.themePack"
    public const val LOCALE: String = "oc.appLocale"
    public const val TEXT_SIZE: String = "oc.textSize"
    public const val ACCENT: String = "oc.appearanceAccent"
    public const val FROST: String = "oc.glass"
    public const val REASONING: String = "oc.transcript.reasoningExpanded"
    public const val TIMESTAMPS: String = "oc.transcript.timestampsVisible"
    public const val MOTION: String = "oc.effectsMotion"

    /** Legacy (L6): `false` with Motion `full` reads as Calm; removed when Full is chosen again. */
    public const val CELEBRATIONS: String = "oc.effectsCelebrations"
    public const val GLOW: String = "oc.effectsActivityGlow"
    public const val GLOW_STYLE: String = "oc.effectsGlowStyle"

    /** A new name (2026-10-26): the old `oc.effectsLiveBackground` is retired. */
    public const val LIVE_BACKGROUND: String = "oc.effectsBackgroundOn"
    public const val LIVE_HELD_BY_STYLE: String = "oc.effectsLiveHeldByStyle"
    public const val LIVE_STYLE: String = "oc.effectsLiveStyle"
    public const val LIVE_INTENSITY: String = "oc.effectsLiveIntensity"
    public const val SURFACE_STYLE: String = "oc.effectsSurfaceStyle"
    public const val CORNERS: String = "oc.effectsCorners"
    public const val DENSITY: String = "oc.effectsDensity"

    /** Handling I: written by Flutter, never read; only removed when a new style resets the knobs. */
    public const val DEPTH: String = "oc.effectsDepth"
    public const val EDGES: String = "oc.effectsEdges"
    public const val GLOW_COLOURS: String = "oc.effectsGlowColours"
    public const val GLOW_SPEED: String = "oc.effectsGlowSpeed"

    /** Every key whose stored value shapes [DevicePreferences] (the I keys excluded: they shape nothing). */
    public val READ: Set<String> = linkedSetOf(
        APPEARANCE, THEME_PACK, LOCALE, TEXT_SIZE, ACCENT, FROST, REASONING, TIMESTAMPS, MOTION, CELEBRATIONS, GLOW,
        GLOW_STYLE, LIVE_BACKGROUND, LIVE_HELD_BY_STYLE, LIVE_STYLE, LIVE_INTENSITY, SURFACE_STYLE, CORNERS, DENSITY,
    )

    /** The keys that make up the facade's `effects` value (provenance: any of them stored = explicit). */
    public val EFFECTS_PROVENANCE: Set<String> = setOf(MOTION, CELEBRATIONS, GLOW, GLOW_STYLE)

    private const val OWNER = ":engine:settings"

    /** Ledger rows for [io.github.eslamasabry.oc.core.data.scope.ProfileScope.register]. */
    public val SHAPES: List<KeyShape> = buildList {
        fun app(key: String, type: PrefType) = add(KeyShape(key, KeyScope.App, type, OWNER, "device preference"))
        listOf(
            APPEARANCE, THEME_PACK, LOCALE, TEXT_SIZE, ACCENT, FROST, MOTION, GLOW_STYLE, LIVE_STYLE, LIVE_INTENSITY,
            SURFACE_STYLE, CORNERS, DENSITY, DEPTH, EDGES, GLOW_COLOURS, GLOW_SPEED,
        ).forEach { app(it, PrefType.Str) }
        listOf(REASONING, TIMESTAMPS, CELEBRATIONS, GLOW, LIVE_BACKGROUND, LIVE_HELD_BY_STYLE)
            .forEach { app(it, PrefType.Bool) }
    }
}

/**
 * Keys nothing reads any more (the liquid glass guard's counters, the live background's old switch). Flutter drops
 * them on every profile load (`ProfileStore.retiredPreferenceKeys`, B-STATE-081); nothing else is touched (the old
 * `oc.effectsGlass` switch is harmless and stays).
 */
public object RetiredPreferences {
    public val KEYS: List<String> =
        listOf("oc.glassLiquidActive", "oc.glassLiquidStrikes", "oc.glassLiquidOffUntil", "oc.effectsLiveBackground")

    /**
     * Removes the retired keys that are present, in one commit; `Ok(count)`. A refused removal returns the storage
     * failure and the keys stay for the next load. No commit at all when none is present.
     */
    public suspend fun drop(store: KeyValueStore, lane: WriterLane): Outcome<Int> {
        val present = KEYS.filter { store.get(it) != null }
        if (present.isEmpty()) return Outcome.Ok(0)
        return when (val result = store.commit(lane) { present.forEach(::remove) }) {
            is Outcome.Ok -> Outcome.Ok(present.size)
            is Outcome.Failed -> result
        }
    }
}
