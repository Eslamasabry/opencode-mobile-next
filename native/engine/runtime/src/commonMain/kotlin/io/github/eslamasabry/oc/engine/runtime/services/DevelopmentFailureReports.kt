package io.github.eslamasabry.oc.engine.runtime.services

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ManagedShell as ServerShell
import io.github.eslamasabry.oc.core.model.ManagedShellOutput as ServerOutput
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

/**
 * Captures only a persisted, exact owned run. Never starts, stops or adopts a discovered command.
 */
public object DevelopmentFailureReports {
    public suspend fun capture(
        saved: String?,
        current: () -> Boolean,
        inspect:
            suspend (id: String, directory: String, workspace: String?) -> Outcome<ServerShell?>?,
        output:
            suspend (id: String, directory: String, workspace: String?) -> Outcome<ServerOutput>?,
        redact: (String) -> String,
    ): List<FailedServiceReport> {
        val services = decode(saved) ?: return emptyList()
        val reports = ArrayList<FailedServiceReport>()
        for (service in services) {
            val receipt = service.run ?: continue
            val id = receipt.shellId ?: continue
            if (receipt.stopped || receipt.startedAt == null || !current()) continue
            val observed =
                (inspect(id, service.directory, service.workspace) as? Outcome.Ok)?.value
                    ?: continue
            if (!current()) return emptyList()
            val shell =
                ManagedShell(
                    observed.id,
                    observed.command,
                    ManagedShellStatus.entries.first { it.name == observed.status.name },
                    observed.startedAtMs,
                    observed.exitCode,
                    observed.directory,
                    observed.ownerToken,
                )
            // Validate every ownership field and the terminal failure before requesting any output.
            if (FailedServiceReport.of(service, shell, "", redact) == null) continue
            val read = output(id, service.directory, service.workspace)
            if (!current()) return emptyList()
            val text = (read as? Outcome.Ok)?.value?.text.orEmpty()
            FailedServiceReport.of(service, shell, text, redact)?.let(reports::add)
        }
        return if (current()) reports else emptyList()
    }

    /** Flutter's persisted version 1 format; malformed ownership never authorizes a read. */
    private fun decode(raw: String?): List<DevelopmentService>? {
        if (raw == null) return emptyList()
        if (raw.length > 256 * 1024) return null
        val root =
            try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null
        if (root.number("version") != 1L) return null
        val items = root["services"] as? JsonArray ?: return null
        if (items.size > DevelopmentServiceStore.MAX_SERVICES) return null
        val result = ArrayList<DevelopmentService>()
        for (element in items) {
            val row = element as? JsonObject ?: return null
            fun required(key: String, max: Int): String? =
                row.text(key)?.takeIf { it.isNotBlank() && it.length <= max }
            val run =
                when (val value = row["run"]) {
                    null,
                    JsonNull -> null
                    is JsonObject ->
                        DevelopmentServiceRun(
                            value.text("owner")?.takeIf { it.isNotEmpty() && it.length <= 100 }
                                ?: return null,
                            value.text("shellID"),
                            value.number("startedAt"),
                            (value["stopped"] as? JsonPrimitive)?.booleanOrNull == true,
                        )
                    else -> return null
                }
            result +=
                DevelopmentService(
                    required("id", 100) ?: return null,
                    required("name", 80) ?: return null,
                    required("command", 4096) ?: return null,
                    required("directory", 4096) ?: return null,
                    row.text("url").orEmpty(),
                    row.text("workspace"),
                    run,
                )
        }
        return result.takeIf { it.map(DevelopmentService::id).toSet().size == it.size }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
}
