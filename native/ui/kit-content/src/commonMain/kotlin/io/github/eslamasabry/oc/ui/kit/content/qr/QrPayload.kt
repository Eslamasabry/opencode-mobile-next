package io.github.eslamasabry.oc.ui.kit.content.qr

/** What `KitQr` can do with a payload (B-KIT-026: an empty payload is refused, never drawn as a blank code). */
public sealed interface QrPayload {
    /** Refused: there is nothing to encode. */
    public data object Empty : QrPayload

    /** Too long for any version at error correction M; the part shows words instead of a code. */
    public data object TooLong : QrPayload

    public data class Ready(val matrix: QrMatrix) : QrPayload

    public companion object {
        /** Never throws; the encoder runs inside this call (plan 10 §14.1: composition never throws). */
        public fun of(data: String): QrPayload {
            if (data.isEmpty()) return Empty
            val matrix =
                try {
                    QrEncoder.encode(data)
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: IndexOutOfBoundsException) {
                    null
                }
            return if (matrix == null) TooLong else Ready(matrix)
        }
    }
}
