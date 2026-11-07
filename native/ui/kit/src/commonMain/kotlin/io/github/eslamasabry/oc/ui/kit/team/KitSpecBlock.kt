package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A spec section whose draft lifecycle belongs to the caller (a port of Dart `KitSpecBlock`,
 * lib/ui/kit/team/kit_spec_block.dart). Editable: a multi-line [KitField] with the label and helper. [readOnly]: the
 * label, a space2 gap, the text as body words and the helper under it. States: empty, disabled.
 */
@Composable
public fun KitSpecBlock(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    helper: String? = null,
    onChanged: ((String) -> Unit)? = null,
    readOnly: Boolean = false,
) {
    val outer = modifier.kitMaxWidth(CapRole.Reading)
    if (!readOnly) {
        val changed = rememberUpdatedState(onChanged)
        LaunchedEffect(state) {
            snapshotFlow { state.text.toString() }.distinctUntilChanged().drop(1).collect { changed.value?.invoke(it) }
        }
        KitField(state, label, outer, kind = KitFieldKind.Multiline, helper = helper)
        return
    }
    Column(outer.fillMaxWidth()) {
        KitText(label, role = TypeRole.Label)
        Spacer(Modifier.height(Space.S2.dp))
        KitText(state.text.toString(), role = TypeRole.Body)
        helper?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
    }
}
