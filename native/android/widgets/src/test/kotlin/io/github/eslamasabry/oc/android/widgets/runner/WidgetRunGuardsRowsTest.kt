package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.engine.profiles.StoredProfileJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The durable ownership guards of test/widget_action_runner_test.dart (private target, profile switch, deletion). */
class WidgetRunGuardsRowsTest {
    private val profiles =
        """[{"id":"profile","name":"Server","baseUrl":"http://localhost:4096","backend":"openCode","flavor":"v2"}]"""
    private val fingerprint = widgetProfileFingerprint(StoredProfileJson.decodeList(profiles).single())
    private val tap =
        WidgetTap("tap", "allowOnce", "profile", "scope", "session", "permission", expectedStartedAt = 1)

    private fun target(scope: String = "scope") =
        """{"profileID":"profile","scopeToken":"$scope","fingerprint":"$fingerprint",""" +
            """"directory":"/private/project","workspace":"private-workspace"}"""

    private fun snapshot(marker: String = "user-1", request: String = "permission") =
        """{"version":2,"profileID":"profile","scopeToken":"scope","onDemand":{"fingerprint":"$fingerprint"},""" +
            """"sessions":[{"id":"session","busy":true,"startedAt":1,"requestID":"$request","runMarker":"$marker",""" +
            """"onDemandActions":["allowOnce","reject","stop"]}]}"""

    private fun prefs() =
        mutableMapOf(
            "oc.profiles" to profiles,
            "oc.activeProfile" to "profile",
            WidgetRunGuards.SNAPSHOT to snapshot(),
            WidgetRunGuards.TARGET_PREFIX + "profile" to target(),
            "oc.backgroundLive" to "false",
        )

    @Test
    fun `B-SEC-180 the private target reader rejects a mismatched identity and a deleted target`() {
        val prefs = prefs()
        val guards = WidgetRunGuards(prefs::get)
        assertEquals("/private/project", guards.target(tap, fingerprint)?.get("directory"))
        assertNull(guards.target(tap, "changed"))
        prefs[WidgetRunGuards.TARGET_PREFIX + "profile"] = target(scope = "changed")
        assertNull(guards.target(tap, fingerprint))
        prefs.remove(WidgetRunGuards.TARGET_PREFIX + "profile")
        assertNull(guards.target(tap, fingerprint))
    }

    @Test
    fun `B-SEC-180 the durable ownership guard follows a profile switch and deletion without writes`() {
        val prefs = prefs()
        val before = prefs.toMap()
        val guards = WidgetRunGuards(prefs::get)
        assertTrue(guards.actionIsCurrent(tap, fingerprint))
        assertEquals(before, prefs)
        prefs["oc.activeProfile"] = "other"
        assertFalse(guards.actionIsCurrent(tap, fingerprint))
        prefs["oc.activeProfile"] = "profile"
        prefs["oc.profiles"] = "[]"
        assertFalse(guards.actionIsCurrent(tap, fingerprint))
        assertEquals("false", prefs["oc.backgroundLive"])
    }

    @Test
    fun `B-SEC-180 a same-scope durable replacement during the read blocks the old stop and the old answer`() {
        val prefs = prefs()
        val original = io.github.eslamasabry.oc.core.common.dart.DartJson.decodeOrNull(snapshot()) as Map<*, *>
        val guards = WidgetRunGuards(prefs::get)
        prefs[WidgetRunGuards.SNAPSHOT] = snapshot(marker = "replacement")
        assertFalse(guards.actionIsCurrent(tap.copy(action = "stop"), fingerprint, original))
        prefs[WidgetRunGuards.SNAPSHOT] = snapshot(marker = "replacement", request = "new-request")
        assertFalse(guards.actionIsCurrent(tap, fingerprint, original))
        // Unchanged, both are current.
        prefs[WidgetRunGuards.SNAPSHOT] = snapshot()
        assertTrue(guards.actionIsCurrent(tap.copy(action = "stop"), fingerprint, original))
        assertTrue(guards.actionIsCurrent(tap, fingerprint, original))
    }

    @Test
    fun `B-ANDR-121 a location switch gives a new scope token, so existing launcher actions are revoked`() {
        var n = 0
        val scope = WidgetActionScope { size -> ByteArray(size) { (n++).toByte() } }
        val first = scope.token(listOf("profile", "gateway-1", "/a", null))
        assertEquals(first, scope.token(listOf("profile", "gateway-1", "/a", null)))
        val moved = scope.token(listOf("profile", "gateway-1", "/b", null))
        assertFalse(first == moved)
        val prefs = prefs()
        prefs[WidgetRunGuards.SNAPSHOT] = snapshot().replace("\"scopeToken\":\"scope\"", "\"scopeToken\":\"$moved\"")
        assertFalse(WidgetRunGuards(prefs::get).actionIsCurrent(tap, fingerprint))
    }

    @Test
    fun `B-SEC-180 the private target round-trips through its own key and only there`() {
        val raw = WidgetActionScope.target("profile", "scope", fingerprint, "/private/project", "private-workspace")
        val prefs = prefs()
        prefs[WidgetRunGuards.TARGET_PREFIX + "profile"] = raw
        assertEquals("private-workspace", WidgetRunGuards(prefs::get).target(tap, fingerprint)?.get("workspace"))
        assertFalse(prefs[WidgetRunGuards.SNAPSHOT]!!.contains("/private/project"))
        assertEquals(listOf("allowOnce", "reject", "stop"), WidgetActionScope.actions(true, true, true, "user-1"))
        assertEquals(listOf("allowOnce", "reject"), WidgetActionScope.actions(true, true, true, null))
        assertTrue(WidgetActionScope.actions(false, true, true, "user-1").isEmpty())
    }

    @Test
    fun `B-SEC-180 an edited endpoint changes the fingerprint and the display name does not`() {
        val stored = StoredProfileJson.decodeList(profiles).single()
        assertEquals(fingerprint, widgetProfileFingerprint(stored.copy(name = "Renamed")))
        assertFalse(fingerprint == widgetProfileFingerprint(stored.copy(baseUrl = "http://localhost:4097")))
    }
}
