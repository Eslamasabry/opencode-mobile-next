package io.github.eslamasabry.oc.feature.chat.composer

import kotlin.test.Test
import kotlin.test.assertEquals

/** First-time-user walk F-006 and F-008: the composer's words are the person's, never an agent's internal ids. */
class ComposerWordsTest {
    @Test
    fun `B-CHAT-WALK-006 the box never asks default or auto, it asks the agent by its name`() {
        assertEquals("Claude Code", askAs("default", "Claude Code"))
        assertEquals("Claude Code", askAs("auto", "Claude Code"))
        assertEquals("Claude Code", askAs(null, "Claude Code"))
        // A name the server gave its own agent stays.
        assertEquals("docs", askAs("docs", "OpenCode"))
    }

    @Test
    fun `B-CHAT-WALK-006 the model chip says the model, and a mode only when it is named`() {
        assertEquals("Opus 5.5", modelLabel("Opus 5.5", "", "default"))
        assertEquals("Gemini 4", modelLabel("Gemini 4", "", "auto"))
        assertEquals("docs · Gemini 4 · High", modelLabel("Gemini 4", "High", "docs"))
    }

    @Test
    fun `B-CHAT-EFFORT-6 the chip says the effort once, as the model's own name does`() {
        // Owner, build 10073: "Gemini 3.8 Flash (High) · high".
        assertEquals("Gemini 3.8 Flash (High)", modelLabel("Gemini 3.8 Flash (High)", "high", "default", "High"))
        assertEquals("Gemini 4 · High", modelLabel("Gemini 4", "high", "default", "High"))
        assertEquals("Gemini 3.8 Flash (Low) · High", modelLabel("Gemini 3.8 Flash (Low)", "high", "default", "High"))
        assertEquals("docs · Gemini 3.8 Flash (High)", modelLabel("Gemini 3.8 Flash (High)", "high", "docs", "High"))
    }

    @Test
    fun `B-CHAT-WALK-008 the picture-only help names the buttons that are on screen`() {
        val tray = pictureWays(listOf(TrayItem.Photos, TrayItem.Camera, TrayItem.File, TrayItem.More))
        assertEquals("composerTrayPhotos" to "composerTrayCamera", tray.first.key to tray.second.key)
        val list = pictureWays(listOf(TrayItem.File, TrayItem.More))
        assertEquals("photoLibraryAction" to "photoCameraAction", list.first.key to list.second.key)
    }
}
