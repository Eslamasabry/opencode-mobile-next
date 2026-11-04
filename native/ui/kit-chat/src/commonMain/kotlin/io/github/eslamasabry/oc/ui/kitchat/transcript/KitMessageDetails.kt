package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource

/** A visible reasoning fold. The collapsed preview never truncates the expanded Markdown body. */
@Composable
public fun KitThoughtBlock(
    key: String,
    title: String,
    text: String,
    live: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(key, text, live) { KitMdSource.blocks(key, text, live) }
    val colors = LocalKitColors.current
    CompositionLocalProvider(LocalKitColors provides colors.copy(text1 = colors.text2)) {
        KitExpandRow(title, expanded, modifier, onExpandedChange = onExpandedChange) {
            KitMarkdown(blocks, actions, live = live)
        }
    }
}

/** The platform formats actual message time with its local date and 12/24-hour preferences. */
@Composable
public fun KitMessageTimestamp(createdMs: Long, nowMs: Long, modifier: Modifier = Modifier) {
    val text = LocalKitPlatform.current.messageTime(createdMs, nowMs) ?: return
    KitQuietLine(text, modifier)
}
