package io.github.eslamasabry.oc.engine.media.office

/**
 * The XML parts of a ZIP archive, read from the **central directory** (plan 10 §10.7), so entries written with data
 * descriptors (as Excel writes them) work. Port of `_unzip` in `lib/domain/office_text.dart`, with the plan's new
 * guards: at most [MAX_ENTRIES] entries, at most [MAX_ENTRY_BYTES] inflated per entry, encrypted entries refused.
 */
internal object ZipCentralDirectory {
    const val MAX_ENTRIES: Int = 2_000
    const val MAX_ENTRY_BYTES: Int = 64 * 1024 * 1024

    sealed interface Result {
        data class Entries(val files: Map<String, ByteArray>) : Result

        data object NotZip : Result

        data object Damaged : Result

        data object Encrypted : Result

        data object Unsupported : Result

        data object TooLarge : Result
    }

    /** Reads only the entries [wanted] accepts (by path), so the media of a document is never inflated. */
    fun read(bytes: ByteArray, wanted: (String) -> Boolean = ::isXmlPart): Result {
        var end = -1
        var i = bytes.size - 22
        val floor = bytes.size - (64 * 1024 + 22)
        while (i >= 0 && i >= floor) {
            if (u32(bytes, i) == 0x06054b50L) {
                end = i
                break
            }
            i--
        }
        if (end < 0) return Result.NotZip
        val count = u16(bytes, end + 10)
        if (count > MAX_ENTRIES) return Result.TooLarge
        var at = u32(bytes, end + 16)
        val files = LinkedHashMap<String, ByteArray>()
        repeat(count) {
            if (at + 46 > bytes.size || u32(bytes, at.toInt()) != 0x02014b50L) return Result.Damaged
            val p = at.toInt()
            val flags = u16(bytes, p + 8)
            val method = u16(bytes, p + 10)
            val packed = u32(bytes, p + 20)
            val unpacked = u32(bytes, p + 24)
            val nameLength = u16(bytes, p + 28)
            val extraLength = u16(bytes, p + 30)
            val commentLength = u16(bytes, p + 32)
            val local = u32(bytes, p + 42)
            if (p + 46 + nameLength > bytes.size) return Result.Damaged
            val path = bytes.decodeToString(p + 46, p + 46 + nameLength)
            at += 46L + nameLength + extraLength + commentLength
            if (flags and 1 != 0) return Result.Encrypted
            if (!wanted(path)) return@repeat
            if (local + 30 > bytes.size) return Result.Damaged
            val l = local.toInt()
            if (u32(bytes, l) != 0x04034b50L) return Result.Damaged
            val start = local + 30 + u16(bytes, l + 26) + u16(bytes, l + 28)
            if (start + packed > bytes.size) return Result.Damaged
            if (unpacked > MAX_ENTRY_BYTES) return Result.TooLarge
            val s = start.toInt()
            val n = packed.toInt()
            files[path] =
                when (method) {
                    0 -> bytes.copyOfRange(s, s + n)
                    8 -> RawInflate.inflate(bytes, s, n, MAX_ENTRY_BYTES) ?: return Result.Damaged
                    else -> return Result.Unsupported
                }
        }
        return Result.Entries(files)
    }

    fun isXmlPart(path: String): Boolean = path.endsWith(".xml") || path.endsWith(".rels")

    private fun u16(b: ByteArray, i: Int): Int =
        if (i < 0 || i + 2 > b.size) -1 else (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, i: Int): Long =
        if (i < 0 || i + 4 > b.size) {
            -1
        } else {
            (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or
                ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)
        }
}
