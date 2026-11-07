package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind

/**
 * A spec section whose draft lifecycle belongs to the caller (Dart `KitSpecBlock`, lib/ui/kit/team/kit_spec_block.dart):
 * a multiline field while it can be edited; read-only, the label over the words and the helper under them.
 *
 * States: empty, disabled
 * Text: reading
 * Cap: Reading (720)
 * Motion: none of its own (the field's)
 * Replaces: KitSpecBlock (lib/ui/kit/team/kit_spec_block.dart)
 */
@Composable
public fun KitSpecBlock(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    helper: String? = null,
    onTextChanged: ((String) -> Unit)? = null,
    readOnly: Boolean = false,
) {
    if (!readOnly) {
        KitField(
            state = state,
            label = label,
            modifier = modifier.kitMaxWidth(CapRole.Reading),
            kind = KitFieldKind.Multiline,
            helper = helper,
            onTextChanged = onTextChanged,
        )
        return
    }
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth()) {
        KitText(label, role = TypeRole.Label)
        Spacer(Modifier.height(Space.S2.dp))
        KitText(state.text.toString(), role = TypeRole.Body)
        helper?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
    }
}
