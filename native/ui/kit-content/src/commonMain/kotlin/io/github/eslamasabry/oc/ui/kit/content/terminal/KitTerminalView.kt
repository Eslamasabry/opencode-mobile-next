package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate

/** Test tags of the terminal parts (the accessible-mode switch keeps Dart's key, plan 06 §7.7). */
public object KitTerminalTags {
    public const val ACCESSIBLE_MODE: String = "terminal-accessible-mode"
    public const val TRANSCRIPT: String = "terminal-accessible-transcript"
    public const val INPUT: String = "terminal-accessible-input"
    public const val SEND: String = "terminal-accessible-send"
    public const val ANNOUNCER: String = "terminal-accessible-announcer"
}

/** The words a terminal shows; the host screen passes them from `:ui:words` (no copy in the kit). */
@Immutable
public data class KitTerminalWords(
    /** The grid's one accessibility description ("Terminal"). */
    val terminal: String,
    /** The switch ("Accessible mode"). */
    val accessibleMode: String,
    /** The transcript list's description ("Terminal transcript"). */
    val transcript: String,
    /** Shown while the transcript is empty ("No terminal output yet."). */
    val noOutput: String,
    /** The field's label ("Send to terminal"). */
    val sendToTerminal: String,
    /** The send button ("Send"). */
    val send: String,
    /** Why input is off (the terminal ended or is not connected). */
    val inputUnavailable: String,
)

/** The newest lines a screen reader hears as they arrive (06 §7.7: the last 3, one announcement per second). */
internal const val ANNOUNCED_LINES: Int = 3
internal const val ANNOUNCE_EVERY_MS: Long = 1_000

/**
 * A live terminal with accessible mode (plan 06 §7.7 `KitTerminalView` `.live` + `.accessible`; DESKTOP-WEB-CROSS-30).
 *
 * The visible switch [KitTerminalTags.ACCESSIBLE_MODE] chooses between the live grid ([KitTerminalLive], one semantics
 * node) and the accessible view ([KitTerminalAccessible]: the transcript as text and a "Send to terminal" field).
 * [accessibleMode] is the person's choice; null follows the system: on by itself while TalkBack or Switch Access is on
 * (`KitSystemSignals.accessibleNavigation`), and the person can turn it off. Without a live [platformView] (`PlatformViewHandle.native`) (not started yet,
 * or a platform without a terminal view) only the accessible view can show.
 *
 * [onSend] receives exactly what to write: the field's value and a carriage return. [above] sits above the grid or the
 * transcript in both modes (the sign-in terminal's "Open sign-in page" and "Copy code", 17 §1.5).
 */
@Composable
public fun KitTerminalView(
    platformView: Any?,
    title: String,
    transcript: String,
    accessibleMode: Boolean?,
    onAccessibleModeChange: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    words: KitTerminalWords,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true,
    above: @Composable ColumnScope.() -> Unit = {},
) {
    val signals by LocalKitPlatform.current.signals.collectAsState()
    val on = KitTerminalRules.accessibleOn(accessibleMode, signals.accessibleNavigation, platformView != null)
    Column(modifier) {
        KitSwitchRow(
            title = words.accessibleMode,
            checked = on,
            modifier = Modifier.testTag(KitTerminalTags.ACCESSIBLE_MODE),
            enabled = if (platformView == null) KitEnabled.Disabled(words.inputUnavailable) else KitEnabled.Enabled,
            onCheckedChange = onAccessibleModeChange,
        )
        above()
        if (on || platformView == null) {
            KitTerminalAccessible(
                transcript = transcript,
                onSend = onSend,
                words = words,
                inputEnabled = inputEnabled,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            KitTerminalLive(platformView, title, words.terminal, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** Pure rules of [KitTerminalView] (unit-tested). */
public object KitTerminalRules {
    /** Accessible mode is the person's choice when made, else on with a screen reader; always on without a grid. */
    public fun accessibleOn(choice: Boolean?, screenReader: Boolean, hasGrid: Boolean): Boolean =
        !hasGrid || (choice ?: screenReader)

    /** The lines a screen reader hears for [lines]: the newest [ANNOUNCED_LINES], joined. */
    public fun announcement(lines: List<String>): String = lines.takeLast(ANNOUNCED_LINES).joinToString("\n")
}

/**
 * The accessible terminal (plan 06 §7.7 `.accessible`): the transcript stripped and capped ([TerminalTranscript]) as a
 * selectable list, one item per line (each merged into one node), newest at the bottom; the newest three lines are a
 * polite live region throttled to one announcement per second; and a mono "Send to terminal" field with a Send button
 * that writes the value and a carriage return, then clears the field.
 */
@Composable
public fun KitTerminalAccessible(
    transcript: String,
    onSend: (String) -> Unit,
    words: KitTerminalWords,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true,
) {
    val lines = remember(transcript) { TerminalTranscript.lines(TerminalTranscript.forDisplay(transcript)) }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }
    val latest by rememberUpdatedState(KitTerminalRules.announcement(lines))
    var spoken by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        snapshotFlow { latest }.conflate().collect {
            spoken = it
            delay(ANNOUNCE_EVERY_MS)
        }
    }
    val field = rememberTextFieldState()
    val send = {
        if (inputEnabled) {
            onSend(TerminalTranscript.sendLine(field.text.toString()))
            field.clearText()
        }
    }
    val enabled = if (inputEnabled) KitEnabled.Enabled else KitEnabled.Disabled(words.inputUnavailable)
    Column(modifier) {
        SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .testTag(KitTerminalTags.TRANSCRIPT)
                    .semantics { contentDescription = words.transcript },
                state = listState,
            ) {
                if (lines.isEmpty()) {
                    item { KitText(words.noOutput, tone = TextTone.Secondary) }
                } else {
                    itemsIndexed(lines) { _, line ->
                        KitText(line, Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, role = TypeRole.Mono)
                    }
                }
            }
        }
        Spacer(
            Modifier
                .size(1.dp)
                .testTag(KitTerminalTags.ANNOUNCER)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = spoken
                },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            KitField(
                state = field,
                label = words.sendToTerminal,
                modifier = Modifier.weight(1f).testTag(KitTerminalTags.INPUT),
                kind = KitFieldKind.Mono,
                imeAction = ImeAction.Send,
                enabled = enabled,
                onSubmit = send,
            )
            Spacer(Modifier.size(8.dp))
            KitButton(
                label = words.send,
                onClick = send,
                modifier = Modifier.testTag(KitTerminalTags.SEND),
                role = KitButtonRole.Secondary,
                enabled = enabled,
            )
        }
    }
}
