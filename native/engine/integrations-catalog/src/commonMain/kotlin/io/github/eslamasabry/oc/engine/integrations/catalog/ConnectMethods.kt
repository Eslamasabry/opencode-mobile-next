package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo

// Flutter lib/ui/widgets/connect_methods.dart (connectMethodNeedsServerBrowser, orderConnectMethods,
// connectMethodHint, keyLedConnectMethods) and lib/ui/screens/library/integrations_connect.dart
// (_connectIntegration: which method types are offered). The one place every native page where a person
// connects a provider asks, so they offer the same choices in the same order.

/** Which line sits under a connect choice (the page maps it to words). */
public enum class ConnectHint {
    Key,
    Browser,
    Device,
    Account,
    Command,
}

/**
 * True for OAuth methods that finish through a browser redirect back to the machine running the
 * server: from a phone the redirect lands on the wrong device, so they go last and carry a warning.
 */
public fun connectMethodNeedsServerBrowser(method: IntegrationMethodInfo): Boolean =
    method.type == "oauth" && method.label.lowercase().contains("browser")

/**
 * The choices a person gets for one provider: `key`, `oauth` and (only when the server runs sign-in
 * commands) `command`, never `env`; a method that cannot be started without an id is left out;
 * key-led providers keep only the key; sign-in first, then the key, then browser sign-ins, then the
 * rest. Nothing the server does not offer is invented.
 */
public fun connectMethods(
    integrationId: String,
    methods: List<IntegrationMethodInfo>,
    commandSupported: Boolean,
): List<IntegrationMethodInfo> {
    val usable =
        methods.filter {
            it.type == "key" ||
                (it.type == "oauth" && it.id != null) ||
                (commandSupported && it.type == "command" && it.id != null)
        }
    val keys = usable.filter { it.type == "key" }
    val offered = if (providerKeyPageUrl(integrationId) != null && keys.isNotEmpty()) keys else usable
    return offered.sortedBy { method ->
        when (method.type) {
            "oauth" -> if (connectMethodNeedsServerBrowser(method)) 2 else 0
            "key" -> 1
            else -> 3
        }
    }
}

/** The line that says how a connect choice finishes. */
public fun connectMethodHint(method: IntegrationMethodInfo): ConnectHint =
    when {
        method.type == "key" -> ConnectHint.Key
        method.type == "command" -> ConnectHint.Command
        connectMethodNeedsServerBrowser(method) -> ConnectHint.Browser
        method.label.lowercase().contains("headless") -> ConnectHint.Device
        else -> ConnectHint.Account
    }

/**
 * False when the server gave the method no name of its own (the protocol layer then falls back to the
 * method type, so a key shows up as "key"); the page shows its own words instead.
 */
public fun connectMethodHasOwnLabel(method: IntegrationMethodInfo): Boolean =
    method.label.isNotBlank() && !method.label.equals(method.type, ignoreCase = true)
