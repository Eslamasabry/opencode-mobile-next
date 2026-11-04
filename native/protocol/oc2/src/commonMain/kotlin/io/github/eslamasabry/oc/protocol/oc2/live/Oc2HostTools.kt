package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.protocol.oc2.wire.*
import io.ktor.http.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*

/** Location is captured once before a workflow's first suspension. */
internal class Oc2HostTools(private val gateway: Oc2Gateway, private val location: ServerLocation) {
    private val query =
        mapOf(
            "location[directory]" to location.directory,
            "location[workspace]" to location.workspace,
        )

    suspend fun initializeGit(): Outcome<Unit> =
        when (
            val reply =
                gateway.json("/vcs/init", HttpMethod.Post, query = query + ("provider" to "git"))
        ) {
            is Outcome.Failed -> if (reply.failure is Failure.Conflict) Outcome.Ok(Unit) else reply
            is Outcome.Ok -> Outcome.Ok(Unit)
        }

    suspend fun createWorktree(directory: String, name: String?): Outcome<WorktreeInfo> {
        if (
            directory.isBlank() ||
                directory.length > 4096 ||
                (name != null && (name.isBlank() || name.length > 200))
        )
            return Failure.Rejected(400).failed()
        val scoped = query + ("location[directory]" to directory)
        val current = gateway.json("/project/current", query = scoped)
        if (current is Outcome.Failed) return current
        val project =
            (data((current as Outcome.Ok).value) as? JsonObject)?.str("id")
                ?: return Failure.UnreadableReply().failed()
        val reply =
            gateway.json(
                "/worktree/${project.encodeURLPathPart()}",
                HttpMethod.Post,
                buildJsonObject { name?.let { put("name", it) } },
                scoped,
            )
        if (reply is Outcome.Failed) return reply
        val obj =
            data((reply as Outcome.Ok).value) as? JsonObject
                ?: return Failure.UnreadableReply().failed()
        val path =
            obj.str("directory")?.takeIf { it.isNotBlank() }
                ?: return Failure.UnreadableReply().failed()
        return WorktreeInfo(
                obj.str("name") ?: name ?: path.substringAfterLast('/'),
                path,
                obj.str("branch"),
                obj.bool("ready") ?: true,
            )
            .ok()
    }

    suspend fun shellSettings(): Outcome<TerminalShellSettings> {
        val shells = gateway.json("/config/shell", query = query)
        if (shells is Outcome.Failed) return shells
        val rows =
            data((shells as Outcome.Ok).value) as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        if (rows.size > 200) return Failure.UnreadableReply().failed()
        val options = rows.map {
            val obj = it as? JsonObject ?: return Failure.UnreadableReply().failed()
            TerminalShellOption(
                obj.str("path") ?: return Failure.UnreadableReply().failed(),
                obj.str("name") ?: return Failure.UnreadableReply().failed(),
                obj.bool("acceptable") ?: return Failure.UnreadableReply().failed(),
            )
        }
        val config = gateway.json("/config", query = query)
        if (config is Outcome.Failed) return config
        val layers =
            data((config as Outcome.Ok).value) as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        var selected = ""
        for (layer in layers) {
            val obj = layer as? JsonObject ?: return Failure.UnreadableReply().failed()
            val info = obj.obj("info") ?: return Failure.UnreadableReply().failed()
            info.str("shell")?.let { selected = it }
        }
        return TerminalShellSettings(selected, options.toImmutableList()).ok()
    }

    suspend fun selectShell(value: String): Outcome<Unit> {
        if (value.isNotEmpty()) {
            val settings = shellSettings()
            if (settings is Outcome.Failed) return settings
            if (!(settings as Outcome.Ok).value.options.any { it.path == value && it.acceptable })
                return Failure.Rejected(400).failed()
        }
        return gateway
            .json(
                "/experimental/config",
                HttpMethod.Patch,
                buildJsonObject {
                    put("shell", value.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
                },
                query,
            )
            .map {}
    }

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value
}
