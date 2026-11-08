package io.github.eslamasabry.oc.engine.linuxsetup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The runner plan (`_spec` in setup_engine/start.dart) and the pinned OpenCode path's script text. */
class SetupPlanTest {
    private val wrapper = SetupScriptWrapper { "PRELUDE\n$it" }

    @Test
    fun `B-LINUX-200 a planned job skips what is installed and hands the runner native, script and step components`() {
        val plan =
            planSetupJob(
                setupComponentRegistry(), setOf("python"),
                mapOf("linux" to SetupCheckResult(true, "24.04"), "essentials" to SetupCheckResult(true, "2.43.0")),
                effectiveJobParams(emptyMap(), null), wrapper,
            )
        assertEquals(listOf("linux", "essentials", "python", "node", "opencode", "start"), plan.specs.map { it.id })
        val byId = plan.specs.associateBy { it.id }
        assertTrue(byId.getValue("linux").skipped)
        assertEquals("2.43.0", byId.getValue("essentials").version)
        val python = byId.getValue("python")
        assertTrue(python.script!!.startsWith("PRELUDE\n"))
        assertTrue(python.script.contains("oc_apt_install python3 python3-venv python3-pip"))
        assertEquals(mapOf("retry" to "Interrupted. Trying again", "compat" to "Trying again in compatibility mode"), python.labels)
        assertEquals("300000000", byId.getValue("opencode").data["requiredFreeBytes"])
        val start = byId.getValue("start")
        assertTrue(start.step)
        assertNull(start.script)
        assertEquals("Starting OpenCode", start.stage)
        assertEquals("opencode2", start.data["runtime"])
        assertEquals("true", start.data["openCodeChanged"])
        assertTrue(plan.openCodeChanged)

        val fresh = planSetupJob(setupComponentRegistry(), emptySet(), emptyMap(), emptyMap(), wrapper)
        val linux = fresh.specs.first()
        assertTrue(linux.native)
        assertNull(linux.script)
        assertEquals(mapOf("download" to "Downloading Linux base", "unpack" to "Unpacking Linux base"), linux.labels)
        assertEquals("${SetupPreflight.requiredFreeBytes(30_000_000)}", linux.data["requiredFreeBytes"])
    }

    @Test
    fun `B-LINUX-201 the progress tracker never moves backwards within a job and resets for a new job`() {
        val tracker = SetupProgressTracker()
        val registry = setupComponentRegistry()
        val now = Instant.fromEpochMilliseconds(1_790_196_840_162)
        fun job(id: String, node: String) =
            assertNotNull(
                parseSetupJob(
                    """{"jobId":"$id","state":"running","startedAt":1790196784379,"order":["node","opencode"],
                    "components":{"node":$node,"opencode":{"state":"pending"}}}""",
                ),
            )
        val downloading = tracker.progress(job("a", """{"state":"running","done":27,"total":30}"""), registry, now)
        val unpacking = tracker.progress(job("a", """{"state":"running","stage":"Unpacking"}"""), registry, now)
        assertEquals(downloading.overall, unpacking.overall)
        val other = tracker.progress(job("b", """{"state":"running","stage":"Unpacking"}"""), registry, now)
        assertTrue(other.overall < unpacking.overall)
    }

    @Test
    fun `B-LINUX-200 the registry's OpenCode start probe hands the password to curl on stdin, never as an argument`() {
        // Flutter 2.1.1 (c4a340f43, lib/builtin/setup/components.dart): a process argument is readable by every
        // process of the app's user, so the password reaches curl as config on stdin.
        val install = assertNotNull(SetupScripts.openCodeNativeInstall())
        assertFalse(install.contains("-u \"opencode:"), "the password is never a curl argument")
        assertTrue(install.contains("printf 'user = \"opencode:%s\"\\n' \"\$oc_pw\" | curl -K - -s --max-time 3"))
    }

    @Test
    fun `B-LINUX-200 the registry's OpenCode path scripts install only the pinned OpenCode 2 and check its own program`() {
        val install = assertNotNull(SetupScripts.openCodeNativeInstall())
        assertTrue(install.contains(OpenCodePins.assets.arm64.url))
        assertTrue(install.contains(OpenCodePins.assets.x64.sha256))
        assertTrue(install.contains("oc_download \"\$oc_url\" \"\$oc_file\" \"\$oc_sha\""))
        assertTrue(install.contains(OpenCodeInstallFailure.NO_START))
        assertTrue(install.trimEnd().endsWith("oc_version \"\$oc_got\""))
        assertFalse(install.contains("npm install -g"))
        val other = assertNotNull(SetupScripts.openCodeNativeInstall("2.2.6"))
        assertTrue(other.startsWith("echo '${OpenCodeInstallFailure.UNPINNED} (2.2.5, not 2.2.6)'"))
        assertNull(SetupScripts.openCodeNativeInstall("1.2.3; echo injected"))
        val check = assertNotNull(SetupScripts.openCodeNativeCheck())
        assertTrue(check.contains("[ \"\$(readlink -f \"\$oc_bin\")\" = '/opt/opencode2/bin/opencode2' ]"))
        assertTrue(check.contains("oc_update_recover()"))
        assertTrue(SetupScripts.NODE_INSTALL.contains("node_version=v24.22.2"))
        assertTrue(SetupScripts.NODE_CHECK.contains("= 'v24.22.2' ]"))
    }
}
