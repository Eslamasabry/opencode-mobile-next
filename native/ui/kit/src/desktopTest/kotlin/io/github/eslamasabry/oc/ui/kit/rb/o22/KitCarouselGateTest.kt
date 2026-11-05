package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.frame.KitCarousel
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test

private class Swatch(val label: String, val ground: Color, val accent: Color, val partner: Color)

private val swatches = listOf(
    Swatch("Default", Color(0xFF14181F), Color(0xFF3DDC84), Color(0xFF8AB4F8)),
    Swatch("Catppuccin", Color(0xFF181825), Color(0xFFCBA6F7), Color(0xFFF5C2E7)),
    Swatch("Gruvbox", Color(0xFF1D2021), Color(0xFFFE8019), Color(0xFFFABD2F)),
    Swatch("Nord", Color(0xFF2E3440), Color(0xFF88C0D0), Color(0xFFEBCB8B)),
    Swatch("Dracula", Color(0xFF282A36), Color(0xFFBD93F9), Color(0xFFFF79C6)),
)

/** Flutter `KitOptionCard.theme`: a ground tile with a stripe and a dot at the foot, the name under it. */
@Composable
private fun ThemeCard(s: Swatch, selected: Boolean) {
    val colors = LocalKitColors.current
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(s.ground, RoundedCornerShape(16.dp))
                .border(if (selected) 1.dp else 0.dp, if (selected) colors.accent else Color.Transparent, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).size(20.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(12.dp)) {
                        val w = 1.6.dp.toPx()
                        drawLine(colors.onAccent, Offset(size.width * .1f, size.height * .55f), Offset(size.width * .4f, size.height * .85f), w, StrokeCap.Round)
                        drawLine(colors.onAccent, Offset(size.width * .4f, size.height * .85f), Offset(size.width * .95f, size.height * .2f), w, StrokeCap.Round)
                    }
                }
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(.72f).height(6.dp).background(s.accent, RoundedCornerShape(4.dp)))
                }
                Box(Modifier.padding(start = 8.dp).size(12.dp).background(s.partner, CircleShape))
            }
        }
        Box(Modifier.padding(start = 4.dp, top = 8.dp)) {
            BasicText(s.label, style = TextStyle(color = colors.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }
    }
}

/** test/goldens/kit/kit_carousel_golden_test.dart: five cards (dots), opened on the second. */
class KitCarouselGateTest {
    @Test fun five() {
        FlutterGoldenGate.check("kit_carousel_five") {
            Box(Modifier.padding(vertical = 16.dp).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                KitCarousel("Themes", 5, 168f, initialIndex = 1) { i ->
                    ThemeCard(swatches[i % swatches.size], selected = i == 1)
                }
            }
        }
    }
}
