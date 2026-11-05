package io.github.eslamasabry.oc.ui.kit.rb.o04

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import kotlin.test.Test

/** kit_style_theme.dart has no golden of its own; the theme is exercised through the matrix (every Style x mode). */
class KitStyleThemeGateTest {
    private val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            for (level in SurfaceLevel.entries) {
                KitSurface(Modifier.fillMaxWidth(), level = level, padding = KitSurfacePadding.None) {
                    KitText(level.name, role = TypeRole.Label)
                }
            }
        }
    }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_style_theme", content = content) }
}
