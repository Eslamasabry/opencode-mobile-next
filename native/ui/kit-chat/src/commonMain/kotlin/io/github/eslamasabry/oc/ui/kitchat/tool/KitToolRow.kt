package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrameText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.motion.KitSwapCount
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.turn.KitLiveSweep
import io.github.eslamasabry.oc.ui.kitchat.turn.WORK_GAP
import io.github.eslamasabry.oc.ui.kitchat.turn.workMark

/** Plan 09 R13: a step sits in the conversation column, capped at 700 dp, on the page gutter. */
private val ConversationColumn =
    Modifier.fillMaxWidth().widthIn(max = 700.dp).padding(horizontal = Space.S4.dp)

/**
 * From this text scale the title, subject and detail may take two lines (Dart `_kWrapTextScale`,
 * A11Y-8).
 */
private const val WRAP_TEXT_SCALE = 1.3f

/** The status words never take more than this share of the line (Dart `KitToolRow`). */
private const val END_SHARE = 0.45f

/**
 * One step of the agent's work as a line of the reply (plan 09 §9.5; Dart `KitToolRow`): no frame,
 * no fill. The kind glyph, the plain title, the subject in mono reading left to right, the detail,
 * the status in the caller's words, the duration and the "+n −n" counts. A failed step is drawn
 * neutral (no danger colour, R8); a stopped one reads as the quiet status it is.
 *
 * With a [body], the whole header is one tappable row that asks to open or fold the step
 * ([onToggle]; the caller owns [opened]); the body (a command box, a diff) is drawn only while
 * [opened]. Without one the row is plain text. [KitToolView.subject] and [KitToolView.detail]
 * display redacted (R18).
 *
 * Inside an opened [KitStepTimeline] the row takes its timeline form (Dart `_buildTimelineStep`): one 32 dp line, its
 * node on the rail, no chevron of its own (the group's line has the one chevron); [onOpen] (a step that opens another
 * page, with no [body]) keeps a forward chevron.
 *
 * Text: frame (≤ 1.3×) for title, detail and status; the subject is mono, left to right.
 */
@Composable
public fun KitToolRow(
    view: KitToolView,
    opened: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    body: (@Composable () -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    if (kitOnStepRail()) {
        TimelineStep(view, opened, onToggle, modifier, body, onOpen)
        return
    }
    val tokens = LocalKitTokens.current
    // Opening fades the body in (never on first composition: a row scrolled back into view shows at once).
    val reduced = KitMotion.state.reduced
    val quick = KitMotion.specs.pace<Float>(KitPace.Quick)
    var wasOpen by remember { mutableStateOf(opened) }
    val fade = remember(opened) { Animatable(if (opened && !wasOpen && !reduced) 0f else 1f) }
    LaunchedEffect(opened) {
        wasOpen = opened
        fade.animateTo(1f, quick)
    }
    Column(modifier.then(ConversationColumn)) {
        if (body != null) {
            KitTappable(
                onClick = onToggle,
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        if (opened) {
                            collapse {
                                onToggle()
                                true
                            }
                        } else {
                            expand {
                                onToggle()
                                true
                            }
                        }
                    },
                role = KitTappableRole.Row,
                shape = KitShapeId.Tile,
            ) {
                StepLine(view, opened = opened)
            }
        } else {
            Box(
                Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).semantics(
                    mergeDescendants = true
                ) {}
            ) {
                StepLine(view, opened = null)
            }
        }
        if (opened && body != null) {
            // E20-S12: the opened parts stack (a Box drew the path and "Show all output" over the code's first lines)
            // and start at the text's own x, under the title, as in Flutter ("its body sits at the text's own x").
            // Flutter `_KitToolRowState._fade`: the body fades in over QUICK when it opens; no size motion (MOT-5,
            // R5: a list item); reduced motion shows it at once.
            Column(
                Modifier.fillMaxWidth()
                    .padding(start = stepGlyph() + WORK_GAP.dp, top = Space.S1.dp, bottom = Space.S2.dp)
                    .graphicsLayer { alpha = fade.value }
                    .testTag("kit-tool-body"),
                verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
            ) {
                CompositionLocalProvider(LocalKitStepBody provides true) { body() }
            }
        }
    }
}

/**
 * True inside an opened step's body: its parts already sit on the step's text edge, so a quiet line drops the page
 * gutter it carries in the transcript (E20-S12).
 */
internal val LocalKitStepBody = compositionLocalOf { false }

/**
 * The kind glyph's size (P-011): the work line's mark, so a step's glyph sits in the summary mark's column and its
 * name on the summary's text edge. It grows with the text like the mark.
 */
@Composable
private fun stepGlyph(): Dp = workMark()

/** The header line. [opened] is null for a row that does not open (no chevron). */
@Composable
private fun StepLine(view: KitToolView, opened: Boolean?) {
    val tokens = LocalKitTokens.current
    // The person's text size (system × in-app), before any frame cap.
    val fontScale = (LocalKitAppDensity.current ?: LocalDensity.current).fontScale
    val wide = fontScale >= WRAP_TEXT_SCALE
    val lines = if (wide) 2 else 1
    val glyph = stepGlyph()
    val subject = view.subject?.takeIf { it.isNotEmpty() }
    val detail = view.detail?.takeIf { it.isNotEmpty() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.S1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // B-KIT-036: a running step's node carries the step light (one 2 s lap; still under reduced motion);
        // the node itself is the style's (Signal draws its lamp).
        Box(Modifier.padding(top = Space.S1.dp / 2), contentAlignment = Alignment.Center) {
            // The node draws the step light itself while the step runs (one light, never two).
            KitToolNode(view, glyph)
        }
        Spacer(Modifier.width(WORK_GAP.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp / 2)) {
            // Flutter's step line: the verb in the label weight and the subject in mono on the same line ("Read
            // checkout_bloc.dart"), so a list of steps scans as one column of verbs. From 1.3x text the subject
            // drops under the verb.
            if (wide || subject == null) {
                KitLiveSweep(working = view.status == KitToolStatus.Running, step = true) {
                    StepTitle(view.title, lines)
                }
                if (subject != null) {
                    KitFrameText { KitMonoLtr(subject, Modifier.fillMaxWidth(), TextTone.Secondary, maxLines = lines) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitLiveSweep(working = view.status == KitToolStatus.Running, step = true) {
                        StepTitle(view.title, 1)
                    }
                    Spacer(Modifier.width(Space.S2.dp))
                    Box(Modifier.weight(1f)) {
                        KitFrameText { KitMonoLtr(subject, Modifier.fillMaxWidth(), TextTone.Primary, maxLines = 1) }
                    }
                }
            }
            if (detail != null) {
                KitText(
                    redacted(detail),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    kind = KitTextKind.Frame,
                    maxLines = lines,
                )
            }
        }
        Spacer(Modifier.width(Space.S2.dp))
        // E20-S14: the time and the chevron are one group at the end, centred on the title's first line (the time sat
        // a line above the chevron). The group never takes more than its share of the line.
        val firstLine =
            with(LocalDensity.current) {
                LocalKitTypography.current.style(TypeRole.Label).lineHeight.takeIf { it.isSp }?.toDp()
            } ?: tokens.smallIcon.dp
        Row(
            Modifier.heightIn(min = firstLine)
                .layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0, maxWidth = (c.maxWidth * END_SHARE).toInt()))
                    layout(p.width, p.height) { p.place(0, 0) }
                }
                .testTag("kit-tool-end"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp, Alignment.End),
        ) {
            StepEnd(view, lines, Modifier.weight(1f, fill = false))
            if (opened != null) KitFoldChevron(opened, Modifier.testTag("kit-tool-chevron"))
        }
    }
}

/**
 * The end of the line, on one line as in Flutter: the counts, then how the step stands — a finished step shows only
 * how long it took; a running one a live dot and its time; waiting, failed and stopped ones a small mark and their
 * words. The status is always spoken.
 */
@Composable
private fun StepEnd(view: KitToolView, lines: Int, modifier: Modifier) {
    val duration = view.durationLabel?.takeIf { it.isNotEmpty() }
    val counts = view.added != null || view.removed != null
    // The parts are S2 apart. The gaps are their own (no spacedBy): the status slot stays composed while a finished
    // step shows nothing there, and an empty slot must not add a gap.
    Row(
        modifier.semantics(mergeDescendants = true) { stateDescription = view.statusLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (counts) Counts(view.added, view.removed)
        // M-020: how the step stands changes as one QUICK cross-fade (live dot → done, failed, stopped), never in one
        // frame; reduced motion: instant.
        KitSwap(view.status) { status -> StepStatus(view, status, lines, gapBefore = counts) }
        if (duration != null && view.status != KitToolStatus.Pending && view.status != KitToolStatus.WaitingForYou) {
            val gap = if (counts || view.status != KitToolStatus.Done) Space.S2.dp else 0.dp
            KitText(duration, Modifier.padding(start = gap).testTag("kit-tool-duration"), role = TypeRole.Caption, tone = TextTone.Tertiary, kind = KitTextKind.Frame, maxLines = 1)
        }
    }
}

/**
 * The step's verb ("Running" → "Running the date"): a new title cross-fades over the old one (M-020, QUICK; instant
 * under reduced motion) instead of replacing it in one frame.
 */
@Composable
private fun StepTitle(title: String, lines: Int) {
    KitSwap(title, alignment = Alignment.CenterStart) { shown ->
        KitText(shown, role = TypeRole.Label, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = lines)
    }
}

@Composable
private fun StepStatus(view: KitToolView, status: KitToolStatus, lines: Int, gapBefore: Boolean) {
    val colors = LocalKitColors.current
    if (status == KitToolStatus.Done) return // a finished step shows only how long it took
    Row(Modifier.padding(start = if (gapBefore) Space.S2.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            KitToolStatus.Done -> Unit
            KitToolStatus.Running -> Box(Modifier.size(8.dp).background(colors.live, CircleShape).testTag("kit-tool-live-dot"))
            // P-105: blocked on the person: the work line's attention dot and the caller's words, no clock.
            KitToolStatus.WaitingForYou ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    Box(Modifier.size(8.dp).background(colors.attention, CircleShape).testTag("kit-tool-waiting-you"))
                    KitText(view.statusLabel, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = lines)
                }
            else -> {
                val mark =
                    when (status) {
                        KitToolStatus.Failed -> KitIcons.error
                        KitToolStatus.Stopped -> KitIcons.stopCircle
                        else -> KitIcons.radioEmpty
                    }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    KitIcon(mark, size = KitIconSize.Tiny, tone = if (status == KitToolStatus.Failed) TextTone.Primary else TextTone.Secondary)
                    KitText(
                        view.statusLabel,
                        role = TypeRole.Secondary,
                        // LOOK-5: a failed step's word is plain text1, never a danger colour; the rest are quiet.
                        tone = if (status == KitToolStatus.Failed) TextTone.Primary else TextTone.Secondary,
                        kind = KitTextKind.Frame,
                        maxLines = lines,
                    )
                }
            }
        }
    }
}

/**
 * "+4 −1": the signs carry the meaning, the colours only repeat it (never colour alone, STATE-9);
 * left to right in any interface language.
 */
@Composable
internal fun Counts(added: Int?, removed: Int?) {
    val colors = LocalKitColors.current
    val style =
        LocalKitTypography.current
            .style(TypeRole.Secondary)
            .copy(
                color = colors.text2,
                textDirection = TextDirection.Ltr,
                fontFeatureSettings = "tnum",
            )
    // M-020: each number rolls to its new value (KitSwap.count: Full rolls, Calm cross-fades, reduced motion jumps).
    KitFrameText {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.testTag("kit-tool-counts"), horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                if (added != null)
                    KitSwapCount(added) { n -> BasicText("+$n", style = style.copy(color = colors.success), maxLines = 1) }
                if (removed != null)
                    KitSwapCount(removed) { n -> BasicText("−$n", style = style, maxLines = 1) }
            }
        }
    }
}

