package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.diagnostics.report.ReportFile
import io.github.eslamasabry.oc.engine.diagnostics.report.ReportFiles
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/**
 * BUILD-DIAGNOSTICS-ATTENTION: the persisted problem report's two app-private files (Flutter `report_problem.json` and
 * its pending write) under filesDir/diagnostics. Synchronous on purpose (a fatal handler cannot wait); every problem
 * throws and the store turns it into a plain Failure. Symbolic links are refused so a planted link cannot redirect it.
 */
class AndroidReportFiles(filesDir: File) : ReportFiles {
    private val dir = File(filesDir, "diagnostics")

    private fun target(file: ReportFile): File {
        if (Files.isSymbolicLink(dir.toPath())) throw IOException("refused")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("unavailable")
        val f = File(dir, file.fileName)
        if (Files.isSymbolicLink(f.toPath())) throw IOException("refused")
        return f
    }

    override fun size(file: ReportFile): Long? {
        val f = target(file)
        return if (Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)) f.length() else null
    }

    override fun read(file: ReportFile): ByteArray = target(file).readBytes()

    override fun writePending(bytes: ByteArray) {
        FileOutputStream(target(ReportFile.Pending)).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
    }

    override fun commitPending() {
        Files.move(
            target(ReportFile.Pending).toPath(),
            target(ReportFile.Snapshot).toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    override fun delete(file: ReportFile) {
        Files.deleteIfExists(target(file).toPath())
    }
}
