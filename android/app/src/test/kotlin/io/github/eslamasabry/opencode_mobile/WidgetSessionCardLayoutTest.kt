package io.github.eslamasabry.opencode_mobile

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Checks the XML used by RemoteViews without needing a launcher or emulator. */
class WidgetSessionCardLayoutTest {
    @Test fun everyLayoutKeepsTheBackgroundIdAndReadsInTheContentDirection() {
        for (name in listOf("widget_card.xml", "widget_sessions_compact.xml", "widget_sessions_strip.xml")) {
            val root = layout(name).documentElement
            assertEquals(name, "@android:id/background", root.android("id"))
            assertEquals(name, "locale", root.android("layoutDirection"))
            // Graphite with a hairline edge; the renderer swaps in the light card.
            assertEquals(name, "@drawable/widget_card_dark", root.android("background"))
        }
    }

    @Test fun tinyShowsAWholeTitleInUpToTwoLines() {
        val title = view(layout("widget_sessions_compact.xml"), "wc_title")
        assertEquals("2", title.android("maxLines"))
        assertEquals("wrap_content", title.android("layout_height"))
        assertEquals("end", title.android("ellipsize"))
    }

    @Test fun stripKeepsTitleAndStatusOnOneLineEachWithTheActionAtTheEnd() {
        val doc = layout("widget_sessions_strip.xml")
        for (id in listOf("wc_title", "wc_sub")) assertEquals(id, "1", view(doc, id).android("maxLines"))
        val children = children(doc.documentElement).map { it.android("id").removePrefix("@+id/") }
        assertEquals("wc_tile", children.first())
        assertEquals(listOf("wc_mic", "wc_allow"), children.takeLast(2))
    }

    @Test fun theCardIsHeaderStoryRowsAndOneBottomAction() {
        val doc = layout("widget_card.xml")
        val order = children(doc.documentElement).map { it.android("id").removePrefix("@+id/") }
        assertEquals(
            listOf(
                "wc_header", "wc_gap", "wc_gap_rows", "wc_title", "wc_cmd", "wc_live_row", "wc_meta", "wc_rows",
                "wc_gap_bottom",
                "wc_buttons", "wc_field",
            ),
            order,
        )
        // Spare height is shared around the story, or goes to the rows when a tall card has them:
        // never one band under the header or over the action.
        assertEquals("1", view(doc, "wc_gap").android("layout_weight"))
        assertEquals("1", view(doc, "wc_gap_bottom").android("layout_weight"))
        assertEquals("1", view(doc, "wc_rows").android("layout_weight"))
        // The exact command is code: left to right whatever the language.
        assertEquals("ltr", view(doc, "wc_cmd").android("textDirection"))
        assertEquals("monospace", view(doc, "wc_cmd").android("fontFamily"))
        // One main action in solid mint, Open beside it as an outline.
        assertEquals("@drawable/widget_btn_mint_dark", view(doc, "wc_allow").android("background"))
        assertEquals("@drawable/widget_btn_outline_dark", view(doc, "wc_open").android("background"))
        // No raw reply text and no flipper: one thing at a time.
        val text = file("res/layout/widget_card.xml").readText()
        assertTrue(!text.contains("wc_preview") && !text.contains("ViewFlipper"))
    }

    @Test fun cardRowsAreQuietRowsWithoutButtons() {
        val doc = layout("widget_card_row.xml")
        assertEquals("1", view(doc, "wc_row_title").android("maxLines"))
        val text = file("res/layout/widget_card_row.xml").readText()
        assertTrue(!text.contains("wc_allow") && !text.contains("wc_btn"))
    }

    @Test fun noColourInTheWidgetDrawablesIsRed() {
        val dir = File(resources(), "drawable")
        for (f in dir.listFiles { f -> f.name.startsWith("widget_") }!!) {
            for (hex in Regex("#([0-9A-Fa-f]{8})").findAll(f.readText()).map { it.groupValues[1] }) {
                val rgb = hex.substring(2).toInt(16)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                // Red reads as red: the red channel well above both others, and a hue under about
                // 25 degrees (amber, the needs-you Allow, sits near 36 and is allowed).
                val hsv = FloatArray(3)
                java.awt.Color.RGBtoHSB(r, g, b, hsv)
                val hue = hsv[0] * 360f
                assertTrue("${f.name} #$hex", !(r > 160 && r > g + 60 && r > b + 60 && (hue < 25f || hue > 335f)))
            }
        }
    }

    private fun resources(): File =
        sequenceOf(File("src/main/res"), File("app/src/main/res"), File("android/app/src/main/res"))
            .first { it.isDirectory }

    private fun file(path: String): File = sequenceOf(File("src/main/$path"), File("app/src/main/$path"),
        File("android/app/src/main/$path")).first { it.isFile }

    @Test fun noOldCopyOrModelIdSurvivesInTheWidgetResources() {
        val resources = sequenceOf(File("src/main/res"), File("app/src/main/res"), File("android/app/src/main/res"))
            .first { it.isDirectory }
        val text = resources.walkTopDown().filter { it.isFile && it.name == "widget_v2_strings.xml" }
            .joinToString("\n") { it.readText() }
        for (banned in listOf("All caught up", "Connects when you tap", "Background connection unavailable",
            "last known", "Open conversation")) {
            assertTrue(banned, !text.contains(banned))
        }
        // Every English string has an Arabic counterpart.
        fun names(dir: String) = Regex("name=\"([a-z0-9_]+)\"")
            .findAll(File(resources, "$dir/widget_v2_strings.xml").readText()).map { it.groupValues[1] }.toSet()
        assertEquals(names("values"), names("values-ar"))
    }

    private fun children(parent: Element): List<Element> =
        (0 until parent.childNodes.length).map { parent.childNodes.item(it) }.filterIsInstance<Element>()

    @Test fun defaultTwoByTwoCardKeepsItsSize() {
        val source = sequenceOf(File("src/main/res/xml/widget_sessions_info.xml"),
            File("app/src/main/res/xml/widget_sessions_info.xml"),
            File("android/app/src/main/res/xml/widget_sessions_info.xml")).first { it.isFile }
        val provider = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(source).documentElement
        assertEquals("2", provider.android("targetCellWidth"))
        assertEquals("2", provider.android("targetCellHeight"))
        assertEquals("horizontal|vertical", provider.android("resizeMode"))
        assertEquals("110dp", provider.android("maxResizeWidth"))
    }

    @Test fun wallpaperMutedCopyFollowsSystemThemeInsteadOfCachedAppTheme() {
        val source = sequenceOf(File("src/main/kotlin/io/github/eslamasabry/opencode_mobile/WidgetV2Style.kt"),
            File("app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WidgetV2Style.kt"),
            File("android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WidgetV2Style.kt"))
            .first { it.isFile }.readText()
        val palette = source.substringAfter("return Palette(background,").substringBefore("private fun themeColor")
        assertTrue(palette.contains("else appText, if (wallpaper) context.getColor("))
        assertTrue(
            palette.contains(
                "if (dark) android.R.color.system_neutral1_300 else android.R.color.system_neutral1_700"
            )
        )
        assertTrue(palette.contains(") else appMuted, accent)"))
    }

    private fun layout(name: String): org.w3c.dom.Document {
        val source = sequenceOf(File("src/main/res/layout/$name"), File("app/src/main/res/layout/$name"),
            File("android/app/src/main/res/layout/$name")).first { it.isFile }
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(source)
    }

    private fun view(doc: org.w3c.dom.Document, id: String): Element {
        val all = doc.getElementsByTagName("*")
        return (0 until all.length).map { all.item(it) as Element }
            .first { it.android("id") == "@+id/$id" }
    }

    private fun Element.android(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)
}
