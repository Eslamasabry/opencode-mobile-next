package io.github.eslamasabry.oc.android.tiles

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchActionsTest {

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
        assertNull(LaunchActions.consume(null))
        assertNull(LaunchActions.consume(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `B-ANDR-028 consume strips the extra so it is delivered once`() {
        val intent = Intent(Intent.ACTION_MAIN).putExtra(LaunchActions.EXTRA, "connect")
        assertSame(LaunchAction.Connect, LaunchActions.consume(intent))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA))
        assertNull(LaunchActions.consume(intent))
    }

    @Test
    fun `B-ANDR-028 an unknown action is stripped too`() {
        val intent = Intent(Intent.ACTION_MAIN).putExtra(LaunchActions.EXTRA, "nope")
        assertNull(LaunchActions.consume(intent))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA))
    }

    @Test
    fun `B-ANDR-031 a session shortcut tap carries both ids and both extras are consumed`() {
        val intent = Intent(Intent.ACTION_MAIN)
            .putExtra(LaunchActions.EXTRA_PROFILE, " p1 ")
            .putExtra(LaunchActions.EXTRA_SESSION, "s9")
        assertEquals(SessionShortcutTap("p1", "s9"), LaunchActions.consumeSessionShortcut(intent))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA_PROFILE))
        assertFalse(intent.hasExtra(LaunchActions.EXTRA_SESSION))
        assertNull(LaunchActions.consumeSessionShortcut(intent))
    }

    @Test
    fun `B-ANDR-027 a session tap missing either id is ignored`() {
        val noProfile = Intent().putExtra(LaunchActions.EXTRA_SESSION, "s1")
        assertNull(LaunchActions.consumeSessionShortcut(noProfile))
        assertFalse(noProfile.hasExtra(LaunchActions.EXTRA_SESSION))
        val blankSession = Intent()
            .putExtra(LaunchActions.EXTRA_PROFILE, "p1")
            .putExtra(LaunchActions.EXTRA_SESSION, "  ")
        assertNull(LaunchActions.consumeSessionShortcut(blankSession))
        assertNull(LaunchActions.consumeSessionShortcut(Intent().putExtra(LaunchActions.EXTRA_PROFILE, "p1")))
        assertNull(LaunchActions.consumeSessionShortcut(null))
    }

    @Test
    fun `B-ANDR-031 the tile intent names MainActivity and a whitelisted action`() {
        val intent = LaunchActions.mainActivityIntent("io.example", LaunchAction.Activity)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(TilesComponents.MAIN_ACTIVITY, intent.component?.className)
        assertEquals("io.example", intent.component?.packageName)
        assertSame(LaunchAction.Activity, LaunchActions.consume(intent))
    }
}
