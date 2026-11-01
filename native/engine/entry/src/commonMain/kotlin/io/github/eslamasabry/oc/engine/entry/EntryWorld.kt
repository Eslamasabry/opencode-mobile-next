package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.StateFlow

/** ADR §9.7 start-up, as the router sees it. Every request waits for [Ready] AND [EntryWorld.gateOpen] (07 §7.3 rule 3). */
public enum class BootstrapPhase { Starting, Ready, SafeMode, Blocked }

/** One connection's status as far as entry routing cares (07 §7.3 rule 4). */
public enum class LinkState {
    /** Not opened and not on its way (the person disconnected, or it never started). */
    Idle,

    /** On its way and nothing has failed: requests keep waiting. */
    Connecting,

    /** Connected and loaded. */
    Ready,

    /** The saved password or token was refused: re-entry needed. */
    NeedsReentry,
    Failed,
}

/** What `setup.json` says (07 §7.4 `phone_setup`). */
public enum class PhoneSetupStatus { None, Running, Stopped, DoneReadyNotShown, Done }

/** A stored scheduled prompt, as the tap needs it (07 §7.9). */
public data class ScheduleInfo(
    val profileId: ProfileId,
    val projectDir: ProjectDir,
    val morningSummary: Boolean,
)

/** A usage-widget row's provider on one server (B-NAV-077). [isClaudeOnThisPhone] = Claude Code on the phone server. */
public data class UsageProvider(val profileId: ProfileId, val providerId: String, val isClaudeOnThisPhone: Boolean)

/**
 * The read model [EntryDecider] decides over: one immutable snapshot, so the table tests need no engine. The app graph
 * builds it from profiles, connections, the MigrationGate, setup and schedules ([EntryWorldSource]).
 */
public data class EntryWorld(
    val bootstrap: BootstrapPhase = BootstrapPhase.Starting,
    val gateOpen: Boolean = false,
    val activeProfileId: ProfileId? = null,
    val savedProfileIds: Set<ProfileId> = emptySet(),
    val links: Map<ProfileId, LinkState> = emptyMap(),
    val isAndroid: Boolean = true,
    val phoneSetup: PhoneSetupStatus = PhoneSetupStatus.None,
    val schedules: Map<String, ScheduleInfo> = emptyMap(),
    val usageProviders: Set<UsageProvider> = emptySet(),
) {
    public val open: Boolean get() = bootstrap == BootstrapPhase.Ready && gateOpen

    public fun linkOf(profileId: ProfileId): LinkState = links[profileId] ?: LinkState.Idle
}

/** Bound by the app graph over the engines that own each field. */
public interface EntryWorldSource {
    public val world: StateFlow<EntryWorld>
}
