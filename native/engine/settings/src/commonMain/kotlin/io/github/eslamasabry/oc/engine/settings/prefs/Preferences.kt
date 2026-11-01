package io.github.eslamasabry.oc.engine.settings.prefs

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferenceKeys as K
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference

/**
 * The app-wide consent authority the device preferences share (Flutter `AppPreferencesStore.readStateConsent`).
 * The per-profile consent store (`:engine:settings` `consent`, SETTINGS-B) implements it. [epoch] must change
 * synchronously, before any storage await, on every consent change, so a revocation reaches observers at once
 * (B-SEC-010).
 */
public interface ConsentAuthority {
    public val epoch: StateFlow<Long>

    public companion object {
        /** No consent store bound (tests, previews). */
        public val None: ConsentAuthority = object : ConsentAuthority {
            override val epoch: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
        }
    }
}

/**
 * The device preferences facade over `:core:data` (plan 03 §11.2 `Preferences`, plan 06 §5.2; Flutter
 * `AppPreferencesStore`). ONE instance per app graph: every connection graph reads the same acknowledged value
 * (B-STATE-126).
 *
 * - Reads come from the store's acknowledged snapshot; unknown names read as defaults (B-STATE-003).
 * - Writes run one at a time ([set]); each change is ONE commit on the app lane, so a multi-key change is
 *   all-or-nothing (B-STATE-004). Nothing is published before the store acknowledges; a refusal publishes nothing and
 *   the cached value stays the acknowledged one (B-STATE-005).
 * - Acknowledged writes by anyone else on the same store (another instance, a Kotlin surface) are picked up from
 *   [KeyValueStore.changes] while [scope] lives.
 */
public class Preferences(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    scope: CoroutineScope,
    /** The shared consent authority (B-SEC-010). */
    public val consent: ConsentAuthority = ConsentAuthority.None,
) {
    private class Snapshot(val value: DevicePreferences, val stored: Set<String>)

    private val writeLock = Mutex()
    private val publishLock = Mutex()

    private val initial = Snapshot(readStored(store), storedKeys(store))
    private val snapshot = AtomicReference(initial)

    private val mutableValue = MutableStateFlow(initial.value)
    private val mutableRevision = MutableStateFlow(0L)
    private val mutableAppearance = MutableStateFlow(initial.value.appearance)
    private val mutableMotion = MutableStateFlow(initial.value.effects.motion)
    private val mutableSaver = MutableStateFlow(initial.value.saver)
    private val mutableLocale = MutableStateFlow(initial.value.locale)

    /** The acknowledged snapshot. */
    public val value: StateFlow<DevicePreferences> = mutableValue.asStateFlow()

    /** Bumps once per published change of [value] (or of which keys are stored). */
    public val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    public val appearance: StateFlow<AppearanceChoice> = mutableAppearance.asStateFlow()
    public val motion: StateFlow<MotionLevel> = mutableMotion.asStateFlow()
    public val saver: StateFlow<Boolean> = mutableSaver.asStateFlow()
    public val locale: StateFlow<LocaleChoice> = mutableLocale.asStateFlow()

    /** The shared consent epoch: moves at once on a revocation (B-SEC-010). */
    public val privacyEpoch: StateFlow<Long> get() = consent.epoch

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.changes.collect { keys -> if (keys.any { it in K.READ }) refresh() }
        }
    }

    /** Provenance for the same acknowledged snapshot as [value]: the key is stored (not a default). */
    public fun hasStored(key: String): Boolean = key in snapshot.load().stored

    /**
     * Saves [change] and returns the acknowledged snapshot. A refused save returns the store's `Failure.Storage` and
     * leaves [value] as it was.
     */
    public suspend fun set(change: PreferenceChange): Outcome<DevicePreferences> = writeLock.withLock { setLocked(change) }

    /** Runs [block] holding the write lock (the settings facade validates its token and writes in one step). */
    internal suspend fun <T> exclusive(block: suspend () -> T): T = writeLock.withLock { block() }

    /** Caller holds the write lock. */
    internal suspend fun setLocked(change: PreferenceChange): Outcome<DevicePreferences> {
        val current = readStored(store)
        val edits = edits(change, current)
        if (edits.isEmpty()) return Outcome.Ok(refresh())
        return when (val result = store.commit(lane) { edits.forEach { (key, value) -> put(key, value) } }) {
            is Outcome.Ok -> Outcome.Ok(refresh())
            is Outcome.Failed -> result
        }
    }

    private suspend fun refresh(): DevicePreferences = publishLock.withLock {
        val next = Snapshot(readStored(store), storedKeys(store))
        val previous = snapshot.load()
        if (next.value != previous.value || next.stored != previous.stored) {
            snapshot.store(next)
            mutableValue.value = next.value
            mutableAppearance.value = next.value.appearance
            mutableMotion.value = next.value.effects.motion
            mutableSaver.value = next.value.saver
            mutableLocale.value = next.value.locale
            mutableRevision.value += 1
        }
        snapshot.load().value
    }

    private fun edits(change: PreferenceChange, current: DevicePreferences): Map<String, Any?> = when (change) {
        is PreferenceChange.Appearance -> mapOf(K.APPEARANCE to change.value.wire)
        is PreferenceChange.Theme -> mapOf(K.THEME_PACK to change.value.wire)
        is PreferenceChange.Locale -> mapOf(K.LOCALE to change.value.code)
        is PreferenceChange.Text -> mapOf(K.TEXT_SIZE to change.value.wire)
        is PreferenceChange.Accent -> mapOf(K.ACCENT to change.id?.takeIf { it.isNotEmpty() })
        is PreferenceChange.Frost -> mapOf(K.FROST to change.value.wire)
        is PreferenceChange.ReasoningExpanded -> mapOf(K.REASONING to change.value)
        is PreferenceChange.TimestampsVisible -> mapOf(K.TIMESTAMPS to change.value)
        is PreferenceChange.SetEffects -> effectEdits(current.effects, change.value)
    }

    private fun effectEdits(before: Effects, value: Effects): Map<String, Any?> = buildMap {
        if (before.motion != value.motion) put(K.MOTION, value.motion.wire)
        if (before.activityGlow != value.activityGlow) put(K.GLOW, value.activityGlow)
        if (before.liveBackground != value.liveBackground) put(K.LIVE_BACKGROUND, value.liveBackground)
        if (before.liveHeldByStyle != value.liveHeldByStyle) put(K.LIVE_HELD_BY_STYLE, if (value.liveHeldByStyle) true else null)
        if (before.liveStyle != value.liveStyle) put(K.LIVE_STYLE, value.liveStyle)
        if (before.liveIntensity != value.liveIntensity) put(K.LIVE_INTENSITY, value.liveIntensity.wire)
        if (before.surfaceStyle != value.surfaceStyle) {
            put(K.SURFACE_STYLE, value.surfaceStyle.wire)
            // A new style starts at its own Fine-tune defaults (plan 06 §5.2 write rule).
            put(K.CORNERS, null)
            put(K.DEPTH, null)
            put(K.DENSITY, null)
            put(K.EDGES, null)
        } else {
            if (before.fineTune.corners != value.fineTune.corners) put(K.CORNERS, value.fineTune.corners?.wire)
            if (before.fineTune.density != value.fineTune.density) put(K.DENSITY, value.fineTune.density?.wire)
        }
        if (before.glowStyle != value.glowStyle) put(K.GLOW_STYLE, value.glowStyle.wire)
        if (value.motion == MotionLevel.Full && store.get(K.CELEBRATIONS) != null) put(K.CELEBRATIONS, null)
    }

    internal companion object {
        fun PrefEdit.put(key: String, value: Any?) {
            when (value) {
                null -> remove(key)
                is String -> putString(key, value)
                is Boolean -> putBool(key, value)
                is Long -> putLong(key, value)
                else -> error("unsupported preference type")
            }
        }

        fun storedKeys(store: KeyValueStore): Set<String> = K.READ.filterTo(LinkedHashSet()) { store.get(it) != null }

        /** The Flutter `_read()` rules, value by value. */
        fun readStored(store: KeyValueStore): DevicePreferences {
            fun raw(key: String): Any? = when (val v = store.get(key)) {
                is PrefValue.Str -> v.value
                is PrefValue.Bool -> v.value
                is PrefValue.Int64 -> v.value
                is PrefValue.Dbl -> v.value
                is PrefValue.StrList -> v.value
                is PrefValue.Undecodable, null -> null
            }
            fun bool(key: String, fallback: Boolean): Boolean = raw(key) as? Boolean ?: fallback

            var motion = wireOf<MotionLevel>(raw(K.MOTION)) ?: MotionLevel.Full
            if (motion == MotionLevel.Full && raw(K.CELEBRATIONS) == false) motion = MotionLevel.Calm
            val language = raw(K.LOCALE) as? String
            return DevicePreferences(
                appearance = wireOf<AppearanceChoice>(raw(K.APPEARANCE)) ?: AppearanceChoice.Dark,
                themePack = wireOf<ThemePack>(raw(K.THEME_PACK)) ?: ThemePack.OpenCode,
                locale = LocaleChoice.entries.firstOrNull { it.code != null && it.code == language } ?: LocaleChoice.System,
                effects = Effects(
                    motion = motion,
                    activityGlow = bool(K.GLOW, true),
                    glowStyle = wireOf<GlowStyle>(raw(K.GLOW_STYLE)) ?: GlowStyle.Classic,
                    liveBackground = bool(K.LIVE_BACKGROUND, false),
                    liveHeldByStyle = bool(K.LIVE_HELD_BY_STYLE, false),
                    liveStyle = raw(K.LIVE_STYLE) as? String ?: Effects.DEFAULT_LIVE_STYLE,
                    liveIntensity = wireOf<LiveIntensity>(raw(K.LIVE_INTENSITY)) ?: LiveIntensity.Normal,
                    surfaceStyle = wireOf<SurfaceStyle>(raw(K.SURFACE_STYLE)) ?: SurfaceStyle.Solid,
                    fineTune = FineTune(
                        corners = wireOf<Corners>(raw(K.CORNERS)),
                        density = wireOf<Density>(raw(K.DENSITY)),
                    ),
                ),
                textSize = wireOf<TextSize>(raw(K.TEXT_SIZE)) ?: TextSize.Normal,
                accent = (raw(K.ACCENT) as? String)?.takeIf { it.isNotEmpty() },
                frost = wireOf<FrostStrength>(raw(K.FROST)) ?: FrostStrength.Balanced,
                reasoningExpanded = bool(K.REASONING, false),
                timestampsVisible = bool(K.TIMESTAMPS, false),
            )
        }
    }
}
