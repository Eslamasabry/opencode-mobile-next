package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import kotlinx.coroutines.delay

/**
 * The code block's Copy at the end of its first line (QA wave 4 N9): after a tap it says [copiedLabel] for
 * [COPIED_MS] (announced politely), and it adds no height of its own, so a one-line block stays one line tall.
 */
@Composable
internal fun BoxScope.CopyCorner(label: String, copiedLabel: String?, onCopy: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        copied = true
        delay(COPIED_MS)
        copied = false
    }
    DisableSelection {
        KitTappable(
            onClick = {
                onCopy()
                taps++
            },
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .offset(x = Space.S3.dp, y = (-14).dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        layout(placeable.width, 0) { placeable.place(0, 0) }
                    }
                    .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            KitText(
                if (copied && copiedLabel != null) copiedLabel else label,
                role = TypeRole.Label,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
            )
        }
    }
}

internal const val COPIED_MS: Long = 2_000
