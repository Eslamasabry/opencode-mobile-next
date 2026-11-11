package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The row outside the app as a pure function of the dock: who appears, the words, the buttons, the masking. */
class DockOutsideTest {
    private val p = ProfileId("p1")
    private fun ref(n: String) = ConversationRef(p, SessionId(n))

    private val words =
        object : DockOutsideWords {
            override fun untitled() = "Untitled conversation"
            override fun helperGeneric() = "a helper"
            override fun needsYouNamed(name: String) = "$name needs you"
            override fun needsYouCount(count: Int) = if (count == 1) "1 needs you" else "$count need you"
            override fun waitingForAnswer() = "Waiting for your answer"
            override fun othersWorking(count: Int) = "$count others working"
            override fun helperAsking(child: String) = "Its helper $child is asking"
            override fun workingOn(name: String) = "Working on $name"
            override fun conversationsWorking(count: Int) = "$count conversations working"
            override fun tasks(done: Int, total: Int) = "$done of $total tasks"
            override fun stateWorking() = "Working"
            override fun stateThinking() = "Thinking"
            override fun stateNeedsYou() = "Needs you"
            override fun stateDone() = "Done, not opened yet"
            override fun stateFailed() = "Stopped before finishing"
            override fun helpers(count: Int) = "$count helpers"
            override fun more(count: Int) = "+$count more"
            override fun lockWorking(count: Int) = "$count working"
            override fun onServer(server: String) = "on $server"
            override fun openNamed(name: String) = "Open $name"
            override fun stopNamed(name: String) = "Stop $name"
        }

    private fun entry(
        id: String,
        state: DockState,
        title: String? = "Chat $id",
        nickname: String? = null,
        pulse: Int = 0,
        children: List<DockChild> = emptyList(),
        causedBy: ConversationRef? = null,
        serverName: String? = null,
    ) =
        DockEntry(
            ref(id), title, nickname, null, state, pulse, causedBy = causedBy,
            children = children.toImmutableList(), serverName = serverName,
        )

    private fun ui(vararg entries: DockEntry, overflow: Int = 0, severalServers: Boolean = false) =
        DockUi(entries = persistentListOf(*entries), overflow = overflow, severalServers = severalServers)

    private fun build(ui: DockUi, facts: DockOutsideFacts = DockOutsideFacts()) = DockOutside.build(ui, facts, words)

    private val working = DockState.Working

    // ---- membership and order

    @Test
    fun `nothing live gives no row, and idle rings alone never appear`() {
        assertNull(build(DockUi()))
        assertNull(build(ui(entry("a", DockState.Idle))))
        // Finished and failed rings are not live: the completion alerts carry them.
        assertNull(build(ui(entry("a", DockState.Finished), entry("b", DockState.Failed))))
    }

    @Test
    fun `finished and failed rings appear only while something else is live, in the dock's order`() {
        val row = build(ui(entry("a", DockState.Finished), entry("b", working), entry("c", DockState.Failed), entry("d", DockState.Idle)))!!
        assertEquals(listOf("Chat a · Done, not opened yet", "Chat b · Working", "Chat c · Stopped before finishing"), row.lines)
        assertEquals(1, row.workingCount)
    }

    @Test
    fun `one ring needs the one line, a title and no lines`() {
        val row = build(ui(entry("a", working)))!!
        assertEquals(emptyList(), row.lines)
        assertEquals("Working on Chat a", row.title)
        assertEquals("Working", row.text)
    }

    @Test
    fun `at most four lines, then the fourth says how many more`() {
        val many = (1..6).map { entry("c$it", working) }.toTypedArray()
        val row = build(ui(*many))!!
        assertEquals(4, row.lines.size)
        assertEquals("+3 more", row.lines.last())
        assertEquals("Chat c3 · Working", row.lines[2])
        val exactlyFour = build(ui(*many.take(4).toTypedArray()))!!
        assertEquals(4, exactlyFour.lines.size)
        assertFalse(exactlyFour.lines.any { it.startsWith("+") })
        // Rings that did not fit the dock itself count as more.
        val overflow = build(ui(*many.take(2).toTypedArray(), overflow = 5))!!
        assertEquals(listOf("Chat c1 · Working", "Chat c2 · Working", "+5 more"), overflow.lines)
    }

    @Test
    fun `the app's own reconnecting or android-limit row speaks unless someone needs you`() {
        val u = ui(entry("a", working))
        assertNull(build(u, DockOutsideFacts(reconnecting = true)))
        assertNull(build(u, DockOutsideFacts(androidLimit = true)))
        val needs = ui(entry("a", working), entry("b", DockState.NeedsYou))
        assertNotNull(build(needs, DockOutsideFacts(reconnecting = true, androidLimit = true)))
    }

    // ---- the words per state

    @Test
    fun `words per state`() {
        assertEquals("Working on Chat a" to "3 of 7 tasks", build(ui(entry("a", DockState.WorkingWithTasks(3, 7))))!!.let { it.title to it.text })
        assertEquals("Thinking", build(ui(entry("a", DockState.Thinking)))!!.text)
        // The tool sentence only when there is no task list, and it is masked again here.
        val tool = DockOutsideFacts(toolLines = mapOf(ref("a") to "Running tests with token=abc123456789"))
        assertEquals("Running tests with token=•••", build(ui(entry("a", working)), tool)!!.text)
        assertEquals("3 of 7 tasks", build(ui(entry("a", DockState.WorkingWithTasks(3, 7))), tool)!!.text)
        val one = build(ui(entry("a", DockState.NeedsYou), entry("b", working)))!!
        assertEquals("Chat a needs you", one.title)
        assertEquals("Waiting for your answer · 1 others working", one.text)
        val several = build(ui(entry("a", DockState.NeedsYou), entry("b", DockState.NeedsYou)))!!
        assertEquals("2 need you", several.title)
        assertEquals("Chat a, Chat b", several.text)
        val group = build(ui(entry("a", working), entry("b", DockState.Thinking)))!!
        assertEquals("2 conversations working", group.title)
        assertEquals(OutsideKind.SeveralWorking, group.kind)
    }

    @Test
    fun `a sub-agent that needs you is named under its parent, and working helpers are a count`() {
        val child = DockChild(ref("kid"), "t", "explore", DockState.NeedsYou, 1)
        val row = build(ui(entry("a", DockState.NeedsYou, children = listOf(child), causedBy = ref("kid"))))!!
        assertEquals("Chat a needs you", row.title)
        assertEquals("Its helper explore is asking", row.text)
        val busy = listOf(DockChild(ref("k1"), null, "x", working, 0), DockChild(ref("k2"), null, "y", DockState.Thinking, 0), DockChild(ref("k3"), null, "z", DockState.Idle, 0))
        val two = build(ui(entry("a", working), entry("b", working, children = busy)))!!
        assertEquals("Chat b · Working · 2 helpers", two.lines[1])
        val fallback = build(ui(entry("a", DockState.NeedsYou, children = listOf(DockChild(ref("kid"), null, null, DockState.NeedsYou, 0)), causedBy = ref("kid"))))!!
        assertEquals("Its helper a helper is asking", fallback.text)
    }

    @Test
    fun `the nickname wins over the title and an untitled conversation has a fallback name`() {
        assertEquals("Working on Mine", build(ui(entry("a", working, title = "Server title", nickname = "Mine")))!!.title)
        assertEquals("Working on Untitled conversation", build(ui(entry("a", working, title = null)))!!.title)
        assertEquals("Working on Untitled conversation", build(ui(entry("a", working, title = "   ")))!!.title)
    }

    @Test
    fun `progress is a real fraction for exactly one live task list and never otherwise`() {
        assertEquals(42, build(ui(entry("a", DockState.WorkingWithTasks(3, 7))))!!.progress)
        assertNull(build(ui(entry("a", working)))!!.progress)
        assertNull(build(ui(entry("a", DockState.Thinking)))!!.progress)
        assertNull(build(ui(entry("a", DockState.WorkingWithTasks(3, 7)), entry("b", working)))!!.progress)
        assertNull(build(ui(entry("a", DockState.WorkingWithTasks(3, 7)), entry("b", DockState.NeedsYou)))!!.progress)
    }

    @Test
    fun `the server is named only when the live rings come from several`() {
        val e = entry("a", working, serverName = "Home box")
        assertEquals("", build(ui(e))!!.subText)
        assertEquals("on Home box", build(ui(e, severalServers = true))!!.subText)
    }

    // ---- action targets

    @Test
    fun `Open targets the first ring that needs you, else the newest working one`() {
        val u = ui(entry("a", working), entry("b", DockState.NeedsYou), entry("c", DockState.NeedsYou))
        assertEquals(ref("b"), build(u)!!.open.ref)
        assertEquals("Open Chat b", build(u)!!.open.label)
        val w = ui(entry("a", working), entry("b", working), entry("c", working))
        val recency = DockOutsideFacts(recency = mapOf(ref("a") to 10L, ref("b") to 30L, ref("c") to 20L))
        assertEquals(ref("b"), build(w, recency)!!.open.ref)
        // Without any timestamps the dock's own order decides.
        assertEquals(ref("a"), build(w)!!.open.ref)
    }

    @Test
    fun `Stop is offered only with exactly one working conversation that does not need you`() {
        val one = build(ui(entry("a", working)))!!
        assertEquals(ref("a"), one.stop!!.ref)
        assertEquals("Stop Chat a", one.stop!!.label)
        assertNull(build(ui(entry("a", working), entry("b", working)))!!.stop)
        assertNull(build(ui(entry("a", DockState.NeedsYou)))!!.stop)
        // One working beside one that needs you: Open names the second, Stop names the first, never mixed up.
        val mixed = build(ui(entry("a", working), entry("b", DockState.NeedsYou)))!!
        assertEquals(ref("b"), mixed.open.ref)
        assertEquals(ref("a"), mixed.stop!!.ref)
        // A ring that needs you only because its helper asks is not a working one.
        val child = DockChild(ref("kid"), null, "x", DockState.NeedsYou, 0)
        assertNull(build(ui(entry("a", DockState.NeedsYou, children = listOf(child), causedBy = ref("kid"))))!!.stop)
        assertNull(build(ui(entry("a", DockState.Finished), entry("b", DockState.Failed), entry("c", DockState.NeedsYou)))!!.stop)
    }

    // ---- masking

    @Test
    fun `names are masked and cut, and no secret shape survives into any field`() {
        val secrets = listOf("sk-abcdefghijklmnop", "ghp_abcdefghijklmnopqrstu", "password=hunter22secret", "Bearer abcdefgh12345678")
        for (secret in secrets) {
            val row = build(ui(entry("a", DockState.NeedsYou, title = "Fix $secret now"), entry("b", working, nickname = secret)))!!
            val all = listOf(row.title, row.text, row.publicTitle, row.subText, row.open.label, row.stop?.label.orEmpty()) + row.lines
            for (field in all) {
                assertFalse(field.contains(secret.substringAfter('=').substringAfter(' ')), "$secret leaked in: $field")
                assertFalse(field.contains("abcdefghijklmnop"), "$secret leaked in: $field")
            }
        }
        val long = build(ui(entry("a", working, title = "x".repeat(200))))!!
        assertTrue(long.title.length <= "Working on ".length + DockOutside.MAX_NAME)
    }

    @Test
    fun `no request body or reply text exists in the row, because the dock has none to give`() {
        // The dock's entries carry request ids only; the row's fields are built from names, counts and fixed words.
        val withRequest = DockEntry(ref("a"), "Chat a", null, null, DockState.NeedsYou, 1, requests = persistentListOf(DockRequestRef(DockRequestKind.Permission, "perm_secret_id")))
        val row = build(DockUi(entries = persistentListOf(withRequest)))!!
        val all = listOf(row.title, row.text, row.publicTitle, row.subText, row.open.label) + row.lines
        assertTrue(all.none { it.contains("perm_secret_id") })
    }

    // ---- the lock screen

    @Test
    fun `the lock screen version has counts and fixed words only, never any part of a name`() {
        val random = Random(7)
        repeat(200) {
            val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            fun name() = (1..random.nextInt(6, 18)).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            val names = (1..random.nextInt(1, 6)).map { name() }
            val states = names.map { listOf(working, DockState.NeedsYou, DockState.Thinking, DockState.Finished)[random.nextInt(4)] }
            val entries = names.mapIndexed { i, n -> entry("s$i", states[i], title = n, serverName = name()) }.toTypedArray()
            val row = build(ui(*entries, severalServers = true)) ?: return@repeat
            val allowed = Regex("""^OpenCode( · \d+ working)?( · (\d+ need you|1 needs you))?$""")
            assertTrue(allowed.matches(row.publicTitle), row.publicTitle)
            for (n in names) assertFalse(row.publicTitle.contains(n.take(4)), "name part in ${row.publicTitle}")
        }
        val row = build(ui(entry("a", working), entry("b", working), entry("c", DockState.NeedsYou)))!!
        assertEquals("OpenCode · 2 working · 1 needs you", row.publicTitle)
        assertEquals(1, row.count)
        assertEquals(OutsideKind.NeedsYou, row.kind)
    }

    // ---- dismissal

    @Test
    fun `a swiped-away row stays away until a new request or failure raises the pulses`() {
        val gate = DockOutsideGate()
        val first = build(ui(entry("a", working, pulse = 2)))!!
        assertTrue(gate.allows(first))
        gate.dismiss(first.pulses)
        // Ordinary state changes (same pulses) do not bring it back.
        assertFalse(gate.allows(build(ui(entry("a", DockState.WorkingWithTasks(1, 3), pulse = 2)))))
        assertFalse(gate.allows(build(ui(entry("a", working, pulse = 2), entry("b", working)))))
        // A ring leaving lowers the sum: still dismissed.
        assertFalse(gate.allows(build(ui(entry("b", working)))))
        // A new request raises it.
        assertTrue(gate.allows(build(ui(entry("a", DockState.NeedsYou, pulse = 3)))))
        // And it is not dismissed any more afterwards.
        assertTrue(gate.allows(build(ui(entry("a", working, pulse = 3)))))
    }

    @Test
    fun `when nothing is live the row is gone and the next run starts fresh`() {
        val gate = DockOutsideGate()
        val row = build(ui(entry("a", working, pulse = 1)))!!
        gate.dismiss(row.pulses)
        val gone = build(ui(entry("a", DockState.Idle)))
        assertNull(gone)
        assertFalse(gate.allows(gone))
        assertTrue(gate.allows(build(ui(entry("a", working, pulse = 1)))))
    }

    @Test
    fun `pulses add up the rings and their sub-agents`() {
        val child = DockChild(ref("k"), null, "x", working, 4)
        assertEquals(7, build(ui(entry("a", working, pulse = 1, children = listOf(child)), entry("b", working, pulse = 2)))!!.pulses)
    }
}
