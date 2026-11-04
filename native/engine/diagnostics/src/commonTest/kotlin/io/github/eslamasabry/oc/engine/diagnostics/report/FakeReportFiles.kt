package io.github.eslamasabry.oc.engine.diagnostics.report

/** The diagnostics folder in memory; it outlives the [ReportProblem] objects, so reopening is a restart. */
internal class FakeReportFiles : ReportFiles {
    val files: MutableMap<ReportFile, ByteArray> = mutableMapOf()
    var refuseWrites = false
    val refuseDelete: MutableSet<ReportFile> = mutableSetOf()
    var refuseOpen = false

    fun text(file: ReportFile): String? = files[file]?.decodeToString()

    fun put(file: ReportFile, text: String) {
        files[file] = text.encodeToByteArray()
    }

    override fun size(file: ReportFile): Long? {
        if (refuseOpen) throw IllegalStateException("/data/user/0/app/files/diagnostics unreadable")
        return files[file]?.size?.toLong()
    }

    override fun read(file: ReportFile): ByteArray = files.getValue(file)

    override fun writePending(bytes: ByteArray) {
        if (refuseWrites) throw IllegalStateException("/data/user/0/app/files/diagnostics/report_problem.pending: no space")
        files[ReportFile.Pending] = bytes
    }

    override fun commitPending() {
        files[ReportFile.Snapshot] = files.remove(ReportFile.Pending) ?: error("nothing pending")
    }

    override fun delete(file: ReportFile) {
        if (refuseOpen) throw IllegalStateException("unreadable")
        if (file in refuseDelete && file in files) throw IllegalStateException("/data/user/0/app/files: busy")
        files.remove(file)
    }
}
