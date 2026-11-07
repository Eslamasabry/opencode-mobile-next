package io.github.eslamasabry.oc.engine.runtime.install

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.history.HistoryCopyScripts
import io.github.eslamasabry.oc.engine.runtime.history.HistoryShellPort
import io.github.eslamasabry.oc.engine.runtime.history.HistorySwitchAdmission
import io.github.eslamasabry.oc.engine.runtime.history.HistorySwitchCheck
import io.github.eslamasabry.oc.engine.runtime.history.LinuxRunResult
import io.github.eslamasabry.oc.engine.runtime.host.refused
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Port of test/phone_host_runtime_test.dart. */
class PhoneHostRuntimeTest {
    @Test
    fun `B-LINUX-142 history admission refuses every failure and identifies separate history`() {
        assertEquals(HistorySwitchAdmission.Allowed, HistorySwitchAdmission.fromResult(0, ""))
        assertEquals(
            HistorySwitchAdmission.SeparateHistory,
            HistorySwitchAdmission.fromResult(74, HistoryCopyScripts.SWITCH_BLOCKED_TEXT),
        )
        for (code in listOf(1, 74, 127, 137)) {
            assertEquals(HistorySwitchAdmission.Unavailable, HistorySwitchAdmission.fromResult(code, "unknown failure"))
        }
        assertEquals(
            HistorySwitchAdmission.Unavailable,
            HistorySwitchAdmission.fromResult(127, HistoryCopyScripts.SWITCH_BLOCKED_TEXT),
        )
    }

    @Test
    fun `B-LINUX-142 the admission runs the read-only check folded with stderr and a failed run is unavailable`() = runTest {
        val scripts = mutableListOf<Pair<String, Duration>>()
        var answer: Outcome<LinuxRunResult> = LinuxRunResult(0, "").ok()
        val check = HistorySwitchCheck(HistoryShellPort { script, timeout -> scripts += script to timeout; answer })
        assertEquals(HistorySwitchAdmission.Allowed, check.admission())
        assertEquals("(\n${HistoryCopyScripts.SWITCH_CHECK}\n) 2>&1", scripts.single().first)
        assertEquals(20.seconds, scripts.single().second)
        answer = LinuxRunResult(74, "[oc-history-switch-blocked] ${HistoryCopyScripts.SWITCH_BLOCKED_TEXT}\n").ok()
        assertEquals(HistorySwitchAdmission.SeparateHistory, check.admission())
        answer = RuntimeProblem.Generic.refused()
        assertEquals(HistorySwitchAdmission.Unavailable, check.admission())
    }

    @Test
    fun `B-LINUX-142 in-app host preserves a legacy installation`() {
        assertEquals(ManagedRuntime.OpenCode2, PhoneHostRuntime.inApp(profile = null, ubuntuInstalled = false))
        assertEquals(ManagedRuntime.OpenCode1, PhoneHostRuntime.inApp(profile = null, ubuntuInstalled = true))
        assertEquals(ManagedRuntime.OpenCode2, PhoneHostRuntime.inApp(profile = null, ubuntuInstalled = null))
        // A saved profile wins over the installation either way.
        val older = PhoneHostRuntime.SavedProfile(requiresOpenCode2Migration = true)
        val current = PhoneHostRuntime.SavedProfile(requiresOpenCode2Migration = false)
        assertEquals(ManagedRuntime.OpenCode1, PhoneHostRuntime.inApp(older, ubuntuInstalled = false))
        assertEquals(ManagedRuntime.OpenCode2, PhoneHostRuntime.inApp(current, ubuntuInstalled = true))
    }

    @Test
    fun `B-LINUX-142 a missing runtime record is OpenCode 1 and an unknown one is refused`() {
        assertEquals(ManagedRuntime.OpenCode1, ManagedRuntime.parse(null))
        assertEquals(ManagedRuntime.OpenCode1, ManagedRuntime.parse(" "))
        assertNull(ManagedRuntime.parse("opencode3"))
    }
}
