package io.github.eslamasabry.opencode_mobile.release

import io.github.eslamasabry.oc.android.tiles.LaunchActions
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * App-level entry contracts read from the shipped app: the launcher shortcuts the app's manifest publishes
 * (B-ANDR-029, `test/launch_shortcut_native_contract_test.dart`) and the platform focus highlight on every activity a
 * person can see (B-ANDR-002, `test/android_focus_highlight_contract_test.dart`).
 */
class AppEntryContractTest {
    private val ns = "http://schemas.android.com/apk/res/android"

    private fun xml(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file).documentElement

    private fun Element.all(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    private fun mainActivity(): Element =
        xml(RepoFiles.native("androidApp/src/main/AndroidManifest.xml")).all("activity").single { it.getAttributeNS(ns, "name") == ".MainActivity" }

    // ---- B-ANDR-029 -------------------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-029 the manifest publishes the static shortcuts resource on MainActivity`() {
        val meta = mainActivity().all("meta-data").single { it.getAttributeNS(ns, "name") == "android.app.shortcuts" }
        assertEquals("@xml/shortcuts", meta.getAttributeNS(ns, "resource"))
        // The preview flavour, installed beside the stable app, overrides that resource with its own package.
        assertTrue(RepoFiles.native("androidApp/src/preview/res/xml/shortcuts.xml").isFile)
    }

    @Test
    fun `B-ANDR-029 every declared shortcut targets MainActivity with a whitelisted id, and the preview differs only in its package`() {
        val stable = RepoFiles.native("androidApp/src/main/res/xml/shortcuts.xml")
        val preview = RepoFiles.native("androidApp/src/preview/res/xml/shortcuts.xml")
        val shortcuts = xml(stable).all("shortcut")
        val ids = shortcuts.map { it.getAttributeNS(ns, "shortcutId") }
        assertEquals(listOf("connect", "new_task"), ids)
        for (shortcut in shortcuts) {
            val intent = shortcut.all("intent").single()
            assertEquals("android.intent.action.MAIN", intent.getAttributeNS(ns, "action"))
            assertEquals("io.github.eslamasabry.opencode_mobile", intent.getAttributeNS(ns, "targetPackage"))
            assertEquals("io.github.eslamasabry.opencode_mobile.MainActivity", intent.getAttributeNS(ns, "targetClass"))
            val extra = intent.all("extra").single()
            assertEquals(LaunchActions.EXTRA, extra.getAttributeNS(ns, "name"))
            assertEquals(shortcut.getAttributeNS(ns, "shortcutId"), extra.getAttributeNS(ns, "value"))
        }
        // The native whitelist MainActivity reads (LaunchActions in :android:tiles) accepts every declared id.
        for (id in ids) assertEquals(id, LaunchActions.parse(id)?.wire)
        assertEquals(
            stable.readText(),
            preview.readText().replace("io.github.eslamasabry.opencode_mobile.native\"", "io.github.eslamasabry.opencode_mobile\""),
        )
    }

    // ---- B-ANDR-002 -------------------------------------------------------------------------------------------

    /** Every activity in a shipping manifest (main and library manifests; debug and benchmark proofs excluded). */
    private fun shippedActivities(): List<Pair<File, Element>> =
        RepoFiles.shippedManifests().flatMap { file -> xml(file).all("activity").map { file to it } }

    @Test
    fun `B-ANDR-002 every visible entry point draws through Compose, which turns the platform focus highlight off`() {
        val activities = shippedActivities()
        val names = activities.map { it.second.getAttributeNS(ns, "name") }.toSet()
        assertEquals(
            setOf(".MainActivity", "io.github.eslamasabry.opencode_mobile.graph.ScheduleOpenActivity"),
            names,
            "a new activity needs its focus highlight checked here",
        )
        // MainActivity's only content is a Compose host (AndroidComposeView); no XML layout, no other view root.
        val main = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt")
        assertTrue(main.contains("class MainActivity : ComponentActivity()"))
        assertTrue(main.contains("setContent {"))
        assertTrue(!main.contains("setContentView("), "a plain View root would bring the platform highlight back")
        // The notification trampoline shows nothing: translucent, no history, no content, finishes in onCreate.
        val trampoline = activities.single { it.second.getAttributeNS(ns, "name").endsWith("ScheduleOpenActivity") }.second
        assertEquals("@android:style/Theme.Translucent.NoTitleBar", trampoline.getAttributeNS(ns, "theme"))
        assertEquals("true", trampoline.getAttributeNS(ns, "noHistory"))
        val schedules = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SchedulesWiring.kt")
        val body = schedules.substring(schedules.indexOf("class ScheduleOpenActivity"))
        assertTrue(body.contains("finish()"))
        assertTrue(!body.contains("setContent") && !body.contains("setContentView"))
    }

    @Test
    fun `B-ANDR-002 the Compose host view disables the default focus highlight`() {
        // AndroidComposeView's constructor calls Api26Impl.focusable(this, View.FOCUSABLE, false) on API 26+, and that
        // helper is the one place that calls View.setDefaultFocusHighlightEnabled. Read from the shipped bytecode.
        val host = Class.forName("androidx.compose.ui.platform.AndroidComposeView", false, javaClass.classLoader)
        val location = File(host.protectionDomain.codeSource.location.toURI())
        val pkg = "androidx/compose/ui/platform/"
        val classes = HashMap<String, String>()
        fun keep(name: String, bytes: ByteArray) {
            if (name.startsWith(pkg) && name.endsWith(".class")) classes[name.removeSuffix(".class")] = String(bytes, Charsets.ISO_8859_1)
        }
        if (location.isDirectory) {
            location.walkTopDown().filter { it.isFile }.forEach { keep(it.relativeTo(location).invariantSeparatorsPath, it.readBytes()) }
        } else {
            ZipFile(location).use { zip ->
                for (entry in zip.entries()) if (entry.name.startsWith(pkg)) keep(entry.name, zip.getInputStream(entry).readBytes())
            }
        }
        val helpers = classes.filterValues { it.contains("setDefaultFocusHighlightEnabled") }.keys
        assertTrue(helpers.isNotEmpty(), "nothing in Compose turns the platform focus highlight off ($location)")
        val view = classes.getValue(pkg + "AndroidComposeView")
        assertTrue(
            helpers.any { it == pkg + "AndroidComposeView" || view.contains(it) },
            "AndroidComposeView no longer calls the helper that turns the highlight off ($helpers)",
        )
    }
}
