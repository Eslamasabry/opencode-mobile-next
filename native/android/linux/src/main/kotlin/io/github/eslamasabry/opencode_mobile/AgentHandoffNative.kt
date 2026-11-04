package io.github.eslamasabry.opencode_mobile

/**
 * Native-owned filesystem evidence. Matching project paths or transport origins cannot create one.
 */
class NativeHandoffFilesystem
internal constructor(
    val filesystemId: String,
    private val remainsCurrent: () -> Boolean,
) {
    fun isCurrent(): Boolean =
        try {
            remainsCurrent()
        } catch (_: Exception) {
            false
        }
}

/**
 * Exact native process admission retained without exposing a process, credential or filesystem
 * path.
 */
class NativeHandoffIdentity internal constructor(private val remainsCurrent: () -> Boolean) {
    fun isCurrent(): Boolean =
        try {
            remainsCurrent()
        } catch (_: Exception) {
            false
        }
}
