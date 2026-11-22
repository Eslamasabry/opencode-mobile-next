package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.model.CatalogProvider
import io.github.eslamasabry.oc.core.model.CatalogSnapshot
import io.github.eslamasabry.oc.core.model.IntegrationConnectionInfo
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderField
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderOutcome
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderProblem
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRefusal
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryInventory
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderStage
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderUi
import io.github.eslamasabry.oc.feature.settings.presentation.shownProviderIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** Pull request 142, ported: the add-a-provider sheet's stages; "saved" and "loaded" never collapse into one. */
class CustomProviderUiTest {
    private val saving = CustomProviderUi(stage = CustomProviderStage.Saving, name = "My box", providerId = "my-box")

    @Test
    fun `PR142-20 an untouched form is never red, Save fills one problem per field`() {
        val fresh = CustomProviderUi()
        assertEquals(CustomProviderStage.Form, fresh.stage)
        assertTrue(CustomProviderField.entries.all { fresh.problem(it) == null })
        val saved =
            fresh.after(
                CustomProviderOutcome.Invalid(
                    listOf(CustomProviderProblem.NameMissing, CustomProviderProblem.IdTaken)
                )
            )
        assertEquals(CustomProviderProblem.NameMissing, saved.problem(CustomProviderField.Name))
        assertEquals(CustomProviderProblem.IdTaken, saved.problem(CustomProviderField.Id))
        assertNull(saved.problem(CustomProviderField.Address))
    }

    @Test
    fun `PR142-21 every outcome lands on its own stage, and a refused save goes back to the form`() {
        assertEquals(CustomProviderStage.Loaded, saving.after(CustomProviderOutcome.Loaded).stage)
        assertEquals(CustomProviderStage.SavedNotLoaded, saving.after(CustomProviderOutcome.SavedNotLoaded).stage)
        val failed = saving.after(CustomProviderOutcome.RestartFailed("Runtime · Generic"))
        assertEquals(CustomProviderStage.RestartFailed, failed.stage)
        assertEquals("Runtime · Generic", failed.detail)
        assertEquals(
            CustomProviderStage.NotLoadedAfterRestart,
            saving.after(CustomProviderOutcome.NotLoadedAfterRestart).stage,
        )
        val refused = saving.after(CustomProviderOutcome.Refused(CustomProviderRefusal.Busy, "Exit 23"))
        assertEquals(CustomProviderStage.Form, refused.stage)
        assertEquals(CustomProviderRefusal.Busy, refused.refusal)
        assertEquals("Exit 23", refused.detail)
        // The name and id stay for the words of the next stage.
        assertEquals("My box", saving.after(CustomProviderOutcome.Loaded).name)
    }

    @Test
    fun `PR142-22 a refusal during a restart keeps the saved stage, since the declaration is on disk`() {
        val restarting = saving.copy(stage = CustomProviderStage.Restarting)
        val changed = restarting.after(CustomProviderOutcome.Refused(CustomProviderRefusal.Changed))
        assertEquals(CustomProviderStage.SavedNotLoaded, changed.stage)
        assertEquals(CustomProviderRefusal.Changed, changed.refusal)
    }

    @Test
    fun `PR142-23 the ids the page shows are refused before Save reaches the server`() {
        val inventory =
            LibraryInventory(
                providers =
                    persistentListOf(IntegrationInfo("openai", "OpenAI", persistentListOf(), persistentListOf(), 0)),
                catalog =
                    CatalogSnapshot(
                        persistentListOf(CatalogProvider("zen", "Zen", true, "opencode")),
                        persistentListOf(),
                        persistentListOf(),
                    ),
            )
        assertEquals(setOf("openai", "zen", "opencode"), shownProviderIds(inventory))
    }

    @Test
    fun `B-LIB-324 the form leaves out a listed provider with no connection and no catalog id`() {
        val inventory =
            LibraryInventory(
                providers =
                    persistentListOf(
                        IntegrationInfo("stranded", "Stranded", persistentListOf(), persistentListOf(), 0),
                        IntegrationInfo(
                            "live",
                            "Live",
                            persistentListOf(),
                            persistentListOf(IntegrationConnectionInfo("credential", "c-1", "", null)),
                            1,
                        ),
                        IntegrationInfo("zen", "Zen", persistentListOf(), persistentListOf(), 0),
                    ),
                catalog =
                    CatalogSnapshot(
                        persistentListOf(CatalogProvider("zen", "Zen", true, "opencode")),
                        persistentListOf(),
                        persistentListOf(),
                    ),
            )
        // stranded is listed with no connection and the loaded catalog does not hold it, so only the settings file
        // can: the form lets the id through and the engine's check decides. live carries a connection, and zen is a
        // catalog id even with no connection: both stay refused.
        assertEquals(setOf("live", "zen", "opencode"), shownProviderIds(inventory))
    }
}
