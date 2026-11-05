package io.github.eslamasabry.oc.ui.kit.rb.o06

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity

/** kitGalleryPart / kitGalleryShot: view DPR 3, toImage(pixelRatio: 1). This keeps both axes independent. */
@Composable
internal fun FlutterCapture(content: @Composable () -> Unit) {
    val output = LocalDensity.current
    val ratio = 3
    val view = Density(output.density * ratio, output.fontScale)
    Layout(content = { CompositionLocalProvider(LocalDensity provides view, LocalKitAppDensity provides view, content = content) }) { measurables, constraints ->
        fun up(v: Int): Int = if (v == Constraints.Infinity) v else v * ratio
        val input = Constraints(up(constraints.minWidth), up(constraints.maxWidth), up(constraints.minHeight), up(constraints.maxHeight))
        val child = measurables.single().measure(input)
        layout(child.width / ratio, child.height / ratio) {
            child.placeWithLayer(0, 0) {
                scaleX = 1f / ratio
                scaleY = 1f / ratio
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
