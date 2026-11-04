package io.github.eslamasabry.oc.engine.termuxhost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** test/termux_default_runtime_test.dart: the install decision (the Termux host controller is migrate-out only). */
class TermuxDefaultRuntimeTest {
    private val fresh = TermuxInstallation.parse("ubuntu=absent\nversion=\n")!!
    private val legacyProfile =
        ManagedProfile("legacy", password = "synthetic-test-secret", requiresOpenCode2Migration = true)

    @Test
    fun `B-LINUX-230 a fresh Termux install selects the pinned OC2`() {
        val plan = TermuxInstallPlan.decide(TermuxHostJob.Install, fresh, null, emptyList())
        assertEquals(TermuxInstallPlan.InstallFresh("2.2.5"), plan)
        assertEquals(TermuxRuntime.OpenCode2, TermuxInstallPlan.runtimeOf(null, fresh, emptyList()))
        val script = TermuxScripts.installAndServeScript(password = "p", version = (plan as TermuxInstallPlan.InstallFresh).version)
        assertTrue(script.contains("setup '4096' '2.2.5'"))
    }

    @Test
    fun `B-LINUX-230 a legacy installation automatically uses the safe OC2 move keeping identity and credentials`() {
        val legacy = TermuxInstallation.parse("ubuntu=installed\nversion=1.19.0\n")!!
        val plan = TermuxInstallPlan.decide(TermuxHostJob.Install, legacy, null, listOf(legacyProfile))
        val move = assertIs<TermuxInstallPlan.MoveToOpenCode2>(plan)
        assertEquals("legacy", move.profile.id)
        assertEquals("synthetic-test-secret", move.profile.password)
        assertTrue(move.profile.openCode2HistoryPending)
        assertEquals(false, move.profile.requiresOpenCode2Migration)
        // A saved legacy phone moves even before the inventory shows a version.
        assertIs<TermuxInstallPlan.MoveToOpenCode2>(TermuxInstallPlan.decide(TermuxHostJob.Install, fresh, null, listOf(legacyProfile)))
    }

    @Test
    fun `B-LINUX-230 a legacy selection without recoverable credentials never installs OC1`() {
        val inventory = TermuxInstallation.parse("ubuntu=absent\nversion=\nruntime=opencode1\n")!!
        assertEquals(
            TermuxInstallPlan.Refused(TermuxInstallPlan.Reason.LegacyWithoutCredentials),
            TermuxInstallPlan.decide(TermuxHostJob.Install, inventory, null, emptyList()),
        )
    }

    @Test
    fun `B-LINUX-230 an independently ready OC2 history cannot adopt the legacy profile`() {
        val inventory = TermuxInstallation.parse("ubuntu=installed\nversion=2.2.5\nruntime=opencode2\n")!!
        val ready = TermuxSetupStatus.parse("phase=ready\nport=4096\nrunner=proot\nversion=2.2.5\nruntime=opencode2\n")
        val plan =
            TermuxInstallPlan.decide(TermuxHostJob.SwitchRuntime, inventory, ready, listOf(legacyProfile), separateOpenCode2History = true)
        assertEquals(TermuxInstallPlan.Refused(TermuxInstallPlan.Reason.SeparateHistory), plan)
    }

    @Test
    fun `B-LINUX-230 an OC2 alias cannot replace the original migration profile`() {
        val inventory = TermuxInstallation.parse("ubuntu=installed\nversion=2.2.5\nruntime=opencode2\n")!!
        val ready = TermuxSetupStatus.parse("phase=ready\nport=4096\nrunner=proot\nversion=2.2.5\nruntime=opencode2\n")
        val alias = ManagedProfile("oc2-alias", password = "alias-only")
        for (pending in listOf(false, true)) {
            val original =
                ManagedProfile(
                    "legacy-owner",
                    password = "original-only",
                    requiresOpenCode2Migration = !pending,
                    openCode2HistoryPending = pending,
                )
            val plan = TermuxInstallPlan.decide(TermuxHostJob.SwitchRuntime, inventory, ready, listOf(alias, original))
            val connect = assertIs<TermuxInstallPlan.Connect>(plan)
            assertEquals("legacy-owner", connect.profile.id)
            assertEquals("original-only", connect.profile.password)
            assertEquals(false, connect.profile.requiresOpenCode2Migration)
            assertTrue(connect.profile.openCode2HistoryPending)
        }
    }

    @Test
    fun `B-LINUX-230 an unreadable inventory cannot be treated as a fresh install`() {
        val unreadable = TermuxInstallation.parse("ubuntu=installed\nversion=not a version")
        assertEquals(
            TermuxInstallPlan.Refused(TermuxInstallPlan.Reason.InventoryUnreadable),
            TermuxInstallPlan.decide(TermuxHostJob.Install, unreadable, null, emptyList()),
        )
    }
}
