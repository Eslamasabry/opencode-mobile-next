package io.github.eslamasabry.oc.ui.kit.dock

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Writes the opened list's picture to docs/designs/glass-in-the-kit/ (render-dock5-list-after.png, 412 dp, Glass dark; the before picture is the same scene run on the base commit). */
class KitEdgeDockListPanePicturesTest {
    private val docs = File(System.getProperty("user.dir"), "../../../docs/designs/glass-in-the-kit").canonicalFile

    @Test
    fun `dock follow-ups 2 - picture, the list over a page of rows`() {
        val bytes = DockFollowUpScenes.png("4")
        val f = File(docs, "render-dock5-list-$TAG.png")
        f.parentFile.mkdirs()
        f.writeBytes(bytes)
        assertTrue(f.length() > 0)
    }

    private companion object {
        const val TAG = "after"
    }
}
