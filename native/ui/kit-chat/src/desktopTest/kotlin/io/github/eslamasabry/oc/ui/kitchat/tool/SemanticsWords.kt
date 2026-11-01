package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider

/**
 * Every word the semantics tree carries (text, editable text, content and state descriptions), unmerged and merged:
 * what a screen reader, a test or an accessibility service can read off the screen.
 */
internal fun SemanticsNodeInteractionsProvider.semanticsWords(): String {
    val any = SemanticsMatcher("any node") { true }
    val nodes =
        onAllNodes(any, useUnmergedTree = true).fetchSemanticsNodes() +
            onAllNodes(any, useUnmergedTree = false).fetchSemanticsNodes()
    return nodes.joinToString("\n") { node ->
        val c = node.config
        buildList {
            c.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
            c.getOrNull(SemanticsProperties.EditableText)?.let { add(it.text) }
            c.getOrNull(SemanticsProperties.ContentDescription)?.let { addAll(it) }
            c.getOrNull(SemanticsProperties.StateDescription)?.let { add(it) }
        }.joinToString("\n")
    }
}
