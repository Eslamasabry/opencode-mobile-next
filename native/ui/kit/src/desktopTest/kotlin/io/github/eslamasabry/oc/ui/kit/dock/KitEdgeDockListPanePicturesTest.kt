package io.github.eslamasabry.oc.ui.kit.dock

import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

/** Writes the opened list's picture to docs/designs/glass-in-the-kit/ (render-dock5-list-<TAG>.png, 412 dp, Glass dark). */
class KitEdgeDockListPanePicturesTest {
    private val docs = File(System.getProperty("user.dir"), "../../../docs/designs/glass-in-the-kit").canonicalFile

    @Test
    fun `dock follow-ups 2 - picture, the list over a page of rows`() {
        val bytes = DockFollowUpScenes.png("4")
        val f = File(docs, "render-dock5-list-$TAG.png")
        f.parentFile.mkdirs()
        f.writeBytes(bytes)
        assertTrue(f.length() > 0)
        // CI keeps no files from this job: the picture is also printed, one marked line per slice.
        val text = Base64.getEncoder().encodeToString(bytes)
        println("PNGBEGIN $TAG ${text.length}")
        text.chunked(3000).forEach { println("PNG $it") }
        println("PNGEND $TAG")
    }

    private companion object {
        const val TAG = "after"
    }
}
