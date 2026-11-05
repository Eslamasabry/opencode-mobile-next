package io.github.eslamasabry.oc.ui.kit.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Builds one Phosphor glyph: the 256 × 256 artwork scaled to a 24 dp box, filled in one colour (the caller tints it).
 * Each part is `path data to alpha`; duotone glyphs carry a 0.2 background layer. [mirror] glyphs point along the
 * reading direction and flip in RTL through the vector's own auto-mirror (B-KIT-001), so callers never flip them.
 */
internal fun phosphor(name: String, mirror: Boolean, vararg parts: Pair<String, Float>): ImageVector =
    ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f,
            autoMirror = mirror,
        )
        .apply { parts.forEach { (d, alpha) -> addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black), fillAlpha = alpha) } }
        .build()
