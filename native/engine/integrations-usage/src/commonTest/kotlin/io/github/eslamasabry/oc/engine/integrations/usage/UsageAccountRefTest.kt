package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Flutter PR #136: the account a plan belongs to travels from the reader (the provider list or the Antigravity CLI)
// through the phone's usage source into the subscription, and an older reading never drops it.

private val PHONE = UsageConnection(UsageConnection.PHONE_ID, "This phone", UsageConnectionKind.PhoneAgents)

private class PhoneOnly(val reply: List<ProviderUsageReading>) : PaseoUsagePorts {
    override fun paseoProfiles(): List<PaseoUsageProfile> = emptyList()

    override fun isReadable(profileId: String): Boolean = false

    override suspend fun readServer(profileId: String): Outcome<List<ProviderUsageReading>> = Outcome.Ok(emptyList())

    override fun phoneSupported(): Boolean = true

    override suspend fun readPhone(): Outcome<List<ProviderUsageReading>?> = Outcome.Ok(reply)
}

class UsageAccountRefTest {
    private val kv = FakeKv()

    @Test
    fun `B-183 the provider list reads the account and plan, capped and cleaned`() {
        val readings =
            parseProviderUsageList(
                mapOf(
                    "providers" to
                        listOf(
                            mapOf(
                                "providerId" to "codex",
                                "displayName" to "Codex",
                                "status" to "available",
                                "planLabel" to "Pro",
                                "email" to "me@corp.io",
                                "windows" to listOf(mapOf("id" to "five_hour", "usedPct" to 10)),
                            ),
                            mapOf(
                                "providerId" to "claude",
                                "displayName" to "Claude",
                                "status" to "unavailable",
                                "account" to "x".repeat(80),
                            ),
                            mapOf(
                                "providerId" to "gemini",
                                "status" to "available",
                                "windows" to listOf(mapOf("id" to "weekly", "usedPct" to 10)),
                            ),
                        ),
                ),
                now = NOW,
            )
        assertEquals("me@corp.io", readings[0].accountRef)
        assertEquals("Pro", readings[0].planLabel)
        assertEquals("x".repeat(64), readings[1].accountRef)
        assertNull(readings[2].accountRef)
        assertNull(readings[2].planLabel)
    }

    @Test
    fun `B-183 the phone source carries the account and plan into the subscription`() =
        runTest {
            val agy =
                ProviderUsageReading(
                    "antigravity",
                    "Antigravity",
                    NOW,
                    listOf(ProviderUsageWindowReading(18_000, "5-hour", 60, null)),
                    planLabel = "Google AI Pro",
                    accountRef = "altaf.alu@gmail.com",
                )
            val monitor = PaseoUsageMonitor(kv, LANE, PhoneOnly(listOf(agy)), widget = null, clock = { NOW })
            monitor.setEnabled(PaseoUsageMonitor.PHONE_ID, true)
            val source = PaseoUsageSource(monitor) { emptyList() }
            val result = (source.read(UsageReadContext(listOf(PHONE), NOW, force = true)) as Outcome.Ok).value
            val reading = result.readings.single()
            assertEquals("altaf.alu@gmail.com", reading.accountRef)
            assertEquals("Google AI Pro", reading.plan)

            val sub = mergeUsageSubscriptions(NOW, listOf(PHONE), result.readings).subscriptions.single()
            assertEquals("altaf.alu@gmail.com", sub.accountRef)
            assertEquals("antigravity|altaf.alu@gmail.com", sub.key)
        }
}
