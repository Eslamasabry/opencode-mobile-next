package io.github.eslamasabry.oc.engine.media.attach

/**
 * A chunked byte source (a platform stream, a picker's in-memory data). [read] fills up to `buffer.size` bytes and
 * returns the count, or -1 at the end.
 */
public fun interface ByteSource {
    public suspend fun read(buffer: ByteArray): Int
}

/**
 * Reads at most [limit] bytes (plan 10 §10.6 step 2, B-CHAT-010; port of `readAttachmentBytesWithinLimit`). Asks for
 * one byte past the limit, so a file that grew after the picker reported its size is refused as soon as the overflow
 * byte arrives and nothing beyond it is requested; null means "more than [limit]". Never trusts a reported size.
 */
public object BoundedRead {
    private const val CHUNK = 64 * 1024

    public suspend fun read(source: ByteSource, limit: Long): ByteArray? {
        require(limit >= 0)
        var out = ByteArray(minOf(limit + 1, CHUNK.toLong()).toInt().coerceAtLeast(1))
        var size = 0L
        while (true) {
            val want = minOf(CHUNK.toLong(), limit + 1 - size).toInt()
            if (want <= 0) return null
            val buffer = ByteArray(want)
            val n = source.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            if (size + n > limit) return null
            if (size + n > out.size) out = out.copyOf(maxOf(out.size * 2L, size + n).coerceAtMost(limit + 1).toInt())
            buffer.copyInto(out, size.toInt(), 0, n)
            size += n
        }
        return out.copyOf(size.toInt())
    }

    /** Whole in-memory data: refused without copying when it is over [limit]. */
    public fun check(bytes: ByteArray, limit: Long): ByteArray? = if (bytes.size > limit) null else bytes
}
