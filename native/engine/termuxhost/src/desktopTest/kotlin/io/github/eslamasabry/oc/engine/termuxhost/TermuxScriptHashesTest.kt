package io.github.eslamasabry.oc.engine.termuxhost

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * test/termux_script_hashes_test.dart for the scripts this port renders. Expected hashes are Flutter's reviewed
 * snapshot (test/fixtures/termux_script_hashes.json). That snapshot predates the 2026-11-01 freeze's manager edit, so
 * scripts embedding the manager are compared with that reviewed manager (resource manager-2.1.0.sh) swapped back in,
 * and the current manager is pinned by the hash of the current Flutter source. The freeze also moved the OpenCode 2
 * pin from 2.1.1 to 2.2.5 (its only other script change), so the two default install renderings are compared at the
 * reviewed 2.1.1 and the current default is asserted separately.
 */
class TermuxScriptHashesTest {
    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private val reviewedManager = Shell.resource("manager-2.1.0.sh")

    private fun reviewed(script: String): String = script.replace(TermuxScripts.managerScript, reviewedManager)

    @Test
    fun `B-LINUX-227 every embedded script matches its reviewed byte snapshot`() {
        assertEquals("2136b3927c9b35f61f89f960991554aae07b53da39a5bb9f9298796493b968f8", sha(reviewedManager))
        // The current Flutter manager (lib/termux/scripts/manager_script.dart at the freeze).
        assertEquals("55ed990eafb0973ed992abe1e6ed82bd916beaca9fdfc3641af30847e58649f4", sha(TermuxScripts.managerScript))
        val expected =
            mapOf(
                "setupBaseScript" to "f23b08ac1cb28050bdb96885440fd4b2a2e4d347298f2278467ec17728c3b807",
                "ensureWakeLockScript" to "ff2b19d0a09fac03db50d9bc80940b5dc1fa3a5f3748620fb53016ec1b926526",
                "unlockCommand" to "a13cc0014fa922ad49c7b44b37aeb84fac482299492abfe14c180cb347cef7a4",
                "storageScript" to "d0aa7bc04c130c84861c38db60839b0eac7f0051df6de6a1a9c6614a33dcdbce",
                "installationScript" to "ee75b21c38c6042f7d699cf768db3011b1f83d4454184c1f7bca43b8eeebb7aa",
                "statusScript" to "cad7ee1354b6345aec068de4ac69820644a5e5064682b0b17af8c4f156af8c19",
                "setupSnapshotScript" to "3b5c64f1484820f1f31a47658c10dc5671c4c15fa48878ae28a5aca69edae2f2",
                "diagnosticsScript" to "f6b2ce2e2cef53bf9f148465c7a09598807603df7c564c0993d6b0c796806ffb",
                "stopScript" to "a1bc6e85ec0d4d615a9abf81f041efc66d415f738b656b1b3fc2feef98a1ff08",
                "stopScript:5000" to "76fc4ef671c8d711472c1cad27fba22226b96b603e47d907ce6562871f87bbcc",
                "createProjectFolderScript" to "5ce591829760c7f7658cb4152ba27d616377e84c952d6881d44e9235e5763244",
                "recoveryControlScript:enable" to "092d0f6a6bea55b0220d80e2743a998af28b67fd555123ca2ee1eeab1daed9b5",
                "recoveryControlScript:disable" to "2888c9356423eb05cceb16156138e4fef2fd3a55935c0fcb5750972361002e4e",
                "installAndServeScript:default" to "5c0d28307626f90270bebe2505616a8173912908b390cf04a7fc4c42cfc3b495",
                "installAndServeScript:2" to "e086f71ab5c8d35aa882c5ed10d5f25bd674d09c61748d180d84402c9c6edc72",
                "installAndServeScript:latest" to "48074cbf5bddf5111fab24f078d79a77c87b72875f7e2bb922c6bcd596dd993f",
                "restartScript:plain" to "2b2ca4ea98c9d404a7ba3a658d8e04cedeaeb0e4927713dd01e6e26ce385228a",
                "restartScript:recovery" to "74e95dbc31537f5aa0ea3f0e54dddc0346f8b5bd3085ce1a87d0455ced1661b2",
                "restartScript:switch" to "f0676c1212478e5ab14fa545fd9bd60950d84a727a820e2884c22509f1fe4dd7",
            )
        val rendered =
            mapOf(
                "setupBaseScript" to TermuxScripts.setupBaseScript,
                "ensureWakeLockScript" to TermuxScripts.ensureWakeLockScript,
                "unlockCommand" to TermuxScripts.unlockCommand,
                "storageScript" to TermuxScripts.storageScript(),
                "installationScript" to TermuxScripts.installationScript(),
                "statusScript" to TermuxScripts.statusScript(),
                "setupSnapshotScript" to TermuxScripts.setupSnapshotScript(),
                "diagnosticsScript" to TermuxScripts.diagnosticsScript(),
                "stopScript" to TermuxScripts.stopScript(),
                "stopScript:5000" to TermuxScripts.stopScript(port = 5000),
                "createProjectFolderScript" to TermuxScripts.createProjectFolderScript("demo"),
                "recoveryControlScript:enable" to TermuxScripts.recoveryControlScript("tok_1", enable = true),
                "recoveryControlScript:disable" to TermuxScripts.recoveryControlScript("tok_1", enable = false),
                "installAndServeScript:default" to TermuxScripts.installAndServeScript(password = "p'w\$d", version = "2.1.1"),
                "installAndServeScript:2" to
                    TermuxScripts.installAndServeScript(
                        port = 4100,
                        password = "secret",
                        version = "2.1.1",
                        runtime = TermuxRuntime.OpenCode2,
                    ),
                "installAndServeScript:latest" to
                    TermuxScripts.installAndServeScript(password = "secret", version = TermuxScripts.LATEST_OPENCODE_VERSION),
                "restartScript:plain" to TermuxScripts.restartScript(operationId = "op1"),
                "restartScript:recovery" to
                    TermuxScripts.restartScript(port = 4097, operationId = "op2", recoveryToken = "rt", expectedOperationId = "op1"),
                "restartScript:switch" to
                    TermuxScripts.restartScript(
                        operationId = "op3",
                        switchTarget = TermuxRuntime.OpenCode2,
                        switchPassword = "pw'x",
                    ),
            )
        assertEquals(expected.keys, rendered.keys)
        assertEquals(
            TermuxScripts.installAndServeScript(password = "p'w\$d", version = TermuxRuntime.OpenCode2.pinnedVersion),
            TermuxScripts.installAndServeScript(password = "p'w\$d"),
        )
        for ((key, hash) in expected) assertEquals(hash, sha(reviewed(rendered.getValue(key))), key)
    }
}
