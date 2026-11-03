package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.android.notifications.NotificationText
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateState
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject

/** The platform hub's decisions (widgets, alerts, shortcuts, share, updates), without Android. */
class PlatformRulesTest {
    private val a = ProfileId("server-a")
    private val b = ProfileId("server-b")

    private fun item(profile: ProfileId, id: String, title: String? = "Work", status: ChatStatus = ChatStatus.Idle, at: Long = 1) =
        ChatFeedItem("oc", profile, SessionId(id), title, "/srv/app", "app", status, at)

    private fun feed(vararg items: ChatFeedItem) = ChatFeedSnapshot(items.toList().toImmutableList())

    private fun permission(profile: ProfileId, session: String, id: String) =
        Waiting(
            profile,
            PendingRequest.Permission(
                PermissionRequest(RequestId(id), SessionId(session), "bash", persistentListOf(), JsonObject(emptyMap()), persistentListOf(), null, false, null),
            ),
        )

    @Test
    fun `B-SEC-181 widget titles, projects and server names are masked and bounded`() {
        val title = "deploy with password=hunter2-very-secret and token: ghp_abcdefghijklmnopqrstuvwxyz0123"
        val model =
            PlatformRules.widgetModel(
                feed(item(a, "s1", title = title + " " + "x".repeat(200))),
                emptyList(),
                a,
                mapOf(a to "Box api_key=sk-live-1234567890abcdef"),
                setOf(a),
            )
        val row = model.rows.single()
        assertFalse("hunter2" in row.title)
        assertFalse("ghp_abcdefghijklmnopqrstuvwxyz0123" in row.title)
        assertTrue(row.title.length <= PlatformRules.WIDGET_TEXT)
        assertFalse("sk-live" in row.server)
        assertFalse("sk-live" in model.servers.single().name)
    }

    @Test
    fun `B-ANDR-116 each widget row names its own server, and another server's request has no inline answer`() {
        val model =
            PlatformRules.widgetModel(
                feed(item(a, "a1"), item(b, "b1")),
                listOf(permission(a, "a1", "per_a"), permission(b, "b1", "per_b")),
                a,
                mapOf(a to "A", b to "B"),
                setOf(a, b),
            )
        val (rowA, rowB) = model.rows
        assertEquals("server-a", rowA.profile)
        assertEquals("server-b", rowB.profile)
        assertEquals("per_a", rowA.requestId)
        assertEquals("", rowB.requestId)
        assertTrue(rowB.needsYou)
    }

    @Test
    fun `B-ANDR-117 the same content gives an equal model, so nothing is rewritten`() {
        fun build() = PlatformRules.widgetModel(feed(item(a, "a1")), emptyList(), a, mapOf(a to "A"), setOf(a))
        assertEquals(build(), build())
        val changed = PlatformRules.widgetModel(feed(item(a, "a1", status = ChatStatus.Running)), emptyList(), a, mapOf(a to "A"), setOf(a))
        assertTrue(changed != build())
    }

    @Test
    fun `B-ANDR-011 a failed run gets the stopped alert, a finished one the finished alert`() {
        assertEquals(AlertKind.Error, PlatformRules.endedAlertKind(true, ChatStatus.Failed))
        assertEquals(AlertKind.Complete, PlatformRules.endedAlertKind(true, ChatStatus.Idle))
        assertNull(PlatformRules.endedAlertKind(true, ChatStatus.Running))
        assertNull(PlatformRules.endedAlertKind(true, ChatStatus.NeedsYou))
        assertNull(PlatformRules.endedAlertKind(false, ChatStatus.Failed))
    }

    @Test
    fun `B-MIGR-005 the Settings switches decide each kind of alert`() {
        assertFalse(PlatformRules.choiceAllows(AlertKind.Permission, requestsOn = false, finishedOn = true))
        assertFalse(PlatformRules.choiceAllows(AlertKind.Question, requestsOn = false, finishedOn = true))
        assertTrue(PlatformRules.choiceAllows(AlertKind.Complete, requestsOn = false, finishedOn = true))
        assertFalse(PlatformRules.choiceAllows(AlertKind.Complete, requestsOn = true, finishedOn = false))
        assertFalse(PlatformRules.choiceAllows(AlertKind.Error, requestsOn = true, finishedOn = false))
        assertTrue(PlatformRules.choiceAllows(AlertKind.Permission, requestsOn = true, finishedOn = false))
    }

    @Test
    fun `B-ANDR-040 coming back to the app clears outstanding alerts once`() {
        assertTrue(PlatformRules.cameToForeground(wasVisible = false, visible = true))
        assertFalse(PlatformRules.cameToForeground(wasVisible = true, visible = true))
        assertFalse(PlatformRules.cameToForeground(wasVisible = true, visible = false))
    }

    @Test
    fun `B-ANDR-030 saved pins are read in order from every folder scope`() {
        val raw = """{"[\"/work\",null]":["ses_1","ses_2"],"[\"/other\",null]":["ses_3","ses_1"]}"""
        assertEquals(listOf("ses_1", "ses_2", "ses_3"), PlatformRules.pinnedIds(raw))
        assertEquals(emptyList(), PlatformRules.pinnedIds("not json"))
        assertEquals(emptyList(), PlatformRules.pinnedIds(null))
    }

    @Test
    fun `B-ANDR-075 shared words go only to the new conversation made for them and wait until saved`() {
        val seed = ShareSeed()
        val old = ConversationRef(a, SessionId("old"))
        val made = ConversationRef(a, SessionId("new"))
        seed.arrived("look at this", setOf(old))
        assertNull(seed.target(ChatsRoute.New(a)))
        // Abandon New, open an existing chat: it never takes the words.
        assertNull(seed.target(ChatsRoute.Home))
        assertNull(seed.target(ChatRoute(old)))
        // Even straight from New, a chat that existed before is not the one made for the share.
        assertNull(seed.target(ChatsRoute.New(a)))
        assertNull(seed.target(ChatRoute(old)))
        // The new conversation takes them; a failed save keeps them for the next one.
        assertNull(seed.target(ChatsRoute.New(a)))
        assertEquals(made, seed.target(ChatRoute(made)))
        seed.notSaved(made)
        assertEquals("look at this", seed.text)
        assertNull(seed.target(ChatsRoute.New(a)))
        assertEquals(made, seed.target(ChatRoute(made)))
        seed.saved(made)
        assertNull(seed.text)
        assertEquals("draft\n\nlook at this", seed.merged("draft\n", "look at this"))
        assertEquals("look at this", seed.merged("  ", "look at this"))
    }

    @Test
    fun `B-SEC-065 only this project's GitHub release pages open, through the policy`() {
        val policy = ExternalLinkPolicy()
        val page = "https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v2.2.0%2B58"
        assertEquals(page, PlatformRules.releaseLink(policy, page)?.uri)
        assertNull(PlatformRules.releaseLink(policy, "https://github.com/someone/else/releases/tag/x"))
        assertNull(PlatformRules.releaseLink(policy, "https://github.com.evil.example/Eslamasabry/opencode-mobile-next/releases/"))
        assertNull(PlatformRules.releaseLink(policy, "intent://github.com/Eslamasabry/opencode-mobile-next/releases/"))
    }

    @Test
    fun `B-MIGR-012 Settings Updates shows the real updater, with nothing waiting too`() {
        val build = AppBuildInfo("2.2.0", 58, Channel.entries.first(), debuggable = false)
        val never = PlatformRules.updateFacts(AppUpdateState(), build) { "x" }
        assertEquals(UpdateState.NeverChecked, never.state)
        assertEquals("2.2.0", never.current)
        assertTrue(never.checksOn)
        val checked = PlatformRules.updateFacts(AppUpdateState(checkedAt = Instant.fromEpochMilliseconds(5), enabled = false), build) { "on $it" }
        assertEquals(UpdateState.UpToDate("on 5"), checked.state)
        assertFalse(checked.checksOn)
        assertEquals(UpdateState.Checking, PlatformRules.updateFacts(AppUpdateState(phase = UpdatePhase.Checking), build) { "" }.state)
    }

    @Test
    fun `B-ANDR-048 the finished alert reads the last reply's text, never a synthetic part or the question`() {
        fun msg(role: Role, vararg parts: Part) =
            MessageWithParts(MessageInfo("m", "s", role), parts.toList().toImmutableList())
        fun text(t: String, synthetic: Boolean = false) = Part("p", "m", "s", PartType.Text, "text", t, synthetic = synthetic)
        val texts =
            PlatformRules.replyTexts(
                listOf(
                    msg(Role.User, text("please fix the build")),
                    msg(Role.Assistant, text("context", synthetic = true), text("Fixed the build:\n- bumped AGP")),
                ),
            )
        assertEquals(listOf("Fixed the build:\n- bumped AGP"), texts)
        assertEquals("Fixed the build: bumped AGP", NotificationText.firstReplyLine(texts))
    }
}
