package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.feature.settings.presentation.HealthAnswers
import io.github.eslamasabry.oc.feature.settings.presentation.HealthCarryOver
import io.github.eslamasabry.oc.feature.settings.presentation.HealthConsentKeys
import io.github.eslamasabry.oc.feature.settings.presentation.HealthConsentSetting

/**
 * s28-app (story 9): Flutter 2.1.5's two answers, `usage` and `crashes`, under Flutter's own keys in the adopted
 * preferences, so an answer given in 2.1.5 carries over and a rollback sees the native one. Only the stored consent:
 * the usage events themselves come with the native analytics wave, which must send only while [read] says `usage`.
 */
class HealthConsentStore(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    override val available: Boolean,
) : HealthConsentSetting {
    override fun read(): HealthAnswers =
        HealthCarryOver.read(
            store.bool(HealthConsentKeys.USAGE),
            store.bool(HealthConsentKeys.CRASHES),
            store.bool(HealthConsentKeys.OLD_SINGLE),
            store.bool(HealthConsentKeys.CRASH_OFFER),
        )

    /** The saved crashes answer, or null when only the old single key (or nothing) says anything. */
    fun storedCrashes(): Boolean? = store.bool(HealthConsentKeys.CRASHES)

    override suspend fun write(answers: HealthAnswers): Boolean =
        store.commit(lane) {
            putBool(HealthConsentKeys.USAGE, answers.usage)
            putBool(HealthConsentKeys.CRASHES, answers.crashes)
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

    /** A crash-report consent change from anywhere (the switch, the first-start question) lands under `crashes`. */
    suspend fun mirrorCrashes(on: Boolean) {
        val now = read()
        if (now.crashes != on || storedCrashes() == null) write(now.copy(crashes = on))
    }
}
