package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ManagedShellGateway
import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellList
import io.github.eslamasabry.oc.core.model.ManagedShellOutput
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.protocol.oc2.wire.bool
import io.github.eslamasabry.oc.protocol.oc2.wire.int
import io.github.eslamasabry.oc.protocol.oc2.wire.long
import io.github.eslamasabry.oc.protocol.oc2.wire.obj
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlin.time.Duration
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * B-LINUX-060/064: the frozen `/api/shell` contract, pinned to a project location for the lifetime
 * of this adapter. Every mutation dispatches once; a lost create reply is recovered through its
 * saved ownership nonce by the engine.
 */
internal class Oc2ManagedShells(private val gateway: Oc2Gateway, location: ServerLocation) :
    ManagedShellGateway {
    private val query =
        mapOf(
            "location[directory]" to location.directory,
            "location[workspace]" to location.workspace,
        )

    override suspend fun startManagedShell(
        command: String,
        directory: String,
        ownerToken: String,
    ): Outcome<ManagedShell> {
        if (
            command.isBlank() ||
                command.length > 4096 ||
                directory.isBlank() ||
                directory.length > 4096 ||
                ownerToken.isBlank() ||
                ownerToken.length > 100
        ) {
            return Failure.Rejected(400).failed()
        }
        return when (
            val answer =
                gateway.json(
                    "/shell",
                    HttpMethod.Post,
                    buildJsonObject {
                        put("command", command)
                        put("cwd", directory)
                        put("timeout", 0)
                        put(
                            "metadata",
                            buildJsonObject { put("ocDevelopmentServiceOwner", ownerToken) },
                        )
                    },
                    query,
                )
        ) {
            is Outcome.Failed -> answer
            is Outcome.Ok -> decodeShell(answer.value)?.ok() ?: unreadable()
        }
    }

    override suspend fun runningShells(): Outcome<ManagedShellList> {
        return when (val answer = gateway.json("/shell", query = query)) {
            is Outcome.Failed -> {
                val failure = answer.failure
                val unsupported =
                    failure is Failure.NotFound ||
                        (failure is Failure.Rejected && failure.status == 405) ||
                        (failure is Failure.Server && failure.status == 501)
                if (unsupported) ManagedShellList(false, persistentListOf()).ok() else answer
            }
            is Outcome.Ok -> {
                val list = data(answer.value) as? JsonArray
                if (list == null || list.size > 2000) unreadable()
                else {
                    val shells = list.map { decodeShell(it) ?: return unreadable() }
                    ManagedShellList(true, shells.toImmutableList()).ok()
                }
            }
        }
    }

    override suspend fun managedShell(id: String): Outcome<ManagedShell?> {
        if (!validId(id)) return Failure.Rejected(400).failed()
        return when (val answer = gateway.json("/shell/${id.encodeURLPathPart()}", query = query)) {
            is Outcome.Failed ->
                if (answer.failure is Failure.NotFound) Outcome.Ok(null) else answer
            is Outcome.Ok -> {
                val shell = decodeShell(answer.value)
                if (shell == null || shell.id != id) unreadable() else shell.ok()
            }
        }
    }

    override suspend fun managedShellOutput(
        id: String,
        cursor: Long,
        limit: Int,
    ): Outcome<ManagedShellOutput> {
        if (!validId(id) || cursor < 0) return Failure.Rejected(400).failed()
        val cap = limit.coerceIn(1, 65_536)
        return when (
            val answer =
                gateway.json(
                    "/shell/${id.encodeURLPathPart()}/output",
                    query = query + mapOf("cursor" to cursor.toString(), "limit" to cap.toString()),
                )
        ) {
            is Outcome.Failed -> answer
            is Outcome.Ok -> {
                val obj = data(answer.value) as? JsonObject ?: return unreadable()
                val text = obj.str("output") ?: return unreadable()
                val next = obj.long("cursor") ?: return unreadable()
                val size = obj.long("size") ?: return unreadable()
                val truncated = obj.bool("truncated") ?: return unreadable()
                if (
                    size < 0 ||
                        next < cursor ||
                        next > size ||
                        next - cursor > cap ||
                        text.length > cap
                )
                    unreadable()
                else ManagedShellOutput(text, next, size, truncated).ok()
            }
        }
    }

    override suspend fun stopManagedShell(id: String): Outcome<Unit> {
        if (!validId(id)) return Failure.Rejected(400).failed()
        return when (
            val answer =
                gateway.json("/shell/${id.encodeURLPathPart()}", HttpMethod.Delete, query = query)
        ) {
            is Outcome.Failed -> answer
            is Outcome.Ok -> Outcome.Ok(Unit)
        }
    }

    override suspend fun setManagedShellTimeout(
        id: String,
        timeout: Duration?,
    ): Outcome<ManagedShell> {
        if (!validId(id) || timeout?.isNegative() == true || timeout?.isInfinite() == true)
            return Failure.Rejected(400).failed()
        return when (
            val answer =
                gateway.json(
                    "/shell/${id.encodeURLPathPart()}/timeout",
                    HttpMethod.Patch,
                    buildJsonObject { put("timeout", timeout?.inWholeMilliseconds ?: 0) },
                    query,
                )
        ) {
            is Outcome.Failed -> answer
            is Outcome.Ok -> {
                val shell = decodeShell(answer.value)
                if (shell == null || shell.id != id) unreadable() else shell.ok()
            }
        }
    }

    override suspend fun managedShellServerIdentity(): Outcome<String?> {
        return when (val answer = gateway.json("/health")) {
            is Outcome.Failed -> answer
            is Outcome.Ok -> {
                val obj = data(answer.value) as? JsonObject ?: return unreadable()
                Outcome.Ok(obj.long("pid")?.takeIf { it > 0 }?.toString())
            }
        }
    }

    private fun data(element: JsonElement): JsonElement =
        (element as? JsonObject)?.get("data") ?: element

    private fun decodeShell(element: JsonElement): ManagedShell? {
        val obj = data(element) as? JsonObject ?: return null
        val id = obj.str("id")?.takeIf(::validId) ?: return null
        val command = obj.str("command") ?: return null
        val time = obj.obj("time") ?: return null
        val started = time.long("started")?.takeIf { it >= 0 } ?: return null
        val status =
            when (obj.str("status")) {
                "running" -> ManagedShellStatus.Running
                "exited" -> ManagedShellStatus.Exited
                "timeout" -> ManagedShellStatus.Timeout
                "killed" -> ManagedShellStatus.Killed
                else -> ManagedShellStatus.Unknown
            }
        return ManagedShell(
            id = id,
            command = command,
            status = status,
            startedAtMs = started,
            completedAtMs = time.long("completed"),
            exitCode = obj.int("exit"),
            sessionId = null,
            directory = obj.str("cwd"),
            ownerToken = obj.obj("metadata")?.str("ocDevelopmentServiceOwner"),
        )
    }

    private fun validId(id: String): Boolean =
        id.startsWith("sh_") &&
            id.length in 4..200 &&
            id.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }

    private fun unreadable(): Outcome.Failed = Outcome.Failed(Failure.UnreadableReply())
}
