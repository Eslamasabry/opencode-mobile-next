package io.github.eslamasabry.oc.ui.words

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The words Android draws outside the app's screens — notifications, channels, widgets, tiles (Flutter
 * `test/native_notification_copy_test.dart`, `native_strings_test.dart`; B-L10N-013). They are Android resources
 * (`strings_os.xml` copied into `android-res`, the widgets' own `widget_v2_strings.xml`), so Android picks them from
 * the language the app runs in, and they must exist in English and Arabic with the same placeholders.
 */
class NativeResourcesTest {
    private fun entries(file: File): Map<String, Element> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        return (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
            .associateBy { it.getAttribute("name") }
    }

    private fun slots(text: String): List<String> = Regex("""%\d+\$[sd]""").findAll(text).map { it.value }.sorted().toList()

    private fun items(e: Element): List<Element> =
        (0 until e.childNodes.length).map { e.childNodes.item(it) }.filterIsInstance<Element>()

    private val osCopies = listOf(
        File(NativeTree.words, "values/strings_os.xml") to File(NativeTree.words, "values-ar/strings_os.xml"),
        File(NativeTree.root, "ui/words/android-res/values/strings_os.xml") to
            File(NativeTree.root, "ui/words/android-res/values-ar/strings_os.xml"),
    )

    private fun assertSameShape(english: Map<String, Element>, arabic: Map<String, Element>, where: String) {
        assertEquals(english.keys, arabic.keys, "$where: Arabic carries exactly the English names")
        for ((name, en) in english) {
            val ar = arabic.getValue(name)
            assertEquals(en.tagName, ar.tagName, "$where $name")
            if (en.tagName == "plurals") {
                // English "one" may say "1" in words; its "other" form carries every placeholder.
                val expected = slots(items(en).first { it.getAttribute("quantity") == "other" }.textContent)
                assertTrue(
                    items(ar).map { it.getAttribute("quantity") }.containsAll(listOf("zero", "one", "two", "few", "many", "other")),
                    "$where $name: Arabic has all six plural forms",
                )
                for (item in items(ar)) {
                    val got = slots(item.textContent)
                    // Arabic zero, one and two may name the count without its numeral.
                    if (item.getAttribute("quantity") in setOf("zero", "one", "two")) {
                        assertTrue(expected.containsAll(got), "$where $name ${item.getAttribute("quantity")}")
                    } else {
                        assertEquals(expected, got, "$where $name ${item.getAttribute("quantity")}")
                    }
                }
            } else if (name.contains("__eq")) {
                // An exact-count piece (`__eq2`): Arabic may say the count in words, as its zero/one/two forms do.
                assertTrue(slots(en.textContent).containsAll(slots(ar.textContent)), "$where $name")
            } else {
                assertEquals(slots(en.textContent), slots(ar.textContent), "$where $name")
            }
        }
    }

    @Test
    fun `B-L10N-013 notification and channel words exist in English and Arabic with the same placeholders`() {
        for ((enFile, arFile) in osCopies) {
            val english = entries(enFile)
            val arabic = entries(arFile)
            assertTrue(
                english.keys.containsAll(
                    listOf(
                        "native_alert_permission_title", "native_alert_question_title", "native_alert_complete_title",
                        "native_alert_error_title", "native_voice_setup", "native_setup_channel", "native_phone_server_body",
                    ),
                ),
                enFile.path,
            )
            assertSameShape(english, arabic, enFile.path)
        }
    }

    @Test
    fun `B-L10N-013 agent titles are localized templates naming the agent once`() {
        for ((enFile, arFile) in osCopies) {
            val english = entries(enFile)
            val arabic = entries(arFile)
            for (kind in listOf("permission", "question", "complete", "error", "checkin")) {
                val id = "native_alert_${kind}_title"
                assertEquals(listOf("%1\$s"), slots(assertNotNull(english[id], id).textContent), id)
                assertEquals(listOf("%1\$s"), slots(assertNotNull(arabic[id], id).textContent), id)
                assertFalse(arabic.getValue(id).textContent == english.getValue(id).textContent, "$id is translated")
            }
        }
    }

    @Test
    fun `B-L10N-013 the widget's fallback and time words are localized resources`() {
        val res = File(NativeTree.root, "android/widgets/src/main/res")
        assertSameShape(
            entries(File(res, "values/widget_v2_strings.xml")),
            entries(File(res, "values-ar/widget_v2_strings.xml")),
            "widget_v2_strings",
        )
        val kotlin = File(NativeTree.root, "android/widgets/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(kotlin.contains("\"Untitled conversation\""), "no English fallback title in code")
        assertFalse(kotlin.contains("-> \"now\""), "no English time word in code")
        for (resource in listOf(
            "widget_v2_empty", "widget_v2_offline_seen", "widget_v2_ago_min", "widget_v2_ago_now", "widget_v2_steer_hint",
            "widget_v2_new_in", "widget_v2_open_chat", "widget_v2_voice",
        )) {
            assertTrue(kotlin.contains("R.string.$resource"), resource)
        }
    }
}
