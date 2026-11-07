package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitPixelRatio
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMarkDone
import io.github.eslamasabry.oc.ui.words.res.kitMarkFailed
import io.github.eslamasabry.oc.ui.words.res.kitMarkPaused
import io.github.eslamasabry.oc.ui.words.res.kitMarkWaiting
import io.github.eslamasabry.oc.ui.words.res.kitMarkWorking
import io.github.eslamasabry.oc.ui.words.res.kitTaskNeedsYou
import io.github.eslamasabry.oc.ui.words.res.kitTaskStopped
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Where a task stands (Dart `KitTaskState`): a step's four states, one waiting on the person, one stopped early. */
public enum class KitTaskState { Waiting, Working, Done, Failed, NeedsYou, Stopped }

/** Pure rules of [KitTaskMark] (Dart `KitTaskMark.wordFor` and its paused assert). */
public object TaskMarkRules {
    /** Paused only applies to a waiting or working task (STATE-11). */
    public fun canPause(state: KitTaskState): Boolean = state == KitTaskState.Waiting || state == KitTaskState.Working

    /** Waiting · Working · Done · Failed · Needs you · Stopped · Paused. */
    public fun word(state: KitTaskState, paused: Boolean = false): StringResource {
        require(!paused || canPause(state)) { "KitTaskMark: paused only applies to waiting or working" }
        if (paused) return Res.string.kitMarkPaused
        return when (state) {
            KitTaskState.Waiting -> Res.string.kitMarkWaiting
            KitTaskState.Working -> Res.string.kitMarkWorking
            KitTaskState.Done -> Res.string.kitMarkDone
            KitTaskState.Failed -> Res.string.kitMarkFailed
            KitTaskState.NeedsYou -> Res.string.kitTaskNeedsYou
            KitTaskState.Stopped -> Res.string.kitTaskStopped
        }
    }

    /** The waiting ring: text3, or text2 where text3 misses 3:1 on the ground or a surface (LOOK-8). */
    public fun ringColor(roles: KitColors): Color {
        val text3 = Argb(roles.text3.toArgb())
        val grounds = listOf(roles.ground, roles.surface1, roles.surface2, roles.surface3).map { Argb(it.toArgb()) }
        val kept = RoleDerivation.readableOn(text3, grounds, 3.0, toward = Argb(roles.text1.toArgb())) // F: lib/ui/kit/kit_status_mark.dart:78
        return if (kept == text3) roles.text3 else roles.text2
    }
}

/**
 * A task row's one leading mark (Dart `KitTaskMark`): the step marks for waiting, working, done and failed, a
 * question in the attention tone when the task needs the person, and a muted stop mark when it was cancelled, in the
 * 32 dp mark slot. Every mark carries its word: in semantics always, and as visible text after the mark with
 * [showLabel]. A change of state cross-fades on the quick pace (instant under reduced motion).
 *
 * The four step glyphs follow `kit_status_mark.dart` (order 05's `KitStatusMark`, not in native yet); once it lands,
 * the step states draw through it.
 *
 * States: waiting, working, done, failed, paused (waiting or working), needsYou, stopped
 * Text: chrome (secondary)
 * Cap: Glyph
 * Motion: cross-fade on a change of state (QUICK); Off = instant, working shows a still dot
 * Replaces: KitTaskMark (lib/ui/kit/kit_task_mark.dart)
 */
@Composable
public fun KitTaskMark(
    state: KitTaskState,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    label: String? = null,
    showLabel: Boolean = false,
) {
    val word = label ?: stringResource(TaskMarkRules.word(state, paused))
    val specs = KitMotion.specs
    val mark: @Composable (Modifier) -> Unit = { m ->
        AnimatedContent(
            targetState = state to paused,
            modifier = m,
            transitionSpec = { fadeIn(specs.quick()) togetherWith fadeOut(specs.quick()) },
            contentAlignment = Alignment.Center,
            label = "kit-task-mark",
        ) { (shown, held) -> TaskGlyph(shown, held) }
    }
    if (!showLabel) {
        mark(modifier.clearAndSetSemantics { contentDescription = word })
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        mark(Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(LocalKitTokens.current.labelGap.dp))
        KitText(word, Modifier.weight(1f, fill = false).clearAndSetSemantics { contentDescription = word }, role = TypeRole.Secondary)
    }
}

/** One state's glyph in the 32 dp slot. */
@Composable
private fun TaskGlyph(state: KitTaskState, paused: Boolean) {
    val roles = LocalKitColors.current
    val glyph = taskGlyphSize()
    Box(Modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp), contentAlignment = Alignment.Center) {
        when {
            paused -> KitGlyph(KitIcons.pause, roles.text2, glyph)
            state == KitTaskState.Done -> KitGlyph(KitIcons.check, roles.success, glyph)
            state == KitTaskState.Failed -> KitGlyph(KitIcons.error, roles.text1, glyph)
            state == KitTaskState.Working ->
                if (KitMotion.state.reduced) KitGlyph(KitIcons.statusDot, roles.accent, FlutterKitTokens.MARK_DOT_SIZE.dp)
                else KitWorkingMark(roles.accent, size = glyph, strokeWidth = FlutterKitTokens.SPINNER_STROKE.dp)
            state == KitTaskState.Waiting ->
                Box(
                    Modifier
                        .size(FlutterKitTokens.MARK_RING_SIZE.dp)
                        .border(kitHairline(), TaskMarkRules.ringColor(roles), CircleShape),
                )
            state == KitTaskState.NeedsYou -> KitGlyph(KitIcons.question, roles.attention, glyph)
            else -> KitGlyph(KitIcons.stopCircle, roles.text2, glyph)
        }
    }
}

/** The small icon grown with text up to the icon cap and snapped to whole physical pixels (LOOK-33). */
@Composable
private fun taskGlyphSize(): Dp {
    val scaled = LocalKitTokens.current.smallIcon * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    val ratio = kitPixelRatio()
    return if (ratio > 0f) ((scaled * ratio).roundToInt() / ratio).dp else scaled.dp
}
