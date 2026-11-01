package io.github.eslamasabry.oc.engine.settings.scope

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.settings.prefs.ConsentAuthority
import kotlinx.coroutines.flow.StateFlow

/**
 * The saved connections as the settings facade needs them (implemented by `:engine:profiles`; `:engine:settings`
 * may not depend on it).
 */
public interface SettingsOwners {
    /**
     * An opaque identity of the profile's transport: it changes when the profile is removed, replaced, or its address,
     * backend, user name or folder changes. Null when no such profile is saved. Never contains a password, token,
     * URL credentials or a query.
     */
    public fun fingerprint(profileId: ProfileId): String?

    /** Moves on every change of the saved profiles (add, replace, edit, remove). */
    public val revision: StateFlow<Long>
}

/**
 * Per-connection read-state consent (`oc.shareSessionViews.<id>`), implemented by the consent store of SETTINGS-B
 * (`engine.settings.consent`). The contract the facade relies on (Flutter `ReadStateConsentStore`):
 *
 * - [epoch] (from [ConsentAuthority]) moves synchronously on every change, revocation first, and on [isClosed] flips.
 * - [enabled] is false for a closed, saving, unmigrated or unknown profile, and false at once after an opt-out was
 *   asked for, even before (or without) the storage acknowledgment.
 * - [setEnabledIfCurrent] checks [isCurrent] before writing and again before publishing; a late identity change
 *   returns a `StaleTarget` failure and never grants consent. A refused write returns a storage failure.
 */
public interface ReadStateConsentPort : ConsentAuthority {
    public fun enabled(profileId: ProfileId): Boolean

    public fun origin(profileId: ProfileId): SettingOriginKind

    public fun isClosed(profileId: ProfileId): Boolean

    public suspend fun setEnabledIfCurrent(profileId: ProfileId, value: Boolean, isCurrent: () -> Boolean): Outcome<Unit>
}
