package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.long
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.analytics.health.HealthExitMarks
import io.github.eslamasabry.oc.engine.analytics.health.Telemetry
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsent
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsentStore
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryReportStore
import io.github.eslamasabry.oc.feature.settings.presentation.HealthAnswers
import io.github.eslamasabry.oc.feature.settings.presentation.HealthCarryOver
import io.github.eslamasabry.oc.feature.settings.presentation.HealthConsentKeys
import io.github.eslamasabry.oc.feature.settings.presentation.HealthConsentSetting

/**
 * B-180 continuity: Flutter 2.1.5's answers and counters under Flutter's own keys in the adopted preferences
 * (`FlutterSharedPreferences.xml` in the same-package build), so an answer given in Flutter carries over, the report
 * code keeps counting, and a rollback to Flutter sees the native answers.
 *
 * - `oc.telemetry.usage` / `oc.telemetry.crashes`: the two answers (F: telemetry_provider.dart:28-31).
 * - `oc.telemetry.crashReports` (2.1.5's first single switch) and `oc.crashReports.offer`: read once to carry an old
 *   answer over, never written (F: `PreferencesTelemetryConsentStore.migrate`).
 * - `oc.telemetryConsentAsked`: set with a carried-over answer so the question is not asked again.
 * - `oc.telemetry.reportCounter` / `oc.telemetry.installCode`: the problem report code (F: setInt lands as a long).
 * - `oc.telemetry.lastExitAt`: the newest process end already reported (F: health_probe.dart `healthLastExitKey`).
 */
class HealthConsentStore(private val store: KeyValueStore, private val lane: WriterLane) :
    TelemetryConsentStore, TelemetryReportStore, HealthExitMarks {
    /** The stored answers, or those carried over from the old single answer when neither was ever saved. */
    fun read(): HealthAnswers =
        HealthCarryOver.read(
            store.bool(HealthConsentKeys.USAGE),
            store.bool(HealthConsentKeys.CRASHES),
            store.bool(HealthConsentKeys.OLD_SINGLE),
            store.bool(HealthConsentKeys.CRASH_OFFER),
        )

    fun stored(): TelemetryConsent = read().let { TelemetryConsent(it.usage, it.crashes) }

    override suspend fun write(consent: TelemetryConsent): Boolean =
        store.commit(lane) {
            putBool(HealthConsentKeys.USAGE, consent.usage)
            putBool(HealthConsentKeys.CRASHES, consent.crashes)
        } is Outcome.Ok

    /** Once (F: PreferencesTelemetryConsentStore.migrate): save a carried-over answer and mark the question asked. */
    suspend fun migrate() {
        val answers =
            HealthCarryOver.migrate(
                store.bool(HealthConsentKeys.USAGE),
                store.bool(HealthConsentKeys.CRASHES),
                store.bool(HealthConsentKeys.OLD_SINGLE),
                store.bool(HealthConsentKeys.CRASH_OFFER),
            ) ?: return
        store.commit(lane) {
            putBool(HealthConsentKeys.USAGE, answers.usage)
            putBool(HealthConsentKeys.CRASHES, answers.crashes)
            putBool(HealthConsentKeys.ASKED, true)
        }
    }

    override fun readCounter(): Int = store.long(REPORT_COUNTER)?.toInt() ?: 0

    override suspend fun writeCounter(value: Int) {
        store.commit(lane) { putLong(REPORT_COUNTER, value.toLong()) }
    }

    override fun readLocalId(): String? = store.string(INSTALL_CODE)

    override suspend fun writeLocalId(value: String) {
        store.commit(lane) { putString(INSTALL_CODE, value) }
    }

    override fun lastExitAtMs(): Long = store.long(LAST_EXIT_AT) ?: 0L

    override suspend fun setLastExitAtMs(atMs: Long) {
        store.commit(lane) { putLong(LAST_EXIT_AT, atMs) }
    }

    companion object {
        const val REPORT_COUNTER = "oc.telemetry.reportCounter"
        const val INSTALL_CODE = "oc.telemetry.installCode"
        const val LAST_EXIT_AT = "oc.telemetry.lastExitAt"
    }
}

/**
 * Settings › Privacy and data's two switches over the live [Telemetry]: a change applies at once (off stops collection
 * and clears what the vendor kept on the phone) and is stored under Flutter's keys.
 */
class HealthConsentPort(private val telemetry: Telemetry) : HealthConsentSetting {
    override val available: Boolean
        get() = telemetry.available

    override fun read(): HealthAnswers = telemetry.consent.let { HealthAnswers(it.usage, it.crashes) }

    override suspend fun write(answers: HealthAnswers): Boolean = telemetry.setConsent(answers.usage, answers.crashes)
}
