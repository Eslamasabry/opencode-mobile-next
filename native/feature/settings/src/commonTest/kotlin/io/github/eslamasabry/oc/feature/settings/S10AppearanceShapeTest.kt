package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGroup
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalPrefs
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.ui.design.style.Corners
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** B-166: Appearance › Style asks Shape (corners) as Flutter 2.1.4 does, and shows the migrated corners. */
@OptIn(ExperimentalCoroutinesApi::class)
class S10AppearanceShapeTest {
    private fun TestScope.presenter(prefs: PersonalPrefs, styles: Boolean = true) =
        SettingsPresenter(
            scope = backgroundScope,
            facts = MutableStateFlow(phoneFacts().copy(surfaceStyles = styles)),
            choices = FakeChoices(prefs),
            openers = RecordingOpeners(),
            analytics = FakeAnalytics(),
            notifications = FakeNotifications(),
            updates = FakeUpdates(UpdateFacts(current = "3.0.0", build = "10001")),
        )

    @Test
    fun `B-166 the Style group asks Style then Shape and shows a migrated Sharp over Soft's own corners`() = runTest {
        // 2.1.4 kept `oc.effectsCorners = sharp` with the Soft style; the engine reads it into the prefs.
        val p = presenter(PersonalPrefs(style = SurfaceStyle.Soft, corners = Corners.Sharp))
        runCurrent()
        p.onOpen(SettingsPage.Appearance); runCurrent()
        val ui = p.state.value.appearance
        assertEquals(
            listOf(AppearanceQuestion.Style, AppearanceQuestion.Shape),
            ui.questions.filter { it.group == AppearanceGroup.Style },
        )
        assertEquals(Corners.Sharp, ui.corners)
        assertTrue(ui.fineTuned, "Sharp is not Soft's own corners")
        p.onAppearanceQuestion(AppearanceQuestion.Shape); runCurrent()
        assertEquals(AppearanceQuestion.Shape, p.state.value.appearance.question)
    }

    @Test
    fun `B-166 where styles are not drawn there is no Shape question`() = runTest {
        val p = presenter(PersonalPrefs(), styles = false)
        runCurrent()
        p.onOpen(SettingsPage.Appearance); runCurrent()
        assertFalse(AppearanceQuestion.Shape in p.state.value.appearance.questions)
        p.onAppearanceQuestion(AppearanceQuestion.Shape); runCurrent()
        assertEquals(null, p.state.value.appearance.question)
    }
}
