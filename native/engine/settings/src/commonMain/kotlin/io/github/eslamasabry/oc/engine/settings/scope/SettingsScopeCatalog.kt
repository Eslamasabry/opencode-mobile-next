package io.github.eslamasabry.oc.engine.settings.scope

import io.github.eslamasabry.oc.engine.settings.scope.SettingOwnerKind as K
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap

/**
 * Every settings door of the freeze tip with its fixed owner set, copied from Flutter `SettingsScopeCatalog`
 * (`lib/domain/settings_scope.dart`, 67 rows, same order). Ownership describes a destination, not permission to write:
 * an owner listed here grants no write (B-STATE-127). Aliases share metadata and never create duplicate preferences.
 */
public object SettingsScopeCatalog {
    private val DEVICE = owners(K.Device)
    private val CONNECTION = owners(K.Connection)
    private val HOST = owners(K.Host)
    private val REGISTRY = owners(K.Device, K.Connection)
    private val ACCOUNT = owners(K.Connection, K.ProviderAccount)
    private val PROJECT = owners(K.Connection, K.Project)
    private val RULES = owners(K.Connection, K.Project, K.Chat)
    private val TEAM = owners(K.Connection, K.Host, K.Project)
    private val DEVICE_HOST = owners(K.Device, K.Host)

    public val rows: ImmutableList<SettingsRowScope> = persistentListOf(
        row("settings-saved-servers", REGISTRY, "connection"),
        row("settings-tailscale", REGISTRY, "connection"),
        row("settings-add-server", REGISTRY, "connection"),
        row("inside-server-disconnect", REGISTRY, "connection"),
        row("settings-category-server", CONNECTION, "serverReported"),
        row("settings-server-capabilities", CONNECTION, "serverReported"),
        row("settings-server-plugins", CONNECTION, "serverReported"),
        row("inside-server-ai-setup", CONNECTION, "serverReported"),
        row("settings-this-phone", HOST, "host"),
        row("settings-on-this-phone", HOST, "host"),
        row("inside-phone-running-now", HOST, "host"),
        row("inside-phone-storage", HOST, "host"),
        row("inside-phone-crash-recovery", HOST, "host"),
        row("inside-phone-claude-code", HOST, "host"),
        row("inside-phone-claude-code-unavailable", HOST, "host"),
        row("settings-agents", TEAM, "byOwner"),
        row("settings-ai-team", TEAM, "byOwner"),
        row("settings-model-and-mode", CONNECTION, "newChats"),
        row("settings-models", CONNECTION, "newChats"),
        row("settings-providers", ACCOUNT, "serverAccount"),
        row("settings-accounts", ACCOUNT, "serverAccount"),
        row("settings-mcp", RULES, "nativeConfiguration"),
        row("settings-show-reasoning", DEVICE, "device"),
        row("settings-show-timestamps", DEVICE, "device"),
        row("settings-voice", DEVICE, "device"),
        row("settings-category-appearance", DEVICE, "device"),
        row("settings-updates", DEVICE, "device"),
        row("settings-setup-guide", DEVICE, "device"),
        row("settings-tips", DEVICE, "device"),
        row("settings-browser-mode", DEVICE, "device"),
        row("library-report-bug", DEVICE, "device"),
        row("settings-about-notices", DEVICE, "device"),
        row("settings-external-agents", DEVICE, "device"),
        row("settings-privacy-data-use", DEVICE, "device"),
        row("settings-try-demo", DEVICE, "device"),
        row("library-keyboard-shortcuts", DEVICE, "device"),
        row("settings-show-tips-again", DEVICE, "device"),
        row("settings-voice-notices", DEVICE, "device"),
        row("inside-notifications-what", DEVICE, "device"),
        row("inside-notifications-quiet", DEVICE, "device"),
        row("inside-notifications-background", DEVICE, "device"),
        row("inside-appearance-mode", DEVICE, "device"),
        row("inside-appearance-language", DEVICE, "device"),
        row("inside-appearance-theme", DEVICE, "device"),
        row("inside-appearance-text", DEVICE, "device"),
        row("inside-appearance-live-background", DEVICE, "device"),
        row("inside-appearance-motion", DEVICE, "device"),
        row("inside-appearance-glow", DEVICE, "device"),
        row("settings-keep-running", DEVICE_HOST, "deviceAndHost"),
        row("inside-keep-running-battery", DEVICE_HOST, "deviceAndHost"),
        row("inside-keep-running-thermal", DEVICE_HOST, "deviceAndHost"),
        row("inside-notifications-servers", CONNECTION, "connectionLocal"),
        row("inside-usage-spent", PROJECT, "measurement"),
        row("inside-usage-budgets", PROJECT, "measurement"),
        row("inside-usage-remaining", ACCOUNT, "accountSource"),
        row("inside-usage-alerts", ACCOUNT, "accountSource"),
        row("settings-commands-tools", PROJECT, "serverReported"),
        row("inside-capabilities-commands", PROJECT, "serverReported"),
        row("inside-capabilities-tools", PROJECT, "serverReported"),
        row("inside-capabilities-skills", PROJECT, "serverReported"),
        row("inside-capabilities-references", PROJECT, "serverReported"),
        row("settings-automation", RULES, "byOwner"),
        row("saved-permissions-entry", RULES, "byOwner"),
        row("default-shell-settings-entry", CONNECTION, "nativeConfiguration"),
        row("settings-tools", owners(K.Device, K.Connection, K.Project, K.Chat), "byOwner"),
        row("settings-phrase-chips", REGISTRY, "byOwner"),
        row("settings-category-background", owners(K.Device, K.Connection, K.Host), "byOwner"),
        row("settings-category-privacy", REGISTRY, "byOwner"),
        row("settings-category-usage", owners(K.Connection, K.Project, K.ProviderAccount), "byOwner"),
    )

    private val byId: ImmutableMap<String, SettingsRowScope> = rows.associateBy { it.rowId }.toImmutableMap()

    public fun forRow(rowId: String): SettingsRowScope? = byId[rowId]

    private fun owners(vararg kinds: K): ImmutableSet<K> = persistentSetOf(*kinds)

    private fun row(rowId: String, owners: ImmutableSet<K>, labelCode: String) =
        SettingsRowScope(rowId, owners, labelCode)
}
