package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitViewerEmpty
import io.github.eslamasabry.oc.ui.words.res.kitViewerPartial
import org.jetbrains.compose.resources.stringResource

/** Test tag prefix of one code line: `kit-viewer-line-12` (1-based). */
public const val KIT_VIEWER_LINE_TAG: String = "kit-viewer-line-"

/** Rows kept above the opened line, so it is not glued to the top edge. */
private const val LINE_CONTEXT = 3

/** The find mark's passive wash (plan 06 `KitFindMark`: .18 passive, never a text colour). */
private const val LINE_MARK_ALPHA = 0.18f

/**
 * The viewer's Code body (plan 10 §11.3): monospace lines with line numbers, always left to right (code never mirrors,
 * LAY-8), scrolling sideways by default. [initialLine] (1-based) is scrolled to and marked with the find wash; a line
 * out of range is the host's notice ("Line N is outside this preview"), not a mark. Lazy like the Text body.
 *
 * Native: highlighting arrives with `CodeTokenizer` (SyntaxMP, after its benchmark); until then lines are one colour.
 *
 * States: content, empty, truncated, opened at a line
 * Text: reading (mono)
 * Cap: Fill
 * Motion: none
 * Hidden: none
 * Replaces: KitViewer code body (lib/ui/kit/kit_viewer_build.dart `_codeBody`)
 */
@Composable
public fun KitViewerCodeBody(
    text: String,
    modifier: Modifier = Modifier,
    initialLine: Int? = null,
    wrap: Boolean = false,
) {
    val cut = remember(text) { ViewerText.cut(text) }
    val lines = remember(cut) { ViewerText.lines(cut.text) }
    val gutter = LocalKitTokens.current.gutter.dp
    if (lines.isEmpty()) {
        KitText(stringResource(Res.string.kitViewerEmpty), modifier.padding(gutter), tone = TextTone.Secondary)
        return
    }
    val mark = initialLine?.takeIf { it in 1..lines.size }
    val offset = if (cut.truncated) 1 else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = mark?.let { maxOf(0, it - 1 - LINE_CONTEXT + offset) } ?: 0)
    val numberWidth = remember(lines.size) { lines.size.toString().length }
    val wash = LocalKitColors.current.accent.copy(alpha = LINE_MARK_ALPHA)
    val partial = if (cut.truncated) stringResource(Res.string.kitViewerPartial) else null
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SelectionContainer(modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().testTag(KIT_VIEWER_LINES_TAG)
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                state = listState,
                contentPadding = PaddingValues(horizontal = gutter, vertical = gutter / 2),
            ) {
                if (partial != null) {
                    item(key = "partial", contentType = "notice") {
                        KitText(partial, Modifier.padding(bottom = gutter / 2), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                }
                itemsIndexed(lines, key = { i, _ -> i }, contentType = { _, _ -> "line" }) { i, line ->
                    val number = i + 1
                    val marked = number == mark
                    Row(
                        Modifier
                            .testTag("$KIT_VIEWER_LINE_TAG$number")
                            .then(if (marked) Modifier.background(wash).semantics { selected = true } else Modifier),
                        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                    ) {
                        DisableSelection { // copying lines never copies their numbers
                            KitText(
                                number.toString().padStart(numberWidth),
                                Modifier.widthIn(min = (numberWidth * 8).dp),
                                role = TypeRole.Mono,
                                tone = TextTone.Tertiary,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                            )
                        }
                        KitText(line.ifEmpty { " " }, role = TypeRole.Mono, maxLines = if (wrap) Int.MAX_VALUE else 1)
                    }
                }
            }
        }
    }
}
