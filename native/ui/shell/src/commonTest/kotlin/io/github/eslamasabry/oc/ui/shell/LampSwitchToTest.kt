package io.github.eslamasabry.oc.ui.shell

import androidx.navigation3.runtime.NavKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.GitRoute
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Edge light plan v3.1 §3: what Back does after a switch with the light. Stacks are written bottom first as
 * "Home > A > A›Files"; every row of the §3 table is one case of [the switch table].
 */
class LampSwitchToTest {
    private fun conv(name: String) = ConversationRef(ProfileId("p"), SessionId("ses_$name"))

    private val a = conv("A")
    private val b = conv("B")
    private val c = conv("C")

    private fun navigator(): ShellNavigator = ShellNavigator().also { it.attach(mutableListOf<NavKey>(ChatsRoute.Home)) }

    private fun ShellNavigator.stack(): String = snapshot().joinToString(" > ") { it.label() }

    private fun AppRoute.label(): String =
        when (this) {
            ChatsRoute.Home -> "Home"
            SettingsRoute.Home -> "Settings"
            is ChatRoute -> ref.name()
            is FilesRoute.Folder -> "${from.name()}›Files"
            is FilesRoute.File -> "${from.name()}›File"
            is ReviewRoute.Changes -> "${ref.name()}›Changes"
            is GitRoute -> "${ref.name()}›Git"
            else -> this::class.simpleName ?: "?"
        }

    private fun ConversationRef.name(): String = sessionId.raw.removePrefix("ses_")

    /** Back until Home, each stack on the way. */
    private fun ShellNavigator.backs(times: Int): List<String> =
        (1..times).map {
            back()
            stack()
        }

    private class Row(
        val name: String,
        val setup: ShellNavigator.() -> Unit,
        val after: String,
        val backs: List<String>,
    )

    @Test
    fun `B-LAMP-N1 the switch table of plan section 3, each case with the Backs that follow`() {
        val rows =
            listOf(
                Row(
                    "1 target is the current run's conversation: pages above it close",
                    { openChat(a); open(FilesRoute.Folder(a)); switchTo(a) },
                    "Home > A",
                    listOf("Home"),
                ),
                Row(
                    "3 no kept run, target not in the stack: pushed over the left run",
                    { openChat(a); switchTo(b) },
                    "Home > A > B",
                    listOf("Home > A", "Home"),
                ),
                Row(
                    "2 target is the kept run: the two runs swap",
                    { openChat(a); switchTo(b); switchTo(a) },
                    "Home > B > A",
                    listOf("Home > B", "Home"),
                ),
                Row(
                    "3 target lower in the stack (a notification-built stack): its run moves up",
                    { openChat(a); openChat(b); openChat(c); switchTo(a) },
                    "Home > B > C > A",
                    listOf("Home > B > C", "Home > B", "Home"),
                ),
                Row(
                    "3 the target's Files moves with it",
                    { openChat(a); open(FilesRoute.Folder(a)); openChat(b); switchTo(a) },
                    "Home > B > A > A›Files",
                    listOf("Home > B > A", "Home > B", "Home"),
                ),
                Row(
                    "3 the kept run includes a Files page",
                    { openChat(a); open(FilesRoute.Folder(a)); switchTo(b) },
                    "Home > A > A›Files > B",
                    listOf("Home > A > A›Files", "Home > A", "Home"),
                ),
                Row(
                    "2 a kept run with Git swaps back whole",
                    { openChat(a); open(GitRoute(a)); switchTo(b); switchTo(a) },
                    "Home > B > A > A›Git",
                    listOf("Home > B > A", "Home > B", "Home"),
                ),
                Row(
                    "3 a third switch drops the old kept run",
                    { openChat(a); switchTo(b); switchTo(c) },
                    "Home > B > C",
                    listOf("Home > B", "Home"),
                ),
                Row(
                    "3 no conversation in the stack: pushed, nothing kept",
                    { switchTo(a) },
                    "Home > A",
                    listOf("Home"),
                ),
                Row(
                    "3 older duplicates of the target go: exactly one entry for it",
                    { openChat(a); openChat(b); openChat(a); openChat(c); switchTo(a) },
                    "Home > B > C > A",
                    listOf("Home > B > C", "Home > B", "Home"),
                ),
                Row(
                    "K lapses once the current run changes (a notification pushed C): the stack walks as it is",
                    { openChat(a); switchTo(b); openChat(c); switchTo(a) },
                    "Home > B > C > A",
                    listOf("Home > B > C", "Home > B", "Home"),
                ),
                Row(
                    "pages that are not the target's stay where they are",
                    { open(SettingsRoute.Home); openChat(a); openChat(b); switchTo(a) },
                    "Home > Settings > B > A",
                    listOf("Home > Settings > B", "Home > Settings", "Home"),
                ),
            )
        rows.forEach { row ->
            val nav = navigator()
            row.setup(nav)
            assertEquals(row.after, nav.stack(), "${row.name}: after the switch")
            assertEquals(row.backs, nav.backs(row.backs.size), "${row.name}: Back, Back…")
        }
    }

    @Test
    fun `B-LAMP-N1 A then B then C, Back Back Back lands C, B, Home`() {
        val nav = navigator()
        nav.openChat(a)
        nav.switchTo(b)
        nav.switchTo(c)
        assertEquals("Home > B > C", nav.stack())
        assertEquals(listOf("Home > B", "Home", "Home"), nav.backs(3), "only one step is kept; the root never pops")
    }

    @Test
    fun `B-LAMP-N1 switchTo_targetNotInStack_pushesAndKeepsLeftRun`() {
        val nav = navigator()
        nav.openChat(a)
        nav.switchTo(b)
        assertEquals("Home > A > B", nav.stack())
        nav.switchTo(a)
        assertEquals("Home > B > A", nav.stack(), "A was kept, so switching back swaps instead of pushing a second A")
    }

    @Test
    fun `B-LAMP-N1 switchTo_targetLowerInStack_movesItsRun`() {
        val nav = navigator()
        nav.openChat(a)
        nav.open(ReviewRoute.Changes(a))
        nav.openChat(b)
        nav.switchTo(a)
        assertEquals("Home > B > A > A›Changes", nav.stack(), "A comes up with the Changes page it was left on")
    }

    @Test
    fun `B-LAMP-N1 switchTo_keptIsChatAndFiles_backLandsOnFiles`() {
        val nav = navigator()
        nav.openChat(a)
        nav.open(FilesRoute.Folder(a, "src"))
        nav.switchTo(b)
        nav.back()
        assertEquals(FilesRoute.Folder(a, "src"), nav.top.value, "Back returns to A's Files, where A was left")
        nav.back()
        assertEquals(ChatRoute(a), nav.top.value)
    }

    @Test
    fun `B-LAMP-N1 back_afterNotificationBuiltStack_walksStack`() {
        val nav = navigator()
        nav.openChat(a)
        nav.switchTo(b)
        nav.openChat(c) // a notification tap: pushes, leaves K alone
        assertEquals("Home > A > B > C", nav.stack())
        assertEquals(listOf("Home > A > B", "Home > A", "Home"), nav.backs(3))
    }

    @Test
    fun `B-LAMP-N1 after a notification over the kept run, the next switch drops nothing it should keep`() {
        val nav = navigator()
        nav.openChat(a)
        nav.switchTo(b)
        nav.openChat(c)
        nav.switchTo(b) // B lies under C, but K (A) lapsed when C arrived: B's run moves, A stays
        assertEquals("Home > A > C > B", nav.stack())
    }

    @Test
    fun `B-LAMP-N1 ringsMode_backToChat_unchanged`() {
        // Golden stacks of the rings' path before the edge light existed.
        val cases =
            listOf(
                Golden({ openChat(a); openChat(b); backToChat(a) }, "Home > A"),
                Golden({ openChat(b); backToChat(a); backToChat(a) }, "Home > B > A"),
                Golden({ openChat(a); open(FilesRoute.Folder(a)); backToChat(a) }, "Home > A"),
                Golden({ openChat(a); backToChat(a) }, "Home > A"),
                Golden({ backToChat(a); openChat(b); backToChat(a); backToChat(b) }, "Home > A > B"),
            )
        cases.forEachIndexed { i, case ->
            val nav = navigator()
            case.steps(nav)
            assertEquals(case.stack, nav.stack(), "rings case $i")
        }
    }

    private class Golden(val steps: ShellNavigator.() -> Unit, val stack: String)

    @Test
    fun `B-LAMP-N1 restore_afterProcessDeath_noKept_backWalks`() {
        val before = navigator()
        before.openChat(a)
        before.switchTo(b)
        val saved = before.snapshot()
        // A new process: a new navigator, the shell comes up on Home, the activity puts the stack back.
        val after = ShellNavigator()
        after.attach(mutableListOf<NavKey>(ChatsRoute.Home))
        after.restoreStack(saved)
        assertEquals("Home > A > B", after.stack())
        after.switchTo(c)
        assertEquals("Home > A > B > C", after.stack(), "no kept run after a restore: A is not dropped")
        assertEquals(listOf("Home > A > B", "Home > A", "Home"), after.backs(3))
    }

    @Test
    fun `B-LAMP-N1 restoring the stack on the same navigator forgets the kept run`() {
        val nav = navigator()
        nav.openChat(a)
        nav.switchTo(b)
        nav.restoreStack(nav.snapshot())
        nav.switchTo(c)
        assertEquals("Home > A > B > C", nav.stack())
    }

    @Test
    fun `B-LAMP-N1 a switch made before the shell is attached is applied on attach`() {
        val nav = ShellNavigator()
        nav.switchTo(a)
        val stack = mutableListOf<NavKey>(ChatsRoute.Home)
        nav.attach(stack)
        assertEquals(listOf<NavKey>(ChatsRoute.Home, ChatRoute(a)), stack)
    }

    @Test
    fun `B-LAMP-N1 the request rides the switch, a plain switch drops it, and it ages out after ten seconds`() {
        val clock = MovingClock()
        val nav = ShellNavigator(clock)
        nav.attach(mutableListOf<NavKey>(ChatsRoute.Home))
        nav.openChat(a)
        nav.switchTo(b, request = "per_1")
        val sent = nav.requestFocus.value!!
        assertEquals(b to "per_1", sent.ref to sent.requestId)
        assertEquals("Home > A > B", nav.stack(), "the request is not part of the entry's key")
        nav.switchTo(a)
        assertNull(nav.requestFocus.value, "the next switch ends it")
        nav.switchTo(b, request = "per_2")
        clock.at += 10.seconds
        nav.back()
        assertNull(nav.requestFocus.value, "ten seconds later it is gone")
    }

    @Test
    fun `B-LAMP-N1 requestFocus_consumedOnce through the navigator`() {
        val nav = navigator()
        nav.switchTo(a, request = "que_1")
        val sent = nav.requestFocus.value!!
        nav.consumeRequestFocus(sent.seq)
        assertNull(nav.requestFocus.value)
    }

    private class MovingClock(var at: Instant = Instant.fromEpochMilliseconds(5_000)) : Clock {
        override fun now(): Instant = at
    }
}
