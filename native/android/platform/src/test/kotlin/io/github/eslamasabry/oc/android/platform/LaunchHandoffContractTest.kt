package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.entry.BootstrapPhase
import io.github.eslamasabry.oc.engine.entry.EntryExtras
import io.github.eslamasabry.oc.engine.entry.EntryLinkParser
import io.github.eslamasabry.oc.engine.entry.EntryOrigin
import io.github.eslamasabry.oc.engine.entry.EntryTarget
import io.github.eslamasabry.oc.engine.entry.EntryValidators
import io.github.eslamasabry.oc.engine.entry.ParsedLink
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `test/launch_shortcut_native_contract_test.dart`, `session_link_native_contract_test.dart` and
 * `share_intent_native_contract_test.dart`, the native way: the Flutter channel's "parked value until Dart says it is
 * ready, then drained once" is `EntryRouter.pending` (held until the shell consumes it) behind `EntryCapture`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchHandoffContractTest {
    private fun activityWith(intent: Intent): Activity = Robolectric.buildActivity(Activity::class.java, intent).create().get()

    private fun TestScope.capture(rig: RouterRig, intent: Intent, saved: Bundle? = null): Pair<Activity, Int> {
        val activity = activityWith(intent)
        return activity to EntryCapture.onCreate(activity, saved, EntryOrigin.Untrusted, FixedClock, rig.router)
    }

    private fun shortcut(value: String) =
        Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(EntryExtras.APP_PACKAGE, StaticShortcuts.MAIN_ACTIVITY))
            .putExtra(StaticShortcuts.EXTRA, value)

    private fun share(text: String) = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    private fun link(raw: String) = Intent(Intent.ACTION_VIEW, Uri.parse(raw)).addCategory(Intent.CATEGORY_BROWSABLE)

    // ---- B-ANDR-029 launcher shortcuts -------------------------------------------------------------------------

    @Test
    fun `B-ANDR-029 the native whitelist is the declared shortcut ids plus the tile, setup, usage and Linux actions`() {
        assertEquals(
            setOf(
                "connect", "new_task", "activity", "usage", "usage_settings", "background_settings",
                "phone_setup", "phone_setup_done", "linux",
            ),
            EntryValidators.launchActions.keys,
        )
        assertTrue(EntryValidators.launchActions.keys.containsAll(StaticShortcuts.ids))
        assertEquals("oc.shortcut", StaticShortcuts.EXTRA)
    }

    @Test
    fun `B-ANDR-029 the launch extra is removed before the whitelist decides - a rejected value is consumed too`() =
        runTest {
            val rig = RouterRig(this)
            for (value in listOf("delete_everything", "", "usage_target:delete:openai:x")) {
                val (activity, submitted) = capture(rig, shortcut(value))
                assertEquals(0, submitted, value)
                assertFalse(activity.intent.hasExtra(StaticShortcuts.EXTRA), value)
                assertNull(rig.ack(), value)
            }
            assertTrue(rig.effects.calls.isEmpty())
        }

    @Test
    fun `B-ANDR-029 the launch action is parked until the app side is ready and then drained once`() =
        runTest {
            val rig = RouterRig(this, readyWorld().copy(bootstrap = BootstrapPhase.Starting, gateOpen = false))
            capture(rig, shortcut("connect"))
            runCurrent()
            assertNull(rig.router.pending.value, "nothing is handed over before the app is ready")

            rig.world.world.value = readyWorld()
            val handoff = rig.ack()
            assertEquals(EntryTarget.Servers(null), handoff?.target)
            assertNull(rig.ack(), "drained once")
        }

    @Test
    fun `B-ANDR-029 a live tap is held until the shell takes it, and a recreated activity forgets nothing parked`() =
        runTest {
            val rig = RouterRig(this)
            val (activity, _) = capture(rig, Intent(Intent.ACTION_MAIN))
            assertEquals(1, EntryCapture.onNewIntent(activity, shortcut("new_task"), EntryOrigin.Untrusted, FixedClock, rig.router))
            runCurrent()
            val parked = rig.router.pending.value
            assertNotNull(parked, "the live tap waits in the router for the shell")

            // Teardown: the recreated activity gets its original intent back with a saved state and replays nothing,
            // while the parked handoff is still there for the new shell.
            val (_, replayed) = capture(rig, shortcut("connect"), saved = Bundle())
            assertEquals(0, replayed)
            runCurrent()
            assertEquals(parked, rig.router.pending.value)
            rig.ack()
            assertNull(rig.ack())
        }

    // ---- B-ANDR-072 session link -------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-072 the native scheme and host match the link shape, case-insensitively, and round-trip`() {
        val made = EntryLinkParser.sessionLink(P1, S1)
        assertEquals("opencode-mobile://session?profile=p1&session=ses_1", made)
        assertEquals(ParsedLink.Session(P1, S1), EntryLinkParser.parse(made!!))
        assertEquals(ParsedLink.Session(P1, S1), EntryLinkParser.parse("OPENCODE-MOBILE://SESSION?profile=p1&session=ses_1"))
        assertEquals(ParsedLink.TeamGate(P1, "g1"), EntryLinkParser.parse("opencode-mobile://team/gate/p1/g1"))
        assertNull(EntryLinkParser.parse("opencode-mobile://elsewhere?profile=p1&session=ses_1"))
        assertNull(EntryLinkParser.parse("https://session?profile=p1&session=ses_1"))
    }

    @Test
    fun `B-ANDR-072 the declared VIEW filter carries route identifiers only - no path prefix and no mime type`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("entry/main-activity.xml")!!
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(stream)
        val ns = "http://schemas.android.com/apk/res/android"
        val filters = doc.getElementsByTagName("intent-filter")
        val view = (0 until filters.length).map { filters.item(it) as Element }.filter { f ->
            val actions = f.getElementsByTagName("action")
            (0 until actions.length).any { (actions.item(it) as Element).getAttributeNS(ns, "name") == "android.intent.action.VIEW" }
        }
        assertEquals(1, view.size)
        val data = view.single().getElementsByTagName("data")
        for (i in 0 until data.length) {
            val e = data.item(i) as Element
            assertEquals("", e.getAttributeNS(ns, "pathPrefix"))
            assertEquals("", e.getAttributeNS(ns, "mimeType"))
            assertEquals("opencode-mobile", e.getAttributeNS(ns, "scheme"))
        }
    }

    @Test
    fun `B-ANDR-072 a link is consumed before it is handed over and only the link text is used`() =
        runTest {
            val rig = RouterRig(this)
            val intent = link("opencode-mobile://session?profile=p1&session=ses_1").putExtra("unrelated", "x")
            val (activity, submitted) = capture(rig, intent)
            assertEquals(1, submitted)
            assertEquals(Intent.ACTION_MAIN, activity.intent.action)
            assertNull(activity.intent.data)
            assertEquals(EntryTarget.Chat(ConversationRef(P1, S1)), rig.ack()?.target)
            // A share or launcher intent is not read as a link.
            assertTrue(EntryIntentReader.read(Intent(Intent.ACTION_MAIN, Uri.parse("opencode-mobile://session?profile=p1&session=ses_1")), EntryOrigin.Untrusted, FixedClock).none { it is io.github.eslamasabry.oc.engine.entry.EntryRequest.Link })
        }

    // ---- B-ANDR-074 share in -----------------------------------------------------------------------------------

    @Test
    fun `B-ANDR-074 a share is delivered only once the app side is ready, then cleaned up`() =
        runTest {
            val rig = RouterRig(this, readyWorld().copy(bootstrap = BootstrapPhase.Starting, gateOpen = false))
            val (activity, submitted) = capture(rig, share("draft me"))
            assertEquals(1, submitted)
            runCurrent()
            assertNull(rig.router.pending.value)
            assertTrue(rig.effects.calls.isEmpty(), "nothing is created before readiness")
            assertNull(activity.intent.getStringExtra(Intent.EXTRA_TEXT))

            rig.world.world.value = readyWorld()
            assertTrue(rig.ack()?.target is EntryTarget.Chat)
            assertEquals("seed:ses_new:draft me", rig.effects.calls.last())
            assertNull(rig.ack())
        }
}
