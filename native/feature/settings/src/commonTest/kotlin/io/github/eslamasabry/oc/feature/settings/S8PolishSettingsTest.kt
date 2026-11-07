package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalPrefs
import io.github.eslamasabry.oc.feature.settings.presentation.ServerModelChoice
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.feature.settings.presentation.reportHeadline
import io.github.eslamasabry.oc.feature.settings.presentation.serverModelLabel
import io.github.eslamasabry.oc.feature.settings.presentation.tidyModelId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** S8 settings polish: the logic behind P-020, P-022 and P-027. */
@OptIn(ExperimentalCoroutinesApi::class)
class S8PolishSettingsTest {
    private val crouton = ModelRef("opencode", "crouton-alpha-free")

    @Test
    fun `P-020 the model row names the model from the catalog, never provider slash id`() {
        val choices =
            listOf(ServerModelChoice(crouton, "Crouton Alpha", "OpenCode Zen", persistentListOf("high")))
        assertEquals("Crouton Alpha · OpenCode Zen", serverModelLabel(crouton, "", choices))
        assertEquals("Crouton Alpha · high · OpenCode Zen", serverModelLabel(crouton, "high", choices))
    }

    @Test
    fun `P-020 without the catalog the ids are tidied into words`() {
        val label = serverModelLabel(crouton, "", emptyList())
        assertEquals("Crouton Alpha Free · OpenCode", label)
        assertFalse("/" in label)
        assertEquals("Claude Opus 4.5 · Anthropic", serverModelLabel(ModelRef("anthropic", "claude-opus-4-5"), "", emptyList()))
        assertEquals("Llama · Groq", serverModelLabel(ModelRef("groq", "meta/llama"), "", emptyList()))
    }

    @Test
    fun `P-020 tidy ids read like Flutter's`() {
        assertEquals("GPT-6.1 Sol", tidyModelId("gpt-6.1-sol"))
        assertEquals("Claude Opus 4.5", tidyModelId("claude-opus-4-5"))
        assertEquals("GLM-4.6", tidyModelId("glm-4.6"))
        assertEquals("Model 20250101", tidyModelId("model-20250101"))
    }

    @Test
    fun `P-022 the closed Details fold is headed by the report's first line`() {
        assertEquals("The app closed unexpectedly", reportHeadline("\nThe app closed unexpectedly\n\nEnvironment\nApp: 3"))
        assertNull(reportHeadline("  \n \n"))
    }

    @Test
    fun `P-027 Back from one Appearance question returns to all groups before leaving the page`() = runTest {
        val p =
            SettingsPresenter(
                scope = backgroundScope,
                facts = MutableStateFlow(phoneFacts()),
                choices = FakeChoices(PersonalPrefs()),
                openers = RecordingOpeners(),
                analytics = FakeAnalytics(),
                notifications = FakeNotifications(),
                updates = FakeUpdates(UpdateFacts(current = "3.0.0", build = "10001")),
            )
        runCurrent()
        p.onOpen(SettingsPage.Appearance); runCurrent()
        p.onAppearanceQuestion(AppearanceQuestion.Theme); runCurrent()
        assertEquals(AppearanceQuestion.Theme, p.state.value.appearance.question)
        assertTrue(p.onBack()); runCurrent()
        assertEquals(SettingsPage.Appearance, p.state.value.page)
        assertNull(p.state.value.appearance.question)
        assertTrue(p.onBack()); runCurrent()
        assertEquals(SettingsPage.Hub, p.state.value.page)
    }
}
