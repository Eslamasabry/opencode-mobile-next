package io.github.eslamasabry.oc.ui.kit.rb.o24

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.input.KitHeroQuestion
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import kotlin.test.Test

class KitHeroQuestionGateTest {
    @Composable private fun scene() {
        KitHeroQuestion("Where should your agent run?", Modifier.padding(24.dp))
    }
    @Test fun kit_hero_question_default() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_hero_question_default", modes = listOf("dark", "light")) { scene() } }
    @Test fun kit_hero_question_default_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_hero_question_default_1280x800", modes = listOf("dark", "light")) { scene() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_hero_question", caps = mapOf("KitHeroQuestion" to "reading")) { KitHeroQuestion("Where should your agent run?") } }
}
