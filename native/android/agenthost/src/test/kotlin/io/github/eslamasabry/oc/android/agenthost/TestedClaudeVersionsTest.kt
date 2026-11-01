package io.github.eslamasabry.oc.android.agenthost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plan 17 §1.3: `TESTED_CLAUDE_VERSIONS` (here) and `claudeCodeTestedVersions` (the pins) must be equal. The pins'
 * source is `lib/paseo/tested_versions.dart` at the freeze tip; once `:engine:agents` is in the tree its
 * `pins/agent-pins.json` is checked too.
 */
class TestedClaudeVersionsTest {
    private val repo = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "lib/paseo/tested_versions.dart").isFile }

    private fun native(): Set<String> =
        PhoneAgentSignIn.TESTED_CLAUDE_VERSIONS.map { it.removeSuffix(" (Claude Code)") }.toSet()

    @Test fun `B-AGENT-050 the native sign-in check accepts every tested Claude build`() {
        val dart = File(repo, "lib/paseo/tested_versions.dart").readText()
        val declared = Regex("""claudeCodeTestedVersions = \{([^}]*)\}""").find(dart)?.groupValues?.get(1)
        val tested = Regex("""'([0-9.]+)'""").findAll(checkNotNull(declared)).map { it.groupValues[1] }.toSet()
        assertTrue(tested.isNotEmpty())
        assertEquals(tested, native())
        assertTrue(PhoneAgentSignIn.TESTED_CLAUDE_VERSIONS.all { it.endsWith(" (Claude Code)") })
    }

    @Test fun `B-AGENT-050 the native list equals the agents engine pins when they are present`() {
        val pins = File(repo, "native/engine/agents/pins/agent-pins.json")
        if (!pins.isFile) return
        val json = org.json.JSONObject(pins.readText()).getJSONArray("claudeCodeTestedVersions")
        assertEquals((0 until json.length()).map { json.getString(it) }.toSet(), native())
        assertFalse(native().isEmpty())
    }
}
