package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.guide.KitGuideItem
import io.github.eslamasabry.oc.ui.kit.guide.KitGuideNextBar
import io.github.eslamasabry.oc.ui.kit.guide.KitGuideQuestionHeading
import io.github.eslamasabry.oc.ui.kit.guide.KitGuideRail
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitGuideAll
import io.github.eslamasabry.oc.ui.words.res.kitGuideBack
import io.github.eslamasabry.oc.ui.words.res.kitGuideChanges
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test

private val groups = listOf(
    KitGuideItem("colours", "Colours", "Dark, Nord"),
    KitGuideItem("style", "Style", "Glass", next = true),
    KitGuideItem("background", "Background", "Off in Saver", held = true),
    KitGuideItem("comfort", "Text & motion", "Default, full motion"),
)

private val questions = listOf(
    KitGuideItem("mode", "Mode", "Dark", done = true),
    KitGuideItem("theme", "Theme", "Nord"),
    KitGuideItem("accent", "Accent", "Match theme", next = true),
)

private enum class GuideState { Default, Question, Returning }

/**
 * test/goldens/kit/kit_guide_golden_test.dart `_guide(state, height)`: the start view (dot on Style, Background held,
 * "4 of 10 seen" / "Continue: Style"), the Colours group's Theme question ("Which colours?", Nord of three segments,
 * "2 of 10" / "Next: Accent") and the returning view (no marks, no bar). Phone shots are 820 tall; the 1280x800 default
 * golden shows the question at 700, and the text 2.0 goldens (dark only) the question at the window height less 64.
 *
 * There is no native `KitGuide` frame: the scene arranges the native guide parts (KitGuideRail, KitGuideQuestionHeading,
 * KitGuideNextBar) the way Dart's KitGuide.build does: rail 8 dp under the top on the start view, the body under it and
 * the bar at the foot; at text 1.3 and over everything but the bar scrolls as one page with a 4 dp top pad.
 */
class KitGuideGateTest {
    @Composable
    private fun Scene(state: GuideState, height: Int) {
        val tokens = LocalKitTokens.current
        val large = LocalDensity.current.fontScale >= 1.3f // F: lib/ui/kit/kit_guide.dart:196
        val rail: @Composable () -> Unit = {
            when (state) {
                GuideState.Question -> KitGuideRail(
                    questions,
                    onItem = {},
                    selected = "theme",
                    group = "Colours",
                    allLabel = stringResource(Res.string.kitGuideAll),
                    backLabel = stringResource(Res.string.kitGuideBack),
                    onBack = {},
                )
                GuideState.Returning -> Box(Modifier.padding(top = Space.S2.dp)) {
                    KitGuideRail(groups.map { KitGuideItem(it.id, it.label, it.value) }, onItem = {})
                }
                GuideState.Default -> Box(Modifier.padding(top = Space.S2.dp)) { KitGuideRail(groups, onItem = {}) }
            }
        }
        val body: @Composable ColumnScope.() -> Unit = {
            when (state) {
                GuideState.Question -> {
                    KitGuideQuestionHeading(
                        "Which colours?",
                        stringResource(Res.string.kitGuideChanges),
                        "The colours of every page",
                        Modifier.fillMaxWidth().padding(start = tokens.gutter.dp, end = tokens.gutter.dp, top = Space.S4.dp),
                    )
                    Spacer(Modifier.height(Space.S3.dp))
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        KitSegmented(
                            segments = listOf(KitSegment(0, "OpenCode"), KitSegment(1, "Nord"), KitSegment(2, "Dracula")),
                            selected = 1,
                            semanticsLabel = "Theme",
                            onChange = {},
                        )
                    }
                }
                GuideState.Returning -> Box(Modifier.padding(16.dp)) {
                    KitText("Changed last: Theme", role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
                GuideState.Default -> Box(Modifier.padding(16.dp)) {
                    KitText("Pick up where you left off, or tap any chip.", role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
            }
        }
        Column(Modifier.fillMaxWidth().height(height.dp)) {
            if (large) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Space.S1.dp)) {
                    rail()
                    body()
                }
            } else {
                rail()
                Column(Modifier.weight(1f).fillMaxWidth()) { body() }
            }
            when (state) {
                GuideState.Question -> KitGuideNextBar("2 of 10", "Next: Accent", onNext = {}, ready = true)
                GuideState.Default -> KitGuideNextBar("4 of 10 seen", "Continue: Style", onNext = {}, ready = true)
                GuideState.Returning -> Unit
            }
        }
    }

    @Test fun kit_guide_default() { FlutterGoldenGate.check("kit_guide_default") { Scene(GuideState.Default, 820) } }
    @Test fun kit_guide_question() { FlutterGoldenGate.check("kit_guide_question") { Scene(GuideState.Question, 820) } }
    @Test fun kit_guide_returning() { FlutterGoldenGate.check("kit_guide_returning") { Scene(GuideState.Returning, 820) } }

    // The Flutter wide "default" golden renders the question state at 700 tall.
    @Test fun kit_guide_default_1280x800() { FlutterGoldenGate.check("kit_guide_default_1280x800") { Scene(GuideState.Question, 700) } }

    // The text 2.0 goldens render the question state at the window height less 64.
    @Test fun kit_guide_default_text2() {
        FlutterGoldenGate.check("kit_guide_default_text2", modes = listOf("dark")) { Scene(GuideState.Question, 915 - 64) }
    }
    @Test fun kit_guide_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_guide_default_text2_1280x800", modes = listOf("dark")) { Scene(GuideState.Question, 800 - 64) }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_guide", caps = mapOf("KitGuide" to "fill")) { Scene(GuideState.Question, 820) }
    }
}
