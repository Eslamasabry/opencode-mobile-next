package io.github.eslamasabry.oc.android.tiles

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The tile contract, read from the library manifest and resources as files (no Android runtime). */
class TilesManifestTest {
    private val ns = "http://schemas.android.com/apk/res/android"

    private fun file(path: String): File =
        sequenceOf(File("src/main/$path"), File("tiles/src/main/$path"), File("android/tiles/src/main/$path"))
            .first { it.isFile }

    private fun services(): Map<String, Element> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file("AndroidManifest.xml")).documentElement
        val nodes = doc.getElementsByTagName("service")
        val out = linkedMapOf<String, Element>()
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            out[e.getAttributeNS(ns, "name")] = e
        }
        return out
    }

    @Test
    fun `B-ANDR-031 both tiles keep their frozen names and are bound only by the system`() {
        val declared = services()
        assertEquals(setOf(TilesComponents.ATTENTION_TILE, TilesComponents.PAUSE_TILE), declared.keys)
        for ((name, service) in declared) {
            assertEquals(name, "android.permission.BIND_QUICK_SETTINGS_TILE", service.getAttributeNS(ns, "permission"))
            assertEquals(name, "true", service.getAttributeNS(ns, "exported"))
            assertEquals(name, "@drawable/tile_ic_mark", service.getAttributeNS(ns, "icon"))
            val actions = service.getElementsByTagName("action")
            assertEquals(name, 1, actions.length)
            assertEquals(
                name,
                "android.service.quicksettings.action.QS_TILE",
                (actions.item(0) as Element).getAttributeNS(ns, "name"),
            )
        }
        assertEquals("@string/tile_label", declared.getValue(TilesComponents.ATTENTION_TILE).getAttributeNS(ns, "label"))
        assertEquals("@string/pause_tile_label", declared.getValue(TilesComponents.PAUSE_TILE).getAttributeNS(ns, "label"))
    }

    @Test
    fun `B-ANDR-031 the tile icon and shortcut icons exist and copy is localized`() {
        for (name in listOf("tile_ic_mark", "ic_shortcut_connect", "ic_shortcut_new_task", "ic_shortcut_session")) {
            assertTrue(name, file("res/drawable/$name.xml").isFile)
        }
        val en = file("res/values/strings_tiles.xml").readText()
        val ar = file("res/values-ar/strings_tiles.xml").readText()
        val names = listOf(
            "tile_label", "tile_label_with_count", "tile_all_clear", "tile_need_you", "pause_tile_label",
            "pause_tile_label_with_state", "pause_tile_on", "pause_tile_paused", "pause_tile_off",
            "shortcut_session_untitled", "shortcut_session_unavailable", "shortcut_connect_short",
            "shortcut_connect_long", "shortcut_new_task_short", "shortcut_new_task_long",
        )
        for (n in names) {
            assertTrue(n, en.contains("name=\"$n\""))
            assertTrue(n, ar.contains("name=\"$n\""))
        }
        assertTrue(en.contains(">Off in Settings<"))
    }
}
