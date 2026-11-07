package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouMark
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import io.github.eslamasabry.oc.ui.kit.state.KitStepMark
import io.github.eslamasabry.oc.ui.kit.state.kitNeedsYouSpan
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMarkDone
import io.github.eslamasabry.oc.ui.words.res.kitMarkFailed
import io.github.eslamasabry.oc.ui.words.res.kitMarkPaused
import io.github.eslamasabry.oc.ui.words.res.kitMarkWaiting
import io.github.eslamasabry.oc.ui.words.res.kitMarkWorking
import io.github.eslamasabry.oc.ui.words.res.kitTaskNeedsYou
import io.github.eslamasabry.oc.ui.words.res.kitTaskStopped
import org.jetbrains.compose.resources.stringResource

/** Where a task stands (Dart `KitTaskState`): the four step states, a task that waits on the person, one stopped early. */
public enum class KitTaskState { Waiting, Working, Done, Failed, NeedsYou, Stopped }

/** A task's priority (Dart `KitPriority`; Linear's five levels). */
public enum class KitPriority { Urgent, High, Normal, Low, Someday }

/** The kind of a card's one flag line (Dart `KitTaskFlagKind`). In-flight writes are a receipt, not a flag. */
public enum class KitTaskFlagKind { NeedsYou, Blocked, Failed, Stopped, Info }

/** The card's one flag line (Dart `KitTaskFlag`): [label] is the whole line from the host; [icon] for [KitTaskFlagKind.Info] only. */
public data class KitTaskFlag(val kind: KitTaskFlagKind, val label: String, val icon: ImageVector? = null)

/** One piece of the meta line (Dart `KitTaskMeta`), joined with " · "; [strong] is text1 semibold (urgent and high). */
public data class KitTaskMeta(
    val label: String,
    val icon: ImageVector? = null,
    val priority: KitPriority? = null,
    val strong: Boolean = false,
)

/** Pure rules of [KitTaskCard] and [KitPriorityGlyph] (Dart `KitTaskCard`, `_KitPriorityPainter`). */
public object KitTaskCardRules {
    /** Done and stopped tasks rest: their title is muted. */
    public fun resting(mark: KitTaskState): Boolean = mark == KitTaskState.Done || mark == KitTaskState.Stopped

    /** A receipt that is sending or sent moves the task; its action waits. */
    public fun moving(receipt: KitReceiptState?): Boolean =
        receipt == KitReceiptState.Sending || receipt == KitReceiptState.Sent

    /** Title lines: 2, 3 from 1.3× text. */
    public fun titleLines(scale: Float): Int = if (scale >= 1.3f) 3 else 2 // F: lib/ui/kit/kit_task_card.dart:259

    /** Lit priority bars of three. */
    public fun litBars(priority: KitPriority): Int =
        when (priority) {
            KitPriority.Urgent, KitPriority.High -> 3 // F: lib/ui/kit/kit_task_card.dart:502
            KitPriority.Normal -> 2 // F: lib/ui/kit/kit_task_card.dart:503
            KitPriority.Low -> 1 // F: lib/ui/kit/kit_task_card.dart:504
            KitPriority.Someday -> 0
        }

    /** The leading mark already draws the error or stop glyph, so the flag line is words alone. */
    public fun markDrawsFlagGlyph(kind: KitTaskFlagKind, mark: KitTaskState): Boolean =
        when (kind) {
            KitTaskFlagKind.Failed -> mark == KitTaskState.Failed
            KitTaskFlagKind.Stopped -> mark == KitTaskState.Stopped
            else -> false
        }
}

/** The word of a task state (Dart `KitTaskMark.wordFor`). */
@Composable
public fun kitTaskWord(state: KitTaskState, paused: Boolean = false): String =
    if (paused) stringResource(Res.string.kitMarkPaused)
    else stringResource(
        when (state) {
            KitTaskState.Waiting -> Res.string.kitMarkWaiting
            KitTaskState.Working -> Res.string.kitMarkWorking
            KitTaskState.Done -> Res.string.kitMarkDone
            KitTaskState.Failed -> Res.string.kitMarkFailed
            KitTaskState.NeedsYou -> Res.string.kitTaskNeedsYou
            KitTaskState.Stopped -> Res.string.kitTaskStopped
        },
    )

/**
 * One task on the AI Team board (Dart `KitTaskCard`): its mark, its title in the person's words, one muted meta line and
 * at most one flag line, or a receipt while a move is in flight. A tap opens the task's conversation. The card never
 * answers, never moves a task and never shows "Done" without the host's echo (STATE-10). ONE trailing icon [action]
 * (KIT-27); it waits while the [receipt] is sending or sent.
 *
 * States: default, needs you, blocked, failed, stopped, done, moving (receipt), read-only
 * Text: reading
 * Cap: Lane
 * Motion: the mark cross-fades on QUICK; Off = instant
 * Replaces: KitTaskCard (lib/ui/kit/kit_task_card.dart)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitTaskCard(
    title: String,
    mark: KitTaskState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    meta: List<KitTaskMeta> = emptyList(),
    flag: KitTaskFlag? = null,
    receipt: KitReceiptState? = null,
    onReceiptRetry: (() -> Unit)? = null,
    action: KitAction? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val word = kitTaskWord(mark, paused)
    val shownFlag = if (receipt == null) flag else null
    val label = listOfNotNull(
        title,
        word,
        meta.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.label },
        shownFlag?.label?.takeIf { it.isNotBlank() },
    ).joinToString(". ")
    val endPad = if (action == null) Space.S3 else Space.S2 + LocalKitTokens.current.minTarget // F: lib/ui/kit/kit_task_card.dart:236
    KitSurface(
        modifier.kitMaxWidth(CapRole.Lane).fillMaxWidth().testTag("kit-task-card"),
        padding = KitSurfacePadding.None,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                KitTappable(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                    onLongClick = onLongPress,
                    shape = KitShapeId.Panel,
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(
                                start = Space.S2.dp,
                                top = Space.S3.dp,
                                end = endPad.dp,
                                bottom = if (receipt == null) Space.S3.dp else 0.dp,
                            )
                            .clearAndSetSemantics { contentDescription = label },
                        verticalAlignment = Alignment.Top,
                    ) {
                        // The mark centres on the title's first line (align gate `icon-label`); the title keeps
                        // Flutter's space1 drop, so only the mark moves.
                        val line = with(LocalDensity.current) { LocalKitTypography.current.style(TypeRole.RowTitle).lineHeight.toDp().value }
                        val slot = FlutterKitTokens.MARK_SLOT_SIZE.toFloat()
                        // A dot or ring (working, waiting) keeps Flutter's place. A glyph sits Flutter's 1 dp under the
                        // title's first-line centre, which the align gate refuses: it centres on that line.
                        val glyph = paused || mark !in setOf(KitTaskState.Working, KitTaskState.Waiting)
                        val lift = if (glyph) (Space.S1 + (line - slot) / 2).dp else 0.dp // NV: the first-line offset
                        Box(Modifier.offset(y = lift)) { TaskMark(mark, paused) }
                        Spacer(Modifier.width(Space.S2.dp))
                        Column(Modifier.weight(1f).padding(top = Space.S1.dp)) {
                            KitText(
                                title,
                                Modifier.testTag("kit-task-card-title"),
                                role = TypeRole.RowTitle,
                                tone = if (KitTaskCardRules.resting(mark)) TextTone.Secondary else TextTone.Primary,
                                maxLines = KitTaskCardRules.titleLines(kitTextScale()),
                            )
                            if (meta.isNotEmpty()) {
                                MetaLine(meta, Modifier.padding(top = Space.S1.dp).testTag("kit-task-card-meta"))
                            }
                            shownFlag?.let {
                                Box(Modifier.testTag("kit-task-card-flag")) { FlagLine(it, mark) }
                            }
                        }
                    }
                }
                if (action != null) {
                    val disabled = KitTaskCardRules.moving(receipt) || !action.runnable
                    KitIconButton(
                        icon = action.icon ?: KitIcons.more,
                        label = action.label,
                        onClick = { action.onClick?.invoke() },
                        modifier = Modifier.align(Alignment.TopEnd)
                            .padding(top = Space.S2.dp, end = Space.S2.dp)
                            .testTag("kit-task-card-action"),
                        enabled = if (disabled) KitEnabled.Disabled(action.enabled.reason ?: action.label) else KitEnabled.Enabled,
                        size = 20, // F: lib/ui/kit/kit_task_card.dart:309
                    )
                }
            }
            if (receipt != null) {
                Box(
                    Modifier.fillMaxWidth().padding(
                        start = (Space.S2 + FlutterKitTokens.MARK_SLOT_SIZE + Space.S2).dp, // F: lib/ui/kit/kit_task_card.dart:218
                        top = Space.S2.dp,
                        end = Space.S3.dp,
                        bottom = Space.S3.dp,
                    ),
                ) { KitReceipt(receipt, onRetry = onReceiptRetry) }
            }
        }
    }
}

/** The card's leading mark (Dart `KitTaskMark` in the 32 dp slot): the step mark, needs-you's question, or a muted stop. */
@Composable
private fun TaskMark(state: KitTaskState, paused: Boolean) {
    if (state == KitTaskState.NeedsYou) {
        KitNeedsYouMark()
        return
    }
    Box(Modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Crossfade(state to paused, animationSpec = KitMotion.specs.quick(), label = "task-mark") { (shown, held) ->
            when {
                shown == KitTaskState.Stopped ->
                    KitIcon(KitIcons.stopCircle, size = KitIconSize.Small, tone = TextTone.Secondary, growsWithText = true)
                held -> KitIcon(KitIcons.pause, size = KitIconSize.Small, tone = TextTone.Secondary, growsWithText = true)
                else -> KitStepMark(
                    when (shown) {
                        KitTaskState.Done -> KitStepState.Done
                        KitTaskState.Failed -> KitStepState.Failed
                        KitTaskState.Working -> KitStepState.Working
                        else -> KitStepState.Waiting
                    },
                )
            }
        }
    }
}

/**
 * The meta line (Dart `Wrap` of pieces): one paragraph when it fits, so the pieces keep their exact text widths (a run
 * per piece would round each up to a whole pixel and wrap earlier than Flutter); otherwise one run per piece, wrapping
 * between pieces and never inside one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaLine(meta: List<KitTaskMeta>, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val style = LocalKitTypography.current.style(TypeRole.Secondary).copy(color = colors.text2)
    val density = LocalDensity.current
    val glyph = with(density) { (KitIconSize.Small.dp + Space.S1).dp.toSp() }
    val gap = with(density) { Space.S1.dp.toSp() }
    val iconHeight = with(density) { KitIconSize.Small.dp.dp.toSp() }
    val text = buildAnnotatedString {
        meta.forEachIndexed { i, piece ->
            if (piece.priority != null || piece.icon != null) appendInlineContent("lead$i", piece.label)
            if (piece.strong) withStyle(SpanStyle(color = colors.text1, fontWeight = FontWeight.W600)) { append(piece.label) } // F: lib/ui/kit/kit_task_card.dart:363
            else append(piece.label)
            if (i != meta.lastIndex) {
                appendInlineContent("gap", " ")
                append("·")
                appendInlineContent("gap", " ")
            }
        }
    }
    val inline = buildMap {
        put("gap", InlineTextContent(Placeholder(gap, iconHeight, PlaceholderVerticalAlign.Center)) {})
        meta.forEachIndexed { i, piece ->
            if (piece.priority != null || piece.icon != null) {
                put("lead$i", InlineTextContent(Placeholder(glyph, iconHeight, PlaceholderVerticalAlign.Center)) {
                    Box(Modifier.padding(end = Space.S1.dp)) {
                        if (piece.priority != null) KitPriorityGlyph(piece.priority)
                        else KitIcon(piece.icon!!, size = KitIconSize.Small, tone = TextTone.Secondary)
                    }
                })
            }
        }
    }
    BoxWithConstraints(modifier) {
        // Laid out as one line first; a line that does not fit falls back to the wrapping runs.
        var wraps by remember(text, constraints.maxWidth) { mutableStateOf(false) }
        if (!wraps) {
            BasicText(
                text, style = style, inlineContent = inline, maxLines = 1, softWrap = false,
                onTextLayout = { if (it.hasVisualOverflow) wraps = true },
            )
        } else {
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                meta.forEachIndexed { i, piece -> MetaPiece(piece, last = i == meta.lastIndex) }
            }
        }
    }
}

/** One meta piece: its glyph, its label and the separator after it, kept whole on its line. */
@Composable
private fun MetaPiece(piece: KitTaskMeta, last: Boolean) {
    val colors = LocalKitColors.current
    val style = LocalKitTypography.current.style(TypeRole.Secondary).copy(color = colors.text2)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val lead: (@Composable () -> Unit)? = when {
            piece.priority != null -> { { KitPriorityGlyph(piece.priority) } }
            piece.icon != null -> { { KitIcon(piece.icon, size = KitIconSize.Small, tone = TextTone.Secondary) } }
            else -> null
        }
        if (lead != null) Box(Modifier.padding(end = Space.S1.dp)) { lead() }
        val text = if (piece.strong) {
            buildAnnotatedString { withStyle(SpanStyle(color = colors.text1, fontWeight = FontWeight.W600)) { append(piece.label) } } // F: lib/ui/kit/kit_task_card.dart:363
        } else AnnotatedString(piece.label)
        BasicText(text, Modifier.weight(1f, fill = false), style = style)
        if (!last) BasicText("·", Modifier.padding(horizontal = Space.S1.dp), style = style)
    }
}

/** The one flag line (Dart `_flagLine`). */
@Composable
private fun FlagLine(flag: KitTaskFlag, mark: KitTaskState) {
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val top = Modifier.padding(top = Space.S2.dp)
    if (flag.kind == KitTaskFlagKind.NeedsYou) {
        val text = flag.label.trim()
        if (text.isEmpty()) {
            KitText(kitTaskWord(KitTaskState.NeedsYou), top, role = TypeRole.Label, tone = TextTone.Attention)
        } else {
            BasicText(
                kitNeedsYouSpan() + AnnotatedString(text),
                top,
                style = typography.style(TypeRole.Secondary).copy(color = colors.text2),
            )
        }
        return
    }
    val (glyph, tone, strong) = when (flag.kind) {
        KitTaskFlagKind.Blocked -> Triple(KitIcons.blocked, TextTone.Primary, true)
        KitTaskFlagKind.Failed -> Triple(KitIcons.error, TextTone.Primary, true)
        KitTaskFlagKind.Stopped -> Triple(KitIcons.stopCircle, TextTone.Secondary, false)
        else -> Triple(flag.icon, TextTone.Secondary, false)
    }
    val style = typography.style(TypeRole.Secondary).copy(
        color = colors.tone(tone),
        fontWeight = if (strong) FontWeight.W600 else typography.style(TypeRole.Secondary).fontWeight, // F: lib/ui/kit/kit_task_card.dart:427
    )
    if (glyph == null || KitTaskCardRules.markDrawsFlagGlyph(flag.kind, mark)) {
        BasicText(flag.label, top, style = style)
        return
    }
    // The words keep Flutter's place; the glyph centres on their first line (align gate `icon-label`).
    // The words keep Flutter's place; the line lifts by half the glyph's extra height so the glyph centres on the
    // words' first line (align gate `icon-label`) and stays inside the line's box.
    val line = with(LocalDensity.current) { style.lineHeight.toDp().value }
    val lift = maxOf(0f, (KitIconSize.Small.dp - line) / 2).dp // NV: half the height difference
    Row(Modifier.padding(top = Space.S2.dp - lift), verticalAlignment = Alignment.Top) {
        KitIcon(glyph, size = KitIconSize.Small, tone = tone)
        Spacer(Modifier.width(Space.S1.dp))
        BasicText(flag.label, Modifier.weight(1f).padding(top = lift), style = style)
    }
}

/**
 * Priority as signal bars (Dart `KitPriorityGlyph`): three bars filled to the level, a filled plate with a bang for
 * urgent, a dashed line for someday. Decorative: the word beside it says the priority. The bars fill from the start edge.
 *
 * States: none (decorative)
 * Text: none
 * Cap: Glyph
 * Motion: none
 * Replaces: KitPriorityGlyph (lib/ui/kit/kit_task_card.dart)
 */
@Composable
public fun KitPriorityGlyph(priority: KitPriority, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.kitMaxWidth(CapRole.Glyph).size(20.dp).clearAndSetSemantics {}) { // F: lib/ui/kit/kit_task_card.dart:470
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        when (priority) {
            KitPriority.Urgent -> {
                drawRoundRect(
                    colors.text1,
                    topLeft = Offset(2 * u, 2 * u), // F: lib/ui/kit/kit_task_card.dart:515,516
                    size = Size(w - 2 * u - 2 * u, h - 2 * u - 2 * u), // F: lib/ui/kit/kit_task_card.dart:515-518
                    cornerRadius = CornerRadius(FlutterKitTokens.PRIORITY_PLATE_RADIUS * u),
                )
                drawLine(colors.ground, Offset(w / 2, 6 * u), Offset(w / 2, 11 * u), 2 * u, StrokeCap.Round) // F: lib/ui/kit/kit_task_card.dart:525,528
                drawCircle(colors.ground, u, Offset(w / 2, 14 * u)) // F: lib/ui/kit/kit_task_card.dart:529
            }
            KitPriority.Someday -> {
                var x = 3 * u // F: lib/ui/kit/kit_task_card.dart:537
                while (x < w - 2 * u) { // F: lib/ui/kit/kit_task_card.dart:537
                    drawLine(colors.text2, Offset(x, h / 2), Offset(x + 2 * u, h / 2), 2 * u, StrokeCap.Round) // F: lib/ui/kit/kit_task_card.dart:535,538
                    x += 5 * u // F: lib/ui/kit/kit_task_card.dart:537
                }
            }
            else -> {
                val on = KitTaskCardRules.litBars(priority)
                val bar = 4 * u // F: lib/ui/kit/kit_task_card.dart:543
                val gap = 2 * u // F: lib/ui/kit/kit_task_card.dart:544
                for (i in 0 until 3) { // NV: three bars
                    var left = 2 * u + i * (bar + gap) // F: lib/ui/kit/kit_task_card.dart:546
                    if (rtl) left = w - left - bar
                    val top = 17 * u - 4 * u - i * 4 * u // F: lib/ui/kit/kit_task_card.dart:548
                    drawRoundRect(
                        if (i < on) colors.text1 else colors.surface3,
                        topLeft = Offset(left, top),
                        size = Size(bar, 17 * u - top), // F: lib/ui/kit/kit_task_card.dart:554
                        cornerRadius = CornerRadius(FlutterKitTokens.PRIORITY_BAR_RADIUS * u),
                    )
                }
            }
        }
    }
}
