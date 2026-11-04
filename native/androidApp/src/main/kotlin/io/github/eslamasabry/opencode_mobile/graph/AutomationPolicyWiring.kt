package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.engine.requests.consent.*
import io.github.eslamasabry.oc.feature.settings.automation.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * All local consent editors share one durable owner; deletion pauses it without discarding consent.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<AutomationHost>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class AppAutomationPolicies(
    private val stores: PreviewStores,
    private val redactor: Redactor,
    private val scope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) : AutomationHost, ProfileCloser, ProfileResourceCleaner {
    private val blocked = ConcurrentHashMap.newKeySet<ProfileId>()
    private val owners = ConcurrentHashMap<ProfileId, AutomationPolicyOwner>()
    private val mutable = MutableStateFlow(AutomationUi())
    override val state: StateFlow<AutomationUi> = mutable.asStateFlow()
    private var observing = false
    private val store =
        object : AutomationStore {
            override fun read(key: String): String? =
                when (val value = stores.prefs.get(key)) {
                    null -> null
                    is PrefValue.Str -> value.value
                    else -> "{}"
                }

            override suspend fun write(key: String, value: String): Boolean {
                val id = ProfileId(key.removePrefix("oc.automation."))
                if (stores.profiles.profile(id) == null) return false
                return stores.prefs.commit(stores.lanes.forProfile(id)) { putString(key, value) } is
                    Outcome.Ok
            }
        }
    override val owner: String = ":engine:requests (automation policy)"
    override val order: Int = DeletionStep.AFTER_ROW

    private fun valid(id: ProfileId): Boolean =
        Regex("^[A-Za-z0-9_-]{1,128}$").matches(id.raw) && !redactor.containsSecret(id.raw)

    private fun forProfile(id: ProfileId): AutomationPolicyOwner? {
        if (!valid(id) || id in blocked || stores.profiles.profile(id) == null) return null
        val value =
            owners.getOrPut(id) {
                AutomationPolicyOwner(id.raw, store) { redactor.redact(it) != it }
            }
        if (id in blocked) {
            value.stopAdmission()
            return null
        }
        return value
    }

    fun allows(id: ProfileId, behavior: AutomationBehavior): Boolean =
        forProfile(id)?.value?.value?.allows(behavior) == true

    fun hasRecord(id: ProfileId): Boolean =
        valid(id) && stores.prefs.get(AutomationPolicyOwner.keyFor(id.raw)) != null

    suspend fun setBehavior(
        id: ProfileId,
        behavior: AutomationBehavior,
        enabled: Boolean,
    ): Outcome<Unit> =
        withContext(dispatchers.main) {
            val value =
                forProfile(id)
                    ?: return@withContext Outcome.Failed(
                        Failure.Refused(RefusalReason.ProfileDeleted)
                    )
            val result = value.setBehavior(behavior, enabled)
            publish()
            result
        }

    override suspend fun refresh(): Outcome<Unit> =
        withContext(dispatchers.main) {
            val warm = stores.warm()
            if (warm is Outcome.Failed) return@withContext warm
            if (!observing) {
                observing = true
                scope.launch(dispatchers.main) { stores.profiles.profiles.collect { publish() } }
            }
            publish()
            Unit.ok()
        }

    override suspend fun setBehavior(
        profile: String,
        behavior: String,
        enabled: Boolean,
    ): Outcome<Unit> =
        withContext(dispatchers.main) {
            val choice =
                AutomationBehavior.entries.firstOrNull { it.wire == behavior }
                    ?: return@withContext Outcome.Failed(Failure.StorageRefused())
            mutable.value = mutable.value.copy(saving = true, failed = false)
            val result = setBehavior(ProfileId(profile), choice, enabled)
            mutable.value = mutable.value.copy(saving = false, failed = result is Outcome.Failed)
            result
        }

    private fun publish() {
        mutable.value =
            mutable.value.copy(
                servers =
                    stores.profiles.profiles.value
                        .mapNotNull { profile ->
                            val policy =
                                forProfile(profile.id)?.value?.value ?: return@mapNotNull null
                            AutomationServer(
                                profile.id.raw,
                                redactor.redact(profile.profile.name),
                                AutomationBehavior.entries
                                    .map {
                                        AutomationChoice(it.wire, policy.allows(it))
                                    }
                                    .toImmutableList(),
                            )
                        }
                        .toImmutableList()
            )
    }

    override fun stopAdmission(profile: ProfileId) {
        blocked += profile
        owners[profile]?.stopAdmission()
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        owners[profile]?.pauseForDeletion()
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) {
        if (stores.profiles.profile(profile) == null) return
        owners[profile]?.cancelDeletion()
        blocked -= profile
        publish()
    }

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> {
        owners[profileId]?.prepareForDeletion()
        owners.remove(profileId)
        return 0.ok()
    }
}
