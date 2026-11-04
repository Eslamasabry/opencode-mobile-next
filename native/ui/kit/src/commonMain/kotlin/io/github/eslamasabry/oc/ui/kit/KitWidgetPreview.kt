package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** A widget style sample rendered with the saved widget's palette and density. */
@Composable
public fun KitWidgetPreview(
    title: String,
    background: String,
    accent: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    val app = LocalKitColors.current
    val swatch =
        when (accent) {
            "green" -> Color(0xFF48AC6F)
            "teal" -> Color(0xFF258783)
            "blue" -> Color(0xFF4187CD)
            "amber" -> Color(0xFFBA8420)
            "wallpaper" -> lerp(app.accent, app.text1, .4f)
            else -> app.accent
        }
    val ground =
        when (background) {
            "transparent" -> Color.Transparent
            "tinted" -> lerp(app.ground, swatch, .12f)
            else -> app.ground
        }
    val colors =
        app.copy(
            ground = ground,
            surface1 = ground,
            text1 = app.text1,
            accent = swatch,
        )
    CompositionLocalProvider(LocalKitColors provides colors) {
        Column(
            modifier
                .fillMaxWidth()
                .background(ground, RoundedCornerShape(16.dp))
                .border(1.dp, swatch, RoundedCornerShape(16.dp))
                .padding(if (compact) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        ) {
            KitText(title, role = TypeRole.Title, kind = KitTextKind.Frame)
            if (wide) {
                repeat(3) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(swatch.copy(alpha = .3f)))
                    Box(
                        Modifier.width((140 - it * 20).dp)
                            .height(8.dp)
                            .background(colors.text1.copy(alpha = .2f))
                    )
                }
            }
        }
    }
}
