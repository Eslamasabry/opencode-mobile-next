package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.entry.EntryExtras
import io.github.eslamasabry.oc.engine.entry.EntryOrigin
import io.github.eslamasabry.oc.engine.entry.EntryTarget
import io.github.eslamasabry.oc.engine.entry.Opened
import io.github.eslamasabry.oc.engine.entry.SettingsSectionTarget
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 07 §7.1 on a real (Robolectric) activity: every intent MainActivity receives goes read → strip → setIntent → submit,
 * and reaches the real `EntryRouter` (S2-ENTRY). Untrusted arrivals only navigate (16 §1.8.1, Astra M2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryCaptureTest {
    private fun activityWith(intent: Intent): Activity = Robolectric.buildActivity(Activity::class.java, intent).create().get()

    private fun TestScope.capture(rig: RouterRig, intent: Intent, saved: Bundle? = null): Activity {
        val activity = activityWith(intent)
        EntryCapture.onCreate(activity, saved, EntryOrigin.Untrusted, FixedClock, rig.router)
        return activity
    }

    private fun share(text: String?, subject: String? = null) =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

    private fun link(raw: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse(raw)).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun shortcut(value: String) =
        Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(EntryExtras.APP_PACKAGE, StaticShortcuts.MAIN_ACTIVITY))
            .putExtra(StaticShortcuts.EXTRA, value)

    @Test
    fun `B-ANDR-074 a shared text reaches the router once as an unsent draft in a new conversation and the intent is cleaned up`() =
        runTest {
            val rig = RouterRig(this)
            val activity = capture(rig, share("Fix the login bug", subject = "Bug"))

            val handoff = rig.ack()
            assertEquals(EntryTarget.Chat(ConversationRef(P1, NEW_SESSION), focusComposer = true, opened = Opened.NewlyCreated), handoff?.target)
            assertEquals(listOf("create:p1", "seed:ses_new:Bug\nFix the login bug"), rig.effects.calls)
            // Cleaned up: a configuration change or a later read sees a plain MAIN intent.
            assertEquals(Intent.ACTION_MAIN, activity.intent.action)
            assertNull(activity.intent.getStringExtra(Intent.EXTRA_TEXT))
            assertNull(activity.intent.getStringExtra(Intent.EXTRA_SUBJECT))
            assertNull(activity.intent.type)
            assertNull(rig.ack()) // delivered once
        }

    @Test
    fun `B-ANDR-074 a rotation or a Recents restore never replays the share, and an empty share submits nothing`() =
        runTest {
            val rig = RouterRig(this)
            capture(rig, share("again"), saved = Bundle())
            assertNull(rig.ack())

            val fromHistory = share("again").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
            assertEquals(0, EntryCapture.onCreate(activityWith(fromHistory), null, EntryOrigin.Untrusted, FixedClock, rig.router))
            assertNull(rig.ack())

            val empty = capture(rig, share("   "))
            assertNull(rig.ack())
            assertEquals(Intent.ACTION_MAIN, empty.intent.action) // consumed even when rejected
            assertTrue(rig.effects.calls.isEmpty())
        }

    @Test
    fun `B-ANDR-074 a live share through onNewIntent reaches the router and replaces the activity intent`() =
        runTest {
            val rig = RouterRig(this)
            val activity = capture(rig, Intent(Intent.ACTION_MAIN))
            assertNull(rig.ack())

            val live = share("live text")
            assertEquals(1, EntryCapture.onNewIntent(activity, live, EntryOrigin.Untrusted, FixedClock, rig.router))
            assertEquals(live, activity.intent)
            assertEquals(Intent.ACTION_MAIN, activity.intent.action)
            assertTrue(rig.ack()?.target is EntryTarget.Chat)
            assertEquals("seed:ses_new:live text", rig.effects.calls.last())
        }

    @Test
    fun `B-ANDR-071 a session deep link reaches the router and opens the chat without any server call`() =
        runTest {
            val rig = RouterRig(this)
            val activity = capture(rig, link("opencode-mobile://session?profile=p1&session=ses_1"))

            assertEquals(EntryTarget.Chat(ConversationRef(P1, S1)), rig.ack()?.target)
            assertTrue(rig.effects.gatewayCalls.isEmpty())
            assertEquals(Intent.ACTION_MAIN, activity.intent.action)
            assertNull(activity.intent.data)
        }

    @Test
    fun `B-ANDR-071 a link to another saved server asks before switching, and a rejected link is consumed with nothing submitted`() =
        runTest {
            val rig = RouterRig(this)
            capture(rig, link("opencode-mobile://session?profile=p2&session=ses_1"))
            assertTrue(rig.ack()?.target is EntryTarget.SwitchServerThen)
            assertTrue(rig.effects.gatewayCalls.isEmpty())

            for (bad in listOf("opencode-mobile://session?profile=p1&session=" + "x".repeat(1100), "opencode-mobile://evil?x=1")) {
                val activity = capture(rig, link(bad))
                assertNull(rig.ack(), bad.take(40))
                assertNull(activity.intent.data)
                assertEquals(Intent.ACTION_MAIN, activity.intent.action)
            }
        }

    @Test
    fun `B-ANDR-029 the static shortcuts reach the router as navigation only - connect opens Servers, new_task opens the sheet`() =
        runTest {
            val rig = RouterRig(this)
            val connect = capture(rig, shortcut("connect"))
            assertEquals(EntryTarget.Servers(null), rig.ack()?.target)
            assertFalse(connect.intent.hasExtra(StaticShortcuts.EXTRA)) // the launch extra is removed

            capture(rig, shortcut("new_task"))
            assertEquals(EntryTarget.NewConversation(P1), rig.ack()?.target)
            assertTrue(rig.effects.gatewayCalls.isEmpty(), "a static shortcut never creates a session")

            val unknown = capture(rig, shortcut("send_prompt"))
            assertNull(rig.ack())
            assertFalse(unknown.intent.hasExtra(StaticShortcuts.EXTRA))
        }

    @Test
    fun `B-ANDR-028 UntrustedArrival every launch value fired at MainActivity from another app makes zero gateway calls`() =
        runTest {
            val rig = RouterRig(this)
            val arrivals =
                listOf(
                    "connect", "new_task", "activity", "usage", "usage_settings", "background_settings", "phone_setup",
                    "phone_setup_done", "linux", "usage_target:sign_in:anthropic:p2", "usage_target:add_key:zai:p1",
                    "usage_target:set_up:minimax:p2",
                ).map(::shortcut) +
                    listOf(
                        Intent(Intent.ACTION_MAIN)
                            .putExtra("oc.shortcut.profile", "p2")
                            .putExtra("oc.shortcut.session", "ses_1"),
                        Intent(Intent.ACTION_MAIN)
                            .putExtra("io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_KIND", "permission")
                            .putExtra("io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_SESSION_ID", "ses_1")
                            .putExtra("io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_PROFILE_ID", "p2"),
                        Intent(Intent.ACTION_MAIN)
                            .putExtra("scheduledProfileId", "p2")
                            .putExtra("scheduledPromptId", "sched_1"),
                        link("opencode-mobile://team/gate/p2/gate_1"),
                    )
            for (intent in arrivals) {
                capture(rig, intent)
                rig.ack()
                assertTrue(rig.effects.gatewayCalls.isEmpty(), "no connect or create before a tap: ${rig.effects.calls}")
            }
        }

    @Test
    fun `B-ANDR-028 TrustedArrival the same new_task through the internal entry creates the session`() =
        runTest {
            val rig = RouterRig(this)
            val activity = activityWith(shortcut("new_task"))
            assertEquals(1, EntryCapture.onCreate(activity, null, EntryOrigin.Internal, FixedClock, rig.router))
            assertEquals(
                EntryTarget.Chat(ConversationRef(P1, NEW_SESSION), focusComposer = true, opened = Opened.NewlyCreated),
                rig.ack()?.target,
            )
            assertEquals(listOf("create:p1"), rig.effects.gatewayCalls)
        }

    @Test
    fun `B-ANDR-073 the tile long-press reaches the router and opens its settings page`() =
        runTest {
            val rig = RouterRig(this)
            val tile =
                Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES").putExtra(
                    Intent.EXTRA_COMPONENT_NAME,
                    ComponentName(EntryExtras.APP_PACKAGE, "io.github.eslamasabry.opencode_mobile.PauseTileService"),
                )
            capture(rig, tile)
            assertEquals(EntryTarget.Settings(SettingsSectionTarget.Background), rig.ack()?.target)
        }

    @Test
    fun `B-ANDR-027 a live tap through onNewIntent beats the stale cold-start one on the same channel`() =
        runTest {
            val rig = RouterRig(this, readyWorld().copy(gateOpen = false)) // cold start: the gate is still closed
            val activity = capture(rig, shortcut("connect"))
            EntryCapture.onNewIntent(activity, shortcut("background_settings"), EntryOrigin.Untrusted, FixedClock, rig.router)
            assertNull(rig.ack())

            rig.world.world.value = readyWorld()
            assertEquals(EntryTarget.Settings(SettingsSectionTarget.Background), rig.ack()?.target)
            assertNull(rig.ack()) // the stale Connect never opens
        }
}
