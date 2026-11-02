package io.github.eslamasabry.oc.android.platform

import io.github.eslamasabry.oc.engine.entry.EntryIntentParser
import io.github.eslamasabry.oc.engine.entry.EntryOrigin
import io.github.eslamasabry.oc.engine.entry.EntryRequest
import io.github.eslamasabry.oc.engine.entry.IntentFields
import io.github.eslamasabry.oc.engine.entry.LaunchAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The static shortcut resources (shipped by this module) and the MainActivity entry filters (proposed fixture). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StaticShortcutsTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private data class Declared(val id: String, val action: String?, val pkg: String?, val cls: String?, val extras: Map<String, String>)

    /** Reads the compiled XML resource exactly as the launcher does. */
    private fun declared(resId: Int): List<Declared> {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(resId)
        val out = mutableListOf<Declared>()
        var id: String? = null
        var intent: Triple<String?, String?, String?>? = null
        val extras = mutableMapOf<String, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "shortcut" -> id = parser.getAttributeValue(androidNs, "shortcutId")
                    "intent" ->
                        intent =
                            Triple(
                                parser.getAttributeValue(androidNs, "action"),
                                parser.getAttributeValue(androidNs, "targetPackage"),
                                parser.getAttributeValue(androidNs, "targetClass"),
                            )
                    "extra" -> extras[parser.getAttributeValue(androidNs, "name")] = parser.getAttributeValue(androidNs, "value")
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "shortcut") {
                out += Declared(id.orEmpty(), intent?.first, intent?.second, intent?.third, extras.toMap())
                extras.clear()
            }
        }
        return out
    }

    @Test
    fun `B-ANDR-029 every declared shortcut targets MainActivity with an id on the whitelist and the native whitelist matches`() {
        val stable = declared(R.xml.shortcuts)
        assertEquals(StaticShortcuts.ids, stable.map { it.id })
        for (shortcut in stable) {
            assertEquals("android.intent.action.MAIN", shortcut.action)
            assertEquals(StaticShortcuts.STABLE_PACKAGE, shortcut.pkg)
            assertEquals(StaticShortcuts.MAIN_ACTIVITY, shortcut.cls)
            assertEquals(mapOf(StaticShortcuts.EXTRA to shortcut.id), shortcut.extras)
            // The value the launcher sends is exactly what EntryIntentReader accepts, and it arrives untrusted.
            val requests =
                EntryIntentParser.parse(
                    IntentFields(action = shortcut.action, strings = shortcut.extras),
                    EntryOrigin.Untrusted,
                    FixedClock.now(),
                )
            val launch = requests.single() as EntryRequest.Launch
            assertEquals(EntryOrigin.Untrusted, launch.origin)
            assertEquals(if (shortcut.id == "connect") LaunchAction.Connect else LaunchAction.NewTask, launch.action)
        }
    }

    @Test
    fun `B-ANDR-029 the preview build differs only in the package it opens`() {
        val stable = declared(R.xml.shortcuts)
        val preview = declared(R.xml.shortcuts_native)
        assertEquals(stable.map { it.copy(pkg = StaticShortcuts.PREVIEW_PACKAGE) }, preview)
        assertEquals("@xml/shortcuts", StaticShortcuts.resourceFor(StaticShortcuts.STABLE_PACKAGE))
        assertEquals("@xml/shortcuts_native", StaticShortcuts.resourceFor(StaticShortcuts.PREVIEW_PACKAGE))
    }

    @Test
    fun `B-ANDR-029 shortcut labels and icons exist in English and Arabic`() {
        val res = RuntimeEnvironment.getApplication().resources
        listOf(R.string.shortcut_connect_short, R.string.shortcut_connect_long, R.string.shortcut_new_task_short, R.string.shortcut_new_task_long)
            .forEach { assertTrue(res.getString(it).isNotBlank()) }
        assertTrue(R.drawable.ic_shortcut_connect != 0 && R.drawable.ic_shortcut_new_task != 0)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `B-ANDR-029 Arabic shortcut labels`() {
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals("محادثة جديدة", res.getString(R.string.shortcut_new_task_short))
    }

    private fun mainActivityFixture(): Element {
        val stream = javaClass.classLoader!!.getResourceAsStream("entry/main-activity.xml")!!
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(stream)
        return doc.getElementsByTagName("activity").item(0) as Element
    }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.attr(name: String): String = getAttributeNS(androidNs, name)

    @Test
    fun `B-ANDR-072 the manifest declares exactly one VIEW filter for the session link whose scheme and host match the link shape`() {
        val activity = mainActivityFixture()
        assertEquals(".MainActivity", activity.attr("name"))
        assertEquals("singleTop", activity.attr("launchMode"))
        assertEquals("", activity.attr("taskAffinity"))
        val filters = activity.children("intent-filter")
        val view = filters.filter { f -> f.children("action").any { it.attr("name") == "android.intent.action.VIEW" } }
        assertEquals(1, view.size)
        val categories = view.single().children("category").map { it.attr("name") }.toSet()
        assertEquals(setOf("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"), categories)
        val data = view.single().children("data").map { it.attr("scheme") to it.attr("host") }.toSet()
        assertEquals(setOf("opencode-mobile" to "session", "opencode-mobile" to "team"), data)

        // Every filter's actions, exactly (16 §1.8.1: what any app can make MainActivity do).
        val actions = filters.map { f -> f.children("action").map { it.attr("name") } }.toSet()
        assertEquals(
            setOf(
                listOf("android.intent.action.MAIN"),
                listOf("android.intent.action.SEND"),
                listOf("android.intent.action.VIEW"),
                listOf("android.service.quicksettings.action.QS_TILE_PREFERENCES"),
            ),
            actions,
        )
        val send = filters.single { f -> f.children("action").any { it.attr("name") == "android.intent.action.SEND" } }
        assertEquals(listOf("text/plain"), send.children("data").map { it.attr("mimeType") })
        val meta = activity.children("meta-data").single()
        assertEquals("android.app.shortcuts", meta.attr("name"))
        assertEquals("\${${StaticShortcuts.MANIFEST_PLACEHOLDER}}", meta.attr("resource"))
    }
}
