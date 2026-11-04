package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * One terminal key. Two shapes share it: a key that runs [onClick] (`KitTerminalKey(label, latched) { … }`, read by
 * [KitTerminalKeyBar]) and a key named by protocol [id] (`KitTerminalKey(id, label, selected)`, read by
 * [KitTerminalKeybar], which hands the id to its `onKey`). A key name is protocol data; its face and accessible name
 * come from native string resources.
 */
@Immutable
public data class KitTerminalKey(
    val label: String,
    val latched: Boolean? = null,
    val testTag: String? = null,
    val id: String = label,
    val onClick: () -> Unit = {},
) {
    /** A key named by protocol [id]; [selected] marks a latched modifier. */
    public constructor(id: String, label: String, selected: Boolean? = null) : this(label = label, latched = selected, id = id)

    /** The latched state under its id-keyed name. */
    val selected: Boolean? get() = latched
}

/** A visible, horizontally scrollable key bar shared by local and server terminals. */
@Composable
public fun KitTerminalKeyBar(
    keys: List<KitTerminalKey>,
    enabled: KitEnabled,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("terminal-key-bar")
    ) {
        keys.forEach { key ->
            KitButton(
                label = key.label,
                onClick = key.onClick,
                role = if (key.latched == true) KitButtonRole.Primary else KitButtonRole.Secondary,
                enabled = enabled,
                modifier =
                    Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .let { m -> key.testTag?.let { m.testTag(it) } ?: m }
                        .semantics {
                            key.latched?.let {
                                toggleableState =
                                    if (it) ToggleableState.On else ToggleableState.Off
                            }
                        },
            )
        }
    }
}

/**
 * Ctrl-C and Ctrl-D as one-tap keys (Dart `interruptKeys`): screens with their own key list put
 * these first so a running command can always be stopped.
 */
@Composable
public fun kitTerminalInterruptKeys(onSend: (String) -> Unit): List<KitTerminalKey> =
    controlKeys(listOf(TerminalBarKey.Interrupt, TerminalBarKey.EndOfInput), onSend)

/**
 * The default control-byte bar (Ctrl-C, Ctrl-D, Esc, Tab) for terminals whose screen does not
 * supply its own keys. Direct bytes, never command-line text or a queued send; keys remain
 * reachable in narrow windows.
 */
@Composable
public fun KitTerminalKeyBar(
    onSend: (String) -> Unit,
    enabled: KitEnabled,
    modifier: Modifier = Modifier,
) {
    KitTerminalKeyBar(
        keys = controlKeys(TerminalBarKey.entries, onSend),
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
private fun controlKeys(
    keys: List<TerminalBarKey>,
    onSend: (String) -> Unit,
): List<KitTerminalKey> =
    keys.map { key ->
        KitTerminalKey(
            label =
                stringResource(
                    when (key) {
                        TerminalBarKey.Interrupt -> Res.string.terminalParityInterrupt
                        TerminalBarKey.EndOfInput -> Res.string.terminalParityEndInput
                        TerminalBarKey.Escape -> Res.string.terminalParityEscape
                        TerminalBarKey.Tab -> Res.string.terminalParityTab
                    }
                ),
            testTag = "terminal-key-${key.name}",
            onClick = { onSend(TerminalText.keyBytes(key)) },
        )
    }

/**
 * The single terminal key row keyed by protocol id: minimum kit targets, one shared horizontal scroll, labelled
 * modifier selection. Each tap hands the key's [KitTerminalKey.id] to [onKey].
 */
@Composable
public fun KitTerminalKeybar(
    keys: List<KitTerminalKey>,
    enabled: KitEnabled,
    onKey: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { key ->
            KitButton(
                label = key.label,
                onClick = { onKey(key.id) },
                modifier = Modifier.semantics { key.selected?.let { selected = it } },
                role =
                    if (key.selected == true) KitButtonRole.Secondary else KitButtonRole.Tertiary,
                enabled = enabled,
            )
        }
    }
}
