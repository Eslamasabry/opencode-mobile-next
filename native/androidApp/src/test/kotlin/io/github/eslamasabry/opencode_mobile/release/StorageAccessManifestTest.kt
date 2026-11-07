package io.github.eslamasabry.opencode_mobile.release

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * B-145: installing native over Flutter 2.1.4 reset All-files access (`MANAGE_EXTERNAL_STORAGE` app-op allow ->
 * default, and a `NO_ISOLATED_STORAGE` deny appeared). Android keeps a granted app-op permission across an update only
 * while the new package requests the permission the same way and does not newly ask for a wider storage scope:
 * an update that starts requesting `requestLegacyExternalStorage` is treated as a scope expansion and its storage
 * grants are reset. 2.1.4 set neither legacy-storage attribute; native set `requestLegacyExternalStorage="true"`.
 *
 * The fixture `src/test/resources/flutter-2.1.4/AndroidManifest.xml` is the released 2.1.4+62 manifest, verbatim.
 * The same rules run on the merged switch manifest at build time (`verify<Variant>StorageManifest` in
 * build.gradle.kts), which also sees library manifests.
 */
class StorageAccessManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val tools = "http://schemas.android.com/tools"
    private val storageAttributes = listOf("requestLegacyExternalStorage", "preserveLegacyExternalStorage")
    private val storagePermissions =
        listOf(
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
        )

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file.also { check(it.isFile) { "missing $it" } })
            .documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.attr(name: String): String? =
        getAttributeNS(android, name).takeIf { hasAttributeNS(android, name) }

    private val flutter: Element by lazy {
        parse(RepoFiles.native("androidApp/src/test/resources/flutter-2.1.4/AndroidManifest.xml"))
    }

    private fun permissions(manifest: Element): Map<String, String?> =
        manifest.children("uses-permission").associate { it.attr("name").orEmpty() to it.attr("maxSdkVersion") }

    @Test
    fun `B-145 the app requests legacy storage exactly as 2_1_4 did`() {
        val flutterApp = flutter.children("application").single()
        for (manifest in RepoFiles.shippedManifests()) {
            val app = parse(manifest).children("application").singleOrNull() ?: continue
            for (name in storageAttributes) {
                assertEquals(
                    flutterApp.attr(name),
                    app.attr(name),
                    "${manifest.relativeTo(RepoFiles.native)}: android:$name differs from 2.1.4 " +
                        "(resets All-files access)",
                )
            }
        }
    }

    @Test
    fun `B-145 every 2_1_4 storage permission stays requested with the same maxSdkVersion`() {
        val old = permissions(flutter).filterKeys { it in storagePermissions }
        assertEquals(storagePermissions.toSet(), old.keys, "fixture lost a storage permission")
        val main = permissions(parse(RepoFiles.native("androidApp/src/main/AndroidManifest.xml")))
        for ((name, maxSdk) in old) {
            assertTrue(name in main, "$name is no longer requested by :androidApp")
            assertEquals(maxSdk, main[name], "$name maxSdkVersion changed")
        }
    }

    @Test
    fun `B-145 no shipped manifest removes or caps a storage permission`() {
        for (manifest in RepoFiles.shippedManifests()) {
            val storage = parse(manifest).children("uses-permission").filter { it.attr("name") in storagePermissions }
            for (permission in storage) {
                val name = permission.attr("name")
                val where = "${manifest.relativeTo(RepoFiles.native)}: $name"
                assertTrue(permission.getAttributeNS(tools, "node").isEmpty(), "$where carries tools:node")
                if (name == "android.permission.MANAGE_EXTERNAL_STORAGE") {
                    assertEquals(null, permission.attr("maxSdkVersion"), "$where must not be capped")
                }
            }
        }
    }
}
