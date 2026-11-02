package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.Seed.PHONE
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.android.migration.steps.RestoreAuthority
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plan 05 §10.2 `RestoreAuthorityCheckTest` (B-LINUX-032, builtin-linux trap T1): M4 equality and predicate. */
class RestoreAuthorityCheckTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.io() = StandardTestDispatcher(testScheduler)

    private fun json(text: String): Map<*, *> = MiniJson.parse(text) as Map<*, *>

    private val policy = """{"version":1,"supervision":"balanced","behaviors":{"restartPhoneServer":true,"pollRestartHealth":true}}"""

    /** Shared vectors; `RestoreAuthorityParityTest` in `:androidApp` runs them through `NativeRecoveryBudget` too. */
    @Test
    fun `B-LINUX-032 the authority predicate matches the Kotlin restore rule on shared vectors`() {
        assertTrue(RestoreAuthority.migrationAllows(json("""{"version":2,"nativeAuthority":true}""")))
        assertFalse(RestoreAuthority.migrationAllows(json("""{"version":1,"nativeAuthority":true}""")))
        assertFalse(RestoreAuthority.migrationAllows(json("""{"version":2.0,"nativeAuthority":true}""")))
        assertFalse(RestoreAuthority.migrationAllows(json("""{"version":2,"nativeAuthority":"true"}""")))
        assertFalse(RestoreAuthority.migrationAllows(null))
        assertTrue(RestoreAuthority.policyAllows(json(policy)))
        assertFalse(RestoreAuthority.policyAllows(json(policy.replace("balanced", "manual"))))
        assertFalse(RestoreAuthority.policyAllows(json(policy.replace("\"pollRestartHealth\":true", "\"pollRestartHealth\":false"))))
        assertFalse(RestoreAuthority.policyAllows(json(policy.replace("\"version\":1", "\"version\":2"))))
        assertNull(RestoreAuthority.disarmReason(json("""{"version":2,"nativeAuthority":true}"""), json(policy), PHONE, PHONE))
        assertEquals("owner", RestoreAuthority.disarmReason(json("""{"version":2,"nativeAuthority":true}"""), json(policy), "../x", PHONE))
        assertEquals("owner", RestoreAuthority.disarmReason(json("""{"version":2,"nativeAuthority":true}"""), json(policy), "other", PHONE))
    }

    @Test
    fun `B-LINUX-032 the armed phone server is counted and the Kotlin half is only read`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        val recipe = """{"packageVersion":9999,"rootfsGeneration":7}"""
        w.kotlinPrefs = mapOf(
            "builtin_server_recovery" to mapOf("restoreOwner" to PHONE, "oc.builtinRuntimeRecipe.$PHONE" to recipe),
            "builtin_component_writer" to mapOf("rootfsGeneration" to 7L),
        )
        assertIs<RunnerState.Open>(w.runner(io()).run())
        val audit = w.paths.audit.readText()
        assertTrue(audit.contains("\"armed\":1"), audit)
        assertTrue(audit.contains("\"restoreOwnerArmed\":true"), audit)
        assertTrue(audit.contains("\"recipesCompatible\":1"), audit)
    }

    @Test
    fun `B-LINUX-032 a missing or incompatible Kotlin half is recorded, never blocked`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        w.kotlinPrefs = mapOf("builtin_server_recovery" to mapOf("oc.builtinRuntimeRecipe.$PHONE" to """{"packageVersion":10001,"rootfsGeneration":7}"""))
        assertIs<RunnerState.Open>(w.runner(io()).run())
        assertTrue(w.paths.audit.readText().contains("\"kotlinHalfDisarmed\":1"))
    }

    @Test
    fun `B-LINUX-032 a restore-authority key that changed after the census blocks with AuthorityChanged`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        // A bug stand-in: something rewrites the phone's automation policy between M3 and M4.
        val tamper = object : StepInterrupter {
            override fun reached(point: String) {
                if (point == "M3") w.prefs.commit(mapOf("flutter.oc.automation.$PHONE" to RawPref.Str("""{"version":1}""")))
            }
        }
        val blocked = assertIs<RunnerState.Blocked>(w.runner(io(), tamper).run())
        assertEquals(BlockReason.AuthorityChanged, blocked.reason)
        assertEquals("M4", blocked.step)
        assertTrue(blocked.restoreAvailable, "R2 is offered: the gate never opened")
    }

    @Test
    fun `B-MIGR-005 any undeclared key that changed during the run blocks M5 with AuthorityChanged`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        val tamper = object : StepInterrupter {
            override fun reached(point: String) {
                if (point == "M4") w.prefs.commit(mapOf("flutter.oc.offlineQueue" to RawPref.Str("[]")))
            }
        }
        val blocked = assertIs<RunnerState.Blocked>(w.runner(io(), tamper).run())
        assertEquals(BlockReason.AuthorityChanged, blocked.reason)
        assertEquals("M5", blocked.step)
    }
}
