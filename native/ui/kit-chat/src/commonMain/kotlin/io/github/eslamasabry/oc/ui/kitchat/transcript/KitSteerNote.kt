package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled

/**
 * The quiet note under a message the agent reads once its running step is done ("{agent} reads this when this step
 * finishes"), with a Stop beside it once the step runs long (Flutter `_PromptNote`, `KitMessage.noteAction`). The
 * button names what it stops; [onStop] null hides it. While [stopping] it waits, disabled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitSteerNote(
    text: String,
    stopLabel: String,
    onStop: (() -> Unit)?,
    modifier: Modifier = Modifier,
    stopping: Boolean = false,
) {
    FlowRow(
        modifier = modifier.then(column()),
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDirection = TextDirection.Content,
                ),
        )
        if (onStop != null)
            KitButton(
                stopLabel,
                onStop,
                role = KitButtonRole.Tertiary,
                enabled = KitEnabled.of(!stopping, null),
                expand = false,
            )
    }
}
