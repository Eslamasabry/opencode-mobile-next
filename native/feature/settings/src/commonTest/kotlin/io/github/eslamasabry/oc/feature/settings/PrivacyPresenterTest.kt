package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.analytics.AnalyticsSink
import io.github.eslamasabry.oc.engine.analytics.Delivery
import io.github.eslamasabry.oc.engine.analytics.DetailedConsent
import io.github.eslamasabry.oc.engine.analytics.SendingState
import io.github.eslamasabry.oc.engine.analytics.SinkId
import io.github.eslamasabry.oc.engine.analytics.SinkKind
import io.github.eslamasabry.oc.engine.analytics.WireBatch
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Privacy rows and the "What we collect" page (plan 16 §5.8, plan 18 §6). */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyPresenterTest {
    private object RemoteSink : AnalyticsSink {
        override val id: SinkId = SinkId.Custom
        override val kind: SinkKind = SinkKind.Remote
        override val host: String = "collect.invalid"

        override suspend fun send(batch: WireBatch): Outcome<Delivery> = Outcome.Ok(Delivery(""))
    }

    private fun TestScope.presenter(analytics: FakeAnalytics) =
        SettingsPresenter(
            scope = backgroundScope,
            facts = MutableStateFlow(phoneFacts()),
            choices = FakeChoices(),
            openers = RecordingOpeners(),
            analytics = analytics,
        ).also { runCurrent() }

    @Test
    fun `B-SEC-191 with nothing sent the page says so and offers no switch`() =
        runTest {
            val p = presenter(FakeAnalytics())
            p.onRow(SettingsRowId.Privacy); runCurrent()
            p.onOpen(SettingsPage.WhatWeCollect); runCurrent()
            val collect = p.state.value.privacy!!.collect
            assertEquals(SendingState.NotSentInThisVersion, collect.sending)
            assertFalse(collect.detailedSwitchShown)
        }

    @Test
    fun `B-SEC-191 detailed usage is off until switched on, and the switch reaches the analytics engine`() =
        runTest {
            val analytics = FakeAnalytics(RemoteSink)
            val p = presenter(analytics)
            val before = p.state.value.privacy!!.collect
            assertTrue(before.detailedSwitchShown)
            assertEquals(DetailedConsent.NotAsked, before.detailed)
            assertTrue(before.detailedEvents.none { it.active })
            p.onDetailedUsage(true); runCurrent()
            assertEquals(listOf(true), analytics.asked)
            val after = p.state.value.privacy!!.collect
            assertEquals(DetailedConsent.Granted, after.detailed)
            assertTrue(after.detailedEvents.all { it.active })
        }

    @Test
    fun `B-SEC-191 a consent save that does not stick keeps the old answer and says so`() =
        runTest {
            val analytics = FakeAnalytics(RemoteSink).apply { refuseNext = true }
            val p = presenter(analytics)
            p.onDetailedUsage(true); runCurrent()
            assertEquals(DetailedConsent.NotAsked, p.state.value.privacy!!.collect.detailed)
            assertEquals(SettingsNote.ConsentNotSaved, p.state.value.note)
            assertFalse(p.state.value.privacy!!.saving)
        }

    @Test
    fun `plan 18 6 the page lists every event the schema can send, by tier`() =
        runTest {
            val collect = presenter(FakeAnalytics()).state.value.privacy!!.collect
            assertEquals(11, collect.basicEvents.size)
            assertEquals(5, collect.detailedEvents.size)
            assertTrue(collect.never.isNotEmpty())
        }
}
