package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.analytics.Analytics
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollect
import kotlinx.coroutines.flow.StateFlow

/*
 * The ports the Settings pages talk through. Analytics consent is a real engine today (`:engine:analytics`) and gets a
 * one-line adapter below. Personal choices belong to `:engine:settings` `Preferences`, which does not compile on this
 * tip; the adapter is a field-for-field mapping (see [PersonalChoices]). Voice, notifications and updates have no engine
 * yet (S4-NOTIFY, S5-MODELS, the updater): their ports are the contract those engines implement.
 */

/**
 * Personal choices (`Device` owner, memory `settings-scope-model`): apply everywhere at once,
 * acknowledged values only (B-STATE-004/005). [value] never shows an optimistic write; a refused
 * [set] leaves it as it was.
 *
 * The `:engine:settings` adapter maps `Preferences.value` (`DevicePreferences`) onto
 * [PersonalPrefs] field for field (`AppearanceChoice` ↔ [AppearanceMode], `ThemePack` ↔
 * [io.github.eslamasabry.oc.ui.design.color.PackId] by storage name,
 * `effects.surfaceStyle`/`fineTune`/`motion`, `reasoningExpanded`, `timestampsVisible`) and each
 * [PersonalChange] onto one `PreferenceChange`; [PersonalChange.Style] becomes
 * `SetEffects(effects.copy(surfaceStyle = …, fineTune = FineTune.None))` (B-KIT-040: a new style
 * clears the fine-tune choices).
 */
public interface PersonalChoices {
    public val value: StateFlow<PersonalPrefs>

    public suspend fun set(change: PersonalChange): Outcome<PersonalPrefs>
}

/** The "What we collect" source and the DETAILED switch (plan 18 §6, plan 16 §5.8). */
public interface AnalyticsChoices {
    public val whatWeCollect: StateFlow<WhatWeCollect>

    public suspend fun setDetailedConsent(granted: Boolean): Outcome<Unit>
}

/** `:engine:analytics` as [AnalyticsChoices]. */
public class EngineAnalyticsChoices(private val analytics: Analytics) : AnalyticsChoices {
    override val whatWeCollect: StateFlow<WhatWeCollect>
        get() = analytics.whatWeCollect

    override suspend fun setDetailedConsent(granted: Boolean): Outcome<Unit> =
        analytics.setDetailedConsent(granted)
}

/** Voice typing and read-aloud facts (plan 07 I11; S5-MODELS owns the speech model engine). */
public interface VoiceSettings {
    public val facts: StateFlow<VoiceFacts>

    public suspend fun setReadAloud(on: Boolean): Outcome<Unit>
}

/** Notification choices shared by every server (plan 07 I05). */
public interface NotificationSettings {
    public val facts: StateFlow<NotificationFacts>

    public suspend fun set(change: NotificationChange): Outcome<Unit>

    public suspend fun sendTest(): Outcome<Unit>

    /**
     * Reads Android's own on/off again (the person may have changed it in Android's settings
     * meanwhile).
     */
    public fun refresh() {}
}

/** The in-app updater (plan 07 I20, plan 16 CR16-10). */
public interface UpdateSettings {
    public val facts: StateFlow<UpdateFacts>

    public suspend fun checkNow(): Outcome<Unit>

    public suspend fun setChecks(on: Boolean): Outcome<Unit>

    /**
     * Download (Available), cancel (Downloading) or install (Downloaded): the one main action of
     * the state.
     */
    public suspend fun act(): Outcome<Unit>
}

/**
 * Report a problem (plan 07 I18, B-SEC-041): the report is generated lines and already-redacted
 * entries only, never raw exception text, server addresses or credentials. Nothing leaves the phone
 * until the person copies or shares it.
 */
public interface DiagnosticsExport {
    /** The report exactly as it will be copied or shared. */
    public suspend fun report(): Outcome<String>

    /** The same preview with the person's in-memory description. */
    public suspend fun report(description: String): Outcome<String> = report()

    /** Drops the in-memory attachment when the report page closes. */
    public fun endReport() {}

    /** Opens the reviewed draft in the issue form; does not submit it. */
    public suspend fun send(description: String): Outcome<Unit> =
        Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Device())

    public suspend fun copy(text: String): Outcome<Unit>

    public suspend fun share(text: String): Outcome<Unit>
}

/**
 * The notification choices' Flutter keys (`NotificationPreferences`), shared by every server; a
 * migrated install keeps them (B-MIGR-005), and the notification engine reads the same values.
 */
public object NotificationKeys {
    public const val FINISHED: String = "oc.notify.finishedRuns"
    public const val REQUESTS: String = "oc.notify.requests"
    public const val WIFI_ONLY: String = "oc.notify.wifiOnly"
    public const val QUIET_START: String = "oc.notify.quietStart"
    public const val QUIET_END: String = "oc.notify.quietEnd"

    private const val DAY = 24 * 60

    /**
     * Quiet hours from the stored minutes; off unless both are a minute of the day (Flutter
     * `shared.minute`).
     */
    public fun quiet(start: Long?, end: Long?): QuietHours? =
        if (start != null && end != null && start in 0 until DAY && end in 0 until DAY)
            QuietHours(start.toInt(), end.toInt())
        else null
}
