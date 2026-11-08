package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner report 2026-11-08 (merge41): after one Antigravity sign-in, every retry said "The shell
 * didn't start". The sign-in started the phone server even while it ran, and the manual start's
 * recovery confirmation refuses a server that was already running.
 */
class PhoneSignInRulesTest {
    @Test
    fun `a running phone server is used as it is, never started again for a sign-in`() {
        assertFalse(PhoneSignInRules.startsServer(Outcome.Ok(RuntimeStatus(serverRunning = true))))
    }

    @Test
    fun `a stopped or unreadable phone server is started first`() {
        assertTrue(PhoneSignInRules.startsServer(Outcome.Ok(RuntimeStatus(serverRunning = false))))
        assertTrue(
            PhoneSignInRules.startsServer(Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic)))
        )
    }
}
