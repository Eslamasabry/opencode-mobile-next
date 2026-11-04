package io.github.eslamasabry.oc.ui.kit.content.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText

/** Bounded selectable output. Levels are spoken in words as well as shown in the attention tone. */
@Composable
public fun KitLogPanel(
    lines: List<KitLogLine>,
    title: String,
    warningLabel: String,
    errorLabel: String,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberLazyListState()
    val follow = !scroll.canScrollForward
    LaunchedEffect(lines) {
        if (follow && lines.isNotEmpty()) scroll.scrollToItem(lines.lastIndex)
    }
    Column(modifier.fillMaxWidth()) {
        KitText(title, role = TypeRole.Headline)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), state = scroll) {
                    itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
                        val label =
                            when (line.level) {
                                KitLogLevel.Normal -> line.text
                                KitLogLevel.Warning -> "$warningLabel: ${line.text}"
                                KitLogLevel.Error -> "$errorLabel: ${line.text}"
                            }
                        KitText(
                            label.ifEmpty { " " },
                            role = TypeRole.Mono,
                            tone =
                                if (line.level == KitLogLevel.Normal) TextTone.Secondary
                                else TextTone.Attention,
                        )
                    }
                }
            }
        }
    }
}
