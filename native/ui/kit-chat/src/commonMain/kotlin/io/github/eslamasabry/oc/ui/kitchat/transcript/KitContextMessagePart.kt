package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButtonCopy

/** Flutter active_context_screen.dart's _Part: a label and Copy, followed by selectable plain or mono text. */
@Composable
public fun KitContextMessagePart(
    heading: String,
    text: String,
    copyLabel: String,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    technical: Boolean = false,
    dropped: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitText(heading, Modifier.weight(1f), role = TypeRole.Label)
            if (text.isNotEmpty() && !dropped) KitIconButtonCopy(text = { text }, label = copyLabel)
        }
        Spacer(Modifier.height(Space.S2.dp))
        if (!dropped) {
            if (text.isEmpty()) KitText(emptyLabel, role = TypeRole.Secondary, tone = TextTone.Secondary)
            else SelectionContainer {
                if (technical) KitMonoText(text) else KitText(text)
            }
        }
    }
}
