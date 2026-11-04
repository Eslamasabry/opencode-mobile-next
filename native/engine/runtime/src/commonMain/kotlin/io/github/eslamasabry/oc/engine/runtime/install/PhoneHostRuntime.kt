package io.github.eslamasabry.oc.engine.runtime.install

/**
 * Port of lib/state/phone_host.dart `InAppPhoneHost.runtime` / `TermuxPhoneHost.runtime` and lib/termux/bridge_models.dart
 * `TermuxInstallation.parse`: which OpenCode runtime the phone host is on. A saved profile wins; a host that was
 * installed before runtime selection existed is legacy (OpenCode 1); only a fresh host adopts OpenCode 2.
 */
public object PhoneHostRuntime {
    /** A saved phone-server profile, as far as runtime choice reads it. */
    public data class SavedProfile(val requiresOpenCode2Migration: Boolean)

    /** The in-app host: the saved profile's runtime, else installed-before-selection = OpenCode 1, else the new default. */
    public fun inApp(profile: SavedProfile?, ubuntuInstalled: Boolean?): ManagedRuntime =
        when {
            profile != null -> ManagedRuntime.ofProfile(profile.requiresOpenCode2Migration)
            ubuntuInstalled == true -> ManagedRuntime.OpenCode1
            else -> ManagedRuntime.defaultForNewInstall
        }

    /** The Termux manager's status, as far as runtime choice reads it. */
    public data class TermuxStatus(val runtime: ManagedRuntime, val runtimeSelected: Boolean, val version: String)

    /** Flutter `TermuxInstallation`: the inventory probe's answer. */
    public data class TermuxInstallation(
        val ubuntuInstalled: Boolean,
        val openCodeVersion: String?,
        val runtime: ManagedRuntime,
        val runtimeSelected: Boolean,
    ) {
        public companion object {
            private val shape =
                Regex("^ubuntu=(absent|installed)\\r?\\nversion=([^\\r\\n]*)(?:\\r?\\nruntime=(opencode1|opencode2))?\\r?\\n?$")
            private val versionShape = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$")

            /** Parses `ubuntu=…\nversion=…[\nruntime=…]`; null for any other shape (`invalid_installation_probe`). */
            public fun parse(output: String): TermuxInstallation? {
                val match = shape.find(output.trim()) ?: return null
                val installed = match.groupValues[1] == "installed"
                val version = match.groupValues[2]
                if (version.isNotEmpty() && (!installed || !versionShape.matches(version))) return null
                val selected = match.groups[3]?.value
                return TermuxInstallation(
                    ubuntuInstalled = installed,
                    openCodeVersion = version.ifEmpty { null },
                    runtime = ManagedRuntime.parse(selected) ?: return null,
                    runtimeSelected = selected != null,
                )
            }
        }
    }

    /**
     * The Termux host: a selected or versioned manager status wins, then a selected or installed inventory (an
     * unselected installed Ubuntu reads as OpenCode 1), then the saved profile, then the fresh-install default.
     */
    public fun termux(status: TermuxStatus?, installation: TermuxInstallation?, profile: SavedProfile?): ManagedRuntime =
        when {
            status != null && (status.runtimeSelected || status.version.isNotEmpty()) -> status.runtime
            installation != null && (installation.runtimeSelected || installation.ubuntuInstalled) -> installation.runtime
            profile != null -> ManagedRuntime.ofProfile(profile.requiresOpenCode2Migration)
            else -> ManagedRuntime.defaultForNewInstall
        }
}
