package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Direct bytes, never command-line text or a queued send; keys remain reachable in narrow windows.
 */
@Composable
public fun KitTerminalKeyBar(
    onSend: (String) -> Unit,
    enabled: KitEnabled,
    modifier: Modifier = Modifier,
) {
    val labels =
        listOf(
            TerminalBarKey.Interrupt to stringResource(Res.string.terminalParityInterrupt),
            TerminalBarKey.EndOfInput to stringResource(Res.string.terminalParityEndInput),
            TerminalBarKey.Escape to stringResource(Res.string.terminalParityEscape),
            TerminalBarKey.Tab to stringResource(Res.string.terminalParityTab),
        )
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("terminal-key-bar")
    ) {
        labels.forEach { (key, label) ->
            KitButton(
                label = label,
                onClick = { onSend(TerminalText.keyBytes(key)) },
                role = KitButtonRole.Tertiary,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("terminal-key-${key.name}"),
            )
        }
    }
}
