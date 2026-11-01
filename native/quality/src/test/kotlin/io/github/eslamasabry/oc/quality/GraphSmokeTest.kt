package io.github.eslamasabry.oc.quality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.FakeGatewayFactory
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.desktop.DesktopAppGraph
import io.github.eslamasabry.oc.desktop.desktopBuildInfo
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.transcript.TranscriptAccessors
import io.github.eslamasabry.oc.engine.transcript.transcript
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsViewModel
import io.github.eslamasabry.oc.feature.chats.ChatsEntries
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Graph smoke (plan 03 §13.8), desktop half. The Android graph is checked by Metro at compile time in
 * :androidApp:assembleDebug; its Robolectric runtime half is an open item (P1 report).
 */
private val fake = FakeGatewayFactory(BackendKind.OpenCode2, FakeServerGateway(listOf(Session(SessionId("s1"), "One"))))

@OptIn(ExperimentalCoroutinesApi::class)
class GraphSmokeTest {
    @BeforeTest fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    /** The release graph's contributions plus one fake backend, so a ConnectionGraph can be opened. */
    @DependencyGraph(AppScope::class)
    interface SmokeGraph : ViewModelGraph {
        val registry: ConnectionRegistry
        val shell: ShellDependencies

        @Provides fun platformCapabilities(): PlatformCapabilities = PlatformCapabilities.desktop()

        @Provides @IntoSet fun fakeBackend(): GatewayFactory = fake

        @DependencyGraph.Factory
        fun interface Factory {
            fun create(@Provides buildInfo: AppBuildInfo, @Provides redactor: Redactor): SmokeGraph
        }
    }

    @Test
    fun `desktop graph resolves accessors, entry installers and every ViewModel`() {
        val graph = createGraphFactory<DesktopAppGraph.Factory>().create(desktopBuildInfo, Redactor())
        assertTrue(graph.shell.installers.any { it is ChatsEntries }, "ChatsEntries was not contributed")
        assertIs<ChatsViewModel>(graph.metroViewModelFactory.create(ChatsViewModel::class, CreationExtras.Empty))
        val allFactory = graph.metroViewModelFactory.createManuallyAssistedFactory(ChatsAllViewModel.Factory::class)
        assertEquals("x", allFactory().create(ChatsRoute.All("x")).state.value.filter)
        assertTrue(graph.viewModelProviders.keys.all { ViewModel::class.java.isAssignableFrom(it.java) })
    }

    @Test
    fun `a ConnectionGraph extension opens and carries contributed accessors from other engines`() = runTest {
        val graph = createGraphFactory<SmokeGraph.Factory>().create(AppBuildInfo("t", 1, Channel.Desktop, true), Redactor())
        val profile =
            ServerProfile(
                ProfileId("p1"), "Laptop", BackendKind.OpenCode2, ServerOrigin("http", "10.0.0.2", 4096),
                ProfileTrust(null, null), managedOnPhone = false, username = null,
            )
        val conn = assertIs<Outcome.Ok<*>>(graph.registry.open(profile)).value
        val connection = graph.registry.live.value.getValue(profile.id)
        assertTrue(conn === connection)
        // S4: :engine:transcript's accessor interface was merged into the generated extension.
        assertIs<TranscriptAccessors>(connection)
        assertEquals("One", (connection.transcript.session(SessionId("s1")) as Outcome.Ok).value.title)
        // Connection-scoped singletons are per extension instance.
        assertTrue(connection.transcript === connection.transcript)
        graph.registry.close(profile.id)
        assertTrue(graph.registry.live.value.isEmpty())
    }
}
