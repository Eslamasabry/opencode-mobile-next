package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.Layout as KitLayout
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.action.KitStopButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitCorners
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.request.KitHeld
import io.github.eslamasabry.oc.ui.kit.request.KitRequest
import io.github.eslamasabry.oc.ui.kit.request.KitRequestAnswers
import io.github.eslamasabry.oc.ui.kit.request.KitRequestWords
import io.github.eslamasabry.oc.ui.kit.row.KitReorderRows
import io.github.eslamasabry.oc.ui.kit.row.KitReorderWords
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.status.KitMarkSize
import io.github.eslamasabry.oc.ui.kit.status.KitStepTree
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeActions
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeKind
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeScope
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeWords
import io.github.eslamasabry.oc.ui.kit.status.KitTaskMark
import io.github.eslamasabry.oc.ui.kit.status.KitTaskState
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStep
import io.github.eslamasabry.oc.ui.kit.status.rememberKitStepTreeState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/** The list's groups (LAMP-PLAN §5); the caller orders them top → bottom, Needs you last, by the thumb. */
public enum class KitLampGroup { Pinned, Recent, Done, Working, Failed, NeedsYou }

/** A conversation row's 24 dp mark. */
public enum class KitLampRowMark { NeedsYou, Failed, Working, Done, Quiet }

/** A state word's tone: quiet, accent (working), attention (waiting for the person). */
public enum class KitLampTone { Quiet, Accent, Attention }

/**
 * One other conversation in the list, plain values. [state] is its state word ("3 of 7 done", "for 4m", "Thinking"),
 * null where it would repeat the group's heading; [newest] its newest line; [request] what it asks (its words then
 * stand in amber in place of the newest line, with Allow once / Deny or Answer… under it); [held] an answer held for
 * 3 s; [stopping] a stop held for 3 s; [stoppable] shows the stop square. [children] are its sub-agents (or steps),
 * drawn as a [steps] tree under it. [canEdit] false: no pin, rename or hide in Edit (a sub-agent's own row).
 */
@Immutable
public data class KitLampRow(
    val key: Any,
    val title: String,
    val mark: KitLampRowMark,
    val progress: Float? = null,
    val state: String? = null,
    val stateTone: KitLampTone = KitLampTone.Accent,
    val newest: String? = null,
    val time: String? = null,
    val server: String? = null,
    val pinned: Boolean = false,
    val request: KitRequest? = null,
    val held: KitHeld? = null,
    val stopping: KitHeld? = null,
    val stoppable: Boolean = false,
    val steps: KitStepTreeKind? = null,
    val children: List<KitTreeStep> = emptyList(),
    val canEdit: Boolean = true,
)

/** One group: its heading words and its rows in order. */
@Immutable
public data class KitLampSection(val group: KitLampGroup, val title: String, val rows: List<KitLampRow>)

/**
 * The whole list, plain values: [sections] top → bottom as shown; [hidden] the conversations hidden from this list
 * ("Hidden · n · Show again", folded at the top); [more] the count left for All conversations; [all] the footer's count;
 * [editing] Edit mode; [youAreIn] the header's second line ("You are in “‹title›”").
 */
@Immutable
public data class KitLampPanelModel(
    val sections: List<KitLampSection>,
    val hidden: List<KitLampRow> = emptyList(),
    val more: Int = 0,
    val all: Int = 0,
    val editing: Boolean = false,
    val youAreIn: String? = null,
)

/** The list's words (the caller's; L1 owns them). The `(String)` forms name a conversation for TalkBack. */
public class KitLampPanelWords(
    public val title: String,
    public val edit: String,
    public val done: String,
    /** "All conversations · 7". */
    public val all: (Int) -> String,
    /** "All · 7", when the long form does not fit. */
    public val allShort: (Int) -> String,
    public val newConversation: String,
    /** "Hidden · 1". */
    public val hidden: (Int) -> String,
    public val showAgain: String,
    /** "3 more in All conversations". */
    public val more: (Int) -> String,
    public val empty: String,
    public val pin: (String) -> String,
    public val unpin: (String) -> String,
    public val rename: (String) -> String,
    public val hide: (String) -> String,
    public val stop: (String) -> String,
    /** "3 sub-agents", the folded block's line. */
    public val subAgents: (Int) -> String,
    public val edgeLight: String,
    public val move: String,
    public val turnOff: String,
    public val reorder: KitReorderWords,
    public val steps: KitStepTreeWords,
    public val request: KitRequestWords,
)

/** What a tap in the list does. Keys are the rows' keys; a step is the sub-agent tapped (null = the row itself). */
public class KitLampPanelActions(
    public val onOpen: (Any) -> Unit = {},
    public val onOpenStep: (Any, KitTreeStep) -> Unit = { _, _ -> },
    public val onStop: (Any) -> Unit = {},
    public val onStopStep: (Any, KitTreeStep) -> Unit = { _, _ -> },
    public val onAllow: (Any, KitTreeStep?) -> Unit = { _, _ -> },
    public val onDeny: (Any, KitTreeStep?) -> Unit = { _, _ -> },
    public val onAnswer: (Any, KitTreeStep?) -> Unit = { _, _ -> },
    public val onUndo: (Any, KitTreeStep?) -> Unit = { _, _ -> },
    public val onHoldEnd: (Any, KitTreeStep?) -> Unit = { _, _ -> },
    public val onEdit: (Boolean) -> Unit = {},
    public val onPin: (Any, Boolean) -> Unit = { _, _ -> },
    public val onRename: (Any) -> Unit = {},
    public val onHide: (Any) -> Unit = {},
    public val onShowAgain: (Any) -> Unit = {},
    /** A pinned row moved from one place among the pins to another. */
    public val onReorderPinned: (Int, Int) -> Unit = { _, _ -> },
    public val onAll: () -> Unit = {},
    public val onNew: () -> Unit = {},
    public val onMove: () -> Unit = {},
    public val onTurnOff: () -> Unit = {},
)

/** The panel's measures (LAMP-CRAFT §4), in dp. */
public object KitLampPanelDims {
    /** The panel never grows past a phone's width class (`Layout.MEDIUM_FROM`, 600 dp). */
    public const val MAX_W: Int = KitLayout.MEDIUM_FROM

    /** From the screen's far edge (`Space.S2`). */
    public const val FAR: Int = Space.S2

    /** From the light's edge (`Space.S4`). */
    public const val NEAR: Int = Space.S4

    /** Above the navigation bar (`Space.S2`). */
    public const val BOTTOM: Int = Space.S2

    /** The header's least height (`Space.S6` × 2 + `Space.S3`). */
    public const val HEADER_H: Int = Space.S6 * 2 + Space.S3

    /** The footer row (`Space.S6` × 2 + `Space.S2`). */
    public const val FOOTER_H: Int = Space.S6 * 2 + Space.S2

    /** A conversation row's mark (`Space.S6`). */
    public const val MARK: Int = Space.S6

    /** Where a row's text starts: gutter + mark + gap, 16 + 24 + 12 (dividers are inset to it). */
    public const val TEXT_START: Int = Space.S4 + MARK + Space.S3

    /** The fade at a cut edge, only while there is more past it (`Space.S4`). */
    public const val FADE: Int = Space.S4

    /** The top cut falls on a group boundary when that keeps at least this share of the room. */
    public const val CUT_MIN_SHARE: Float = 0.45f // NV: LAMP-PLAN §B, 45 % of the room

    /** The panel is at most this share of the window's height. */
    public const val MAX_HEIGHT_SHARE: Float = 0.8f // NV: LAMP-CRAFT §4, 80 % of the window
}

/** The panel's pure rules. */
public object KitLampPanelRules {
    /**
     * The corners in reading order: toward the screen's middle the sheet's (`KitShapeId.Sheet`), on the light's edge the
     * panel's (`KitShapeId.Panel`). [lightAtEnd] is true when the light sits on the reading end.
     */
    public fun corners(lightAtEnd: Boolean): KitCorners =
        if (lightAtEnd) KitCorners.sides(start = KitShapeId.Sheet, end = KitShapeId.Panel)
        else KitCorners.sides(start = KitShapeId.Panel, end = KitShapeId.Sheet)

    /**
     * The list's visible height for groups of [heights] (top → bottom) in [room]: all of them when they fit; else, filled
     * from the bottom, the whole groups that fit when they keep at least [CUT_MIN_SHARE] of the room (the top cut on a
     * group boundary: no orphan heading, no half row); else the whole room.
     */
    public fun viewport(heights: List<Int>, room: Int): Int {
        val total = heights.sum()
        if (total <= room) return total
        var kept = 0
        for (h in heights.asReversed()) {
            if (kept + h > room) break
            kept += h
        }
        return if (kept >= room * KitLampPanelDims.CUT_MIN_SHARE) kept else room
    }

    /** The scrim's strength behind the open panel, by style (LAMP-CRAFT §6). */
    public fun scrimAlpha(style: Style, dark: Boolean): Float =
        when (style) {
            Style.Glass -> MotionTokens.DIM_MOST
            Style.Soft, Style.Paper -> SCRIM_SOFT
            else -> if (dark) SCRIM_DARK else SCRIM_SOFT
        }

    private const val SCRIM_DARK: Float = 0.66f // NV: LAMP-CRAFT §6, 66 % scrim (Solid dark, Brutal)
    private const val SCRIM_SOFT: Float = 0.5f // NV: LAMP-CRAFT §6, 50 % scrim (light, Soft, Paper)
}

/**
 * The edge light's list (LAMP-PLAN §5, LAMP-CRAFT §4-5): "Conversations" with Edit / Done on its baseline and "You are
 * in “‹title›”"; the groups filled from the bottom (Needs you by the thumb), opening scrolled to the bottom, a 16 dp
 * fade at a cut edge only while there is more past it, the top cut on a group boundary; conversation rows with their
 * mark, title (two lines) with time, server and pin, the state word, the newest line or the request in amber, Allow
 * once / Deny or Answer…, the 44 dp stop square, held receipts; sub-agents as a step tree under their row; in Edit:
 * pin · rename · hide per row, a drag handle on pinned rows ([KitReorderRows]) and the "Edge light: Move… · Turn off"
 * strip; the footer "All conversations · N ›" and "+ New conversation". Sits inside [KitLampPanelHost].
 *
 * States: rest, editing, empty, a row asking, held answers and stops
 * Text: frame (header Title, second line Secondary, headings Label, titles RowTitle, state Caption, newest Secondary)
 * Cap: 600 dp ([KitLampPanelDims.MAX_W])
 * Motion: Edit cross-fades every row's trailing controls at once (QUICK); the strip unfolds (`KitReveal`); receipts as
 *   KitRequestAnswers; Off = instant
 * Replaces: the rings' pulled-out list (DockPanel) for the edge light
 */
@Composable
public fun KitLampPanel(
    model: KitLampPanelModel,
    words: KitLampPanelWords,
    actions: KitLampPanelActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.kitMaxWidth(KitLampPanelDims.MAX_W.dp).testTag(LAMP_PANEL_TAG)) {
        PanelHeader(model, words, actions)
        PanelList(model, words, actions, Modifier.weight(1f, fill = false))
        KitReveal(model.editing) { KitLampEditStrip(words, actions.onMove, actions.onTurnOff) }
        KitDivider()
        PanelFooter(model.all, words, actions)
    }
}

@Composable
private fun PanelHeader(model: KitLampPanelModel, words: KitLampPanelWords, actions: KitLampPanelActions) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = KitLampPanelDims.HEADER_H.dp).padding(start = Space.S4.dp, end = Space.S2.dp, top = Space.S3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = Space.S1.dp)) {
            KitText(words.title, role = TypeRole.Title, kind = KitTextKind.Frame, maxLines = 1, pageTitle = true)
            model.youAreIn?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1) }
        }
        KitButton(
            if (model.editing) words.done else words.edit,
            { actions.onEdit(!model.editing) },
            Modifier.testTag(LAMP_PANEL_EDIT_TAG),
            role = KitButtonRole.Tertiary,
            expand = false,
        )
    }
}

/** The groups, bottom-filled, scrolling from the bottom, faded at a cut edge while there is more past it. */
@Composable
private fun PanelList(model: KitLampPanelModel, words: KitLampPanelWords, actions: KitLampPanelActions, modifier: Modifier) {
    val scroll = rememberScrollState()
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val parts = 1 + model.sections.size
    val fade = KitLampPanelDims.FADE.dp
    Box(
        modifier
            .fillMaxWidth()
            .testTag(LAMP_PANEL_LIST_TAG)
            .layout { m, c ->
                val known = (0 until parts).map { heights[it] ?: 0 }
                val room = if (c.hasBoundedHeight) c.maxHeight else Constraints.Infinity
                val h = if (room == Constraints.Infinity) known.sum() else KitLampPanelRules.viewport(known, room).coerceAtMost(room)
                val p = m.measure(c.copy(minHeight = h, maxHeight = h))
                layout(p.width, h) { p.place(0, 0) }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Reverse scrolling: 0 is the bottom. More above while not at the top; more below while scrolled up.
                val above = scroll.value < scroll.maxValue
                val below = scroll.value > 0
                if (!above && !below) return@drawWithContent
                val f = (fade.toPx() / size.height).coerceIn(0f, 0.5f)
                val top = if (above) Color.Transparent else Color.Black
                val bottom = if (below) Color.Transparent else Color.Black
                drawRect(Brush.verticalGradient(0f to top, f to Color.Black, 1f - f to Color.Black, 1f to bottom), blendMode = BlendMode.DstIn)
            },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll, reverseScrolling = true)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { heights[0] = it.height }) {
                if (model.hidden.isNotEmpty()) HiddenGroup(model.hidden, words, actions)
                if (model.more > 0) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = KitLampPanelDims.FOOTER_H.dp).kitTappable(onClick = actions.onAll, role = KitTappableRole.Row).padding(horizontal = Space.S4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { KitText(words.more(model.more), role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 2) }
                }
                if (model.sections.isEmpty() && model.hidden.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(Space.S4.dp), contentAlignment = Alignment.Center) {
                        KitText(words.empty, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
                    }
                }
            }
            model.sections.forEachIndexed { i, section ->
                Column(Modifier.fillMaxWidth().onSizeChanged { heights[i + 1] = it.height }) {
                    Section(section, model.editing, words, actions)
                }
            }
        }
    }
}

@Composable
private fun Section(section: KitLampSection, editing: Boolean, words: KitLampPanelWords, actions: KitLampPanelActions) {
    KitText(
        section.title,
        Modifier.padding(start = Space.S4.dp, end = Space.S4.dp, top = Space.S3.dp, bottom = Space.S1.dp).testTag(LAMP_PANEL_HEADING_TAG),
        role = TypeRole.Label,
        tone = if (section.group == KitLampGroup.NeedsYou) TextTone.Attention else TextTone.Secondary,
        kind = KitTextKind.Frame,
        maxLines = 1,
        sectionLabel = true,
    )
    if (editing && section.group == KitLampGroup.Pinned) {
        KitReorderRows(
            section.rows,
            key = { it.key },
            title = { it.title },
            onMove = actions.onReorderPinned,
            words = words.reorder,
        ) { row, handle ->
            Column {
                LampBlock(row, editing = true, words, actions, handle)
                if (row != section.rows.last()) RowDivider()
            }
        }
    } else {
        section.rows.forEachIndexed { i, row ->
            androidx.compose.runtime.key(row.key) { LampBlock(row, editing, words, actions, null) }
            if (i < section.rows.size - 1) RowDivider()
        }
    }
}

@Composable
private fun RowDivider() {
    KitDivider(Modifier.padding(start = KitLampPanelDims.TEXT_START.dp), endInset = Space.S4.dp)
}

/** A conversation, with its sub-agents as one step-tree block when it has any. */
@Composable
private fun LampBlock(row: KitLampRow, editing: Boolean, words: KitLampPanelWords, actions: KitLampPanelActions, handle: (@Composable () -> Unit)?) {
    val kind = row.steps
    if (kind == null || row.children.isEmpty()) {
        LampRowView(row, editing, words, actions, handle, null)
        return
    }
    val tree = rememberKitStepTreeState()
    KitStepTree(
        kind,
        row.children,
        words.steps,
        actions =
            KitStepTreeActions(
                onOpen = { if (!editing) actions.onOpenStep(row.key, it) },
                onStop = { actions.onStopStep(row.key, it) },
                onAllow = { actions.onAllow(row.key, it) },
                onDeny = { actions.onDeny(row.key, it) },
                onAnswer = { actions.onAnswer(row.key, it) },
                onUndo = { actions.onUndo(row.key, it) },
                onHoldEnd = { actions.onHoldEnd(row.key, it) },
            ),
        state = tree,
    ) { LampRowView(row, editing, words, actions, handle, this) }
}

/**
 * One conversation row: mark, title (two lines) with time / server / pin, the state word, the newest line or the
 * request's words; at the end the stop square, or in Edit pin · rename · hide; under it the answers or a held receipt.
 */
@Composable
private fun LampRowView(
    row: KitLampRow,
    editing: Boolean,
    words: KitLampPanelWords,
    actions: KitLampPanelActions,
    handle: (@Composable () -> Unit)?,
    tree: KitStepTreeScope?,
) {
    val asking = row.request != null && !editing
    Column(Modifier.fillMaxWidth().testTag(LAMP_ROW_TAG)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (handle != null) handle()
            Row(
                Modifier
                    .weight(1f)
                    // In Edit a row's tap does nothing of its own: opening is for outside Edit; the row has pin, rename, hide.
                    .then(if (editing) Modifier else Modifier.kitTappable(onClick = { actions.onOpen(row.key) }, role = KitTappableRole.Row))
                    .padding(start = if (handle != null) 0.dp else Space.S4.dp, top = Space.S3.dp, bottom = Space.S3.dp, end = Space.S2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val mark = Modifier.testTag(LAMP_ROW_MARK_TAG)
                KitTaskMark(
                    row.mark.taskState(),
                    if (tree != null) with(tree) { mark.stepTreeRoot() } else mark,
                    size = KitMarkSize.Row,
                    progress = row.progress,
                )
                Box(Modifier.width(Space.S3.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        KitText(row.title, Modifier.weight(1f), role = TypeRole.RowTitle, kind = KitTextKind.Frame, maxLines = 2)
                        val meta = listOfNotNull(row.time, row.server).joinToString(" · ")
                        if (meta.isNotEmpty() || row.pinned) {
                            Row(Modifier.padding(start = Space.S2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                                if (row.pinned) KitIcon(KitIcons.pin, size = KitIconSize.Tiny, tone = TextTone.Secondary)
                                if (meta.isNotEmpty()) KitText(meta, role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1, tabular = true)
                            }
                        }
                    }
                    val state = if (tree != null && tree.folded) words.subAgents(tree.count) else row.state
                    state?.let { KitText(it, role = TypeRole.Caption, tone = row.stateTone.textTone(), kind = KitTextKind.Frame, maxLines = 1) }
                    val line = if (asking) row.request!!.words else row.newest
                    line?.let { KitText(it, role = TypeRole.Secondary, tone = if (asking) TextTone.Attention else TextTone.Secondary, kind = KitTextKind.Frame, maxLines = if (asking) 3 else 1) }
                }
            }
            if (tree != null && !editing) tree.FoldChevron()
            // Every row's trailing controls change at once with Edit (QUICK cross-fade, no stagger).
            Crossfade(editing, animationSpec = KitMotion.specs.quick(), label = "kit-lamp-row-trailing") { edit ->
                if (edit && row.canEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KitIconButton(
                            if (row.pinned) KitIcons.unpin else KitIcons.pin,
                            if (row.pinned) words.unpin(row.title) else words.pin(row.title),
                            { actions.onPin(row.key, !row.pinned) },
                            tone = if (row.pinned) TextTone.Accent else TextTone.Primary,
                        )
                        KitIconButton(KitIcons.edit, words.rename(row.title), { actions.onRename(row.key) })
                        KitIconButton(KitIcons.hidden, words.hide(row.title), { actions.onHide(row.key) })
                    }
                } else if (!edit && row.stoppable && row.stopping == null) {
                    KitStopButton(words.stop(row.title), { actions.onStop(row.key) }, Modifier.padding(end = Space.S2.dp))
                } else {
                    Box(Modifier.size(Space.S2.dp))
                }
            }
        }
        val under = Modifier.fillMaxWidth().padding(start = KitLampPanelDims.TEXT_START.dp, end = Space.S2.dp, bottom = Space.S3.dp)
        when {
            asking ->
                KitRequestAnswers(
                    row.request!!,
                    row.title,
                    words.request,
                    onAllow = { actions.onAllow(row.key, null) },
                    onDeny = { actions.onDeny(row.key, null) },
                    onAnswer = { actions.onAnswer(row.key, null) },
                    modifier = under,
                    held = row.held,
                    onUndo = { actions.onUndo(row.key, null) },
                    onHoldEnd = { actions.onHoldEnd(row.key, null) },
                    showWords = false,
                )
            row.stopping != null && !editing ->
                KitReceipt(
                    KitReceiptState.Confirmed,
                    under,
                    label = row.stopping.label,
                    onUndo = { actions.onUndo(row.key, null) },
                    countdownMs = MotionTokens.HELD_ANSWER,
                    onHoldEnd = { actions.onHoldEnd(row.key, null) },
                )
        }
    }
}

@Composable
private fun HiddenGroup(hidden: List<KitLampRow>, words: KitLampPanelWords, actions: KitLampPanelActions) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KitLampPanelDims.FOOTER_H.dp)
            .kitTappable(onClick = { open = !open }, role = KitTappableRole.Row)
            .padding(horizontal = Space.S4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        KitIcon(KitIcons.hidden, size = KitIconSize.Small, tone = TextTone.Secondary)
        KitText(words.hidden(hidden.size), Modifier.weight(1f), role = TypeRole.Label, kind = KitTextKind.Frame, maxLines = 1)
        KitIcon(if (open) KitIcons.chevronUp else KitIcons.chevronDown, size = KitIconSize.Small, tone = TextTone.Secondary)
    }
    if (open) {
        for (row in hidden) {
            Row(Modifier.fillMaxWidth().padding(start = Space.S4.dp, end = Space.S2.dp), verticalAlignment = Alignment.CenterVertically) {
                KitTaskMark(row.mark.taskState(), size = KitMarkSize.Row, progress = row.progress)
                Box(Modifier.width(Space.S3.dp))
                KitText(row.title, Modifier.weight(1f), role = TypeRole.RowTitle, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
                KitButton(words.showAgain, { actions.onShowAgain(row.key) }, role = KitButtonRole.Tertiary, expand = false, semanticsLabel = "${words.showAgain}: ${row.title}")
            }
        }
    }
}

/** "All conversations · N ›" (or "All · N" when it does not fit) and "+ New conversation", one 56 dp row. */
@Composable
private fun PanelFooter(all: Int, words: KitLampPanelWords, actions: KitLampPanelActions) {
    val link: @Composable (String) -> Unit = { text ->
        Row(
            Modifier.heightIn(min = KitLampPanelDims.FOOTER_H.dp).kitTappable(onClick = actions.onAll, role = KitTappableRole.Row).padding(horizontal = Space.S4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            KitText(text, role = TypeRole.Body, kind = KitTextKind.Frame, maxLines = 1, tabular = true)
            KitIcon(KitIcons.chevronRight, size = KitIconSize.Small, tone = TextTone.Secondary)
        }
    }
    Layout(
        content = {
            Box(Modifier.testTag(LAMP_PANEL_ALL_TAG)) { link(words.all(all)) }
            Box(Modifier.testTag(LAMP_PANEL_ALL_SHORT_TAG)) { link(words.allShort(all)) }
            KitButton(words.newConversation, actions.onNew, Modifier.padding(end = Space.S2.dp), role = KitButtonRole.Primary, icon = KitIcons.add, expand = false, maxLines = 1)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = KitLampPanelDims.FOOTER_H.dp),
    ) { ms, c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val button = ms[2].measure(loose)
        val left = (c.maxWidth - button.width).coerceAtLeast(0)
        val long = ms[0].measure(loose.copy(maxWidth = Constraints.Infinity))
        val useLong = long.width <= left
        val link = if (useLong) long else ms[1].measure(loose.copy(maxWidth = left))
        val h = maxOf(c.minHeight, button.height, link.height)
        layout(c.maxWidth, h) {
            link.placeRelative(0, (h - link.height) / 2)
            button.placeRelative(c.maxWidth - button.width, (h - button.height) / 2)
        }
    }
}

/** "Edge light: Move… · Turn off": the strip Edit unfolds above the footer (LAMP-CRAFT §5). */
@Composable
public fun KitLampEditStrip(words: KitLampPanelWords, onMove: () -> Unit, onTurnOff: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.S4.dp, end = Space.S2.dp, top = Space.S2.dp, bottom = Space.S2.dp).testTag(LAMP_EDIT_STRIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitText(words.edgeLight, Modifier.weight(1f), role = TypeRole.Label, kind = KitTextKind.Frame, maxLines = 1, sectionLabel = true)
        KitButton(words.move, onMove, role = KitButtonRole.Primary, expand = false, maxLines = 1)
        KitButton(words.turnOff, onTurnOff, role = KitButtonRole.Secondary, expand = false, maxLines = 1)
    }
}

/**
 * The open list attached to the light (LAMP-CRAFT §3-4): a scrim (by style: 66 / 50 %, Glass 32 %) that closes it on a
 * tap and lightens with a predictive back, and the [panel] growing out of [anchorInWindow] (the light) in
 * [KitGrowFromAnchor], 8 dp from the far edge, 16 dp from the light's edge, at the bottom of the space it is given (the
 * caller keeps the navigation bar and the 80 % cap out of it), with the sheet's corners toward the middle and the
 * panel's on the light's edge. The light itself stays the caller's (it becomes the close tab).
 *
 * States: folded (nothing drawn), opening, open, closing, predictive back
 * Text: none (the panel brings its own)
 * Cap: the panel's 600 dp
 * Motion: KitGrowFromAnchor's; the scrim follows the body; Off = instant
 * Replaces: none
 */
@Composable
public fun KitLampPanelHost(
    open: Boolean,
    anchorInWindow: () -> Rect,
    side: KitLampSide,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    state: KitGrowFromAnchorState = rememberKitGrowFromAnchorState(open),
    panel: @Composable () -> Unit,
) {
    val atEnd = side == KitLampSide.End
    val scrim = LocalKitColors.current.scrim
    val strength = KitLampPanelRules.scrimAlpha(LocalKitStyle.current.style, LocalKitRoles.current.dark)
    Box(modifier.fillMaxSize()) {
        if (open || !state.folded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(LAMP_SCRIM_TAG)
                    .drawBehind {
                        val shown = state.fraction.coerceIn(0f, 1f) * (1f - SCRIM_BACK_SHARE * state.backProgress())
                        drawRect(scrim.copy(alpha = strength * shown))
                    }
                    .pointerInput(onClose) { detectTapGestures { onClose() } },
            )
        }
        KitGrowFromAnchor(
            open,
            anchorInWindow,
            onClose,
            Modifier
                .fillMaxSize()
                .padding(
                    start = (if (atEnd) KitLampPanelDims.FAR else KitLampPanelDims.NEAR).dp,
                    end = (if (atEnd) KitLampPanelDims.NEAR else KitLampPanelDims.FAR).dp,
                    bottom = KitLampPanelDims.BOTTOM.dp,
                ),
            state = state,
            role = SurfaceRole.Floating,
            level = SurfaceLevel.Surface1,
            corners = KitLampPanelRules.corners(lightAtEnd = atEnd),
            wrapHeight = true,
        ) { panel() }
    }
}

private fun KitLampRowMark.taskState(): KitTaskState =
    when (this) {
        KitLampRowMark.NeedsYou -> KitTaskState.NeedsYou
        KitLampRowMark.Failed -> KitTaskState.Failed
        KitLampRowMark.Working -> KitTaskState.Working
        KitLampRowMark.Done -> KitTaskState.Done
        KitLampRowMark.Quiet -> KitTaskState.Waiting
    }

private fun KitLampTone.textTone(): TextTone =
    when (this) {
        KitLampTone.Quiet -> TextTone.Secondary
        KitLampTone.Accent -> TextTone.Accent
        KitLampTone.Attention -> TextTone.Attention
    }

/** How far the scrim lightens with a full predictive back (to 65 %). */
private const val SCRIM_BACK_SHARE: Float = 0.35f // NV: LAMP-CRAFT §3, the scrim lightens to 65 %

/** The panel, for tests. */
public const val LAMP_PANEL_TAG: String = "kit-lamp-panel"
public const val LAMP_PANEL_LIST_TAG: String = "kit-lamp-panel-list"
public const val LAMP_PANEL_HEADING_TAG: String = "kit-lamp-panel-heading"
public const val LAMP_PANEL_EDIT_TAG: String = "kit-lamp-panel-edit"
public const val LAMP_PANEL_ALL_TAG: String = "kit-lamp-panel-all"
public const val LAMP_PANEL_ALL_SHORT_TAG: String = "kit-lamp-panel-all-short"
public const val LAMP_ROW_TAG: String = "kit-lamp-row"
public const val LAMP_ROW_MARK_TAG: String = "kit-lamp-row-mark"
public const val LAMP_EDIT_STRIP_TAG: String = "kit-lamp-edit-strip"
public const val LAMP_SCRIM_TAG: String = "kit-lamp-scrim"
