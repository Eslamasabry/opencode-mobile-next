package io.github.eslamasabry.oc.engine.runtime.install

/**
 * Port of lib/termux/bridge_models.dart `TermuxRuntime`: the runtime of the one app-managed Ubuntu server. OpenCode 1
 * is detection only (an old installation must move before it can start); only OpenCode 2 installs, at [pinnedVersion].
 */
public enum class ManagedRuntime(public val wireName: String, public val pinnedVersion: String) {
    OpenCode1("opencode1", ""),
    OpenCode2("opencode2", "2.2.5"),
    ;

    /** Whether this runtime can be installed or started (Flutter `requireSupported`, code `opencode1_migration_required`). */
    public val supported: Boolean get() = this == OpenCode2

    public companion object {
        /** Fresh setup only. Missing runtime records still describe older installs. */
        public val defaultForNewInstall: ManagedRuntime = OpenCode2

        /** Flutter `TermuxRuntime.parse`: a missing or empty record is OpenCode 1; an unknown one is null (`invalid_runtime`). */
        public fun parse(value: String?): ManagedRuntime? =
            when (value?.trim()) {
                null, "", "opencode1" -> OpenCode1
                "opencode2" -> OpenCode2
                else -> null
            }

        /** Flutter `BuiltinLinux.runtimeForProfile` / `ManagedRuntimeFlavor.runtimeOf`: a profile still marked for the move is OpenCode 1. */
        public fun ofProfile(requiresOpenCode2Migration: Boolean): ManagedRuntime =
            if (requiresOpenCode2Migration) OpenCode1 else OpenCode2
    }
}
