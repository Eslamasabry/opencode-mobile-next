package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.ActionBlockRules
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.action.KitActionLayout
import io.github.eslamasabry.oc.ui.kit.action.KitActionStack
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitChevron
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitChecklistHideSteps
import io.github.eslamasabry.oc.ui.words.res.kitChecklistNeedsYou
import io.github.eslamasabry.oc.ui.words.res.kitChecklistNext
import io.github.eslamasabry.oc.ui.words.res.kitChecklistShowSteps
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitMarkDone
import io.github.eslamasabry.oc.ui.words.res.kitMarkFailed
import io.github.eslamasabry.oc.ui.words.res.kitMarkPaused
import io.github.eslamasabry.oc.ui.words.res.kitMarkWaiting
import io.github.eslamasabry.oc.ui.words.res.kitMarkWorking
import io.github.eslamasabry.oc.ui.words.res.kitProgressStep
import io.github.eslamasabry.oc.ui.words.res.kitSinceStillWaiting
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * One step of a [KitChecklist] (Dart `KitStep`). Its mark comes from the engine, never inferred: a step from an
 * existing install is [KitStepState.Done] with "Already installed" as its [supporting] (STATE-11). [paused] only with a
 * waiting or working step; [retry] and [report] on the failed row only; [value] 0..1 while it works. A [personAction]
 * makes it a step only the person can do: the row leads with the needs-you mark and the action is its button.
 */
public data class KitStep(
    val title: String,
    val state: KitStepState,
    val paused: Boolean = false,
    val supporting: String? = null,
    val personAction: KitAction? = null,
    val retry: KitAction? = null,
    val report: KitAction? = null,
    val value: Float? = null,
    val testTag: String? = null,
) {
    init {
        require(!paused || state == KitStepState.Waiting || state == KitStepState.Working) {
            "KitStep: paused only applies to a waiting or working step"
        }
        require(retry == null || state == KitStepState.Failed) { "KitStep: Try again belongs on the failed row only" }
        require(report == null || state == KitStepState.Failed) { "KitStep: Report belongs on the failed row only" }
        require(value == null || value in 0f..1f) { "KitStep: value must be between 0 and 1" }
    }

    /** A step only the person can do, not yet done. */
    val needsPerson: Boolean get() = personAction != null && state != KitStepState.Done
}

/** Pure rules of [KitChecklist] (Dart `_KitChecklistState`). */
public object KitChecklistRules {
    /** At most two ways out once the working step is slow. */
    public const val MAX_ON_SLOW: Int = 2 // F: lib/ui/kit/kit_checklist.dart:143

    /** The step the job is at: the first working, failed or person step, else the first waiting one, else the last. */
    public fun current(steps: List<KitStep>): Int {
        steps.forEachIndexed { i, s ->
            if (s.state == KitStepState.Working || s.state == KitStepState.Failed || s.needsPerson) return i
        }
        steps.forEachIndexed { i, s -> if (s.state == KitStepState.Waiting) return i }
        return steps.lastIndex
    }

    /** Nothing started yet: every step waits, none paused, none the person's. */
    public fun beforeStart(steps: List<KitStep>): Boolean =
        steps.all { it.state == KitStepState.Waiting && !it.paused && !it.needsPerson }

    /** A step works now (not paused): only then can the job be slow. */
    public fun working(steps: List<KitStep>): Boolean = steps.any { it.state == KitStepState.Working && !it.paused }

    public fun allDone(steps: List<KitStep>): Boolean = steps.all { it.state == KitStepState.Done }

    /** The row's button moves under the words when the text is very large or the button wants over half the row. */
    public fun buttonBelow(buttonWidth: Float, rowWidth: Float, textScale: Float): Boolean =
        textScale > 1.6f || buttonWidth > rowWidth / 2 // F: lib/ui/app_theme.dart:205 lib/ui/kit/kit_checklist.dart:616
}

/**
 * A job made of steps, with steps only the person can do mixed in (Dart `KitChecklist`): phone setup, Termux, the voice
 * model, a team task's cycle. A mark per step, one optional bar for the whole job ([progress]), the [estimate] and
 * [cost] before the start, Stop and Resume, a failure on its own row with Try again, the job's one [log] folded under
 * Details, and a one-line [compact] form that unfolds in place. Each row is one semantics node ("Step 2 of 5, Download,
 * Working, 29 of 30 MB"); a visually hidden summary is the live region and changes only with the current step or its
 * state. A working step with [since] escalates once after 8 s: its supporting line counts "Still waiting after 23 s" and
 * the [onSlow] ways out unfold. One Done haptic when every step turns done. Stop never confirms by itself.
 *
 * States: before-start, working, person-step, slow, failed, paused, stopped, done, compact
 * Text: reading
 * Cap: Reading
 * Motion: marks cross-fade QUICK, a step's bar eases STANDARD, the slow ways out and the compact list reveal; Off = still
 * Replaces: KitChecklist, KitStep (lib/ui/kit/kit_checklist.dart)
 */
@Composable
public fun KitChecklist(
    steps: List<KitStep>,
    modifier: Modifier = Modifier,
    progress: KitProgress? = null,
    estimate: String? = null,
    cost: List<String> = emptyList(),
    stop: KitAction? = null,
    resume: KitAction? = null,
    log: (@Composable () -> Unit)? = null,
    since: TimeMark? = null,
    onSlow: List<KitAction> = emptyList(),
    compact: Boolean = false,
    next: String? = null,
) {
    require(steps.isNotEmpty()) { "KitChecklist: at least one step" }
    require(onSlow.size <= KitChecklistRules.MAX_ON_SLOW) { "KitChecklist: at most two ways out" }
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val done = KitChecklistRules.allDone(steps)
    val was = remember { mutableStateOf(done) }
    val still = KitMotion.state.level == MotionLevel.Off
    LaunchedEffect(done) {
        // Once, on the turn to done; a first build that is already done is not a turn.
        if (done && !was.value && appearance.haptics && !still) platform.haptic(KitHaptic.Done)
        was.value = done
    }
    // Only a step that works can be slow; a stopped or finished job waits for nobody.
    val watched = if (KitChecklistRules.working(steps)) since else null
    var slow by remember(watched) { mutableStateOf(watched != null && watched.elapsedNow() >= ESCALATE) }
    if (watched != null && !slow) {
        LaunchedEffect(watched) {
            delay((ESCALATE - watched.elapsedNow()).coerceAtLeast(Duration.ZERO))
            slow = true
        }
    }
    Box(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().testTag("kit-checklist")) {
        if (compact) Compact(steps, next, slow, since, progress, estimate, cost, stop, resume, log, onSlow)
        else Full(steps, slow, since, progress, estimate, cost, stop, resume, log, onSlow, announce = true)
    }
}

private val ESCALATE: Duration = MotionTokens.ESCALATE_AFTER.milliseconds

@Composable
private fun markWord(state: KitStepState, paused: Boolean): String =
    stringResource(
        if (paused) Res.string.kitMarkPaused
        else when (state) {
            KitStepState.Waiting -> Res.string.kitMarkWaiting
            KitStepState.Working -> Res.string.kitMarkWorking
            KitStepState.Done -> Res.string.kitMarkDone
            KitStepState.Failed -> Res.string.kitMarkFailed
        },
    )

@Composable
private fun stillWaiting(since: TimeMark?): String =
    stringResource(Res.string.kitSinceStillWaiting, (since?.elapsedNow() ?: ESCALATE).inWholeSeconds.coerceAtLeast(ESCALATE.inWholeSeconds))

/** "Step 3 of 7, Installing, Working[, needs you, Allow][, Still waiting after 8 s]": what the live region says. */
@Composable
private fun summary(steps: List<KitStep>, slow: Boolean, since: TimeMark?): String {
    val index = KitChecklistRules.current(steps)
    val step = steps[index]
    return listOfNotNull(
        stringResource(Res.string.kitProgressStep, index + 1, steps.size),
        step.title,
        markWord(step.state, step.paused),
        if (step.needsPerson) stringResource(Res.string.kitChecklistNeedsYou, step.personAction!!.label) else null,
        if (slow && step.state == KitStepState.Working && !step.paused) stillWaiting(since) else null,
    ).joinToString(", ")
}

@Composable
private fun Compact(
    steps: List<KitStep>,
    next: String?,
    slow: Boolean,
    since: TimeMark?,
    progress: KitProgress?,
    estimate: String?,
    cost: List<String>,
    stop: KitAction?,
    resume: KitAction?,
    log: (@Composable () -> Unit)?,
    onSlow: List<KitAction>,
) {
    var unfolded by rememberSaveable { mutableStateOf(false) }
    val index = KitChecklistRules.current(steps)
    val step = steps[index]
    val nextWords = next?.let { stringResource(Res.string.kitChecklistNext, it) }
    val line = listOfNotNull(stringResource(Res.string.kitProgressStep, index + 1, steps.size), step.title, nextWords).joinToString(" · ")
    val spoken = listOfNotNull(summary(steps, slow, since), nextWords).joinToString(", ")
    val hint = stringResource(if (unfolded) Res.string.kitChecklistHideSteps else Res.string.kitChecklistShowSteps)
    Column(Modifier.fillMaxWidth()) {
        KitTappable(
            onClick = { unfolded = !unfolded },
            modifier = Modifier.fillMaxWidth().semantics {
                liveRegion = LiveRegionMode.Polite
                if (unfolded) collapse { unfolded = false; true } else expand { unfolded = true; true }
            }.testTag("kit-checklist-compact"),
            role = KitTappableRole.Row,
            onClickLabel = hint,
            shape = KitShapeId.Tile,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = LocalKitTokens.current.minTarget.dp).padding(horizontal = Space.S2.dp)
                    .clearAndSetSemantics { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepMarkSlot(step)
                Spacer(Modifier.width(Space.S3.dp))
                KitText(line, Modifier.weight(1f).testTag("kit-checklist-compact-line"), role = TypeRole.Label, maxLines = 2) // F: lib/ui/kit/kit_checklist.dart:303
            }
        }
        KitReveal(unfolded) { Full(steps, slow, since, progress, estimate, cost, stop, resume, log, onSlow, announce = false) }
    }
}

@Composable
private fun Full(
    steps: List<KitStep>,
    slow: Boolean,
    since: TimeMark?,
    progress: KitProgress?,
    estimate: String?,
    cost: List<String>,
    stop: KitAction?,
    resume: KitAction?,
    log: (@Composable () -> Unit)?,
    onSlow: List<KitAction>,
    announce: Boolean,
) {
    val tokens = LocalKitTokens.current
    val current = KitChecklistRules.current(steps)
    val beforeStart = KitChecklistRules.beforeStart(steps) && (estimate != null || cost.isNotEmpty())
    Column(Modifier.fillMaxWidth()) {
        if (announce) {
            // The job header's live region (A11Y-3): a visually hidden summary.
            val said = summary(steps, slow, since)
            Box(
                Modifier.size(FlutterKitTokens.LIVE_REGION_SIZE.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = said }
                    .testTag("kit-checklist-summary"),
            )
        }
        if (beforeStart) {
            estimate?.let { KitText(it, Modifier.testTag("kit-checklist-estimate"), role = TypeRole.Secondary, tone = TextTone.Secondary) }
            if (cost.isNotEmpty()) {
                if (estimate != null) Spacer(Modifier.height(Space.S2.dp))
                KitCostNotice(cost, Modifier.testTag("kit-checklist-cost"))
            }
            Spacer(Modifier.height(Space.S3.dp))
        }
        progress?.let {
            KitProgressView(it)
            Spacer(Modifier.height(Space.S3.dp))
        }
        Column(Modifier.fillMaxWidth().testTag("kit-checklist-steps")) {
            steps.forEachIndexed { i, step ->
                StepRow(step, i, steps.size, slow = slow && i == current, since = since)
            }
        }
        if (onSlow.isNotEmpty()) {
            KitReveal(slow) {
                Box(Modifier.padding(top = Space.S2.dp).testTag("kit-checklist-on-slow")) { Actions(null, onSlow) }
            }
        }
        if (resume != null || stop != null) {
            Spacer(Modifier.height(Space.S3.dp))
            Actions(resume, listOfNotNull(stop))
        }
        if (log != null) {
            Spacer(Modifier.height(tokens.sectionGap.dp))
            KitDivider()
            Spacer(Modifier.height(Space.S2.dp))
            LogFold(log)
        }
    }
}

/**
 * Dart `KitActionBlock`: where it stacks (a compact window, or a destructive tertiary) each text action keeps the start
 * edge (`KitInset`), which is [KitActionStack]'s stack; from medium up it is the block's one end-aligned row.
 */
@Composable
private fun Actions(primary: KitAction?, tertiary: List<KitAction>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (ActionBlockRules.layout(maxWidth.value, tertiary) == KitActionLayout.Stack) KitActionStack(primary = primary, tertiary = tertiary)
        else KitActionBlock(primary = primary, tertiary = tertiary)
    }
}

/** The job's one log, folded under Details (closed by default; it never opens itself). */
@Composable
private fun LogFold(log: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        KitTappable(
            onClick = { open = !open },
            modifier = Modifier.fillMaxWidth().semantics {
                if (open) collapse { open = false; true } else expand { open = true; true }
            }.testTag("kit-checklist-details"),
            role = KitTappableRole.Row,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = LocalKitTokens.current.minTarget.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitText(stringResource(Res.string.kitDetails), Modifier.weight(1f), role = TypeRole.Secondary, tone = TextTone.Secondary)
                KitChevron(open)
            }
        }
        KitReveal(open) { log() }
    }
}

/** A step's leading mark: the needs-you mark for a person step, else its state mark; a change cross-fades on QUICK. */
@Composable
private fun StepMarkSlot(step: KitStep) {
    val person = step.needsPerson
    Box(Modifier.widthIn(min = FlutterKitTokens.ICON_TILE_SIZE.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Crossfade(Triple(person, step.state, step.paused), animationSpec = KitMotion.specs.quick(), label = "checklist-mark") { (p, state, paused) ->
            when {
                p -> KitNeedsYouMark()
                paused -> Box(Modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp), contentAlignment = Alignment.Center) {
                    KitIcon(KitIcons.pause, size = KitIconSize.Small, tone = TextTone.Secondary, growsWithText = true)
                }
                else -> Box(Modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp), contentAlignment = Alignment.Center) { KitStepMark(state) }
            }
        }
    }
}

/**
 * Dart `KitStatusMark`'s glyph for a step: still (reduced motion or a still frame), a working step is the 12 dp status
 * dot glyph (`AppIconography.statusDot`, whose drawing sits inside its box as Flutter's does); otherwise [StepMark].
 */
@Composable
internal fun KitStepMark(state: KitStepState) {
    if (state == KitStepState.Working && !KitMotion.state.ambientLoops) {
        KitGlyph(KitIcons.statusDot, LocalKitColors.current.accent, FlutterKitTokens.MARK_DOT_SIZE.dp)
    } else {
        StepMark(state)
    }
}

/** One step as a row (Dart `_StepRow` over `KitRow`): mark, title, supporting line, and for a person or failed step its one button. */
@Composable
private fun StepRow(step: KitStep, index: Int, total: Int, slow: Boolean, since: TimeMark?) {
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val person = step.needsPerson
    val working = step.state == KitStepState.Working && !step.paused
    val supportingText = if (slow && working) stillWaiting(since) else step.supporting
    val supporting: AnnotatedString? = when {
        person -> kitNeedsYouSpan() + AnnotatedString(supportingText.orEmpty())
        supportingText == null -> null
        else -> AnnotatedString(supportingText)
    }
    val action = if (person) step.personAction else step.retry
    val label = listOfNotNull(
        stringResource(Res.string.kitProgressStep, index + 1, total),
        step.title,
        markWord(step.state, step.paused),
        supportingText,
        if (person) stringResource(Res.string.kitChecklistNeedsYou, step.personAction!!.label) else null,
    ).joinToString(", ")
    val scale = kitTextScale()
    // Under the words: the mark slot and the row's 12 dp gap.
    val indent = (FlutterKitTokens.MARK_SLOT_SIZE + Space.S3).dp // F: lib/ui/kit/kit_checklist.dart:555
    val words: @Composable (Modifier) -> Unit = { m ->
        Row(
            m.heightIn(min = (if (supporting == null) FlutterKitTokens.ROW_HEIGHT else FlutterKitTokens.ROW_HEIGHT_TWO_LINE).dp)
                .padding(vertical = Space.S1.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepMarkSlot(step)
            Spacer(Modifier.width(Space.S3.dp))
            Column(Modifier.weight(1f)) {
                KitText(step.title, role = TypeRole.RowTitle, maxLines = 2) // F: lib/ui/kit/kit_checklist.dart:548
                if (supporting != null) {
                    Spacer(Modifier.height(FlutterKitTokens.ROW_LINE_GAP.dp))
                    BasicText(
                        supporting,
                        style = typography.style(TypeRole.Secondary).copy(color = colors.text2, fontFeatureSettings = "tnum"),
                        maxLines = 4, // F: lib/ui/kit/kit_checklist.dart:550
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = label }.then(step.testTag?.let { Modifier.testTag(it) } ?: Modifier),
    ) {
        if (action == null) {
            words(Modifier.fillMaxWidth())
        } else {
            val measurer = rememberTextMeasurer()
            val buttonStyle = typography.style(TypeRole.Button)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val text = with(LocalDensity.current) { measurer.measure(action.label, buttonStyle).size.width.toDp().value }
                val estimate = text + 48 + (if (action.icon == null) 0 else 26) // F: lib/ui/kit/kit_checklist.dart:658
                if (KitChecklistRules.buttonBelow(estimate, maxWidth.value, scale)) {
                    Column(Modifier.fillMaxWidth()) {
                        words(Modifier.fillMaxWidth())
                        Box(Modifier.padding(start = indent, bottom = Space.S2.dp)) {
                            KitButton(action, role = KitButtonRole.Secondary)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        words(Modifier.weight(1f))
                        Box(Modifier.padding(start = Space.S3.dp)) { KitButton(action, role = KitButtonRole.Secondary) }
                    }
                }
            }
        }
        val value = step.value
        if (working && value != null) {
            Box(Modifier.padding(start = indent, bottom = Space.S1.dp).clearAndSetSemantics {}) { StepBar(value) }
        }
        if (step.report != null && !person) {
            Box(Modifier.padding(start = indent, bottom = Space.S1.dp)) { KitButton(step.report, role = KitButtonRole.Tertiary) }
        }
    }
}

/** The thin per-row bar: `accent` on `surface3`, the loading bar's height, filling from the start, easing STANDARD. */
@Composable
private fun StepBar(value: Float) {
    val colors = LocalKitColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shown by animateFloatAsState(value, KitMotion.specs.enter(), label = "checklist-step-bar")
    Canvas(Modifier.fillMaxWidth().height(FlutterKitTokens.LOADING_BAR_HEIGHT.dp)) {
        val r = CornerRadius(size.height / 2) // F: lib/ui/kit/kit_checklist.dart:677
        drawRoundRect(colors.surface3, cornerRadius = r)
        val w = size.width * shown.coerceIn(0f, 1f)
        drawRoundRect(colors.accent, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height), cornerRadius = r)
    }
}
