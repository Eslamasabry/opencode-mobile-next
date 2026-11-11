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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.eslamasabry.oc.ui.kit.agent.AgentMark
import io.github.eslamasabry.oc.ui.kit.agent.agentMonogram
import io.github.eslamasabry.oc.ui.kit.choice.rememberKitHaptics
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.breath
import io.github.eslamasabry.oc.ui.kit.motion.lap
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
 * Motion: springs in the draw and layout phases only; reduced motion turns travel into instant changes and looks into
 * short cross-fades, with no shake, pulse, comet or overshoot. Text: frame (capped at 1.3 x).
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
    // Nothing live: no dock (the host shows none either).
    if (model.rings.isEmpty()) return
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val motion = LocalKitMotion.current
    val colors = LocalKitColors.current
    val face = rememberDockFace()
    val inks = remember(colors, face.ink, face.ink2) { DockInks.from(colors, face.ink, face.ink2) }
    val haptics = rememberKitHaptics()
    val scope = rememberCoroutineScope()
    val textScale = kitTextScale().coerceIn(1f, TextScalePolicy.FRAME_CAP)
    BoxWithConstraints(modifier.fillMaxSize().semantics { isTraversalGroup = true }.testTag("kit-edge-dock")) {
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
        val column = DockColumn.of(model.rings.size, pinned, safeBottom - safeTop, reordering = state.carrying >= 0)
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
        state.panelContent = panelContentHeight(model.rings, model.hidden, state.hiddenOpen, state.expanded.value, textScale)

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
                state.closeSwell()
                if (state.panelOpen) state.closePanel()
            }
            state.out.animateTo(to, if (model.tucked) DockSprings.close(motion.reduced) else DockSprings.drop(motion.reduced))
        }
        // The model moved on under an open swell: close it rather than show another ring's words.
        LaunchedEffect(model.rings.size) {
            if (state.swellRing >= model.rings.size) state.closeSwell()
            state.scroll.snapTo(state.scroll.value.coerceIn(0f, column.maxScroll))
        }
        // Keep the conversation on screen in view.
        val currentIndex = model.rings.indexOfFirst { it.key == model.current }
        LaunchedEffect(currentIndex, column) {
            if (currentIndex >= 0) state.scroll.animateTo(column.scrollToShow(currentIndex, state.scroll.value), DockSprings.fill(motion.reduced))
        }

        DockBodyNode(state, face, inks)
        model.rings.forEachIndexed { i, ring ->
            androidx.compose.runtime.key(ring.key) {
                DockRingNode(state, ring, i, ring.key == model.current, inks, words, intents)
            }
        }
        DockFootNode(state, words, intents)
        DockHitNode(state)
        val swell = state.swell
        if (swell != null && state.swellRing in model.rings.indices) {
            DockSwellContent(state, swell, model.rings[state.swellRing], words, inks)
        }
        if (state.panelShown) DockPanel(state, model, words, inks, intents)
        KitBackGuard(enabled = state.anythingOpen) {
            when {
                state.renaming != null -> state.endRename(save = false, text = "")
                state.panelOpen -> state.closePanel()
                else -> state.closeSwell()
            }
        }
    }
}

/** The body: the face clipped to the outline, then the failure pulse and the marks (grip, pin line). */
@Composable
private fun DockBodyNode(state: KitEdgeDockState, face: DockFace, inks: DockInks) {
    val path = remember { Path() }
    val pulsePath = remember { Path() }
    Box(
        Modifier.fillMaxSize()
            .drawWithContent {
                if (state.column.count == 0) return@drawWithContent
                val f = state.computeFrame()
                val o = state.outline
                buildOutline(o, f.aLeft, f.aTop, f.aBottom, f.aRadius, f.hasB, f.bLeft, f.bTop, f.bBottom, f.bRadius, f.grow, f.fillet, f.edgeX, state.shoulders)
                val d = density
                val pt = state.pulse.value
                val pulsing = pt < 1f && state.pulseRing >= 0 && !state.reduced
                if (pulsing) {
                    val start = o.indexNearY(state.slotY(state.pulseRing), f.aLeft + 2f)
                    pulseOffsets(o, start, pt * 900f, 3.4f * kotlin.math.exp(-pt * 3.3f), state.pulseDx, state.pulseDy)
                }
                fun px(i: Int): Float = state.phys(o.x[i] + (if (pulsing) state.pulseDx[i] else 0f)) * d
                fun py(i: Int): Float = (o.y[i] + (if (pulsing) state.pulseDy[i] else 0f)) * d
                val beyond = state.phys(f.edgeX + DockDims.OVERHANG) * d
                path.rewind()
                path.moveTo(beyond, py(0))
                for (i in 0 until o.n) path.lineTo(px(i), py(i))
                path.lineTo(beyond, py(o.n - 1))
                path.close()
                clipPath(path) { this@drawWithContent.drawContent() }
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
                // Tucked: the rings shrink into dots of their state colour along the nub.
                val out = state.out.value
                val vis = smoothstep(15f, 58f, out)
                if (vis < 1f) {
                    val nx = state.phys(f.edgeX - max(out, DockDims.NUB_W) / 2f) * d
                    state.rings.forEachIndexed { i, r ->
                        val cy = state.slotY(i) - DockDims.RING_LIFT
                        drawCircle(inks.of(r.state), 3f * d, Offset(nx, cy * d), alpha = 1f - vis)
                    }
                }
                // The grip at the dock's head: a short bar (a mark, not a surface).
                if (out > DockDims.REST_W * 0.5f && !f.panel) {
                    val cx = state.phys(f.aLeft + (out + state.lean.value) / 2f) * d
                    val gy = (state.top.value + DockDims.HEAD / 2f + 2f) * d
                    drawLine(inks.ink2, Offset(cx - 8f * d, gy), Offset(cx + 8f * d, gy), 3f * d, StrokeCap.Round, alpha = 0.35f)
                    val col = state.column
                    if (col.pinGap > 0f) {
                        val ly = col.pinLineY(state.top.value, state.scroll.value) * d
                        drawLine(inks.ink2, Offset(cx - 14f * d, ly), Offset(cx + 14f * d, ly), 1.5f * d, StrokeCap.Round, alpha = 0.5f)
                    }
                }
            }
            .then(face.modifier),
    )
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
        if (grew && !reduced) launch { m.pop.animateTo(0f, DockSprings.kick(), initialVelocity = 2.4f) }
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
            m.settle.animateTo(1f, DockSprings.look())
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(200)
        m.check.animateTo(1f, DockSprings.timed(340))
        launch { m.pop.animateTo(0f, DockSprings.kick(), initialVelocity = 3f) }
        kotlinx.coroutines.delay(2100)
        m.settle.animateTo(1f, DockSprings.timed(400))
    }
    // An alert: a request pops twice and leans the dock out; a failure flashes, shakes once and pulses the body.
    LaunchedEffect(ring.pulse) {
        val rose = m.lastPulse >= 0 && ring.pulse > m.lastPulse
        m.lastPulse = ring.pulse
        if (!rose) return@LaunchedEffect
        val failed = ring.state == KitDockRingState.Failed
        state.alert(index, failed)
        if (reduced) return@LaunchedEffect
        if (failed) {
            launch {
                m.flash.snapTo(1f)
                m.flash.animateTo(0f, DockSprings.timed(220))
            }
            m.shake.animateTo(0f, DockSprings.shake(), initialVelocity = 560f)
        } else {
            m.pop.animateTo(0f, DockSprings.pop(), initialVelocity = 3f)
            kotlinx.coroutines.delay(360)
            m.pop.animateTo(0f, DockSprings.pop(), initialVelocity = 3f)
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
    Box(
        Modifier
            .absoluteOffset {
                val p = ringCenter(state, index, m)
                IntOffset(((p.x - DockDims.REST_W / 2f) * density).roundToInt(), ((p.y - state.column.pitch / 2f) * density).roundToInt())
            }
            .size(DockDims.REST_W.dp, state.column.pitch.dp)
            .graphicsLayer {
                val vis = smoothstep(15f, 58f, state.out.value) * (1f - smoothstep(0.1f, 0.45f, state.panelX.value))
                alpha = vis * m.presence.value.coerceIn(0f, 1f)
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
            Modifier.align(Alignment.Center).absoluteOffset(y = 21.dp).widthIn(max = 64.dp).graphicsLayer {
                alpha = 1f - state.ride.value.coerceIn(0f, 1f) * (if (state.renaming == ring.key) 1f else 0f)
            },
        ) {
            KitText(ring.name, role = TypeRole.Caption, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = 1)
        }
    }
}

/** A ring's centre (edge frame, dp, converted to physical x): its slot, the lean, the ride out for rename, the carry. */
private fun ringCenter(state: KitEdgeDockState, index: Int, m: RingMotion): Offset {
    val o = max(state.out.value, DockDims.NUB_W) + state.lean.value
    val baseX = state.widthDp - state.edgeInset - o / 2f
    var y = state.column.slotCenterAt(m.slot.value.coerceAtLeast(0f), state.top.value, state.scroll.value)
    var x = baseX
    if (state.carrying == index) y = state.carryY
    val ride = state.ride.value
    if (ride > 0f && state.swellRing == index && state.renaming == state.rings.getOrNull(index)?.key) {
        val f = state.computeFrame()
        val rideX = (state.swell?.rideX ?: 0f)
        x = lerp(baseX, f.bLeft + rideX, ride)
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

/** The round new-conversation button at the dock's foot: part of the same body, a 48 dp target. */
@Composable
private fun DockFootNode(state: KitEdgeDockState, words: KitDockWords, intents: KitDockIntents) {
    val colors = LocalKitColors.current
    Box(
        Modifier
            .absoluteOffset {
                val o = max(state.out.value, DockDims.NUB_W) + state.lean.value
                val cx = state.phys(state.widthDp - state.edgeInset - o / 2f)
                val cy = state.top.value + DockDims.HEAD + state.column.viewportHeight + DockDims.FOOT / 2f
                IntOffset(((cx - 24f) * density).roundToInt(), ((cy - 24f) * density).roundToInt())
            }
            .size(48.dp)
            .graphicsLayer { alpha = smoothstep(15f, 58f, state.out.value) * (1f - smoothstep(0.1f, 0.45f, state.panelX.value)) }
            .semantics {
                contentDescription = words.newConversation
                role = Role.Button
                onClick(words.newConversation) {
                    intents.onNewConversation()
                    true
                }
            }
            .drawBehind { drawCircle(colors.text1, 20f * density, alpha = 0.14f, style = Stroke(3f * density)) },
        contentAlignment = Alignment.Center,
    ) {
        KitIcon(KitIcons.add, size = KitIconSize.Small, tone = TextTone.Primary)
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
                val full = state.anythingOpen || state.gesture.mode != DockMode.Idle
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
                            state.onOutcome(DockOutcome.Close)
                        }
                        return@awaitEachGesture
                    }
                    down.consume()
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
                                } else -1L
                            val event =
                                if (wait > 0) withTimeoutOrNull(wait) { awaitPointerEvent() } else awaitPointerEvent()
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
                            change.consume()
                            state.computeFrame()
                            val fb = state.gesture.move(x, y, now, state.hits)
                            if (!state.gesture.holding) {
                                state.pressRing = -1
                                state.stopCharge()
                            }
                            state.onFeedback(fb)
                            state.onMove(x, y, x0)
                        }
                    } finally {
                        if (state.gesture.mode != DockMode.Idle) {
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
