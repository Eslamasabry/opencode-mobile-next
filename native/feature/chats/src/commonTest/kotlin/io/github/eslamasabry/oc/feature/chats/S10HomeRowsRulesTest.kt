package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.BranchPlace
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeRows
import io.github.eslamasabry.oc.feature.chats.presentation.HomeUsageLimit
import io.github.eslamasabry.oc.feature.chats.presentation.RunLength
import io.github.eslamasabry.oc.ui.kit.row.KitProjectMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** B-161 / B-162: the pure rules behind the richer home rows and the Usage row. */
class S10HomeRowsRulesTest {
    @Test
    fun `B-CHATS-161 the project tile takes two letters from the name, without the phone's language`() {
        assertEquals("OM", KitProjectMark.letters("opencode-mobile-next"))
        assertEquals("AP", KitProjectMark.letters("api"))
        assertEquals("WS", KitProjectMark.letters("  web site "))
        assertEquals("I", KitProjectMark.letters("i"))
        assertEquals("", KitProjectMark.letters(" / "))
        // Arabic letters stay as they are (no case), two of them.
        assertEquals("مش", KitProjectMark.letters("مشروع"))
    }

    @Test
    fun `B-CHATS-161 the project colour is stable for a name and differs between names`() {
        val a = KitProjectMark.hue("opencode-mobile-next")
        assertEquals(a, KitProjectMark.hue("opencode-mobile-next"))
        assertEquals(a, KitProjectMark.hue(" OpenCode-Mobile-Next "), "case and edge blanks do not change the colour")
        assertNotEquals(a, KitProjectMark.hue("api"))
        listOf("", "api", "web", "x".repeat(200)).forEach { assertTrue(KitProjectMark.hue(it) in 0f..<360f) }
        // Pinned values: a change to the derivation would repaint every project on the owner's phone.
        assertEquals(47f, KitProjectMark.hue("api"), 0.01f)
        assertEquals(37f, KitProjectMark.hue("opencode-mobile-next"), 0.01f)
    }

    @Test
    fun `B-CHATS-161 a live run reads in minutes, then hours and minutes, and says nothing under a minute`() {
        assertNull(RunLength.of(null))
        assertNull(RunLength.of(59_000))
        assertEquals(RunLength.Minutes(1), RunLength.of(60_000))
        assertEquals(RunLength.Minutes(34), RunLength.of(34 * 60_000L + 59_000))
        assertEquals(RunLength.HoursMinutes(1, 5), RunLength.of(65 * 60_000L))
    }

    @Test
    fun `B-CHATS-161 rows carry their folder's branch from the same server only`() {
        val ui =
            ChatsState.build(
                ChatFeedSnapshot(
                    persistentListOf(item("a", "/work/api", at = 20_000), item("b", "/work/web", at = 20_000))
                ),
                ReturnBrief.None,
                ChatsFilters(),
                10_000,
                false,
            )
        assertEquals(setOf(BranchPlace(P1, "/work/api"), BranchPlace(P1, "/work/web")), HomeRows.places(ui))
        val branched =
            HomeRows.withBranches(
                ui,
                mapOf(BranchPlace(P1, "/work/api") to "main", BranchPlace(ProfileId("other"), "/work/web") to "dev"),
            )
        assertEquals("main", branched.today.single { it.project == "api" }.branch)
        assertNull(branched.today.single { it.project == "web" }.branch, "another server's folder is not this one")
    }

    @Test
    fun `B-CHATS-162 the Usage row names the tightest limit, else nothing`() {
        assertNull(HomeRows.tightest(emptyList()))
        assertEquals(
            HomeUsageLimit("Claude Code", 34),
            HomeRows.tightest(
                listOf(HomeUsageLimit("Codex", 80), HomeUsageLimit("Claude Code", 34), HomeUsageLimit("GLM", 50))
            ),
        )
        assertEquals(HomeUsageLimit("Codex", 0), HomeRows.tightest(listOf(HomeUsageLimit("Codex", -3))))
        assertNull(HomeRows.tightest(listOf(HomeUsageLimit(" ", 10))), "a limit with no name says nothing")
    }

    @Test
    fun `B-CHATS-161 a running row keeps its state for the live line`() {
        val ui =
            ChatsState.build(
                ChatFeedSnapshot(persistentListOf(item("r", status = ChatStatus.Running, at = 20_000))),
                ReturnBrief.None,
                ChatsFilters(),
                10_000,
                false,
            )
        assertEquals("/work/api", ui.today.single().directory)
    }
}
