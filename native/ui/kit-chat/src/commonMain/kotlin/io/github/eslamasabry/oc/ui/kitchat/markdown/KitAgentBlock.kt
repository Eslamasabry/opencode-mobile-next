package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind

/**
 * Flutter `agent_blocks.dart`: the three rich fences an agent can send. Choices are rows that fill the message box
 * (copied when the host gives no insert), a checklist is read-only with the state said in words as well as the mark
 * (done is muted, never struck through), and commands are a "Run on your computer" code block with Copy.
 */
@Composable
public fun KitAgentBlock(block: KitMdBlock.Agent, actions: KitMarkdownActions, modifier: Modifier = Modifier) {
    if (block.items.isEmpty()) return
    when (block.kind) {
        KitAgentKind.Choices ->
            DisableSelection {
                val pick = actions.onChoice ?: actions.onCopyCode
                KitRowGroup(modifier.testTag("agent-choices-block")) {
                    block.items.forEachIndexed { i, item ->
                        KitRow(
                            title = item.text,
                            titleMaxLines = 4,
                            trailing = KitRowTrailing.Chevron,
                            onClickLabel = actions.chooseLabel(item.text),
                            onClick = pick?.let { p -> { p(item.text) } },
                            modifier = Modifier.testTag("agent-choice-$i"),
                        )
                    }
                }
            }
        KitAgentKind.Checklist ->
            DisableSelection {
                Column(modifier.testTag("agent-checklist-block")) {
                    block.items.forEachIndexed { i, item ->
                        val word = if (item.done) actions.doneWord else actions.todoWord
                        KitRow(
                            title = item.text,
                            titleMaxLines = 6,
                            leading =
                                KitRowLeading.Glyph(
                                    if (item.done) KitIcons.checkCircle else KitIcons.radioEmpty,
                                ),
                            leadingTone = if (item.done) TextTone.Success else TextTone.Tertiary,
                            modifier =
                                Modifier.testTag(if (item.done) "agent-check-done" else "agent-check-open")
                                    .clearAndSetSemantics { contentDescription = "$word: ${item.text}" },
                        )
                    }
                }
            }
        KitAgentKind.Command ->
            KitCodeBlock(
                source = block.items.joinToString("\n") { it.text },
                kind = KitCodeKind.Command,
                modifier = modifier.testTag("agent-command-block"),
                caption = actions.runOnComputerLabel,
                copyLabel = actions.copyCodeLabel,
                onCopy = actions.onCopyCode,
            )
    }
}
