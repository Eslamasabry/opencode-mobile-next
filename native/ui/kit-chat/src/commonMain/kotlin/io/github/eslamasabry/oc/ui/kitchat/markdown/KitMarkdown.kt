package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.stream.drive
import io.github.eslamasabry.oc.ui.kitchat.stream.growOnly
import io.github.eslamasabry.oc.ui.kitchat.stream.kitFreshFade
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * What the person can do inside a reply. Plain values and lambdas (C5): `:feature:chat` maps `ChatActions` onto them.
 * [onLink] receives every link address; it goes through the app's link policy, never straight to the platform (R17).
 * [confirmedPaths] are the paths the engine found on the server (B-CHAT-064); only they become tappable.
 */
@Immutable
public class KitMarkdownActions(
    public val onLink: (url: String) -> Unit,
    public val onPath: (path: String, line: Int?) -> Unit = { _, _ -> },
    public val confirmedPaths: ImmutableSet<String> = persistentSetOf(),
    public val copyCodeLabel: String? = null,
    public val onCopyCode: ((String) -> Unit)? = null,
    public val moreCodeLabel: ((hiddenLines: Int) -> String)? = null,
    public val onOpenCode: ((KitMdBlock.Code) -> Unit)? = null,
    /** P12-CHAT-FEEL: a picture tile was tapped; null draws tiles that do nothing but name the picture. */
    public val onPicture: ((KitPicture) -> Unit)? = null,
    /** The tile's words for a picture (description or name are passed through; the caller adds its own word). */
    public val pictureLabel: (KitPicture) -> String = { it.label ?: it.path ?: it.url ?: "" },
    /** Whether [onPicture] can show a picture that lives on the server (a viewer is wired); web ones always open. */
    public val openFilePictures: Boolean = false,
)

/**
 * How a streamed reply reveals (plan 09 §8, plan 08 §5.1). [registry] lives outside the lazy items (one per transcript);
 * blocks of one part ([group]) reveal in order. History rows pass `streamed = false` and never animate.
 */
@Immutable
public class KitMdReveal(
    public val registry: RevealRegistry,
    public val group: String,
    public val motion: KitMotionLevel,
    public val streamed: Boolean = true,
)

/**
 * The transcript's selection (plan 09 §7.8): one [SelectionContainer] so a selection runs across blocks and turns, and
 * a context menu filtered to Copy and Select all, so no system "Open" or "Call" on a selected address or number
 * bypasses the link policy (R17, smart selection off, layer 1).
 */
@Composable
public fun KitMarkdownSelection(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SelectionContainer(
        modifier.filterTextContextMenuComponents {
            it.key == TextContextMenuKeys.CopyKey || it.key == TextContextMenuKeys.SelectAllKey
        },
        content = content,
    )
}

/**
 * A whole reply as blocks, for places that draw one reply in one item (cards, the reader, previews). The transcript
 * puts each block in its own lazy row instead and calls [KitMarkdownBlock]. Each block is keyed by its parser key, so
 * a streaming reply re-composes only its tail: settled blocks keep their state and their built text.
 *
 * [live] marks the reply as still streaming: the last block takes the caret (if it ends in text) and is not
 * selectable until the Compose selection-while-streaming fix ships (§7.8).
 */
@Composable
public fun KitMarkdown(
    blocks: ImmutableList<KitMdBlock>,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    reveal: KitMdReveal? = null,
) {
    Column(modifier.widthIn(max = 700.dp), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        val last = blocks.lastIndex
        blocks.forEachIndexed { i, block ->
            key(block.key) {
                KitMarkdownBlock(block, actions, live = live && i == last, caret = live && i == last, reveal = reveal)
            }
        }
    }
}

/**
 * One block (06 §7.8 names: `KitMarkdownProse`, `KitMarkdownHeading`, `KitMarkdownQuote`, `KitMarkdownListItem`,
 * `KitMarkdownTable`, plus code through [KitCodeBlock]). [caret] draws the stream caret after the last word when the
 * block ends in text (a reply ending in code, a table or a rule shows none).
 */
@Composable
public fun KitMarkdownBlock(
    block: KitMdBlock,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    caret: Boolean = false,
    reveal: KitMdReveal? = null,
) {
    val body = @Composable {
        when (block) {
            is KitMdBlock.Prose -> KitMarkdownProse(block, actions, modifier, live, caret, reveal)
            is KitMdBlock.Heading -> KitMarkdownHeading(block, actions, modifier, live, caret, reveal)
            is KitMdBlock.ListItem -> KitMarkdownListItem(block, actions, modifier, live, caret, reveal)
            is KitMdBlock.Quote -> KitMarkdownQuote(block, actions, modifier, live, caret, reveal)
            is KitMdBlock.Table -> KitMarkdownTable(block, actions, modifier)
            is KitMdBlock.Rule -> KitMarkdownRule(modifier)
            is KitMdBlock.Pictures -> KitPictureTiles(block, actions, modifier)
            is KitMdBlock.Code ->
                KitCodeBlock(
                    source = block.text,
                    kind = KitCodeKind.Code,
                    modifier = modifier,
                    language = CodeLanguage.of(block.info),
                    open = block.open,
                    copyLabel = actions.copyCodeLabel,
                    onCopy = actions.onCopyCode,
                    moreLabel = actions.moreCodeLabel,
                    onMore = actions.onOpenCode?.let { open -> { open(block) } },
                )
        }
    }
    if (live) DisableSelection { body() } else body()
}

@Composable
public fun KitMarkdownProse(
    block: KitMdBlock.Prose,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    caret: Boolean = false,
    reveal: KitMdReveal? = null,
) {
    MdText(block.key, block.inlines, readingStyle(TypeRole.Body), actions, modifier, live, caret, reveal)
}

@Composable
public fun KitMarkdownHeading(
    block: KitMdBlock.Heading,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    caret: Boolean = false,
    reveal: KitMdReveal? = null,
) {
    val role = if (block.level <= 2) TypeRole.Headline else TypeRole.RowTitle
    MdText(block.key, block.inlines, readingStyle(role), actions, modifier.semantics(mergeDescendants = true) { heading() }, live, caret, reveal)
}

@Composable
public fun KitMarkdownListItem(
    block: KitMdBlock.ListItem,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    caret: Boolean = false,
    reveal: KitMdReveal? = null,
) {
    val style = readingStyle(TypeRole.Body)
    val marker =
        when {
            block.checked == true -> "☑"
            block.checked == false -> "☐"
            block.ordered -> "${block.number ?: 1}."
            else -> "•"
        }
    Row(modifier.fillMaxWidth().padding(start = (Space.S4 * block.depth.coerceIn(0, 8)).dp)) {
        DisableSelection {
            BasicText(
                marker,
                Modifier.widthIn(min = Space.S6.dp).padding(end = Space.S2.dp),
                style = style.copy(color = LocalKitColors.current.text2, textAlign = TextAlign.End),
            )
        }
        val muted = block.checked == true // done checklist items are muted, never struck through (§9.1)
        MdText(
            block.key, block.inlines, if (muted) style.copy(color = LocalKitColors.current.text3) else style, actions,
            Modifier.weight(1f), live, caret, reveal,
        )
    }
}

@Composable
public fun KitMarkdownQuote(
    block: KitMdBlock.Quote,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    caret: Boolean = false,
    reveal: KitMdReveal? = null,
) {
    val rail = LocalKitColors.current.hairline
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val w = 3.dp.toPx()
                drawRect(rail, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height))
            }
            .padding(start = Space.S4.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        val last = block.children.lastIndex
        block.children.forEachIndexed { i, child ->
            key(child.key) {
                KitMarkdownBlock(child, actions, live = live && i == last, caret = caret && i == last, reveal = reveal)
            }
        }
    }
}

/**
 * A table (plan 09 §9.1): measured columns (each as wide as its widest cell, at most 320 dp) inside one horizontal
 * scroller, so a wide table scrolls inside the 700 dp column instead of squeezing its words.
 */
@Composable
public fun KitMarkdownTable(block: KitMdBlock.Table, actions: KitMarkdownActions, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val style = readingStyle(TypeRole.Body)
    val inline = rememberInlineStyle()
    val sink = rememberSink(actions)
    val columns = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val rows = listOf(block.header) + block.rows
    Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Layout(
            content = {
                rows.forEachIndexed { r, row ->
                    for (c in 0 until columns) {
                        val cell = row.getOrNull(c) ?: KitInlines("")
                        val align =
                            when (block.aligns.getOrNull(c)) {
                                KitAlign.Center -> TextAlign.Center
                                KitAlign.End -> TextAlign.End
                                else -> TextAlign.Start
                            }
                        val text = remember(cell, inline, actions.confirmedPaths) {
                            inlineAnnotated(cell, inline, sink) { it in actions.confirmedPaths }
                        }
                        BasicText(
                            text,
                            Modifier
                                .then(if (r == 0) Modifier.background(colors.surface2) else Modifier)
                                .drawBehind { drawRect(colors.hairline, Offset(0f, size.height - 1f), Size(size.width, 1f)) }
                                .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
                            style = (if (r == 0) style.copy(fontWeight = FontWeight.SemiBold) else style).copy(textAlign = align),
                        )
                    }
                }
            },
        ) { measurables, _ ->
            val maxCell = 320.dp.roundToPx()
            val widths = IntArray(columns)
            measurables.forEachIndexed { i, m ->
                val c = i % columns
                widths[c] = maxOf(widths[c], minOf(m.maxIntrinsicWidth(Constraints.Infinity), maxCell))
            }
            val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(widths[i % columns])) }
            val heights = placeables.chunked(columns).map { row -> row.maxOf { it.height } }
            layout(widths.sum(), heights.sum()) {
                var y = 0
                placeables.chunked(columns).forEachIndexed { r, row ->
                    var x = 0
                    row.forEachIndexed { c, p ->
                        p.placeRelative(x, y)
                        x += widths[c]
                    }
                    y += heights[r]
                }
            }
        }
    }
}

@Composable
public fun KitMarkdownRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = Space.S2.dp).height(1.dp).background(LocalKitColors.current.hairline))
}

/**
 * The stream caret (B-CHAT-060): a 2 dp bar after the last shown character, drawn over the text. It takes no width, so
 * a line never re-wraps when the caret appears or goes. Still under every motion level (no blink loop, R5).
 */
public fun Modifier.kitStreamCaret(layout: () -> TextLayoutResult?, at: () -> Int, colors: KitColors): Modifier =
    testTag(STREAM_CARET_TAG).drawWithContent {
        drawContent()
        val l = layout() ?: return@drawWithContent
        val r = l.getCursorRect(at().coerceIn(0, l.layoutInput.text.length))
        drawRect(colors.accent, topLeft = Offset(r.left, r.top), size = Size(2.dp.toPx(), r.height))
    }

/** The test tag of a block drawing the caret. */
public const val STREAM_CARET_TAG: String = "kit-stream-caret"

@Composable
private fun readingStyle(role: TypeRole): TextStyle =
    LocalKitTypography.current.style(role).copy(
        color = LocalKitColors.current.text1,
        textDirection = TextDirection.Content, // prose follows its own direction (R15)
        textMotion = TextMotion.Static, // §8.3: rows move by whole pixels
    )

@Composable
private fun rememberInlineStyle(): KitMdInlineStyle {
    val colors = LocalKitColors.current
    val mono = LocalKitTypography.current.style(TypeRole.Mono).fontFamily
    return remember(colors, mono) {
        KitMdInlineStyle(link = colors.accent, codeFill = colors.surface2, pathFill = colors.surface2, mono = mono!!)
    }
}

/** A sink that always calls the newest [actions], so built strings never go stale and are never rebuilt for it. */
@Composable
private fun rememberSink(actions: KitMarkdownActions): KitMdLinkSink {
    val latest by rememberUpdatedState(actions)
    return remember {
        KitMdLinkSink { target ->
            when (target) {
                is KitSpanKind.Link -> latest.onLink(target.url)
                is KitSpanKind.Path -> latest.onPath(target.path, target.line)
                else -> Unit
            }
        }
    }
}

private class LayoutHolder {
    var layout: TextLayoutResult? = null
}

/**
 * The text of one block. The string is built once per (block inlines, theme, confirmed paths): a settled block that
 * keeps its instance never rebuilds it. A streamed block reveals word by word with the draw-phase fade (plan 09 §8.2,
 * the P3 `RevealRegistry` clock), and grows smoothly while live.
 */
@Composable
private fun MdText(
    key: String,
    inlines: KitInlines,
    style: TextStyle,
    actions: KitMarkdownActions,
    modifier: Modifier,
    live: Boolean,
    caret: Boolean,
    reveal: KitMdReveal?,
) {
    val inline = rememberInlineStyle()
    val sink = rememberSink(actions)
    val text: AnnotatedString =
        remember(inlines, inline, actions.confirmedPaths) {
            inlineAnnotated(inlines, inline, sink) { it in actions.confirmedPaths }
        }
    val holder = remember { LayoutHolder() }
    val state =
        if (reveal != null && reveal.streamed) reveal.registry.stateFor(key, reveal.group, live, text.length) else null
    if (state != null) {
        val latest by rememberUpdatedState(text)
        val motion by rememberUpdatedState(reveal!!.motion)
        SideEffect { state.targetLength = text.length }
        LaunchedEffect(state) { state.drive({ latest }, { motion }) }
    }
    val shown = state?.shown?.coerceAtMost(text.length) ?: text.length
    val visible = if (shown >= text.length) text else text.subSequence(0, shown)
    val revealing = state != null && (live || state.active)
    var m = modifier.fillMaxWidth()
    if (revealing) m = m.clipToBounds().growOnly(enabled = live && reveal!!.motion == KitMotionLevel.Full)
    var textModifier: Modifier = if (revealing) Modifier.kitFreshFade({ holder.layout }, state) else Modifier
    if (caret) {
        val colors = LocalKitColors.current
        textModifier = textModifier.kitStreamCaret({ holder.layout }, { visible.length }, colors)
    }
    Box(m) {
        BasicText(text = visible, style = style, onTextLayout = { holder.layout = it }, modifier = textModifier)
    }
}
