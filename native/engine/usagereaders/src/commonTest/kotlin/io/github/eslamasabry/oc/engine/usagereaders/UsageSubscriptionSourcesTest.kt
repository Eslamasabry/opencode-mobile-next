package io.github.eslamasabry.oc.engine.usagereaders

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val NOW = COMPUTER_NOW
private const val HOUR = 3_600_000L

class UsageSubscriptionSourcesTest {
    private fun context(lastReading: ((String, String) -> UsageReading?)? = null) =
        UsageReadContext(nowMs = NOW, force = true, lastReading = lastReading)

    private class Account(
        val account: AgentAccount,
        val buckets: List<AccountRateBucket> = emptyList(),
        val fail: Failure? = null,
        val throws: Boolean = false,
    ) : AgentAccountGateway {
        var closed = 0

        override fun openAccountSession(): AgentAccountSession = object : AgentAccountSession {
            override suspend fun read(): Outcome<AgentAccount> {
                if (throws) error("private")
                return fail?.let { Outcome.Failed(it) } ?: account.ok()
            }

            override suspend fun rateLimits(): Outcome<List<AccountRateBucket>> = buckets.ok()

            override suspend fun close() {
                closed++
            }
        }
    }

    private fun sourceFor(gateway: Account) = AgentAccountSource { OpenAgentAccount("codex1", gateway) }

    // ---- B-AGENT-312 Codex account

    @Test
    fun `B-AGENT-312 reads the 5-hour and weekly account limits and hashes the email`() = runTest {
        val gateway = Account(
            AgentAccount(type = "chatgpt", email = "sam@example.com", plan = "Plus", requiresSignIn = false),
            buckets = listOf(
                AccountRateBucket(
                    null,
                    AccountRateWindow(37, 300, NOW + HOUR),
                    AccountRateWindow(12, 10_080, NOW + 48 * HOUR),
                ),
            ),
        )
        val r = sourceFor(gateway).read(context()).readings.single()
        assertEquals(UsageReadingStatus.Ok, r.status)
        assertEquals("plus", r.plan)
        assertEquals(listOf(UsageWindowKind.FiveHour, UsageWindowKind.Weekly), r.windows.map { it.kind })
        val ref = r.accountRef.orEmpty()
        assertTrue(Regex("^[a-f0-9]{64}$").matches(ref))
        assertFalse("sam" in ref)
        assertEquals(1, gateway.closed)
    }

    @Test
    fun `B-AGENT-312 an API key account is an apiKey sign-in, never a plan reading`() = runTest {
        val result = sourceFor(Account(AgentAccount(type = "apiKey", requiresSignIn = false))).read(context())
        assertTrue(result.readings.isEmpty())
        val signIn = result.signIns.single()
        assertEquals(SignInMethod.ApiKey, signIn.method)
        assertEquals("codex", signIn.providerId)
        assertEquals("codex1", signIn.connectionId)
    }

    @Test
    fun `B-AGENT-312 signed out is listed only when it needs a sign-in or was used`() = runTest {
        val out = sourceFor(Account(AgentAccount(requiresSignIn = true))).read(context())
        assertEquals(UsageReadingStatus.NotSignedIn, out.readings.single().status)
        assertTrue(sourceFor(Account(AgentAccount(requiresSignIn = false))).read(context()).readings.isEmpty())
        val before = UsageReading("codex", "Codex", "codex1", UsageReadingStatus.Ok, NOW)
        val used = sourceFor(Account(AgentAccount(requiresSignIn = false)))
            .read(context(lastReading = { c, p -> if (c == "codex1" && p == "codex") before else null }))
        assertEquals(UsageReadingStatus.NotSignedIn, used.readings.single().status)
    }

    @Test
    fun `B-AGENT-312 a failing session is a broken reader and closes, a dropped link is offline`() = runTest {
        val failing = Account(AgentAccount(requiresSignIn = false), throws = true)
        val broken = sourceFor(failing).read(context())
        assertEquals(UsageProblem.Broken, broken.failures.single().problem)
        assertEquals(1, failing.closed)
        val dropped = sourceFor(
            Account(AgentAccount(requiresSignIn = false), fail = Failure.Network(NetworkWhy.Reset)),
        ).read(context())
        assertEquals(setOf("codex1"), dropped.unreached)
    }

    @Test
    fun `B-AGENT-312 with no open Codex connection it reads nothing`() = runTest {
        val result = AgentAccountSource { null }.read(context())
        assertTrue(result.readings.isEmpty())
        assertTrue(result.unreached.isEmpty())
    }

    // ---- B-AGENT-313 Paseo

    private class PaseoRig(autoRead: Boolean = false) {
        val switches = MemoryUsageSwitches()
        var reply: List<ProviderUsageReading> = listOf(reading("claude"), reading("copilot"))
        var hostRunning = true
        var phoneReads = 0
        val servers = listOf(UsageServer("p1", BackendKind.Paseo))
        val monitor = PaseoUsageMonitor(
            switches = switches,
            servers = { servers },
            createGateway = {
                object : ProviderUsageGateway {
                    override suspend fun read(): Outcome<List<ProviderUsageReading>> = reply.ok()

                    override suspend fun close() {}
                }
            },
            isReadable = { id -> servers.any { it.id == id } },
            clock = { NOW },
            phoneSupported = { true },
            phoneRead = {
                phoneReads++
                if (hostRunning) reply else null
            },
            autoRead = autoRead,
        )

        companion object {
            fun reading(id: String, signIn: Boolean = false) = ProviderUsageReading(
                providerId = id,
                displayName = id,
                fetchedAtMs = NOW,
                needsSignIn = signIn,
                windows = if (signIn) {
                    emptyList()
                } else {
                    listOf(
                        ProviderUsageWindowReading(18_000, "", 70, NOW + HOUR),
                        ProviderUsageWindowReading(604_800, "", 40, null),
                    )
                },
            )
        }
    }

    @Test
    fun `B-AGENT-313 opted-in servers and the phone host feed readings, others are not read`() = runTest {
        val rig = PaseoRig()
        val source = PaseoUsageSource(rig.monitor) { listOf("p1") }
        assertTrue(source.read(context()).readings.isEmpty())
        assertEquals(0, rig.phoneReads)

        rig.monitor.setEnabled("p1", true)
        rig.monitor.setEnabled(PaseoUsageMonitor.PHONE_ID, true)
        assertEquals(true, rig.switches.bool("oc.paseoUsage.p1"))
        val result = source.read(context())
        val byConn = result.readings.groupBy({ it.connectionId }, { it.providerId }).mapValues { it.value.toSet() }
        assertEquals(mapOf("phone" to setOf("claude", "copilot"), "p1" to setOf("claude", "copilot")), byConn)
        val claude = result.readings.first { it.providerId == "claude" }
        assertEquals(listOf(30.0, 60.0), claude.windows.map { it.usedPercent })
        assertEquals(listOf(UsageWindowKind.FiveHour, UsageWindowKind.Weekly), claude.windows.map { it.kind })
    }

    @Test
    fun `B-AGENT-313 with automatic reading no switch is needed`() = runTest {
        val rig = PaseoRig(autoRead = true)
        val source = PaseoUsageSource(rig.monitor) { listOf("p1") }
        assertEquals(setOf("p1", "phone"), source.read(context()).readings.map { it.connectionId }.toSet())
        assertTrue(rig.switches.keys().isEmpty())
        // A stopped host is still never started.
        rig.hostRunning = false
        assertTrue("phone" in source.read(context()).unreached)
    }

    @Test
    fun `B-AGENT-313 a signed-out Claude Code becomes a not-signed-in reading`() = runTest {
        val rig = PaseoRig()
        rig.reply = listOf(PaseoRig.reading("claude", signIn = true))
        rig.monitor.setEnabled(PaseoUsageMonitor.PHONE_ID, true)
        val result = PaseoUsageSource(rig.monitor) { emptyList() }.read(context())
        assertEquals(UsageReadingStatus.NotSignedIn, result.readings.single().status)
        assertEquals("phone", result.readings.single().connectionId)
    }

    @Test
    fun `B-AGENT-313 a stopped phone host is unreached, never started`() = runTest {
        val rig = PaseoRig()
        rig.hostRunning = false
        rig.monitor.setEnabled(PaseoUsageMonitor.PHONE_ID, true)
        val result = PaseoUsageSource(rig.monitor) { emptyList() }.read(context())
        assertTrue(result.readings.isEmpty())
        assertEquals(setOf("phone"), result.unreached)
        assertEquals(PaseoUsageStatus.HostStopped, rig.monitor.statusFor(PaseoUsageMonitor.PHONE_ID))
    }
}
