package io.github.eslamasabry.opencode_mobile.graph

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * signin-truth-2 (Galaxy, build 10055): Claude Code's "Hi" and "Reply with OK" opened EMPTY; build 10050 showed their
 * weekly-limit reply. Since issue 147's no-wake rule (PaseoGateway.mayWake) the phone agents' helper reads an idle
 * conversation's history only once it is marked opened (HistoryOpenGateway.historyOpened); any other read gets the
 * gateway's kept page, empty after an app start. A row's prefetch (ConnectionReads.prefetch) is held and then taken by
 * the opening chat as its whole first page. The home marked the row opened before its prefetch; All conversations and
 * Projects (LiveConversationServers) did not, so their prefetch held an empty page and the chat opened on it.
 *
 * The class of fault: every prefetch of a row about to open marks it opened first, in the same function.
 */
class PrefetchOpensHistoryTest {
    @Test
    fun `I147-TRUTH-41 every prefetch of a conversation about to open marks its history opened first`() {
        val sources =
            File("src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val prefetches = mutableListOf<String>()
        for (file in sources) {
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (!line.contains("reads.prefetch(") && !line.contains("reads?.prefetch(")) return@forEachIndexed
                prefetches += "${file.name}:${index + 1}"
                // The function around it: back to its `fun` line.
                fun opensFunction(l: String) = l.trimStart().startsWith("fun ") || l.contains(" fun ")
                val start = (index downTo 0).firstOrNull { opensFunction(lines[it]) } ?: 0
                val body = lines.subList(start, index + 1).joinToString("\n")
                assertTrue(
                    "historyOpened(" in body,
                    "${file.name}:${index + 1} prefetches a conversation without marking its history opened first",
                )
            }
        }
        assertTrue(prefetches.size >= 2, "the home's and All conversations' prefetch are both found: $prefetches")
    }
}
