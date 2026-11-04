package io.github.eslamasabry.opencode_mobile.release

import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.security.AddressRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `test/release_blockers_test.dart` rows of B-ANDR-068 that have a native form today (each named after its Flutter
 * case). Read from the checked-in manifests, build files and sources, like the Flutter test.
 */
class ReleaseBlockersTest {
    private val manifest by lazy { RepoFiles.text("androidApp/src/main/AndroidManifest.xml") }

    @Test
    fun `B-ANDR-068 cleartext - the platform policy is open on purpose and code decides, with no private address pinned`() {
        // Android cannot express 100.64.0.0/10, so (as in the Flutter app's network config) the platform policy is
        // open and the address rule in code asks before any plain-HTTP server that is not this phone.
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"true\""))
        assertTrue(AddressRules.needsCleartextConsent(ServerOrigin("http", listOf(100, 101, 102, 103).joinToString("."), 8373)))
        assertTrue(AddressRules.needsCleartextConsent(ServerOrigin("http", listOf(192, 168, 1, 20).joinToString("."), 4096)))
        assertFalse(AddressRules.needsCleartextConsent(ServerOrigin("http", "127.0.0.1", 4096)))
        assertFalse(AddressRules.needsCleartextConsent(ServerOrigin("https", "server.example.test", 443)))
        for (file in RepoFiles.shippedManifests()) {
            val text = file.readText()
            assertFalse(text.contains(listOf(192, 168).joinToString(".") + "."), file.path)
            assertFalse(text.contains(listOf(100, 101).joinToString(".") + "."), file.path)
        }
    }

    @Test
    fun `B-ANDR-068 background coding alerts are private and actionable`() {
        val channels = RepoFiles.text("android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/Channels.kt")
        val shade = RepoFiles.text("android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/AlertShade.kt")
        assertTrue(Regex("""CodingAction\(\s*"opencode_coding_action", NotificationManager.IMPORTANCE_HIGH,[^)]*ChannelFeature.Core, true,\s*\)""").containsMatchIn(channels))
        assertTrue(Regex("""CodingStatus\(\s*"opencode_coding_status", NotificationManager.IMPORTANCE_DEFAULT,[^)]*ChannelFeature.Core, true,\s*\)""").containsMatchIn(channels))
        assertTrue(channels.contains("lockscreenVisibility = Notification.VISIBILITY_PRIVATE"))
        assertTrue(shade.contains(".setVisibility(NotificationCompat.VISIBILITY_PRIVATE)"))
        assertTrue(shade.contains("setPublicVersion(publicVersion("), "the lock screen gets a version without the words")
        // A tap carries the alert's kind and ids only, never a title or error text.
        assertTrue(shade.contains("putString(FrozenExtras.EXTRA_CODING_ALERT_KIND, alert.kind.wire)"))
        assertTrue(shade.contains("putString(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID, alert.sessionId)"))
        assertFalse(shade.contains("sessionTitle"))
        assertFalse(shade.contains("errorMessage"))
        // MainActivity takes the tap on a live activity too.
        val activity = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt")
        assertTrue(activity.contains("override fun onNewIntent(intent: Intent)"))
        assertTrue(activity.contains("platformHub.route(intent)"))
    }

    @Test
    fun `B-ANDR-068 updates have one app-controlled owner and every install needs the person`() {
        val app = RepoFiles.text("androidApp/build.gradle.kts")
        assertTrue(app.contains("\":engine:updates\"") && app.contains("\":feature:updates\""))
        val wiring = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt")
        assertEquals(1, Regex("""\bAppUpdates\(""").findAll(wiring).count(), "one updater instance")
        val installer = RepoFiles.text("android/tiles/src/main/kotlin/io/github/eslamasabry/oc/android/tiles/updates/AndroidUpdates.kt")
        assertTrue(installer.contains("setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)"))
        assertTrue(RepoFiles.text("android/tiles/src/main/AndroidManifest.xml").contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        // No second, silent updater (the Flutter app's Shorebird auto_update is off; native has no code push at all).
        for (file in listOf("androidApp/build.gradle.kts", "build-logic/convention/src/main/kotlin/oc/buildlogic/OcAndroidApplicationPlugin.kt")) {
            assertFalse(RepoFiles.text(file).contains("shorebird", ignoreCase = true), file)
        }
    }

    @Test
    fun `B-ANDR-068 the build identity is exposed and comes from version properties`() {
        val plugin = RepoFiles.text("build-logic/convention/src/main/kotlin/oc/buildlogic/OcAndroidApplicationPlugin.kt")
        assertTrue(plugin.contains("buildConfig = true"))
        assertTrue(plugin.contains("rootDir.resolve(\"version.properties\")"))
    }

    @Test
    fun `B-ANDR-068 the biometric lint exception stays narrow - no biometric permission and no path suppressions`() {
        for (file in RepoFiles.shippedManifests()) {
            assertFalse(file.readText().contains("android.permission.USE_BIOMETRIC"), file.path)
        }
        val lint = RepoFiles.text("androidApp/lint.xml")
        // Exactly one path suppression, for the deliberately open cleartext base config; nothing biometric.
        assertEquals(1, Regex("""<ignore path=""").findAll(lint).count())
        assertTrue(Regex("""<issue id="InsecureBaseConfiguration">\s*<ignore path="src/main/res/xml/network_security_config.xml" />""").containsMatchIn(lint))
        assertFalse(lint.contains("MissingPermission"))
        assertFalse(lint.contains("Biometric", ignoreCase = true))
    }
}
