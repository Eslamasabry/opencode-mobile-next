package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.attention.AttentionRow
import io.github.eslamasabry.oc.feature.chats.attention.AttentionUi
import io.github.eslamasabry.oc.feature.chats.attention.HistoryIdentity
import io.github.eslamasabry.oc.feature.chats.attention.HistoryRow
import io.github.eslamasabry.oc.feature.chats.attention.KeptAttention
import io.github.eslamasabry.oc.feature.chats.attention.MonitorRow
import io.github.eslamasabry.oc.feature.chats.presentation.ImportsLastKnown
import io.github.eslamasabry.oc.feature.chats.presentation.KeptImports
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActKind
import io.github.eslamasabry.oc.engine.attention.feed.AttentionKind
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json

/**
 * Cache-first (owner rule, 2026-11-09): the Claude import list opens on the list last read for the folder, and
 * Needs you opens on the rows it last drew; the live read replaces them in place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstNewConversationTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/kept"
    private val item =
        ImportableConversation("claude", "h1", dir, Instant.fromEpochMilliseconds(1_000), "Plan the trip", "x".repeat(500))

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)
        override fun open(route: AppRoute) {}
        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}
        override fun land() {}
        override fun replaceRoot(root: AppRoute) {}
        override fun back() {}
    }

    private class Shelf(var value: KeptImports? = null) : ImportsLastKnown {
        override fun peek(profile: ProfileId, directory: String) = value
        override suspend fun read(profile: ProfileId, directory: String) = value
        override suspend fun keep(profile: ProfileId, directory: String, kept: KeptImports) {
            value = Json.decodeFromString(KeptImports.serializer(), Json.encodeToString(KeptImports.serializer(), kept))
        }
    }

    private inner class Host(val live: CompletableDeferred<Outcome<ImportableConversations>>) : NewConversationHost {
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))
        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(NewConversationSetup(dir, emptyList(), emptyList(), emptyList()))
        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = true
        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("")
        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun start(
            profile: ProfileId,
            directory: String,
            text: String,
            model: ModelRef?,
            agent: String?,
            separateCopy: Boolean,
            previous: NewConversationAttempt?,
            onStarted: (ConversationRef) -> Unit,
        ): NewConversationAttempt = NewConversationAttempt(directory, failure = Failure.Unavailable(FeatureId("test")))
        override suspend fun imports(profile: ProfileId, directory: String) = live.await()
        override suspend fun import(profile: ProfileId, directory: String, conversation: ImportableConversation): Outcome<ConversationRef> =
            Outcome.Failed(Failure.Unavailable(FeatureId("test")))
    }

    private fun vm(host: Host, shelf: ImportsLastKnown?) =
        NewConversationViewModel(ChatsRoute.New(phone), host, Nav(), Clock.System, ListenedReports(), setOfNotNull(shelf))

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `CF-50 the first frame of the import list has the kept rows and is not busy`() {
        val shelf = Shelf(KeptImports.of(ImportableConversations(persistentListOf(item), 2), 5_000))
        val live = CompletableDeferred<Outcome<ImportableConversations>>()
        val model = vm(Host(live), shelf)
        model.sheet(NewConversationSheet.Import)
        val first = model.state.value
        assertEquals(false, first.importBusy)
        assertEquals(listOf("h1"), first.imports?.items?.map { it.handle })
        assertEquals(2, first.imports?.alreadyImported)
        assertEquals(5_000, first.importsReadAtMs)
        assertEquals(KeptImports.MAX_PROMPT, first.imports?.items?.single()?.firstPrompt?.length)
    }

    @Test
    fun `CF-51 the live import list replaces the kept one and is kept, a failed read keeps the rows`() {
        val shelf = Shelf(KeptImports.of(ImportableConversations(persistentListOf(item), 0), 5_000))
        val fresh = item.copy(handle = "h2")
        val live = CompletableDeferred<Outcome<ImportableConversations>>()
        val model = vm(Host(live), shelf)
        model.sheet(NewConversationSheet.Import)
        live.complete(Outcome.Ok(ImportableConversations(persistentListOf(fresh), 0)))
        assertEquals(listOf("h2"), model.state.value.imports?.items?.map { it.handle })
        assertEquals(listOf("h2"), shelf.value?.rows?.map { it.handle })

        val failing = CompletableDeferred<Outcome<ImportableConversations>>()
        val again = vm(Host(failing), shelf)
        again.sheet(NewConversationSheet.Import)
        failing.complete(Outcome.Failed(Failure.Network()))
        assertEquals(listOf("h2"), again.state.value.imports?.items?.map { it.handle })
        assertTrue(again.state.value.importFailed)
    }

    @Test
    fun `CF-52 with nothing kept the first-ever import load has no list yet`() {
        val model = vm(Host(CompletableDeferred()), Shelf())
        model.sheet(NewConversationSheet.Import)
        assertNull(model.state.value.imports)
        assertTrue(model.state.value.importBusy)
    }

    @Test
    fun `CF-53 the first frame of Needs you has the kept rows, not loading, and no undo state`() {
        val ui =
            AttentionUi(
                loading = false,
                requests =
                    persistentListOf(
                        AttentionRow("k1", AttentionKind.Permission, "Home", "Fix tests", true, ConversationRef(phone, SessionId("s1")))
                    ),
                history =
                    persistentListOf(
                        HistoryRow(HistoryIdentity("phone", "a1"), AutomaticActKind.Reconnect, "Home", "9:00", undoable = true, undoing = true)
                    ),
                monitors = persistentListOf(MonitorRow("other", "Office", true)),
            )
        val kept = assertNotNull(KeptAttention.of(ui))
        val back = Json.decodeFromString(KeptAttention.serializer(), Json.encodeToString(KeptAttention.serializer(), kept)).toUi()
        assertEquals(false, back.loading)
        assertEquals(ui.requests, back.requests)
        assertEquals(ui.monitors, back.monitors)
        assertEquals(listOf("a1"), back.history.map { it.identity.id })
        assertEquals(false, back.history.single().undoing)
        assertNull(KeptAttention.of(ui.copy(incomplete = true)), "a partial read never replaces a good one")
        assertNull(KeptAttention.of(AttentionUi()), "nothing is kept while loading")
    }
}
