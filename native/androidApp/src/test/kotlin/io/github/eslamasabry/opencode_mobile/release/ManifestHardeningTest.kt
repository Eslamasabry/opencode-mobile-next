package io.github.eslamasabry.opencode_mobile.release

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * E15-S01 (no catalog row yet; the story id names it): server passwords never leave the phone by cloud backup or
 * device transfer, and the widget overlay never shows in screenshots or recents. Same rules as the Flutter app
 * (android/app/src/main/res/xml/{data_extraction_rules,backup_rules}.xml, WidgetOverlayActivity.kt:30).
 */
class ManifestHardeningTest {
    private val android = "http://schemas.android.com/apk/res/android"

    /** Every backup domain Android knows; each must be excluded with path ".". */
    private val domains =
        listOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )

    private fun xml(path: String): Element =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(RepoFiles.native(path).also { check(it.isFile) { "missing native/$path" } })
            .documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun application(): Element = xml("androidApp/src/main/AndroidManifest.xml").children("application").single()

    private fun excludesEverything(section: Element, where: String) {
        assertTrue(section.children("include").isEmpty(), "$where includes something")
        val excluded = section.children("exclude").filter { it.getAttribute("path") == "." }.map { it.getAttribute("domain") }
        assertEquals(domains.sorted(), excluded.sorted(), "$where must exclude every domain")
    }

    @Test
    fun `E15-S01 device transfer and cloud backup copy nothing`() {
        val app = application()
        assertEquals("false", app.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(android, "dataExtractionRules"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(android, "fullBackupContent"))
        val rules = xml("androidApp/src/main/res/xml/data_extraction_rules.xml")
        excludesEverything(rules.children("cloud-backup").single(), "cloud-backup")
        excludesEverything(rules.children("device-transfer").single(), "device-transfer")
        assertEquals("true", rules.children("cloud-backup").single().getAttribute("disableIfNoEncryptionCapabilities"))
        excludesEverything(xml("androidApp/src/main/res/xml/backup_rules.xml"), "full-backup-content (Android 11 and older)")
    }

    @Test
    fun `E15-S01 the rules are the Flutter app's own files`() {
        for (name in listOf("data_extraction_rules.xml", "backup_rules.xml")) {
            val flutter = File(RepoFiles.root, "android/app/src/main/res/xml/$name")
            if (!flutter.isFile) continue // a native-only checkout: the structural test above still holds
            assertEquals(flutter.readText().trim(), RepoFiles.text("androidApp/src/main/res/xml/$name").trim(), name)
        }
    }

    @Test
    fun `E15-S01 the widget overlay and configure windows are secure`() {
        val source = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WidgetOverlayActivity.kt")
        val onCreate = source.substringAfter("override fun onCreate(").substringBefore("setContent {")
        assertTrue(
            Regex("""window\.(addFlags|setFlags)\(\s*WindowManager\.LayoutParams\.FLAG_SECURE""").containsMatchIn(onCreate),
            "WidgetOverlayActivity must set FLAG_SECURE in onCreate, before any content",
        )
        // Before the early finish() paths too: no frame of the window is ever captured.
        assertTrue(onCreate.indexOf("FLAG_SECURE") < onCreate.indexOf("finish()"), "FLAG_SECURE must come before finish()")
        assertTrue("class WidgetConfigureActivity : WidgetOverlayActivity()" in source, "the configure window inherits it")
        assertTrue(!Regex("""clearFlags\([^)]*FLAG_SECURE""").containsMatchIn(source), "FLAG_SECURE is never cleared")
    }

    @Test
    fun `E15-S01 the hardening adds no component`() {
        val app = application()
        val components = listOf("activity", "activity-alias", "service", "receiver", "provider").sumOf { app.children(it).size }
        assertEquals(14, components, "the app manifest's component count changed; update this only with a component story")
    }
}
