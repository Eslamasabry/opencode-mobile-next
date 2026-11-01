// S1-LINUX shim (native-authored, not a twin). TODO(S3/S5 :android:terminal): the Flutter LocalTerminal imports
// io.flutter (banned in native/) and Termux's terminal-emulator (allowed only in :android:terminal, ExternalRules), so it
// is not copied in slice 1. Until :android:terminal exists there are no terminal sessions, which is what this answers.
package io.github.eslamasabry.opencode_mobile

import android.content.Context

/** The members BuiltinLinux reads, with the Flutter class's signatures. It never holds a session. */
class LocalTerminal private constructor() {
    /** Mirror of `LocalTerminal.Session`'s members that BuiltinLinux reads. */
    class Session(val id: Int, val pid: Int, val confined: Boolean) {
        val running: Boolean get() = false
        fun stop() = Unit
        fun toMap(): Map<String, Any?> = mapOf("id" to id, "pid" to pid, "running" to running, "confined" to confined)
    }

    /**
     * Same signature as the Flutter class (BuiltinWorkLeaseAcceptance calls it). There is no PTY in this module, so it
     * refuses; that acceptance step stays red on a device until :android:terminal replaces this shim.
     */
    @Suppress("UNUSED_PARAMETER")
    fun start(rows: Int, cols: Int, signInProfile: String? = null, signInProgram: List<String>? = null): Session =
        throw UnsupportedOperationException("The local terminal is not part of :android:linux yet")

    @Synchronized fun list(): List<Session> = emptyList()

    @Synchronized fun hasLiveSessions(): Boolean = false

    @Synchronized fun hasUnconfinedSessions(): Boolean = false

    fun remove(@Suppress("UNUSED_PARAMETER") id: Int) = Unit

    companion object {
        private val instance = LocalTerminal()

        fun get(@Suppress("UNUSED_PARAMETER") context: Context): LocalTerminal = instance
    }
}
