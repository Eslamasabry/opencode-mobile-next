package io.github.eslamasabry.opencode_mobile.telemetry

import com.google.firebase.analytics.FirebaseAnalytics
import io.github.eslamasabry.oc.engine.analytics.product.ProductScreen
import io.github.eslamasabry.opencode_mobile.graph.TelemetryContinuity
import io.github.eslamasabry.opencode_mobile.release.RepoFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-180: the Android Firebase wiring of Flutter 2.1.5 (android/app/build.gradle.kts, AndroidManifest.xml). */
class FirebaseTwinTest {
    private val manifest by lazy { RepoFiles.text("androidApp/src/main/AndroidManifest.xml") }
    private val build by lazy { RepoFiles.text("androidApp/build.gradle.kts") }

    @Test
    fun `B-180-030 the config is read from OC_GOOGLE_SERVICES_DIR or a property, never committed`() {
        assertTrue("providers.gradleProperty(\"OC_GOOGLE_SERVICES_DIR\")" in build)
        assertTrue("System.getenv(\"OC_GOOGLE_SERVICES_DIR\")" in build)
        // The same Firebase app as Flutter: the file is looked up by the real package name.
        assertTrue("\"switch\" to \"io.github.eslamasabry.opencode_mobile\"" in build)
        assertTrue("google-services-\$id.json" in build)
        val ignore = RepoFiles.root.resolve(".gitignore").readText()
        // A local build copies it next to the flavor; git must never pick it up.
        assertTrue("native/androidApp/src/*/google-services.json" in ignore)
    }

    @Test
    fun `B-180-031 with no config the build applies no Firebase plugin and the app uses the no-op sinks`() {
        val guard = build.substringBefore("/** E01-S01")
        assertTrue("if (found) {" in guard)
        assertTrue("apply(plugin = \"com.google.gms.google-services\")" in guard)
        assertTrue("apply(plugin = \"com.google.firebase.crashlytics\")" in guard)
        assertTrue("implementation(libs.firebase.analytics)" in build)
        val telemetry = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/telemetry/Telemetry.kt")
        assertTrue("eventVendor ?: NoOpProductEventSink" in telemetry)
        assertTrue("crashVendor ?: NoOpReportSink" in telemetry)
    }

    @Test
    fun `B-180-032 ad ID and SSAID are off and the ad-services permissions are removed`() {
        for (permission in
            listOf(
                "com.google.android.gms.permission.AD_ID",
                "android.permission.ACCESS_ADSERVICES_AD_ID",
                "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            )) {
            assertTrue(
                Regex("""<uses-permission android:name="${Regex.escape(permission)}" tools:node="remove" />""")
                    .containsMatchIn(manifest),
                permission,
            )
        }
        for (flag in
            listOf(
                "firebase_crashlytics_collection_enabled",
                "firebase_analytics_collection_enabled",
                "google_analytics_adid_collection_enabled",
                "google_analytics_ssaid_collection_enabled",
                "google_analytics_default_allow_ad_personalization_signals",
                "google_analytics_default_allow_ad_user_data",
                "google_analytics_default_allow_ad_storage",
                "google_analytics_automatic_screen_reporting_enabled",
            )) {
            assertTrue("<meta-data android:name=\"$flag\" android:value=\"false\" />" in manifest, flag)
        }
        // "deactivated" would switch Analytics off for good, so the person's yes could never turn it on.
        assertFalse("firebase_analytics_collection_deactivated" in manifest)
        assertTrue("<uses-library android:name=\"android.ext.adservices\" android:required=\"false\" tools:node=\"remove\" />" in manifest)
    }

    @Test
    fun `B-180-033 consent mode grants analytics storage only with a yes and never any ad signal`() {
        for (on in listOf(true, false)) {
            val consent = FirebaseAnalyticsSink.consent(on)
            assertEquals(
                if (on) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                consent[FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE],
            )
            for (ad in
                listOf(
                    FirebaseAnalytics.ConsentType.AD_STORAGE,
                    FirebaseAnalytics.ConsentType.AD_USER_DATA,
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION,
                )) {
                assertEquals(FirebaseAnalytics.ConsentStatus.DENIED, consent[ad], "$ad with $on")
            }
        }
        assertEquals(FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalyticsSink.wireName("screen_view"))
        assertEquals("setup_done", FirebaseAnalyticsSink.wireName("setup_done"))
    }

    @Test
    fun `B-180-034 a screen view names the route's kind, never an argument`() {
        val name = TelemetryContinuity.routeName("io.github.eslamasabry.oc.ui.routes.ChatsRoute\$Home")
        assertEquals("ChatsRoute.Home", name)
        assertEquals(ProductScreen.Conversations, ProductScreen.of(name))
        assertEquals("Pixel 9 Pro", TelemetryContinuity.plainFact("Pixel 9 Pro\u0000/"))
    }

    @Test
    fun `B-180-035 Crashlytics gets class names and checked frames, never a message`() {
        val store = RepoFiles.text("android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/NativeCrashStore.kt")
        assertTrue("previous.uncaughtException(thread, CrashSanitizer.sanitize(error))" in store)
        val sink = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/telemetry/CrashlyticsSink.kt")
        assertTrue("SafeReportException(report)" in sink, "non-fatal reports carry only the redacted SafeReport")
    }
}
