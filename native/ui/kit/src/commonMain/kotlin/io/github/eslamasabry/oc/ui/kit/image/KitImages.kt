package io.github.eslamasabry.oc.ui.kit.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The one picture pipeline of the app (audit gap 1): every picture a part draws from bytes goes through here.
 *
 * - **Downsampled decode:** a picture is decoded to at most `maxEdge` pixels on its longest side, never at full size
 *   (a 4000x3000 screenshot is 48 MB at full size and about 3 MB for a 400 dp tile).
 * - **Memory LRU:** bounded in bytes from the heap limit ([imageMemoryBudgetBytes]); a hit is on the first frame, so a
 *   lazy item scrolled back in, or a sent picture echoed by the server, does not blink.
 * - **Disk cache:** when a scope (the profile id) and a [KitImageDisk] are given, the downsampled thumbnail is kept on
 *   disk under that profile, so a later open skips the network read and the big decode. The app deletes it with the
 *   profile.
 * - **Off the main thread:** reading, decoding and encoding run on [Dispatchers.Default].
 *
 * The memory cache is touched only from the caller's own thread (the main thread in the app and in tests), as the
 * picture memo it replaces was; [trim] is called from `onTrimMemory`, also the main thread.
 */
public object KitImages {
    private val memory = KitImageMemory(imageMemoryBudgetBytes())

    /** Builds the disk cache for one scope (profile id). Set once by the app; null keeps pictures in memory only. */
    public var diskFor: ((scope: String) -> KitImageDisk)? = null

    /** The decoded picture for [key] at [maxEdge], or null; never reads or decodes. */
    public fun peek(key: String, maxEdge: Int): ImageBitmap? = memory[memoryKey(key, maxEdge)]

    /** Bytes held by the memory cache. */
    public val memoryBytes: Long get() = memory.bytes

    /** The most the memory cache keeps. */
    public val memoryBudgetBytes: Long get() = memory.budget

    /**
     * The picture for [key] at [maxEdge] pixels: memory, then the scope's disk cache, then [read] (decoded
     * downsampled and cached). Null when the bytes are missing or refused. Cancellation passes through.
     */
    @Suppress("TooGenericExceptionCaught") // a decoder's refusal of server bytes is expected; callers show a plain tile
    public suspend fun load(
        key: String,
        maxEdge: Int,
        scope: String? = null,
        work: CoroutineContext = Dispatchers.Default,
        read: suspend () -> ByteArray?,
    ): ImageBitmap? {
        val mk = memoryKey(key, maxEdge)
        memory[mk]?.let { return it }
        val disk = scope?.let { diskFor?.invoke(it) }
        val bitmap =
            try {
                withContext(work) {
                    val thumb = disk?.get(mk)?.let { decodeSampled(it, maxEdge) }
                    if (thumb != null) {
                        thumb
                    } else {
                        val bytes = read()?.takeIf { it.isNotEmpty() }
                        val decoded = bytes?.let { decodeSampled(it, maxEdge) }
                        if (decoded != null && disk != null) encodeThumb(decoded)?.let { disk.put(mk, it) }
                        decoded
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        if (bitmap != null) memory[mk] = bitmap
        return bitmap
    }

    /** [bytes] decoded downsampled, cached by their content (for callers that hold bytes but no address). */
    public suspend fun loadBytes(
        bytes: ByteArray,
        maxEdge: Int,
        work: CoroutineContext = Dispatchers.Default,
    ): ImageBitmap? {
        val key = withContext(work) { "b:${bytes.size}:${bytes.contentHashCode()}" }
        return load(key, maxEdge, null, work) { bytes }
    }

    /** [bytes] decoded downsampled to [maxEdge], uncached; null when refused. Runs on the caller's thread. */
    @Suppress("TooGenericExceptionCaught") // a decoder's refusal of server bytes is expected
    public fun decode(bytes: ByteArray, maxEdge: Int): ImageBitmap? =
        try {
            if (bytes.isEmpty()) null else decodeSampled(bytes, maxEdge)
        } catch (_: Exception) {
            null
        }

    /** The pixel size of an encoded picture without decoding its pixels, or null when it does not parse. */
    @Suppress("TooGenericExceptionCaught") // a decoder's refusal of server bytes is expected
    public fun sizeOf(bytes: ByteArray): Pair<Int, Int>? =
        try {
            if (bytes.isEmpty()) null else decodeBounds(bytes)?.takeIf { it.first > 0 && it.second > 0 }
        } catch (_: Exception) {
            null
        }

    /**
     * Frees memory for `onTrimMemory`: keep at most [keepFraction] (0 to 1) of the budget; 0 empties the cache.
     */
    public fun trim(keepFraction: Float) {
        memory.trimTo((memory.budget * keepFraction.coerceIn(0f, 1f)).toLong())
    }

    /** Empties the memory cache and, in tests, restores a budget. */
    public fun clearMemory() {
        memory.trimTo(0)
    }

    private fun memoryKey(key: String, maxEdge: Int) = "$key@$maxEdge"
}

/** A byte-bounded least-recently-used cache of decoded pictures. Single-threaded by contract. */
internal class KitImageMemory(val budget: Long) {
    private val map = LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)
    var bytes: Long = 0
        private set

    operator fun get(key: String): ImageBitmap? = map[key]

    operator fun set(key: String, bitmap: ImageBitmap) {
        val cost = bitmap.cost()
        if (cost > budget) return
        map.put(key, bitmap)?.let { bytes -= it.cost() }
        bytes += cost
        trimTo(budget)
    }

    fun trimTo(limit: Long) {
        val it = map.entries.iterator()
        while (bytes > limit && it.hasNext()) {
            val eldest = it.next()
            bytes -= eldest.value.cost()
            it.remove()
        }
    }

    private fun ImageBitmap.cost(): Long = width.toLong() * height * 4
}

/** Keeps downsampled thumbnails for one scope; the app implements it under the profile's own folder. */
public interface KitImageDisk {
    public suspend fun get(key: String): ByteArray?

    public suspend fun put(key: String, bytes: ByteArray)
}

/**
 * The picture for [key], held in [KitImages]. It is there on the first frame when the memory cache has it (no blink),
 * else null until [read] and the decode finish. [maxEdge] is the longest side a part will ever draw.
 */
@Composable
public fun rememberKitImage(
    key: String?,
    maxEdge: Dp,
    scope: String? = null,
    read: suspend () -> ByteArray?,
): State<ImageBitmap?> {
    val px = with(LocalDensity.current) { maxEdge.toPx() }.toInt().coerceAtLeast(1)
    val work = LocalKitImageWork.current
    return produceState(key?.let { KitImages.peek(it, px) }, key, px, scope) {
        value = if (key == null) null else KitImages.load(key, px, scope, work, read)
    }
}

/** A short key for a long address (a `data:` URL): length, hash and tail, never the whole text. */
public fun kitImageKeyOf(address: String): String =
    "${address.length}:${address.hashCode()}:${address.takeLast(24)}"

/**
 * Where pictures are read and decoded. The app provides its default dispatcher (off the main thread); tests and
 * previews keep the composition's own context, so the work stays deterministic.
 */
public val LocalKitImageWork: ProvidableCompositionLocal<CoroutineContext> =
    staticCompositionLocalOf { EmptyCoroutineContext }

/** The longest side a full-screen picture viewer asks for. */
public const val KIT_VIEWER_EDGE: Int = 2048
