package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture

/** One golden stem in [modes], rendered the way kitGalleryPart captures it (view DPR 3, image at 1x). */
internal fun gate23(name: String, vararg modes: String = arrayOf("light", "dark"), content: @Composable () -> Unit) {
    FlutterGoldenGate.check(name = name, modes = modes.toList(), wrap = { FlutterCapture(it) }, content = content)
}
