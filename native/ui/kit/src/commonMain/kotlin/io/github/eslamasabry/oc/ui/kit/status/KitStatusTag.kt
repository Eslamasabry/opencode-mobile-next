package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
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
    val plain = tone == KitStatusTagTone.Failed
    val fill = if (plain) Color.Transparent else base.copy(alpha = .18f).compositeOver(c.surface1) // F: lib/ui/kit/kit_status_tag.dart:56
    val ink = if (plain) c.text2 else Color(
        RoleDerivation.readableOn(Argb(base.toArgb()), listOf(Argb(fill.toArgb())), 4.7, Argb(c.text1.toArgb())).v, // F: lib/ui/kit/kit_status_tag.dart:59
    )
    val shape = RoundedCornerShape(Space.S3.dp)
    val look = if (plain) null else kitSurfaceLook(
        KitSurfaceInput(role = SurfaceRole.Control, shape = KitShapeId.Button, ownFill = null, color = fill),
    )
    val surface = when {
        look == null -> Modifier.background(fill, shape)
        look.modifier === Modifier -> Modifier.background(fill, shape) // Solid: the tag's own tint and 12 dp corner
        else -> look.modifier
    }
    val style = LocalKitTypography.current.style(TypeRole.Caption).copy(color = ink)
    KitSwap(label to tone, modifier.kitMaxWidth(CapRole.Chip), alignment = Alignment.CenterStart) { (word, _) ->
        BasicText(
            word,
            Modifier.then(surface).padding(horizontal = Space.S2.dp, vertical = Space.S1.dp),
            style = style,
            maxLines = if (plain) Int.MAX_VALUE else 1,
            overflow = if (plain) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}
