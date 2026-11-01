package io.github.eslamasabry.oc.feature.servers.presentation

/** One way the server offers to connect a provider (Dart `IntegrationMethodInfo`, the parts ordering needs). */
public data class ConnectMethod(val type: String, val id: String?, val label: String)

/** The one line under a connect option that says how it finishes; words are `e7Setup*Hint` in `:ui:words`. */
public enum class ConnectMethodHint { PasteApiKey, PasteCallbackUrl, WorksFromPhone, Account }

/** Ports `lib/ui/widgets/connect_methods.dart` (B-NAV-006). */
public object ConnectMethods {
    /** OAuth that finishes by a browser redirect to the server's machine: from a phone it lands on the wrong device. */
    public fun needsServerBrowser(method: ConnectMethod): Boolean =
        method.type == "oauth" && method.label.lowercase().contains("browser")

    /** Headless and device-code first, keys next, browser-redirect last; stable otherwise. */
    public fun order(methods: List<ConnectMethod>): List<ConnectMethod> =
        methods.sortedBy { m ->
            when (m.type) {
                "oauth" -> if (needsServerBrowser(m)) 2 else 0
                "key" -> 1
                else -> 3
            }
        }

    public fun hint(method: ConnectMethod): ConnectMethodHint =
        when {
            method.type == "key" -> ConnectMethodHint.PasteApiKey
            needsServerBrowser(method) -> ConnectMethodHint.PasteCallbackUrl
            method.label.lowercase().contains("headless") -> ConnectMethodHint.WorksFromPhone
            else -> ConnectMethodHint.Account
        }
}
