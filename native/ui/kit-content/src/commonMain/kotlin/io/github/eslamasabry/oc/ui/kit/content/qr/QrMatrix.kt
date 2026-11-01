package io.github.eslamasabry.oc.ui.kit.content.qr

import androidx.compose.runtime.Immutable

/**
 * A finished QR symbol: [size] × [size] modules, row-major, `true` = dark. The quiet zone is not part of it; `KitQr`
 * adds four light modules on every side when drawing.
 */
@Immutable
public class QrMatrix internal constructor(
    public val size: Int,
    /** The ISO 18004 version (1…40). */
    public val version: Int,
    /** The mask pattern (0…7) the symbol was built with. */
    public val mask: Int,
    private val modules: BooleanArray,
) {
    public fun isDark(row: Int, col: Int): Boolean = modules[row * size + col]

    override fun equals(other: Any?): Boolean =
        other is QrMatrix && other.size == size && other.mask == mask && other.modules.contentEquals(modules)

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()
}
