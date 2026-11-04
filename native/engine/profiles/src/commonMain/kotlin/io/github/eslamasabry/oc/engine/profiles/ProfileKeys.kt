package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope

/**
 * The keys and secret names `:engine:profiles` owns (plan 05 Appendix A/B rows `:engine:profiles`).
 */
public object ProfileKeys {
    public const val OWNER: String = ":engine:profiles"

    public const val PROFILES: String = "oc.profiles"
    public const val ACTIVE: String = "oc.activeProfile"
    public const val CLEARTEXT_OK: String = "oc.cleartextOk."
    public const val CERTIFICATE_PIN: String = "oc.certificatePin."
    public const val TAILSCALE: String = "oc.tailscale."
    public const val TRANSACTION: String = "oc.profileTransaction."
    public const val TRANSACTION_SECRET: String = "oc.profileTransactionSecret."
    public const val SECRET_REENTRY: String = "oc.secretReentry."
    public const val PENDING_AUTH: String = "oc.pendingAuth."

    /** The Linux keyring probe name (never on Android). */
    public const val SECURE_PROBE: String = "oc.secure.probe"

    // Secure-storage prefixes (plan 05 Appendix B). Names are ids, never secrets.
    public const val PASSWORD: String = "pw."
    public const val CODEX_TOKEN: String = "oc.codexToken."
    public const val GITHUB_TOKEN: String = "oc.githubToken."
    public const val BYO_HOST_SECRETS: String = "oc.byoHostSecrets."
    public const val AGENT_HOST_SECRET: String = "oc.agentHostSecret."
    public const val QUOTA_TOKEN: String = "oc.quotaToken."
    public const val TEAM_ENGINE_AUTH: String = "oc.teamEngineAuth."
    public const val FACTSBOOK_TOKEN: String = "oc.factsbook."
    public const val FACTSBOOK_OWNER: String = "oc.factsbookOwner."
    public const val FACTSBOOK_OUTBOX: String = "oc.factsbookOutbox."

    /** `oc.orchestration.<id>.grant` (reserved, never written; still swept). */
    public const val ORCHESTRATION_SECRET: String = "oc.orchestration."

    public fun password(id: ProfileId): String = PASSWORD + id.raw

    public fun codexToken(id: ProfileId): String = CODEX_TOKEN + id.raw

    public fun cleartextOk(id: ProfileId): String = CLEARTEXT_OK + id.raw

    public fun certificatePin(id: ProfileId): String = CERTIFICATE_PIN + id.raw

    public fun tailscale(id: ProfileId): String = TAILSCALE + id.raw

    public fun secretReentry(id: ProfileId): String = SECRET_REENTRY + id.raw

    /**
     * Every secure-storage prefix keyed by the profile id, with its owner (plan 05 Appendix B;
     * deferred owners included). `oc.agentHostSecret.<owner>` is NOT here: it is keyed by the
     * phone-agent owner, which other profiles may share, so `:engine:agents` removes it with its
     * own cleaner only when no profile keeps the owner (Flutter
     * `phoneAgentOwnerRetainedAfterRemoving`).
     */
    public val SECRET_PREFIXES: Map<String, String> =
        linkedMapOf(
            // BYO identity first: its refusal keeps the server (Flutter ProfileStore.remove order).
            BYO_HOST_SECRETS to ":engine:integrations",
            TRANSACTION_SECRET to OWNER,
            PASSWORD to OWNER,
            CODEX_TOKEN to OWNER,
            GITHUB_TOKEN to ":engine:project",
            QUOTA_TOKEN to ":engine:integrations",
            TEAM_ENGINE_AUTH to DeferredFeatureData.TEAM,
            FACTSBOOK_TOKEN to DeferredFeatureData.FACTSBOOK,
            FACTSBOOK_OWNER to DeferredFeatureData.FACTSBOOK,
            FACTSBOOK_OUTBOX to DeferredFeatureData.FACTSBOOK,
            ORCHESTRATION_SECRET to DeferredFeatureData.TEAM,
        )

    /** The sign-ins "Reset saved sign-ins" removes (Flutter `resetSavedSignIns.ownsKey`). */
    public val SIGN_IN_PREFIXES: List<String> =
        listOf(
            PASSWORD,
            CODEX_TOKEN,
            TRANSACTION_SECRET,
            TEAM_ENGINE_AUTH,
            QUOTA_TOKEN,
            GITHUB_TOKEN,
            AGENT_HOST_SECRET,
            BYO_HOST_SECRETS,
        )

    /** The key shapes this module owns (plan 05 Appendix A, A.4). */
    public val SHAPES: List<KeyShape> =
        listOf(
            KeyShape(
                PROFILES,
                KeyScope.Other,
                PrefType.Str,
                OWNER,
                "saved server profiles (JSON list)",
            ),
            KeyShape(ACTIVE, KeyScope.App, PrefType.Str, OWNER, "the active profile id"),
            KeyShape(
                CLEARTEXT_OK,
                KeyScope.Profile,
                PrefType.Str,
                OWNER,
                "cleartext consent: the exact origin",
            ),
            KeyShape(
                CERTIFICATE_PIN,
                KeyScope.Profile,
                PrefType.Str,
                OWNER,
                "certificate pin {origin, sha256}",
            ),
            KeyShape(
                TAILSCALE,
                KeyScope.Profile,
                PrefType.Bool,
                OWNER,
                "Tailscale guidance shown for the profile",
            ),
            KeyShape(
                TRANSACTION,
                KeyScope.Profile,
                PrefType.Str,
                OWNER,
                "profile cross-store recovery journal",
            ),
            KeyShape(
                SECRET_REENTRY,
                KeyScope.Profile,
                PrefType.Str,
                OWNER,
                "secrets M2 could not copy (names only)",
            ),
            KeyShape(
                PENDING_AUTH,
                KeyScope.Profile,
                PrefType.Str,
                ":engine:integrations-catalog",
                "provider sign-in recovery metadata only",
            ),
            KeyShape(
                "oc.setupRegistry.",
                KeyScope.Profile,
                PrefType.Str,
                ":engine:setupassist",
                "public MCP registry opt-in and redacted metadata cache",
            ),
        )

    /**
     * Registers [SHAPES] and every per-profile secret prefix this module deletes with the profile.
     */
    public fun register(scope: ProfileScope) {
        SHAPES.forEach(scope::register)
        SECRET_PREFIXES.forEach { (prefix, owner) -> scope.registerSecret(prefix, owner) }
    }
}
