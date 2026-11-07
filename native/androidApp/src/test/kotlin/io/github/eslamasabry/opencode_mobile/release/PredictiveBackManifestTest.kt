package io.github.eslamasabry.opencode_mobile.release

import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.Element

/**
 * M-001: the system back gesture reaches the app's own back callbacks (and so the shell's predictive preview) on
 * Android 13–15 too, where the default is still the legacy onBackPressed path. The Flutter app sets the same
 * attribute (android/app/src/main/AndroidManifest.xml).
 */
class PredictiveBackManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"

    private fun application(path: String): Element {
        val root =
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(RepoFiles.native(path).also { check(it.isFile) { "missing native/$path" } })
                .documentElement
        val nodes = root.getElementsByTagName("application")
        check(nodes.length == 1) { "$path must have one <application>" }
        return nodes.item(0) as Element
    }

    @Test
    fun `M-001 the app opts in to predictive back like the Flutter app`() {
        assertEquals(
            "true",
            application("androidApp/src/main/AndroidManifest.xml")
                .getAttributeNS(android, "enableOnBackInvokedCallback"),
        )
        assertEquals(
            "true",
            application("androidApp/src/test/resources/flutter-2.1.4/AndroidManifest.xml")
                .getAttributeNS(android, "enableOnBackInvokedCallback"),
            "the reference manifest still opts in",
        )
    }
}
