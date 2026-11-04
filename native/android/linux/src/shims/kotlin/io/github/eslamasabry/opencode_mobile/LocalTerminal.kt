package io.github.eslamasabry.opencode_mobile

import android.content.Context

/**
 * Runtime-facing registry: the app graph supplies the real PTY manager, avoiding a platform-module
 * cycle.
 */
class LocalTerminal private constructor() {
    class Session(
        val id: Int,
        val pid: Int,
        val confined: Boolean,
        private val live: () -> Boolean,
        private val stopSession: () -> Unit,
    ) {
        val running: Boolean
            get() = live()

        fun stop() = stopSession()

        fun toMap(): Map<String, Any?> =
            mapOf("id" to id, "pid" to pid, "running" to running, "confined" to confined)
    }

    interface Backend {
        fun start(
            rows: Int,
            cols: Int,
            signInProfile: String?,
            signInProgram: List<String>?,
        ): Session

        fun list(): List<Session>

        fun remove(id: Int)
    }

    @Volatile private var backend: Backend? = null

    fun install(value: Backend) {
        backend = value
    }

    fun start(
        rows: Int,
        cols: Int,
        signInProfile: String? = null,
        signInProgram: List<String>? = null,
    ): Session =
        checkNotNull(backend) { "terminal_unavailable" }
            .start(rows, cols, signInProfile, signInProgram)

    fun list(): List<Session> = backend?.list().orEmpty()

    fun hasLiveSessions(): Boolean = list().any { it.running }

    fun hasUnconfinedSessions(): Boolean = list().any { it.running && !it.confined }

    fun remove(id: Int) {
        backend?.remove(id)
    }

    companion object {
        private val instance = LocalTerminal()

        fun get(@Suppress("UNUSED_PARAMETER") context: Context): LocalTerminal = instance
    }
}
