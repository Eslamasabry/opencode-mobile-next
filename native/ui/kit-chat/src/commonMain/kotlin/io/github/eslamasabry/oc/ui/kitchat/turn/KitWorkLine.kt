package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.SignalPalette
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.icon.KitStatusIcon
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepTimeline
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitWorkSeparator
import io.github.eslamasabry.oc.ui.words.res.signalHide
import io.github.eslamasabry.oc.ui.words.res.signalShow
import org.jetbrains.compose.resources.stringResource

/**
 * Where a turn's work stands (Dart `KitWorkState`, KIT-12). The host decides; the line only says
 * it. EndedFailed opens by default ([opensByDefault]); WaitingForYou is never a spinner.
 */
public enum class KitWorkState {
    Running,
    WaitingForYou,
    Done,
    EndedFailed,
    Stopped;

    /** Dart `KitWorkLine.opensByDefault`: only work that ended on a failure opens by itself. */
    public val opensByDefault: Boolean
        get() = this == EndedFailed
}

/**
 * Dart `KitWorkLine.stepCap`: an open line shows at most this many steps before "Show earlier
 * steps".
 */
public const val KIT_WORK_STEP_CAP: Int = 25

/**
 * The one line a turn's work folds under (Dart `KitWorkLine`, lib/ui/kit/chat/kit_work_line.dart; plan 09
 * chat-transcript-turn-model: "work folded under ONE line"), drawn as a [KitStepTimeline]. [label] is the summary in
 * the caller's words ("Read 3 files · edited 1"); [now] is the live step's words, shown while the work runs and the
 * line is shut; [stateWords] is how it stands, read with it and, for stopped and failed work, said after the summary.
 * [icon] leads the line (see `kitWorkIcon`). Tapping the line or the screen reader's expand / collapse action asks
 * [onToggle]; the caller owns [expanded]. [steps] hang off the rail while open; [earlier] is "Show N earlier steps".
 * No frame, no fill, no page gutter of its own (the transcript gives it the 16 dp edge): work is quiet.
 */
@Composable
public fun KitWorkLine(
    label: String,
    stateWords: String?,
    state: KitWorkState,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    earlier: KitLineAction? = null,
    icon: ImageVector = KitIcons.tools,
    now: String? = null,
    steps: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().widthIn(max = 700.dp)) { // F: lib/ui/kit/kit_layout.dart:48
        if (LocalKitAppearance.current.style == KitSurfaceStyle.Signal) {
            val colors = LocalKitColors.current
            val palette = SignalPalette.of(colors.dark)
            KitTappable(
                onClick = { onToggle(!expanded) },
                modifier = Modifier.fillMaxWidth().semantics { stateDescription = stateWords ?: label },
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .drawBehind {
                            val line = Color(palette.track.v)
                            for (y in listOf(0f, size.height)) drawLine(
                                line,
                                Offset(0f, y),
                                Offset(size.width, y),
                                1.5.dp.toPx(),
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(3.dp.toPx(), 3.dp.toPx())
                                    ),
                            )
                        }
                        .padding(vertical = 8.dp), // NV: the Signal line keeps its 8 dp band; the page gutter is the host's now
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        when (state) {
                            KitWorkState.Running,
                            KitWorkState.WaitingForYou -> {
                                drawRoundRect(
                                    Color(palette.head.v),
                                    cornerRadius =
                                        androidx.compose.ui.geometry.CornerRadius(size.width / 2f),
                                )
                                drawCircle(
                                    Color(
                                        if (state == KitWorkState.WaitingForYou)
                                            SignalPalette.LampRed.v
                                        else SignalPalette.LampGreen.v
                                    ),
                                    radius = size.minDimension * .3f,
                                )
                            }
                            else ->
                                for (y in listOf(.2f, .5f, .8f)) drawLine(
                                    Color(palette.track.v),
                                    Offset(0f, size.height * y),
                                    Offset(size.width, size.height * y),
                                    2.dp.toPx(),
                                )
                        }
                    }
                    KitText(
                        label,
                        Modifier.weight(1f).liveLabel(state),
                        role = TypeRole.Mono,
                        maxLines = 2,
                    )
                    KitText(
                        stringResource(
                                if (expanded) Res.string.signalHide else Res.string.signalShow
                            )
                            .uppercase(),
                        role = TypeRole.Caption,
                    )
                }
            }
            if (expanded) {
                if (earlier != null)
                    KitButton(earlier.label, earlier.onClick, role = KitButtonRole.Tertiary)
                steps()
            }
        } else {
            // Flutter's work line (kit_work_line.dart `_KitWorkLineState._buildContent`): a KitStepTimeline whose line
            // says the summary (or, folded while a step runs, the live step), with the failed mark in the glyph's
            // place when the work ended on a failure and the step light on the glyph while a step runs and the
            // line is shut. The state words are what TalkBack adds.
            val sep = stringResource(Res.string.kitWorkSeparator)
            val shown =
                when (state) {
                    KitWorkState.Running -> if (expanded) label else now ?: label
                    KitWorkState.WaitingForYou -> stateWords ?: label
                    KitWorkState.Done -> label
                    KitWorkState.EndedFailed,
                    KitWorkState.Stopped -> if (stateWords == null) label else "$label$sep$stateWords"
                }
            KitStepTimeline(
                label = shown,
                icon = icon,
                expanded = expanded,
                onPressed = { onToggle(!expanded) },
                spoken = stateWords,
                mark =
                    if (state == KitWorkState.EndedFailed) {
                        { KitStatusIcon(StatusTone.Failure) }
                    } else null,
                live = state == KitWorkState.Running && !expanded,
                head =
                    if (earlier == null) null
                    else {
                        { KitButton(earlier.label, earlier.onClick, role = KitButtonRole.Tertiary) }
                    },
                lineModifier = Modifier.liveLabel(state),
                steps = steps,
            )
        }
    }
}

/**
 * P-011: the work line's one icon column. The summary's mark and every step's glyph ([KitToolRow]) take this size and
 * are followed by [WORK_GAP], so an opened group has one start edge (16 dp), one icon column and one text edge. The box
 * follows the person's text size (system × in-app), capped so the line stays one row.
 */
@Composable
internal fun workMark(): Dp {
    val fontScale = (LocalKitAppDensity.current ?: LocalDensity.current).fontScale
    return (WORK_MARK * minOf(fontScale, WORK_MARK_CAP)).dp
}

/** The work line's mark at text size 1.0. */
internal const val WORK_MARK: Float = 16f

/** How far the mark grows with the text. */
private const val WORK_MARK_CAP: Float = 1.4f

/** The space between a mark (or a step's glyph) and its words: one kit step. */
internal const val WORK_GAP: Int = Space.S2

/** A running line is a polite live region (the step changed); a settled one is just text. */
private fun Modifier.liveLabel(state: KitWorkState): Modifier =
    if (state == KitWorkState.Running) semantics { liveRegion = LiveRegionMode.Polite } else this
