package io.github.eslamasabry.opencode_mobile

import android.content.Context

/**
 * The runtime's terminal inventory, registered by :android:terminal without a reverse dependency.
 */
class LocalTerminal private constructor() {
    interface Control {
        val running: Boolean
        val exitCode: Int?

        fun stop()
    }

    class Session(
        val id: Int,
        val pid: Int,
        val confined: Boolean,
        private val control: Control,
        private val signIn: Boolean,
    ) {
        val running: Boolean
            get() = control.running

        fun stop() = control.stop()

        fun toMap(): Map<String, Any?> =
            mapOf(
                "id" to id,
                "pid" to pid,
                "running" to running,
                "exitCode" to control.exitCode,
                "confined" to confined,
                "signIn" to signIn,
            )
    }

    interface Driver {
        fun start(
            rows: Int,
            cols: Int,
            signInProfile: String?,
            signInProgram: List<String>?,
        ): Session

        fun list(): List<Session>

        fun remove(id: Int)
    }

    @Volatile private var driver: Driver? = null

    fun bind(value: Driver) {
        synchronized(this) {
            check(driver == null || driver === value)
            driver = value
        }
    }

    fun start(
        rows: Int,
        cols: Int,
        signInProfile: String? = null,
        signInProgram: List<String>? = null,
    ): Session =
        checkNotNull(driver) { "The terminal host is unavailable." }
            .start(rows, cols, signInProfile, signInProgram)

    fun list(): List<Session> = driver?.list().orEmpty()

    fun hasLiveSessions(): Boolean = list().any { it.running }

    fun hasUnconfinedSessions(): Boolean = list().any { it.running && !it.confined }

    fun remove(id: Int) {
        driver?.remove(id)
    }

    companion object {
        private val instance = LocalTerminal()

        fun get(@Suppress("UNUSED_PARAMETER") context: Context): LocalTerminal = instance
    }
}
