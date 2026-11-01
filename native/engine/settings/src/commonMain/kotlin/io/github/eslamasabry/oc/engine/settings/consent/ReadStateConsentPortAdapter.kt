package io.github.eslamasabry.oc.engine.settings.consent

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.settings.scope.ReadStateConsentPort
import io.github.eslamasabry.oc.engine.settings.scope.SettingOriginKind
import kotlinx.coroutines.flow.StateFlow

/**
 * Binds the read-state consent store to the settings facade's port (SETTINGS-A `scope`): [epoch] is the store's
 * [ReadStateConsentStore.revision]; a stale editor fails with `Failure.Refused(StaleScope)` (= `StaleTarget`).
 */
public fun ReadStateConsentStore.asSettingsPort(): ReadStateConsentPort = ReadStateConsentPortAdapter(this)

private class ReadStateConsentPortAdapter(private val store: ReadStateConsentStore) : ReadStateConsentPort {
    override val epoch: StateFlow<Long> get() = store.revision

    override fun enabled(profileId: ProfileId): Boolean = store.enabled(profileId)

    override fun origin(profileId: ProfileId): SettingOriginKind = when (store.origin(profileId)) {
        ConsentOrigin.Default -> SettingOriginKind.DefaultValue
        ConsentOrigin.Explicit -> SettingOriginKind.ExplicitValue
        ConsentOrigin.Migrated -> SettingOriginKind.Migrated
    }

    override fun isClosed(profileId: ProfileId): Boolean = store.isClosed(profileId)

    override suspend fun setEnabledIfCurrent(profileId: ProfileId, value: Boolean, isCurrent: () -> Boolean): Outcome<Unit> =
        store.setEnabledIfCurrent(profileId, value, isCurrent)
}
