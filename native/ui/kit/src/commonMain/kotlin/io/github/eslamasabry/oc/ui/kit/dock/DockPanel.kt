package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.agent.AgentMark
import io.github.eslamasabry.oc.ui.kit.agent.agentMonogram
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The pulled-out list: box B at full size, every conversation with its state and last line, sub-agents indented under
 * their parent (alerts first), removed-but-live ones under "N hidden" with a way back, and the new-conversation row at
 * the foot. Its words come up only once the body has reached them; it closes with no bounce (rows empty first).
 * Hold a row and drag it to reorder; dropping it above the pin line pins it.
 */
@Composable
internal fun DockPanel(state: KitEdgeDockState, model: KitDockModel, words: KitDockWords, inks: DockInks, intents: KitDockIntents) {
    val scroll = rememberScrollState()
    // Row spans (in the scrolled column), for the drop index; filled by layout, read on drop.
    val spans = remember { HashMap<Int, Pair<Float, Float>>() }
    var pinLine by remember { mutableFloatStateOf(Float.NaN) }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val s = state.textScale
    val edgeSign = if (state.onRight) 1f else -1f
    val drag = rememberDraggableState { delta -> state.panelDrag(edgeSign * delta / state.density) }
    Box(
        Modifier
            .layout { measurable, constraints ->
                val p = state.panelTarget()
                val d = density
                // The whole pane stands a page gutter in from the dock's screen edge: edge frame [p.left, width - gutter];
                // mirrored, [gutter, width - p.left]. The rows keep their own gutter inside it.
                val w = state.widthDp - DockDims.GUTTER - p.left
                val x = if (state.onRight) p.left else DockDims.GUTTER
                val pl = measurable.measure(Constraints.fixed((w * d).roundToInt().coerceAtLeast(1), ((p.bottom - p.top) * d).roundToInt().coerceAtLeast(1)))
                layout(constraints.maxWidth, constraints.maxHeight) { pl.place((x * d).roundToInt(), (p.top * d).roundToInt()) }
            }
            .graphicsLayer { alpha = if (state.panelOpen) smoothstep(0.45f, 0.95f, state.panelX.value) else smoothstep(0.6f, 1f, state.panelX.value) }
            .drawWithContent {
                // The rows stay inside the body while it grows: cut at its moving inner edge.
                val f = state.computeFrame()
                val p = state.panelTarget()
                val cut = ((f.bLeft - p.left).coerceAtLeast(0f) + 4f) * density
                if (state.onRight) clipRect(left = cut) { this@drawWithContent.drawContent() }
                else clipRect(right = size.width - cut) { this@drawWithContent.drawContent() }
            }
            .draggable(drag, Orientation.Horizontal, onDragStopped = { v -> state.panelDragEnd(edgeSign * v / state.density) }),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // The dock's own screen edge keeps the page gutter (names end in an ellipsis before it); the side
                // toward the middle keeps the body's 8 dp. Physical sides: the dock may stand on either edge.
                .absolutePadding(
                    left = (if (state.onRight) PANEL_INNER_PAD else DockDims.GUTTER).dp,
                    top = DockDims.HEAD.dp,
                    right = (if (state.onRight) DockDims.GUTTER else PANEL_INNER_PAD).dp,
                    bottom = 8.dp,
                )
                .verticalScroll(scroll),
        ) {
            val pinnedCount = model.rings.count { it.pinned }
            if (pinnedCount > 0) {
                KitText(words.pinnedLabel, Modifier.padding(start = 56.dp, bottom = 4.dp), role = TypeRole.Caption, tone = TextTone.Tertiary, kind = KitTextKind.Frame, maxLines = 1)
            }
            model.rings.forEachIndexed { i, ring ->
                if (i == pinnedCount && (pinnedCount > 0 || dragging >= 0)) PinLine(inks) { pinLine = it / state.density }
                val lifted = dragging == i
                Column(
                    Modifier
                        .onPlaced { c ->
                            val y = c.positionInParent().y / state.density
                            spans[i] = y to (y + c.size.height / state.density)
                        }
                        .graphicsLayer {
                            translationY = if (lifted) dragDy * state.density else 0f
                            scaleX = if (lifted) 1.03f else 1f
                            scaleY = if (lifted) 1.03f else 1f
                        }
                        .pointerInput(i, ring.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = i
                                    dragDy = 0f
                                    state.haptic(io.github.eslamasabry.oc.ui.design.motion.KitHaptic.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDy += amount.y / state.density
                                },
                                onDragEnd = {
                                    val span = spans[i]
                                    if (span != null) {
                                        val cy = (span.first + span.second) / 2f + dragDy
                                        val to = panelDropIndex(spans, model.rings.size, cy)
                                        val pinned = if (pinLine.isNaN()) false else cy < pinLine
                                        if (to != i) intents.onReorder(i, to)
                                        if (pinned != ring.pinned) intents.onPinChange(ring.key, pinned)
                                    }
                                    dragging = -1
                                    dragDy = 0f
                                },
                                onDragCancel = {
                                    dragging = -1
                                    dragDy = 0f
                                },
                            )
                        },
                ) {
                    RingRow(ring, ring.key == model.current, words, inks, dim = false, s = s, onPin = { intents.onPinChange(ring.key, !ring.pinned) }) {
                        intents.onOpen(ring.key)
                        state.closePanel()
                    }
                    ChildRows(state, ring, model.currentChild, words, inks, intents)
                }
            }
            if (model.hidden.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = PanelRows.hidden(s).dp).kitTappable(onClick = { state.hiddenOpen = !state.hiddenOpen }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { KitIcon(KitIcons.hidden, size = KitIconSize.Small, tone = TextTone.Secondary) }
                    KitText(words.hidden(model.hidden.size), Modifier.weight(1f), role = TypeRole.RowTitle, kind = KitTextKind.Frame, maxLines = 1)
                    KitIcon(if (state.hiddenOpen) KitIcons.chevronUp else KitIcons.chevronDown, size = KitIconSize.Small, tone = TextTone.Secondary)
                }
                if (state.hiddenOpen) {
                    for (ring in model.hidden) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                RingRow(ring, false, words, inks, dim = true, s = s, onPin = null) {
                                    intents.onOpen(ring.key)
                                    state.closePanel()
                                }
                            }
                            Box(
                                Modifier.size(48.dp).kitTappable(onClick = { intents.onRestore(ring.key) }, onClickLabel = words.actionOn(words.restore, ring.name)),
                                contentAlignment = Alignment.Center,
                            ) {
                                KitIcon(KitIcons.restore, size = KitIconSize.Small, tone = TextTone.Primary, label = words.actionOn(words.restore, ring.name))
                            }
                        }
                    }
                }
            }
            if (model.offersNew) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).kitTappable(onClick = { intents.onNewConversation(); state.closePanel() }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { KitIcon(KitIcons.add, size = KitIconSize.Small, tone = TextTone.Primary) }
                    KitText(words.newConversation, role = TypeRole.RowTitle, kind = KitTextKind.Frame, maxLines = 1)
                }
            }
        }
    }
}

/** The index a row dropped with its centre at [y] lands on: the row whose span holds it, else the nearest. */
internal fun panelDropIndex(spans: Map<Int, Pair<Float, Float>>, count: Int, y: Float): Int {
    var best = 0
    var bd = Float.POSITIVE_INFINITY
    for (i in 0 until count) {
        val sp = spans[i] ?: continue
        if (y >= sp.first && y < sp.second) return i
        val d = minOf(abs(y - sp.first), abs(y - sp.second))
        if (d < bd) {
            bd = d
            best = i
        }
    }
    return best
}

/** The pin line between pinned rows and the rest: a short mark, reported to the drop rule by its height. */
@Composable
private fun PinLine(inks: DockInks, onY: (Float) -> Unit) {
    Canvas(
        Modifier.fillMaxWidth().padding(vertical = (DockDims.PIN_GAP / 2f).dp).height(2.dp).onPlaced { onY(it.positionInParent().y) },
    ) {
        drawLine(inks.ink2, Offset(48.dp.toPx(), size.height / 2f), Offset(size.width - 12.dp.toPx(), size.height / 2f), 1.5.dp.toPx(), alpha = 0.4f)
    }
}

/** One conversation: its ring with the agent's mark, name, state line (and server), last line. */
@Composable
private fun RingRow(ring: KitDockRing, current: Boolean, words: KitDockWords, inks: DockInks, dim: Boolean, s: Float, onPin: (() -> Unit)?, onOpen: () -> Unit) {
    val alert = ring.state == KitDockRingState.NeedsYou || ring.state == KitDockRingState.Failed
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PanelRows.ring(s, ring.server != null, words.hasStateLine(ring)).dp)
            .graphicsLayer { alpha = if (dim) 0.6f else 1f }
            .kitTappable(onClick = onOpen, onClickLabel = words.actionOn(words.open, ring.name))
            .semantics {
                if (onPin != null) {
                    customActions = listOf(CustomAccessibilityAction(words.actionOn(if (ring.pinned) words.unpin else words.pin, ring.name)) { onPin(); true })
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(40.dp)) {
                val tasks = ring.state as? KitDockRingState.WorkingWithTasks
                drawDockRing(
                    Offset(size.width / 2f, size.height / 2f), 16f * density, 3f * density, ring.state, inks,
                    arc = tasks?.let { if (it.total > 0) it.done.toFloat() / it.total else 0f } ?: 1f,
                    check = 1f, settle = 0f, spin = -60f, breath = 0.6f, flash = 0f,
                )
                if (current) drawCircle(inks.ink, 21f * density, alpha = 0.07f)
            }
            AgentMark(ring.markId.orEmpty(), inks.ink, 16.dp, monogram = agentMonogram(ring.name).takeIf { ring.markId == null })
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            KitText(dockTitleLine(ring.name), role = TypeRole.RowTitle, kind = KitTextKind.Frame, maxLines = 1)
            // A blank state line takes no line: the name and the last line close up.
            val line = words.stateLine(ring)
            if (line.isNotBlank()) KitText(line, role = TypeRole.Caption, tone = if (alert) TextTone.Attention else TextTone.Accent.takeIf { ring.state.running } ?: TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
            ring.lastLine?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1) }
        }
    }
}

/** Sub-agent rows under their conversation: alerts first; more than three collapse to the alerts and "N sub-agents". */
@Composable
private fun ChildRows(state: KitEdgeDockState, ring: KitDockRing, currentChild: String?, words: KitDockWords, inks: DockInks, intents: KitDockIntents) {
    if (ring.children.isEmpty()) return
    val expanded = ring.key in state.expanded.value
    val order = childOrder(ring.children)
    val rows = panelChildRows(ring.children, expanded)
    val collapsed = rows < ring.children.size
    val shown = if (collapsed) rows - 1 else rows
    for (k in 0 until shown) {
        val c = ring.children[order[k]]
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PanelRows.child(state.textScale).dp)
                .padding(start = 48.dp)
                .kitTappable(onClick = { intents.onOpenChild(ring.key, c.key); state.closePanel() }, onClickLabel = words.actionOn(words.open, c.name)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(Modifier.size(18.dp)) {
                val tasks = c.state as? KitDockRingState.WorkingWithTasks
                drawDockRing(
                    Offset(size.width / 2f, size.height / 2f), 7f * density, 2.2f * density, c.state, inks,
                    arc = tasks?.let { if (it.total > 0) it.done.toFloat() / it.total else 0f } ?: 1f,
                    check = 1f, settle = 0f, spin = -60f, breath = 0.6f, flash = 0f, mini = true,
                )
                if (c.key == currentChild) drawCircle(inks.ink, 11f * density, alpha = 0.08f)
            }
            KitText(dockTitleLine(c.name), role = TypeRole.Label, kind = KitTextKind.Frame, maxLines = 1)
            val alert = c.state == KitDockRingState.NeedsYou || c.state == KitDockRingState.Failed
            val word = words.stateWord(c.state)
            if (word.isNotBlank()) KitText(word, role = TypeRole.Caption, tone = if (alert) TextTone.Attention else TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
            c.lastLine?.let { KitText(it, Modifier.weight(1f), role = TypeRole.Caption, tone = TextTone.Tertiary, kind = KitTextKind.Frame, maxLines = 1) }
        }
    }
    if (collapsed) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PanelRows.child(state.textScale).dp)
                .padding(start = 48.dp)
                .kitTappable(onClick = { state.expanded.value = state.expanded.value + ring.key }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KitText(words.subAgents(ring.childTotal), role = TypeRole.Label, kind = KitTextKind.Frame, maxLines = 1)
            KitIcon(KitIcons.chevronDown, size = KitIconSize.Tiny, tone = TextTone.Secondary)
        }
    }
}

/**
 * A name shown on one line, with no break opportunity: the platform's one-line cut at a space drops the rest of the
 * title without marking it (device QA run 5, "Quarterly budget review for the" ended with no "..."), while a line
 * with nothing to break at is cut by the overflow and ends in the mark.
 */
internal fun dockTitleLine(name: String): String = name.replace(' ', '\u00A0')

/** The list's padding on the side toward the screen's middle (dp). */
private const val PANEL_INNER_PAD = 8f
