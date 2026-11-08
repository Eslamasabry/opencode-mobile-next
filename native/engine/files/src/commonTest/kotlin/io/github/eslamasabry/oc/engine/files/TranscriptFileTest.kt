package io.github.eslamasabry.oc.engine.files

import kotlin.test.Test
import kotlin.test.assertEquals

/** Export conversation as Markdown: the saved file (Flutter `_exportTranscript`). */
class TranscriptFileTest {
    @Test
    fun `B-MEDIA-001 the transcript saves as opencode plus the first 8 of the session id, as UTF-8 Markdown`() {
        val file = TranscriptFile.markdown("ses_4f2a91c0d1e2", "# Fix login\n\n**You**\n\nمرحبا")
        assertEquals("opencode-ses_4f2a.md", file.name)
        assertEquals("text/markdown", file.mime)
        assertEquals("# Fix login\n\n**You**\n\nمرحبا", file.bytes.decodeToString())
    }

    @Test
    fun `B-MEDIA-001 a session id never puts a path or odd characters in the file name`() {
        assertEquals("opencode-abc.md", TranscriptFile.name("a/b\\c"))
        assertEquals("opencode.md", TranscriptFile.name("../"))
        assertEquals("opencode.md", TranscriptFile.name(""))
    }
}
