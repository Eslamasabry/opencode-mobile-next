package io.github.eslamasabry.oc.android.tiles

import android.content.Context
import android.content.Intent
import io.github.eslamasabry.oc.core.common.launch.LaunchToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchActionsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `B-ANDR-028 the whitelist is the Flutter one`() {
        val wires = listOf(
            "connect", "new_task", "activity", "usage", "usage_settings", "background_settings",
            "phone_setup", "phone_setup_done", "linux",
        )
        for (wire in wires) assertEquals(wire, LaunchActions.parse(wire)?.wire)
        assertSame(LaunchAction.Connect, LaunchActions.parse(" connect "))
        assertSame(LaunchAction.NewTask, LaunchActions.parse("new_task"))
        assertSame(LaunchAction.Activity, LaunchActions.parse("activity"))
        assertSame(LaunchAction.BackgroundSettings, LaunchActions.parse("background_settings"))
    }

    @Test
    fun `B-ANDR-028 usage targets parse and malformed ones are ignored`() {
        assertEquals(
            LaunchAction.UsageTarget("sign_in", "openai", "gpt-4.1_x"),
            LaunchActions.parse("usage_target:sign_in:openai:gpt-4.1_x"),
        )
        assertNull(LaunchActions.parse("usage_target:delete:openai:x"))
        assertNull(LaunchActions.parse("usage_target:sign_in:OpenAI:x"))
        assertNull(LaunchActions.parse("usage_target:sign_in:openai:"))
    }

    @Test
    fun `B-ANDR-028 unknown null and empty actions are ignored`() {
        assertNull(LaunchActions.parse(null))
        assertNull(LaunchActions.parse(""))
        assertNull(LaunchActions.parse("background"))
        assertNull(LaunchActions.parse("delete_everything"))
        assertNull(LaunchActions.consume(null, trusted = true))
        assertNull(LaunchActions.consume(Intent(Intent.ACTION_MAIN), trusted = true))
    }

    @Test
    fun `B-ANDR-028 consume strips the extra so it is delivered once`() {
        val intent = Intent(Intent.ACTION_MAIN).putExtra(LaunchActions.EXTRA, "connect")
        assertSame(LaunchAction.Connect, LaunchActions.consume(intent, trusted = true))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA))
        assertNull(LaunchActions.consume(intent, trusted = true))
    }

    @Test
    fun `B-ANDR-028 an unknown action is stripped too`() {
        val intent = Intent(Intent.ACTION_MAIN).putExtra(LaunchActions.EXTRA, "nope")
        assertNull(LaunchActions.consume(intent, trusted = true))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA))
    }

    @Test
    fun `B-ANDR-031 a session shortcut tap carries both ids and both extras are consumed`() {
        val intent = Intent(Intent.ACTION_MAIN)
            .putExtra(LaunchActions.EXTRA_PROFILE, " p1 ")
            .putExtra(LaunchActions.EXTRA_SESSION, "s9")
        assertEquals(SessionShortcutTap("p1", "s9"), LaunchActions.consumeSessionShortcut(intent, trusted = true))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA_PROFILE))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA_SESSION))
        assertNull(LaunchActions.consumeSessionShortcut(intent, trusted = true))
    }

    @Test
    fun `B-ANDR-027 a session tap missing either id is ignored`() {
        val noProfile = Intent().putExtra(LaunchActions.EXTRA_SESSION, "s1")
        assertNull(LaunchActions.consumeSessionShortcut(noProfile, trusted = true))
        assertFalse(noProfile.hasExtra(LaunchActions.EXTRA_SESSION))
        val blankSession = Intent()
            .putExtra(LaunchActions.EXTRA_PROFILE, "p1")
            .putExtra(LaunchActions.EXTRA_SESSION, "  ")
        assertNull(LaunchActions.consumeSessionShortcut(blankSession, trusted = true))
        assertNull(LaunchActions.consumeSessionShortcut(Intent().putExtra(LaunchActions.EXTRA_PROFILE, "p1"), trusted = true))
        assertNull(LaunchActions.consumeSessionShortcut(null, trusted = true))
    }

    @Test
    fun `B-ANDR-031 the tile intent names MainActivity and a whitelisted action`() {
        val intent = LaunchActions.mainActivityIntent(context, LaunchAction.Activity)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(TilesComponents.MAIN_ACTIVITY, intent.component?.className)
        assertEquals(context.packageName, intent.component?.packageName)
        assertTrue(LaunchToken.consume(context, intent))
        assertSame(LaunchAction.Activity, LaunchActions.consume(intent, trusted = true))
    }

    // ---- B-175 (security audit 2026-11-08): launch extras from other apps need the per-install token.

    /** What another app can send: an explicit intent for MainActivity with chosen extras, no token. */
    private fun foreign(): Intent =
        Intent(Intent.ACTION_MAIN).setClassName(context.packageName, TilesComponents.MAIN_ACTIVITY)

    @Test
    fun `B-175 a foreign intent without the token opens no setup, add-key or other action`() {
        val wires = listOf(
            "phone_setup", "phone_setup_done", "linux", "usage", "usage_settings", "background_settings",
            "activity", "usage_target:add_key:openai:gpt-4.1_x", "usage_target:sign_in:openai:x",
        )
        for (wire in wires) {
            val intent = foreign().putExtra(LaunchActions.EXTRA, wire)
            val trusted = LaunchToken.consume(context, intent)
            assertFalse(wire, trusted)
            assertNull(wire, LaunchActions.consume(intent, trusted))
            assertFalse(wire, intent.hasExtra(LaunchActions.EXTRA))
        }
    }

    @Test
    fun `B-175 a foreign intent cannot open a chosen session`() {
        val intent = foreign()
            .putExtra(LaunchActions.EXTRA_PROFILE, "p1")
            .putExtra(LaunchActions.EXTRA_SESSION, "s9")
        val trusted = LaunchToken.consume(context, intent)
        assertFalse(trusted)
        assertNull(LaunchActions.consumeSessionShortcut(intent, trusted))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA_PROFILE))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA_SESSION))
    }

    @Test
    fun `B-175 a guessed or copied-looking token is refused and stripped`() {
        for (guess in listOf("", "0".repeat(64), "a".repeat(64), LaunchToken.value(context).uppercase())) {
            val intent = foreign().putExtra(LaunchToken.EXTRA, guess).putExtra(LaunchActions.EXTRA, "phone_setup")
            assertFalse(guess, LaunchToken.consume(context, intent))
            assertFalse(intent.hasExtra(LaunchToken.EXTRA))
        }
    }

    @Test
    fun `B-175 the app's own stamped intents still open their action and session`() {
        val action = LaunchActions.mainActivityIntent(context, LaunchAction.PhoneSetup)
        assertSame(LaunchAction.PhoneSetup, LaunchActions.consume(action, LaunchToken.consume(context, action)))
        val session = LaunchToken.stamp(
            context,
            foreign().putExtra(LaunchActions.EXTRA_PROFILE, "p1").putExtra(LaunchActions.EXTRA_SESSION, "s9"),
        )
        assertEquals(
            SessionShortcutTap("p1", "s9"),
            LaunchActions.consumeSessionShortcut(session, LaunchToken.consume(context, session)),
        )
        // Consumed once: a recreated activity cannot replay the token.
        assertFalse(LaunchToken.consume(context, session))
    }

    @Test
    fun `B-175 the static launcher shortcuts still work without a token`() {
        for (action in listOf(LaunchAction.Connect, LaunchAction.NewTask)) {
            val intent = foreign().putExtra(LaunchActions.EXTRA, action.wire)
            assertSame(action, LaunchActions.consume(intent, LaunchToken.consume(context, intent)))
        }
        assertEquals(setOf(LaunchAction.Connect, LaunchAction.NewTask), LaunchActions.UNSTAMPED)
    }

    @Test
    fun `B-175 the token is 64 hex digits and stays the same for the install`() {
        val token = LaunchToken.value(context)
        assertTrue(Regex("^[0-9a-f]{64}$").matches(token))
        assertEquals(token, LaunchToken.value(context))
    }
}
