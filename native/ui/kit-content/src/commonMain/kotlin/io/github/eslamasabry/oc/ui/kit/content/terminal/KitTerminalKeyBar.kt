package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled

public data class KitTerminalKey(
    val label: String,
    val latched: Boolean? = null,
    val onClick: () -> Unit,
)

/** A visible, horizontally scrollable key bar shared by local and server terminals. */
@Composable
public fun KitTerminalKeyBar(
    keys: List<KitTerminalKey>,
    enabled: KitEnabled,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState())) {
        keys.forEach { key ->
            KitButton(
                label = key.label,
                onClick = key.onClick,
                role = if (key.latched == true) KitButtonRole.Primary else KitButtonRole.Secondary,
                enabled = enabled,
                modifier =
                    Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).semantics {
                        key.latched?.let {
                            toggleableState = if (it) ToggleableState.On else ToggleableState.Off
                        }
                    },
            )
        }
    }
}
