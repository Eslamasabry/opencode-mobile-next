package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.io.IOException

/** An unreadable extant task is never projected as an absent owned task. */
internal class PhoneAgentAuthInventory(
    private val directories: () -> List<File>,
    private val sameUid: (File) -> Boolean,
    private val read: (File) -> String = { File(it, "stat").readText() },
) {
    fun snapshot(): List<PhoneAgentAuthProcessIdentity> = directories().mapNotNull { directory ->
        val pid = directory.name.toIntOrNull()
        if (pid == null || !sameUid(directory)) null else identity(directory, pid)
    }

    private fun identity(directory: File, pid: Int): PhoneAgentAuthProcessIdentity? {
        val stat = try { read(directory) } catch (error: IOException) {
            if (directory.exists()) throw error else return null
        }
        val values = stat.substringAfterLast(") ").split(' ')
        val parent = checkNotNull(values.getOrNull(1)?.toIntOrNull())
        val start = checkNotNull(values.getOrNull(START_TIME_FIELD))
        check(start.toLongOrNull() != null)
        return PhoneAgentAuthProcessIdentity(pid, parent, start, values[0].single())
    }

    private companion object { const val START_TIME_FIELD = 19 }
}
