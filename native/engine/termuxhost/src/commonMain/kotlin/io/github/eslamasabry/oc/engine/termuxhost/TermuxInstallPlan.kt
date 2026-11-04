package io.github.eslamasabry.oc.engine.termuxhost

/**
 * The decision half of lib/state/termux_host_setup.dart (B-LINUX-230): what a Set up / Switch on the phone's Termux
 * does, from what Termux reported and the saved profiles. A fresh install selects the pinned OpenCode 2; a legacy
 * install moves to OpenCode 2 keeping its profile identity and credentials; OpenCode 1 is never installed; a ready
 * OpenCode 2 with its own history never adopts the legacy profile; an unreadable inventory is never a fresh install.
 */
public enum class TermuxHostJob { Install, SwitchRuntime }

/** A saved profile that points at the app-managed loopback server. */
public data class ManagedProfile(
    val id: String,
    val password: String,
    val requiresOpenCode2Migration: Boolean = false,
    val openCode2HistoryPending: Boolean = false,
) {
    val runtime: TermuxRuntime get() = if (requiresOpenCode2Migration) TermuxRuntime.OpenCode1 else TermuxRuntime.OpenCode2
}

public sealed interface TermuxInstallPlan {
    /** Download and start the pinned OpenCode 2; store a new OpenCode 2 profile. */
    public data class InstallFresh(val version: String) : TermuxInstallPlan

    /** OpenCode 2 is installed and a profile holds its credential: start what is there. */
    public data class StartInstalled(val profileId: String) : TermuxInstallPlan

    /** The safe move: switch the managed server to OpenCode 2, keeping [profile]'s id and credential. */
    public data class MoveToOpenCode2(val profile: ManagedProfile) : TermuxInstallPlan

    /** OpenCode 2 already answers: connect [profile] (the original migration owner wins over an alias). */
    public data class Connect(val profile: ManagedProfile) : TermuxInstallPlan

    /** Nothing is launched and no profile changes. */
    public data class Refused(val reason: Reason) : TermuxInstallPlan

    public enum class Reason {
        /** The inventory could not be read: it is not a fresh install. */
        InventoryUnreadable,

        /** A legacy installation with no recoverable credential: OpenCode 1 is never installed instead. */
        LegacyWithoutCredentials,

        /** OpenCode 2 already has a separate history: the legacy profile is not adopted. */
        SeparateHistory,
    }

    public companion object {
        /** The runtime the manager runs now (TermuxHostSetup.runtime). */
        public fun runtimeOf(
            status: TermuxSetupStatus?,
            installation: TermuxInstallation?,
            profiles: List<ManagedProfile>,
        ): TermuxRuntime =
            when {
                status != null && (status.runtimeSelected || status.version.isNotEmpty()) -> status.runtime
                installation != null && (installation.runtimeSelected || installation.ubuntuInstalled) -> installation.runtime
                profiles.isNotEmpty() -> profiles.first().runtime
                else -> TermuxRuntime.DefaultForNewInstall
            }

        /**
         * [separateOpenCode2History] is the manager's read-only history admission (exit 74: OpenCode 2 already has
         * its own history).
         */
        public fun decide(
            job: TermuxHostJob,
            installation: TermuxInstallation?,
            status: TermuxSetupStatus?,
            profiles: List<ManagedProfile>,
            separateOpenCode2History: Boolean = false,
        ): TermuxInstallPlan {
            installation ?: return Refused(Reason.InventoryUnreadable)
            // The original migration owner (legacy or already moving) is chosen before any OpenCode 2 alias.
            val owner =
                profiles.firstOrNull { it.requiresOpenCode2Migration || it.openCode2HistoryPending } ?: profiles.firstOrNull()
            if (status != null && status.isReady && status.runtime == TermuxRuntime.OpenCode2) {
                owner ?: return InstallFresh(TermuxRuntime.OpenCode2.pinnedVersion)
                if (owner.requiresOpenCode2Migration && separateOpenCode2History) return Refused(Reason.SeparateHistory)
                return Connect(owner.movedToOpenCode2())
            }
            if (runtimeOf(status, installation, profiles) == TermuxRuntime.OpenCode1) {
                val legacy = owner?.takeIf { it.password.isNotEmpty() } ?: return Refused(Reason.LegacyWithoutCredentials)
                if (separateOpenCode2History) return Refused(Reason.SeparateHistory)
                return MoveToOpenCode2(legacy.movedToOpenCode2())
            }
            val installed = installation.openCodeVersion != null
            val profile = profiles.firstOrNull { it.runtime == installation.runtime && it.password.isNotEmpty() }
            return if (job == TermuxHostJob.Install && installed && profile != null) {
                StartInstalled(profile.id)
            } else {
                InstallFresh(TermuxRuntime.OpenCode2.pinnedVersion)
            }
        }

        private fun ManagedProfile.movedToOpenCode2(): ManagedProfile =
            if (requiresOpenCode2Migration || openCode2HistoryPending) {
                copy(requiresOpenCode2Migration = false, openCode2HistoryPending = true)
            } else {
                this
            }
    }
}
