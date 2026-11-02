package io.github.eslamasabry.oc.engine.agents.claudeagent

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The root script is the Dart one byte for byte: this renders `ClaudeAgentLink.script` from the Dart source itself
 * (Dart's own string rules, independently of the generated Kotlin template) and compares.
 */
class ClaudeAgentLinkScriptParityTest {
    private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "lib/builtin/claude_agent_link.dart").isFile }

    @Test
    fun `B-AGENT-409 the root script equals the Dart freeze tip byte for byte`() {
        val dart = File(repo, "lib/builtin/claude_agent_link.dart").readText()
        val marker = Regex("static const shimMarker = '([^']*)';").find(dart)!!.groupValues[1]
        var body = dart.substring(dart.indexOf("return '''") + "return '''".length, dart.indexOf("''';"))
        // Dart drops the first line of a triple-quoted string when it holds only whitespace.
        val firstBreak = body.indexOf('\n')
        if (firstBreak >= 0 && body.substring(0, firstBreak).isBlank()) body = body.substring(firstBreak + 1)
        val owner = "owner-1"
        val expected = body
            .replace("\\\$", "\u0000") // a Dart-escaped dollar is literal text
            .replace("\$shimMarker", marker)
            .replace("\$home", "/home/oc/.oc-profiles/$owner/claude")
            .replace("\u0000", "\$")
        assertEquals(marker, ClaudeAgentLink.SHIM_MARKER)
        assertEquals(expected, ClaudeAgentLink.script(ProfileId(owner)))
    }
}
