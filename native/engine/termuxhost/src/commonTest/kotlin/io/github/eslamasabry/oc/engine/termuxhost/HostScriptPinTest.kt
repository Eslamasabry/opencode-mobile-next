package io.github.eslamasabry.oc.engine.termuxhost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** test/host_script_pin_test.dart, the parts that need no checkout (the file checks are in desktopTest). */
class HostScriptPinTest {
    @Test
    fun `B-SEC-092 the pin is a full commit hash, never a branch or a tag`() {
        assertTrue(Regex("^[0-9a-f]{40}$").matches(HostScripts.COMMIT))
        val script = HostScripts.front
        assertTrue(Regex("^[0-9a-f]{64}$").matches(script.sha256))
        assertEquals(
            "https://raw.githubusercontent.com/${HostScripts.REPOSITORY}/${HostScripts.COMMIT}/${script.path}",
            script.url,
        )
        assertFalse(script.url.contains("/master/"))
        assertFalse(script.url.contains("/main/"))
    }

    @Test
    fun `B-SEC-092 the command checks the download before it is named`() {
        val script = HostScripts.front
        val lines = script.verifiedDownload.split('\n')
        assertTrue(lines.first().startsWith("curl -fsSLo ${script.saveAs}.part"))
        assertTrue(script.verifiedDownload.contains(script.url))
        assertTrue(script.verifiedDownload.contains("echo '${script.sha256}  ${script.saveAs}.part' | sha256sum -c - &&"))
        assertEquals("mv ${script.saveAs}.part ${script.saveAs}", lines.last())
        assertFalse(HostScripts.PipedToShell.containsMatchIn(script.verifiedDownload))
    }

    @Test
    fun `B-SEC-092 install uses the official pinned OpenCode 2 package directly`() {
        val command = HostScripts.install(4747)
        assertEquals("npm install -g @opencode/cli@2.2.5 &&\nopencode2 serve --hostname 127.0.0.1 --port 4747", command)
        assertFalse(command.contains("ubuntu-opencode.sh"))
        assertFalse(command.contains("opencode-ai"))
    }

    @Test
    fun `B-SEC-092 the front command checks, then runs the checked copy`() {
        val command = HostScripts.teamFront
        assertTrue(command.startsWith(HostScripts.front.verifiedDownload))
        assertTrue(command.contains("python3 opencode-mobile-front.py "))
        assertFalse(command.contains("python3 tool/"))
    }

    @Test
    fun `B-SEC-092 the piped-download pattern catches curl and wget into a shell`() {
        assertTrue(HostScripts.PipedToShell.containsMatchIn("curl -fsSL https://example.invalid/x | bash"))
        assertTrue(HostScripts.PipedToShell.containsMatchIn("wget -qO- https://example.invalid/x | sudo sh"))
        assertFalse(HostScripts.PipedToShell.containsMatchIn("curl -fsSLo x.part https://example.invalid/x &&"))
    }
}
