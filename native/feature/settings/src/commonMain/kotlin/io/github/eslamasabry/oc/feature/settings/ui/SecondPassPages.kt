package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import androidx.compose.runtime.*
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ColumnScope.PhraseChipsBody(on: Boolean, actions: SettingsActions) {
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.nativePhraseChipsTitle),
            supporting = stringResource(Res.string.nativePhraseChipsDescription),
            checked = on,
            onCheckedChange = actions::onPhraseChips,
        )
    }
}

/** Omit the maintainer introduction and regeneration instructions exactly as Flutter does. */
public fun readerNotices(markdown: String): String {
    val lines = markdown.lines()
    val start = lines.indexOfFirst { it.startsWith("## ") }
    if (start < 0) return markdown
    val end =
        (start until lines.size).firstOrNull { lines[it].startsWith("## Regenerating") }
            ?: lines.size
    return lines.subList(start, end).joinToString("\n").trim()
}

/**
 * The notices as the page reads them (audit C07): the page's own title is not repeated as the first heading, and a
 * link becomes its words, since a bundled licence file is not an address the phone can open; a web address stays
 * readable in brackets after its words.
 */
public fun noticesForReading(notices: String): String {
    val lines = notices.lines()
    val body = if (lines.firstOrNull()?.startsWith("## ") == true) lines.drop(1) else lines
    return unwrapNotices(body).trim().replace(NOTICE_LINK) { m ->
        val words = m.groupValues[1]
        val target = m.groupValues[2]
        if (target.startsWith("http://") || target.startsWith("https://")) "$words ($target)" else words
    }
}

private val NOTICE_LINK = Regex("""\[([^\]]+)\]\(([^)\s]+)\)""")

/** A line that starts its own block: a heading, a list item, a quote, a table row or a code fence. */
private val NOTICE_BLOCK_START = Regex("""^\s*(#|[-*+]\s|\d+[.)]\s|>|\||```)""")

/**
 * The notices file is wrapped at 80 columns for reading as source; on a phone those breaks cut sentences in the middle,
 * so a line that only continues the paragraph above joins it with a space. Code blocks keep their lines.
 */
internal fun unwrapNotices(lines: List<String>): String {
    val out = StringBuilder()
    var fenced = false
    var previous: String? = null
    for (line in lines) {
        val fence = line.trimStart().startsWith("```")
        val continues =
            !fenced && !fence && line.isNotBlank() && previous != null && previous.isNotBlank() &&
                !NOTICE_BLOCK_START.containsMatchIn(line) && !previous.trimStart().startsWith("#") &&
                !previous.trimStart().startsWith("|")
        if (continues) out.append(' ').append(line.trim()) else {
            if (previous != null) out.append('\n')
            out.append(line)
        }
        if (fence) fenced = !fenced
        previous = line
    }
    return out.toString()
}

@Composable
internal fun ColumnScope.NoticesBody(file: String = "files/THIRD_PARTY_NOTICES.md") {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        text = noticesForReading(readerNotices(Res.readBytes(file).decodeToString()))
    }
    val content = text
    if (content == null) {
        KitStateView(KitStateKind.Loading, stringResource(Res.string.aboutAllLicences))
        return
    }
    // C07: the notices are Markdown, drawn by the kit's Markdown part (headings, lists, code names), never shown as
    // raw text with its # and backticks; C02: on the page gutter.
    val blocks = remember(content) { KitMdSource.blocks("notices", content, streaming = false) }
    KitMarkdown(
        blocks,
        KitMarkdownActions(onLink = {}),
        Modifier.fillMaxWidth()
            .padding(horizontal = kitPageGutter(), vertical = Space.S2.dp)
            .testTag("settings-notices"),
    )
}
