package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException

/**
 * Answers a script's `::oc unpack <archive> <dest> <strip> <member|->` request
 * (issue #117): when `tar` inside Ubuntu dies from a signal on some ROMs, the
 * app unpacks the same .tar.gz itself, on the Android side, into the same
 * folder. The script checked the archive's pinned SHA-256 before it asked.
 *
 * Handshake, by two marker files next to the archive (like [SetupAppFetch]):
 *  - `<archive>.oc-unpack-ack` appears at once.
 *  - `<archive>.oc-unpack-done` appears last and holds `ok` or `fail <plain reason>`.
 *
 * Safety: guest paths must stay inside the root filesystem; an entry with an
 * absolute path or a `..` part is refused (the whole unpack fails), and so is
 * an entry that would be written through a link that leaves the destination.
 */
class SetupAppUnpack(private val rootfs: File, private val allowed: List<String>? = null) {
    /** Never throws for a bad archive; the answer is in the `.oc-unpack-done` file. */
    fun run(request: SetupProtocol.Event.Unpack, cancelled: () -> Boolean) {
        val paths = RootfsPaths(rootfs, allowed)
        val archive = paths.resolve(request.archive) ?: return
        val dest = paths.resolve(request.dest) ?: return
        val done = File(archive.path + ".oc-unpack-done")
        Files.deleteIfExists(done.toPath())
        RootfsPaths.writeFresh(File(archive.path + ".oc-unpack-ack"), "1")
        val answer = try {
            unpack(archive, dest, request.strip, request.member, cancelled)
            "ok"
        } catch (error: UnpackFailure) {
            "fail ${error.plain}"
        } catch (error: IOException) {
            "fail ${UnpackFailure("the archive could not be unpacked", error).plain}"
        }
        val tmp = File(archive.path + ".oc-unpack-tmp")
        RootfsPaths.writeFresh(tmp, answer)
        if (!tmp.renameTo(done)) RootfsPaths.writeFresh(done, answer)
    }

    class UnpackFailure(val plain: String, cause: Throwable? = null) : Exception(plain, cause)

    /** Extracts [archive] (gzip-compressed tar) into [dest]; [member] limits it to one path. */
    fun unpack(archive: File, dest: File, strip: Int, member: String?, cancelled: () -> Boolean) {
        requireValidRequest(archive, strip)
        dest.mkdirs()
        val base = dest.canonicalFile
        try {
            extractAll(archive, base, strip, member, cancelled)
        } catch (error: ZipException) {
            throw damaged(error)
        } catch (error: EOFException) {
            throw damaged(error)
        }
    }

    private fun damaged(cause: IOException) = UnpackFailure("the archive is damaged", cause)

    private fun requireValidRequest(archive: File, strip: Int) {
        if (!archive.isFile) throw UnpackFailure("the downloaded file is missing")
        if (strip < 0) throw UnpackFailure("the request was not valid")
    }

    private fun extractAll(archive: File, base: File, strip: Int, member: String?, cancelled: () -> Boolean) {
        GZIPInputStream(archive.inputStream().buffered(BUFFER)).use { gz ->
            val reader = TarReader(gz)
            var entry = reader.next()
            var matched = member == null
            while (entry != null) {
                if (cancelled()) throw UnpackFailure("cancelled")
                if (extract(entry, reader, base, strip, member)) matched = true
                entry = reader.next()
            }
            if (!matched) throw UnpackFailure("the archive did not contain the program")
        }
    }

    /** True when [entry] was selected and written. */
    private fun extract(entry: TarReader.Entry, reader: TarReader, base: File, strip: Int, member: String?): Boolean {
        val target = targetFor(entry, base, strip, member) ?: return false
        UnpackGuards.requireInside(base, target)
        return write(entry, reader, base, target, strip)
    }

    /** Where [entry] goes inside [base], or null when it is not selected. */
    private fun targetFor(entry: TarReader.Entry, base: File, strip: Int, member: String?): File? {
        val name = UnpackGuards.safeName(entry.name).removePrefix("./")
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        val skipped = (member != null && name != member.trimEnd('/')) || parts.size <= strip
        return if (skipped) null else File(base, parts.drop(strip).joinToString("/"))
    }

    private fun write(entry: TarReader.Entry, reader: TarReader, base: File, target: File, strip: Int): Boolean {
        val written = when (entry.type) {
            TarReader.DIR -> { target.mkdirs(); true }
            TarReader.FILE, TarReader.FILE_OLD -> { writeFile(reader, target); true }
            TarReader.SYMLINK -> { UnpackFiles.link(target, entry.link); true }
            TarReader.HARDLINK -> { copyHardLink(base, target, entry.link, strip); true }
            else -> false
        }
        if (written && entry.type != TarReader.SYMLINK) UnpackFiles.applyMode(target, entry.mode)
        return written
    }

    private fun writeFile(reader: TarReader, target: File) {
        if (Files.isSymbolicLink(target.toPath()) || target.isFile) target.delete()
        target.outputStream().buffered(BUFFER).use { out -> reader.copyBody(out) }
    }

    private fun copyHardLink(base: File, target: File, linkName: String, strip: Int) {
        val parts = UnpackGuards.safeName(linkName).split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.size <= strip) throw UnpackFailure("the archive has an unsafe path")
        val source = File(base, parts.drop(strip).joinToString("/"))
        UnpackGuards.requireInside(base, source)
        // The source itself may be a link the archive just made; copying it would read through it.
        if (Files.isSymbolicLink(source.toPath()) || !source.isFile) {
            throw UnpackFailure("the archive has a broken link")
        }
        target.delete()
        source.copyTo(target)
    }

    private companion object {
        const val BUFFER = 65_536
    }
}

/** A small tar reader: ustar, pax (`path`, `linkpath`, `size`) and GNU long names. */
internal class TarReader(private val input: InputStream) {
    class Entry(val name: String, val type: Char, val mode: Int, val size: Long, val link: String)

    private var remaining = 0L
    private var padding = 0

    fun next(): Entry? {
        skipBody()
        val extra = ExtendedFields()
        while (true) {
            val header = readBlock() ?: return null
            val type = header[TYPE_OFFSET].toInt().toChar().let { if (it == '\u0000') '0' else it }
            val size = TarFields.octal(header, SIZE_OFFSET, SIZE_LENGTH)
            when (type) {
                'L' -> extra.longName = readText(size)
                'K' -> extra.longLink = readText(size)
                'x' -> extra.pax = TarFields.parsePax(readText(size))
                'g' -> readText(size)
                else -> return finish(header, type, size, extra)
            }
        }
    }

    /** Long names and pax records seen before the entry's own header. */
    private class ExtendedFields {
        var longName: String? = null
        var longLink: String? = null
        var pax: Map<String, String> = emptyMap()
    }

    private fun finish(header: ByteArray, type: Char, size: Long, extra: ExtendedFields): Entry {
        val pax = extra.pax
        val prefix = TarFields.text(header, PREFIX_OFFSET, PREFIX_LENGTH)
        val plainName = TarFields.text(header, 0, NAME_LENGTH).let { if (prefix.isNotEmpty()) "$prefix/$it" else it }
        val realSize = pax["size"]?.toLongOrNull() ?: size
        remaining = if (type == FILE || type == FILE_OLD) realSize else 0L
        padding = ((BLOCK - realSize % BLOCK) % BLOCK).toInt()
        if (type != FILE && type != FILE_OLD) {
            // Link and directory headers carry no body.
            remaining = 0L
            padding = 0
        }
        return Entry(
            pax["path"] ?: extra.longName ?: plainName,
            type,
            TarFields.octal(header, MODE_OFFSET, MODE_LENGTH).toInt() and MODE_MASK,
            realSize,
            pax["linkpath"] ?: extra.longLink ?: TarFields.text(header, LINK_OFFSET, NAME_LENGTH),
        )
    }

    /** Copies the current regular file's bytes; a short archive is an [EOFException]. */
    fun copyBody(out: java.io.OutputStream) {
        val buffer = ByteArray(COPY_BUFFER)
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) throw EOFException("short archive")
            out.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun skipBody() {
        val total = remaining + padding
        remaining = 0
        padding = 0
        skipFully(total)
    }

    private fun skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) { left -= skipped; continue }
            if (input.read() < 0) throw EOFException("short archive")
            left--
        }
    }

    private fun readText(size: Long): String {
        if (size < 0 || size > MAX_META) throw SetupAppUnpack.UnpackFailure("the archive is damaged")
        val out = ByteArrayOutputStream()
        val bytes = readFully(size.toInt())
        out.write(bytes)
        skipFully((BLOCK - size % BLOCK) % BLOCK)
        return out.toString(Charsets.UTF_8.name()).trimEnd('\u0000', '\n')
    }

    private fun readFully(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var got = 0
        while (got < count) {
            val n = input.read(bytes, got, count - got)
            if (n < 0) throw EOFException("short archive")
            got += n
        }
        return bytes
    }

    /** The next 512-byte header, or null at the end-of-archive zero block. */
    private fun readBlock(): ByteArray? {
        val block = ByteArray(BLOCK)
        var got = 0
        while (got < BLOCK) {
            val n = input.read(block, got, BLOCK - got)
            if (n < 0) { if (got == 0) return null else throw EOFException("short archive") }
            got += n
        }
        return if (block.all { it == 0.toByte() }) null else block
    }

    companion object {
        const val FILE = '0'
        const val FILE_OLD = '\u0000'
        const val HARDLINK = '1'
        const val SYMLINK = '2'
        const val DIR = '5'
        private const val BLOCK = 512
        private const val NAME_LENGTH = 100
        private const val MODE_OFFSET = 100
        private const val MODE_LENGTH = 8
        private const val MODE_MASK = 0xFFF
        private const val SIZE_OFFSET = 124
        private const val SIZE_LENGTH = 12
        private const val TYPE_OFFSET = 156
        private const val LINK_OFFSET = 157
        private const val PREFIX_OFFSET = 345
        private const val PREFIX_LENGTH = 155
        private const val MAX_META = 1_048_576L
        private const val COPY_BUFFER = 65_536
    }
}

/** Pure readers for tar header fields. */
private object TarFields {
    private const val OCTAL = 8
    private const val HIGH_BIT = 0x80
    private const val BYTE_BITS = 8
    private const val BYTE_MASK = 0xFFL

    fun parsePax(text: String): Map<String, String> {
        val result = HashMap<String, String>()
        for (record in text.split('\n')) {
            val body = record.substringAfter(' ', "")
            val key = body.substringBefore('=', "")
            if (key.isNotEmpty()) result[key] = body.substringAfter('=')
        }
        return result
    }

    fun text(block: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && block[end] != 0.toByte()) end++
        return String(block, offset, end - offset, Charsets.UTF_8)
    }

    fun octal(block: ByteArray, offset: Int, length: Int): Long {
        if (block[offset].toInt() and HIGH_BIT != 0) {
            // GNU base-256 number.
            var value = (block[offset].toInt() and HIGH_BIT.inv()).toLong()
            for (i in 1 until length) value = (value shl BYTE_BITS) or (block[offset + i].toLong() and BYTE_MASK)
            return value
        }
        val digits = text(block, offset, length).trim()
        val value = if (digits.isEmpty()) 0L else digits.toLongOrNull(OCTAL)
        return value ?: throw SetupAppUnpack.UnpackFailure("the archive is damaged")
    }
}

/** Path checks that keep every write inside the destination folder. */
private object UnpackGuards {
    /** Refuses absolute and `..` paths in the archive. */
    fun safeName(raw: String): String {
        if (raw.startsWith("/") || raw.contains('\u0000') || raw.split('/').any { it == ".." }) {
            throw SetupAppUnpack.UnpackFailure("the archive has an unsafe path")
        }
        return raw
    }

    /** The parent of [target] (links resolved) must still be inside [base]. */
    fun requireInside(base: File, target: File) {
        val parent = target.parentFile ?: throw SetupAppUnpack.UnpackFailure("the archive has an unsafe path")
        parent.mkdirs()
        val real = parent.canonicalFile
        if (real != base && !real.path.startsWith(base.path + File.separator)) {
            throw SetupAppUnpack.UnpackFailure("the archive has an unsafe path")
        }
    }
}

/** Small file operations for one archive entry. */
private object UnpackFiles {
    fun link(target: File, linkTarget: String) {
        Files.deleteIfExists(target.toPath())
        Files.createSymbolicLink(target.toPath(), File(linkTarget).toPath())
    }

    fun applyMode(target: File, mode: Int) {
        val permissions = buildSet {
            for ((bit, permission) in MODE_TABLE) if (mode and bit != 0) add(permission)
            // The app itself must be able to replace what it wrote.
            add(PosixFilePermission.OWNER_READ)
            add(PosixFilePermission.OWNER_WRITE)
            if (target.isDirectory) add(PosixFilePermission.OWNER_EXECUTE)
        }
        Files.setPosixFilePermissions(target.toPath(), permissions)
    }

    /** Unix mode bits (the tar header stores them in octal) and the permission each one grants. */
    const val OWNER_READ_BIT = 0x100
    const val OWNER_WRITE_BIT = 0x80
    const val OWNER_EXECUTE_BIT = 0x40
    const val GROUP_READ_BIT = 0x20
    const val GROUP_WRITE_BIT = 0x10
    const val GROUP_EXECUTE_BIT = 0x8
    const val OTHERS_READ_BIT = 0x4
    const val OTHERS_WRITE_BIT = 0x2
    const val OTHERS_EXECUTE_BIT = 0x1

    val MODE_TABLE = listOf(
        OWNER_READ_BIT to PosixFilePermission.OWNER_READ,
        OWNER_WRITE_BIT to PosixFilePermission.OWNER_WRITE,
        OWNER_EXECUTE_BIT to PosixFilePermission.OWNER_EXECUTE,
        GROUP_READ_BIT to PosixFilePermission.GROUP_READ,
        GROUP_WRITE_BIT to PosixFilePermission.GROUP_WRITE,
        GROUP_EXECUTE_BIT to PosixFilePermission.GROUP_EXECUTE,
        OTHERS_READ_BIT to PosixFilePermission.OTHERS_READ,
        OTHERS_WRITE_BIT to PosixFilePermission.OTHERS_WRITE,
        OTHERS_EXECUTE_BIT to PosixFilePermission.OTHERS_EXECUTE,
    )
}
