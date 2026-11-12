package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.models.CatalogModel
import io.github.eslamasabry.oc.engine.agents.models.CatalogVariant
import io.github.eslamasabry.oc.engine.agents.models.ModelLibrary
import io.github.eslamasabry.oc.engine.agents.models.ModelRef
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsData
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsHost
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsMode
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Settings > Model and mode: favourites and the thinking level reach the host, never a model the server does not list. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelDefaultsPickerTest {
    private object Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class Host(var data: ModelDefaultsData) : ModelDefaultsHost {
        val variants = mutableListOf<Pair<ModelRef, String>>()
        val favorites = mutableListOf<ModelRef>()
        val modes = mutableListOf<String>()

        override suspend fun chooseMode(mode: String): Outcome<Unit> {
            modes += mode
            data = data.copy(mode = mode)
            return Outcome.Ok(Unit)
        }

        override suspend fun read(profile: ProfileId?): Outcome<ModelDefaultsData> = Outcome.Ok(data)

        override suspend fun choose(model: ModelRef?): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun chooseWithVariant(model: ModelRef, variant: String): Outcome<Unit> {
            variants += model to variant
            data = data.copy(current = model, variant = variant)
            return Outcome.Ok(Unit)
        }

        override suspend fun toggleFavorite(model: ModelRef): Outcome<Unit> {
            favorites += model
            data = data.copy(library = data.library.toggleFavorite(model))
            return Outcome.Ok(Unit)
        }
    }

    private val sonnet = ModelRef("anthropic", "sonnet")
    private val data =
        ModelDefaultsData(
            "Studio",
            thisPhone = false,
            models =
                listOf(
                    CatalogModel(
                        "sonnet",
                        "anthropic",
                        "Claude Sonnet",
                        variants = listOf(CatalogVariant("low"), CatalogVariant("high"), CatalogVariant("off", disabled = true)),
                    ),
                    CatalogModel("mini", "openai", "GPT mini"),
                ),
            current = sonnet,
            defaults = emptyList(),
            library = ModelLibrary(),
        )

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `the chosen model offers only its enabled thinking levels and saves one`() = runTest {
        val host = Host(data)
        val vm = ModelDefaultsViewModel(SettingsRoute.ModelDefaults(), host, Nav)
        assertEquals(listOf("low", "high"), vm.state.value.models.first { it.model == sonnet }.variants)
        vm.chooseVariant("high")
        assertEquals(listOf(sonnet to "high"), host.variants)
        assertEquals("high", vm.state.value.variant)
        vm.chooseVariant("off")
        assertEquals(1, host.variants.size, "a disabled level is never saved")
    }

    @Test
    fun `rows carry the server's provider name and the page knows when only the server default applies`() = runTest {
        val host = Host(data.copy(providerNames = mapOf("anthropic" to "Anthropic"), usesDefault = true))
        val vm = ModelDefaultsViewModel(SettingsRoute.ModelDefaults(), host, Nav)
        assertEquals("Anthropic", vm.state.value.models.first { it.model == sonnet }.providerName)
        assertEquals("", vm.state.value.models.first { it.model.providerId == "openai" }.providerName)
        assertEquals(true, vm.state.value.usesDefault)
    }

    @Test
    fun `a favourite star reaches the host and the page shows it`() = runTest {
        val host = Host(data)
        val vm = ModelDefaultsViewModel(SettingsRoute.ModelDefaults(), host, Nav)
        vm.toggleFavorite(sonnet)
        assertEquals(listOf(sonnet), host.favorites)
        assertEquals(listOf(sonnet), vm.state.value.favorites)
    }

    @Test
    fun `the page lists the server's modes and stores the choice`() = runTest {
        val host = Host(data.copy(modes = listOf(ModelDefaultsMode("build"), ModelDefaultsMode("plan", "Reads only"))))
        val vm = ModelDefaultsViewModel(SettingsRoute.ModelDefaults(), host, Nav)
        assertEquals(listOf("build", "plan"), vm.state.value.modes.map { it.id })
        assertEquals("", vm.state.value.mode)
        vm.chooseMode("plan")
        assertEquals(listOf("plan"), host.modes)
        assertEquals("plan", vm.state.value.mode)
        vm.chooseMode("nonsense")
        assertEquals(1, host.modes.size, "a mode the server does not list is never saved")
        vm.chooseMode("")
        assertEquals(listOf("plan", ""), host.modes)
    }

    @Test
    fun `a server with no modes shows no mode row and saves none`() = runTest {
        val host = Host(data)
        val vm = ModelDefaultsViewModel(SettingsRoute.ModelDefaults(), host, Nav)
        assertEquals(emptyList(), vm.state.value.modes)
        vm.chooseMode("plan")
        assertEquals(emptyList(), host.modes)
    }
}
