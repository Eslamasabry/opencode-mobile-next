package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitViewerEmpty
import io.github.eslamasabry.oc.ui.words.res.kitViewerPartial
import org.jetbrains.compose.resources.stringResource

/** Test tag of the lazy line list of the Text and Code bodies. */
public const val KIT_VIEWER_LINES_TAG: String = "kit-viewer-lines"

/**
 * The viewer's Text body (plan 10 §11.3): plain reading text as a lazy list of lines (stable keys = line index), so a
 * 200,000-character file never lays out at once. Each paragraph takes its direction from its own words (K17). Wraps by
 * default; with [wrap] false lines scroll sideways together. Text over [ViewerText.MAX_CHARS] is cut and says so.
 *
 * States: content, empty ("This file is empty"), truncated ("Showing part of this file")
 * Text: reading
 * Cap: Fill
 * Motion: none
 * Hidden: none
 * Replaces: KitViewer text body (lib/ui/kit/kit_viewer_build.dart)
 */
@Composable
public fun KitViewerTextBody(text: String, modifier: Modifier = Modifier, wrap: Boolean = true) {
    val cut = remember(text) { ViewerText.cut(text) }
    val lines = remember(cut) { ViewerText.lines(cut.text) }
    val gutter = LocalKitTokens.current.gutter.dp
    if (lines.isEmpty()) {
        KitText(stringResource(Res.string.kitViewerEmpty), modifier.padding(gutter), tone = TextTone.Secondary)
        return
    }
    val partial = if (cut.truncated) stringResource(Res.string.kitViewerPartial) else null
    Box(modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(
                Modifier.fillMaxSize().testTag(KIT_VIEWER_LINES_TAG)
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                contentPadding = PaddingValues(horizontal = gutter, vertical = gutter / 2),
            ) {
                if (partial != null) {
                    item(key = "partial", contentType = "notice") {
                        KitText(partial, Modifier.padding(bottom = gutter / 2), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                }
                itemsIndexed(lines, key = { i, _ -> i }, contentType = { _, _ -> "line" }) { _, line ->
                    KitText(line.ifEmpty { " " }, maxLines = if (wrap) Int.MAX_VALUE else 1)
                }
            }
        }
    }
}
