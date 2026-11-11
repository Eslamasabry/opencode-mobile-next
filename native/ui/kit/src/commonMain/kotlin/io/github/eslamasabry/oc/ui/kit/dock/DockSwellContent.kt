package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrameText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.math.roundToInt

/**
 * What box B holds: the ring's name over its state, its sub-agents as mini rings with legible names, the peek's lines,
 * the action targets, or the rename field. Laid out on B's full-grown box (layout phase only), shown once the body has
 * reached it (alpha follows B's growth, and the words are cut at B's tip as it is now, the very edge the body has). The
 * words lie on the dock's own body: no pane of their own.
 */
@Composable
internal fun DockSwellContent(state: KitEdgeDockState, layout: SwellLayout, ring: KitDockRing, words: KitDockWords, inks: DockInks) {
    // The box reaches from B's tip to the screen edge (the rename field lies over the strip).
    val boxW = layout.length + DockDims.REST_W
    val ringIndex = state.swellRing
    Box(
        Modifier
            .absoluteOffset {
                val f = state.computeFrame()
                val left = if (state.onRight) f.bRestLeft else state.widthDp - (f.bRestLeft + boxW)
                IntOffset((left * density).roundToInt(), (f.bTop * density).roundToInt())
            }
            .size(boxW.dp, layout.height.dp)
            .graphicsLayer { alpha = smoothstep(0.62f, 0.92f, state.swellGrow.value) }
            .drawWithContent {
                // Words never stand outside the body: cut them at B's tip now (plus a margin that closes as it grows).
                val f = state.computeFrame()
                val g = f.grow.coerceIn(0f, 1f)
                val cut = ((f.bLeft - f.bRestLeft).coerceAtLeast(0f) + 5f * (1f - g)) * density
                if (state.onRight) clipRect(left = cut) { this@drawWithContent.drawContent() }
                else clipRect(right = size.width - cut) { this@drawWithContent.drawContent() }
            },
    ) {
        if (layout.kind == SwellKind.Rename) {
            RenameField(state, layout, ring, words, boxW)
            return@Box
        }
        val hot = state.target
        val order = remember(ring.children) { childOrder(ring.children) }
        for (t in layout.targets) {
            val r = t.rect
            val x = if (state.onRight) r.left else boxW - r.right
            Box(
                Modifier
                    .absoluteOffset(x.dp, r.top.dp)
                    .size(r.width.dp, r.height.dp)
                    .then(if (hot == t) hotFace() else Modifier)
                    .semantics {
                        role = Role.Button
                        selected = hot == t
                        contentDescription = targetLabel(t, ring, order, words)
                        onClick {
                            state.runTarget(ringIndex, t.kind, t.child)
                            true
                        }
                    },
                contentAlignment = if (t.kind == DockTargetKind.Header) Alignment.CenterStart else Alignment.Center,
            ) {
                when (t.kind) {
                    DockTargetKind.Header -> Header(ring, words, layout.kind == SwellKind.Peek)
                    DockTargetKind.Child -> order.getOrNull(t.child)?.let { MiniCell(ring.children[it], inks, hot == t) }
                    DockTargetKind.More -> KitText("+${t.child}", role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
                    else -> ActionCell(t.kind, words)
                }
            }
        }
        val lines = layout.lines
        if (lines != null) {
            val x = if (state.onRight) lines.left else boxW - lines.right
            Column(Modifier.absoluteOffset(x.dp, lines.top.dp).size(lines.width.dp, lines.height.dp)) {
                for (line in ring.peek.take(DockDims.PEEK_LINES)) {
                    KitText(
                        line.text,
                        role = if (line.role == KitDockLineRole.Tool) TypeRole.Caption else TypeRole.Secondary,
                        tone = if (line.role == KitDockLineRole.Agent) TextTone.Primary else TextTone.Secondary,
                        kind = KitTextKind.Frame,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The hovered target's light: the kit's state layer, not a pane of this part's own. */
@Composable
private fun hotFace(): Modifier {
    val colors = LocalKitColors.current
    return kitSurfaceLook(
        KitSurfaceInput(role = SurfaceRole.StateLayer, shape = KitShapeId.Tile, ownFill = colors.text1.copy(alpha = 0.10f), pressed = true),
    ).modifier
}

private fun targetLabel(t: DockTarget, ring: KitDockRing, order: List<Int>, words: KitDockWords): String =
    when (t.kind) {
        DockTargetKind.Header -> words.actionOn(words.open, ring.name)
        DockTargetKind.Child -> order.getOrNull(t.child)?.let { ring.children[it] }?.let { words.actionOn(words.open, it.name) + ", " + words.stateWord(it.state) } ?: ""
        DockTargetKind.More -> words.subAgents(ring.childTotal)
        else -> words.actionOn(words.targetWord(t.kind), ring.name)
    }

/** Name over state (and the server, when more than one is live). */
@Composable
private fun Header(ring: KitDockRing, words: KitDockWords, peek: Boolean) {
    val alert = ring.state == KitDockRingState.NeedsYou || ring.state == KitDockRingState.Failed
    Column(Modifier.fillMaxWidth()) {
        KitText(ring.name, role = if (peek) TypeRole.Headline else TypeRole.RowTitle, kind = KitTextKind.Frame, maxLines = 1)
        KitText(
            words.stateLine(ring),
            role = TypeRole.Caption,
            tone = if (alert) TextTone.Attention else TextTone.Secondary,
            kind = KitTextKind.Frame,
            maxLines = if (ring.server != null) 2 else 1,
        )
    }
}

/** A sub-agent: its mini ring (the same drawing, motion calmed) over a name that may wrap to two short lines. */
@Composable
private fun MiniCell(child: KitDockChild, inks: DockInks, hot: Boolean) {
    Column(Modifier.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(24.dp)) {
            val tasks = child.state as? KitDockRingState.WorkingWithTasks
            drawDockRing(
                Offset(size.width / 2f, size.height / 2f), 9f * density, 2.4f * density, child.state, inks,
                arc = tasks?.let { if (it.total > 0) it.done.toFloat() / it.total else 0f } ?: 1f,
                check = 1f, settle = 0f, spin = -60f, breath = 0.6f, flash = 0f, mini = true,
            )
        }
        KitText(
            child.name,
            role = TypeRole.Caption,
            tone = if (hot) TextTone.Primary else TextTone.Secondary,
            kind = KitTextKind.Frame,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/** An action target: its glyph over its word. At least 48 dp each way. */
@Composable
private fun ActionCell(kind: DockTargetKind, words: KitDockWords) {
    val icon =
        when (kind) {
            DockTargetKind.Open -> KitIcons.launch
            DockTargetKind.Allow -> KitIcons.check
            DockTargetKind.Deny -> KitIcons.close
            DockTargetKind.Stop -> KitIcons.stop
            DockTargetKind.Rename -> KitIcons.edit
            else -> KitIcons.removeCircle
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KitIcon(icon, size = KitIconSize.Small, tone = if (kind == DockTargetKind.Remove) TextTone.Secondary else TextTone.Primary)
        KitText(words.targetWord(kind), role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
    }
}

/** The nickname field, right of the ridden-out ring, over the strip, under the thumb. Enter or a tap away saves. */
@Composable
private fun RenameField(state: KitEdgeDockState, layout: SwellLayout, ring: KitDockRing, words: KitDockWords, boxW: Float) {
    val spot = layout.field ?: return
    val colors = LocalKitColors.current
    val style = LocalKitTypography.current.style(TypeRole.RowTitle).copy(color = colors.text1)
    val field = rememberTextFieldState(ring.name, TextRange(0, ring.name.length))
    val focus = remember { FocusRequester() }
    LaunchedEffect(ring.key) {
        focus.requestFocus()
        snapshotFlow { field.text.toString() }.collect { state.renameText = it }
    }
    val x = if (state.onRight) spot.left else boxW - spot.right
    KitFrameText {
        BasicTextField(
            state = field,
            modifier =
                Modifier
                    .absoluteOffset(x.dp, spot.top.dp)
                    .size(spot.width.dp, spot.height.dp)
                    .graphicsLayer { alpha = ((state.ride.value - 0.55f) / 0.35f).coerceIn(0f, 1f) }
                    .focusRequester(focus)
                    .semantics { contentDescription = words.nickname },
            textStyle = style,
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            onKeyboardAction = { state.endRename(save = true, text = field.text.toString()) },
        )
    }
}
