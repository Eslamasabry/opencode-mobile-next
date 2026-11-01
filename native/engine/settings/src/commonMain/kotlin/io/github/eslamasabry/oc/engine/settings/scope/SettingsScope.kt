package io.github.eslamasabry.oc.engine.settings.scope

import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.Flow

// The settings scope domain (plan 07 §5 I; Flutter `lib/domain/settings_scope.dart`, frozen contract
// `docs/design/settings-scope-contract-2026-10-12.md`). No platform, storage or protocol types.

public enum class SettingOwnerKind { Device, Connection, Host, ProviderAccount, Project, Chat }

public enum class SettingAuthority { LocalPreference, ServerConfiguration, HostRuntime, ProviderAccount }

public enum class SettingValueState { Known, Unknown, Unsupported, Stale }

public enum class SettingOriginKind { DefaultValue, ExplicitValue, Migrated, Reported }

public enum class SettingsFailureReason {
    Unsupported, InvalidValue, StaleTarget, Conflict, StorageFailed, Disposed, Offline, NeedsSignIn, Managed,
    OutcomeUnknown,
}

/**
 * Where a setting lives. Ids are opaque, non-secret identities; the UI supplies owner names. Non-device ids must be
 * non-empty (Dart asserted it; here it is a programming error).
 */
public sealed interface SettingsTarget {
    public val kind: SettingOwnerKind

    /** `device` for the device; the profile id for a connection; the project / session id below it. */
    public val ownerId: String

    public val profileId: ProfileId? get() = null

    public data object Device : SettingsTarget {
        override val kind: SettingOwnerKind get() = SettingOwnerKind.Device
        override val ownerId: String get() = "device"
    }

    public data class Connection(override val profileId: ProfileId) : SettingsTarget {
        init { require(profileId.raw.isNotEmpty()) { "profile id required" } }

        override val kind: SettingOwnerKind get() = SettingOwnerKind.Connection
        override val ownerId: String get() = profileId.raw
    }

    public data class Host(val hostId: String) : SettingsTarget {
        init { require(hostId.isNotEmpty()) { "host id required" } }

        override val kind: SettingOwnerKind get() = SettingOwnerKind.Host
        override val ownerId: String get() = hostId
    }

    public data class ProviderAccount(val accountId: String) : SettingsTarget {
        init { require(accountId.isNotEmpty()) { "account id required" } }

        override val kind: SettingOwnerKind get() = SettingOwnerKind.ProviderAccount
        override val ownerId: String get() = accountId
    }

    public data class Project(override val profileId: ProfileId, val projectId: String) : SettingsTarget {
        init { require(profileId.raw.isNotEmpty() && projectId.isNotEmpty()) { "profile and project ids required" } }

        override val kind: SettingOwnerKind get() = SettingOwnerKind.Project
        override val ownerId: String get() = projectId
    }

    public data class Chat(override val profileId: ProfileId, val sessionId: String, val projectId: String? = null) :
        SettingsTarget {
        init {
            require(profileId.raw.isNotEmpty() && sessionId.isNotEmpty()) { "profile and session ids required" }
            require(projectId == null || projectId.isNotEmpty()) { "project id must be null or non-empty" }
        }

        override val kind: SettingOwnerKind get() = SettingOwnerKind.Chat
        override val ownerId: String get() = sessionId
    }
}

/** A settings door: its fixed owner set and a semantic label code (an id, never a sentence). */
public data class SettingsRowScope(val rowId: String, val owners: ImmutableSet<SettingOwnerKind>, val labelCode: String)

/** A setting value: immutable by construction, so a published snapshot cannot be changed by its reader. */
public sealed interface SettingValue {
    public data class Text(val value: String) : SettingValue

    public data class Flag(val value: Boolean) : SettingValue

    public data class Record(val fields: ImmutableMap<String, SettingValue>) : SettingValue
}

public data class EffectiveSetting(
    val id: String,
    val target: SettingsTarget,
    val value: SettingValue? = null,
    val state: SettingValueState = SettingValueState.Known,
    val authority: SettingAuthority = SettingAuthority.LocalPreference,
    val origin: SettingOriginKind = SettingOriginKind.DefaultValue,
    val revision: Long = 0,
    val writable: Boolean = false,
    val reason: SettingsFailureReason? = null,
)

/**
 * A snapshot for conflict and identity checks, NOT a credential. Only the issuing facade accepts it, once; switching
 * the main connection never retargets it; replacing the profile or its transport identity invalidates it. It cannot
 * be built outside this module, and a copy made inside it is still refused (tokens are checked by identity).
 */
public class SettingsEditToken internal constructor(
    public val before: EffectiveSetting,
    internal val ownerFingerprint: String,
    internal val issuer: Any,
) {
    override fun toString(): String = "SettingsEditToken(${before.id}, ${before.target.kind})"
}

/**
 * Every Settings row reads and writes through this facade. Reads never fail: an absent, closed or unreadable setting
 * is an [EffectiveSetting] with a non-Known [EffectiveSetting.state] and a [EffectiveSetting.reason].
 */
public interface SettingsAccess {
    public suspend fun read(id: String, target: SettingsTarget): EffectiveSetting

    /** The current value first, then every change; a replaced or closed owner yields a Stale value. */
    public fun watch(id: String, target: SettingsTarget): Flow<EffectiveSetting>

    public suspend fun beginEdit(id: String, target: SettingsTarget): Outcome<SettingsEditToken>

    /** Returns the confirmed setting only after local persistence was acknowledged. */
    public suspend fun write(edit: SettingsEditToken, value: SettingValue?): Outcome<EffectiveSetting>
}

/** The tag a revision conflict carries in [Failure.Net.Conflict] (logic only, never shown). */
public val SETTINGS_REVISION_CONFLICT: WireTag = WireTag("settings-revision")

/** The feature name a settings `Unsupported` carries in [Failure.Unavailable]. */
public val SETTINGS_FEATURE: FeatureId = FeatureId("settings")

/**
 * `:core:common`'s closed [Failure] has no settings leaf, so each reason maps to exactly one existing leaf, and
 * [settingsReason] maps it back (request to the core owner in the S1-ENG report).
 */
public fun SettingsFailureReason.asFailure(): Failure = when (this) {
    SettingsFailureReason.Unsupported -> Failure.Unavailable(SETTINGS_FEATURE)
    SettingsFailureReason.InvalidValue -> Failure.Storage(StorageProblem.InvalidValue)
    SettingsFailureReason.StaleTarget -> Failure.Refused(RefusalReason.StaleScope)
    SettingsFailureReason.Conflict -> Failure.Net.Conflict(tag = SETTINGS_REVISION_CONFLICT)
    SettingsFailureReason.StorageFailed -> Failure.Storage(StorageProblem.WriteRefused)
    SettingsFailureReason.Disposed -> Failure.Unexpected()
    SettingsFailureReason.Offline -> Failure.Net.Offline()
    SettingsFailureReason.NeedsSignIn -> Failure.Net.SignInNeeded(Failure.Net.SignInNeeded.Why.TokenMissing)
    SettingsFailureReason.Managed -> Failure.Refused(RefusalReason.NotOnThisPhone)
    SettingsFailureReason.OutcomeUnknown -> Failure.Net.DeliveryUnknown()
}

/** The settings reason a facade failure stands for; null for a failure no settings facade returns. */
public fun Failure.settingsReason(): SettingsFailureReason? = when (this) {
    is Failure.Unavailable -> SettingsFailureReason.Unsupported
    is Failure.Storage ->
        if (problem == StorageProblem.InvalidValue) SettingsFailureReason.InvalidValue else SettingsFailureReason.StorageFailed
    is Failure.Refused -> when (reason) {
        RefusalReason.StaleScope, RefusalReason.ProfileClosing, RefusalReason.ProfileDeleted -> SettingsFailureReason.StaleTarget
        RefusalReason.NotOnThisPhone -> SettingsFailureReason.Managed
        else -> null
    }
    is Failure.Net.Conflict -> SettingsFailureReason.Conflict
    is Failure.Unexpected -> SettingsFailureReason.Disposed
    is Failure.Net.Offline -> SettingsFailureReason.Offline
    is Failure.Net.SignInNeeded -> SettingsFailureReason.NeedsSignIn
    is Failure.Net.DeliveryUnknown -> SettingsFailureReason.OutcomeUnknown
    else -> null
}

internal fun <T> SettingsFailureReason.failed(): Outcome<T> = Outcome.Failed(asFailure())
