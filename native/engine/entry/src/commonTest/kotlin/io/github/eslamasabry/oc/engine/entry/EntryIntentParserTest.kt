package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 07 §7.1 / §7.13: what `EntryIntentReader` accepts from an Intent, as plain fields. */
class EntryIntentParserTest {
    private fun parse(fields: IntentFields, origin: EntryOrigin = EntryOrigin.Untrusted) = EntryIntentParser.parse(fields, origin, T0)

    private fun extras(vararg pairs: Pair<String, String?>) = IntentFields(strings = mapOf(*pairs))

    @Test
    fun `B-ANDR-028 the whitelist and the usage_target regex are accepted, everything else is ignored`() {
        for ((value, action) in EntryValidators.launchActions) {
            assertEquals(listOf<EntryRequest>(EntryRequest.Launch(action, T0)), parse(extras(EntryExtras.SHORTCUT to " $value ")))
        }
        assertEquals(
            listOf<EntryRequest>(EntryRequest.Launch(LaunchAction.UsageTarget(UsageTargetKind.AddKey, "glm", ProfileId("p.1")), T0)),
            parse(extras(EntryExtras.SHORTCUT to "usage_target:add_key:glm:p.1")),
        )
        for (bad in listOf(null, "", "unknown", "CONNECT", "usage_target:sign_in:GLM:p1", "usage_target:sign_in:glm:", "usage_target:delete:glm:p1")) {
            assertEquals(emptyList(), parse(extras(EntryExtras.SHORTCUT to bad)), "$bad")
        }
    }

    @Test
    fun `B-ANDR-028 the trust of a launch action comes from the receiving component, never from the extras`() {
        val fields = extras(EntryExtras.SHORTCUT to "new_task")
        assertEquals(EntryOrigin.Untrusted, parse(fields).single().origin)
        assertEquals(EntryOrigin.Internal, parse(fields, EntryOrigin.Internal).single().origin)
        // Links, shares and tile long-presses are untrusted wherever they arrive.
        val link = IntentFields(action = EntryExtras.ACTION_VIEW, data = "opencode-mobile://session?profile=p&session=s")
        assertEquals(EntryOrigin.Untrusted, parse(link, EntryOrigin.Internal).single().origin)
    }

    @Test
    fun `B-ANDR-027 a pinned-session tap needs both safe ids, else it is ignored`() {
        assertEquals(
            listOf<EntryRequest>(EntryRequest.PinnedSession(ProfileId("p1"), SessionId("ses_1"), T0)),
            parse(extras(EntryExtras.SHORTCUT_PROFILE to "p1", EntryExtras.SHORTCUT_SESSION to " ses_1 ")),
        )
        assertEquals(emptyList(), parse(extras(EntryExtras.SHORTCUT_SESSION to "ses_1")))
        assertEquals(emptyList(), parse(extras(EntryExtras.SHORTCUT_PROFILE to "p1")))
        assertEquals(emptyList(), parse(extras(EntryExtras.SHORTCUT_PROFILE to "p1", EntryExtras.SHORTCUT_SESSION to "-x")))
    }

    @Test
    fun `B-ANDR-071 only opencode-mobile session and team links are captured, within the ASCII ceiling`() {
        fun view(data: String?) = IntentFields(action = EntryExtras.ACTION_VIEW, data = data)
        val ok = "opencode-mobile://session?profile=p&session=s"
        assertEquals(listOf<EntryRequest>(EntryRequest.Link(ok, T0)), parse(view(ok)))
        assertEquals(1, parse(view("Opencode-Mobile://TEAM/gate/p/g")).size)
        for (bad in listOf(
            null,
            "https://example.com/session",
            "opencode-mobile://elsewhere?x=1",
            "opencode-mobile://session?profile=p&session=" + "a".repeat(1_100),
            "opencode-mobile://session?profile=p&session=é",
        )) {
            assertEquals(emptyList(), parse(view(bad)), "$bad")
        }
        assertEquals(emptyList(), parse(IntentFields(action = EntryExtras.ACTION_MAIN, data = ok)))
    }

    @Test
    fun `B-ANDR-075 text shares join the subject, drop empty ones and stay within 8000 chars`() {
        fun send(text: String?, subject: String? = null, type: String = "text/plain") =
            IntentFields(action = EntryExtras.ACTION_SEND, type = type, strings = mapOf(EntryExtras.TEXT to text, EntryExtras.SUBJECT to subject))
        assertEquals("Title\nbody", (parse(send("body", "Title")).single() as EntryRequest.SharedText).text)
        assertEquals("Title and body", (parse(send("Title and body", "Title")).single() as EntryRequest.SharedText).text)
        assertEquals("Only", (parse(send(null, " Only ")).single() as EntryRequest.SharedText).text)
        assertEquals(8_000, (parse(send("x".repeat(9_000))).single() as EntryRequest.SharedText).text.length)
        assertEquals(emptyList(), parse(send("  ", "")))
        assertEquals(emptyList(), parse(send("body", type = "image/png")))
    }

    @Test
    fun `B-NAV-005 an alert needs a known kind and a safe session id, and a malformed monitor token voids it`() {
        fun alert(kind: String, session: String = "ses_1", profile: String? = "p1", token: String? = null, reply: Boolean = false) =
            IntentFields(
                strings =
                    mapOf(
                        EntryExtras.ALERT_KIND to kind,
                        EntryExtras.ALERT_SESSION_ID to session,
                        EntryExtras.ALERT_PROFILE_ID to profile,
                        EntryExtras.MONITOR_TOKEN to token,
                    ),
                widgetReply = reply,
            )
        assertEquals(
            listOf<EntryRequest>(EntryRequest.AlertOpen(AlertKind.Complete, ProfileId("p1"), SessionId("ses_1"), null, true, T0)),
            parse(alert("complete", reply = true)),
        )
        assertEquals(TOKEN, (parse(alert("permission", token = TOKEN.raw)).single() as EntryRequest.AlertOpen).monitorToken)
        assertEquals(null, (parse(alert("checkin", profile = null)).single() as EntryRequest.AlertOpen).profileId)
        assertEquals(emptyList(), parse(alert("permission", token = "A".repeat(64))))
        assertEquals(emptyList(), parse(alert("permission", token = "abc")))
        assertEquals(emptyList(), parse(alert("unknown_kind")))
        assertEquals(emptyList(), parse(alert("complete", session = "")))
        assertEquals(emptyList(), parse(alert("complete", profile = "bad id")))
    }

    @Test
    fun `B-NAV-054 a scheduled-run tap needs valid profile and schedule ids`() {
        fun scheduled(profile: String?, schedule: String?, session: String? = null) =
            extras(EntryExtras.SCHEDULED_PROFILE to profile, EntryExtras.SCHEDULED_PROMPT to schedule, EntryExtras.SCHEDULED_SESSION to session)
        assertEquals(
            listOf<EntryRequest>(EntryRequest.ScheduledOpen(ProfileId("p1"), "sch_1", SessionId("ses_1"), T0)),
            parse(scheduled("p1", "sch_1", "ses_1")),
        )
        assertEquals(null, (parse(scheduled("p1", "sch_1")).single() as EntryRequest.ScheduledOpen).sessionId)
        assertEquals(emptyList(), parse(scheduled("p 1", "sch_1")))
        assertEquals(emptyList(), parse(scheduled("p1", null)))
        assertEquals(emptyList(), parse(scheduled("p1", "sch_1", "-x")))
    }

    @Test
    fun `B-ANDR-028 a tile long-press names our own tile component, anything else is ignored`() {
        fun tile(pkg: String, cls: String) = IntentFields(action = EntryExtras.ACTION_TILE_PREFERENCES, component = pkg to cls)
        assertEquals(listOf<EntryRequest>(EntryRequest.TilePreferences(TileId.Attention, T0)), parse(tile(EntryExtras.APP_PACKAGE, EntryExtras.ATTENTION_TILE)))
        assertEquals(listOf<EntryRequest>(EntryRequest.TilePreferences(TileId.Pause, T0)), parse(tile(EntryExtras.APP_PACKAGE, EntryExtras.PAUSE_TILE)))
        assertEquals(emptyList(), parse(tile("com.other", EntryExtras.ATTENTION_TILE)))
        assertEquals(emptyList(), parse(tile(EntryExtras.APP_PACKAGE, "${EntryExtras.APP_PACKAGE}.Other")))
        assertEquals(emptyList(), parse(IntentFields(action = EntryExtras.ACTION_TILE_PREFERENCES)))
    }

    @Test
    fun `B-ANDR-073 the reader strips every extra it owns so nothing can replay`() {
        val owned = EntryExtras.owned.toSet()
        assertTrue(
            owned.containsAll(
                listOf(
                    EntryExtras.SHORTCUT, EntryExtras.SHORTCUT_PROFILE, EntryExtras.SHORTCUT_SESSION, EntryExtras.ALERT_KIND,
                    EntryExtras.ALERT_SESSION_ID, EntryExtras.ALERT_PROFILE_ID, EntryExtras.MONITOR_TOKEN, EntryExtras.WIDGET_REPLY,
                    EntryExtras.SCHEDULED_PROFILE, EntryExtras.SCHEDULED_PROMPT, EntryExtras.SCHEDULED_SESSION, EntryExtras.TEXT,
                    EntryExtras.SUBJECT,
                ),
            ),
        )
    }
}
