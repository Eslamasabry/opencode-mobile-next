package io.github.eslamasabry.oc.engine.analytics.product

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane

/**
 * B-180 continuity: the app-wide answers Flutter 2.1.5 added, read and written under Flutter's own logical keys in the
 * adopted `FlutterSharedPreferences.xml`, so a person who answered in Flutter is never asked again and a rollback to
 * Flutter sees the same answers. None is per server profile: consent and crashes are the app's.
 */
public object TelemetryKeys {
    /** "Send reports to developer" (crash reports and usage analytics). Off unless turned on. */
    public const val CONSENT: String = "oc.telemetry.crashReports"

    /** The app version last seen at start-up (`2.1.5+70`), so a newer one counts as an installed update. */
    public const val LAST_VERSION: String = "oc.telemetry.lastVersion"

    /** Whether the one-time "Help improve OpenCode Mobile?" question was asked. */
    public const val CONSENT_ASKED: String = "oc.telemetryConsentAsked"

    /** "Offer crash reports"; on unless turned off. */
    public const val CRASH_OFFER: String = "oc.crashReports.offer"

    /** Whether this install ever saw a reply (`first_reply` is sent once). */
    public const val FIRST_REPLY_SEEN: String = "oc.telemetry.firstReplySeen"

    /** The setup funnel's memory across restarts: last step, attempts, start time (words and numbers only). */
    public const val SETUP_JOURNEY: String = "oc.telemetry.setupJourney"

    /** The time (epoch ms) of the newest crash already offered, so each crash is offered at most once. */
    public const val CRASH_LAST_OFFERED: String = "oc.crashReports.lastOffered"
}

/** [TelemetryKeys] over the one [KeyValueStore]; reads in memory, one commit per change on [lane]. */
public class TelemetryAnswers(private val store: KeyValueStore, private val lane: WriterLane) {
    /** The stored consent answer, or null when none was ever stored (Flutter or native). */
    public fun consent(): Boolean? = store.bool(TelemetryKeys.CONSENT)

    public suspend fun setConsent(granted: Boolean): Outcome<Unit> =
        store.commit(lane) { putBool(TelemetryKeys.CONSENT, granted) }

    public fun consentAsked(): Boolean = store.bool(TelemetryKeys.CONSENT_ASKED) ?: false

    public suspend fun markConsentAsked(): Outcome<Unit> =
        store.commit(lane) { putBool(TelemetryKeys.CONSENT_ASKED, true) }

    public fun lastVersion(): String? = store.string(TelemetryKeys.LAST_VERSION)

    public suspend fun setLastVersion(version: String): Outcome<Unit> =
        store.commit(lane) { putString(TelemetryKeys.LAST_VERSION, version) }

    public fun firstReplySeen(): Boolean = store.bool(TelemetryKeys.FIRST_REPLY_SEEN) ?: false

    public suspend fun markFirstReplySeen(): Outcome<Unit> =
        store.commit(lane) { putBool(TelemetryKeys.FIRST_REPLY_SEEN, true) }

    public fun setupJourney(): String? = store.string(TelemetryKeys.SETUP_JOURNEY)

    public suspend fun saveSetupJourney(encoded: String?): Outcome<Unit> =
        store.commit(lane) { if (encoded == null) remove(TelemetryKeys.SETUP_JOURNEY) else putString(TelemetryKeys.SETUP_JOURNEY, encoded) }

    public fun crashOfferEnabled(): Boolean = store.bool(TelemetryKeys.CRASH_OFFER) ?: true

    public suspend fun setCrashOfferEnabled(on: Boolean): Outcome<Unit> =
        store.commit(lane) { putBool(TelemetryKeys.CRASH_OFFER, on) }

    /** Flutter's `prefs.setInt` lands as a long on Android; an int-sized value is accepted too. */
    public fun crashLastOfferedMs(): Long =
        when (val value = store.get(TelemetryKeys.CRASH_LAST_OFFERED)) {
            is PrefValue.Int64 -> value.value
            else -> 0L
        }

    public suspend fun setCrashLastOfferedMs(atMs: Long): Outcome<Unit> =
        store.commit(lane) { putLong(TelemetryKeys.CRASH_LAST_OFFERED, atMs) }
}

/**
 * Which consent answer wins once the adopted store can be read. The reporter starts before the migration gate opens
 * with [early] (the app's own early copy, or the flavour default when there is none); the stored answer then takes
 * over, so Flutter's "yes" or "no" carries across the update. Null: nothing to change.
 */
public object ConsentCarryOver {
    public fun adopt(stored: Boolean?, early: Boolean): Boolean? = stored?.takeIf { it != early }
}
