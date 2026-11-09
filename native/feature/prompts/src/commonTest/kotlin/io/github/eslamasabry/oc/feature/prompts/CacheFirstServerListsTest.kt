package io.github.eslamasabry.oc.feature.prompts

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandItem
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandsViewModel
import io.github.eslamasabry.oc.feature.prompts.presentation.ConversationLists
import io.github.eslamasabry.oc.feature.prompts.presentation.KeptServerList
import io.github.eslamasabry.oc.feature.prompts.presentation.ListPhase
import io.github.eslamasabry.oc.feature.prompts.presentation.ServerList
import io.github.eslamasabry.oc.feature.prompts.presentation.ServerListsLastKnown
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskItem
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskRules
import io.github.eslamasabry.oc.feature.prompts.presentation.TasksViewModel
import io.github.eslamasabry.oc.ui.kitchat.composer.ComposerInbox
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.PromptsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstServerListsTest {
    private val ref = ConversationRef(ProfileId("p1"), SessionId("s1"))

    private class Slow(
        var commands: Outcome<ServerList<CommandItem>> = ServerList.Shown(emptyList<CommandItem>(), "Home").ok(),
        var tasks: Outcome<ServerList<TaskItem>> = ServerList.Shown(emptyList<TaskItem>(), "Home").ok(),
    ) : ConversationLists {
        val gate = CompletableDeferred<Unit>()

        override suspend fun commands(ref: ConversationRef): Outcome<ServerList<CommandItem>> {
            gate.await()
            return commands
        }

        override suspend fun tasks(ref: ConversationRef): Outcome<ServerList<TaskItem>> {
            gate.await()
            return tasks
        }
    }

    private class Shelf : ServerListsLastKnown {
        val items = mutableMapOf<String, KeptServerList>()
        override fun peek(ref: ConversationRef, item: String) = items[item]
        override suspend fun read(ref: ConversationRef, item: String) = items[item]
        override suspend fun keep(ref: ConversationRef, item: String, kept: KeptServerList) {
            items[item] = Json.decodeFromString(KeptServerList.serializer(), Json.encodeToString(KeptServerList.serializer(), kept))
        }
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun commandsVm(lists: Slow, shelf: Shelf) =
        CommandsViewModel(PromptsRoute.Commands(ref), lists, ComposerInbox(), emptySet(), StackNavigator(ChatRoute(ref)), setOf(shelf))

    @Test
    fun `CF-80 the first frame of the commands page with kept commands is the list, not a skeleton`() = runTest {
        val shelf = Shelf().also { it.items["commands"] = KeptServerList("Home", commands = listOf(KeptServerList.Command("review", "Review changes"))) }
        val vm = commandsVm(Slow(), shelf)
        val shown = assertIs<ListPhase.Shown<CommandItem>>(vm.state.value.phase)
        assertEquals("review", shown.items.single().name)
        assertTrue(shown.updating)
    }

    @Test
    fun `CF-81 the live commands replace the kept ones in place and are kept for next time`() = runTest {
        val shelf = Shelf().also { it.items["commands"] = KeptServerList("Home", commands = listOf(KeptServerList.Command("old", null))) }
        val lists = Slow(commands = ServerList.Shown(listOf(CommandItem("init", null)), "Home").ok())
        val vm = commandsVm(lists, shelf)
        lists.gate.complete(Unit)
        val shown = assertIs<ListPhase.Shown<CommandItem>>(vm.state.value.phase)
        assertEquals(listOf("init"), shown.items.map { it.name })
        assertFalse(shown.updating)
        assertEquals(listOf("init"), shelf.items["commands"]!!.commands.map { it.name })
    }

    @Test
    fun `CF-82 a failed live read leaves the kept commands standing`() = runTest {
        val shelf = Shelf().also { it.items["commands"] = KeptServerList("Home", commands = listOf(KeptServerList.Command("review", null))) }
        val lists = Slow(commands = Failure.Network(NetworkWhy.Offline).failed())
        val vm = commandsVm(lists, shelf)
        lists.gate.complete(Unit)
        val shown = assertIs<ListPhase.Shown<CommandItem>>(vm.state.value.phase)
        assertEquals(listOf("review"), shown.items.map { it.name })
        assertFalse(shown.updating)
    }

    @Test
    fun `CF-83 the first frame of the tasks page with kept tasks is the list`() = runTest {
        val shelf = Shelf().also { it.items["tasks"] = KeptServerList("Home", tasks = listOf(KeptServerList.Task("Write tests", "InProgress", "High"))) }
        val vm = TasksViewModel(PromptsRoute.Tasks(ref), Slow(), StackNavigator(ChatRoute(ref)), setOf(shelf))
        val shown = assertIs<ListPhase.Shown<TaskItem>>(vm.state.value.phase)
        assertEquals("Write tests", shown.items.single().content)
        assertTrue(shown.updating)
    }

    @Test
    fun `CF-84 live tasks replace the kept ones and are kept`() = runTest {
        val shelf = Shelf()
        val lists = Slow(tasks = ServerList.Shown(listOf(TaskRules.item("Ship", "pending", null)), "Home").ok())
        val vm = TasksViewModel(PromptsRoute.Tasks(ref), lists, StackNavigator(ChatRoute(ref)), setOf(shelf))
        assertEquals(ListPhase.Loading, vm.state.value.phase) // nothing kept: the first-ever load
        lists.gate.complete(Unit)
        assertEquals(listOf("Ship"), vm.state.value.visible.map { it.content })
        assertEquals(listOf("Ship"), shelf.items["tasks"]!!.tasks.map { it.content })
    }
}
