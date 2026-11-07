package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.ResolvedTextDirection
import io.github.eslamasabry.oc.ui.design.motion.CurveId
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
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
import kotlinx.coroutines.launch

/**
 * What the person can do inside a reply. Plain values and lambdas (C5): `:feature:chat` maps
 * `ChatActions` onto them. [onLink] receives every link address; it goes through the app's link
 * policy, never straight to the platform (R17). [confirmedPaths] are the paths the engine found on
 * the server (B-CHAT-064); only they become tappable.
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
    /**
     * P12-CHAT-FEEL: a picture tile was tapped; null draws tiles that do nothing but name the
     * picture.
     */
    public val onPicture: ((KitPicture) -> Unit)? = null,
    /**
     * The tile's words for a picture (description or name are passed through; the caller adds its
     * own word).
     */
    public val pictureLabel: (KitPicture) -> String = { it.label ?: it.path ?: it.url ?: "" },
    /**
     * Whether [onPicture] can show a picture that lives on the server (a viewer is wired); web ones
     * always open.
     */
    public val openFilePictures: Boolean = false,
    /** Only server/phone paths enter this callback. Web images are never fetched. */
    public val readPicture: (suspend (path: String) -> ByteArray?)? = null,
)

/**
 * How a streamed reply reveals (plan 09 §8, plan 08 §5.1). [registry] lives outside the lazy items
 * (one per transcript); blocks of one part ([group]) reveal in order. History rows pass `streamed =
 * false` and never animate.
 */
@Immutable
public class KitMdReveal(
    public val registry: RevealRegistry,
    public val group: String,
    public val motion: KitMotionLevel,
    public val streamed: Boolean = true,
)

/**
 * The transcript's selection (plan 09 §7.8): one [SelectionContainer] so a selection runs across
 * blocks and turns, and a context menu filtered to Copy and Select all, so no system "Open" or
 * "Call" on a selected address or number bypasses the link policy (R17, smart selection off, layer
 * 1).
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
 * A whole reply as blocks, for places that draw one reply in one item (cards, the reader,
 * previews). The transcript puts each block in its own lazy row instead and calls
 * [KitMarkdownBlock]. Each block is keyed by its parser key, so a streaming reply re-composes only
 * its tail: settled blocks keep their state and their built text.
 *
 * [live] marks the reply as still streaming: the last block takes the caret (if it ends in text)
 * and is not selectable until the Compose selection-while-streaming fix ships (§7.8). [caret] is false while the
 * turn waits on the person (C-021: Flutter shows no caret while a request is pending).
 */
@Composable
public fun KitMarkdown(
    blocks: ImmutableList<KitMdBlock>,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    reveal: KitMdReveal? = null,
    caret: Boolean = live,
) {
    Column(modifier.widthIn(max = 700.dp)) {
        val last = blocks.lastIndex
        blocks.forEachIndexed { i, block ->
            key(block.key) {
                if (i > 0) Spacer(Modifier.height(kitMdGap(blocks[i - 1], block).dp))
                KitMarkdownBlock(
                    block,
                    actions,
                    live = live && i == last,
                    caret = caret && live && i == last,
                    reveal = reveal,
                )
            }
        }
    }
}

/**
 * C-025: the space between two blocks of one reply, in dp. Flutter `KitMarkdown._buildContent` puts `space3` (12 dp)
 * between blocks; the items of one list sit `space2` (8 dp) apart (`_KitMdList`, top padding of every item after the
 * first). The transcript, which shows a reply one block per row, uses the same gaps between its block rows.
 */
public fun kitMdGap(before: KitMdBlock, after: KitMdBlock): Int =
    if (before is KitMdBlock.ListItem && after is KitMdBlock.ListItem) Space.S2 else Space.S3

/**
 * One block (06 §7.8 names: `KitMarkdownProse`, `KitMarkdownHeading`, `KitMarkdownQuote`,
 * `KitMarkdownListItem`, `KitMarkdownTable`, plus code through [KitCodeBlock]). [caret] draws the
 * stream caret after the last word when the block ends in text (a reply ending in code, a table or
 * a rule shows none).
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
    val body =
        @Composable {
            when (block) {
                is KitMdBlock.Prose ->
                    KitMarkdownProse(block, actions, modifier, live, caret, reveal)
                is KitMdBlock.Heading ->
                    KitMarkdownHeading(block, actions, modifier, live, caret, reveal)
                is KitMdBlock.ListItem ->
                    KitMarkdownListItem(block, actions, modifier, live, caret, reveal)
                is KitMdBlock.Quote ->
                    KitMarkdownQuote(block, actions, modifier, live, caret, reveal)
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
    // P12-CHAT-FEEL: a streaming block can change kind as it grows (a line of text that turns out
    // to be a table, a
    // rule or a picture). Those kinds draw at once, so a reveal begun under the same key is
    // finished here, never left
    // half way (holding up the blocks after it in its part).
    if (reveal != null && reveal.streamed && !block.reveals) {
        SideEffect { reveal.registry.settle(block.key, reveal.group) }
    }
    if (live) DisableSelection { body() } else body()
}

/** The kinds drawn through the word-by-word reveal. */
private val KitMdBlock.reveals: Boolean
    get() =
        this is KitMdBlock.Prose ||
            this is KitMdBlock.Heading ||
            this is KitMdBlock.ListItem ||
            this is KitMdBlock.Quote

/**
 * A block that was drawn under [key] is gone (a growing row re-parsed into fewer blocks): its
 * reveal is finished where it stands, so it never holds up the blocks after it.
 */
public fun KitMdReveal.settle(key: String) {
    registry.settle(key, group)
}

/** Finishes [key]'s reveal where it stands (a block of the same key now draws at full length). */
private fun RevealRegistry.settle(key: String, group: String) {
    val state = stateFor(key, group, live = false, length = 0)
    if (state.targetLength != state.shown) state.targetLength = state.shown
    if (state.runs.isNotEmpty()) state.runs.clear()
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
    MdText(
        block.key,
        block.inlines,
        readingStyle(TypeRole.Body),
        actions,
        modifier,
        live,
        caret,
        reveal,
    )
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
    MdText(
        block.key,
        block.inlines,
        readingStyle(role),
        actions,
        modifier.semantics(mergeDescendants = true) { heading() },
        live,
        caret,
        reveal,
    )
}

/**
 * N-49 / C-022: one level of list nesting, 16 dp (Flutter `_KitMdList`: `space4 * level`). A nested item's marker (and
 * an item's quote, table or code) moves in by this much per level.
 */
internal const val KIT_MD_NEST_STEP: Int = Space.S4

/**
 * C-022: the width of a list's marker column at font scale 1 (Flutter `_KitMdList`): `space6` (24 dp) for numbers,
 * `space4` (16 dp) for bullets and checkboxes. It grows with the font scale so the gap to the words holds.
 */
internal fun kitMdMarkerColumn(ordered: Boolean): Int = if (ordered) Space.S6 else Space.S4

/**
 * N-49: a reply block that sits [depth] levels inside a list. The transcript shows a reply one block per row, so a
 * nested item (or an item's quote, table or fence) arrives alone and is indented here to the depth the whole text
 * gave it. Depth 0 draws [content] unchanged.
 *
 * States: none
 * Motion: none (the indent is part of the layout)
 */
@Composable
public fun KitMdNest(depth: Int, content: @Composable () -> Unit) {
    if (depth <= 0) content()
    else Box(Modifier.padding(start = (KIT_MD_NEST_STEP * depth.coerceAtMost(6)).dp)) { content() }
}

/**
 * The widest marker a list item's column must fit (B-101): for a numbered item, zeros of its number's digit count
 * (at least two) and the dot; null for bullets and checkboxes, which fit the minimum column.
 */
internal fun kitMdMarkerSample(ordered: Boolean, number: Int?): String? {
    if (!ordered) return null
    val digits = (number ?: 1).coerceAtLeast(0).toString().length.coerceAtLeast(2)
    return "0".repeat(digits) + "."
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
    // B-101: every item's words start on one edge. Digits are tabular and the column is a fixed width (Flutter: 24 dp
    // for numbers, 16 dp for bullets, scaled with the text), so "1." and "8." (and "9." and "10.") never shift the
    // words. A number wider than the column (three digits and up) widens it rather than running into the words.
    // The marker starts at the column's start edge, as Flutter's KitText does.
    val markerStyle =
        style.copy(color = LocalKitColors.current.text2, textAlign = TextAlign.Start, fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val widest = kitMdMarkerSample(block.ordered, block.number)
    val density = LocalDensity.current
    val markerWidth =
        with(density) {
            val sample = widest?.let { measurer.measure(it, markerStyle, softWrap = false, maxLines = 1).size.width }
            maxOf((kitMdMarkerColumn(block.ordered) * fontScale).dp, sample?.toDp() ?: 0.dp)
        }
    Row(modifier.fillMaxWidth().padding(start = (KIT_MD_NEST_STEP * block.depth.coerceIn(0, 6)).dp)) {
        DisableSelection {
            BasicText(marker, Modifier.width(markerWidth), style = markerStyle)
        }
        val muted =
            block.checked == true // done checklist items are muted, never struck through (§9.1)
        // The item's own words, then whatever else it holds (a fence under a step, a quote) inside the same column,
        // so it lines up with the words and not with the marker.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            if (block.inlines.text.isNotEmpty() || block.children.isEmpty()) {
                MdText(
                    block.key,
                    block.inlines,
                    if (muted) style.copy(color = LocalKitColors.current.text3) else style,
                    actions,
                    Modifier,
                    live && block.children.isEmpty(),
                    caret && block.children.isEmpty(),
                    reveal,
                )
            }
            val last = block.children.lastIndex
            block.children.forEachIndexed { i, child ->
                key(child.key) {
                    KitMarkdownBlock(
                        child,
                        actions,
                        live = live && i == last,
                        caret = caret && i == last,
                        reveal = reveal,
                    )
                }
            }
        }
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
    // C-018 (Flutter `_KitMdQuote`): a focus-ring-wide bar (two device pixels) in text3 at 45 % on the start edge,
    // 8 dp to the words, which are in the secondary tone.
    val colors = LocalKitColors.current
    val rail = colors.text3.copy(alpha = .45f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val w = kitMdQuoteBarPx(density)
                drawRect(
                    rail,
                    topLeft = Offset(if (rtl) size.width - w else 0f, 0f),
                    size = Size(w, size.height),
                )
            }
            .padding(start = Space.S2.dp),
    ) {
        val last = block.children.lastIndex
        block.children.forEachIndexed { i, child ->
            key(child.key) {
                if (i > 0) Spacer(Modifier.height(kitMdGap(block.children[i - 1], child).dp))
                CompositionLocalProvider(LocalMdTone provides colors.text2) {
                    KitMarkdownBlock(
                        child,
                        actions,
                        live = live && i == last,
                        caret = caret && i == last,
                        reveal = reveal,
                    )
                }
            }
        }
    }
}

/**
 * C-018: the quote bar's width in pixels: Flutter's `focusRingWidth`, two device pixels, never under one logical
 * pixel at a density of 1 or less.
 */
internal fun kitMdQuoteBarPx(density: Float): Float = if (density <= 1f) maxOf(2f, density) else 2f

/** The tone of reading text inside a block that sets one (a quote's words are secondary); null = text1. */
private val LocalMdTone = staticCompositionLocalOf<Color?> { null }

/**
 * A table (plan 09 §9.1): measured columns (each as wide as its widest cell, at most 320 dp) inside
 * one horizontal scroller, so a wide table scrolls inside the 700 dp column instead of squeezing
 * its words.
 */
@Composable
public fun KitMarkdownTable(
    block: KitMdBlock.Table,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKitColors.current
    val style = readingStyle(TypeRole.Body)
    val inline = rememberInlineStyle()
    val sink = rememberSink(actions)
    val columns = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val rows = listOf(block.header) + block.rows
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = constraints.maxWidth
        Box(
            Modifier.horizontalScroll(scroll)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false
                    else
                        when (event.key) {
                            Key.DirectionLeft -> {
                                scope.launch {
                                    scroll.scrollTo((scroll.value - 48).coerceAtLeast(0))
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                scope.launch {
                                    scroll.scrollTo(
                                        (scroll.value + 48).coerceAtMost(scroll.maxValue)
                                    )
                                }
                                true
                            }
                            else -> false
                        }
                }
                .focusable()
                .semantics { collectionInfo = CollectionInfo(rows.size, columns) }
        ) {
            Layout(
                // C-024: the table's frame is one device-pixel hairline on every side.
                modifier = Modifier.border((1f / LocalDensity.current.density).dp, colors.hairline),
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
                            val text =
                                remember(cell, inline, actions.confirmedPaths) {
                                    inlineAnnotated(cell, inline, sink) {
                                        it in actions.confirmedPaths
                                    }
                                }
                            BasicText(
                                text,
                                Modifier.then(
                                        if (r == 0) Modifier.background(colors.surface1) // C-024: Flutter header band
                                        else Modifier
                                    )
                                    .drawBehind {
                                        // C-024 (Flutter `_KitMdTable`): a hairline above every row
                                        // after the header; the frame draws the outer edges.
                                        if (r > 0) drawRect(colors.hairline, Offset.Zero, Size(size.width, 1f))
                                    }
                                    .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
                                style =
                                    (if (r == 0) style.copy(fontWeight = FontWeight.SemiBold)
                                        else style)
                                        .copy(textAlign = align),
                            )
                        }
                    }
                }
            ) { measurables, _ ->
                val maxCell = 320.dp.roundToPx()
                val natural = IntArray(columns)
                val minimum = IntArray(columns)
                measurables.forEachIndexed { i, m ->
                    val c = i % columns
                    natural[c] = maxOf(natural[c], m.maxIntrinsicWidth(Constraints.Infinity))
                    minimum[c] = maxOf(minimum[c], m.minIntrinsicWidth(Constraints.Infinity))
                }
                val widths = natural.copyOf()
                if (natural.sum() > available) {
                    if (minimum.sum() > available) {
                        for (c in widths.indices) widths[c] =
                            maxOf(minimum[c], minOf(natural[c], maxCell))
                    } else {
                        var low = 0
                        var high = natural.maxOrNull() ?: 0
                        while (low < high) {
                            val middle = (low + high + 1) / 2
                            val total =
                                widths.indices.sumOf {
                                    maxOf(minimum[it], minOf(natural[it], middle))
                                }
                            if (total <= available) low = middle else high = middle - 1
                        }
                        for (c in widths.indices) widths[c] =
                            maxOf(minimum[c], minOf(natural[c], low))
                    }
                }
                val placeables = measurables.mapIndexed { i, m ->
                    m.measure(Constraints.fixedWidth(widths[i % columns]))
                }
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
}

/** C-026 (Flutter `_KitMdRule`): the kit's one hairline divider with 4 dp above and below. */
@Composable
public fun KitMarkdownRule(modifier: Modifier = Modifier) {
    KitDivider(modifier.padding(vertical = Space.S1.dp))
}

/**
 * The stream caret (B-CHAT-060, C-021, C-028; Flutter `KitStreamCaret`): a rounded accent bar 2 dp wide and 16 sp
 * tall, centred on the line, 2 dp after the last laid-out glyph (before it, in right-to-left text). It takes no width,
 * so a line never re-wraps when the caret appears or goes. [alpha] is read in the draw phase only: the breath
 * ([rememberCaretBreath]) changes no layout.
 */
public fun Modifier.kitStreamCaret(
    layout: () -> TextLayoutResult?,
    at: () -> Int,
    colors: KitColors,
    alpha: () -> Float = { 1f },
): Modifier =
    testTag(STREAM_CARET_TAG).drawWithContent {
        drawContent()
        val l = layout() ?: return@drawWithContent
        val bar = kitCaretRect(l, at(), this)
        drawRoundRect(
            colors.accent,
            topLeft = bar.topLeft,
            size = bar.size,
            cornerRadius = CornerRadius(bar.width / 2f),
            alpha = alpha().coerceIn(0f, 1f),
        )
    }

/**
 * Where the caret's bar goes for a caret after [offset] in [layout] (px): `space1 / 2` (2 dp) past the cursor in the
 * paragraph's direction, `space1 / 2` wide, 16 sp tall (no taller than the line), centred on the line.
 */
internal fun kitCaretRect(layout: TextLayoutResult, offset: Int, density: Density): Rect =
    with(density) {
        val at = offset.coerceIn(0, layout.layoutInput.text.length)
        val r = layout.getCursorRect(at)
        val w = (Space.S1 * .5f).dp.toPx()
        val gap = (Space.S1 * .5f).dp.toPx()
        val h = minOf(CARET_HEIGHT.toPx(), r.height)
        val rtl = layout.getParagraphDirection(at) == ResolvedTextDirection.Rtl
        val x = if (rtl) r.left - gap - w else r.left + gap
        Rect(Offset(x, r.top + (r.height - h) / 2f), Size(w, h))
    }

/** Flutter `KitStreamCaret`: `textScaler.scale(16)` tall. */
private val CARET_HEIGHT = 16.sp

/** Flutter `KitStreamCaret.breath`: one breath (down or back up). */
public const val CARET_BREATH_MS: Int = 1100

/** Flutter `KitStreamCaret`: how far the breath fades the caret. */
public const val CARET_BREATH_LOW: Float = .25f

/**
 * C-021: the caret breathes 1.0 → 0.25 and back, [CARET_BREATH_MS] each way on Flutter's `stepEase`
 * (`easeInOutQuad`), only where ambient loops may run (full motion, not a still frame). Elsewhere it is steady at 1.
 */
@Composable
internal fun rememberCaretBreath(): () -> Float {
    if (!LocalKitMotion.current.ambientLoops) return { 1f }
    val breath =
        rememberInfiniteTransition("caret")
            .animateFloat(
                1f,
                CARET_BREATH_LOW,
                infiniteRepeatable(tween(CARET_BREATH_MS, easing = StepEase), RepeatMode.Reverse),
                "breath",
            )
    return { breath.value }
}

private val StepEase =
    CurveId.StepEase.let { CubicBezierEasing(it.x1, it.y1, it.x2, it.y2) }

/** The test tag of a block drawing the caret. */
public const val STREAM_CARET_TAG: String = "kit-stream-caret"

@Composable
private fun readingStyle(role: TypeRole): TextStyle =
    LocalKitTypography.current
        .style(role)
        .copy(
            color = LocalMdTone.current ?: LocalKitColors.current.text1,
            textDirection = TextDirection.Content, // prose follows its own direction (R15)
            textMotion = TextMotion.Static, // §8.3: rows move by whole pixels
        )

/**
 * C-015 (R15; Flutter `_directionOf`): the reading direction of a block from its first strong letter (Hebrew and
 * Arabic ranges read right to left; Latin, Greek and Cyrillic left to right), or null when it has none, so the
 * ambient direction applies. Each text block (heading, paragraph, list item, quote line) takes its own.
 */
public fun kitMdDirectionOf(text: CharSequence): LayoutDirection? {
    var i = 0
    while (i < text.length) {
        val c = text[i].code
        if (c in 0x0590..0x08FF || c in 0xFB1D..0xFDFF || c in 0xFE70..0xFEFF) return LayoutDirection.Rtl
        if (c in 0x41..0x5A || c in 0x61..0x7A || c in 0xC0..0x024F || c in 0x0370..0x058F) return LayoutDirection.Ltr
        i++
    }
    return null
}

@Composable
private fun rememberInlineStyle(): KitMdInlineStyle {
    val colors = LocalKitColors.current
    val monoStyle = LocalKitTypography.current.style(TypeRole.Mono)
    val mono = monoStyle.fontFamily
    val monoSize = monoStyle.fontSize // C-016: inline code is Mono 13, not the body size
    return remember(colors, mono, monoSize) { kitMdInlineStyle(colors, monoStyle) }
}

/**
 * The inline look from the theme: links in `roles.partner` (C-017), confirmed paths in the accent, code and paths in
 * the Mono role's face and size (C-016).
 */
internal fun kitMdInlineStyle(colors: KitColors, mono: TextStyle): KitMdInlineStyle =
    KitMdInlineStyle(
        link = colors.partner,
        mono = mono.fontFamily ?: androidx.compose.ui.text.font.FontFamily.Monospace,
        monoSize = mono.fontSize,
        pathLink = colors.accent,
    )

/**
 * A sink that always calls the newest [actions], so built strings never go stale and are never
 * rebuilt for it.
 */
@Composable
private fun rememberSink(actions: KitMarkdownActions): KitMdLinkSink {
    val latest by rememberUpdatedState(actions)
    return remember {
        KitMdLinkSink { target ->
            when (target) {
                is KitSpanKind.Link -> latest.onLink(target.url)
                is KitSpanKind.Path -> latest.onPath(target.path, target.line)
                is KitSpanKind.Picture ->
                    if (latest.openFilePictures)
                        latest.onPicture?.invoke(KitPicture(target.label, path = target.path))
                else -> Unit
            }
        }
    }
}

private class LayoutHolder {
    var layout: TextLayoutResult? = null
}

/**
 * The text of one block. The string is built once per (block inlines, theme, confirmed paths): a
 * settled block that keeps its instance never rebuilds it. A streamed block reveals word by word
 * with the draw-phase fade (plan 09 §8.2, the P3 `RevealRegistry` clock), and grows smoothly while
 * live.
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
        if (reveal != null && reveal.streamed)
            reveal.registry.stateFor(key, reveal.group, live, text.length)
        else null
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
    if (revealing)
        m = m.clipToBounds().growOnly(enabled = live && reveal!!.motion == KitMotionLevel.Full)
    var textModifier: Modifier =
        if (revealing) Modifier.kitFreshFade({ holder.layout }, state) else Modifier
    if (caret) {
        // C-028: the caret follows the last laid-out glyph (Flutter's trailing caret span), words still fading in
        // included, so it never stands on a glyph.
        val breath = rememberCaretBreath()
        val colors = LocalKitColors.current
        textModifier = textModifier.kitStreamCaret({ holder.layout }, { visible.length }, colors, breath)
    }
    // C-015: the block reads in its own direction, from the whole text (not only what is revealed so far), and fills
    // the column so a one-line right-to-left heading or paragraph sits on the right edge.
    val direction = remember(inlines.text) { kitMdDirectionOf(inlines.text) }
    val directed =
        when (direction) {
            LayoutDirection.Rtl -> style.copy(textDirection = TextDirection.Rtl)
            LayoutDirection.Ltr -> style.copy(textDirection = TextDirection.Ltr)
            null -> style
        }
    Box(m) {
        BasicText(
            text = visible,
            style = directed,
            onTextLayout = { holder.layout = it },
            modifier = Modifier.fillMaxWidth().then(textModifier),
        )
    }
}
