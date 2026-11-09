package io.github.eslamasabry.oc.ui.kit.image

import androidx.compose.ui.graphics.ImageBitmap

/** Pixel size read from the picture's header, without decoding pixels. */
internal expect fun decodeBounds(bytes: ByteArray): Pair<Int, Int>?

/** Decodes [bytes] with the longest side at most [maxEdge] pixels (power-of-two sampling, then an exact scale). */
internal expect fun decodeSampled(bytes: ByteArray, maxEdge: Int): ImageBitmap?

/** A small encoded copy of [bitmap] for the disk cache, or null. */
internal expect fun encodeThumb(bitmap: ImageBitmap): ByteArray?

/** What the memory cache may hold: an eighth of the heap limit, between 8 and 64 MB. */
internal expect fun imageMemoryBudgetBytes(): Long
