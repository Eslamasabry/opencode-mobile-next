package io.github.eslamasabry.oc.quality

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-604: tracks every first-run docs picture under `docs/native/qa/s31-app` in `PICTURES.tsv`, checks that each row
 * names its generator test or explains why it cannot be redrawn, and verifies the release report and dry-run hook.
 */
class DocsPicturesLedgerTest {
    private val repoRoot = File(System.getProperty("oc.root")).parentFile
    private val picturesDir = File(repoRoot, "docs/native/qa/s31-app")
    private val ledgerFile = File(picturesDir, "PICTURES.tsv")
    private val reportScript = File(repoRoot, "native/tool/release/docs_pictures.py")
    private val releaseScript = File(repoRoot, "scripts/native-release.sh")

    private val versionPattern = Regex("""^\d+\.\d+\.\d+$""")
    private val generatorPattern = Regex("""^:([a-z0-9:-]+):desktopTest --tests ([A-Za-z0-9_.]+)$""")
    private val nonePattern = Regex("""^none: .{10,}$""")

    private fun dataRows(): List<Pair<Int, String>> {
        assertTrue(ledgerFile.isFile, "missing docs/native/qa/s31-app/PICTURES.tsv")
        return ledgerFile.readLines().mapIndexedNotNull { index, line ->
            if (line.isBlank() || line.startsWith("#")) null else (index + 1) to line
        }
    }

    private fun validateRow(lineNo: Int, line: String): List<String> {
        val parts = line.split('\t')
        return if (parts.size != 3) {
            listOf("line $lineNo: expected 3 tab-separated columns, got ${parts.size}")
        } else {
            val problems = mutableListOf<String>()
            val drawnFor = parts[1]
            val way = parts[2]
            if (drawnFor != "-" && !versionPattern.matches(drawnFor)) {
                problems += "line $lineNo: invalid drawn_for '$drawnFor'"
            }
            validateWay(lineNo, way)?.let { problems += it }
            problems
        }
    }

    private fun validateWay(lineNo: Int, way: String): String? {
        val match = generatorPattern.matchEntire(way)
        return when {
            nonePattern.matches(way) -> null
            match == null -> "line $lineNo: invalid way '$way'"
            else -> {
                val modulePath = match.groupValues[1].replace(':', '/')
                val classPath = match.groupValues[2].replace('.', '/')
                val rel = "native/$modulePath/src/desktopTest/kotlin/$classPath.kt"
                if (File(repoRoot, rel).isFile) null else "line $lineNo: generator test file not found: $rel"
            }
        }
    }

    private fun runReport(root: File, version: String): Pair<Int, String> {
        val process = ProcessBuilder(
            "python3",
            "-I",
            reportScript.absolutePath,
            root.absolutePath,
            version,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return exitCode to output
    }

    private fun touch(file: File) {
        file.parentFile.mkdirs()
        file.writeBytes(ByteArray(0))
    }

    @Test
    fun `B-604 every first-run docs picture has exactly one ledger row`() {
        val rows = dataRows()
        val listed = rows.map { (_, line) -> line.substringBefore('\t') }
        val duplicates = listed.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        assertTrue(duplicates.isEmpty(), "duplicate rows in docs/native/qa/s31-app/PICTURES.tsv: $duplicates")
        val onDisk = picturesDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".png") }
            .map { it.relativeTo(repoRoot).path.replace('\\', '/') }
            .toSet()
        assertEquals(onDisk, listed.toSet(), "docs/native/qa/s31-app/PICTURES.tsv does not match .png files")
    }

    @Test
    fun `B-604 every ledger row says how its picture is redrawn or why it cannot be`() {
        val problems = dataRows().flatMap { (lineNo, line) -> validateRow(lineNo, line) }
        assertTrue(
            problems.isEmpty(),
            "invalid rows in docs/native/qa/s31-app/PICTURES.tsv:\n" + problems.joinToString("\n"),
        )
    }

    @Test
    fun `B-604 the release report names stale unlisted and frozen pictures`() {
        val tmp = Files.createTempDirectory("docs-pictures-report-").toFile()
        try {
            val base = "docs/native/qa/s31-app"
            val tsv = File(tmp, "$base/PICTURES.tsv")
            tsv.parentFile.mkdirs()
            val way = ":feature:onboarding:desktopTest --tests a.B"
            tsv.writeText(
                listOf(
                    "# test ledger",
                    "$base/native/old.png\t3.0.1\t$way",
                    "$base/native/new.png\t3.0.2\t$way",
                    "$base/flutter/f.png\t-\tnone: the retired Flutter app picture",
                ).joinToString("\n") + "\n",
            )
            touch(File(tmp, "$base/native/old.png"))
            touch(File(tmp, "$base/native/new.png"))
            touch(File(tmp, "$base/flutter/f.png"))
            touch(File(tmp, "$base/native/extra.png"))

            val (code, output) = runReport(tmp, "3.0.2")
            assertEquals(0, code, "expected exit 0, got $code:\n$output")
            val lines = output.lines()
            assertTrue(
                lines.any {
                    it.startsWith("STALE") &&
                        "$base/native/old.png (drawn for 3.0.1)" in it &&
                        way in it
                },
                "expected STALE line for old.png:\n$output",
            )
            assertTrue(
                lines.any { it.startsWith("current") && "new.png" in it },
                "expected current line for new.png:\n$output",
            )
            assertTrue(
                lines.any {
                    it.startsWith("NOT REDRAWN") &&
                        "flutter/f.png: the retired Flutter app picture" in it
                },
                "expected NOT REDRAWN line for flutter/f.png:\n$output",
            )
            assertTrue(
                lines.any { it.startsWith("UNLISTED") && "extra.png" in it },
                "expected UNLISTED line for extra.png:\n$output",
            )
            assertTrue(
                "1 current, 1 stale, 1 not redrawn, 1 unlisted" in output,
                "expected summary counts:\n$output",
            )
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `B-604 the report refuses a missing ledger`() {
        val tmp = Files.createTempDirectory("docs-pictures-missing-").toFile()
        try {
            val (code, output) = runReport(tmp, "3.0.2")
            assertEquals(2, code, "expected exit 2 for missing ledger, got $code:\n$output")
            assertTrue("no ledger" in output, "expected 'no ledger' in output:\n$output")
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `B-604 the release dry run prints the docs pictures report and a checklist line`() {
        assertTrue(releaseScript.isFile, "missing scripts/native-release.sh")
        val text = releaseScript.readText()
        assertTrue(
            "tool/release/docs_pictures.py" in text,
            "scripts/native-release.sh does not call tool/release/docs_pictures.py",
        )
        assertTrue(
            "[ ] docs first-run pictures" in text,
            "scripts/native-release.sh GO checklist is missing '[ ] docs first-run pictures'",
        )
    }
}
