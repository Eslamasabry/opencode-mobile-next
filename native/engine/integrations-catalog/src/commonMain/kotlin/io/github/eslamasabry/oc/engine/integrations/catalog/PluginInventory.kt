package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// Flutter lib/domain/plugin_inventory.dart + lib/api2/plugin_mapper.dart + Api2OperationsGateway.listPlugins.
// Safe display metadata only: no plugin options, executable UI, paths, URLs, raw failures or credentials cross here.

public enum class PluginStatus { Active, Failed, Unknown }

public enum class PluginSourceKind { Builtin, Package, Local, Sdk, Unknown }

public data class PluginInfo(
    val id: String?,
    val status: PluginStatus,
    val source: PluginSourceKind,
    val terminalUi: Boolean,
    val packageName: String? = null,
)

private val PLUGIN_ID = Regex("""^@?[A-Za-z0-9][A-Za-z0-9_./@-]*$""")
private val NPM_PACKAGE = Regex("""^(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*(?:@[A-Za-z0-9][A-Za-z0-9._+-]*)?$""")
private const val MAX_ID = 200

/**
 * One inventory row, or null when the row is not valid plugin metadata (the inventory then fails as a whole). The
 * beta reported `status`/`tui` on the row; the stable line nests `state.status` and `features.tui` (absent = false).
 */
public fun mapPluginInfo(value: JsonObject): PluginInfo? {
    val source = value["source"] as? JsonObject ?: return null
    val type = source.string("type") ?: return null
    val state = value["state"] as? JsonObject
    val features = value["features"] as? JsonObject
    val status = value.string("status") ?: state?.string("status") ?: return null
    val tuiElement = value["tui"] ?: features?.let { it["tui"] ?: JsonPrimitive(false) }
    val tui = (tuiElement as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: return null
    val rawId = value.string("id")
    val id = rawId?.takeIf { it.length <= MAX_ID && PLUGIN_ID.matches(it) }
    val pkg = source.string("package")?.takeIf { it.length <= MAX_ID && NPM_PACKAGE.matches(it) }
    val kind =
        when (type) {
            "builtin" -> PluginSourceKind.Builtin
            "package" -> PluginSourceKind.Package
            "local" -> PluginSourceKind.Local
            "sdk" -> PluginSourceKind.Sdk
            else -> PluginSourceKind.Unknown
        }
    return PluginInfo(
        id = id,
        status =
            when (status) {
                "active" -> PluginStatus.Active
                "failed" -> PluginStatus.Failed
                else -> PluginStatus.Unknown
            },
        source = kind,
        packageName = if (kind == PluginSourceKind.Package) pkg else null,
        terminalUi = tui,
    )
}

/**
 * Reads `GET /plugin` pinned to the location captured when the call starts (a later location change cannot move an
 * in-flight read). An invalid inventory is [Failure.UnreadableReply], never an empty success; a server failure keeps
 * its status only. Neither carries server text.
 */
public class PluginInventoryReader(
    private val transport: CatalogTransport,
    private val location: () -> CatalogLocation,
) {
    public suspend fun listPlugins(): Outcome<List<PluginInfo>> {
        val pinned = location()
        val reply =
            when (val sent = transport.send(CatalogRequest("GET", "/plugin", pinned.query()))) {
                is Outcome.Failed -> return sent
                is Outcome.Ok -> sent.value
            }
        if (!reply.isSuccess) return statusFailure(reply.status).failed()
        val rows = ((reply.body as? JsonObject)?.get("data") as? JsonArray) ?: return Failure.UnreadableReply().failed()
        val plugins = ArrayList<PluginInfo>(rows.size)
        for (row in rows) {
            val obj = row as? JsonObject ?: return Failure.UnreadableReply().failed()
            plugins += mapPluginInfo(obj) ?: return Failure.UnreadableReply().failed()
        }
        return plugins.toList().ok()
    }
}

/** A status-only failure for an answered request (no details: server bodies may hold secrets). */
internal fun statusFailure(status: Int): Failure =
    when {
        status >= 500 -> Failure.Server(status)
        status == 404 -> Failure.NotFound()
        else -> Failure.Rejected(status)
    }

/** `plugin.added` / `plugin.updated` only forward an inventory refresh hint; the event's data is dropped. */
public fun pluginEventHint(type: String, @Suppress("UNUSED_PARAMETER") data: JsonElement?): CatalogHint? =
    if (type == "plugin.added" || type == "plugin.updated") CatalogHint(type) else null
