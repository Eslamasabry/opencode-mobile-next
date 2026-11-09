package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.models.CatalogModel
import io.github.eslamasabry.oc.engine.agents.models.ModelCost
import io.github.eslamasabry.oc.engine.agents.models.ModelLibrary
import io.github.eslamasabry.oc.engine.agents.models.ModelRef
import io.github.eslamasabry.oc.feature.settings.presentation.KeptModelDefaults
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsData
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsHost
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json

/** Cache-first (CF): Settings › Model defaults opens on the rows its last read showed, then refreshes in place. */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstModelDefaultsTest {
    private object Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class Host(val kept: ModelDefaultsData?) : ModelDefaultsHost {
        val live = CompletableDeferred<Outcome<ModelDefaultsData>>()

        override suspend fun read(profile: ProfileId?): Outcome<ModelDefaultsData> = live.await()

        override suspend fun choose(model: ModelRef?): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun kept(profile: ProfileId?): ModelDefaultsData? = kept
    }

    private val data =
        ModelDefaultsData(
            "Studio",
            thisPhone = false,
            models =
                listOf(
                    CatalogModel("sonnet", "anthropic", "Claude Sonnet", cost = ModelCost(3.0, 15.0)),
                    CatalogModel("mini", "openai", "GPT mini", cost = ModelCost(0.0, 0.0)),
                ),
            current = ModelRef("anthropic", "sonnet"),
            defaults = listOf(ModelRef("openai", "mini")),
            library = ModelLibrary(),
        )

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `CF-15 the kept rows are the first frame and match the live frame's structure`() = runTest {
        // Kept through the wire, as the last-known store keeps it.
        val json = Json
        val wire = json.decodeFromString(KeptModelDefaults.serializer(), json.encodeToString(KeptModelDefaults.serializer(), KeptModelDefaults.of(data)))
        val host = Host(wire.data(data.library, stored = null))
        val vm = ModelDefaultsViewModel(SettingsRoute.ModelDefaults(), host, Nav)

        val first = vm.state.value
        assertTrue(first.loading, "the live read is still on its way")
        assertEquals("Studio", first.serverName)
        val firstRows = first.models.map { it.model to it.name } to first.recommended.map { it.model to it.reason }

        host.live.complete(Outcome.Ok(data))
        val live = vm.state.value
        assertFalse(live.loading)
        assertEquals(live.models.map { it.model to it.name } to live.recommended.map { it.model to it.reason }, firstRows)
        assertEquals(live.models.map { it.selected }, first.models.map { it.selected })
    }

    @Test
    fun `CF-16 a failed read keeps the kept rows and says so, never an empty page`() = runTest {
        val host = Host(data)
        val vm = ModelDefaultsViewModel(SettingsRoute.ModelDefaults(), host, Nav)
        host.live.complete(Outcome.Failed(Failure.Network(NetworkWhy.Offline)))
        val ui = vm.state.value
        assertNotNull(ui.failure)
        assertEquals(2, ui.models.size)
        assertFalse(ui.loading)
    }
}
