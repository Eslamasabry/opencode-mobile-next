package io.github.eslamasabry.oc.engine.project.terminal

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.Flow

/** What the Android side (LocalTerminal.kt) says about one shell. */
public data class LocalShellInfo(
    val id: Int,
    val pid: Int,
    val running: Boolean = true,
    val exitCode: Int? = null,
    /** An agent's sign-in, not a shell: one left from an earlier run belongs to no screen and is removed on load. */
    val signIn: Boolean = false,
)

/**
 * One reading of `list`: the shells that exist, and how many processes run as this app (Android stops extras
 * past 32).
 */
public data class LocalTerminalListing(val shells: List<LocalShellInfo>, val processes: Int? = null)

/** What the Android event channel pushes. */
public sealed interface LocalTerminalEvent {
    public val id: Int

    public class Output(override val id: Int, public val data: ByteArray) : LocalTerminalEvent

    public data class Exit(override val id: Int, val code: Int) : LocalTerminalEvent

    /** A sign-in asked the system to open a page (its stand-in `xdg-open`); checked before anything opens it. */
    public data class OpenUrl(override val id: Int, val url: String) : LocalTerminalEvent
}

/**
 * The calls into LocalTerminal.kt (Flutter `LocalTerminalBackend`); the Android app binds the channel, tests a fake.
 * Android owns the processes: shells outlive every screen and every run of this side.
 */
public interface LocalTerminalPort {
    public val events: Flow<LocalTerminalEvent>

    /** Starts `bash -l` on a PTY of this size. A refusal is a typed failure (for example Runtime NotInstalled). */
    public suspend fun start(rows: Int, cols: Int): Outcome<LocalShellInfo>

    public suspend fun list(): Outcome<LocalTerminalListing>

    /** The recent output of a shell this side has not seen yet. */
    public suspend fun attach(id: Int): Outcome<ByteArray?>

    public suspend fun write(id: Int, data: ByteArray): Outcome<Unit>

    /** The last output chunk of [id] is drawn: the next may come. */
    public suspend fun ack(id: Int): Outcome<Unit>

    public suspend fun resize(id: Int, rows: Int, cols: Int): Outcome<Unit>

    /** Stops the shell and everything started from it. */
    public suspend fun stop(id: Int): Outcome<Unit>

    /** Forgets the shell, stopping it first when it still runs. */
    public suspend fun remove(id: Int): Outcome<Unit>
}

/**
 * The Android channel shapes (Flutter `ChannelLocalTerminalBackend`): method channel
 * [METHOD_CHANNEL], event channel [EVENT_CHANNEL]. The app's binding hands the decoded platform values here.
 */
public object LocalTerminalChannel {
    public const val METHOD_CHANNEL: String = "io.github.eslamasabry.opencode_mobile/local_terminal"
    public const val EVENT_CHANNEL: String = "io.github.eslamasabry.opencode_mobile/local_terminal/events"

    /** `{type: output, id, data: bytes}`, `{type: exit, id, code}`, `{type: openUrl, id, url}`; else null. */
    public fun parseEvent(raw: Any?): LocalTerminalEvent? {
        if (raw !is Map<*, *>) return null
        val id = raw["id"] as? Int ?: return null
        return when (raw["type"]) {
            "output" -> (raw["data"] as? ByteArray)?.let { LocalTerminalEvent.Output(id, it) }
            "exit" -> LocalTerminalEvent.Exit(id, (raw["code"] as? Number)?.toInt() ?: -1)
            "openUrl" -> (raw["url"] as? String)?.let { LocalTerminalEvent.OpenUrl(id, it) }
            else -> null
        }
    }

    /** One shell map: missing id reads -1, missing pid 0, `running` true unless false. */
    public fun parseShell(raw: Map<*, *>): LocalShellInfo =
        LocalShellInfo(
            id = (raw["id"] as? Number)?.toInt() ?: -1,
            pid = (raw["pid"] as? Number)?.toInt() ?: 0,
            running = raw["running"] != false,
            exitCode = (raw["exitCode"] as? Number)?.toInt(),
            signIn = raw["signIn"] == true,
        )

    /** The `list` reply: `{sessions: [shell maps], processes: n}`. */
    public fun parseListing(raw: Any?): LocalTerminalListing {
        val map = raw as? Map<*, *>
        val shells = (map?.get("sessions") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map(::parseShell)
        return LocalTerminalListing(shells = shells, processes = (map?.get("processes") as? Number)?.toInt())
    }
}
