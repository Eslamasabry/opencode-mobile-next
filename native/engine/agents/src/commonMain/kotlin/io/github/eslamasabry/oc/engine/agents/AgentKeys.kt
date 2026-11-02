package io.github.eslamasabry.oc.engine.agents

import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * Every preference and secret name this engine owns (map agents-backends §4, plan 17 §1). Per-profile keys are
 * `oc.<what>.<profileId>` (AGENTS.md rule 9) so the profile sweep finds them; the agents backend's own keys are
 * `oc.<what>.<owner>.agents` and are swept with the owner. Formats are the Flutter ones byte for byte.
 */
public object AgentKeys {
    public const val AGENTS_SUFFIX: String = ".agents"

    public fun chatAgent(profile: ProfileId): String = "oc.chatAgent.${profile.raw}"
    public fun agentModel(profile: ProfileId): String = "oc.agentModel.${profile.raw}"
    public fun modelNames(profile: ProfileId): String = "oc.modelNames.${profile.raw}"
    public fun phoneAgentOwner(profile: ProfileId): String = "oc.phoneAgentOwner.${profile.raw}"
    public const val PHONE_AGENT_OWNER_PREFIX: String = "oc.phoneAgentOwner."
    public fun phoneAgentGateMigration(owner: OwnerId): String = "oc.phoneAgentGateMigration.${owner.raw}"
    public fun agentPhoneGate(owner: OwnerId): String = "oc.agentPhoneGate.${owner.raw}"
    public fun agentInstall(owner: OwnerId): String = "oc.agentInstall.${owner.raw}"
    public fun phoneAgentsUsed(profile: ProfileId): String = "oc.phoneAgentsUsed.${profile.raw}"
    public fun agentFeed(profile: ProfileId): String = "oc.agentFeed.${profile.raw}"
    public fun mainFeed(profile: ProfileId): String = "oc.mainFeed.${profile.raw}"
    public fun sideFeed(serverId: ProfileId): String = "oc.sideFeed.${serverId.raw}"
    public fun agentSignedOut(profile: ProfileId): String = "oc.agentSignedOut.${profile.raw}"
    public fun autoApprove(profile: ProfileId): String = "oc.autoApprove.${profile.raw}"
    public fun agentTurnFailures(profile: ProfileId): String = "oc.agentTurnFailures.${profile.raw}"
    public fun conversationTitles(profile: ProfileId): String = "oc.conversationTitles.${profile.raw}"
    public fun contextUsage(profile: ProfileId): String = "oc.contextUsage.${profile.raw}"
    public fun agentHandoffs(profile: ProfileId): String = "oc.agentHandoffs.${profile.raw}"

    /** Secure storage: the in-app Paseo helper's 64-hex password (05 Appendix B). */
    public fun agentHostSecret(owner: OwnerId): String = "oc.agentHostSecret.${owner.raw}"
    public const val AGENT_HOST_SECRET_PREFIX: String = "oc.agentHostSecret."
}
