package io.github.eslamasabry.opencode_mobile

import android.content.Context

/**
 * The runtime's terminal inventory, registered without a reverse dependency. Two units register drivers: LINUX-2's
 * local terminal ([Backend] through [install]) and AGENT-2's terminal manager ([Driver] through [bind]). The
 * inventory is every driver's sessions; a new session starts on AGENT-2's manager when it is present.
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
        /** LINUX-2's shape: liveness and stop as functions. */
        constructor(
            id: Int,
            pid: Int,
            confined: Boolean,
            live: () -> Boolean,
            stopSession: () -> Unit,
        ) : this(
            id,
            pid,
            confined,
            object : Control {
                override val running: Boolean
                    get() = live()

                override val exitCode: Int? = null

                override fun stop() = stopSession()
            },
            false,
        )

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

    /** LINUX-2's name for a driver. */
    interface Backend : Driver

    @Volatile private var drivers: List<Driver> = emptyList()

    fun bind(value: Driver) {
        synchronized(this) { if (drivers.none { it === value }) drivers = drivers + value }
    }

    fun install(value: Backend) = bind(value)

    fun start(
        rows: Int,
        cols: Int,
        signInProfile: String? = null,
        signInProgram: List<String>? = null,
    ): Session {
        val current = drivers
        val driver =
            checkNotNull(current.firstOrNull { it !is Backend } ?: current.firstOrNull()) {
                "The terminal host is unavailable."
            }
        return driver.start(rows, cols, signInProfile, signInProgram)
    }

    fun list(): List<Session> = drivers.flatMap { it.list() }

    fun hasLiveSessions(): Boolean = list().any { it.running }

    fun hasUnconfinedSessions(): Boolean = list().any { it.running && !it.confined }

    fun remove(id: Int) {
        drivers.firstOrNull { driver -> driver.list().any { it.id == id } }?.remove(id)
    }

    companion object {
        private val instance = LocalTerminal()

        fun get(@Suppress("UNUSED_PARAMETER") context: Context): LocalTerminal = instance
    }
}
