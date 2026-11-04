package io.github.eslamasabry.opencode_mobile.graph

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * Validate tar members before any guest extraction. GNU long names and per-member PAX are
 * understood.
 */
internal object LinuxBackupTar {
    private val home =
        setOf(
            ".bashrc",
            ".bash_profile",
            ".bash_aliases",
            ".profile",
            ".inputrc",
            ".gitconfig",
            ".vimrc",
            ".tmux.conf",
            ".config/git",
            ".config/nvim",
            ".config/htop",
            ".ssh/authorized_keys",
            ".ssh/config",
            ".ssh/known_hosts",
        )

    fun validate(archive: java.io.InputStream, part: String, rootfs: File, budget: Long) {
        var total = 0L
        var longName: String? = null
        var longLink: String? = null
        var extended = emptyMap<String, String>()
        val links = mutableSetOf<String>()
        // Every directory prefix a symlink target walks through; a link may never sit on one, and a
        // target may never walk through a link, so chains cannot climb out of the restore root.
        val traversed = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        val files = mutableSetOf<String>()
        val base =
            when (part) {
                "home.tar.gz" -> File(rootfs, "root")
                "services.tar.gz" -> File(rootfs, "root/.oc-builtin/svc")
                "projects.tar.gz" -> File(rootfs, "root/.oc-builtin/restore/projects.tmp")
                else -> throw IOException("unknown part")
            }
        DataInputStream(GZIPInputStream(archive.buffered())).use { input ->
            val header = ByteArray(512)
            var members = 0
            while (true) {
                input.readFully(header)
                if (header.all { it == 0.toByte() }) break
                if (++members > 1_000_000) throw IOException("too many members")
                val checksum = number(header, 148, 8)
                val actual =
                    header.indices.sumOf {
                        if (it in 148..155) 32L else (header[it].toInt() and 255).toLong()
                    }
                check(checksum == actual)
                val rawSize = number(header, 124, 12)
                val type = header[156].toInt().toChar()
                if (type == 'L' || type == 'K' || type == 'x') {
                    check(rawSize in 0..1_048_576)
                    val data = ByteArray(rawSize.toInt())
                    input.readFully(data)
                    skipFully(input, padding(rawSize))
                    when (type) {
                        'L' -> longName = data.decodeToString().trimEnd('\u0000', '\n')
                        'K' -> longLink = data.decodeToString().trimEnd('\u0000', '\n')
                        'x' -> extended = pax(data)
                    }
                    continue
                }
                check(type in setOf('\u0000', '0', '1', '2', '5'))
                val prefix = if (text(header, 257, 6) == "ustar") text(header, 345, 155) else ""
                val rawName =
                    extended["path"]
                        ?: longName
                        ?: listOf(prefix, text(header, 0, 100))
                            .filter { it.isNotEmpty() }
                            .joinToString("/")
                val name = normalized(rawName)
                val size = extended["size"]?.toLongOrNull() ?: rawSize
                check(size >= 0 && size <= budget - total)
                total += size
                check(seen.add(name))
                check(links.none { name.startsWith("$it/") })
                if (part == "home.tar.gz") check(home.any { name == it || name.startsWith("$it/") })
                if (part == "services.tar.gz")
                    check(
                        name == "defs" ||
                            name == "want" ||
                            name.startsWith("defs/") ||
                            name.startsWith("want/")
                    )
                noExistingLinks(base, name)
                if (type == '1' || type == '2') {
                    val link = extended["linkpath"] ?: longLink ?: text(header, 157, 100)
                    if (type == '1') {
                        check(normalized(link) in files)
                    } else {
                        check(!link.startsWith('/') && !link.contains('\\'))
                        val parent = name.substringBeforeLast('/', "")
                        val walked = confined(if (parent.isEmpty()) link else "$parent/$link")
                        check(walked.none { it in links || it == name })
                        check(traversed.none { it == name || it.startsWith("$name/") })
                        check(seen.none { it.startsWith("$name/") })
                        traversed += walked
                        links += name
                    }
                }
                if (type == '\u0000' || type == '0') files += name
                skipFully(input, size + padding(size))
                longName = null
                longLink = null
                extended = emptyMap()
            }
        }
    }

    private fun normalized(raw: String): String {
        check(!raw.startsWith('/') && !raw.contains('\\') && !raw.contains('\u0000'))
        val parts = raw.split('/').filter { it.isNotEmpty() && it != "." }
        check(parts.none { it == ".." })
        return parts.joinToString("/")
    }

    /** Checks [path] never climbs above the root and returns every prefix it walks through. */
    private fun confined(path: String): List<String> {
        val stack = ArrayDeque<String>()
        val walked = mutableListOf<String>()
        for (part in path.split('/')) {
            when (part) {
                "",
                "." -> Unit
                ".." -> {
                    check(stack.isNotEmpty())
                    stack.removeLast()
                }
                else -> {
                    stack.addLast(part)
                    walked += stack.joinToString("/")
                }
            }
        }
        return walked
    }

    private fun noExistingLinks(base: File, relative: String) {
        var path: File? = base
        while (path != null) {
            check(!Files.isSymbolicLink(path.toPath()))
            path = path.parentFile
        }
        var at = base
        relative
            .split('/')
            .filter { it.isNotEmpty() }
            .forEach { part ->
                at = File(at, part)
                check(!Files.isSymbolicLink(at.toPath()))
            }
    }

    private fun number(bytes: ByteArray, at: Int, length: Int): Long {
        if (bytes[at].toInt() and 128 != 0) {
            check(bytes[at].toInt() and 64 == 0)
            var result = (bytes[at].toInt() and 63).toLong()
            for (i in at + 1 until at + length) {
                check(result <= (Long.MAX_VALUE ushr 8))
                result = (result shl 8) or (bytes[i].toInt() and 255).toLong()
            }
            return result
        }
        return text(bytes, at, length).trim().ifEmpty { "0" }.toLong(8)
    }

    private fun text(bytes: ByteArray, at: Int, length: Int): String =
        bytes
            .copyOfRange(at, at + length)
            .takeWhile { it != 0.toByte() }
            .toByteArray()
            .decodeToString()

    private fun padding(size: Long): Long = (512 - size % 512) % 512

    private fun skipFully(input: DataInputStream, count: Long) {
        var left = count
        val buffer = ByteArray(64 * 1024)
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            if (n < 0) throw IOException("truncated tar")
            left -= n
        }
    }

    private fun pax(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var at = 0
        while (at < bytes.size) {
            val space =
                (at until bytes.size).firstOrNull { bytes[it] == 32.toByte() }
                    ?: throw IOException("bad pax")
            val length = bytes.copyOfRange(at, space).decodeToString().toInt()
            check(length > space - at + 1 && length <= bytes.size - at)
            val record =
                bytes.copyOfRange(space + 1, at + length).decodeToString().removeSuffix("\n")
            val key = record.substringBefore('=')
            // Sparse files and global metadata can change extraction semantics; authored backups
            // never need them.
            check(!key.startsWith("GNU.sparse") && key != "SCHILY.filetype")
            result[key] = record.substringAfter('=', "")
            at += length
        }
        return result
    }
}
