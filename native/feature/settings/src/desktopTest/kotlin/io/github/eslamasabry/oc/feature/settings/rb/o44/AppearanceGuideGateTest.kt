package io.github.eslamasabry.oc.feature.settings.rb.o44

import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Gate cases for the guided Settings › Appearance (Flutter `test/goldens/settings_appearance_guided_golden_test.dart`):
 * the guide's states reached by tapping from the start view (`_taps`: mode = Colours, theme = Colours › Theme,
 * style = Style, look = Background), in Solid; and the start view and Style in Soft and Brutal. The scene is
 * `test/support/settings_scenes.dart`'s Appearance: Graphite, dark or light, a first visit.
 */
class AppearanceGuideGateTest {
    private val screen = FlutterGoldenGate.Frame.Screen

    private fun appearance(question: AppearanceQuestion? = null, style: SurfaceStyle = SurfaceStyle.Solid) =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Appearance),
            appearance =
                AppearanceUi(
                    style = style,
                    packs = Packs.mostUsed.toImmutableList(),
                    seeAllCount = PackId.entries.size,
                    // The questions the scene's taps opened (`_open` remembers each as seen): Colours opens Mode,
                    // so the Theme state has passed Mode.
                    guide =
                        AppearanceGuideRecord(
                            seen =
                                when (question) {
                                    null -> emptyList()
                                    AppearanceQuestion.Theme -> listOf(AppearanceQuestion.Mode, question)
                                    else -> listOf(question)
                                }
                        ),
                    question = question,
                    questions = AppearanceQuestion.entries.toImmutableList(),
                ),
        )

    private fun shot(name: String, question: AppearanceQuestion?, style: SurfaceStyle = SurfaceStyle.Solid) {
        FlutterGoldenGate.check(name, frame = screen, style = Style.valueOf(style.name)) {
            SettingsScreen(appearance(question, style), RecordingActions())
        }
    }

    @Test fun settings_appearance_guided_solid_mode() = shot("settings_appearance_guided_solid_mode", AppearanceQuestion.Mode)

    @Test
    fun settings_appearance_guided_solid_theme() = shot("settings_appearance_guided_solid_theme", AppearanceQuestion.Theme)

    @Test
    fun settings_appearance_guided_solid_style() = shot("settings_appearance_guided_solid_style", AppearanceQuestion.Style)

    @Test fun settings_appearance_guided_solid_look() = shot("settings_appearance_guided_solid_look", AppearanceQuestion.Look)

    @Test
    fun settings_appearance_guided_soft_home() = shot("settings_appearance_guided_soft_home", null, SurfaceStyle.Soft)

    @Test
    fun settings_appearance_guided_soft_style() =
        shot("settings_appearance_guided_soft_style", AppearanceQuestion.Style, SurfaceStyle.Soft)

    @Test
    fun settings_appearance_guided_brutal_home() =
        shot("settings_appearance_guided_brutal_home", null, SurfaceStyle.Brutal)

    @Test
    fun settings_appearance_guided_brutal_style() =
        shot("settings_appearance_guided_brutal_style", AppearanceQuestion.Style, SurfaceStyle.Brutal)
}
