package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
    // One rule for every status word in the kit: a dot in the status colour (grey when done or failed), then the word in
    // plain secondary text.
    val dot = when (tone) {
        KitStatusTagTone.NeedsYou -> c.attention
        KitStatusTagTone.Running -> c.accent
        KitStatusTagTone.Done, KitStatusTagTone.Failed -> c.text3
    }
    val style = LocalKitTypography.current.style(TypeRole.Caption).copy(color = c.text2)
    KitSwap(label to tone, modifier.kitMaxWidth(CapRole.Chip), alignment = Alignment.CenterStart) { (word, _) ->
        Row(Modifier.padding(vertical = Space.S1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            Box(Modifier.size(Space.S2.dp).background(dot, CircleShape).testTag("kit-status-dot"))
            BasicText(word, Modifier.weight(1f, fill = false), style = style, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
        }
    }
}
