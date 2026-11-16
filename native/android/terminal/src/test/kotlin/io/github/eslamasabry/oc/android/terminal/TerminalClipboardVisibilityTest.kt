package io.github.eslamasabry.oc.android.terminal

import android.app.Activity
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TerminalClipboardVisibilityTest {
    @Test
    fun `B-SEC-145 hidden empty and oversized OSC copies never reach the sensitive writer`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val visible = View(activity)
        activity.setContentView(visible)
        val copied = mutableListOf<String>()
        val clipboard = TerminalTextClipboard { copied += it }
        copyTerminalText("hidden", null, clipboard)
        copyTerminalText("", visible, clipboard)
        copyTerminalText("x".repeat(65_537), visible, clipboard)
        copyTerminalText("exact\n  text", visible, clipboard)
        assertEquals(listOf("exact\n  text"), copied)
        visible.visibility = View.GONE
        copyTerminalText("hidden again", visible, clipboard)
        assertEquals(listOf("exact\n  text"), copied)
    }
}
