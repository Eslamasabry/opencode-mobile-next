package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.teaching.KitGuide
import io.github.eslamasabry.oc.ui.kit.teaching.KitGuideGroup
import io.github.eslamasabry.oc.ui.kit.teaching.KitGuideItem
import io.github.eslamasabry.oc.ui.kit.teaching.KitGuideNext
import io.github.eslamasabry.oc.ui.kit.teaching.KitGuideQuestion
import kotlin.test.Test

private val groups = listOf(
    KitGuideItem(id = "colours", label = "Colours", value = "Dark, Nord"),
    KitGuideItem(id = "style", label = "Style", value = "Glass", next = true),
    KitGuideItem(id = "background", label = "Background", value = "Off in Saver", held = true),
    KitGuideItem(id = "comfort", label = "Text & motion", value = "Default, full motion"),
)

private val questions = listOf(
    KitGuideItem(id = "mode", label = "Mode", value = "Dark", done = true),
    KitGuideItem(id = "theme", label = "Theme", value = "Nord"),
    KitGuideItem(id = "accent", label = "Accent", value = "Match theme", next = true),
)

/** test/goldens/kit/kit_guide_golden_test.dart `_guide(state, height)`. */
@Composable
private fun guide(state: String, height: Int) {
    Box(Modifier.height(height.dp)) {
        when (state) {
            "question" -> KitGuide(
                items = questions,
                selected = "theme",
                onItem = {},
                group = KitGuideGroup(label = "Colours", onBack = {}),
                question = KitGuideQuestion(
                    id = "theme",
                    title = "Which colours?",
                    changes = "The colours of every page",
                ) {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        KitSegmented(
                            semanticsLabel = "Theme",
                            selected = 1,
                            segments = listOf(KitSegment(0, "OpenCode"), KitSegment(1, "Nord"), KitSegment(2, "Dracula")),
                            onChange = {},
                        )
                    }
                },
                next = KitGuideNext(count = "2 of 10", label = "Next: Accent", onPressed = {}, ready = true),
            )
            "returning" -> KitGuide(
                items = groups.map { KitGuideItem(id = it.id, label = it.label, value = it.value) },
                onItem = {},
                home = { Box(Modifier.padding(16.dp)) { KitText("Changed last: Theme", role = TypeRole.Secondary) } },
            )
            else -> KitGuide(
                items = groups,
                onItem = {},
                home = {
                    Box(Modifier.padding(16.dp)) {
                        KitText("Pick up where you left off, or tap any chip.", role = TypeRole.Secondary)
                    }
                },
                next = KitGuideNext(count = "4 of 10 seen", label = "Continue: Style", onPressed = {}, ready = true),
            )
        }
    }
}

class KitGuideGateTest {
    @Test fun kit_guide_default() { FlutterGoldenGate.check("kit_guide_default") { guide("default", 820) } }

    @Test fun kit_guide_question() { FlutterGoldenGate.check("kit_guide_question") { guide("question", 820) } }

    @Test fun kit_guide_returning() { FlutterGoldenGate.check("kit_guide_returning") { guide("returning", 820) } }

    /** The wide golden renders the question, 700 high (the Dart test's own choice). */
    @Test fun kit_guide_default_1280x800() {
        FlutterGoldenGate.check("kit_guide_default_1280x800") { guide("question", 700) }
    }

    @Test fun kit_guide_default_text2() {
        FlutterGoldenGate.check("kit_guide_default_text2", modes = listOf("dark")) { guide("question", 915 - 64) }
    }

    @Test fun kit_guide_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_guide_default_text2_1280x800", modes = listOf("dark")) { guide("question", 800 - 64) }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_guide", caps = mapOf("KitGuide" to "fill")) { guide("question", 820) }
    }
}
