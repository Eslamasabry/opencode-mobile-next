package io.github.eslamasabry.oc.engine.runtime.storage

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * Direct, no-follow readings: browsing never launches Ubuntu or the phone server (B-LINUX-016/017).
 */
public data class LinuxFolder(val name: String, val path: String, val git: Boolean)

public data class LinuxRemovalSize(
    val runtimeBytes: Long,
    val projectsBytes: Long,
    val measuredAtMilliseconds: Long,
) {
    init {
        require(runtimeBytes >= 0 && projectsBytes >= 0 && measuredAtMilliseconds >= 0)
        require(runtimeBytes <= Long.MAX_VALUE - projectsBytes)
    }

    public fun freed(deleteProjects: Boolean): Long =
        runtimeBytes + if (deleteProjects) projectsBytes else 0
}

public interface LinuxLocalStorage {
    public suspend fun folders(path: String): Outcome<List<LinuxFolder>>

    public suspend fun createProject(path: String): Outcome<Unit>

    public suspend fun measure(): Outcome<LinuxRemovalSize>

    public suspend fun remove(deleteProjects: Boolean, confirmationName: String?): Outcome<Unit>

    public companion object {
        public const val PROJECTS: String = "/root/projects"
        public const val DELETION_NAME: String = "OpenCode"

        public fun normalize(path: String): String? {
            if (path.any { it.code < 0x20 || it.code == 0x7f }) return null
            val value = path.trim()
            if (!value.startsWith('/')) return null
            val segments = value.split('/').filter { it.isNotEmpty() && it != "." }
            if (segments.any { it == ".." }) return null
            return "/" + segments.joinToString("/")
        }

        public fun parent(path: String): String? =
            normalize(path)?.takeUnless { it == "/" }?.substringBeforeLast('/', "")?.ifEmpty { "/" }
    }
}
