package io.github.eslamasabry.oc.engine.lifecycle

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of test/lifecycle_report_test.dart. */
class LifecycleReportTest {
    private val stoppedAt = 1_790_000_000_000L
    private val forceStop =
        AppExitRecord(AndroidExitReason.USER_REQUESTED, stoppedAt, description = "password=fake-native-secret")

    private inner class Linux : PhoneLinuxStatusSource {
        var installed = true
        var running = true
        var teamRunning = false
        var reads = 0
        var fails = false

        override suspend fun status(): Outcome<PhoneLinuxSnapshot> {
            reads++
            if (fails) return Failure.Unexpected().failed()
            return PhoneLinuxSnapshot(installed, running, if (teamRunning) listOf("aiteam") else emptyList()).ok()
        }
    }

    private inner class Bridge(var report: AppLaunchReport) : LaunchHistorySource {
        var reads = 0
        var fails = false

        override suspend fun launchReport(): Outcome<AppLaunchReport> {
            reads++
            return if (fails) Failure.Unexpected().failed() else report.ok()
        }
    }

    private val linux = Linux()
    private val bridge = Bridge(AppLaunchReport(forceStop, persistentListOf("server")))
    private val teamRequests = mutableListOf<String>()
    private var supervisorHealthy = true
    private var cityHealthy = true
    private var probes = 0
    private var password = "fake-serve-password"
    private var health: suspend () -> Boolean = { true }
    private val team =
        HttpPhoneTeamHealth({ path ->
            teamRequests += path
            val healthy = if (path == "/health") supervisorHealthy else cityHealthy
            if (healthy) """{"status":"ok"}""" else null
        })
    private val probe =
        PhoneServerHealthProbe { baseUrl, _, given ->
            probes++
            assertEquals(PhoneOwnServers.IN_APP_URL, baseUrl)
            assertTrue(given == password) // a boolean, so a failure never prints the credential
            health()
        }
    private val controller = LifecycleReportController(linux, bridge, probe, team)

    private fun profile(
        url: String = PhoneOwnServers.IN_APP_URL,
        pw: String = password,
        reentry: Boolean = false,
        team: Boolean = false,
    ) = LifecycleProfile("phone", url, password = pw, requiresPasswordReentry = reentry, teamConfigured = team)

    private val value get() = controller.value.value

    private fun TestScope.pendingHealth(): CompletableDeferred<Boolean> {
        val pending = CompletableDeferred<Boolean>()
        health = { pending.await() }
        return pending
    }

    @Test
    fun `B-STATE-025 exit time is available but recovery waits for an authenticated health answer`() = runTest {
        val pending = pendingHealth()
        val refresh = async { controller.refresh(profile()) }
        runCurrent()
        assertEquals(LifecycleReadiness.Checking, value.readiness)
        assertFalse(value.everythingBack)
        pending.complete(true)
        refresh.await()
        assertEquals(AppExitKind.ForceStop, value.exitKind)
        assertEquals(stoppedAt, value.stoppedAtMillis)
        assertTrue(value.showNotice)
        assertTrue(value.everythingBack)
        assertFalse("fake-native-secret" in value.toString())
    }

    @Test
    fun `B-STATE-025 a process without an answering server never counts as recovered`() = runTest {
        health = { false }
        controller.refresh(profile())
        assertEquals(LifecycleReadiness.Unreachable, value.readiness)
        assertFalse(value.everythingBack)
    }

    @Test
    fun `B-STATE-025 missing credentials do not probe or claim recovery`() = runTest {
        controller.refresh(profile(pw = ""))
        assertEquals(0, probes)
        assertEquals(LifecycleReadiness.Unreachable, value.readiness)
        assertFalse(value.everythingBack)
        controller.refresh(profile(reentry = true))
        assertEquals(0, probes)
        assertFalse(value.everythingBack)
    }

    @Test
    fun `B-STATE-025 a crash is attributed to the app rather than a phone setting`() = runTest {
        bridge.report = AppLaunchReport(AppExitRecord(AndroidExitReason.CRASH, stoppedAt), persistentListOf("server"))
        controller.refresh(profile())
        assertEquals(AppExitKind.Crash, value.exitKind)
        assertTrue(value.everythingBack)
    }

    @Test
    fun `B-STATE-025 stopped and uninstalled servers are distinct and never probed`() = runTest {
        linux.running = false
        controller.refresh(profile())
        assertEquals(LifecycleReadiness.Stopped, value.readiness)
        linux.installed = false
        controller.refresh(profile())
        assertEquals(LifecycleReadiness.Unavailable, value.readiness)
        assertEquals(0, probes)
    }

    @Test
    fun `B-STATE-025 remote, deleted and Termux profiles do not reach native or HTTP`() = runTest {
        for (candidate in listOf(null, profile(url = "https://example.test"), profile(url = PhoneOwnServers.TERMUX_URL))) {
            controller.refresh(candidate)
            assertEquals(LifecycleReadiness.Unavailable, value.readiness)
        }
        assertEquals(0, probes)
        assertEquals(0, linux.reads)
        assertEquals(0, bridge.reads)
    }

    @Test
    fun `B-STATE-025 team recovery needs its process, supervisor and city health`() = runTest {
        bridge.report = AppLaunchReport(forceStop, persistentListOf("server", "aiteam"))
        controller.refresh(profile(team = true))
        assertEquals(LifecycleReadiness.Partial, value.readiness)
        assertTrue(teamRequests.isEmpty())
        linux.teamRunning = true
        supervisorHealthy = false
        controller.refresh(profile(team = true))
        assertFalse(value.everythingBack)
        assertEquals(listOf("/health"), teamRequests)
        supervisorHealthy = true
        cityHealthy = false
        controller.refresh(profile(team = true))
        assertEquals(LifecycleReadiness.Partial, value.readiness)
        cityHealthy = true
        controller.refresh(profile(team = true))
        assertTrue(value.everythingBack)
        assertEquals("/v0/city/phone/health", teamRequests.last())
        assertEquals(1, bridge.reads)
    }

    @Test
    fun `B-STATE-025 a removed team configuration is not silently recovered`() = runTest {
        bridge.report = AppLaunchReport(forceStop, persistentListOf("server", "aiteam"))
        linux.teamRunning = true
        controller.refresh(profile(team = false))
        assertEquals(LifecycleReadiness.Partial, value.readiness)
        assertTrue(teamRequests.isEmpty())
    }

    @Test
    fun `B-STATE-025 unknown historical services prevent an everything-back claim`() = runTest {
        bridge.report = AppLaunchReport(forceStop, persistentListOf("server", "future-service"))
        controller.refresh(profile())
        assertEquals(LifecycleReadiness.Partial, value.readiness)
        assertFalse(value.everythingBack)
    }

    @Test
    fun `B-STATE-025 normal launch, update and no previously running services show no notice`() = runTest {
        val reports =
            listOf(
                AppLaunchReport(previousServices = persistentListOf("server")),
                AppLaunchReport(AppExitRecord(AndroidExitReason.PACKAGE_UPDATED, stoppedAt), persistentListOf("server")),
                AppLaunchReport(forceStop),
            )
        for (report in reports) {
            val other = LifecycleReportController(linux, Bridge(report), { _, _, _ -> true }, team)
            other.refresh(profile())
            assertEquals(LifecycleReadiness.Ready, other.value.value.readiness)
            assertFalse(other.value.value.showNotice)
            assertFalse(other.value.value.everythingBack)
            other.dispose()
        }
    }

    @Test
    fun `B-STATE-027 invalidation removes success and rejects an in-flight health result`() = runTest {
        controller.refresh(profile())
        assertTrue(value.everythingBack)
        val pending = pendingHealth()
        val refresh = async { controller.refresh(profile()) }
        runCurrent()
        controller.invalidate()
        assertEquals(LifecycleReadiness.Unchecked, value.readiness)
        pending.complete(true)
        refresh.await()
        assertFalse(value.everythingBack)
        assertEquals(LifecycleReadiness.Unchecked, value.readiness)
    }

    @Test
    fun `B-STATE-027 switching profiles supersedes an older refresh`() = runTest {
        val pending = pendingHealth()
        val old = async { controller.refresh(profile()) }
        runCurrent()
        controller.refresh(null)
        pending.complete(true)
        old.await()
        assertEquals(LifecycleReadiness.Unavailable, value.readiness)
        assertFalse(value.everythingBack)
    }

    @Test
    fun `B-STATE-027 dismissal survives refresh without writing preferences`() = runTest {
        controller.refresh(profile())
        controller.dismiss()
        controller.refresh(profile())
        assertEquals(LifecycleReadiness.Ready, value.readiness)
        assertFalse(value.showNotice)
        assertEquals(1, bridge.reads)
    }

    @Test
    fun `B-STATE-027 native failures remain unknown and a later refresh can recover`() = runTest {
        bridge.fails = true
        controller.refresh(profile())
        assertEquals(LifecycleReadiness.Unavailable, value.readiness)
        assertFalse(value.everythingBack)
        bridge.fails = false
        controller.refresh(profile())
        assertTrue(value.everythingBack)
        assertEquals(2, bridge.reads)
        linux.fails = true
        controller.refresh(profile())
        assertEquals(LifecycleReadiness.Unavailable, value.readiness)
    }

    @Test
    fun `B-STATE-027 disposal prevents publication from an in-flight check`() = runTest {
        val pending = CompletableDeferred<Boolean>()
        val other = LifecycleReportController(linux, bridge, { _, _, _ -> pending.await() }, team)
        val published = mutableListOf<LifecycleReport>()
        val watch = async { other.value.collect { published += it } }
        val refresh = async { other.refresh(profile()) }
        runCurrent()
        other.dispose()
        val before = published.size
        pending.complete(true)
        refresh.await()
        runCurrent()
        assertEquals(before, published.size)
        assertFalse(other.value.value.everythingBack)
        watch.cancel()
    }

    @Test
    fun `B-STATE-025 exit classification names force stop, update, memory, crash and kills`() {
        assertEquals(AppExitKind.Update, classifyAppExit(AppExitRecord(AndroidExitReason.USER_REQUESTED, 1, subReason = 25)))
        assertEquals(AppExitKind.ForceStop, classifyAppExit(AppExitRecord(AndroidExitReason.OTHER, 1, subReason = 21)))
        assertEquals(AppExitKind.LowMemory, classifyAppExit(AppExitRecord(AndroidExitReason.SIGNALED, 1, subReason = 16)))
        assertEquals(AppExitKind.Crash, classifyAppExit(AppExitRecord(AndroidExitReason.ANR, 1)))
        assertEquals(AppExitKind.Normal, classifyAppExit(AppExitRecord(AndroidExitReason.EXIT_SELF, 1)))
        assertEquals(AppExitKind.Killed, classifyAppExit(AppExitRecord(AndroidExitReason.PERMISSION_CHANGE, 1)))
        assertFalse(AppExitKind.Update.notable)
        assertTrue(AppExitKind.Killed.notable)
    }
}
