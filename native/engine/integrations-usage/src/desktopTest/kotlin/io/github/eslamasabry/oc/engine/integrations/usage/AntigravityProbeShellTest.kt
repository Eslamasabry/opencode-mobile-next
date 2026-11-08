package io.github.eslamasabry.oc.engine.integrations.usage

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Flutter PR #136: runs the real probe script under sh, with /home/oc moved into a temporary folder, a fake `agy`
// and a fake sign-in file. Proves only the checked email leaves the script, never the file or its tokens.

private const val FAKE_TOKEN_VALUE = "ya29.FAKE-access-value-for-test"
private const val FAKE_REFRESH_VALUE = "1//FAKE-refresh-value-for-test"

class AntigravityProbeShellTest {
    private val root: File = Files.createTempDirectory("oc-agy-probe").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun home(path: String): File = File(root, path.removePrefix("/home/oc/").removePrefix("/home/oc"))

    private fun install(at: String) {
        val bin = home(at).also { it.parentFile.mkdirs() }
        bin.writeText("#!/bin/sh\necho '{\"command\":{\"data\":{\"groups\":[]}},\"home\":\"'\"\$HOME\"'\"}'\n")
        bin.setExecutable(true)
    }

    private fun signIn(homeDir: String, emailJson: String) {
        val file = File(home(homeDir), ".gemini/antigravity-cli/antigravity-oauth-token")
        file.parentFile.mkdirs()
        file.writeText(
            "{\"access_token\":\"$FAKE_TOKEN_VALUE\",\"refresh_token\":\"$FAKE_REFRESH_VALUE\",$emailJson}"
        )
    }

    private fun probe(profileId: String? = "p1"): String {
        val script = AntigravityQuotaClient.cliScript(profileId).replace("/home/oc", root.absolutePath)
        val process = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        val out = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.exitValue(), out)
        assertFalse(FAKE_TOKEN_VALUE in out, "the token never leaves the script")
        assertFalse(FAKE_REFRESH_VALUE in out, "the refresh token never leaves the script")
        return out
    }

    @Test
    fun `B-183 the probe prints the profile's id_token_email once, then the CLI's own answer`() {
        install("/home/oc/.local/bin/agy")
        File(root, ".oc-profiles/p1").mkdirs()
        signIn("/home/oc/.oc-profiles/p1", "\"id_token_email\":\"altaf.alu@gmail.com\",\"email\":\"other@x.io\"")
        val out = probe()
        assertEquals(1, Regex("ACCOUNT_EMAIL:").findAll(out).count())
        assertEquals("altaf.alu@gmail.com", AntigravityQuotaClient.parseProbeAccount(out))
        assertTrue("\"home\":\"${root.absolutePath}/.oc-profiles/p1\"" in out, "the CLI runs with the profile's home")
    }

    @Test
    fun `B-183 the probe falls back to the email field, and to a signed-in profile when the shared home has none`() {
        install("/home/oc/.local/bin/agy")
        signIn("/home/oc/.oc-profiles/other", "\"email\":\"me@corp.io\"")
        val out = probe(profileId = null)
        assertEquals("me@corp.io", AntigravityQuotaClient.parseProbeAccount(out))
        assertTrue("\"home\":\"${root.absolutePath}/.oc-profiles/other\"" in out)
    }

    @Test
    fun `B-183 the probe prints no account for an unsafe or too long value, or without a sign-in file`() {
        install("/home/oc/.local/bin/agy")
        File(root, ".oc-profiles/p1").mkdirs()
        signIn("/home/oc/.oc-profiles/p1", "\"id_token_email\":\"me@x.io; rm -rf /\"")
        assertFalse("ACCOUNT_EMAIL" in probe())

        signIn("/home/oc/.oc-profiles/p1", "\"id_token_email\":\"${"a".repeat(60)}@corp.io\"")
        assertFalse("ACCOUNT_EMAIL" in probe())

        File(root, ".oc-profiles/p1/.gemini").deleteRecursively()
        val out = probe()
        assertFalse("ACCOUNT_EMAIL" in out)
        assertNull(AntigravityQuotaClient.parseProbeAccount(out))
        assertTrue("\"groups\"" in out, "the CLI still answers")
    }
}
