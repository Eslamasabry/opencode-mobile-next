package io.github.eslamasabry.oc.android.tiles

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** E00-S05: a live mode paused on the Flutter build stays paused after the update, read from its preference XML. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PauseTileStateTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    /** Writes `src/test/resources/flutter-prefs/oc_background_native.xml` where the Flutter build left it. */
    private fun installFlutterPause() {
        val xml = javaClass.getResourceAsStream("/flutter-prefs/oc_background_native.xml")!!.readBytes()
        val dir = File(context.dataDir, "shared_prefs").apply { mkdirs() }
        File(dir, "${PauseTileState.FLUTTER_PREFS}.xml").writeBytes(xml)
    }

    private fun flutterPaused(): Boolean =
        context.getSharedPreferences(PauseTileState.FLUTTER_PREFS, Context.MODE_PRIVATE)
            .getBoolean(PauseTileState.FLUTTER_KEY_PAUSED, false)

    @Test
    fun `B-MIGR-013 a live mode paused on Flutter reads as paused, and the Flutter marker is kept`() {
        installFlutterPause()
        assertTrue(PauseTileState.isPausedByUser(context))
        assertEquals(PauseTileState.Mode.Paused, PauseTileState.mode(context))
        assertTrue(TilesComponents.prefs(context).getBoolean(PauseTileState.KEY_FLUTTER_PAUSE_ADOPTED, false))
        assertTrue(flutterPaused())
    }

    @Test
    fun `B-MIGR-013 the Flutter pause is adopted once, so resuming on native is not undone`() {
        installFlutterPause()
        assertTrue(PauseTileState.isPausedByUser(context))
        PauseTileState.setPausedByUser(context, false)
        assertFalse(PauseTileState.isPausedByUser(context))
        assertTrue(flutterPaused()) // still the Flutter build's own choice, for a rollback
    }

    @Test
    fun `B-MIGR-013 a choice made on native before the adoption wins`() {
        installFlutterPause()
        PauseTileState.setPausedByUser(context, false)
        assertFalse(PauseTileState.isPausedByUser(context))
    }

    @Test
    fun `B-MIGR-013 without a Flutter pause nothing is paused`() {
        assertFalse(PauseTileState.isPausedByUser(context))
        assertEquals(PauseTileState.Mode.Off, PauseTileState.mode(context))
        assertTrue(TilesComponents.prefs(context).getBoolean(PauseTileState.KEY_FLUTTER_PAUSE_ADOPTED, false))
    }
}
