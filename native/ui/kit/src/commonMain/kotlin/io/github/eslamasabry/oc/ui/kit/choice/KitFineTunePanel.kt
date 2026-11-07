package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal

/**
 * One knob of a [KitFineTunePanel] (Dart `KitFineTuneKnob`): its name, at most one short line on what it does
 * ([hint]), its choices as [KitSegment]s, the one in force and the style's own choice in words ([defaultNote],
 * "Default: Round"). [onChanged] null means a rule holds the knob, and then [disabledReason] says which.
 */
@Immutable
public class KitFineTuneKnob<T>(
    public val title: String,
    public val segments: List<KitSegment<T>>,
    public val selected: T,
    public val defaultNote: String,
    public val onChanged: ((T) -> Unit)?,
    public val hint: String? = null,
    public val disabledReason: String? = null,
) {
    init {
        // F: lib/ui/kit/kit_fine_tune_panel.dart:27-30 (a held knob says why)
        require(onChanged != null || disabledReason != null) {
            "KitFineTuneKnob.disabledReason is required when onChanged is null."
        }
    }

    /** The knob's [KitSegmented] (Dart `_control`). */
    @Composable
    internal fun Control() {
        val change = onChanged
        KitSegmented(
            segments = segments,
            selected = selected,
            semanticsLabel = title,
            enabled = if (change != null) KitEnabled.Enabled else KitEnabled.Disabled(disabledReason.orEmpty()),
            onChange = { change?.invoke(it) },
        )
    }
}

/** Pure rules of [KitFineTunePanel]. */
public object KitFineTuneRules {
    /** The reset action shows only while some knob differs from the style's own choice (the caller passes a reset). */
    public fun resetShown(onReset: (() -> Unit)?, disabledReason: String?): Boolean = disabledReason == null && onReset != null

    /** While [disabledReason] is set, the knobs give way to that one line. */
    public fun knobsShown(disabledReason: String?): Boolean = disabledReason == null
}

/**
 * Settings › Appearance › Fine-tune (Dart `KitFineTunePanel`): a few knobs over the chosen style (corners, depth,
 * density, edges), each a [KitSegmented] under its name with the style's own choice said in words at the end of the
 * name's line. "Reset to <style> defaults" unfolds under the knobs while any of them differs from the style ([onReset]
 * not null), and folds away once they match again ([KitReveal]; instant under reduced motion). While [disabledReason]
 * is set (Saver keeps surfaces solid), the knobs give way to that one line.
 *
 * States: default, disabled
 * Text: frame (the knobs), reading (the lines)
 * Cap: Reading
 * Motion: the reset unfolds STANDARD and folds QUICK (KitReveal); Off = instant
 * Replaces: KitFineTunePanel, KitFineTuneKnob (lib/ui/kit/kit_fine_tune_panel.dart)
 */
@Composable
public fun KitFineTunePanel(
    knobs: List<KitFineTuneKnob<*>>,
    resetLabel: String,
    onReset: (() -> Unit)?,
    modifier: Modifier = Modifier,
    disabledReason: String? = null,
) {
    kitAssert(resetLabel.isNotBlank()) { "KitFineTunePanel: the reset needs words" }
    val capped = modifier.kitMaxWidth(CapRole.Reading)
    if (disabledReason != null) {
        // The one line keeps its own width; the host places it (Dart `KitMaxWidth.reading(child: KitText)`).
        KitText(disabledReason, capped.testTag("kit-fine-tune-disabled"), role = TypeRole.Secondary)
        return
    }
    Column(capped.fillMaxWidth()) {
        knobs.forEachIndexed { i, knob ->
            if (i > 0) Spacer(Modifier.height(Space.S4.dp))
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    KitText(knob.title, Modifier.weight(1f), role = TypeRole.RowTitle)
                    Spacer(Modifier.width(Space.S2.dp))
                    // The style's own choice, at the end of the line.
                    // Dart `Flexible` + `Align(topEnd)`: the note takes its share and sits at the share's end.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                        KitText(knob.defaultNote, role = TypeRole.Secondary, textAlign = TextAlign.End)
                    }
                }
                knob.hint?.let { KitText(it, role = TypeRole.Secondary) }
                Spacer(Modifier.height(Space.S2.dp))
                knob.Control()
            }
        }
        KitReveal(onReset != null) {
            Row(Modifier.fillMaxWidth().padding(top = Space.S3.dp)) {
                KitButton(
                    label = resetLabel,
                    onClick = { onReset?.invoke() },
                    modifier = Modifier.testTag("kit-fine-tune-reset"),
                    role = KitButtonRole.Tertiary,
                    icon = KitIcons.restart,
                )
            }
        }
    }
}
