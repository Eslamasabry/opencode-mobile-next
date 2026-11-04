package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellOutput
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.*

/**
 * Read-only owned-run evidence, invoked only while preparing the user's local problem-report
 * preview.
 */
public object Oc2ServiceReports {
    public suspend fun inspect(
        gateway: ServerGateway,
        id: String,
        directory: String,
        workspace: String?,
    ): Outcome<ManagedShell?>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        return when (
            val read =
                oc2.json("/shell/${id.encodeURLPathPart()}", query = location(directory, workspace))
        ) {
            is Outcome.Failed -> if (read.failure is Failure.NotFound) null.ok() else read
            is Outcome.Ok -> {
                val json =
                    data(read.value) as? JsonObject ?: return Failure.UnreadableReply().failed()
                val time = json["time"] as? JsonObject ?: return Failure.UnreadableReply().failed()
                val metadata = json["metadata"] as? JsonObject
                val shell =
                    ManagedShell(
                        id = json.text("id") ?: return Failure.UnreadableReply().failed(),
                        command = json.text("command") ?: return Failure.UnreadableReply().failed(),
                        status =
                            when (json.text("status")) {
                                "running" -> ManagedShellStatus.Running
                                "exited" -> ManagedShellStatus.Exited
                                "timeout" -> ManagedShellStatus.Timeout
                                "killed" -> ManagedShellStatus.Killed
                                else -> ManagedShellStatus.Unknown
                            },
                        startedAtMs =
                            time.number("started") ?: return Failure.UnreadableReply().failed(),
                        completedAtMs = time.number("completed"),
                        exitCode = json.number("exit")?.toInt(),
                        sessionId = metadata?.text("sessionID"),
                        directory = json.text("cwd"),
                        ownerToken = metadata?.text("ocDevelopmentServiceOwner"),
                    )
                shell.ok()
            }
        }
    }

    public suspend fun output(
        gateway: ServerGateway,
        id: String,
        directory: String,
        workspace: String?,
    ): Outcome<ManagedShellOutput>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        return when (
            val read =
                oc2.json(
                    "/shell/${id.encodeURLPathPart()}/output",
                    query =
                        location(directory, workspace) + mapOf("cursor" to "0", "limit" to "65536"),
                )
        ) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                val json =
                    data(read.value) as? JsonObject ?: return Failure.UnreadableReply().failed()
                ManagedShellOutput(
                        json.text("output").orEmpty(),
                        json.number("cursor") ?: return Failure.UnreadableReply().failed(),
                        json.number("size") ?: return Failure.UnreadableReply().failed(),
                        (json["truncated"] as? JsonPrimitive)?.booleanOrNull == true,
                    )
                    .ok()
            }
        }
    }

    private fun location(directory: String, workspace: String?): Map<String, String?> =
        mapOf("location[directory]" to directory, "location[workspace]" to workspace)

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
}
