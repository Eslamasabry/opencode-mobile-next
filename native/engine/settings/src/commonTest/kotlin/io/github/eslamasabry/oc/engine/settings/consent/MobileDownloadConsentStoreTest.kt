package io.github.eslamasabry.oc.engine.settings.consent

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class MobileDownloadConsentStoreTest {
    private val mobile = NetworkRoute(RouteStatus.Online, RouteTransport.Mobile, metered = true, connected = true)
    private val wifi = NetworkRoute(RouteStatus.Online, RouteTransport.Wifi, metered = true, connected = true)
    private val large = DownloadSize.exact(50_000_001)
    private val phone = pid("phone")
    private val other = pid("other")
    private fun probe(route: NetworkRoute) = NetworkRouteProbe { route.ok() }

    private fun TestScope.kv(extra: Map<String, Any> = emptyMap(), vararg ids: String = arrayOf("phone", "other")) =
        TestKv(mapOf("oc.profiles" to profilesJson(*ids)) + extra, UnconfinedTestDispatcher(testScheduler))

    private fun store(kv: TestKv) = MobileDownloadConsentStore(kv, kv.lanes)

    @Test
    fun `B-SEC-013 eng-5 revocation withdraws the grant before a refused save`() = runTest {
        val kv = kv()
        val gate = store(kv)
        gate.changeFromSettings(phone, allow = true)
        val before = gate.revision.value
        val hold = kv.hold("oc.mobileDownloadConsent.phone")
        kv.disk.refuse = { key, _ -> key == "oc.mobileDownloadConsent.phone" }
        val revoke = async { gate.changeFromSettings(phone, allow = false) }
        hold.entered.await()
        assertFalse(gate.read(phone, ConsentTopic.MobileDownload).granted)
        assertTrue(gate.revision.value > before)
        hold.release.complete(Unit)
        assertIs<Outcome.Failed>(revoke.await())
        assertFalse(gate.read(phone, ConsentTopic.MobileDownload).granted)
        assertFalse(gate.storageAvailable(phone))
    }

    @Test
    fun `B-STATE-030 the threshold is strict and only an explicit mobile route asks`() = runTest {
        val gate = store(kv())
        var calls = 0
        for (bytes in listOf(0L, 49_999_999L, 50_000_000L)) {
            val result = gate.run(phone, DownloadSize.exact(bytes), probe(mobile)) { (++calls).ok() }
            assertEquals(MobileDownloadReason.BelowThreshold, result.decision.reason)
        }
        val onWifi = gate.run(phone, DownloadSize.Unknown, probe(wifi)) { (++calls).ok() }
        assertEquals(MobileDownloadReason.NonMobile, onWifi.decision.reason)
        val onMobile = gate.run(phone, large, probe(mobile)) { (++calls).ok() }
        assertEquals(MobileDownloadDecisionKind.NeedsConsent, onMobile.decision.kind)
        assertNull(onMobile.value)
        assertEquals(4, calls)
    }

    @Test
    fun `B-STATE-030 unknown, VPN, offline and failed probes never run or claim`() = runTest {
        val gate = store(kv())
        for (route in listOf(
            NetworkRoute.Unknown,
            NetworkRoute(RouteStatus.Online, RouteTransport.Unknown, metered = true, connected = true), // a VPN
            NetworkRoute(RouteStatus.Offline, RouteTransport.Mobile, metered = true, connected = false),
        )) {
            val result = gate.run<Unit>(phone, large, probe(route)) { fail("must not start a download") }
            assertFalse(result.decision.allowed)
        }
        val failed = gate.request(phone, large) { Outcome.Failed(Failure.Device()) }
        assertEquals(MobileDownloadReason.NetworkUnknown, failed.reason)
        val thrown = gate.request(phone, large) { throw IllegalStateException("sensitive native details") }
        assertEquals(MobileDownloadReason.NetworkUnknown, thrown.reason)
        assertEquals(ConsentChoice.Unseen, gate.choice(phone))
    }

    @Test
    fun `B-STATE-030 size provenance fails closed and a sufficient lower bound can offer`() = runTest {
        val gate = store(kv())
        for (size in listOf(DownloadSize.Unknown, DownloadSize.estimate(999_999_999), DownloadSize.lowerBound(50_000_000))) {
            val result = gate.run<Unit>(phone, size, probe(mobile)) { fail("must not use an incomplete size") }
            assertEquals(MobileDownloadReason.SizeUnknown, result.decision.reason)
        }
        assertEquals(MobileDownloadDecisionKind.NeedsConsent, gate.request(phone, DownloadSize.lowerBound(50_000_001), probe(mobile)).kind)
        assertIs<Outcome.Ok<Unit>>(gate.answer(phone, allow = true))
        val ran = gate.run(phone, DownloadSize.lowerBound(50_000_001), probe(mobile)) { "downloaded".ok() }
        assertEquals(Outcome.Ok("downloaded"), ran.value)
        assertEquals(MobileDownloadReason.SizeUnknown, gate.request(phone, DownloadSize.Unknown, probe(mobile)).reason)
    }

    @Test
    fun `B-STATE-031 concurrent claims persist once and denial and interruption survive reload`() = runTest {
        val kv = kv()
        val gate = store(kv)
        val claims = (1..4).map { async { gate.request(phone, large, probe(mobile)) } }.awaitAll()
        assertEquals(1, claims.count { it.kind == MobileDownloadDecisionKind.NeedsConsent })
        assertTrue(kv.string("oc.mobileDownloadConsent.phone")!!.contains("offered"))
        assertEquals("""{"version":1,"choice":"offered"}""", kv.string("oc.mobileDownloadConsent.phone"))

        val interrupted = store(kv.restart(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(MobileDownloadReason.Unfinished, interrupted.request(phone, large, probe(mobile)).reason)

        assertIs<Outcome.Ok<Unit>>(gate.answer(phone, allow = false))
        val restoredKv = kv.restart(UnconfinedTestDispatcher(testScheduler))
        val restored = store(restoredKv)
        assertEquals(MobileDownloadReason.Declined, restored.request(phone, large, probe(mobile)).reason)
        assertEquals(ConsentRead.Refused, restored.read(phone, ConsentTopic.MobileDownload))
        assertEquals(ConsentChoice.Unseen, restored.choice(other))
        assertEquals(ConsentRead.Unknown, restored.read(other, ConsentTopic.MobileDownload))

        assertIs<Outcome.Ok<Unit>>(restored.changeFromSettings(phone, allow = true))
        assertEquals(Outcome.Ok(42), restored.run(phone, large, probe(mobile)) { 42.ok() }.value)
        assertEquals(ConsentRead.Granted, restored.read(phone, ConsentTopic.MobileDownload))
    }

    @Test
    fun `B-STATE-031 an accepted retry refreshes the route and a queued revocation precedes the next start`() = runTest {
        val gate = store(kv())
        gate.changeFromSettings(phone, allow = true)
        val revoked = async { gate.changeFromSettings(phone, allow = false) }
        val blocked = async { gate.run<Unit>(phone, large, probe(mobile)) { fail("consent was revoked before start") } }
        assertIs<Outcome.Ok<Unit>>(revoked.await())
        assertEquals(MobileDownloadReason.Declined, blocked.await().decision.reason)
        gate.changeFromSettings(phone, allow = true)
        val unknown = gate.run<Unit>(phone, large, probe(NetworkRoute.Unknown)) { fail("the route must be refreshed") }
        assertEquals(MobileDownloadReason.NetworkUnknown, unknown.decision.reason)
    }

    @Test
    fun `B-STATE-031 forgetting a profile rejects stale writers without waiting for the transfer`() = runTest {
        val kv = kv()
        val gate = store(kv)
        gate.changeFromSettings(phone, allow = true)
        val started = CompletableDeferred<Unit>()
        val transfer = CompletableDeferred<String>()
        val running = async {
            gate.run(phone, large, probe(mobile)) {
                started.complete(Unit)
                transfer.await().ok()
            }
        }
        started.await()
        assertIs<Outcome.Ok<Unit>>(gate.forgetProfile(phone))
        assertNull(kv.get("oc.mobileDownloadConsent.phone"))
        assertEquals(Failure.Refused(RefusalReason.ProfileClosing), (gate.changeFromSettings(phone, allow = true) as Outcome.Failed).failure)
        val closed = gate.run<Unit>(phone, large, probe(mobile)) { fail("a closed profile must not run") }
        assertEquals(MobileDownloadReason.Closed, closed.decision.reason)
        assertNull(kv.get("oc.mobileDownloadConsent.phone"))
        transfer.complete("finished")
        assertEquals(Outcome.Ok("finished"), running.await().value)
        assertEquals(ConsentRead.Unknown, gate.read(phone, ConsentTopic.MobileDownload))
        assertIs<Outcome.Ok<Unit>>(gate.forgetProfile(phone), "idempotent")
    }

    @Test
    fun `B-STATE-031 closing during a probe can neither prompt nor start later`() = runTest {
        val kv = kv()
        val gate = store(kv)
        val route = CompletableDeferred<NetworkRoute>()
        val entered = CompletableDeferred<Unit>()
        val pending = async {
            gate.run<Unit>(phone, large, { entered.complete(Unit); route.await().ok() }) { fail("closed while awaiting the probe") }
        }
        entered.await()
        gate.closeProfile(phone)
        route.complete(mobile)
        assertEquals(MobileDownloadReason.Closed, pending.await().decision.reason)
        assertNull(kv.get("oc.mobileDownloadConsent.phone"), "no claim was written")
    }

    @Test
    fun `B-STATE-031 corrupt and refused storage never authorises or returns a prompt`() = runTest {
        val corrupt = store(kv(mapOf("oc.mobileDownloadConsent.phone" to "broken")))
        assertFalse(corrupt.storageAvailable(phone))
        assertNull(corrupt.choice(phone))
        val result = corrupt.run<Unit>(phone, large, probe(mobile)) { fail("corrupt storage must not run") }
        assertEquals(MobileDownloadReason.StorageUnavailable, result.decision.reason)
        assertIs<Outcome.Failed>(corrupt.changeFromSettings(phone, allow = true))
        assertEquals(ConsentRead.Unknown, corrupt.read(phone, ConsentTopic.MobileDownload))

        val wrongShape = store(kv(mapOf("oc.mobileDownloadConsent.phone" to """{"version":2,"choice":"accepted"}""")))
        assertEquals(ConsentRead.Unknown, wrongShape.read(phone, ConsentTopic.MobileDownload))

        val refusingKv = kv()
        refusingKv.disk.refuse = { _, _ -> true }
        val refusing = store(refusingKv)
        val refused = refusing.run<Unit>(phone, large, probe(mobile)) { fail("a failed durable claim must not run") }
        assertEquals(MobileDownloadReason.StorageUnavailable, refused.decision.reason)
        assertFalse(refusing.storageAvailable(phone))
        assertEquals(ConsentChoice.Unseen, refusing.choice(phone))
        val failure = (refusing.changeFromSettings(phone, allow = true) as Outcome.Failed).failure
        assertIs<Failure.StorageRefused>(failure)
    }

    @Test
    fun `B-SEC-013 a refused removal reopens consent with the stored answer`() = runTest {
        val kv = kv(ids = arrayOf("retained"))
        val retained = pid("retained")
        val consent = store(kv)
        consent.changeFromSettings(retained, allow = false)
        kv.disk.refuse = { key, value -> key == "oc.mobileDownloadConsent.retained" && value == null }
        val removal = consent.forgetProfile(retained)
        assertIs<Failure.StorageRefused>((removal as Outcome.Failed).failure)
        assertNull(consent.choice(retained), "closed while the deletion is undecided")
        // The deletion aborted with the profile kept: :engine:profiles reopens it.
        assertTrue(consent.reopenProfile(retained))
        assertEquals(ConsentChoice.Denied, consent.choice(retained))
        assertIs<Outcome.Ok<Unit>>(consent.changeFromSettings(retained, allow = true))
    }

    @Test
    fun `B-SEC-013 only a completed abort reopens a retained profile`() = runTest {
        val kv = kv(ids = arrayOf("retained"))
        val retained = pid("retained")
        val consent = store(kv)
        consent.changeFromSettings(retained, allow = false)
        consent.closeProfile(retained)
        assertTrue(consent.reopenProfile(retained))
        assertEquals(ConsentChoice.Denied, consent.choice(retained))

        consent.closeProfile(retained)
        kv.commit(kv.lanes.app) { putString("oc.profiles", "[]") }
        assertFalse(consent.reopenProfile(retained))
        assertIs<Outcome.Failed>(consent.changeFromSettings(retained, allow = true))
    }

    @Test
    fun `B-SEC-013 a new store cannot resurrect a removed profile key`() = runTest {
        val kv = kv(ids = arrayOf("removed"))
        val removed = pid("removed")
        val original = store(kv)
        original.changeFromSettings(removed, allow = true)
        assertIs<Outcome.Ok<Unit>>(original.forgetProfile(removed))
        kv.commit(kv.lanes.app) { putString("oc.profiles", "[]") }

        val reopened = store(kv)
        val late = reopened.changeFromSettings(removed, allow = true)
        assertEquals(Failure.Refused(RefusalReason.ProfileClosing), (late as Outcome.Failed).failure)
        assertIs<Outcome.Failed>(original.changeFromSettings(removed, allow = true))
        assertNull(kv.get("oc.mobileDownloadConsent.removed"))
    }

    @Test
    fun `B-SEC-013 deletion closes new admissions before draining a probe`() = runTest {
        val kv = kv(ids = arrayOf("closing"))
        val id = pid("closing")
        val consent = store(kv)
        val route = CompletableDeferred<NetworkRoute>()
        val entered = CompletableDeferred<Unit>()
        val pending = async { consent.request(id, DownloadSize.exact(60_000_000)) { entered.complete(Unit); route.await().ok() } }
        entered.await()
        val closing = async { consent.forgetProfile(id) }
        runCurrent()
        assertFalse(closing.isCompleted, "drains the pending probe first")
        assertFalse(consent.storageAvailable(id))
        assertNull(consent.choice(id), "no new admission while the probe drains")
        route.complete(mobile)
        assertEquals(MobileDownloadReason.Closed, pending.await().reason)
        assertIs<Outcome.Ok<Unit>>(closing.await())
        assertNull(kv.get("oc.mobileDownloadConsent.closing"))
    }

    @Test
    fun `B-SEC-013 absent profiles can neither load nor write`() = runTest {
        val kv = kv(ids = arrayOf("present"))
        val consent = store(kv)
        assertNull(consent.choice(pid("missing")))
        assertIs<Outcome.Failed>(consent.changeFromSettings(pid("missing"), allow = true))
        assertEquals(ConsentChoice.Unseen, consent.choice(pid("present")))
        kv.commit(kv.lanes.app) { putString("oc.profiles", "[]") }
        assertIs<Outcome.Failed>(consent.changeFromSettings(pid("present"), allow = true))
        assertNull(kv.get("oc.mobileDownloadConsent.present"))
        assertNull(consent.choice(pid("bad id!")))
    }
}
