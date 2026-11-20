package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.engine.analytics.health.healthKeyPatterns
import io.github.eslamasabry.oc.engine.analytics.health.healthKeyWords
import io.github.eslamasabry.oc.engine.analytics.health.healthPropertyPatterns
import io.github.eslamasabry.oc.engine.analytics.health.healthPropertyWords
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiField
import io.github.eslamasabry.oc.feature.settings.presentation.CollectGroup
import io.github.eslamasabry.oc.feature.settings.presentation.CollectItem
import io.github.eslamasabry.oc.feature.settings.presentation.HealthAnswers
import io.github.eslamasabry.oc.feature.settings.presentation.HealthCarryOver
import io.github.eslamasabry.oc.feature.settings.presentation.HealthConsentKeys
import io.github.eslamasabry.oc.feature.settings.presentation.HealthConsentSetting
import io.github.eslamasabry.oc.feature.settings.presentation.HealthContract
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** s28-app: Flutter 2.1.5's two stored answers and What we collect (docs/telemetry/health-and-diagnostics.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class S28HealthConsentTest {
    private class FakeHealth(var stored: HealthAnswers = HealthAnswers(), var refuse: Boolean = false) :
        HealthConsentSetting {
        val writes = mutableListOf<HealthAnswers>()

        override fun read(): HealthAnswers = stored

        override suspend fun write(answers: HealthAnswers): Boolean {
            writes += answers
            if (refuse) return false
            stored = answers
            return true
        }
    }

    private fun TestScope.presenter(health: FakeHealth) =
        SettingsPresenter(
            scope = backgroundScope,
            facts = MutableStateFlow(phoneFacts()),
            choices = FakeChoices(),
            openers = RecordingOpeners(),
            healthConsent = health,
        ).also { runCurrent() }

    @Test
    fun `B-SEC-200 the keys are Flutter 2-1-5's own`() {
        assertEquals("oc.telemetry.usage", HealthConsentKeys.USAGE)
        assertEquals("oc.telemetry.crashes", HealthConsentKeys.CRASHES)
        assertEquals("oc.telemetry.crashReports", HealthConsentKeys.OLD_SINGLE)
        assertEquals("oc.crashReports.offer", HealthConsentKeys.CRASH_OFFER)
    }

    @Test
    fun `B-SEC-201 an old single yes carries over to both, unless the crash offer was turned off`() {
        assertEquals(HealthAnswers(true, true), HealthCarryOver.read(null, null, oldYes = true, offer = null))
        assertEquals(HealthAnswers(true, false), HealthCarryOver.read(null, null, oldYes = true, offer = false))
        assertEquals(HealthAnswers(false, false), HealthCarryOver.read(null, null, oldYes = false, offer = true))
        assertEquals(HealthAnswers(false, false), HealthCarryOver.read(null, null, oldYes = null, offer = null))
        // Saved answers win over the old one.
        assertEquals(HealthAnswers(false, true), HealthCarryOver.read(null, true, oldYes = false, offer = false))
    }

    @Test
    fun `B-SEC-202 migration saves once, only when an old answer exists`() {
        assertEquals(HealthAnswers(true, true), HealthCarryOver.migrate(null, null, oldYes = true, offer = null))
        assertEquals(HealthAnswers(false, false), HealthCarryOver.migrate(null, null, oldYes = null, offer = false))
        assertNull(HealthCarryOver.migrate(null, null, oldYes = null, offer = null))
        assertNull(HealthCarryOver.migrate(true, null, oldYes = false, offer = null))
    }

    @Test
    fun `B-SEC-203 both switches start off and Usage and app health is stored`() = runTest {
        val health = FakeHealth()
        val p = presenter(health)
        p.onOpen(SettingsPage.Privacy); runCurrent()
        assertEquals(false, p.state.value.privacy!!.usage)
        assertEquals(false, p.state.value.privacy!!.crashReports)
        p.onUsage(true); runCurrent()
        assertEquals(listOf(HealthAnswers(usage = true, crashes = false)), health.writes)
        assertEquals(true, p.state.value.privacy!!.usage)
    }

    @Test
    fun `B-SEC-204 the crash reports switch is stored as the crashes answer`() = runTest {
        val health = FakeHealth(HealthAnswers(usage = true))
        val p = presenter(health)
        p.onCrashReports(true); runCurrent()
        assertEquals(HealthAnswers(usage = true, crashes = true), health.stored)
        assertEquals(true, p.state.value.privacy!!.crashReports)
    }

    @Test
    fun `B-SEC-205 an answer the store refused goes back and says so`() = runTest {
        val health = FakeHealth(refuse = true)
        val p = presenter(health)
        p.onUsage(true); runCurrent()
        assertEquals(false, p.state.value.privacy!!.usage)
        assertEquals(SettingsNote.ConsentNotSaved, p.state.value.note)
    }

    @Test
    fun `B-SEC-206 What we collect covers every event, property and key the code can send`() {
        // The live allow-lists, not a copy of the old 2.1.5 contract: a new event fails here until a line names it.
        val sendable =
            productSchema.keys +
                healthPropertyWords.keys + healthPropertyPatterns.keys +
                healthKeyWords.keys + healthKeyPatterns.keys +
                // The fields only our own server gets, from the code's one field list (umami-rich-301).
                UmamiField.wires
        val listed = HealthContract.contractEvents.values.flatten().toSet()
        assertEquals(emptySet(), sendable - listed)
        // And nothing listed that the code cannot send (the three crash-side lines are not events).
        assertEquals(emptySet(), listed - sendable - setOf("crash", "breadcrumbs", "problem_report", "report_kind"))
        assertEquals(CollectItem.entries.toSet(), HealthContract.items.values.flatten().toSet())
        assertEquals(CollectGroup.entries.toSet(), HealthContract.items.keys)
    }
}
