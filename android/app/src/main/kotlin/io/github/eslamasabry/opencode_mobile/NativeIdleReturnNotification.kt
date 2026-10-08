package io.github.eslamasabry.opencode_mobile

/** Plain return-to-app notification policy, independent of process and service admission. */
internal class NativeIdleReturnNotification(
    private val show: () -> Boolean,
    private val remove: () -> Unit,
) {
    data class State(
        val available: Boolean,
        val owner: String?,
        val generation: Long,
        val idleStopped: Boolean,
        val wanted: Boolean,
        val userStopped: Boolean,
        val foreground: Boolean,
        val serverRunning: Boolean,
    )

    private data class Key(val owner: String, val generation: Long)
    private var shown: Key? = null

    @Synchronized fun update(state: State): Boolean {
        val owner = state.owner
        val eligible = state.available && owner != null && OWNER.matches(owner) &&
            state.generation > 0 && state.idleStopped && state.wanted &&
            !state.userStopped && !state.foreground && !state.serverRunning
        if (!eligible) {
            clear()
            return false
        }
        val key = Key(owner!!, state.generation)
        if (shown == key) return true
        // A refused replacement must not let a later check skip its retry.
        shown = null
        val visible = try { show() } catch (_: Throwable) { false }
        if (visible) shown = key
        return visible
    }

    @Synchronized fun clear() {
        shown = null
        // The platform can retain a notification from an earlier app process.
        // Clear even when this instance has never successfully called show.
        try { remove() } catch (_: Throwable) { }
    }

    private companion object {
        val OWNER = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
