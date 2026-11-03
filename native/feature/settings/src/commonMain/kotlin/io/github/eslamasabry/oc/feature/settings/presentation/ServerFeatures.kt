package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Where a feature's answer comes from: the heading an absent one is listed under (Flutter `ServerFeatureSource`). */
public enum class FeatureSource { Server, Device }

/** What [ServerFeature.available] reads (Flutter `ServerFeatureFacts`). */
public data class FeatureFacts(
    val server: ServerCapabilities,
    val device: PlatformCapabilities,
    val usageStatistics: Boolean,
)

/**
 * Every feature the app hides when the server or the device cannot do it, in the order a person meets them: project
 * tools, then what a conversation can do, then setup, then what the device decides (Flutter `serverFeatures`). [id] is
 * the stable feature id (row tags `capability-<state>-<id>`). No backend name appears in its words (B-L10N-018).
 */
public enum class ServerFeature(
    public val id: String,
    public val source: FeatureSource = FeatureSource.Server,
    private val gate: (FeatureFacts) -> Boolean,
) {
    Files("files", gate = { it.server.fileBrowsing }),

    /** Either door is enough: a conversation's own diff, or the project's working tree through the file API. */
    Changes("changes", gate = { it.server.sessionDiff || it.server.fileBrowsing }),
    Terminal("terminal", gate = { it.server.terminal }),
    Shell("shell", gate = { it.server.shellSettings }),
    DevServices("dev-services", gate = { it.server.developmentServices }),
    Projects("projects", gate = { it.server.projectManagement }),
    Worktrees("worktrees", gate = { it.server.projectManagement || it.server.worktreeBrowsing }),
    CloudEnvironments("cloud-environments", gate = { it.server.managedWorkspaces }),
    Attachments("attachments", gate = { it.server.promptAttachments }),
    WebSearch("web-search", gate = { it.server.webSearch }),
    Subagents("subagents", gate = { it.server.promptAgentMentions }),
    Compact("compact", gate = { it.server.sessionCompact }),
    Share("share", gate = { it.server.sessionShare }),
    Fork("fork", gate = { it.server.sessionFork }),
    Revert("revert", gate = { it.server.sessionRevert }),
    Archive("archive", gate = { it.server.sessionArchive }),
    DeleteMessage("delete-message", gate = { it.server.messageDelete }),
    Todos("todos", gate = { it.server.sessionTodos }),
    Notes("notes", gate = { it.server.sessionNotes }),
    ImportExport("import-export", gate = { it.server.sessionImportExport }),
    AllConversations("all-conversations", gate = { it.server.globalSessionSearch }),
    AlwaysAllow("always-allow", gate = { it.server.persistentPermissionGrants }),
    SavedPermissions("saved-permissions", gate = { it.server.savedPermissionList }),
    Approvals("approvals", gate = { it.server.permissionRequests }),
    SendLater("send-later", gate = { it.server.offlinePromptQueue }),
    SendWhileWorking("send-while-working", gate = { it.server.inbox }),
    ContinueOnComputer("continue-on-computer", gate = { it.server.cliSessionResume }),
    Models("models", gate = { it.server.serverCatalog }),
    Skills("skills", gate = { it.server.serverCatalog }),
    Mcp("mcp", gate = { it.server.serverCatalog }),
    McpAdd("mcp-add", gate = { it.server.mcpConfigWrites || it.server.mcpRuntimeAdds }),
    Plugins("plugins", gate = { it.server.pluginInventory }),
    Usage("usage", gate = { it.usageStatistics }),
    ServerUpdates("server-updates", gate = { it.server.remoteUpgrade }),

    BackgroundNotifications(
        "background-notifications",
        FeatureSource.Device,
        { it.device.notifications && it.device.backgroundService },
    ),
    OnThisPhone("on-this-phone", FeatureSource.Device, { it.device.builtinLinux }),
    Voice("voice", FeatureSource.Device, { it.device.voice }),
    ;

    public fun available(facts: FeatureFacts): Boolean = gate(facts)
}

/** One listed feature: present ones say "Works here"; missing ones say where the gap is. */
public data class FeatureRowUi(val feature: ServerFeature, val present: Boolean) {
    /** The row tag's state slug (Flutter `capability-<slug>-<id>`). */
    val slug: String
        get() =
            when {
                present -> "available"
                feature.source == FeatureSource.Device -> "device"
                else -> "unavailable"
            }
}

/**
 * Available on this server, ordered by urgency (Flutter `ServerCapabilitiesScreen`): the focused feature first, then
 * what this server lacks, then what this device lacks, then the one row that unfolds everything that works here.
 */
public data class CapabilitiesUi(
    /** The server's own name: the only proper noun on the page. Null: no name known (the words say "this server"). */
    val serverName: String?,
    val focused: ImmutableList<FeatureRowUi>,
    val missingOnServer: ImmutableList<FeatureRowUi>,
    val missingOnDevice: ImmutableList<FeatureRowUi>,
    val available: ImmutableList<FeatureRowUi>,
    /** Something this server cannot do is listed: the intro says where missing features work. */
    val serverGaps: Boolean,
    /** Nothing is missing on the server (and the focused feature, if any, is here): the one "everything" notice. */
    val allAvailable: Boolean,
    /** "Add a server that has these" leads to adding a server; only when there are server gaps and the app can open it. */
    val canAddServer: Boolean,
) {
    public companion object {
        public fun of(facts: SettingsFacts, focus: ServerFeature?): CapabilitiesUi {
            val f = FeatureFacts(facts.caps, facts.platform, facts.usageStatistics)
            fun row(feature: ServerFeature) = FeatureRowUi(feature, feature.available(f))
            val others = ServerFeature.entries.filter { it != focus }.map(::row)
            val focused = listOfNotNull(focus?.let(::row))
            val onServer = others.filter { !it.present && it.feature.source == FeatureSource.Server }
            val onDevice = others.filter { !it.present && it.feature.source == FeatureSource.Device }
            val gaps = onServer.isNotEmpty() || focused.any { !it.present && it.feature.source == FeatureSource.Server }
            return CapabilitiesUi(
                serverName = facts.server?.name,
                focused = focused.toImmutableList(),
                missingOnServer = onServer.toImmutableList(),
                missingOnDevice = onDevice.toImmutableList(),
                available = others.filter { it.present }.toImmutableList(),
                serverGaps = gaps,
                allAvailable = onServer.isEmpty() && focused.all { it.present },
                canAddServer = gaps && facts.canOpen(SettingsOutside.Servers),
            )
        }
    }
}
