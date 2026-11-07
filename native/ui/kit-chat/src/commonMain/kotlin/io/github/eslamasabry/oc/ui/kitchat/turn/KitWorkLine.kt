package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.design.color.SignalPalette
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
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
 * The one line a turn's work folds under (Dart `KitWorkLine`, plan 09 chat-transcript-turn-model:
 * "work folded under ONE line"). [label] is the summary in the caller's words ("Read 3 files ·
 * edited 1", or what is happening now while it runs); [state] reads out with it. Tapping the line
 * (ripple, 48 dp row) or the screen reader's expand / collapse action asks [onToggle]; the caller
 * owns [expanded]. [steps] are drawn only while open, revealed with the kit's one layout motion
 * (reduced motion: instant). [earlier] is "Show N earlier steps" when the open line hides older
 * ones.
 *
 * Text: reading role through [KitExpandRow] (frame-capped by the kit); no frame, no fill: work is
 * quiet.
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
    steps: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().widthIn(max = 700.dp).semantics {
            stateDescription = stateWords ?: label
        }
    ) {
        if (LocalKitAppearance.current.style == KitSurfaceStyle.Signal) {
            val colors = LocalKitColors.current
            val palette = SignalPalette.of(colors.dark)
            KitTappable(onClick = { onToggle(!expanded) }, modifier = Modifier.fillMaxWidth()) {
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
            // Flutter's work line: one quiet line, a mark for where the work stands, the summary in the supporting
            // grey and a caret at the end; no frame, no fill. The state words are what TalkBack adds.
            val colors = LocalKitColors.current
            val iconSize = workMark()
            KitTappable(
                onClick = { onToggle(!expanded) },
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        if (expanded) collapse { onToggle(false); true } else expand { onToggle(true); true }
                    },
                role = KitTappableRole.Row,
                shape = KitShapeId.Tile,
            ) {
                Row(
                    // On the reply's own edge (16 dp, like its words): the mark and the caret never touch the screen.
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WORK_GAP.dp),
                ) {
                    when (state) {
                        KitWorkState.Running -> Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(8.dp).background(colors.live, CircleShape))
                        }
                        KitWorkState.WaitingForYou -> Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(8.dp).background(colors.attention, CircleShape))
                        }
                        KitWorkState.EndedFailed -> KitIcon(KitIcons.warning, size = KitIconSize.Tiny, tone = TextTone.Secondary, growsWithText = true)
                        KitWorkState.Stopped -> KitIcon(KitIcons.stopCircle, size = KitIconSize.Tiny, tone = TextTone.Secondary, growsWithText = true)
                        KitWorkState.Done -> KitIcon(KitIcons.fileText, size = KitIconSize.Tiny, tone = TextTone.Secondary, growsWithText = true)
                    }
                    // One live line: when the step changes the new words fade in over the old (QUICK; instant
                    // under reduced motion). While running it is one polite live region, so the screen reader
                    // says the new step once, never per token.
                    KitSwap(label, Modifier.weight(1f).liveLabel(state), alignment = Alignment.CenterStart) { shown ->
                        KitText(
                            shown,
                            Modifier.fillMaxWidth(),
                            role = TypeRole.Secondary,
                            tone = if (state == KitWorkState.Running) TextTone.Primary else TextTone.Secondary,
                            kind = KitTextKind.Reading,
                            maxLines = 1,
                        )
                    }
                    KitIcon(if (expanded) KitIcons.chevronUp else KitIcons.chevronDown, size = KitIconSize.Tiny, tone = TextTone.Secondary, growsWithText = true)
                }
            }
            KitReveal(visible = expanded) {
                // P-011: the steps sit on the line's own 16 dp edge, their glyphs in the summary mark's column and their
                // names on the summary's text edge; no extra indent of their own.
                Column(Modifier.fillMaxWidth()) {
                    if (earlier != null)
                        KitButton(earlier.label, earlier.onClick, role = KitButtonRole.Tertiary)
                    steps()
                }
            }
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
