package io.github.eslamasabry.oc.engine.settings.scope

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.settings.prefs.AppearanceChoice
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferenceKeys
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferences
import io.github.eslamasabry.oc.engine.settings.prefs.GlowColours
import io.github.eslamasabry.oc.engine.settings.prefs.GlowSpeed
import io.github.eslamasabry.oc.engine.settings.prefs.GlowStyle
import io.github.eslamasabry.oc.engine.settings.prefs.LocaleChoice
import io.github.eslamasabry.oc.engine.settings.prefs.MotionLevel
import io.github.eslamasabry.oc.engine.settings.prefs.PreferenceChange
import io.github.eslamasabry.oc.engine.settings.prefs.Preferences
import io.github.eslamasabry.oc.engine.settings.prefs.ThemePack
import io.github.eslamasabry.oc.engine.settings.prefs.WireNamed
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference

/**
 * Local Settings access bound to explicit owners (Flutter `ProfileSettingsAccess`). It never acquires a gateway,
 * connects a server or writes remote configuration.
 *
 * | Id | Value | Target | Write |
 * |---|---|---|---|
 * | `appearance`, `themePack` | Text (Dart enum name) | device | yes |
 * | `locale` | Text language code, or null for the system | device | yes |
 * | `reasoningExpanded`, `timestampsVisible` | Flag | device | yes |
 * | `effects` | Record `motion`, `activityGlow`, `glowStyle`, `glowColours`, `glowSpeed` | device | yes, whole record |
 * | `shareReadState` | Flag | connection | yes; false is an immediate fail-closed revocation |
 * | `model` | null or Record `providerId`, `modelId` | connection | read-only |
 * | `agent`, `variant` | Text | connection | read-only |
 *
 * Device edits validate their token and persist in one step under the shared [Preferences] write lock, so competing
 * editors on different facades conflict instead of overwriting each other (B-STATE-125).
 */
public class LocalSettingsAccess(
    private val preferences: Preferences,
    private val owners: SettingsOwners,
    private val consent: ReadStateConsentPort,
    private val store: KeyValueStore,
    scope: CoroutineScope,
) : SettingsAccess {
    private val closed = MutableStateFlow(false)
    private val issued = AtomicReference<PersistentSet<SettingsEditToken>>(persistentHashSetOf())
    private val selectionRevision = MutableStateFlow(0L)

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.changes.collect { keys ->
                if (keys.any { key -> SELECTION_PREFIXES.any(key::startsWith) }) selectionRevision.value += 1
            }
        }
    }

    /** Closes watches and refuses later operations (`Disposed`). The shared preferences stay usable. */
    public fun close() {
        closed.value = true
        issued.store(persistentHashSetOf())
    }

    override suspend fun read(id: String, target: SettingsTarget): EffectiveSetting = readNow(id, target)

    override fun watch(id: String, target: SettingsTarget): Flow<EffectiveSetting> = flow {
        if (closed.value) {
            emit(unavailable(id, target, SettingsFailureReason.Disposed))
            return@flow
        }
        val captured = fingerprint(target)
        val signals = combine(preferences.revision, owners.revision, consent.epoch, selectionRevision, closed) { _, _, _, _, isClosed -> isClosed }
        signals.transformWhile { isClosed ->
            if (!isClosed) {
                val replaced = target !is SettingsTarget.Device && fingerprint(target) != captured
                emit(if (replaced) unavailable(id, target, SettingsFailureReason.StaleTarget) else readNow(id, target))
            }
            !isClosed
        }.collect { emit(it) }
    }

    override suspend fun beginEdit(id: String, target: SettingsTarget): Outcome<SettingsEditToken> {
        val before = readNow(id, target)
        writableReason(before)?.let { return it.failed() }
        val fingerprint = fingerprint(target) ?: return SettingsFailureReason.StaleTarget.failed()
        val token = SettingsEditToken(before, fingerprint, this)
        update { it.adding(token) }
        return Outcome.Ok(token)
    }

    override suspend fun write(edit: SettingsEditToken, value: SettingValue?): Outcome<EffectiveSetting> {
        checkEdit(edit)?.let { return it.failed() }
        if (edit.before.id == SHARE_READ_STATE) return writeConsent(edit, value)
        val change = deviceChange(edit.before.id, value) ?: return SettingsFailureReason.InvalidValue.failed()
        return preferences.exclusive {
            checkEdit(edit)?.let { return@exclusive it.failed() }
            update { it.removing(edit) }
            when (val saved = preferences.setLocked(change)) {
                is Outcome.Failed -> saved
                is Outcome.Ok -> confirm(edit, value)
            }
        }
    }

    private suspend fun writeConsent(edit: SettingsEditToken, value: SettingValue?): Outcome<EffectiveSetting> {
        val enable = (value as? SettingValue.Flag)?.value ?: return SettingsFailureReason.InvalidValue.failed()
        val profileId = edit.before.target.profileId ?: return SettingsFailureReason.StaleTarget.failed()
        update { it.removing(edit) }
        val saved = consent.setEnabledIfCurrent(profileId, enable) {
            !closed.value && fingerprint(edit.before.target) == edit.ownerFingerprint
        }
        if (saved is Outcome.Failed) return saved
        return confirm(edit, value)
    }

    /** After the acknowledged write: the owner must still be the captured one and the stored value the asked one. */
    private fun confirm(edit: SettingsEditToken, value: SettingValue?): Outcome<EffectiveSetting> {
        if (closed.value) return SettingsFailureReason.Disposed.failed()
        if (fingerprint(edit.before.target) != edit.ownerFingerprint) return SettingsFailureReason.StaleTarget.failed()
        val confirmed = readNow(edit.before.id, edit.before.target)
        if (confirmed.value != value) return SettingsFailureReason.Conflict.failed()
        return Outcome.Ok(confirmed)
    }

    private fun checkEdit(edit: SettingsEditToken): SettingsFailureReason? {
        if (closed.value) return SettingsFailureReason.Disposed
        if (edit.issuer !== this || edit !in issued.load() || fingerprint(edit.before.target) != edit.ownerFingerprint) {
            return SettingsFailureReason.StaleTarget
        }
        val current = readNow(edit.before.id, edit.before.target)
        writableReason(current)?.let { return it }
        if (current.revision != edit.before.revision) return SettingsFailureReason.Conflict
        return null
    }

    private fun writableReason(value: EffectiveSetting): SettingsFailureReason? =
        if (value.state != SettingValueState.Known || !value.writable) value.reason ?: SettingsFailureReason.Unsupported else null

    private fun fingerprint(target: SettingsTarget): String? {
        if (target is SettingsTarget.Device) return DEVICE
        val profileId = target.profileId ?: return null
        if (consent.isClosed(profileId)) return null
        return owners.fingerprint(profileId)
    }

    private fun revision(target: SettingsTarget): Long =
        if (target is SettingsTarget.Device) {
            preferences.revision.value
        } else {
            owners.revision.value + consent.epoch.value + selectionRevision.value
        }

    private fun unavailable(id: String, target: SettingsTarget, reason: SettingsFailureReason): EffectiveSetting =
        EffectiveSetting(
            id = id,
            target = target,
            revision = revision(target),
            state = when (reason) {
                SettingsFailureReason.StaleTarget -> SettingValueState.Stale
                SettingsFailureReason.Unsupported -> SettingValueState.Unsupported
                else -> SettingValueState.Unknown
            },
            reason = reason,
        )

    private fun readNow(id: String, target: SettingsTarget): EffectiveSetting {
        if (closed.value) return unavailable(id, target, SettingsFailureReason.Disposed)
        return when (target) {
            SettingsTarget.Device -> readDevice(id)
            is SettingsTarget.Connection -> readConnection(id, target)
            else -> unavailable(id, target, SettingsFailureReason.Unsupported)
        }
    }

    private fun readDevice(id: String): EffectiveSetting {
        val target = SettingsTarget.Device
        val value = preferences.value.value
        val data: SettingValue? = when (id) {
            APPEARANCE -> text(value.appearance)
            THEME_PACK -> text(value.themePack)
            LOCALE -> value.locale.code?.let(SettingValue::Text)
            REASONING -> SettingValue.Flag(value.reasoningExpanded)
            TIMESTAMPS -> SettingValue.Flag(value.timestampsVisible)
            EFFECTS -> effectsValue(value)
            else -> return unavailable(id, target, SettingsFailureReason.Unsupported)
        }
        val saved = when (id) {
            EFFECTS -> DevicePreferenceKeys.EFFECTS_PROVENANCE.any(preferences::hasStored)
            else -> preferences.hasStored(DEVICE_KEYS.getValue(id))
        }
        return EffectiveSetting(
            id = id,
            target = target,
            value = data,
            revision = revision(target),
            origin = if (saved) SettingOriginKind.ExplicitValue else SettingOriginKind.DefaultValue,
            writable = true,
        )
    }

    private fun readConnection(id: String, target: SettingsTarget.Connection): EffectiveSetting {
        if (fingerprint(target) == null) return unavailable(id, target, SettingsFailureReason.StaleTarget)
        val profileId = target.profileId
        if (id == SHARE_READ_STATE) {
            return EffectiveSetting(
                id = id,
                target = target,
                value = SettingValue.Flag(consent.enabled(profileId)),
                origin = consent.origin(profileId),
                revision = revision(target),
                writable = true,
            )
        }
        val key = when (id) {
            MODEL -> MODEL_PREFIX
            AGENT -> AGENT_PREFIX
            VARIANT -> VARIANT_PREFIX
            else -> return unavailable(id, target, SettingsFailureReason.Unsupported)
        } + profileId.raw
        val stored = store.get(key)
        val value: SettingValue? = when (stored) {
            null -> if (id == MODEL) null else SettingValue.Text("")
            is PrefValue.Str -> if (id == MODEL) parseModel(stored.value) else SettingValue.Text(stored.value)
            else -> null
        }
        if (stored != null && value == null) return unavailable(id, target, SettingsFailureReason.InvalidValue)
        return EffectiveSetting(
            id = id,
            target = target,
            value = value,
            revision = revision(target),
            origin = if (stored != null) SettingOriginKind.ExplicitValue else SettingOriginKind.DefaultValue,
        )
    }

    private fun deviceChange(id: String, value: SettingValue?): PreferenceChange? {
        val text = (value as? SettingValue.Text)?.value
        val flag = (value as? SettingValue.Flag)?.value
        return when (id) {
            APPEARANCE -> named<AppearanceChoice>(text)?.let(PreferenceChange::Appearance)
            THEME_PACK -> named<ThemePack>(text)?.let(PreferenceChange::Theme)
            LOCALE -> when {
                value == null -> PreferenceChange.Locale(LocaleChoice.System)
                text != null -> LocaleChoice.entries.firstOrNull { it.code != null && it.code == text }?.let(PreferenceChange::Locale)
                else -> null
            }
            REASONING -> flag?.let(PreferenceChange::ReasoningExpanded)
            TIMESTAMPS -> flag?.let(PreferenceChange::TimestampsVisible)
            EFFECTS -> effectsChange(value)
            else -> null
        }
    }

    /**
     * The whole effects record. Its glow colours and speed are the app's own (their keys are never read), so only
     * `one` and `normal` can be confirmed; anything else is refused before writing (Flutter wrote it, then reported a
     * conflict).
     */
    private fun effectsChange(value: SettingValue?): PreferenceChange? {
        val fields = (value as? SettingValue.Record)?.fields ?: return null
        if (fields.keys != EFFECT_FIELDS) return null
        fun text(name: String) = (fields[name] as? SettingValue.Text)?.value
        val motion = named<MotionLevel>(text("motion")) ?: return null
        val glow = (fields["activityGlow"] as? SettingValue.Flag)?.value ?: return null
        val style = named<GlowStyle>(text("glowStyle")) ?: return null
        if (named<GlowColours>(text("glowColours")) != GlowColours.One) return null
        if (named<GlowSpeed>(text("glowSpeed")) != GlowSpeed.Normal) return null
        val current = preferences.value.value.effects
        return PreferenceChange.SetEffects(current.copy(motion = motion, activityGlow = glow, glowStyle = style))
    }

    private inline fun update(change: (PersistentSet<SettingsEditToken>) -> PersistentSet<SettingsEditToken>) {
        while (true) {
            val current = issued.load()
            if (issued.compareAndSet(current, change(current))) return
        }
    }

    public companion object {
        public const val APPEARANCE: String = "appearance"
        public const val THEME_PACK: String = "themePack"
        public const val LOCALE: String = "locale"
        public const val REASONING: String = "reasoningExpanded"
        public const val TIMESTAMPS: String = "timestampsVisible"
        public const val EFFECTS: String = "effects"
        public const val SHARE_READ_STATE: String = "shareReadState"
        public const val MODEL: String = "model"
        public const val AGENT: String = "agent"
        public const val VARIANT: String = "variant"

        private const val DEVICE = "device"
        private const val MODEL_PREFIX = "oc.model."
        private const val AGENT_PREFIX = "oc.agent."
        private const val VARIANT_PREFIX = "oc.variant."
        private val SELECTION_PREFIXES = listOf(MODEL_PREFIX, "oc.modelExplicit.", AGENT_PREFIX, VARIANT_PREFIX)
        private val EFFECT_FIELDS = setOf("motion", "activityGlow", "glowStyle", "glowColours", "glowSpeed")

        private val DEVICE_KEYS = mapOf(
            APPEARANCE to DevicePreferenceKeys.APPEARANCE,
            THEME_PACK to DevicePreferenceKeys.THEME_PACK,
            LOCALE to DevicePreferenceKeys.LOCALE,
            REASONING to DevicePreferenceKeys.REASONING,
            TIMESTAMPS to DevicePreferenceKeys.TIMESTAMPS,
        )

        private fun text(value: WireNamed): SettingValue = SettingValue.Text(value.wire)

        private inline fun <reified T> named(raw: String?): T? where T : Enum<T>, T : WireNamed =
            enumValues<T>().firstOrNull { it.wire == raw }

        /** The facade's effects record: the reply glow is one choice; colours and speed are the app's own. */
        internal fun effectsValue(value: DevicePreferences): SettingValue = SettingValue.Record(
            persistentMapOf(
                "motion" to text(value.effects.motion),
                "activityGlow" to SettingValue.Flag(value.effects.activityGlow),
                "glowStyle" to text(value.effects.glowStyle),
                "glowColours" to text(GlowColours.One),
                "glowSpeed" to text(GlowSpeed.Normal),
            ),
        )

        /** `provider|model` (Flutter `ProfileStore.modelFor`); anything else is unreadable. */
        private fun parseModel(raw: String): SettingValue? {
            if ('|' !in raw) return null
            val parts = raw.split('|')
            if (parts[0].isEmpty() || parts[1].isEmpty()) return null
            return SettingValue.Record(persistentMapOf("providerId" to SettingValue.Text(parts[0]), "modelId" to SettingValue.Text(parts[1])))
        }
    }
}
