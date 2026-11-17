package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What the message box's recorder row is doing. */
public enum class KitRecorderStage {
    /** The microphone is being opened: the words say so, ✕ and ✓ already work. */
    Starting,

    /** Listening: the live waveform moves with the voice, the time runs. */
    Listening,

    /** The finger is on the mic (hold to talk): no buttons, the words say what letting go does. */
    Held,

    /** The words are being written down: the waveform settles into a calm progress and the stage is named. */
    Transcribing,
}

/**
 * The message box while the person dictates (owner 2026-11-17, "the box itself becomes the recorder"): one row INSIDE
 * the box, never a second box. A round ✕ at the start ([cancel]: the words said are thrown away), the live waveform
 * drawn from [level] with the time ([elapsed]) beside it, and a round ✓ at the end ([accept]: the words land in the box,
 * editable). The box's own Send keeps its place and is the third action; nothing here is a text link.
 *
 * [status] names the real stage ("Listening…", "Writing down what you said…"); it is drawn while starting, held and
 * transcribing, and is always the row's polite live region, with [announcement] (the stage plus the time at sensible
 * steps) replacing it while listening so a screen reader hears the time without a tick every second.
 *
 * States: starting, listening, held (cancel armed = [attention]), transcribing
 * Text: frame (the status and the time; at most 1.3)
 * Cap: the box's own (it lives inside the message box)
 * Motion: the waveform scrolls with each level the microphone reports (no frame loop; still in silence); transcribing
 *   settles the bars to a flat line on QUICK at Full and Calm, instantly at Off. The row's arrival is the box's.
 */
@Composable
public fun KitVoiceRecorderRow(
    stage: KitRecorderStage,
    status: String,
    modifier: Modifier = Modifier,
    elapsed: String? = null,
    announcement: String? = null,
    level: (() -> Float)? = null,
    attention: Boolean = false,
    cancel: KitLineAction? = null,
    accept: KitLineAction? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = tokens.minTarget.dp)
            .testTag(VOICE_RECORDER_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cancel != null) RecorderDisc(cancel, KitIcons.close, TextTone.Secondary, VOICE_RECORDER_CANCEL_TAG)
        // The spoken half: one polite live region with the stage (and, while listening, the time at sensible steps).
        val spoken = if (stage == KitRecorderStage.Listening) announcement ?: status else status
        Row(
            Modifier.weight(1f)
                .padding(horizontal = Space.S2.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = spoken
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            KitSwap(stage == KitRecorderStage.Transcribing, Modifier.weight(1f)) { writing ->
                if (writing) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    ) {
                        if (KitMotion.state.ambientLoops) KitWorkingMark(colors.accent, size = tokens.smallIcon.dp)
                        KitText(
                            status,
                            Modifier.weight(1f).testTag(VOICE_RECORDER_STATUS_TAG).clearAndSetSemantics {},
                            role = TypeRole.Secondary,
                            tone = TextTone.Secondary,
                            kind = KitTextKind.Frame,
                            maxLines = 2,
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    ) {
                        // Listening shows the voice itself; the other stages say what is happening first.
                        if (stage != KitRecorderStage.Listening)
                            KitText(
                                status,
                                Modifier.weight(1f, fill = false).testTag(VOICE_RECORDER_STATUS_TAG).clearAndSetSemantics {},
                                role = TypeRole.Secondary,
                                tone = if (attention) TextTone.Attention else TextTone.Secondary,
                                kind = KitTextKind.Frame,
                                maxLines = 2,
                            )
                        KitVoiceWave(
                            level = level.takeIf { stage != KitRecorderStage.Starting },
                            attention = attention,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (elapsed != null && stage != KitRecorderStage.Transcribing)
                KitText(
                    elapsed,
                    Modifier.testTag(VOICE_RECORDER_TIME_TAG),
                    role = TypeRole.Caption,
                    tone = if (attention) TextTone.Attention else TextTone.Secondary,
                    kind = KitTextKind.Frame,
                    maxLines = 1,
                    tabular = true,
                )
        }
        if (accept != null) RecorderDisc(accept, KitIcons.check, TextTone.Accent, VOICE_RECORDER_ACCEPT_TAG)
    }
}

/**
 * The live waveform: one bar per slice of time, newest at the reading end, each as tall as the voice was loud. Drawn in
 * the draw phase from a small ring of samples; a new sample (the microphone reports about thirty levels a second, three
 * make one bar) invalidates only the drawing. With no [level] (starting) the bars lie flat and quiet.
 */
@Composable
public fun KitVoiceWave(level: (() -> Float)?, modifier: Modifier = Modifier, attention: Boolean = false) {
    val colors = LocalKitColors.current
    val ring = remember { WaveRing() }
    val drawn = remember { mutableIntStateOf(0) }
    val current by rememberUpdatedState(level)
    LaunchedEffect(level != null) {
        if (level == null) return@LaunchedEffect
        snapshotFlow { current?.invoke() ?: 0f }.collect { value ->
            if (ring.add(value)) drawn.intValue += 1
        }
    }
    // Settles to a flat line when the level goes away (transcribing), on the kit's quick pace; instant at Off.
    val live by
        animateFloatAsState(
            if (level != null) 1f else 0f,
            if (KitMotion.state.reduced) androidx.compose.animation.core.snap() else KitMotion.specs.quick(),
            label = "kit.voice.wave",
        )
    val ink = if (attention) colors.attentionLine else colors.accent
    val quiet = colors.text3
    Box(
        modifier
            .height(FlutterKitTokens.METER_BAR_MAX.dp)
            .testTag(VOICE_WAVE_TAG)
            .clearAndSetSemantics {}
            .drawBehind {
                // Read so a new sample redraws (and only redraws).
                if (drawn.intValue < 0) return@drawBehind
                val bar = (FlutterKitTokens.METER_BAR_WIDTH / 2f).dp.toPx().roundToInt().coerceAtLeast(2).toFloat()
                val gap = (Space.S1 / 2f).dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
                val count = ((size.width + gap) / (bar + gap)).toInt().coerceIn(0, WaveRing.SIZE)
                val floor = bar
                val rtl = layoutDirection == LayoutDirection.Rtl
                for (i in 0 until count) {
                    // i = 0 is the newest sample; it sits at the reading end.
                    val sample = ring.ago(i) * live
                    val h = (floor + (size.height - floor) * sample).coerceAtLeast(floor)
                    val fromEnd = i * (bar + gap)
                    val x = if (rtl) fromEnd else size.width - bar - fromEnd
                    drawRoundRect(
                        color = if (sample > 0.02f) ink else quiet,
                        topLeft = Offset(x, (size.height - h) / 2f),
                        size = Size(bar, h),
                        cornerRadius = CornerRadius(bar / 2f, bar / 2f),
                    )
                }
            }
    )
}

/** The last [SIZE] bars, each the loudest of [PER_BAR] reported levels, shaped so a quiet voice still shows. */
internal class WaveRing {
    private val samples = FloatArray(SIZE)
    private var head = 0
    private var pending = 0f
    private var seen = 0

    /** Adds one reported level; true when it completed a bar (the drawing then changes). */
    fun add(level: Float): Boolean {
        val clean = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
        pending = maxOf(pending, clean)
        seen += 1
        if (seen < PER_BAR) return false
        head = (head + 1) % SIZE
        samples[head] = sqrt(pending)
        pending = 0f
        seen = 0
        return true
    }

    /** The bar [steps] back from the newest (0 = newest). */
    fun ago(steps: Int): Float = samples[((head - steps) % SIZE + SIZE) % SIZE]

    companion object {
        const val SIZE: Int = 64
        const val PER_BAR: Int = 3
    }
}

/**
 * One of the recorder's round buttons: the composer's 40 dp disc (as Send's) in a 48 dp target, on the style's control
 * face nested in the box (Brutal's edge, never a second shadow). Only Send is filled with the accent, so ✓ and Send never
 * look alike: ✓ is the accent glyph on the quiet disc.
 */
@Composable
private fun RecorderDisc(action: KitLineAction, icon: ImageVector, tone: TextTone, tag: String) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                role = SurfaceRole.Control,
                shape = KitShapeId.Circle,
                level = SurfaceLevel.Surface2,
                // Inside the lifted box (dark) the controls sit one step down, as the box's chips do.
                ownFill = if (colors.dark) colors.surface2 else colors.surface3,
                nested = true,
                disabled = !action.enabled,
            )
        )
    val ink = if (!action.enabled) colors.text3 else colors.tone(tone)
    Box(Modifier.size(tokens.minTarget.dp).testTag(action.testTag ?: tag), contentAlignment = Alignment.Center) {
        Box(Modifier.size(FlutterKitTokens.COMPOSER_ACTION_SIZE.dp).then(look.modifier))
        KitTappable(
            onClick = action.onClick,
            modifier = Modifier.size(tokens.minTarget.dp).semantics { contentDescription = action.semanticsLabel ?: action.label },
            enabled = action.enabled,
            disabledReason = action.disabledReason,
            shape = KitShapeId.Circle,
            ink = tone,
        ) {
            Image(
                icon,
                contentDescription = null,
                modifier = Modifier.size(tokens.smallIcon.dp).align(Alignment.Center),
                colorFilter = ColorFilter.tint(ink),
            )
        }
    }
}

/** Test handles. */
public const val VOICE_RECORDER_TAG: String = "kit-voice-recorder"
public const val VOICE_RECORDER_CANCEL_TAG: String = "kit-voice-recorder-cancel"
public const val VOICE_RECORDER_ACCEPT_TAG: String = "kit-voice-recorder-accept"
public const val VOICE_RECORDER_STATUS_TAG: String = "kit-voice-recorder-status"
public const val VOICE_RECORDER_TIME_TAG: String = "kit-voice-recorder-time"
public const val VOICE_WAVE_TAG: String = "kit-voice-wave"
