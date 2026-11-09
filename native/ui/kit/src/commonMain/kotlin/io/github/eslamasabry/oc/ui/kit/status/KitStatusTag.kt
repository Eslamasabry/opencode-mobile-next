package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import androidx.compose.foundation.text.BasicText

/** Which state a [KitStatusTag] names (Dart `KitStatusTagTone`). */
public enum class KitStatusTagTone { NeedsYou, Running, Done, Failed }

/**
 * A small worded tag at the end of a feed row ("Needs you", "Running", "Done", "Failed"). A fact, not a control: it
 * never takes a tap and always carries its word. (Dart `KitStatusTag`)
 *
 * States: none, a fixed word in one of four tones Text: Caption Cap: Chip Motion: cross-fade on a change of tone or
 * word Hidden: none Replaces: KitStatusTag (lib/ui/kit/kit_status_tag.dart)
 */
@Composable
public fun KitStatusTag(label: String, tone: KitStatusTagTone, modifier: Modifier = Modifier) {
    val c = LocalKitColors.current
    val base = when (tone) {
        KitStatusTagTone.NeedsYou -> c.attention
        KitStatusTagTone.Running -> c.accent
        KitStatusTagTone.Done, KitStatusTagTone.Failed -> c.text2 // F: kit_status_tag.dart:49 calm, not a second green
    }
    // C03 (audit 2026-11-09): never a tinted capsule. The word alone, in the tone's ink where colour carries meaning
    // (needs you, running) and in calm secondary text otherwise.
    val ink = if (tone == KitStatusTagTone.Done || tone == KitStatusTagTone.Failed) c.text2 else Color(
        RoleDerivation.readableOn(Argb(base.toArgb()), listOf(Argb(c.surface1.toArgb())), 4.7, Argb(c.text1.toArgb())).v, // F: lib/ui/kit/kit_status_tag.dart:59
    )
    val style = LocalKitTypography.current.style(TypeRole.Caption).copy(color = ink)
    KitSwap(label to tone, modifier.kitMaxWidth(CapRole.Chip), alignment = Alignment.CenterStart) { (word, _) ->
        BasicText(
            word,
            Modifier.padding(vertical = Space.S1.dp),
            style = style,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
        )
    }
}
