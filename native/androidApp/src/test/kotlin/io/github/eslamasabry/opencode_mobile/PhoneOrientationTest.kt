package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import io.github.eslamasabry.opencode_mobile.release.RepoFiles
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element

/** Phones stay upright, tablets rotate: the one rule, and the guard that every activity goes through it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneOrientationTest {
    private val android = "http://schemas.android.com/apk/res/android"

    @Test
    fun `PHONE-PORTRAIT-001 a width under 600 dp is held upright and 600 dp or more is left free`() {
        for (dp in listOf(0, 320, 360, 411, 480, 599)) {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, PhoneOrientation.requestedFor(dp), "$dp dp")
        }
        for (dp in listOf(600, 720, 840, 1280)) {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, PhoneOrientation.requestedFor(dp), "$dp dp")
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun `PHONE-PORTRAIT-002 a phone that starts in landscape is asked for portrait`() {
        val activity = Robolectric.buildActivity(OwnActivity::class.java).get()
        PhoneOrientation.apply(activity)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
    }

    @Test
    @Config(qualifiers = "w1280dp-h840dp-land")
    fun `PHONE-PORTRAIT-003 a tablet is not asked for anything`() {
        val activity = Robolectric.buildActivity(OwnActivity::class.java).get()
        PhoneOrientation.apply(activity)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `PHONE-PORTRAIT-004 an activity from a library is left alone`() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        PhoneOrientation.apply(activity)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
    }

    @Test
    fun `PHONE-PORTRAIT-005 the application installs the rule before anything else can start an activity`() {
        val app = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/OcApplication.kt")
        val onCreate = app.substringAfter("override fun onCreate()")
        assertTrue(
            onCreate.indexOf("PhoneOrientation.install(this)") in 0 until onCreate.indexOf("isMainProcess()"),
            "OcApplication.onCreate installs PhoneOrientation first",
        )
    }

    @Test
    fun `PHONE-PORTRAIT-006 every activity in every manifest is covered by the rule and none sets its own orientation`() {
        val manifests =
            RepoFiles.native.walkTopDown()
                .onEnter { it.name != "build" && it.name != ".gradle" && it.name != "test" }
                .filter { it.name == "AndroidManifest.xml" && it.parentFile.parentFile?.name == "src" }
                .toList()
        assertTrue(manifests.size >= 5, "the scan found the app's and the libraries' manifests")
        var activities = 0
        for (manifest in manifests) {
            val root =
                DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                    .newDocumentBuilder().parse(manifest).documentElement
            val nodes = root.getElementsByTagName("activity")
            for (i in 0 until nodes.length) {
                val activity = nodes.item(i) as Element
                val name = activity.getAttributeNS(android, "name")
                val full = if (name.startsWith(".")) "io.github.eslamasabry.opencode_mobile$name" else name
                val where = "${manifest.parentFile.parentFile.parentFile.name}: $name"
                assertTrue(
                    full.startsWith("io.github.eslamasabry.opencode_mobile."),
                    "$where is outside the package the rule covers; widen PhoneOrientation.covers",
                )
                assertEquals("", activity.getAttributeNS(android, "screenOrientation"), "$where sets its own orientation")
                activities++
            }
        }
        assertTrue(activities >= 8, "found the app's activities ($activities)")
    }

    class OwnActivity : Activity()
}
