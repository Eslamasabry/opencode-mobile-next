package io.github.eslamasabry.opencode_mobile

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The picker entries and the fan-out, checked from the manifest and the sources. */
class WidgetProvidersTest {
    private val ns = "http://schemas.android.com/apk/res/android"

    private fun file(path: String): File = sequenceOf(File("src/main/$path"), File("app/src/main/$path"),
        File("android/app/src/main/$path")).first { it.isFile }

    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(file(path)).documentElement

    private fun receivers(): Map<String, String> {
        val doc = xml("AndroidManifest.xml")
        val nodes = doc.getElementsByTagName("receiver")
        val out = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val r = nodes.item(i) as Element
            val meta = r.getElementsByTagName("meta-data")
            for (j in 0 until meta.length) {
                val m = meta.item(j) as Element
                if (m.getAttributeNS(ns, "name") == "android.appwidget.provider") {
                    val resource = m.getAttributeNS(ns, "resource").removePrefix("@xml/")
                    out[r.getAttributeNS(ns, "name").removePrefix(".")] = resource
                }
            }
        }
        return out
    }

    @Test fun everyPickerEntryIsItsOwnReceiverWithItsOwnSizeFile() {
        val declared = receivers()
        val expected = WidgetProviders.sessionNames + WidgetProviders.usageNames + WidgetProviders.statusNames
        assertEquals(expected.map { it.substringAfterLast('.') }.toSet(), declared.keys)
        // One size file per entry: a launcher reads a single size from each provider.
        assertEquals(declared.size, declared.values.toSet().size)
    }

    @Test fun eachEntryDeclaresTheSizeItsNameSays() {
        val declared = receivers()
        val cells = WidgetProviders.sessionCells + WidgetProviders.usageCells + WidgetProviders.statusCells
        for ((name, cell) in cells) {
            val info = xml("res/xml/${declared.getValue(name.substringAfterLast('.'))}.xml")
            assertEquals(name, cell.first.toString(), info.getAttributeNS(ns, "targetCellWidth"))
            assertEquals(name, cell.second.toString(), info.getAttributeNS(ns, "targetCellHeight"))
            // Launchers before Android 12 read minWidth/minHeight instead.
            assertTrue(name, info.getAttributeNS(ns, "minWidth").endsWith("dp"))
            assertTrue(name, info.getAttributeNS(ns, "minHeight").endsWith("dp"))
            // Each entry keeps its size: another size is another entry (owner,
            // 2026-10-29). The resize frame stays, because launchers put the
            // widget's Edit button on it, but it cannot grow or shrink.
            assertEquals(name, "horizontal|vertical", info.getAttributeNS(ns, "resizeMode"))
            for (axis in listOf("Width", "Height")) {
                val size = info.getAttributeNS(ns, "min$axis")
                assertEquals(name, size, info.getAttributeNS(ns, "minResize$axis"))
                assertEquals(name, size, info.getAttributeNS(ns, "maxResize$axis"))
            }
            // Launcher3 keeps targetCellWidth only inside the span the locked
            // size gives. 250dp came to 3 cells on a Pixel grid (about 97dp a
            // cell with its gap), so the four-wide entries lock at 340dp.
            if (cell.first == 4) {
                assertTrue(name, info.getAttributeNS(ns, "minWidth").removeSuffix("dp").toInt() >= 300)
            }
            assertTrue(name, info.getAttributeNS(ns, "previewImage").startsWith("@drawable/"))
            assertTrue(name, info.getAttributeNS(ns, "description").startsWith("@string/"))
        }
    }

    @Test fun theDefaultEntriesStayTwoByTwoAndWideEntriesAreWide() {
        assertEquals(2 to 2, WidgetProviders.sessionCells.getValue(SessionsWidgetProvider::class.java.name))
        assertEquals(2 to 2, WidgetProviders.usageCells.getValue(UsageWidgetProvider::class.java.name))
        assertEquals(setOf(2 to 2, 4 to 2, 4 to 4, 4 to 1), WidgetProviders.sessionCells.values.toSet())
        assertEquals(setOf(2 to 2, 4 to 2), WidgetProviders.usageCells.values.toSet())
        assertEquals(2 to 2, WidgetProviders.statusCells.getValue(StatusWidgetProvider::class.java.name))
        // The 4x2 entry's declared minimum reaches the wide layout's breakpoint (250dp wide).
        val wide = xml("res/xml/widget_sessions_wide_info.xml")
        assertTrue(wide.getAttributeNS(ns, "minWidth").removeSuffix("dp").toInt() >= 250)
        // Before Android 12 the launcher reports the declared minimum; it must still pick the wide card.
        assertEquals(WidgetV2Policy.Size.WIDE, WidgetSizePolicy.size(
            wide.getAttributeNS(ns, "minWidth").removeSuffix("dp").toInt(),
            WidgetSizePolicy.legacyHeight(wide.getAttributeNS(ns, "minHeight").removeSuffix("dp").toInt())))
        val list = xml("res/xml/widget_sessions_list_info.xml")
        assertEquals(WidgetV2Policy.Size.LIST, WidgetSizePolicy.size(
            list.getAttributeNS(ns, "minWidth").removeSuffix("dp").toInt(),
            WidgetSizePolicy.legacyHeight(list.getAttributeNS(ns, "minHeight").removeSuffix("dp").toInt())))
        val strip = xml("res/xml/widget_sessions_strip_info.xml")
        assertEquals(WidgetV2Policy.Size.STRIP, WidgetSizePolicy.size(
            strip.getAttributeNS(ns, "minWidth").removeSuffix("dp").toInt(),
            WidgetSizePolicy.legacyHeight(strip.getAttributeNS(ns, "minHeight").removeSuffix("dp").toInt())))
        // The default 2x2 entry stays the card.
        val card = xml("res/xml/widget_sessions_info.xml")
        assertEquals(WidgetV2Policy.Size.CARD, WidgetSizePolicy.size(
            card.getAttributeNS(ns, "minWidth").removeSuffix("dp").toInt(),
            WidgetSizePolicy.legacyHeight(card.getAttributeNS(ns, "minHeight").removeSuffix("dp").toInt())))
        // A one-row minimum is not lifted; a launcher's real 4x1 (about 100dp) is a strip.
        assertEquals(40, WidgetSizePolicy.legacyHeight(40))
        assertEquals(WidgetV2Policy.Size.STRIP, WidgetSizePolicy.size(320, 103))
    }

    @Test fun entriesShareTheRendererAndKeepTheSameBehaviour() {
        for (cls in WidgetProviders.sessions) {
            assertTrue(cls.name, SessionsWidgetProvider::class.java.isAssignableFrom(cls))
        }
        for (cls in WidgetProviders.usage) assertTrue(cls.name, UsageWidgetProvider::class.java.isAssignableFrom(cls))
        for (cls in WidgetProviders.status) assertTrue(cls.name, StatusWidgetProvider::class.java.isAssignableFrom(cls))
        // Existing placed widgets keep the original receivers.
        assertEquals(SessionsWidgetProvider::class.java, WidgetProviders.sessions.first())
        assertEquals(UsageWidgetProvider::class.java, WidgetProviders.usage.first())
    }

    @Test fun everyRedrawAndOwnershipCheckWalksTheWholeList() {
        val dir = sequenceOf(File("src/main/kotlin/io/github/eslamasabry/opencode_mobile"),
            File("app/src/main/kotlin/io/github/eslamasabry/opencode_mobile"),
            File("android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile")).first { it.isDirectory }
        val sources = dir.listFiles { f -> f.extension == "kt" }!!
        for (f in sources) {
            val text = f.readText()
            for (match in Regex("getAppWidgetIds\\(\\s*ComponentName\\([^)]*\\)").findAll(text)) {
                // Only the fan-out helpers may look widgets up, and only through the provider list.
                val componentName = Regex("ComponentName\\(context,\\s*(provider|it)\\)")
                assertTrue("${f.name}: ${match.value}", match.value.contains(componentName))
                assertTrue(f.name, text.contains("WidgetProviders."))
            }
            // Nothing may single out one provider class to decide ownership.
            assertTrue(f.name, !Regex("className in setOf\\(").containsMatchIn(text) || f.name == "WidgetProviders.kt")
        }
    }

    @Test fun everyEntryHasAnEnglishAndArabicLabelAndDescription() {
        val declared = xml("AndroidManifest.xml")
        val labels = mutableSetOf<String>()
        val nodes = declared.getElementsByTagName("receiver")
        for (i in 0 until nodes.length) {
            val r = nodes.item(i) as Element
            val l = r.getAttributeNS(ns, "label")
            if (l.startsWith("@string/widget_label") || l.startsWith("@string/usage_widget_title")) {
                labels.add(l.removePrefix("@string/"))
            }
        }
        assertTrue(
            labels.containsAll(
                listOf(
                    "widget_label", "widget_label_wide", "widget_label_list", "widget_label_strip",
                    "usage_widget_title", "usage_widget_title_wide", "widget_label_status",
                ),
            ),
        )
        for (dir in listOf("values", "values-ar")) {
            val text = file("res/$dir/strings.xml").readText()
            val descriptions = listOf(
                "widget_description_wide", "widget_description_list", "widget_description_strip",
                "usage_widget_description_wide", "widget_description_status",
            )
            for (key in labels + descriptions) assertTrue("$dir $key", text.contains("name=\"$key\""))
        }
    }

    @Test fun thePickerOffersExactlyConversationsAndStatus() {
        val declared = receivers()
        val shown = declared.filter { (_, res) ->
            !xml("res/xml/$res.xml").getAttributeNS(ns, "widgetFeatures").split('|').contains("hide_from_picker")
        }.keys
        // Owner, 2026-10-29: at most two widgets, each with one job.
        assertEquals(WidgetProviders.picker.map { it.substringAfterLast('.') }.toSet(), shown)
        assertEquals(setOf("SessionsWideWidgetProvider", "StatusWidgetProvider"), shown)
        assertEquals(4 to 2, WidgetProviders.sessionCells.getValue(SessionsWideWidgetProvider::class.java.name))
        assertEquals(2 to 2, WidgetProviders.statusCells.getValue(StatusWidgetProvider::class.java.name))
        // Their picker names say what they are.
        val strings = file("res/values/strings.xml").readText()
        assertTrue(strings.contains("<string name=\"widget_label_wide\">Conversations</string>"))
        assertTrue(strings.contains("<string name=\"widget_label_status\">Status</string>"))
        // Retired entries stay registered, so widgets already placed keep drawing.
        assertTrue(declared.keys.containsAll(listOf("SessionsWidgetProvider", "SessionsListWidgetProvider",
            "SessionsStripWidgetProvider", "UsageWidgetProvider", "UsageWideWidgetProvider")))
    }

    @Test fun eachEntryStartsFromItsOwnLayout() {
        val declared = receivers()
        val expected = mapOf(
            "SessionsWidgetProvider" to "@layout/widget_card",
            "SessionsWideWidgetProvider" to "@layout/widget_conversations",
            "SessionsListWidgetProvider" to "@layout/widget_card",
            "SessionsStripWidgetProvider" to "@layout/widget_sessions_strip",
            "StatusWidgetProvider" to "@layout/widget_status",
        )
        for ((receiver, layout) in expected) {
            val info = xml("res/xml/${declared.getValue(receiver)}.xml")
            assertEquals(receiver, layout, info.getAttributeNS(ns, "initialLayout"))
            // The picker image is rendered from the real layout (the render tests), so no
            // hand-written preview layout can drift from it.
            assertEquals(receiver, "", info.getAttributeNS(ns, "previewLayout"))
        }
    }

    @Test fun usageEntriesKeepTheirSamplePreviewLayouts() {
        for (name in listOf("widget_usage_preview", "widget_usage_preview_wide")) {
            val root = xml("res/layout/$name.xml")
            assertEquals(name, "match_parent", root.getAttributeNS(ns, "layout_width"))
            val text = file("res/layout/$name.xml").readText()
            assertTrue(name, text.contains("@string/") && !text.contains("@+id/"))
            assertTrue(name, text.contains("widget_background"))
        }
    }

    @Test fun usagePreviewsShowTwoProvidersWithTwoWindowsAndInkBarsAtDifferentFills() {
        for (name in listOf("widget_usage_preview", "widget_usage_preview_wide")) {
            val text = file("res/layout/$name.xml").readText()
            assertEquals(name, 2, Regex("widget_preview_usage_p[12]\\b").findAll(text).map { it.value }.toSet().size)
            assertEquals(name, 4, Regex("android:progress=\"(\\d+)\"").findAll(text).count())
            // Each provider has its own numbers, so the sample doesn't read as one row copied twice.
            val progress = Regex("android:progress=\"(\\d+)\"").findAll(text).map { it.groupValues[1] }.toList()
            assertEquals(name, listOf("62", "18", "34", "71"), progress)
            // Ink fill on a faint ink track, not the old green on pale.
            assertTrue(name, text.contains("widget_usage_bar_ink") && !text.contains("WidgetUsageBar"))
        }
        val bar = file("res/drawable/widget_usage_bar_ink.xml").readText()
        assertTrue(bar.contains("@color/widgetInk") && bar.contains("@color/widgetBarTrack"))
        // The live track is the ink at 0x38 alpha; the static preview colours say the same in both themes.
        val light = file("res/values/colors.xml").readText()
        val dark = file("res/values-night/colors.xml").readText()
        assertTrue(light.contains("name=\"widgetBarTrack\">#381A1B1E"))
        assertTrue(dark.contains("name=\"widgetBarTrack\">#38F3F3F1"))
        assertEquals(0x38, WidgetUsageStyle.usageBar(0xFFF3F3F1.toInt(), 0, 50, false).second ushr 24)
    }

    @Test fun everyEntryDeclaresAPreviewImageWithBothThemesOnDisk() {
        // Launchers that cannot inflate previewLayout (ZTE/Nubia) use previewImage only.
        for ((receiver, res) in receivers()) {
            val info = xml("res/xml/$res.xml")
            val image = info.getAttributeNS(ns, "previewImage")
            if (receiver.startsWith("Usage")) {
                assertTrue("$receiver previewLayout", info.getAttributeNS(ns, "previewLayout").startsWith("@layout/"))
            }
            assertTrue("$receiver previewImage", image.startsWith("@drawable/"))
            val name = image.removePrefix("@drawable/")
            for (dir in listOf("drawable-nodpi", "drawable-night-nodpi")) {
                val f = file("res/$dir/$name.png")
                assertTrue("$receiver $dir/$name.png", f.length() in 1..150_000)
            }
        }
    }
}
