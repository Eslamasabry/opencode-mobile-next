package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.LastKnown
import io.github.eslamasabry.oc.core.data.cache.ServerMark
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateDecision
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.TransientOwners
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The update note's maker, fed the way [PhoneAgentAutoUpdate] feeds it: one decision at a time. Only this side can see
 * the SAME agent report [AgentUpdateStep.Updated] twice (a re-read, a retry); the screen-side tests hand the state
 * ready-made, so the audit's rule is proven here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppPhoneAgentsUpdateNoteTest {
    private fun phones(): AppPhoneAgents {
        val app = RuntimeEnvironment.getApplication()
        val dispatchers = TestDispatchers(StandardTestDispatcher())
        val redactor = Redactor()
        val scope = AppCoroutineScope(SupervisorJob() + dispatchers.default)
        val build = AppBuildInfo("3.0.2", 10130, Channel.Preview, debuggable = true)
        val migration = MigrationWiring(app, build, redactor, scope, dispatchers, TestClock())
        val stores = PreviewStores(app, dispatchers, redactor, StateMigrationGate.alwaysOpen(), migration, build)
        val payloads = PhonePayloadGuard(app, stores, dispatchers, TransientOwners(), redactor, NoLastKnown())
        val setupJobs = NativeAgentSetupJobs(app, stores, dispatchers, payloads)
        val registry =
            ConnectionRegistry(
                emptySet(),
                ConnectionGraph.Factory { _, _, _ -> error("no connection is opened in this test") },
                scope,
                dispatchers,
            )
        return AppPhoneAgents(app, dispatchers, stores, setupJobs, registry, redactor, payloads)
    }

    // The audit of a9c9449f0b: a re-read or a retry can hand the maker the same agent's Updated decision twice, and
    // the note would read "updated Codex, Codex". An agent is named once, at the place of its first finish.
    @Test
    fun `B-AGENT-461 an agent that reports Updated twice is named once`() {
        val phones = phones()
        phones.setUpdateNote(AgentUpdateDecision(AgentUpdateStep.Updated, AgentId("claude")))
        phones.setUpdateNote(AgentUpdateDecision(AgentUpdateStep.Updated, AgentId("codex")))
        phones.setUpdateNote(AgentUpdateDecision(AgentUpdateStep.Updated, AgentId("claude")))
        assertEquals(listOf("Claude Code", "Codex"), phones.state.value.updatedAgentNames.toList())
    }

    /** The maker never reads last-known data in this test; it only needs the seam to exist. */
    private class NoLastKnown : LastKnown {
        override fun <T> peek(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<T>, item: String): T? = null

        override suspend fun <T> read(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<T>, item: String): T? = null

        override suspend fun <T> write(
            profile: ProfileId,
            mark: ServerMark,
            shelf: CacheShelf<T>,
            item: String,
            value: T,
        ): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun warm(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<*>) {}

        override suspend fun clear(profile: ProfileId): Outcome<Int> = Outcome.Ok(0)
    }
}
