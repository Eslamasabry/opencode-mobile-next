package io.github.eslamasabry.oc.engine.files

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest

private class FakeFiles(
    val nodes: Map<String, List<FileNode>> = emptyMap(),
    val contents: Map<String, FileContent> = emptyMap(),
    val found: List<String> = emptyList(),
) : FileGateway {
    val listed = mutableListOf<String>()
    val read = mutableListOf<String>()

    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> {
        listed += path
        return nodes[path]?.let { Outcome.Ok(it.toImmutableList()) } ?: Outcome.Failed(Failure.NotFound())
    }

    override suspend fun fileContent(path: String): Outcome<FileContent> {
        read += path
        return contents[path]?.let { Outcome.Ok(it) } ?: Outcome.Failed(Failure.NotFound())
    }

    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> = Outcome.Ok(found.toImmutableList())

    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("fileBrowsing")))
}

class ProjectFilesTest {
    @Test
    fun `B-NAV-026 a folder lists exactly that project folder, in the server's order, root as empty path`() = runTest {
        val gateway =
            FakeFiles(
                nodes =
                    mapOf(
                        "" to listOf(FileNode("lib", "lib/", true), FileNode("README.md", "README.md", false)),
                        "lib" to listOf(FileNode("main.dart", "./lib/main.dart", false)),
                    )
            )
        val root = (ProjectFiles.list(gateway, "/") as Outcome.Ok).value
        assertEquals(listOf("lib", "README.md"), root.map { it.path })
        assertTrue(root[0].isDir)
        val lib = (ProjectFiles.list(gateway, "lib/") as Outcome.Ok).value
        assertEquals(listOf("lib/main.dart"), lib.map { it.path })
        assertEquals(listOf("", "lib"), gateway.listed)
    }

    @Test
    fun `B-NAV-026 a failed listing is the gateway's failure, never an empty folder`() = runTest {
        assertIs<Outcome.Failed>(ProjectFiles.list(FakeFiles(), "missing"))
    }

    @Test
    fun `B-NAV-026 hidden entries are left out until asked for and an all-hidden folder says so`() {
        val all = listOf(FileNode(".git", ".git", true), FileNode(".env", ".env", false), FileNode("a", "a", false))
        val calm = FolderListing.of(all, showHidden = false)
        assertEquals(listOf("a"), calm.entries.map { it.name })
        assertEquals(2, calm.hiddenCount)
        val onlyHidden = FolderListing.of(all.take(2), showHidden = false)
        assertTrue(onlyHidden.onlyHidden)
        assertEquals(3, FolderListing.of(all, showHidden = true).entries.size)
    }

    @Test
    fun `B-MEDIA-001 text previews decode strictly while the bytes stay exact`() = runTest {
        val invalid = byteArrayOf(0x68, 0x69, 0xC3.toByte(), 0x28) // "hi" + an invalid UTF-8 sequence
        val gateway =
            FakeFiles(contents = mapOf("bad.txt" to FileContent(Base64.encode(invalid), "binary", "base64", null)))
        val opened = (ProjectFiles.open(gateway, "bad.txt") as Outcome.Ok).value
        assertIs<FileBody.Binary>(opened.body)
        assertContentEquals(invalid, opened.data.bytes)
        assertEquals("text/plain", opened.data.mime)
    }

    @Test
    fun `B-MEDIA-001 a file with a NUL byte is binary even when it decodes`() {
        val data = FileData("x.txt", null, byteArrayOf(0x61, 0x00, 0x62))
        assertIs<FileBody.Binary>(FilePreview.body(data))
    }

    @Test
    fun `B-MEDIA-001 viewer kinds follow the file - code, markdown, text, image, pdf, and a line opens code`() {
        fun body(path: String, bytes: ByteArray = "x".encodeToByteArray(), line: Int? = null) =
            FilePreview.body(FileData(path, FilePreview.mimeFor(path), bytes), line)
        assertEquals("kt", (body("src/A.kt") as FileBody.Code).language)
        assertIs<FileBody.Markdown>(body("README.md"))
        assertIs<FileBody.Text>(body("notes.txt"))
        assertIs<FileBody.Text>(body("LICENSE"))
        assertIs<FileBody.Code>(body("Dockerfile"))
        assertIs<FileBody.Image>(body("logo.png", byteArrayOf(1, 2, 3)))
        assertIs<FileBody.Pdf>(body("doc.pdf", byteArrayOf(0x25, 0x50)))
        assertEquals(12, (body("README.md", line = 12) as FileBody.Code).line)
        assertIs<FileBody.Code>(body("icon.svg"))
    }

    @Test
    fun `B-MEDIA-001 long text is cut at the viewer limit without splitting a character`() {
        val text = "a".repeat(FilePreview.MAX_VIEWER_CHARS - 1) + "😀" + "tail"
        val (shown, truncated) = FilePreview.cut(text)
        assertTrue(truncated)
        assertEquals(FilePreview.MAX_VIEWER_CHARS - 1, shown.length)
        assertEquals("abc" to false, FilePreview.cut("abc"))
    }

    @Test
    fun `B-MEDIA-001 unreadable base64 is a plain not-showable failure`() = runTest {
        val gateway = FakeFiles(contents = mapOf("a.bin" to FileContent("@@not base64@@", "binary", "base64", null)))
        val failed = ProjectFiles.open(gateway, "a.bin") as Outcome.Failed
        assertEquals(Failure.Media(MediaProblem.FileNotShowable), failed.failure)
    }

    @Test
    fun `B-NAV-026 name search returns project files, empty query asks nothing`() = runTest {
        val gateway = FakeFiles(found = listOf("lib/main.dart", "./lib/main.dart", "README.md"))
        val found = (ProjectFiles.find(gateway, " main ") as Outcome.Ok).value
        assertEquals(listOf("lib/main.dart", "README.md"), found.map { it.path })
        assertEquals("main.dart", found[0].name)
        assertEquals(persistentListOf(), (ProjectFiles.find(gateway, "  ") as Outcome.Ok).value)
    }

    @Test
    fun `B-NAV-028 paths - parent, name, child, extension and crumbs`() {
        assertEquals("lib/ui", FilePaths.parent("lib/ui/a.kt"))
        assertEquals("", FilePaths.parent("a.kt"))
        assertEquals("a.kt", FilePaths.name("./lib/ui/a.kt"))
        assertEquals("lib/a.kt", FilePaths.child("lib/", "a.kt"))
        assertEquals("a.kt", FilePaths.child("", "a.kt"))
        assertEquals("", FilePaths.extension(".env"))
        assertEquals("gz", FilePaths.extension("a.tar.GZ"))
        assertEquals(listOf("", "lib", "lib/ui"), FilePaths.crumbs("lib/ui"))
        assertEquals(listOf(""), FilePaths.crumbs(""))
    }
}
