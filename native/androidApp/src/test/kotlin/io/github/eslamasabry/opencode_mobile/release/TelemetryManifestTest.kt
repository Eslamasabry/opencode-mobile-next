package io.github.eslamasabry.opencode_mobile.release

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * B-180/B-190: analytics, crash reports and Performance stay off until the person agrees, and nothing reads an
 * advertising ID (PRIVACY.md; Flutter `test/analytics_manifest_test.dart` and `perf_telemetry_test.dart`).
 *
 * The critical one: Flutter 2.1.5 shipped `firebase_performance_collection_deactivated=true`. A "deactivated" flag
 * turns collection off for good: the run-time switch can never turn it on, so the person's consent would do nothing.
 * Every collection here is `*_enabled=false` in the manifest and enabled at run time only with consent, and no
 * `*_deactivated` flag may ever appear. The same rules run on the merged manifest at build time
 * (`verify<Variant>TelemetryManifest` in build.gradle.kts), which also sees what the Firebase SDKs merge in.
 */
class TelemetryManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val tools = "http://schemas.android.com/tools"

    private val manifestFile = RepoFiles.native("androidApp/src/main/AndroidManifest.xml")
    private val manifest: Element by lazy {
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifestFile)
            .documentElement
    }
    private val application: Element by lazy { manifest.children("application").single() }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.attr(name: String): String? = getAttributeNS(android, name).takeIf { hasAttributeNS(android, name) }

    private fun metaData(): Map<String, String?> =
        application.children("meta-data").associate { it.attr("name").orEmpty() to it.attr("value") }

    @Test
    fun `B-190 Performance is enabled-false in the manifest and never deactivated`() {
        assertEquals("false", metaData()["firebase_performance_collection_enabled"])
        val text = manifestFile.readText()
        assertFalse("firebase_performance_collection_deactivated" in text, "deactivated can never be turned on")
    }

    @Test
    fun `B-180 analytics and crash reports are off until consent, never deactivated`() {
        val meta = metaData()
        for (flag in
            listOf(
                "firebase_crashlytics_collection_enabled",
                "firebase_analytics_collection_enabled",
                "firebase_performance_collection_enabled",
            )) {
            assertEquals("false", meta[flag], flag)
        }
        assertTrue(meta.keys.none { it.endsWith("_collection_deactivated") }, "no *_collection_deactivated flag")
        assertFalse("_deactivated" in manifestFile.readText(), "no deactivated flag of any kind")
    }

    @Test
    fun `B-180 no ad signals - ad id, SSAID, ad storage, ad personalisation and automatic screens are off`() {
        val meta = metaData()
        for (flag in
            listOf(
                "google_analytics_adid_collection_enabled",
                "google_analytics_ssaid_collection_enabled",
                "google_analytics_default_allow_ad_personalization_signals",
                "google_analytics_default_allow_ad_user_data",
                "google_analytics_default_allow_ad_storage",
                "google_analytics_default_allow_analytics_storage",
                "google_analytics_automatic_screen_reporting_enabled",
            )) {
            assertEquals("false", meta[flag], flag)
        }
    }

    @Test
    fun `B-180 the advertising ID permission and Android's ad services are removed`() {
        val removed =
            manifest
                .children("uses-permission")
                .filter { it.getAttributeNS(tools, "node") == "remove" }
                .mapNotNull { it.attr("name") }
                .toSet()
        for (permission in
            listOf(
                "com.google.android.gms.permission.AD_ID",
                "android.permission.ACCESS_ADSERVICES_AD_ID",
                "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            )) {
            assertTrue(permission in removed, "$permission must be tools:node=remove")
        }
        // Nothing requests one either.
        val requested =
            manifest.children("uses-permission").filter { it.getAttributeNS(tools, "node") != "remove" }.mapNotNull {
                it.attr("name")
            }
        assertTrue(requested.none { "AD_ID" in it || "ADSERVICES" in it }, "requested: $requested")
        val adServices = application.children("uses-library").single { it.attr("name") == "android.ext.adservices" }
        assertEquals("remove", adServices.getAttributeNS(tools, "node"))
    }

    @Test
    fun `B-190 the Performance Gradle plugin is never applied, only the SDK`() {
        val plugin = "com.google.firebase.firebase-perf"
        val builds =
            RepoFiles.native.walkTopDown()
                .onEnter { it.name !in setOf("build", ".gradle", "node_modules", ".kotlin") }
                .filter { it.isFile && (it.name.endsWith(".gradle.kts") || it.name == "libs.versions.toml") }
                .toList()
        assertTrue(builds.isNotEmpty())
        for (file in builds) {
            val text = file.readText()
            assertFalse(plugin in text, "${file.relativeTo(RepoFiles.native)} applies the Performance plugin")
            assertFalse("perf-plugin" in text, "${file.relativeTo(RepoFiles.native)} names the Performance plugin")
        }
        val toml = RepoFiles.text("gradle/libs.versions.toml")
        assertTrue("com.google.firebase:firebase-perf\"" in toml, "the Performance SDK is the library, not the plugin")
    }

    @Test
    fun `B-190 no network trace is ever created by hand`() {
        val sources =
            RepoFiles.native.walkTopDown()
                .onEnter { it.name !in setOf("build", ".gradle", "test", "commonTest", "desktopTest") }
                .filter { it.isFile && it.extension == "kt" }
        val offenders = sources.filter { "newHttpMetric" in it.readText() }.map { it.relativeTo(RepoFiles.native) }
        assertEquals(emptyList<File>(), offenders.toList())
    }

    @Test
    fun `B-180 the private Firebase config is never in git`() {
        val ignore = RepoFiles.rootText(".gitignore")
        assertTrue("native/androidApp/src/*/google-services.json" in ignore)
    }
}
