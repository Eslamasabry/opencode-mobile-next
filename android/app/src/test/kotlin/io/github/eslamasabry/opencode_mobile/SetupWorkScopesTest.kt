package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SetupWorkScopesTest {
    private class Fixture {
        var now = 0L
        var admissions = 0
        val leases = WorkLeases({ now }, maxLifetimeMs = 1000)
        val tokens = mutableMapOf<Any, Long?>()
        val scopes = SetupWorkScopes({ owner, _ ->
            admissions++; tokens[owner] = leases.acquire(WorkLeases.Kind.SETUP, 1000)
        }, { owner -> tokens.remove(owner)?.let { leases.release(it) } })
        fun installer() {
            if (scopes.childNeedsLease()) { admissions++; leases.acquire(WorkLeases.Kind.SETUP, 1000) }
        }
    }
    @Test fun workerDelayedUntilAfterTimeoutDoesNotReadmitItsJobOrInstaller() {
        val f = Fixture(); val scope = f.scopes.prepare()
        f.leases.releaseKind(WorkLeases.Kind.SETUP)
        f.scopes.run(scope) { f.installer(); assertEquals(0, f.leases.snapshot().activeCount) }
        assertEquals(1, f.admissions)
    }
    @Test fun nextInstallerAfterWholeJobCapCannotCreateAReplacementHold() {
        val f = Fixture(); val scope = f.scopes.prepare()
        f.scopes.run(scope) {
            f.installer(); f.now = 1000
            assertEquals(0, f.leases.snapshot().activeCount)
            f.installer(); assertEquals(0, f.leases.snapshot().activeCount)
        }
        assertEquals(1, f.admissions)
    }
    @Test fun closedPreparedScopeDoesNotReadmitAndCannotBeEnteredTwice() {
        val f = Fixture(); val scope = f.scopes.prepare(); f.scopes.close(scope)
        f.scopes.run(scope) { f.installer(); assertEquals(0, f.leases.snapshot().activeCount) }
        try { f.scopes.run(scope) {}; fail("reused scope admitted") } catch (_: IllegalStateException) { }
        assertEquals(1, f.admissions)
    }
    @Test fun completionAndFailureRestoreStandaloneAdmissionAndReleaseExactOwner() {
        val f = Fixture()
        try { f.scopes.run(f.scopes.prepare()) { throw IllegalStateException() } }
        catch (_: IllegalStateException) { }
        assertEquals(0, f.leases.snapshot().activeCount); assertTrue(f.scopes.childNeedsLease())
        f.installer(); assertEquals(1, f.leases.snapshot().activeCount)
    }
    @Test fun failedAdmissionClosesCapturedLivenessAndReleasesIt() {
        var alive: (() -> Boolean)? = null; var releases = 0
        val scopes = SetupWorkScopes({ _, captured -> alive = captured; error("admission refused") }, { releases++ })
        try { scopes.prepare(); fail("failed admission passed") } catch (_: IllegalStateException) { }
        assertFalse(alive!!()); assertEquals(1, releases); assertTrue(scopes.childNeedsLease())
    }
}
