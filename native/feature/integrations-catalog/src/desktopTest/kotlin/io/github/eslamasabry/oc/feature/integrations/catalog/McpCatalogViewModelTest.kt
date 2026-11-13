package io.github.eslamasabry.oc.feature.integrations.catalog

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.McpCatalogRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive

/** Flutter `_stopUsingRegistry`: the bar's "Stop using the registry", and its failure. */
@OptIn(ExperimentalCoroutinesApi::class)
class McpCatalogViewModelTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun model(host: FakeCatalogHost) =
        McpCatalogViewModel(McpCatalogRoute(ProfileId("p1")), RecordingNavigator(), controllers(host))

    @Test
    fun `B-AGENT-133 Stop using the registry turns the catalogue off and saves it`() = runTest {
        val host = FakeCatalogHost(optedIn = true)
        val vm = model(host)
        assertTrue(vm.state.value.catalog.optedIn)
        assertTrue(vm.state.value.canForget)
        assertEquals(5, vm.state.value.rows.size)

        vm.forget()

        assertFalse(vm.state.value.catalog.optedIn)
        assertFalse(vm.state.value.canForget)
        assertFalse(vm.state.value.forgetFailed)
        assertEquals(JsonPrimitive(false), host.saved.last()["optedIn"])
    }

    @Test
    fun `B-AGENT-133 a Stop using the registry that cannot be saved says so until the next choice`() =
        runTest {
            val host = FakeCatalogHost(optedIn = true)
            val vm = model(host)
            host.saveFails = true

            vm.forget()
            assertTrue(vm.state.value.forgetFailed)

            host.saveFails = false
            vm.consent(true)
            assertFalse(vm.state.value.forgetFailed)
            assertTrue(vm.state.value.catalog.optedIn)
        }

    @Test
    fun `B-AGENT-133 the menu is not offered before the person turns the catalogue on`() = runTest {
        val vm = model(FakeCatalogHost(optedIn = false))
        assertFalse(vm.state.value.catalog.optedIn)
        assertFalse(vm.state.value.canForget)
    }

    @Test
    fun `P37 first open does not claim the connection changed when the server is the same`() = runTest {
        val host = FakeCatalogHost(optedIn = false)
        host.rebuildsSource = true
        val vm = model(host)
        assertEquals(null, vm.state.value.catalog.notice)
        assertTrue(vm.state.value.catalog.canAdd)
    }
}
