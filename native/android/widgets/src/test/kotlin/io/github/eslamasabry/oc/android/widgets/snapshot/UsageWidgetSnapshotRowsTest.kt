package io.github.eslamasabry.oc.android.widgets.snapshot

import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Flutter `usage_widget_snapshot_test.dart` facts on the native usage widget store (B-ANDR-100). */
class UsageWidgetSnapshotRowsTest {
    private val now = 1_791_806_400_000L // 2026-10-12T12:00Z
    private val prefs = mutableMapOf<String, String>()
    private var redraws = 0

    private fun store(capabilities: PlatformCapabilities = PlatformCapabilities.android()) =
        UsageWidgetSnapshotStore(
            capabilities = capabilities,
            read = { prefs[it] },
            write = { key, value ->
                if (value == null) prefs.remove(key) else prefs[key] = value
                true
            },
            redraw = { redraws++ },
            now = { now },
        )

    private fun reading(provider: String = "codex", used: Double = 38.0, usable: Boolean = true) =
        UsageQuotaReading(
            provider = provider,
            usable = usable,
            fetchedAt = now,
            windows =
                if (!usable) emptyList()
                else listOf(UsageQuotaWindow(seconds = 18_000, remainingPercent = 100 - used, resetsAt = now + 3 * 3_600_000)),
        )

    private fun stored(profile: String): JsonObject =
        Json.parseToJsonElement(prefs.getValue(UsageWidgetSnapshotRows.key(profile))).jsonObject

    private fun row(profile: String, provider: String): JsonObject =
        stored(profile).getValue("rows").jsonObject.getValue(provider).jsonObject

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    @Test
    fun `B-ANDR-100 writes remaining percent, window length, reset and fetch time`() = runTest {
        val widget = store()
        widget.record("p1", "Home", reading())
        val expected = buildJsonObject {
            put("name", "Home")
            put(
                "rows",
                buildJsonObject {
                    put(
                        "codex",
                        buildJsonObject {
                            put("state", "ok")
                            put("fetchedAt", now)
                            put(
                                "windows",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("seconds", 18_000)
                                            put("left", 62)
                                            put("resetsAt", now + 3 * 3_600_000)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }
        assertEquals(expected, stored("p1"))
        assertEquals(1, redraws)
        // The same numbers do not redraw again.
        widget.record("p1", "Home", reading())
        assertEquals(1, redraws)
    }

    @Test
    fun `B-ANDR-100 keeps several providers of one server in one entry`() = runTest {
        val widget = store()
        widget.record("p1", "Home", reading())
        widget.record("p1", "Home", reading(provider = "minimax", used = 10.0))
        assertTrue(stored("p1").getValue("rows").jsonObject.keys.containsAll(listOf("codex", "minimax")))
    }

    @Test
    fun `B-ANDR-100 Claude and unusable readings never appear`() = runTest {
        val widget = store()
        widget.record("p1", "Home", reading(provider = "claude"))
        widget.record("p1", "Home", reading(usable = false))
        assertNull(prefs[UsageWidgetSnapshotRows.key("p1")])
        assertEquals(0, redraws)
        widget.markAuthNeeded("p1", "Home", "claude")
        assertNull(prefs[UsageWidgetSnapshotRows.key("p1")])
    }

    @Test
    fun `B-ANDR-100 auth-needed keeps the numbers and flags the row`() = runTest {
        val widget = store()
        widget.record("p1", "Home", reading())
        widget.markAuthNeeded("p1", "Home", "codex")
        val flagged = row("p1", "codex")
        assertEquals("auth", flagged.str("state"))
        assertEquals(now.toString(), flagged.str("fetchedAt"))
        assertEquals(62, flagged.getValue("windows").jsonArray.single().jsonObject.getValue("left").jsonPrimitive.int)
        // A fresh measurement clears the flag.
        widget.record("p1", "Home", reading(used = 40.0))
        assertEquals("ok", row("p1", "codex").str("state"))
    }

    @Test
    fun `B-ANDR-100 removing the last provider removes the entry`() = runTest {
        val widget = store()
        widget.record("p1", "Home", reading())
        widget.removeProvider("p1", "codex")
        assertNull(prefs[UsageWidgetSnapshotRows.key("p1")])
    }

    @Test
    fun `B-ANDR-100 is inert where there are no home widgets`() = runTest {
        store(PlatformCapabilities.desktop()).record("p1", "Home", reading())
        assertTrue(prefs.isEmpty())
    }

    @Test
    fun `B-ANDR-100 clearing one server removes only its entry and redraws`() = runTest {
        val widget = store()
        widget.record("p1", "Home", reading())
        widget.record("p2", "Other", reading())
        redraws = 0
        assertTrue(widget.clearForProfile("p2"))
        assertNull(prefs[UsageWidgetSnapshotRows.key("p2")])
        assertNotNull(prefs[UsageWidgetSnapshotRows.key("p1")])
        assertEquals(1, redraws)
        assertTrue(widget.clearForProfile("p2"))
        assertEquals(1, redraws)
    }

    @Test
    fun `B-ANDR-100 not signed in writes a row with the way in and no numbers`() = runTest {
        val widget = store()
        widget.markNotSignedIn("p1", "Home", "glm")
        val glm = row("p1", "glm")
        assertEquals("signin", glm.str("state"))
        assertEquals("add_key", glm.str("action"))
        assertTrue(glm.getValue("windows").jsonArray.isEmpty())
        widget.markNotSignedIn("p1", "Home", "codex")
        assertEquals("sign_in", row("p1", "codex").str("action"))
        // Numbers arriving replace the row, target and all.
        widget.record("p1", "Home", reading())
        val ok = row("p1", "codex")
        assertEquals("ok", ok.str("state"))
        assertFalse("action" in ok)
        // The server's data goes away as a whole.
        assertTrue(widget.clearForProfile("p1"))
        assertNull(prefs[UsageWidgetSnapshotRows.key("p1")])
    }

    @Test
    fun `B-ANDR-100 not signed in keeps the numbers of a row that has them`() = runTest {
        val widget = store()
        widget.record("p1", "Home", reading())
        widget.markNotSignedIn("p1", "Home", "codex")
        val codex = row("p1", "codex")
        assertEquals("ok", codex.str("state"))
        assertTrue(codex.getValue("windows").jsonArray.isNotEmpty())
    }

    @Test
    fun `B-ANDR-100 an expired sign-in with no row yet still gets a tap target`() = runTest {
        store().markAuthNeeded("p1", "Home", "codex")
        val codex = row("p1", "codex")
        assertEquals("auth", codex.str("state"))
        assertEquals("sign_in", codex.str("action"))
        assertTrue(codex.getValue("windows").jsonArray.isEmpty())
    }

    @Test
    fun `B-ANDR-100 readings replace a server's providers and offer sign-in rows`() = runTest {
        val widget = store()
        widget.record("p1", "Home", reading(provider = "minimax"))
        widget.recordReadings(
            "p1",
            "Home",
            listOf(
                UsageProviderReading("codex", "Codex", now, needsSignIn = false, windows = listOf(UsageReadingWindow(18_000, null, 120.0, null))),
                UsageProviderReading("glm", "GLM", now, needsSignIn = true, windows = emptyList()),
                UsageProviderReading("empty", "Empty", now, needsSignIn = false, windows = emptyList()),
            ),
            thisPhone = true,
        )
        val all = stored("p1")
        assertEquals(setOf("codex", "glm"), all.getValue("rows").jsonObject.keys)
        assertEquals("true", all.str("local"))
        assertEquals(100, row("p1", "codex").getValue("windows").jsonArray.single().jsonObject.getValue("left").jsonPrimitive.int)
        assertEquals(JsonNull, row("p1", "codex").getValue("windows").jsonArray.single().jsonObject["resetsAt"])
        val glm = row("p1", "glm")
        assertEquals("signin", glm.str("state"))
        assertEquals("add_key", glm.str("action"))
        assertEquals("GLM", glm.str("name"))
    }

    @Test
    fun `B-ANDR-100 stores no address, password or account words`() = runTest {
        val widget = store()
        widget.record("p1", "Home", reading())
        val raw = prefs.getValue(UsageWidgetSnapshotRows.key("p1"))
        for (forbidden in listOf("http", "ref", "plus", "password")) assertFalse(forbidden in raw)
        // An invalid provider id never becomes a row.
        widget.markNotSignedIn("p2", "Home", "Bad Id!")
        assertNull(prefs[UsageWidgetSnapshotRows.key("p2")])
    }
}
