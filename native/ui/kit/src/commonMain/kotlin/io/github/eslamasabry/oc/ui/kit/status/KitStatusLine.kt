package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMore
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitSinceStillWaiting
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The quiet line's glyph, drawn at a fixed size (Dart `Icon(icon, size: 14)`). */
private val QUIET_GLYPH = 14.dp // F: lib/ui/kit/kit_status_line.dart:484

/** The More and close buttons' glyph size (Dart `KitIconButton(size: 20)`). */
private const val CONTROL_GLYPH = 20 // F: lib/ui/kit/kit_status_line.dart:616

/** A wait turns slow this long after it began (Dart `KitMotion.escalateAfter`). */
private val ESCALATE_AFTER: Duration = 8.seconds // F: lib/ui/kit/kit_motion.dart:42

/** The countdown asks for its words again this often (Dart `_Ticker`). */
private val TICK: Duration = 1.seconds // F: lib/ui/kit/kit_status_line.dart:947

/**
 * The one status line (Dart `KitStatusLine.of`): renders [status] from its entity's status source (ARCH-8).
 *
 * States: condition, working, slow, now-line, error, disabled (action) Text: reading Cap: fill Motion: entrance on a
 * new condition, the Now line unfolds, the supporting line cross-fades; Off = instant Hidden: none Replaces:
 * KitStatusLine (lib/ui/kit/kit_status_line.dart)
 */
@Composable
public fun KitStatusLine(status: KitStatus, modifier: Modifier = Modifier) {
    KitStatusLine(
        message = status.message,
        modifier = modifier,
        icon = status.icon,
        tone = status.tone,
        action = status.action,
        more = status.more,
        onDismiss = status.onDismiss,
        dismissLabel = status.dismissLabel,
        supporting = status.supporting,
        next = status.next,
        riskySwitch = status.kind == KitStatusKind.RiskySwitch,
    )
}

/**
 * A condition on an otherwise working screen (Dart `KitStatusLine`): the condition's glyph, its words, an optional
 * supporting line and Now line, its one action and further actions behind a small More menu and, only when dismissing
 * changes nothing real, a close. Never a card; one hairline under it. When the words and the action do not fit on one
 * line (long words, large text) the action moves under the words; More and the close stay at the top unless
 * [controlsTogether]. [since] escalates the line after 8 s ("Still waiting after 8 s") and offers [onSlow]. A
 * [countdownTo] in the future makes the supporting line [countdownLine] of the time left, asked again once a second.
 * [quiet] draws one small muted caption line with an underlined action and no divider. The message is the one polite
 * live region.
 *
 * States: condition, working, slow, now-line, error, disabled (action), quiet, countdown Text: reading Cap: fill Motion:
 * entrance on a new condition, the Now line unfolds, the supporting line cross-fades; Off = instant Hidden: none
 * Replaces: KitStatusLine (lib/ui/kit/kit_status_line.dart)
 */
@Composable
public fun KitStatusLine(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: StatusTone = StatusTone.Neutral,
    action: KitLineAction? = null,
    more: List<KitLineAction> = emptyList(),
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String? = null,
    controlsTogether: Boolean = false,
    supporting: String? = null,
    next: String? = null,
    since: TimeMark? = null,
    onSlow: List<KitLineAction> = emptyList(),
    quiet: Boolean = false,
    semanticsLabel: String? = null,
    countdownTo: TimeMark? = null,
    countdownLine: ((Duration) -> String)? = null,
    onOpen: (() -> Unit)? = null,
    riskySwitch: Boolean = false,
) {
    kitAssert(!riskySwitch || onDismiss == null) { "KitStatusLine: a risky switch is never dismissible (SEC-9)" }
    kitAssert(onSlow.size <= 2) { "KitStatusLine.onSlow offers at most two ways out" } // F: lib/ui/kit/kit_status_line.dart:339
    kitAssert(countdownTo == null || countdownLine != null) { "KitStatusLine: a countdown needs countdownLine" }
    val glyph = icon ?: glyphFor(tone)
    val left = countdownLeft(countdownTo)
    val line = StatusLineParts(
        message = message,
        icon = glyph,
        tone = tone,
        dismissLabel = dismissLabel ?: stringResource(Res.string.kitSheetDismiss),
        onDismiss = onDismiss,
        moreLabel = stringResource(Res.string.kitMore),
        semanticsLabel = semanticsLabel,
        onOpen = onOpen,
    )
    if (quiet) {
        KitEntrance(modifier, key = tone to glyph) {
            QuietLine(line, action, more, words = quietWords(message, left, countdownLine))
        }
        return
    }
    val slow = rememberSlow(since)
    KitEntrance(modifier, key = tone to glyph) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val firstSlow = if (slow) onSlow.firstOrNull() else null
            val shownAction = action ?: firstSlow
            val shownMore =
                when {
                    !slow -> more
                    action == null -> onSlow.drop(1) + more
                    else -> onSlow + more
                }
            val stacked = stacks(constraints.maxWidth, message, shownAction, shownMore, onDismiss != null)
            // A countdown at large text: one quiet line keeps the action in sight (Dart `_countingQuietLine`).
            if (stacked && countdownTo != null) {
                QuietLine(line, shownAction, shownMore, words = quietWords(message, left, countdownLine))
            } else {
                FullLine(
                    line,
                    slow = slow,
                    action = shownAction,
                    more = shownMore,
                    stacked = stacked,
                    controlsTogether = controlsTogether,
                    supporting = if (left != null && countdownLine != null) countdownLine(left) else supporting,
                    countdown = left != null,
                    next = next,
                )
            }
        }
    }
}

/** The words every form shares. */
private class StatusLineParts(
    val message: String,
    val icon: ImageVector,
    val tone: StatusTone,
    val dismissLabel: String,
    val onDismiss: (() -> Unit)?,
    val moreLabel: String,
    val semanticsLabel: String?,
    val onOpen: (() -> Unit)?,
)

/** The quiet line's words: the message, then the countdown when there is one. */
private fun quietWords(message: String, left: Duration?, line: ((Duration) -> String)?): String? =
    if (left == null || line == null) null else "$message · ${line(left)}" // F: lib/ui/kit/kit_status_line.dart:438

/** True once [since] lies 8 s back; flips by itself when the wait crosses it (Dart `KitSince`). */
@Composable
private fun rememberSlow(since: TimeMark?): Boolean {
    var slow by remember(since) { mutableStateOf(since != null && since.elapsedNow() >= ESCALATE_AFTER) }
    if (since != null && !slow) {
        LaunchedEffect(since) {
            delay((ESCALATE_AFTER - since.elapsedNow()).coerceAtLeast(Duration.ZERO))
            slow = true
        }
    }
    return slow
}

/** The time left until [until], asked again once a second until it passes, then once more at zero (Dart `_Ticker`). */
@Composable
private fun countdownLeft(until: TimeMark?): Duration? {
    if (until == null) return null
    var tick by remember(until) { mutableIntStateOf(0) }
    LaunchedEffect(until) {
        while (until.elapsedNow() < Duration.ZERO) {
            delay(TICK)
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val left = -until.elapsedNow()
    return if (left.isNegative()) Duration.ZERO else left
}

/**
 * Whether the action moves under the words (Dart `KitStatusLine._stacks`): only with an action, when the message on
 * one line, the action and the frame (gutter, glyph, gaps, More and close targets) do not fit in [width] px.
 */
@Composable
private fun stacks(width: Int, message: String, action: KitLineAction?, more: List<KitLineAction>, dismiss: Boolean): Boolean {
    if (action == null) return false
    val tokens = LocalKitTokens.current
    val typography = LocalKitTypography.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return with(density) {
        fun oneLine(text: String, role: TypeRole) =
            measurer.measure(text, typography.style(role), softWrap = false, maxLines = 1).size.width
        val frame =
            tokens.gutter.dp.toPx() + iconSize().toPx() + Space.S3.dp.toPx() + Space.S1.dp.toPx() + // F: lib/ui/kit/kit_status_line.dart:660-663
                (if (more.isEmpty()) 0f else tokens.minTarget.dp.toPx()) + // F: lib/ui/kit/kit_status_line.dart:664
                (if (dismiss) tokens.minTarget.dp.toPx() else 0f) // F: lib/ui/kit/kit_status_line.dart:665
        val actionWidth = oneLine(action.label, TypeRole.Button) + 2 * Space.S2.dp.toPx() + Space.S1.dp.toPx() // F: lib/ui/kit/kit_status_line.dart:667-669
        statusControlsStack(width, frame.toInt(), oneLine(message, TypeRole.Body), listOf(actionWidth.toInt()), gap = 0)
    }
}

/**
 * Whether a status line's controls go under its words (Dart `KitStatusLine._stacks`): the words on one line, the frame
 * in front and every control with its gap do not fit in [available] (all in px).
 */
internal fun statusControlsStack(available: Int, lead: Int, message: Int, controls: List<Int>, gap: Int): Boolean =
    controls.isNotEmpty() && lead.toLong() + message + controls.sumOf { it.toLong() + gap } > available

/** The small-icon size grown with the person's text size up to the icon cap (Dart `tokens.iconSize`). */
@Composable
private fun iconSize(): Dp {
    val tokens = LocalKitTokens.current
    val scale = LocalDensity.current.fontScale
    return (tokens.smallIcon * minOf(scale, tokens.maxIconScale)).dp
}

/** The full line (Dart `KitStatusLine._line`). */
@Composable
private fun FullLine(
    line: StatusLineParts,
    slow: Boolean,
    action: KitLineAction?,
    more: List<KitLineAction>,
    stacked: Boolean,
    controlsTogether: Boolean,
    supporting: String?,
    countdown: Boolean,
    next: String?,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val hairline = kitHairline()
    val slowLabel = stringResource(Res.string.kitSinceStillWaiting, ESCALATE_AFTER.inWholeSeconds.toString())
    val announced = if (slow) "${line.message}\n$slowLabel" else line.message
    val shownSupporting = if (countdown) supporting else if (slow) slowLabel else supporting
    val disabledReason = action?.takeIf { !it.enabled }?.disabledReason
    val moreMenu: (@Composable () -> Unit)? = if (more.isEmpty()) null else { { StatusMore(more, line.moreLabel) } }
    val dismissButton: (@Composable () -> Unit)? =
        line.onDismiss?.let { onDismiss ->
            {
                KitIconButton(
                    KitIcons.close,
                    line.dismissLabel,
                    onDismiss,
                    Modifier.testTag("kit-status-dismiss"),
                    size = CONTROL_GLYPH,
                )
            }
        }
    val lone = !stacked && action != null && moreMenu == null && dismissButton == null
    val bodyLine = with(LocalDensity.current) { LocalKitTypography.current.style(TypeRole.Body).lineHeight.toDp() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.statusLineMin.dp)
            .drawBehind {
                val w = hairline.toPx()
                drawRect(colors.hairline, Offset(0f, size.height - w), Size(size.width, w))
            }
            .padding(
                start = tokens.gutter.dp,
                // A lone text action's label ends on the gutter; More and close keep their tighter inset.
                end = if (lone) (tokens.gutter - Space.S2).dp else Space.S1.dp, // F: lib/ui/kit/kit_status_line.dart:801-807
            )
            .testTag("kit-status-line"),
        verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(
            if (stacked) Modifier.padding(top = Space.S2.dp).height(bodyLine) else Modifier,
            contentAlignment = Alignment.Center,
        ) { StatusIcon(line.icon, line.tone) }
        Spacer(Modifier.width(Space.S3.dp))
        Column(Modifier.weight(1f).padding(vertical = Space.S2.dp)) {
            // On one line the glyph centres on the whole block of words (Flutter's centred row), so the block is read
            // as one: the message (announced once, a polite live region), then the supporting and Now lines. A
            // countdown's seconds stay out of it: they are never announced each tick. Stacked, the glyph sits on the
            // message's first line and every line keeps its own node.
            val spoken = listOfNotNull(announced, shownSupporting?.takeIf { !countdown }, next).joinToString("\n")
            Column(
                // Stacked, the words are one group read before the action under them (Dart `OrdinalSortKey`, A11Y-4).
                if (stacked) Modifier.semantics { isTraversalGroup = true }
                else Modifier.testTag("kit-status-message").clearAndSetSemantics {
                    text = AnnotatedString(spoken)
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                KitText(
                    line.message,
                    if (stacked) Modifier.testTag("kit-status-message").semantics { liveRegion = LiveRegionMode.Polite }
                    else Modifier,
                    role = TypeRole.Body,
                    tone = TextTone.Primary,
                )
                if (shownSupporting != null) {
                    KitSwap(shownSupporting, Modifier.padding(top = Space.S1.dp), alignment = Alignment.TopStart) {
                        KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, tabular = countdown)
                    }
                }
                KitReveal(visible = next != null) {
                    KitSwap(next.orEmpty(), Modifier.padding(top = Space.S1.dp), alignment = Alignment.TopStart) {
                        KitText(it, Modifier.testTag("kit-status-next"), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                }
            }
            if (stacked && controlsTogether) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { action?.let { StatusAction(it) } }
                    moreMenu?.invoke()
                    dismissButton?.invoke()
                }
            } else if (stacked && action != null) {
                // The tertiary button's words start on the message's edge (Dart `KitInset`): the kit's text button
                // already reaches its padding out past its edge.
                StatusAction(action)
            }
            if (disabledReason != null) {
                KitText(disabledReason, Modifier.padding(top = Space.S1.dp), role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
        }
        if (!stacked && action != null) {
            // In the row the button keeps its own side padding, as Flutter's padded text button does.
            Box(Modifier.padding(horizontal = Space.S2.dp)) { StatusAction(action) }
        }
        if (!(stacked && controlsTogether)) {
            moreMenu?.invoke()
            dismissButton?.invoke()
        }
    }
}

/** The status's glyph in its tone (Dart `KitIcon.status(tone, icon: icon)`), grown with text up to the icon cap. */
@Composable
private fun StatusIcon(icon: ImageVector, tone: StatusTone) {
    KitIcon(icon, size = KitIconSize.Small, tone = toneFor(tone), growsWithText = true)
}

/** The line's action as a tertiary button (Dart `KitButton.fromAction(action, role: tertiary)`). */
@Composable
private fun StatusAction(action: KitLineAction) {
    KitButton(
        KitAction(
            action.label,
            action.onClick,
            enabled = KitEnabled.of(action.enabled, action.disabledReason),
            icon = action.icon,
            semanticsLabel = action.semanticsLabel,
        ),
        Modifier.testTag(action.testTag ?: "kit-status-action"),
        KitButtonRole.Tertiary,
    )
}

/** The quiet form (Dart `KitStatusLine._quietLine`): a small glyph, caption words, an underlined text action. */
@Composable
private fun QuietLine(line: StatusLineParts, action: KitLineAction?, more: List<KitLineAction>, words: String?) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val typography = LocalKitTypography.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = typography.style(TypeRole.Secondary).copy(color = colors.text2)
    val message = words ?: line.message
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = tokens.gutter.dp, end = (tokens.gutter - Space.S2).dp) // F: lib/ui/kit/kit_status_line.dart:451,452
            .testTag("kit-status-line"),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val oneLine =
                action == null ||
                    with(density) {
                        fun widthOf(text: String) =
                            measurer.measure(text, style, softWrap = false, maxLines = 1).size.width.toFloat()
                        val room =
                            constraints.maxWidth - QUIET_GLYPH.toPx() - Space.S2.dp.toPx() - // F: lib/ui/kit/kit_status_line.dart:460-462
                                (if (line.onDismiss == null) 0f else tokens.minTarget.dp.toPx()) - // F: lib/ui/kit/kit_status_line.dart:463
                                (if (more.isEmpty()) 0f else tokens.minTarget.dp.toPx()) // F: lib/ui/kit/kit_status_line.dart:464
                        widthOf(message) + Space.S2.dp.toPx() + widthOf("·") + 2 * Space.S2.dp.toPx() + // F: lib/ui/kit/kit_status_line.dart:467-470
                            widthOf(action.label) <= room
                    }
            val firstLine = with(density) { style.lineHeight.toDp() }
            val iconTop =
                if (oneLine) 0.dp
                else ((firstLine - QUIET_GLYPH) / 2).coerceIn(0.dp, tokens.minTarget.dp) // F: lib/ui/kit/kit_status_line.dart:480
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = if (oneLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                KitGlyph(line.icon, colors.text2, QUIET_GLYPH, Modifier.padding(top = iconTop))
                Spacer(Modifier.width(Space.S2.dp))
                FlowRow(Modifier.weight(1f), verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
                    val open = line.onOpen
                    BasicText(
                        message,
                        Modifier
                            .testTag("kit-status-message")
                            .then(if (open != null) Modifier.kitTappable(onClick = open).testTag("kit-status-open") else Modifier)
                            .clearAndSetSemantics {
                                contentDescription = line.semanticsLabel ?: line.message
                                liveRegion = LiveRegionMode.Polite
                            },
                        style = if (words == null) style else style.copy(fontFeatureSettings = "tnum"),
                    )
                    if (action != null && oneLine) {
                        BasicText("·", Modifier.padding(start = Space.S2.dp).clearAndSetSemantics {}, style = style)
                    }
                    if (action != null) {
                        Box(
                            Modifier
                                .heightIn(min = tokens.minTarget.dp)
                                .kitTappable(onClick = action.onClick, onClickLabel = action.label)
                                .semantics { contentDescription = action.label }
                                .testTag(action.testTag ?: "kit-status-action")
                                .padding(horizontal = Space.S2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                action.label,
                                Modifier.clearAndSetSemantics {},
                                style = style.copy(textDecoration = TextDecoration.Underline),
                            )
                        }
                    }
                }
                if (more.isNotEmpty()) StatusMore(more, line.moreLabel)
                line.onDismiss?.let {
                    KitIconButton(KitIcons.close, line.dismissLabel, it, Modifier.testTag("kit-status-dismiss"), size = CONTROL_GLYPH)
                }
            }
        }
    }
}

/** The line's More menu (Dart `_moreMenu`): a small ⋯ button opening the other ways out. */
@Composable
private fun StatusMore(more: List<KitLineAction>, moreLabel: String) {
    var open by remember { mutableStateOf(false) }
    KitMenu(
        expanded = open,
        items =
            more.map {
                KitMenuItem(it.label, it.onClick, enabled = KitEnabled.of(it.enabled, it.disabledReason))
            },
        onDismissRequest = { open = false },
        anchor = {
            KitIconButton(KitIcons.more, moreLabel, { open = true }, Modifier.testTag("kit-status-more"), size = CONTROL_GLYPH)
        },
    )
}

/** The one glyph per status (Dart `KitTokens.glyphFor`): every status has its own shape (STATE-9). */
internal fun glyphFor(tone: StatusTone): ImageVector =
    when (tone) {
        StatusTone.Neutral -> KitIcons.radioEmpty
        StatusTone.Progress -> KitIcons.sync
        StatusTone.Ok -> KitIcons.check
        StatusTone.Attention -> KitIcons.warning
        StatusTone.Failure -> KitIcons.error
    }

/** The kit's one status tone map (Dart `KitTokens.toneFor`): amber is for needs-you only (LOOK-4, LOOK-5). */
internal fun toneFor(tone: StatusTone): TextTone =
    when (tone) {
        StatusTone.Neutral -> TextTone.Secondary
        StatusTone.Progress -> TextTone.Accent
        StatusTone.Ok -> TextTone.Success
        StatusTone.Attention,
        StatusTone.Failure -> TextTone.Primary
    }

/** [toneFor] resolved against [colors] (a notice's glyph uses it too). */
internal fun glyphColor(tone: StatusTone, colors: KitColors) = colors.tone(toneFor(tone))

/** A title-less notice's message size (P-014; read by `KitNotice`). */
internal val STATUS_MESSAGE_ROLE: TypeRole = TypeRole.Secondary
