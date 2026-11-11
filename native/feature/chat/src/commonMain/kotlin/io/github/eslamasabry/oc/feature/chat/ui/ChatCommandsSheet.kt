package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commandLauncherSubtitle
import io.github.eslamasabry.oc.ui.words.res.composerFullCommands
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import org.jetbrains.compose.resources.stringResource

/**
 * One row of the commands launcher (Flutter `CommandSheetEntry`). The app's own actions carry
 * [onRun]; a server command's [onRun] puts `/name ` in the message box. Words are resolved by the
 * screen; this class holds none of its own.
 */
internal class ChatCommandEntry(
    val key: String,
    val slash: String,
    val title: String,
    val description: String?,
    val group: String,
    val icon: ImageVector?,
    val aliases: List<String> = emptyList(),
    val onRun: () -> Unit,
) {
    fun matches(query: String): Boolean {
        val q = query.trim().removePrefix("/").lowercase()
        if (q.isEmpty()) return true
        return title.lowercase().contains(q) ||
            slash.lowercase().contains(q) ||
            aliases.any { it.lowercase().contains(q) } ||
            description?.lowercase()?.contains(q) == true
    }
}

/** The launcher's keyboard: which row is lit, and what a key does to it (pure, so every case is a plain test). */
internal object LauncherKeys {
    /** Arrow keys move the lit row by [delta] and stop at the ends; an empty list lights nothing. */
    fun move(lit: Int, delta: Int, count: Int): Int = if (count <= 0) 0 else (lit + delta).coerceIn(0, count - 1)

    /** Enter runs the lit row; with no row to run it does nothing. */
    fun run(entries: List<ChatCommandEntry>, lit: Int): ChatCommandEntry? = entries.getOrNull(lit.coerceAtLeast(0))
}

/**
 * "Commands" (Flutter `command_launcher.dart` and the command sheet): a searchable list, grouped,
 * that runs the action and closes. Rows are 48 dp kit rows; nothing here animates by itself.
 */
@Composable
internal fun ChatCommandsSheet(
    visible: Boolean,
    entries: List<ChatCommandEntry>,
    onDismiss: () -> Unit,
) {
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.composerFullCommands),
        subtitle = stringResource(Res.string.commandLauncherSubtitle),
        closeLabel = stringResource(Res.string.kitSheetDismiss),
    ) {
        val field = rememberTextFieldState()
        val query = field.text.toString()
        val shown = remember(entries, query) { entries.filter { it.matches(query) } }
        // Hardware keyboard: the first match is lit; Up/Down move it, Enter runs it, Esc on an empty field closes.
        var lit by remember(query) { mutableIntStateOf(0) }
        fun runLit() {
            val entry = LauncherKeys.run(shown, lit) ?: return
            onDismiss()
            entry.onRun()
        }
        Column(
            Modifier.fillMaxWidth().testTag("chat-commands-sheet").onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> { lit = LauncherKeys.move(lit, 1, shown.size); true }
                    Key.DirectionUp -> { lit = LauncherKeys.move(lit, -1, shown.size); true }
                    Key.Escape -> if (query.isEmpty()) { onDismiss(); true } else false
                    else -> false
                }
            },
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitSearchField(
                label = stringResource(Res.string.composerFullCommands),
                state = field,
                modifier = Modifier.testTag("chat-commands-search"),
                onSearch = {},
                onSubmit = { runLit() },
                resultCount = if (query.isBlank()) null else shown.size,
            )
            if (shown.isEmpty()) {
                KitSearchNoMatch(
                    query,
                    { field.edit { replace(0, length, "") } },
                    Modifier.testTag("chat-commands-no-match"),
                )
            }
            shown.groupBy { it.group }.forEach { (group, rows) ->
                KitRowGroup(label = group) {
                    rows.forEach { entry ->
                        KitRow(
                            selected = shown.indexOf(entry) == lit,
                            title = entry.title,
                            supporting = entry.description,
                            supportingMaxLines = 2,
                            meta = "/" + entry.slash,
                            leading = entry.icon?.let { KitRowLeading.Glyph(it) },
                            modifier = Modifier.testTag("chat-command-${entry.key}"),
                            onClick = {
                                onDismiss()
                                entry.onRun()
                            },
                        )
                    }
                }
            }
        }
    }
}
