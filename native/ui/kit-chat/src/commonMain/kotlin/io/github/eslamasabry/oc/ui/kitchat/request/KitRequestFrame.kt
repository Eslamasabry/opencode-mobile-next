package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.background
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitDressedLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitNested
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYou
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouReason
import io.github.eslamasabry.oc.ui.kit.style.KitSignalNeedsYou
import io.github.eslamasabry.oc.ui.kit.style.KitSignalSlip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouWaiting
import io.github.eslamasabry.oc.ui.words.res.kitRequestNeedsYouLabel
import org.jetbrains.compose.resources.stringResource

/**
 * How a waiting request card is drawn (Flutter `KitRequestLook`, lib/ui/kit/kit_request_card.dart part
 * kit_request_question.dart:3-16).
 */
public enum class KitRequestLook {
    /** The needs-you card every list and sheet uses: the attention surface and line all round. */
    Standard,

    /**
     * The conversation's question: a calm `surface1` card under the attention line at half strength, with a 4 dp
     * attention edge at its start; "● Needs you · 3 s" inside it, the ask at the title size, what it will run in one
     * bordered mono block with Details in it, Reject and the primary, and the named standing grant as an underlined link.
     */
    Question,
}

/**
 * One bounded request surface for permissions and questions, with answers always outside its
 * scrolling words. [look] picks the needs-you frame ([KitRequestLook]).
 */
@Composable
public fun KitRequestFrame(
    modifier: Modifier = Modifier,
    look: KitRequestLook = KitRequestLook.Standard,
    answers: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Card.toShape()
    val question = look == KitRequestLook.Question
    // Brutal and Paper dress the card as a card on the page (audit 2026-11-14): Brutal's ink edge and hard shadow,
    // Paper's ink box with square corners; the attention stays in its fill (standard) or its start edge (question). The
    // controls inside are nested in its outline (LocalKitNested): edged, never a second shadow.
    val dressed =
        kitDressedLook(
            KitSurfaceInput(
                role = SurfaceRole.Content,
                shape = KitShapeId.Card,
                level = SurfaceLevel.Surface2,
                color = if (question) colors.surface1 else colors.attentionSurface.compositeOver(colors.surface1),
            )
        )
    val face =
        if (LocalKitAppearance.current.style == KitSurfaceStyle.Signal) Modifier
        else if (dressed != null) Modifier.kitSurface(dressed).then(if (question) Modifier.questionEdge().questionPadding() else Modifier.padding(Space.S4.dp))
        else if (question) Modifier.questionFace(shape)
        else
            Modifier.clip(shape)
                .background(colors.surface1)
                .background(colors.attentionSurface)
                .border(kitHairline(), colors.attentionLine, shape)
                .padding(Space.S4.dp)
    // Audit 2026-11-14 (c20, Glass and Solid): Flutter's 4 dp ring of the attention colour at 6 % around the waiting card
    // (kit_request_shape.dart:217-238) read as a second pane round the card, on another radius: a box in a box. The
    // standard card is one pane with one outline, its attention line and face; it fills its slot as the message box
    // under it does. The question look and Signal keep their place as before.
    val ringWidth = FlutterKitTokens.NEEDS_YOU_RING_WIDTH.dp
    val ring =
        if (question || LocalKitAppearance.current.style == KitSurfaceStyle.Signal) Modifier.padding(horizontal = ringWidth)
        else Modifier
    KitSignalSlip(
        modifier.widthIn(max = 700.dp).fillMaxWidth().heightIn(max = maxCardHeight()),
        endsWithTap = true,
    ) {
        Column(
            Modifier.fillMaxWidth().then(ring).testTag("kit-request-face").then(face),
            // Question look: the answers sit space4 under the words (kit_request_question.dart:183-186).
            verticalArrangement = Arrangement.spacedBy(if (question) Space.S4.dp else Space.S3.dp),
        ) {
        CompositionLocalProvider(LocalKitNested provides (dressed != null)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                // The question look spaces its own lines (tag 8, title 4, block 16); the standard card keeps 8.
                verticalArrangement = if (question) Arrangement.Top else Arrangement.spacedBy(Space.S2.dp),
                content = content,
            )
            answers()
        }
        }
    }
}

/**
 * The question look's face (kit_request_shape.dart:54-200 with `edge`): `surface1`, the attention colour at half
 * strength as a hairline border, and a [FlutterKitTokens.NEEDS_YOU_EDGE_WIDTH] attention edge along the start side,
 * inside the corners; the words start that much further in.
 */
@Composable
private fun Modifier.questionFace(shape: Shape): Modifier {
    val colors = LocalKitColors.current
    return clip(shape)
        .background(colors.surface1)
        .questionEdge()
        .border(kitHairline(), colors.attention.copy(alpha = FlutterKitTokens.NEEDS_YOU_LINE_ALPHA), shape)
        .questionPadding()
}

/** The question look's [FlutterKitTokens.NEEDS_YOU_EDGE_WIDTH] attention edge along the start side. */
@Composable
private fun Modifier.questionEdge(): Modifier {
    val attention = LocalKitColors.current.attention
    val edge = FlutterKitTokens.NEEDS_YOU_EDGE_WIDTH.dp
    return drawBehind {
        val w = edge.toPx()
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
        drawRect(attention, topLeft = Offset(x, 0f), size = Size(w, size.height))
    }
}

/** The question look's padding: its words start the attention edge's width further in. */
private fun Modifier.questionPadding(): Modifier =
    padding(start = Space.S4.dp + FlutterKitTokens.NEEDS_YOU_EDGE_WIDTH.dp, top = Space.S4.dp, end = Space.S4.dp, bottom = Space.S4.dp)

/**
 * "● Needs you · 3 s" (kit_request_question.dart:201-245): the 8 dp attention dot and the words in the label role and
 * attention ink while the request waits, with [age] (the short age words, counting seconds for the first minute) and
 * [more] ("1 more request is waiting") after it. Once it no longer waits, no dot and the secondary ink. Under Signal the
 * printed needs-you mark says it.
 */
@Composable
public fun KitRequestNeedsYouTag(
    age: String?,
    modifier: Modifier = Modifier,
    waiting: Boolean = true,
    more: String? = null,
) {
    val label = stringResource(Res.string.kitRequestNeedsYouLabel)
    val text = listOfNotNull(label, age, more?.trimEnd('.', '。')).joinToString(" · ")
    if (LocalKitAppearance.current.style == KitSurfaceStyle.Signal) {
        KitSignalNeedsYou(text, waiting = waiting, modifier = modifier)
        return
    }
    val colors = LocalKitColors.current
    Row(
        modifier.testTag("kit-request-needs-you"),
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (waiting) {
            Box(
                Modifier.size(FlutterKitTokens.NEEDS_YOU_DOT_SIZE.dp)
                    .clip(CircleShape)
                    .background(colors.attention)
                    .testTag("kit-request-needs-you-dot")
            )
        }
        KitText(
            text,
            role = TypeRole.Label,
            tone = if (waiting) TextTone.Attention else TextTone.Secondary,
            tabular = true,
        )
    }
}

/** The visible waiting caption follows the current style without changing its spoken words. */
@Composable
public fun KitRequestWaitingCaption(waiting: String?, modifier: Modifier = Modifier) {
    if (LocalKitAppearance.current.style == KitSurfaceStyle.Signal) {
        val label = stringResource(Res.string.kitRequestNeedsYouLabel)
        val age = waiting?.let { stringResource(Res.string.kitNeedsYouWaiting, it) }
        KitSignalNeedsYou(if (age == null) label else "$label · $age", modifier = modifier)
    } else {
        KitNeedsYou(KitNeedsYouReason.Decision, modifier = modifier, waiting = waiting)
    }
}

/**
 * The collapsed answered row (kit_request_card.dart `_collapsed`): no card, border or attention colour. The request's glyph
 * at the start, the ask in small secondary words, and the [outcome] (the receipt, then any Undo) under it.
 */
@Composable
public fun KitRequestAnsweredRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    outcome: @Composable () -> Unit,
) {
    // kitRequestPlacement: the page gutter less the 4 dp ring across (12 dp), space1 (4 dp) above and below.
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.S3.dp, vertical = Space.S1.dp)
            .heightIn(min = LocalKitTokens.current.rowHeightOne.dp).padding(horizontal = Space.S4.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitIcon(icon, size = KitIconSize.Small, tone = TextTone.Secondary)
        Column(Modifier.weight(1f)) {
            KitText(title, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Reading)
            outcome()
        }
    }
}

/**
 * The collapsed answered row whose [outcome] changes in place (M-005): "Allowed · Undo" → "Sending…" → "Not confirmed
 * yet" cross-fade through the kit's [KitSwap] (QUICK; instant with reduced motion) instead of swapping in one frame.
 * [outcomeKey] says when the outcome is a different one; the leaving outcome is drawn from its own key, ignores touches
 * (a late Undo tap never lands) and is hidden from TalkBack while it fades.
 */
@Composable
public fun <T> KitRequestAnsweredRow(
    icon: ImageVector,
    title: String,
    outcomeKey: T,
    modifier: Modifier = Modifier,
    outcome: @Composable (T) -> Unit,
) {
    // Named, without outcomeKey: only the plain row fits (this overload needs a key).
    KitRequestAnsweredRow(
        icon = icon,
        title = title,
        modifier = modifier,
        outcome = {
            KitSwap(outcomeKey, Modifier.fillMaxWidth(), pace = KitPace.Quick, alignment = Alignment.CenterStart) { shown ->
                outcome(shown)
            }
        },
    )
}
