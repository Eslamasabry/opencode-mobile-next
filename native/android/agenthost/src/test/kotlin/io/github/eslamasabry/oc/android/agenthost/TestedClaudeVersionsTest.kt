package io.github.eslamasabry.oc.android.agenthost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plan 17 §1.3: `TESTED_CLAUDE_VERSIONS` (here) and `claudeCodeTestedVersions` in
 * `native/engine/agents/pins/agent-pins.json`
 * (the source of truth since 2026-11-13; it began as the Flutter app's `lib/paseo/tested_versions.dart`) must be equal.
 */
class TestedClaudeVersionsTest {
    private val repo = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "native/settings.gradle.kts").isFile }

    private fun native(): Set<String> =
        PhoneAgentSignIn.TESTED_CLAUDE_VERSIONS.map { it.removeSuffix(" (Claude Code)") }.toSet()

    @Test fun `B-AGENT-050 the native sign-in check accepts every tested Claude build`() {
        assertFalse(native().isEmpty())
        assertTrue(PhoneAgentSignIn.TESTED_CLAUDE_VERSIONS.all { it.endsWith(" (Claude Code)") })
    }

    @Test fun `B-AGENT-050 the native list equals the agents engine pins`() {
        val pins = File(repo, "native/engine/agents/pins/agent-pins.json")
        val json = org.json.JSONObject(pins.readText()).getJSONArray("claudeCodeTestedVersions")
        assertEquals((0 until json.length()).map { json.getString(it) }.toSet(), native())
    }
}
