package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of the `server address`, `scripts` and `shared OpenCode setup script` groups of test/builtin_linux_test.dart. */
class BuiltinScriptsTest {
    private fun install(runtime: OpenCodeRuntime = OpenCodeRuntime.DefaultForNewInstall): String =
        BuiltinScripts.installOpenCodeScript(runtime).getOrNull()!!

    private fun server(): String = BuiltinScripts.serverScript().getOrNull()!!

    @Test
    fun `B-LINUX-023 the in-app server is its own port, never the Termux-managed one`() {
        assertEquals("http://127.0.0.1:4097", BuiltinLinuxBridge.SERVER_URL)
        assertTrue(BuiltinLinuxBridge.managesServerUrl(BuiltinLinuxBridge.SERVER_URL))
        // The Termux-managed server (TermuxBridge.managedServerUrl) is 127.0.0.1:4096.
        assertFalse(BuiltinLinuxBridge.managesServerUrl("http://127.0.0.1:4096"))
        assertFalse(BuiltinLinuxBridge.managesServerUrl("http://0.0.0.0:4097"))
        assertFalse(BuiltinLinuxBridge.managesServerUrl("https://127.0.0.1:4097"))
        assertFalse(BuiltinLinuxBridge.managesServerUrl(null))
        assertTrue(BuiltinLinuxBridge.managesServerUrl("http://127.0.0.1:4097/api"))
    }

    @Test
    fun `B-LINUX-023 fresh script defaults install and start OpenCode 2 at its pin`() {
        assertContains(install(), "export OC_RUNTIME='opencode2'")
        assertContains(install(), "export OC_REQUESTED_VERSION='2.2.5'")
        assertContains(server(), "exec opencode2 serve --hostname 127.0.0.1 --port 4097")
    }

    @Test
    fun `B-LINUX-023 the install uses the shared setup text at the pinned version`() {
        val script = install(OpenCodeRuntime.OpenCode2)
        assertContains(script, "export OC_REQUESTED_VERSION='${OpenCodeRuntime.OpenCode2.pinnedVersion}'")
        assertContains(script, "export OC_RUNTIME='opencode2'")
        assertContains(script, "bash -s <<'OC_PROOT_SETUP'\n${BuiltinScriptTexts.UBUNTU_SETUP}OC_PROOT_SETUP\n")
        assertFalse(script.contains("opencode models --refresh"))
        assertFalse(script.contains("models --refresh"))
        assertEquals(Failure.Unexpected(), (BuiltinScripts.installOpenCodeScript(version = "1; rm -rf /") as Outcome.Failed).failure)
    }

    @Test
    fun `B-LINUX-023 the server binds 127_0_0_1 only and reads its password file`() {
        val script = server()
        assertContains(script, "exec opencode2 serve --hostname 127.0.0.1 --port 4097\n")
        assertContains(script, "cd /root/projects")
        assertContains(script, "export OPENCODE_SERVER_USERNAME=opencode")
        assertContains(script, "export OPENCODE_SERVER_PASSWORD=\"\$password\"")
        assertContains(script, "export OPENCODE_PASSWORD=\"\$password\"")
        assertContains(script, "cat ${BuiltinScripts.PASSWORD_FILE}")
        assertContains(script, "XDG_DATA_HOME")
        assertFalse(script.contains("0.0.0.0"))
        for (line in listOf(
            "export XDG_DATA_HOME=/root/.oc-opencode2/data",
            "export XDG_CACHE_HOME=/root/.oc-opencode2/cache",
            "export XDG_STATE_HOME=/root/.oc-opencode2/state",
            "export XDG_CONFIG_HOME=/root/.oc-opencode2/config",
            "export OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode",
            "export OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db",
            "unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT",
        )) {
            assertContains(script, line)
        }
        assertIs<Outcome.Failed>(BuiltinScripts.serverScript(port = 80))
    }

    @Test
    fun `B-LINUX-023 the password is quoted and never in the server script`() {
        val write = BuiltinScripts.writePasswordScript("it's-secret")!!
        assertContains(write, "umask 077")
        assertContains(write, "'it'\"'\"'s-secret'")
        assertFalse(server().contains("secret"))
        assertNull(BuiltinScripts.writePasswordScript(""))
        assertEquals("write-password", BuiltinScripts.scriptLabel(write))
    }

    @Test
    fun `B-LINUX-024 versions are read the way the Termux manager trims them`() {
        assertEquals("1.18.29", BuiltinScripts.parseVersion("1.18.29\n"))
        assertEquals("2.0.10", BuiltinScripts.parseVersion("opencode2 v2.0.10"))
        assertEquals("2.0.10", BuiltinScripts.parseVersion("opencode v2.0.10\n\n"))
        assertNull(BuiltinScripts.parseVersion(""))
        assertNull(BuiltinScripts.parseVersion("command not found"))
    }

    @Test
    fun `B-LINUX-024 create refuses a relative path`() {
        assertNull(BuiltinScripts.createFolderScript("projects/x"))
        assertNull(BuiltinScripts.repoStateScript("projects/x"))
        assertNull(BuiltinScripts.firstCommitScript("projects/x"))
        assertNull(BuiltinScripts.createFolderScript("/root/projects/a\nb"))
    }

    @Test
    fun `B-LINUX-024 legacy identity is detection only and cannot install or start`() {
        assertEquals(OpenCodeRuntime.OpenCode2, OpenCodeRuntime.DefaultForNewInstall)
        val legacy = Failure.Incompatible(IncompatibleKind.LegacyOpenCode1)
        assertEquals(legacy, (BuiltinScripts.installOpenCodeScript(OpenCodeRuntime.OpenCode1) as Outcome.Failed).failure)
        assertEquals(legacy, (BuiltinScripts.serverScript(OpenCodeRuntime.OpenCode1) as Outcome.Failed).failure)
    }

    @Test
    fun `B-LINUX-024 the shared setup text ends with the version check and holds no heredoc terminator`() {
        assertTrue(BuiltinScriptTexts.UBUNTU_SETUP.endsWith("\"\$command\" --version\n"))
        assertFalse(BuiltinScriptTexts.UBUNTU_SETUP.contains("\nOC_PROOT_SETUP\n"))
        assertTrue(install().endsWith("bash -s <<'OC_PROOT_SETUP'\n${BuiltinScriptTexts.UBUNTU_SETUP}OC_PROOT_SETUP\n"))
    }

    @Test
    fun `B-LINUX-024 project lists leave hidden folders out`() {
        assertEquals(listOf("it's-new"), BuiltinScripts.parseProjectList("it's-new\n.cache\n\n"))
        assertEquals("find /root/projects -mindepth 1 -maxdepth 1 -type d -printf '%f\\n' | sort", BuiltinScripts.listProjectsScript().lines()[2])
    }
}
