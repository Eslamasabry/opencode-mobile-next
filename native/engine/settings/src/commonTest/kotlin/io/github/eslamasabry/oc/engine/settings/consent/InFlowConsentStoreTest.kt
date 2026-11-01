package io.github.eslamasabry.oc.engine.settings.consent

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InFlowConsentStoreTest {
    private val phone = pid("phone")
    private val other = pid("other")

    private fun TestScope.kv(extra: Map<String, Any> = emptyMap()) =
        TestKv(mapOf("oc.profiles" to profilesJson("phone", "other")) + extra, UnconfinedTestDispatcher(testScheduler))

    private fun store(kv: TestKv) = InFlowConsentStore(kv, kv.lanes)

    private fun Outcome<List<InFlowConsentKind>>.kinds(): List<InFlowConsentKind> = (this as Outcome.Ok).value

    @Test
    fun `B-ANDR-025 the first phone start claims once, remembers denial and isolates servers`() = runTest {
        val kv = kv()
        val consent = store(kv)
        val first = async { consent.phoneServerFirstStart(phone, batteryAlreadyExempt = false, makerHasAutoStartStep = true) }
        val second = async { consent.phoneServerFirstStart(phone, batteryAlreadyExempt = false, makerHasAutoStartStep = true) }
        assertEquals(
            listOf(InFlowConsentKind.BatteryExemption, InFlowConsentKind.MakerAutoStart),
            first.await().kinds(),
        )
        assertEquals(emptyList<InFlowConsentKind>(), second.await().kinds())
        consent.answer(phone, InFlowConsentKind.BatteryExemption, allow = false)
        consent.answer(phone, InFlowConsentKind.MakerAutoStart, allow = false)

        val restored = store(kv.restart(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(emptyList<InFlowConsentKind>(), restored.phoneServerFirstStart(phone, false, true).kinds())
        assertEquals(InFlowConsentExplanation.BatteryMayStopServer, restored.row(phone, InFlowConsentKind.BatteryExemption).explanation)
        assertEquals(InFlowConsentExplanation.MakerMayPreventRestart, restored.row(phone, InFlowConsentKind.MakerAutoStart).explanation)
        assertEquals(ConsentRead.Refused, restored.read(phone, ConsentTopic.BatteryExemption))

        // An exempt battery and a maker without an auto-start step ask nothing; a later change does not restart it.
        assertEquals(emptyList<InFlowConsentKind>(), restored.phoneServerFirstStart(other, batteryAlreadyExempt = true, makerHasAutoStartStep = false).kinds())
        assertEquals(emptyList<InFlowConsentKind>(), restored.phoneServerFirstStart(other, batteryAlreadyExempt = false, makerHasAutoStartStep = true).kinds())
        assertEquals(ConsentChoice.Unseen, restored.row(other, InFlowConsentKind.BatteryExemption).choice)
        assertEquals(ConsentRead.Unknown, restored.read(other, ConsentTopic.BatteryExemption))
    }

    @Test
    fun `B-ANDR-026 the needs-you preset survives restart, explains an interrupted ask and Settings revises it`() = runTest {
        val kv = kv()
        assertEquals(true, store(kv).requestNeedsYouPreset(phone).getOrNull())
        val reloadedKv = kv.restart(UnconfinedTestDispatcher(testScheduler))
        var consent = store(reloadedKv)
        assertEquals(false, consent.requestNeedsYouPreset(phone).getOrNull())
        assertEquals(InFlowConsentExplanation.Unfinished, consent.row(phone, InFlowConsentKind.NeedsYouNotifications).explanation)
        consent.answer(phone, InFlowConsentKind.NeedsYouNotifications, allow = false)
        assertEquals(InFlowConsentExplanation.NeedsYouAlertsOff, consent.row(phone, InFlowConsentKind.NeedsYouNotifications).explanation)
        assertIs<Outcome.Ok<Unit>>(consent.changeFromSettings(phone, InFlowConsentKind.NeedsYouNotifications, allow = true))

        val restartedKv = reloadedKv.restart(UnconfinedTestDispatcher(testScheduler))
        consent = store(restartedKv)
        assertEquals(ConsentChoice.Accepted, consent.row(phone, InFlowConsentKind.NeedsYouNotifications).choice)
        assertEquals(ConsentRead.Granted, consent.read(phone, ConsentTopic.NeedsYouNotifications))
        assertEquals(false, consent.requestNeedsYouPreset(phone).getOrNull())
        val twice = consent.answer(phone, InFlowConsentKind.NeedsYouNotifications, allow = true)
        assertEquals(Failure.Refused(RefusalReason.AlreadyAnswered), (twice as Outcome.Failed).failure)
        // Stored intent never rewrites the app's global notification rules.
        assertTrue(restartedKv.keys("oc.notify").isEmpty())
        assertEquals(
            """{"version":1,"firstStart":false,"choices":{"needsYouNotifications":"accepted"}}""",
            restartedKv.string("oc.inFlowConsent.phone"),
        )
    }

    @Test
    fun `B-SEC-093 deleting a profile removes its consent and leaves another server intact`() = runTest {
        val kv = kv()
        val consent = store(kv)
        consent.requestNeedsYouPreset(phone)
        consent.requestNeedsYouPreset(other)
        assertIs<Outcome.Ok<Unit>>(consent.forgetProfile(phone))
        assertNull(kv.get("oc.inFlowConsent.phone"))
        assertNotNull(kv.get("oc.inFlowConsent.other"))
        assertIs<Outcome.Failed>(consent.requestNeedsYouPreset(phone), "a stale writer cannot bring the key back")
        assertNull(kv.get("oc.inFlowConsent.phone"))
        // A profile saved again later with the same id starts from nothing.
        assertTrue(consent.reopenProfile(phone))
        assertEquals(true, consent.requestNeedsYouPreset(phone).getOrNull())
    }

    @Test
    fun `B-SEC-093 bad storage and refused writes never offer consent or record a grant`() = runTest {
        val broken = store(kv(mapOf("oc.inFlowConsent.phone" to "{broken")))
        assertFalse(broken.storageAvailable(phone))
        assertIs<Outcome.Failed>(broken.requestNeedsYouPreset(phone))
        assertIs<Outcome.Failed>(broken.phoneServerFirstStart(phone, false, true))
        assertEquals(ConsentRead.Unknown, broken.read(phone, ConsentTopic.NeedsYouNotifications))
        val badChoice = store(kv(mapOf("oc.inFlowConsent.phone" to """{"version":1,"firstStart":true,"choices":{"needsYouNotifications":"maybe"}}""")))
        assertFalse(badChoice.storageAvailable(phone))

        val refusingKv = kv()
        refusingKv.disk.refuse = { _, _ -> true }
        val consent = store(refusingKv)
        assertIs<Outcome.Failed>(consent.requestNeedsYouPreset(phone))
        assertFalse(consent.storageAvailable(phone))
        assertEquals(ConsentChoice.Unseen, consent.row(phone, InFlowConsentKind.NeedsYouNotifications).choice)
        assertIs<Outcome.Failed>(consent.requestNeedsYouPreset(phone))
        val restored = store(refusingKv.restart(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(ConsentChoice.Unseen, restored.row(phone, InFlowConsentKind.NeedsYouNotifications).choice)
    }
}
