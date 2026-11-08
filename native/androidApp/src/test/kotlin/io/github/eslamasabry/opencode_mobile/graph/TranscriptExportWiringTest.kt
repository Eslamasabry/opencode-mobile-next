package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.FileSaver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** S24-CHAT story 8: the chat menu's Export transcript reaches the platform saver as a Markdown file. */
class TranscriptExportWiringTest {
    @Test
    fun `B-CHAT-S24-50 Export transcript saves opencode-id dot md as UTF-8 Markdown through the saver`() = runTest {
        val saved = mutableListOf<FileData>()
        val export = AppTranscriptExport(setOf(FileSaver { saved += it; Outcome.Ok(true) }))
        assertEquals(Outcome.Ok(true), export.saveMarkdown("opencode-ses_abcd.md", "# Title\n\nHéllo"))
        val file = saved.single()
        assertEquals("opencode-ses_abcd.md", file.name)
        assertEquals("text/markdown", file.mime)
        assertEquals("# Title\n\nHéllo", file.bytes.decodeToString())
    }

    @Test
    fun `B-CHAT-S24-51 a name never carries a path and a cancelled save is not a failure`() = runTest {
        assertEquals("opencode-etcpasswd.md", transcriptFileData("opencode-../etc/passwd.md", "x").name)
        assertEquals("opencode.md", transcriptFileData("../.md", "x").name)
        val export = AppTranscriptExport(setOf(FileSaver { Outcome.Ok(false) }))
        assertEquals(Outcome.Ok(false), export.saveMarkdown("opencode-a.md", "x"))
        assertTrue(AppTranscriptExport(emptySet()).saveMarkdown("opencode-a.md", "x") is Outcome.Failed)
    }
}
