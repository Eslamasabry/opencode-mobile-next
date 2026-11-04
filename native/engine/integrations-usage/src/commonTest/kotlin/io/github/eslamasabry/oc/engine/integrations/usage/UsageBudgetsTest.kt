package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

// Ported from Flutter test/usage_budgets_test.dart, test/quota_budgets_retired_test.dart and the OpenCode sign-ins
// group of test/usage_subscription_sources_test.dart.

class UsageBudgetsTest {
    private val kv = FakeKv()
    private val locks = UsageBudgetLocks()
    private var current = true
    private var present = true

    private fun make(origin: String = "https://synthetic.example") =
        UsageBudgets(kv, LANE, locks, "profile", origin, isCurrent = { current }, isProfilePresent = { present })

    private fun sample(from: Long = 100, project: String? = null, timezone: String = "UTC") =
        UsageBudgetScope(project = project, range = "today", from = from, timezone = timezone)

    private val budgets = make()

    @Test
    fun `B-STATE-146 restart keeps measured USD and token budgets in their exact scopes`() =
        runTest {
            val data = sample()
            assertTrue(budgets.save(data, UsageBudgetUnit.Usd, 12.5))
            assertTrue(budgets.save(data, UsageBudgetUnit.Tokens, 10_000.0))
            val restored = make()
            // The upper bound of the window is not part of the scope.
            assertEquals(12.5, restored.limit(sample(), UsageBudgetUnit.Usd))
            assertEquals(10_000.0, restored.limit(data, UsageBudgetUnit.Tokens))
            for (other in listOf(sample(from = 101), sample(project = "other"), sample(timezone = "Asia/Dubai"))) {
                assertNull(restored.limit(other, UsageBudgetUnit.Usd))
            }
            assertNull(make(origin = "https://other.example").limit(data, UsageBudgetUnit.Usd))
            assertFalse(budgets.save(data, UsageBudgetUnit.Tokens, 1.5))
            assertFalse(budgets.save(data, UsageBudgetUnit.Usd, Double.POSITIVE_INFINITY))
        }

    @Test
    fun `B-STATE-146 failed write preserves durable previous budget and deletion sweeps it`() =
        runTest {
            val data = sample()
            budgets.save(data, UsageBudgetUnit.Usd, 10.0)
            kv.refuse = true
            assertFalse(budgets.save(data, UsageBudgetUnit.Usd, 20.0))
            assertEquals(10.0, budgets.limit(data, UsageBudgetUnit.Usd))
            kv.refuse = false
            assertEquals(10.0, make().limit(data, UsageBudgetUnit.Usd))
            assertTrue(budgets.key in kv.scopedKeys("profile"))
            kv.sweep("profile")
            assertTrue(kv.values.isEmpty())
            present = false
            current = false
            assertNull(budgets.limit(data, UsageBudgetUnit.Usd))
        }

    @Test
    fun `B-STATE-146 mismatched persisted scope cannot masquerade as the current budget`() =
        runTest {
            val data = sample()
            assertTrue(budgets.save(data, UsageBudgetUnit.Usd, 10.0))
            val stored = DartJson.decode(kv.str(budgets.key)!!) as Map<*, *>
            val rules = stored["rules"] as Map<*, *>
            val (id, rule) = rules.entries.single()
            val changed = (rule as Map<*, *>).entries.associate { it.key.toString() to it.value } + ("timezone" to "Asia/Dubai")
            kv.values[budgets.key] = PrefValue.Str(DartJson.encode(mapOf("version" to 1, "rules" to mapOf(id to changed))))
            val restored = make()
            assertNull(restored.limit(data, UsageBudgetUnit.Usd))
            assertTrue(restored.state.value.failed)
        }

    @Test
    fun `B-STATE-146 one malformed persisted rule does not discard valid budgets`() =
        runTest {
            val first = sample()
            val second = sample(from = 101)
            assertTrue(budgets.save(first, UsageBudgetUnit.Usd, 10.0))
            assertTrue(budgets.save(second, UsageBudgetUnit.Usd, 20.0))
            val stored = DartJson.decode(kv.str(budgets.key)!!) as Map<*, *>
            val rules = (stored["rules"] as Map<*, *>).entries.associate { it.key.toString() to it.value } +
                ("f".repeat(64) to mapOf("unit" to "usd", "limit" to "invalid"))
            kv.values[budgets.key] = PrefValue.Str(DartJson.encode(mapOf("version" to 1, "rules" to rules)))
            val restored = make()
            assertEquals(10.0, restored.limit(first, UsageBudgetUnit.Usd))
            assertEquals(20.0, restored.limit(second, UsageBudgetUnit.Usd))
            assertTrue(restored.state.value.failed)
            assertTrue(restored.save(first, UsageBudgetUnit.Usd, 30.0))
            assertEquals(30.0, restored.limit(first, UsageBudgetUnit.Usd))
            assertTrue(restored.state.value.failed)
        }

    @Test
    fun `B-STATE-147 replacement screens serialize writes and merge separate units`() =
        runTest {
            val replacement = make()
            kv.gate = CompletableDeferred()
            kv.started = CompletableDeferred()
            val first = async { budgets.save(sample(), UsageBudgetUnit.Usd, 10.0) }
            kv.started!!.await()
            val second = async { replacement.save(sample(), UsageBudgetUnit.Tokens, 100.0) }
            kv.gate!!.complete(Unit)
            assertTrue(first.await())
            assertTrue(second.await())
            val restored = make()
            assertEquals(10.0, restored.limit(sample(), UsageBudgetUnit.Usd))
            assertEquals(100.0, restored.limit(sample(), UsageBudgetUnit.Tokens))
        }

    @Test
    fun `B-STATE-147 deletion during a pending write cannot resurrect the budget`() =
        runTest {
            kv.gate = CompletableDeferred()
            kv.started = CompletableDeferred()
            val write = async { budgets.save(sample(), UsageBudgetUnit.Usd, 10.0) }
            kv.started!!.await()
            present = false
            current = false
            kv.sweep("profile")
            kv.gate!!.complete(Unit)
            assertFalse(write.await())
            assertNull(kv.get(budgets.key))
        }

    @Test
    fun `B-STATE-147 explicit clearing recovers full capacity without silently evicting rules`() =
        runTest {
            for (i in 0 until 64) assertTrue(budgets.save(sample(from = i.toLong()), UsageBudgetUnit.Usd, (i + 1).toDouble()))
            assertFalse(budgets.save(sample(from = 65), UsageBudgetUnit.Usd, 99.0))
            assertTrue(budgets.state.value.failed)
            assertEquals(1.0, budgets.limit(sample(from = 0), UsageBudgetUnit.Usd))
            assertTrue(budgets.clearAll())
            assertNull(kv.get(budgets.key))
            assertTrue(budgets.save(sample(from = 65), UsageBudgetUnit.Usd, 99.0))
        }

    @Test
    fun `B-STATE-068 load drops every stored quota budget and nothing else`() =
        runTest {
            kv.values["oc.budgets.laptop"] = PrefValue.Str("""{"version":1,"rules":{}}""")
            kv.values["oc.budgets.phone"] = PrefValue.Str("""{"version":1,"rules":{}}""")
            kv.values["oc.consumptionBudgets.laptop"] = PrefValue.Str("""{"version":1}""")
            kv.values["oc.activeProfile"] = PrefValue.Str("laptop")
            assertEquals(Outcome.Ok(2), RetiredQuotaBudgets.dropAll(kv, LANE))
            assertEquals(Outcome.Ok(0), RetiredQuotaBudgets.dropAll(kv, LANE))
            assertTrue(kv.keys(RetiredQuotaBudgets.PREFIX).isEmpty())
            assertEquals("""{"version":1}""", kv.str("oc.consumptionBudgets.laptop"))
            assertEquals("laptop", kv.str("oc.activeProfile"))
        }

    @Test
    fun `B-STATE-068 profile deletion still sweeps the key`() {
        kv.values["oc.budgets.laptop"] = PrefValue.Str("{}")
        assertTrue("oc.budgets.laptop" in kv.scopedKeys("laptop"))
    }
}

class OpenCodeSignInSourceTest {
    private val context = UsageReadContext(connections = emptyList(), now = NOW, force = true)

    private fun providers(vararg ids: String) = Outcome.Ok(ids.map { ConnectedProvider(it, it) })

    @Test
    fun `B-PROTO-123 maps connected providers ignores non-subscriptions and is authoritative`() =
        runTest {
            val source =
                OpenCodeSignInSource(
                    servers = { listOf(SignInServer("pc", isOpenCode = true), SignInServer("p1", isOpenCode = false)) },
                    isReadable = { true },
                    providers = { providers("openai", "zai-coding-plan", "openrouter") },
                )
            val result = (source.read(context) as Outcome.Ok).value
            assertEquals(listOf("pc:codex", "pc:glm"), result.signIns.map { "${it.connectionId}:${it.providerId}" })
            assertEquals(setOf("pc"), result.authoritativeSignIns)
            assertEquals("Codex", result.signIns.first().displayName)
        }

    @Test
    fun `B-PROTO-123 a server that does not answer is unreached and not authoritative`() =
        runTest {
            val failing =
                OpenCodeSignInSource(
                    servers = { listOf(SignInServer("pc", isOpenCode = true)) },
                    isReadable = { true },
                    providers = { Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Network()) },
                )
            val result = (failing.read(context) as Outcome.Ok).value
            assertEquals(setOf("pc"), result.unreached)
            assertTrue(result.authoritativeSignIns.isEmpty())
            assertTrue(result.signIns.isEmpty())
            // A server that never answers in time is the same.
            val stalled =
                OpenCodeSignInSource(
                    servers = { listOf(SignInServer("pc", isOpenCode = true)) },
                    isReadable = { true },
                    providers = { kotlinx.coroutines.delay(1.hours); providers() },
                    timeout = 10.seconds,
                )
            assertEquals(setOf("pc"), (stalled.read(context) as Outcome.Ok).value.unreached)
        }

    @Test
    fun `B-PROTO-123 a server that needs its password again is skipped never contacted`() =
        runTest {
            var asked = 0
            val source =
                OpenCodeSignInSource(
                    servers = { listOf(SignInServer("pc", isOpenCode = true, requiresPasswordReentry = true)) },
                    isReadable = { true },
                    providers = {
                        asked++
                        providers()
                    },
                )
            val result = (source.read(context) as Outcome.Ok).value
            assertEquals(0, asked)
            assertTrue(result.unreached.isEmpty())
        }
}
