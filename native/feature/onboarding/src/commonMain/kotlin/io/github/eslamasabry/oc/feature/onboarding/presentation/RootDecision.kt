package io.github.eslamasabry.oc.feature.onboarding.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId

/** What the root decision reads (07 §2.6; Flutter `_Root.build`, `root.dart:443`). One immutable snapshot. */
public data class RootWorld(
    /** Null after Leave or Disconnect too: the saved list is kept, the active profile is cleared. */
    val activeProfileId: ProfileId? = null,
    val savedProfileIds: Set<ProfileId> = emptySet(),
    /** The active in-app profile needs the OpenCode 1 → 2 move. */
    val needsManagedMove: Boolean = false,
    /** The active profile's password, Codex token or certificate must be entered again. */
    val needsReentry: Boolean = false,
)

/** Which root the first stack (and every root-kind change) gets. */
public sealed interface RootChoice {
    /** `ServersRoute.Servers`: the welcome when nothing is saved, otherwise the saved list. */
    public data class Servers(val welcome: Boolean) : RootChoice

    /** `OnboardingRoute.ManagedMove`. */
    public data object ManagedMove : RootChoice

    /** `ServersRoute.Servers` + `ServersArrival(Edit(profileId, focusPassword = true))`. */
    public data class Reentry(val profileId: ProfileId) : RootChoice

    /** `ChatsRoute.Home`: connected, connecting, starting or not answering (B-NAV-024). */
    public data object Conversations : RootChoice
}

/** Pure; the rows are checked in the 07 §2.6 order. Row 1 is "no **active** profile", never "nothing saved". */
public object RootDecision {
    public fun decide(world: RootWorld): RootChoice {
        val active = world.activeProfileId ?: return RootChoice.Servers(welcome = world.savedProfileIds.isEmpty())
        return when {
            world.needsManagedMove -> RootChoice.ManagedMove
            world.needsReentry -> RootChoice.Reentry(active)
            else -> RootChoice.Conversations
        }
    }

    /** The shell's RootViewModel replaces the root only when its kind changes (07 §2.6 "Later changes"). */
    public fun sameKind(a: RootChoice, b: RootChoice): Boolean =
        when (a) {
            is RootChoice.Servers -> b is RootChoice.Servers
            is RootChoice.Reentry -> b is RootChoice.Reentry && a.profileId == b.profileId
            else -> a == b
        }
}
