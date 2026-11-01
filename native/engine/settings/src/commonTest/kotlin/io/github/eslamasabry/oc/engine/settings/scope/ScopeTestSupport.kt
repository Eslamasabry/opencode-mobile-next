package io.github.eslamasabry.oc.engine.settings.scope

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.settings.prefs.Preferences
import io.github.eslamasabry.oc.engine.settings.prefs.TestDisk
import io.github.eslamasabry.oc.engine.settings.prefs.storeOver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal val PHONE = ProfileId("phone")
internal val LAPTOP = ProfileId("laptop")
internal val DEVICE = SettingsTarget.Device
internal val PHONE_TARGET = SettingsTarget.Connection(PHONE)
internal val LAPTOP_TARGET = SettingsTarget.Connection(LAPTOP)

/** The saved profiles as `:engine:profiles` would report them: a new fingerprint on every replacement. */
internal class FakeOwners(ids: List<ProfileId>) : SettingsOwners {
    private val fingerprints = LinkedHashMap<ProfileId, String>()
    private var generation = 0
    private val mutableRevision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> get() = mutableRevision

    init {
        ids.forEach { fingerprints[it] = "fp-${it.raw}-0" }
    }

    override fun fingerprint(profileId: ProfileId): String? = fingerprints[profileId]

    /** A transport change or an upsert with a new address. */
    fun replace(profileId: ProfileId) {
        fingerprints[profileId] = "fp-${profileId.raw}-${++generation}"
        mutableRevision.value += 1
    }

    fun remove(profileId: ProfileId) {
        fingerprints.remove(profileId)
        mutableRevision.value += 1
    }
}

/**
 * The read-state consent contract of [ReadStateConsentPort] as Flutter `ReadStateConsentStore` implements it (the real
 * store is SETTINGS-B's): opt-out private at once, opt-in only after the acknowledged write, a late identity change
 * never grants consent.
 */
internal class FakeReadStateConsent : ReadStateConsentPort {
    private val mutableEpoch = MutableStateFlow(0L)
    override val epoch: StateFlow<Long> get() = mutableEpoch

    /** The "disk". */
    val saved = LinkedHashMap<ProfileId, Boolean>()
    private val confirmed = LinkedHashMap<ProfileId, Boolean>()
    private val private = LinkedHashSet<ProfileId>()
    private val closed = LinkedHashSet<ProfileId>()
    private val pending = LinkedHashMap<ProfileId, Int>()
    var gate: CompletableDeferred<Unit>? = null
    var entered: CompletableDeferred<Unit>? = null
    var refuse: Boolean = false

    fun seed(profileId: ProfileId, value: Boolean) {
        saved[profileId] = value
        confirmed[profileId] = value
    }

    override fun enabled(profileId: ProfileId): Boolean =
        profileId !in closed && profileId !in private && (pending[profileId] ?: 0) == 0 && confirmed[profileId] == true

    override fun origin(profileId: ProfileId): SettingOriginKind =
        if (profileId in saved) SettingOriginKind.ExplicitValue else SettingOriginKind.DefaultValue

    override fun isClosed(profileId: ProfileId): Boolean = profileId in closed

    fun closeProfile(profileId: ProfileId) {
        closed += profileId
        private += profileId
        confirmed.remove(profileId)
        mutableEpoch.value += 1
    }

    override suspend fun setEnabledIfCurrent(profileId: ProfileId, value: Boolean, isCurrent: () -> Boolean): Outcome<Unit> {
        if (profileId in closed || !isCurrent()) return SettingsFailureReason.StaleTarget.failed()
        pending[profileId] = (pending[profileId] ?: 0) + 1
        private += profileId // even a failed opt-out stays private; opt-in never authorises early
        mutableEpoch.value += 1
        try {
            entered?.complete(Unit)
            gate?.await()
            if (refuse) return Outcome.Failed(Failure.Storage(StorageProblem.WriteRefused))
            saved[profileId] = value
            if (profileId in closed || !isCurrent()) {
                saved[profileId] = false // erase the authorisation before a restart
                return SettingsFailureReason.StaleTarget.failed()
            }
            confirmed[profileId] = value
            private -= profileId
            return Outcome.Ok(Unit)
        } finally {
            pending[profileId] = (pending[profileId] ?: 1) - 1
            mutableEpoch.value += 1
        }
    }
}

internal class ScopeFixture(scope: TestScope, val store: KeyValueStore = storeOver(TestDisk(), StandardTestDispatcher(scope.testScheduler))) {
    val app = WriterLane("app")
    val consent = FakeReadStateConsent()
    val owners = FakeOwners(listOf(PHONE, LAPTOP))
    val preferences = Preferences(store, app, scope.backgroundScope, consent)
    private val background = scope.backgroundScope

    fun access(preferences: Preferences = this.preferences, store: KeyValueStore = this.store): LocalSettingsAccess =
        LocalSettingsAccess(preferences, owners, consent, store, background)
}

internal fun assertReason(expected: SettingsFailureReason, outcome: Outcome<*>) {
    val failed = assertIs<Outcome.Failed>(outcome, "expected $expected")
    assertEquals(expected, failed.failure.settingsReason())
}

internal fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this).value

internal fun text(value: String): SettingValue = SettingValue.Text(value)

internal fun flag(value: Boolean): SettingValue = SettingValue.Flag(value)
