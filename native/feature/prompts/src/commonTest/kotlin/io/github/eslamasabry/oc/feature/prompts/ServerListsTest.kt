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
import io.github.eslamasabry.oc.feature.prompts.presentation.ListPhase
import io.github.eslamasabry.oc.feature.prompts.presentation.ServerList
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskItem
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskPriority
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskRules
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskState
import io.github.eslamasabry.oc.feature.prompts.presentation.TasksViewModel
import io.github.eslamasabry.oc.ui.kitchat.composer.ComposerInbox
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.PromptsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ServerListsTest {
    private val ref = ConversationRef(ProfileId("p1"), SessionId("s1"))

    private class Lists(
        var commands: Outcome<ServerList<CommandItem>> = ServerList.Shown(emptyList<CommandItem>(), "Home").ok(),
        var tasks: Outcome<ServerList<TaskItem>> = ServerList.Shown(emptyList<TaskItem>(), "Home").ok(),
    ) : ConversationLists {
        var reads = 0

        override suspend fun commands(ref: ConversationRef): Outcome<ServerList<CommandItem>> {
            reads++
            return commands
        }

        override suspend fun tasks(ref: ConversationRef): Outcome<ServerList<TaskItem>> {
            reads++
            return tasks
        }
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-NAV-051 the server's commands are listed and one puts its slash command into this composer`() = runTest {
        val lists = Lists(commands = ServerList.Shown(listOf(CommandItem("review", "Review changes")), "Home").ok())
        val inbox = ComposerInbox()
        val nav = StackNavigator(ChatRoute(ref), PromptsRoute.Saved(ref), PromptsRoute.Commands(ref))
        val vm = CommandsViewModel(PromptsRoute.Commands(ref), lists, inbox, nav)
        val shown = assertIs<ListPhase.Shown<CommandItem>>(vm.state.value.phase)
        assertEquals("review", shown.items.single().name)
        vm.choose("review")
        assertEquals("/review ", inbox.pendingFor(ComposerInbox.target("p1", "s1"))?.text)
        // Back to the same chat, never a second copy of it.
        assertIs<ChatRoute>(nav.top.value)
        assertEquals(1, nav.stack.size)
    }

    @Test
    fun `B-NAV-051 a server without commands says so and an unknown command inserts nothing`() = runTest {
        val inbox = ComposerInbox()
        val nav = StackNavigator(ChatRoute(ref), PromptsRoute.Commands(ref))
        val vm = CommandsViewModel(PromptsRoute.Commands(ref), Lists(commands = ServerList.NotOnServer("Home").ok()), inbox, nav)
        assertEquals(ListPhase.NotOnServer("Home"), vm.state.value.phase)
        vm.choose("review")
        assertNull(inbox.pendingFor(ComposerInbox.target("p1", "s1")))
        assertIs<PromptsRoute.Commands>(nav.top.value)
    }

    @Test
    fun `B-NAV-051 an offline read fails plainly and Try again reads again`() = runTest {
        val lists = Lists(commands = Failure.Network(NetworkWhy.Offline).failed())
        val vm = CommandsViewModel(PromptsRoute.Commands(ref), lists, ComposerInbox(), StackNavigator(ChatRoute(ref)))
        assertIs<ListPhase.Failed>(vm.state.value.phase)
        lists.commands = ServerList.Shown(listOf(CommandItem("init", null)), "Home").ok()
        vm.retry()
        assertIs<ListPhase.Shown<CommandItem>>(vm.state.value.phase)
        assertEquals(2, lists.reads)
    }

    @Test
    fun `B-CHAT-065 task states are bounded, priority is optional and progress ignores cancelled tasks`() {
        val items =
            listOf(
                TaskRules.item("Write tests", "completed", "high"),
                TaskRules.item("Fix build", "in_progress", null),
                TaskRules.item("Old idea", "cancelled", "low"),
                TaskRules.item("Ship", "something-new", "urgent"),
            )
        assertEquals(
            listOf(TaskState.Completed, TaskState.InProgress, TaskState.Cancelled, TaskState.Pending),
            items.map { it.state },
        )
        assertEquals(listOf(TaskPriority.High, null, TaskPriority.Low, null), items.map { it.priority })
        assertEquals(1 to 3, TaskRules.progress(items))
        assertEquals(listOf("Fix build", "Ship"), TaskRules.unfinished(items).map { it.content })
    }

    @Test
    fun `B-CHAT-065 the tasks page shows the list and can keep only unfinished tasks`() = runTest {
        val tasks = listOf(TaskRules.item("Done", "completed", null), TaskRules.item("Next", "pending", null))
        val vm = TasksViewModel(PromptsRoute.Tasks(ref), Lists(tasks = ServerList.Shown(tasks, "Home").ok()), StackNavigator(ChatRoute(ref)))
        assertEquals(2, vm.state.value.visible.size)
        vm.toggleUnfinished()
        assertEquals(listOf("Next"), vm.state.value.visible.map { it.content })
    }
}
