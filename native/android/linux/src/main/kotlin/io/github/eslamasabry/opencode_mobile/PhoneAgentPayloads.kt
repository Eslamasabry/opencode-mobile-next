package io.github.eslamasabry.opencode_mobile

/**
 * Read-only catalog payload presence, including interrupted staging; null preserves unknown storage
 * state.
 */
object PhoneAgentPayloads {
    fun present(linux: BuiltinLinux, executable: String): Boolean? =
        PhoneAgentInventory.present(linux.rootfs, executable)
}
