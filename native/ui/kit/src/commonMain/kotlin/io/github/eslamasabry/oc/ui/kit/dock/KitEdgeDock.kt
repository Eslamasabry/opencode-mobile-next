package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.agent.AgentMark
import io.github.eslamasabry.oc.ui.kit.agent.agentMonogram
import io.github.eslamasabry.oc.ui.kit.choice.rememberKitHaptics
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.breath
import io.github.eslamasabry.oc.ui.kit.motion.lap
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitOnLiquidGlass
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The edge dock: a strip on the trailing screen edge (the leading edge in RTL, or the start edge when the person moves
 * it there), one ring per live conversation, floating over whatever the host draws behind it. Kit part; it reports
 * intents through [intents] and performs nothing.
 *
 * - Rings: working with tasks (arc), working (comet), thinking (breath), needs you (attention, double pop, the dock
 *   leans out), finished (arc closes, check draws), failed (failure ink + "!" mark, one shake, a pulse along the body),
 *   idle (hollow). The agent's mark sits in the ring, its name under it. Sub-agents are satellites on the rim.
 * - Scrub along the dock: the body swells out at the finger (box B) with the ring's name, state, sub-agents and actions
 *   (Allow / Deny, or Stop; Rename; Remove). Slide onto a target and lift to run it.
 * - Hold a ring still (charge arc, about 450 ms): B grows into a peek card with the conversation's tail. Lift without
 *   moving to keep it open; slide onto Open (or back onto the ring) to switch. After the peek opened, a move along the
 *   strip picks the ring up to reorder it; above the pin line pins it.
 * - Pull the dock toward the middle: the same body stretches into the list. Flick it to the edge: it tucks to the nub.
 * - Drag the nub or the grip at the dock's head to move it along the edge, or across to the other edge.
 *
 * Place it over the whole screen area it may cover ([modifier] fills it) and tell it what to keep clear of ([insets]).
 * Apply [KitEdgeDockState.gestureExclusion] with `systemGestureExclusion` on the same node so Android's back gesture
 * does not take the scrub zone. Under a lens, read [KitEdgeDockState.body] to draw the joined glass.
 *
 * Motion: springs in the draw and layout phases only; every spec in [DockSprings] snaps by itself under reduced motion
 * (no travel, shake, pulse, comet or overshoot). Text: frame (capped at 1.3 x).
 *
 * The body: box A (the strip) and box B (the swell, peek or list), as they are now, joined by the cubic smooth-minimum
 * field ([DockJoinField]); [KitEdgeDockState.body] hands over exactly those boxes and the join's reach, and the Solid face
 * fills exactly that field's zero line. The face is decided in one place (`rememberDockFace`); a face that draws the
 * body itself (`DockFace.drawsOwnBody`, a lens) gets no clip and no path from the body node.
 *
 * NOTES (known, not fixed yet; from the glass owner's review of 2026-11-11):
 * - The join field here is a copy of the glass branch's `LiquidJoin.sample` (same definition, from the liquid-glass
 *   skill's `joined-glass.md` and `join_shape.py`); `LiquidJoin` is not on this branch. When both meet, one of them
 *   should go, and `DockGeometryTest` should check the outline against `LiquidJoin.sample` itself.
 * - Shoulders (the concave blends into the screen edge) are a third shape outside the two-box join; the Solid face
 *   draws them, the join does not.
 * - Tucked, the strip's hit area is narrower than the 48 dp band the nub promises (the touch node is the nub's band
 *   only while tucked; the ring nodes are the rest width).
 * - Ring names are one line at most [DockDims.LABEL_MAX] dp wide (6 dp in from the strip's sides): a long name ends in
 *   an ellipsis.
 * - The pulled-out list composes every row (a scrolling Column, not a lazy list): a cost with many conversations.
 * - Rings have no exit animation: a removed ring disappears at once.
 * - The back-gesture exclusion ([KitEdgeDockState.gestureExclusion]) is the host's job (`systemGestureExclusion`);
 *   check it on a device.
 * - `panelBoxNow` allocates a box per frame while the dock moves (its cache key is the moving top).
 */
@Composable
public fun KitEdgeDock(
    model: KitDockModel,
    intents: KitDockIntents,
    words: KitDockWords,
    modifier: Modifier = Modifier,
    insets: KitDockInsets = KitDockInsets(),
    state: KitEdgeDockState = rememberKitEdgeDockState(),
) {
    // No rings and no "+" to offer (a screen with its own new-conversation button): the dock draws nothing at all and
    // takes no touches. Anything it had open closes at once, so it does not come back open when a ring does.
    if (!model.offersNew && model.rings.isEmpty()) {
        state.rings = emptyList()
        state.column = DockColumn(0, 0, 0, DockDims.PITCH, 0f, 0f)
        LaunchedEffect(state) { state.clearAll() }
        return
    }
    // No rings: the dock still stands (owner 2026-11-11: always there), its strip or its nub and the "+" alone.
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val motion = LocalKitMotion.current
    val colors = LocalKitColors.current
    val face = rememberDockFace(state)
    val inks = remember(colors, face.ink, face.ink2) { DockInks.from(colors, face.ink, face.ink2) }
    val haptics = rememberKitHaptics()
    val scope = rememberCoroutineScope()
    val textScale = kitTextScale().coerceIn(1f, TextScalePolicy.FRAME_CAP)
    // Tucked, or showing an alert's look-only swell, the dock lets the page under it see every touch too (dock study,
    // findings 1 and 2): its area shares touches with the screen's other layers instead of taking them.
    val shares by remember(state) {
        androidx.compose.runtime.derivedStateOf {
            (state.out.value < DockDims.REST_W * 0.5f && !state.anythingOpen) || (state.swellLookOnly && state.swell != null)
        }
    }
    // Under a glass face the dock's area hosts the words on the glass (their ink flips with it), as a bar's row does.
    BoxWithConstraints(
        modifier.fillMaxSize().then(if (shares) DockSharesTouches(state) else Modifier).then(face.words)
            .semantics { isTraversalGroup = true }.testTag("kit-edge-dock"),
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val right = physicallyRight(model.placement.side, rtl)
        val startInset = insets.start.value
        val endInset = insets.end.value
        // The insets in the edge frame: the dock's own edge, and the far one.
        val edgeInset = if ((model.placement.side == KitDockSide.End)) endInset else startInset
        val farInset = if ((model.placement.side == KitDockSide.End)) startInset else endInset
        val safeTop = insets.top.value
        val safeBottom = h - insets.bottom.value
        val pinned = model.rings.count { it.pinned }
        val column = DockColumn.of(model.rings.size, pinned, safeBottom - safeTop, reordering = state.carrying >= 0, offersNew = model.offersNew)
        val keepClear = remember(insets.keepClear, right, w) {
            insets.keepClear.map { r ->
                val d = DockRect(r.left.value, r.top.value, r.right.value, r.bottom.value)
                if (right) d else mirrorRect(d, w)
            }
        }
        // Layout facts for the draw and gesture phases (plain fields: they change with the window, not per frame).
        state.widthDp = w
        state.heightDp = h
        state.safeTop = safeTop
        state.safeBottom = safeBottom
        state.edgeInset = edgeInset
        state.farInset = farInset
        state.onRight = right
        state.onRightForEnd = !rtl
        state.density = density.density
        state.textScale = textScale
        state.column = column
        state.keepClear = keepClear
        state.reduced = motion.reduced
        state.shoulders = face.shoulders
        state.rings = model.rings
        state.scope = scope
        state.haptic = haptics
        state.intents = intents
        state.words = words
        state.panelContent = panelContentHeight(model.rings, model.hidden, state.hiddenOpen, state.expanded.value, textScale) { words.hasStateLine(it) }

        // Where it lives: the person's height, clear of the bars, the keyboard and the keep-clear spots.
        val placedTop = placeTop(column.height, model.placement.verticalBias, safeTop, safeBottom, keepClear, w - edgeInset - DockDims.REST_W - DockDims.SHOULDER_REACH, w)
        LaunchedEffect(placedTop, state.placeGeneration) {
            if (!state.placed) {
                state.top.snapTo(placedTop)
                state.placed = true
            } else if (!state.moving) {
                state.top.animateTo(placedTop, DockSprings.fill(motion.reduced))
            }
        }
        // Tucked to the nub, or out: arriving from the edge passes its line once; tucking does not bounce.
        LaunchedEffect(model.tucked) {
            val to = if (model.tucked) DockDims.NUB_W else DockDims.REST_W
            if (state.out.value == to) return@LaunchedEffect
            if (model.tucked) {
                state.stripOpen = false
                state.closeSwell()
                if (state.panelOpen) state.closePanel()
            }
            state.out.animateTo(to, if (model.tucked) DockSprings.close(motion.reduced) else DockSprings.drop(motion.reduced))
        }
        // The model moved on under an open swell: close it rather than show another ring's words.
        LaunchedEffect(model.rings.size) {
            if (state.swellRing >= model.rings.size) state.closeSwell()
        }
        // Keep the conversation on screen in view. A new column (the area changed) first settles the scroll inside what
        // it can scroll (the rings already read it so, [KitEdgeDockState.scrollNow]).
        // While a ring is renamed (the keyboard is up and the column may have shrunk) it is the one kept in view.
        val currentIndex = model.rings.indexOfFirst { it.key == model.current }
        val renamingIndex = state.renaming?.let { k -> model.rings.indexOfFirst { it.key == k } } ?: -1
        // The renamed ring rides out into the swell, so the column keeps another ring in view (the strip keeps its rings).
        val shownIndex =
            if (renamingIndex >= 0 && model.rings.size > 1) {
                if (currentIndex >= 0 && currentIndex != renamingIndex) currentIndex else if (renamingIndex > 0) renamingIndex - 1 else renamingIndex + 1
            } else if (renamingIndex >= 0) renamingIndex else currentIndex
        LaunchedEffect(shownIndex, column) {
            val now = state.scroll.value.coerceIn(0f, column.maxScroll)
            if (now != state.scroll.value) state.scroll.snapTo(now)
            if (shownIndex >= 0) state.scroll.animateTo(column.scrollToShow(shownIndex, now), DockSprings.fill(motion.reduced))
        }

        DockBodyNode(state, face, inks)
        // On glass the words' second line reads brighter, as on the bar.
        CompositionLocalProvider(LocalKitOnLiquidGlass provides face.onGlass) {
            DockRingBand(state) {
                model.rings.forEachIndexed { i, ring ->
                    androidx.compose.runtime.key(ring.key) {
                        DockRingNode(state, ring, i, ring.key == model.current, inks, words, intents)
                    }
                }
            }
            if (model.offersNew) DockFootNode(state, words, intents)
            DockHitNode(state)
            val swell = state.swell
            if (swell != null && state.swellRing in model.rings.indices) {
                DockSwellContent(state, swell, model.rings[state.swellRing], words, inks)
            }
            if (state.panelShown) DockPanel(state, model, words, inks, intents)
        }
        KitBackGuard(enabled = state.anythingOpen) {
            when {
                state.renaming != null -> state.endRename(save = false, text = "")
                state.panelOpen -> state.closePanel()
                state.swell != null && !state.swellClosing -> state.closeSwell()
                else -> state.tuckByPerson()
            }
        }
        // An open strip never outlives its page: leaving the page puts it away, so the next page does not show it open.
        DisposableEffect(state) { onDispose { if (state.stripOpen) state.tuckByPerson() } }
    }
}

/**
 * The body: the face clipped to the outline (the join field's zero line), then the failure pulse and the marks (grip,
 * pin line). A face that draws the body itself ([DockFace.drawsOwnBody]) is drawn as it is, with no clip and no path:
 * it reads [KitEdgeDockState.body] (the same boxes and reach) in its own draw.
 */
@Composable
private fun DockBodyNode(state: KitEdgeDockState, face: DockFace, inks: DockInks) {
    val path = remember { Path() }
    val pulsePath = remember { Path() }
    Box(
        Modifier.fillMaxSize()
            .drawWithContent {
                val f = state.computeFrame()
                val pt = state.pulse.value
                val pulsing = pt < 1f && state.pulseRing >= 0 && !state.reduced
                if (face.drawsOwnBody) {
                    this@drawWithContent.drawContent()
                    if (!pulsing) {
                        drawMarks(state, f, inks)
                        return@drawWithContent
                    }
                }
                val o = state.bodyOutline(f)
                val d = density
                if (pulsing) {
                    val start = o.indexNearY(state.slotY(state.pulseRing), f.aLeft + 2f)
                    pulseOffsets(o, start, pt * 900f, 3.4f * kotlin.math.exp(-pt * 3.3f), state.pulseDx, state.pulseDy)
                }
                fun px(i: Int): Float = state.phys(o.x[i] + (if (pulsing) state.pulseDx[i] else 0f)) * d
                fun py(i: Int): Float = (o.y[i] + (if (pulsing) state.pulseDy[i] else 0f)) * d
                if (!face.drawsOwnBody) {
                    val beyond = state.phys(f.edgeX + DockDims.OVERHANG) * d
                    path.rewind()
                    path.moveTo(beyond, py(0))
                    for (i in 0 until o.n) path.lineTo(px(i), py(i))
                    path.lineTo(beyond, py(o.n - 1))
                    path.close()
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
                if (pulsing) {
                    pulsePath.rewind()
                    val s0 = o.s[o.indexNearY(state.slotY(state.pulseRing), f.aLeft + 2f)]
                    val h1 = s0 + pt * 900f
                    val h2 = s0 - pt * 900f
                    var open1 = false
                    var open2 = false
                    for (i in 0 until o.n) {
                        val s = o.s[i]
                        if (s in (h1 - 64f)..h1) {
                            if (open1) pulsePath.lineTo(px(i), py(i)) else pulsePath.moveTo(px(i), py(i))
                            open1 = true
                        }
                    }
                    for (i in 0 until o.n) {
                        val s = o.s[i]
                        if (s in h2..(h2 + 64f)) {
                            if (open2) pulsePath.lineTo(px(i), py(i)) else pulsePath.moveTo(px(i), py(i))
                            open2 = true
                        }
                    }
                    val a = (1f - pt).coerceIn(0f, 1f)
                    drawPath(pulsePath, inks.failure, alpha = a * a, style = Stroke(2.6f * d, cap = StrokeCap.Round))
                }
                drawMarks(state, f, inks)
            }
            .then(face.modifier),
    )
}

/** The marks on the body: the nub's dots while tucked, the grip at the dock's head and the pin line (not surfaces). */
private fun DrawScope.drawMarks(state: KitEdgeDockState, f: DockFrame, inks: DockInks) {
    val d = density
    // Tucked: the rings shrink into dots of their state colour along the nub.
    val out = state.out.value
    val vis = smoothstep(15f, 58f, out)
    if (vis < 1f) {
        val nx = state.phys(f.edgeX - max(out, DockDims.NUB_W) / 2f) * d
        val bandTop = state.ringBandTop()
        val bandBottom = state.ringBandBottom()
        state.rings.forEachIndexed { i, r ->
            val cy = state.slotY(i) - DockDims.RING_LIFT
            // Only the rings in view, as the strip shows them.
            if (cy in bandTop..bandBottom) drawCircle(inks.of(r.state), 3f * d, Offset(nx, cy * d), alpha = 1f - vis)
        }
    }
    // The grip at the dock's head: a short bar (a mark, not a surface).
    if (out > DockDims.REST_W * 0.5f && !f.panel) {
        val cx = state.phys(f.aLeft + (out + state.lean.value) / 2f) * d
        val gy = (state.top.value + DockDims.HEAD / 2f + 2f) * d
        drawLine(inks.ink2, Offset(cx - 8f * d, gy), Offset(cx + 8f * d, gy), 3f * d, StrokeCap.Round, alpha = 0.35f)
        val col = state.column
        if (col.pinGap > 0f) {
            val ly = col.pinLineY(state.top.value, state.scrollNow) * d
            drawLine(inks.ink2, Offset(cx - 14f * d, ly), Offset(cx + 14f * d, ly), 1.5f * d, StrokeCap.Round, alpha = 0.5f)
        }
    }
}

/**
 * The band the rings stand in: the column's viewport ([KitEdgeDockState.ringBandTop]..[KitEdgeDockState.ringBandBottom])
 * across the whole area, cut at its edges. Placed in the layout phase from the same state the body is drawn from, so
 * the rings and the body always share one frame; a ring scrolled out of the viewport is cut away instead of standing
 * over the head, the "+", or outside the body. While a ring rides out to be renamed the band also holds box B.
 */
@Composable
private fun DockRingBand(state: KitEdgeDockState, content: @Composable () -> Unit) {
    Box(
        Modifier
            .layout { measurable, constraints ->
                val (t, b) = ringBand(state)
                val d = density
                val hPx = ((b - t) * d).roundToInt().coerceAtLeast(0)
                val p = measurable.measure(Constraints.fixed(constraints.maxWidth, hPx))
                layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, (t * d).roundToInt()) }
            }
            .clipToBounds(),
    ) {
        content()
    }
}

/** The ring band's top and bottom now (dp, area); see [DockRingBand]. */
private fun ringBand(state: KitEdgeDockState): Pair<Float, Float> {
    var t = state.ringBandTop()
    var b = state.ringBandBottom()
    if (state.renaming != null && state.ride.value > 0f) {
        val f = state.computeFrame()
        if (f.hasB) {
            t = min(t, f.bTop)
            b = max(b, f.bBottom)
        }
    }
    return t to b
}

/** One ring: its art, its mark, its name, its semantics and keys. Moves by its offset; never recomposes per frame. */
@Composable
private fun DockRingNode(
    state: KitEdgeDockState,
    ring: KitDockRing,
    index: Int,
    current: Boolean,
    inks: DockInks,
    words: KitDockWords,
    intents: KitDockIntents,
) {
    val m = remember { RingMotion() }
    val reduced = state.reduced
    val focus = LocalFocusManager.current
    // Its place: the model's index, shifted while another ring is carried over it.
    val slotTarget = visualIndex(index, state.carrying, state.carryTo).toFloat()
    LaunchedEffect(slotTarget) {
        if (m.slot.value < 0f) m.slot.snapTo(slotTarget) else m.slot.animateTo(slotTarget, DockSprings.follow(reduced))
    }
    LaunchedEffect(Unit) { m.presence.animateTo(1f, DockSprings.drop(reduced)) }
    // The task arc springs forward with a small overshoot and a pop.
    val arcTarget =
        when (val s = ring.state) {
            is KitDockRingState.WorkingWithTasks -> if (s.total > 0) s.done.toFloat() / s.total else 0f
            KitDockRingState.Working, KitDockRingState.Idle -> 0f
            else -> 1f
        }
    LaunchedEffect(arcTarget) {
        val grew = arcTarget > m.arc.value + 0.001f && m.arc.value > 0f
        if (grew && !reduced) launch { m.pop.animateTo(0f, DockSprings.kick(reduced), initialVelocity = 2.4f) }
        m.arc.animateTo(arcTarget, DockSprings.arc(reduced))
    }
    // Finished: the arc closes, the check draws itself, then the ring settles dim.
    val finished = ring.state == KitDockRingState.Finished
    LaunchedEffect(finished) {
        if (!finished) {
            m.check.snapTo(0f)
            m.settle.snapTo(0f)
            return@LaunchedEffect
        }
        if (reduced) {
            m.check.snapTo(1f)
            m.settle.animateTo(1f, DockSprings.look(reduced))
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(200)
        m.check.animateTo(1f, DockSprings.timed(reduced, 340))
        launch { m.pop.animateTo(0f, DockSprings.kick(reduced), initialVelocity = 3f) }
        kotlinx.coroutines.delay(2100)
        m.settle.animateTo(1f, DockSprings.timed(reduced, 400))
    }
    // An alert: a request pops twice and leans the dock out; a failure flashes, shakes once and pulses the body.
    LaunchedEffect(ring.pulse) {
        val rose = m.lastPulse >= 0 && ring.pulse > m.lastPulse
        m.lastPulse = ring.pulse
        if (!rose) return@LaunchedEffect
        val failed = ring.state == KitDockRingState.Failed
        state.alert(index, failed)
        // Tucked: the ring's dot only changes colour; no pop, flash or shake on the nub.
        if (reduced || state.tucked) return@LaunchedEffect
        if (failed) {
            launch {
                m.flash.snapTo(1f)
                m.flash.animateTo(0f, DockSprings.timed(reduced, 220))
            }
            m.shake.animateTo(0f, DockSprings.shake(reduced), initialVelocity = 560f)
        } else {
            m.pop.animateTo(0f, DockSprings.pop(reduced), initialVelocity = 3f)
            kotlinx.coroutines.delay(360)
            m.pop.animateTo(0f, DockSprings.pop(reduced), initialVelocity = 3f)
        }
    }
    // Loops (the comet, the breath) run only where the kit allows ambient loops: never under reduced motion or stills.
    val specs = KitMotion.specs
    val loops = LocalKitMotion.current.ambientLoops
    val transition = if (loops) rememberInfiniteTransition(label = "kit.dock.ring") else null
    val spin: State<Float>? =
        if (transition != null && ring.state == KitDockRingState.Working) {
            transition.animateFloat(0f, 360f, specs.lap((360f / COMET_DEG_PER_S * 1000f).toInt()), label = "kit.dock.comet")
        } else null
    val breath: State<Float>? =
        if (transition != null && (ring.state == KitDockRingState.Thinking || ring.children.any { it.state.running })) {
            transition.animateFloat(0f, 1f, specs.breath(1200), label = "kit.dock.breath")
        } else null
    val order = remember(ring.children) { childOrder(ring.children) }
    val measurer = rememberTextMeasurer()
    val moreCount = if (ring.childTotal > 4) ring.childTotal - 4 else 0
    val more = remember(moreCount, inks.ink2) {
        if (moreCount > 0) measurer.measure("+$moreCount", TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = inks.ink2)) else null
    }
    val label = words.ringLabel(ring)
    val actions = ringActions(state, ring, index, words, intents)
    val towardLeft = state.onRight
    // The name keeps the page gutter from the screen's own edge, and its inner margin from the strip's other side.
    val labelMax = DockDims.labelMax(state.edgeInset)
    val labelDx = DockDims.labelShift(state.edgeInset) * (if (state.onRight) 1f else -1f)
    Box(
        Modifier
            .absoluteOffset {
                // Relative to the ring band (placed at its top, rounded the same way).
                val p = ringCenter(state, index, m)
                val band = (ringBand(state).first * density).roundToInt()
                IntOffset(((p.x - DockDims.REST_W / 2f) * density).roundToInt(), ((p.y - state.column.pitch / 2f) * density).roundToInt() - band)
            }
            // Whole rings only: a ring not wholly inside the viewport takes no room and draws nothing (the others do not
            // slide under the "+" while the column scrolls or the area changes).
            .layout { measurable, constraints ->
                // Measured at no size when it does not stand (so its bounds are empty and nothing of it draws).
                val p = measurable.measure(if (ringStands(state, index, m)) constraints else Constraints.fixed(0, 0))
                layout(p.width, p.height) { p.place(0, 0) }
            }
            .size(DockDims.REST_W.dp, state.column.pitch.dp)
            .graphicsLayer {
                val vis = smoothstep(15f, 58f, state.out.value) * (1f - smoothstep(0.1f, 0.45f, state.panelX.value))
                // While another ring is renamed the strip's rings stay, dimmed.
                val dim = if (state.renaming != null && state.renaming != ring.key) 0.45f else 1f
                alpha = if (ringStands(state, index, m)) vis * m.presence.value.coerceIn(0f, 1f) * dim else 0f
                val s = lerp(0.3f, 1f, vis) * (1f + m.pop.value) * (if (state.carrying == index) 1.1f else 1f)
                scaleX = s
                scaleY = s
                translationX = m.shake.value * density
            }
            .semantics {
                contentDescription = label
                role = Role.Button
                selected = current
                onClick(words.actionOn(words.open, ring.name)) {
                    intents.onOpen(ring.key)
                    true
                }
                customActions = actions
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                    Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        intents.onOpen(ring.key)
                        true
                    }
                    Key.F2 -> {
                        state.startRename(index)
                        true
                    }
                    Key.Menu -> {
                        state.openSwell(SwellKind.Peek, index)
                        state.gesture.keepOpenForAlert(index)
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val towardMiddle = (e.key == Key.DirectionLeft) == state.onRight
                        if (towardMiddle) state.openPanel() else if (state.panelOpen) state.closePanel()
                        true
                    }
                    Key.Escape -> {
                        if (state.anythingOpen) {
                            state.closeSwell()
                            if (state.panelOpen) state.closePanel()
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
            .drawBehind {
                val d = density
                val c = Offset(size.width / 2f, size.height / 2f - DockDims.RING_LIFT * d)
                val r = DockDims.RING_R * d
                val ridden = state.renaming == ring.key
                drawDockRing(
                    c, r, DockDims.RING_STROKE * d, ring.state, inks,
                    arc = m.arc.value, check = m.check.value, settle = m.settle.value,
                    spin = spin?.value ?: -60f, breath = breath?.value ?: 0.6f, flash = m.flash.value,
                )
                if (state.pressRing == index) drawCharge(c, r, inks.ink, state.charge.value)
                if (!ridden) {
                    val satA = 1f - smoothstep(0.2f, 0.7f, state.panelX.value)
                    drawSatellites(c, r, ring.children, order, inks, towardLeft, breath?.value ?: 1f, more, satA)
                }
                if (current) {
                    // The conversation on screen: a soft lens behind its ring (a mark of state, not a surface).
                    drawCircle(inks.ink, r + 5f * d, c, alpha = 0.07f)
                }
            },
    ) {
        // The agent's mark in the ring's centre.
        Box(
            Modifier.align(Alignment.Center).absoluteOffset(y = (-DockDims.RING_LIFT).dp).graphicsLayer {
                alpha = if (ring.state == KitDockRingState.Idle) 0.45f else 1f
            },
        ) {
            AgentMark(ring.markId.orEmpty(), inks.ink, 18.dp, monogram = agentMonogram(ring.name).takeIf { ring.markId == null })
        }
        // The name under the ring (hidden while it rides out to be renamed).
        Box(
            Modifier.align(Alignment.Center).absoluteOffset(x = labelDx.dp, y = 21.dp).widthIn(max = labelMax.dp).graphicsLayer {
                alpha = 1f - state.ride.value.coerceIn(0f, 1f) * (if (state.renaming == ring.key) 1f else 0f)
            },
        ) {
            KitText(ring.name, role = TypeRole.Caption, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = 1)
        }
    }
}

/**
 * Whether ring [index] is in the strip now: wholly inside the column's viewport, or riding out to be renamed (it then
 * stands in box B). Read in the layout phase.
 */
private fun ringStands(state: KitEdgeDockState, index: Int, m: RingMotion): Boolean {
    if (state.renaming != null && state.renaming == state.rings.getOrNull(index)?.key) return true
    val viewTop = state.top.value + DockDims.HEAD
    val y = ringCenter(state, index, m).y
    return ringStandsWhole(y, state.column.pitch, viewTop, viewTop + state.column.viewportHeight)
}

/** A ring's centre (edge frame, dp, converted to physical x): its slot (a carried ring's is its drop place), the lean, the ride out for rename. */
private fun ringCenter(state: KitEdgeDockState, index: Int, m: RingMotion): Offset {
    val o = max(state.out.value, DockDims.NUB_W) + state.lean.value
    val baseX = state.widthDp - state.edgeInset - o / 2f - state.stripShift()
    var y = state.column.slotCenterAt(m.slot.value.coerceAtLeast(0f), state.top.value, state.scrollNow)
    // A carried ring stands in the gap the others make for it (its slot is the drop place, [visualIndex]), not under
    // the finger between two slots, where it covered its neighbours' rings and names (device QA 2026-11-11, m1).
    var x = baseX
    val ride = state.ride.value
    if (ride > 0f && state.swellRing == index && state.renaming == state.rings.getOrNull(index)?.key) {
        val f = state.computeFrame()
        val rideX = (state.swell?.rideX ?: 0f)
        x = lerp(baseX, f.bRestLeft + rideX, ride)
        y = lerp(y, (f.bTop + f.bBottom) / 2f + DockDims.RING_LIFT, ride)
    }
    return Offset(state.phys(x), y)
}

/** Where ring [i] stands while ring [from] is carried to [to]: the others make room. */
internal fun visualIndex(i: Int, from: Int, to: Int): Int =
    when {
        from < 0 || to < 0 -> i
        i == from -> to
        from < to && i in (from + 1)..to -> i - 1
        from > to && i in to until from -> i + 1
        else -> i
    }

/** The custom actions of a ring for assistive technology: they name their target. */
private fun ringActions(state: KitEdgeDockState, ring: KitDockRing, index: Int, words: KitDockWords, intents: KitDockIntents): List<CustomAccessibilityAction> {
    val list = ArrayList<CustomAccessibilityAction>(8)
    if (ring.state == KitDockRingState.NeedsYou) {
        list += CustomAccessibilityAction(words.actionOn(words.allow, ring.name)) { intents.onAllow(ring.key); true }
        list += CustomAccessibilityAction(words.actionOn(words.deny, ring.name)) { intents.onDeny(ring.key); true }
    } else if (ring.state.running) {
        list += CustomAccessibilityAction(words.actionOn(words.stop, ring.name)) { intents.onStop(ring.key); true }
    }
    for (c in ring.children) {
        list += CustomAccessibilityAction(words.actionOn(words.open, c.name)) { intents.onOpenChild(ring.key, c.key); true }
    }
    list += CustomAccessibilityAction(words.actionOn(words.rename, ring.name)) { state.startRename(index); true }
    list += CustomAccessibilityAction(words.actionOn(if (ring.pinned) words.unpin else words.pin, ring.name)) { intents.onPinChange(ring.key, !ring.pinned); true }
    list += CustomAccessibilityAction(words.actionOn(words.remove, ring.name)) { intents.onRemove(ring.key); true }
    return list
}

/**
 * The round new-conversation button at the dock's foot: the kit's round icon button (its face from the resolver, no
 * ring of the dock's own), a 48 dp target. Touches reach it through the dock's gesture loop (the foot zone); its own
 * click serves assistive technology and keys.
 */
@Composable
private fun DockFootNode(state: KitEdgeDockState, words: KitDockWords, intents: KitDockIntents) {
    Box(
        Modifier
            .absoluteOffset {
                val o = max(state.out.value, DockDims.NUB_W) + state.lean.value
                val cx = state.phys(state.widthDp - state.edgeInset - o / 2f - state.stripShift())
                val cy = state.top.value + DockDims.HEAD + state.column.viewportHeight + DockDims.FOOT / 2f
                IntOffset(((cx - 24f) * density).roundToInt(), ((cy - 24f) * density).roundToInt())
            }
            .size(48.dp)
            .graphicsLayer { alpha = smoothstep(15f, 58f, state.out.value) * (1f - smoothstep(0.1f, 0.45f, state.panelX.value)) },
        contentAlignment = Alignment.Center,
    ) {
        KitIconButton(
            icon = KitIcons.add,
            label = words.newConversation,
            onClick = { intents.onNewConversation() },
            size = KitIconSize.Small.dp,
            round = true,
        )
    }
}

/**
 * The touch area: the strip (or the nub) at rest, the whole dock area while something is open (a tap outside closes
 * it). One pointer loop runs the gesture rules ([DockGesture]); timers settle the hold without a move.
 */
@Composable
private fun DockHitNode(state: KitEdgeDockState) {
    Box(
        Modifier
            .layout { measurable, constraints ->
                val d = density
                val full = state.takesWholeArea || state.gesture.mode != DockMode.Idle
                val f = state.computeFrame()
                val tucked = state.out.value < DockDims.REST_W * 0.5f
                val l: Float
                val t: Float
                val r: Float
                val b: Float
                if (full) {
                    l = 0f
                    t = 0f
                    r = state.widthDp
                    b = state.heightDp
                } else {
                    val w = if (tucked) DockDims.NUB_TOUCH else (state.widthDp - f.aLeft + 8f)
                    val el = state.widthDp - w
                    val x0 = state.phys(el)
                    val x1 = state.phys(state.widthDp)
                    l = min(x0, x1)
                    r = max(x0, x1)
                    t = f.aTop - 8f
                    b = f.aBottom + 8f
                }
                val wPx = ((r - l) * d).roundToInt().coerceAtLeast(1)
                val hPx = ((b - t) * d).roundToInt().coerceAtLeast(1)
                state.hitLeft = l
                state.hitTop = t
                val p = measurable.measure(Constraints.fixed(wPx, hPx))
                layout(constraints.maxWidth, constraints.maxHeight) { p.place((l * d).roundToInt(), (t * d).roundToInt()) }
            }
            .pointerInput(state) {
                val rules = DockRules()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val d = density
                    fun ex(px: Float): Float {
                        val phys = state.hitLeft + px / d
                        return if (state.onRight) phys else mirrorX(phys, state.widthDp)
                    }
                    fun ey(py: Float): Float = state.hitTop + py / d
                    val x0 = ex(down.position.x)
                    val y0 = ey(down.position.y)
                    val zone = state.zoneAt(x0, y0)
                    if (zone == DockZone.Outside || zone == DockZone.None) {
                        if (state.anythingOpen) {
                            down.consume()
                            if (state.renaming != null) state.endRename(save = true, text = state.renameText)
                            state.dismissOutside()
                        }
                        return@awaitEachGesture
                    }
                    // The tucked band acts only on a tap and on a mostly-sideways drag; until it knows, it takes nothing,
                    // so a scroll or a long press that starts on it stays the page's (dock study, finding 1).
                    val band = zone == DockZone.Nub
                    if (!band) down.consume()
                    // The page shares the band's touches and sees them after the dock only in the Initial pass: the band
                    // reads (and takes) its tap's lift and its sideways drag there, before the page could act on them.
                    val pass = if (band) PointerEventPass.Initial else PointerEventPass.Main
                    val t0 = down.uptimeMillis
                    state.gesture.down(zone, x0, y0, t0, state.hits)
                    state.pressRing = if (zone == DockZone.Ring) state.gesture.ring else -1
                    state.onDown(zone)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var now = t0
                    try {
                        while (true) {
                            val wait =
                                if (state.gesture.holding) {
                                    val held = now - t0
                                    (if (held < rules.swellDelayMs) rules.swellDelayMs else rules.holdMs) - held
                                } else if (band && state.gesture.mode == DockMode.NubPress) {
                                    viewConfiguration.longPressTimeoutMillis - (now - t0)
                                } else -1L
                            val event =
                                if (wait > 0) withTimeoutOrNull(wait) { awaitPointerEvent(pass) }
                                else if (band && state.gesture.mode == DockMode.NubPress) null
                                else awaitPointerEvent(pass)
                            if (event == null && band) {
                                // A long press on the band is the page's.
                                state.gesture.cancel()
                                break
                            }
                            if (event == null) {
                                now = t0 + (if (now - t0 < rules.swellDelayMs) rules.swellDelayMs else rules.holdMs)
                                state.onFeedback(state.gesture.elapsed(now))
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            now = change.uptimeMillis
                            val x = ex(change.position.x)
                            val y = ey(change.position.y)
                            if (!change.pressed) {
                                val v = tracker.calculateVelocity().x / d
                                val vEdge = if (state.onRight) v else -v
                                change.consume()
                                state.computeFrame()
                                state.pressRing = -1
                                state.onOutcome(state.gesture.up(x, y, vEdge, state.hits))
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!band) change.consume()
                            state.computeFrame()
                            val fb = state.gesture.move(x, y, now, state.hits)
                            if (band) {
                                // Not sideways: the page's scroll (the band gave the gesture up). Sideways: the dock's.
                                if (state.gesture.mode == DockMode.Idle) break
                                if (state.gesture.mode == DockMode.NubDrag) change.consume()
                            }
                            if (!state.gesture.holding) {
                                state.pressRing = -1
                                state.stopCharge()
                            }
                            state.onFeedback(fb)
                            state.onMove(x, y, x0)
                        }
                    } finally {
                        if (state.gesture.mode != DockMode.Idle) {
                            // A carried ring's card closes with the carry.
                            if (state.carrying >= 0) state.closeSwell()
                            state.gesture.cancel()
                            state.pressRing = -1
                            state.carrying = -1
                            state.moving = false
                            state.stopCharge()
                        }
                    }
                }
            },
    )
}

/**
 * The dock's area sharing touches with the screen's other layers (the page under it): attached only while the dock is
 * tucked or shows an alert's look-only swell, so the page sees every touch that starts there as if the dock were not
 * there, and the dock's own band still sees its taps and sideways drags. A touch that is not the dock's closes the
 * alert's swell, without taking the touch.
 */
private data class DockSharesTouches(val state: KitEdgeDockState) : ModifierNodeElement<DockSharesTouchesNode>() {
    override fun create(): DockSharesTouchesNode = DockSharesTouchesNode(state)

    override fun update(node: DockSharesTouchesNode) {
        node.state = state
    }
}

private class DockSharesTouchesNode(var state: KitEdgeDockState) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        // Main runs from the dock's own touch area up to here: a press the dock took has set its gesture by now.
        if (pass != PointerEventPass.Main || pointerEvent.type != PointerEventType.Press) return
        if (state.swellLookOnly && state.swell != null && state.gesture.mode == DockMode.Idle) state.closeSwell()
    }

    override fun onCancelPointerInput() = Unit

    override fun sharePointerInputWithSiblings(): Boolean = true
}
