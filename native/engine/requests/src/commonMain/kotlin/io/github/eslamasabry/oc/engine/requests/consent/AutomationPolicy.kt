package io.github.eslamasabry.oc.engine.requests.consent

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.cancellation.CancellationException

/** Local supervision consent, distinct from a host's observed policy (Flutter `AutomationSupervision`). */
public enum class AutomationSupervision(public val wire: String) { High("high"), Balanced("balanced"), Autonomous("autonomous") }

/**
 * Existing behaviours grouped by the automation contract (Flutter `AutomationBehavior`, wire names unchanged). A choice
 * permits an existing executor; it never starts one. The last six need consent in flow and are off on a fresh profile.
 */
public enum class AutomationBehavior(public val wire: String, public val requiresConsent: Boolean = false) {
    Reconnect("reconnect"), RetryIntake("retryIntake"), ReconcileQueuedSends("reconcileQueuedSends"),
    RetryProviderErrors("retryProviderErrors"), RestartPhoneServer("restartPhoneServer"), ResumeSetup("resumeSetup"),
    RecheckEnvironment("recheckEnvironment"), PollRestartHealth("pollRestartHealth"), ThermalRecovery("thermalRecovery"),
    ReduceEffects("reduceEffects"), ChooseDefaults("chooseDefaults"), DetectInstallations("detectInstallations"),
    PreserveDrafts("preserveDrafts"), FollowOutput("followOutput"), PollOpenPages("pollOpenPages"),
    BoundedPaging("boundedPaging"), RouteReadyWork("routeReadyWork"), WakeStalledPool("wakeStalledPool"),
    RestartWorkers("restartWorkers"), RecycleWorkers("recycleWorkers"), HoldAtProviderLimit("holdAtProviderLimit"),
    RetryUnconfirmedAnswer("retryUnconfirmedAnswer"), RetryTransientTask("retryTransientTask"),
    FallBackToDirectTask("fallBackToDirectTask"), ApplyCodePush("applyCodePush"),
    RestartDevServices("restartDevServices", true), StopIdleHelpers("stopIdleHelpers", true),
    CleanCaches("cleanCaches", true), UpdateWhenIdle("updateWhenIdle", true),
    MonitorOtherServers("monitorOtherServers", true), MonitorQuota("monitorQuota", true),
}

/** Immutable local preferences (Flutter `AutomationPolicy`); never a grant for irreversible or public actions. */
public class AutomationPolicy(
    public val supervision: AutomationSupervision = AutomationSupervision.High,
    public val autoApprove: Boolean = false,
    public val autoMergeOnGreen: Boolean = false,
    behaviors: Map<AutomationBehavior, Boolean> = emptyMap(),
) {
    /** Every behaviour, read-only; a missing entry takes its consent default. */
    public val behaviors: Map<AutomationBehavior, Boolean> =
        ReadOnlyBehaviors(AutomationBehavior.entries.associateWith { behaviors[it] ?: !it.requiresConsent })

    /** A view no caller can cast back to a mutable map (Flutter's `Map.unmodifiable`). */
    private class ReadOnlyBehaviors(map: Map<AutomationBehavior, Boolean>) : Map<AutomationBehavior, Boolean> by map

    public fun allows(behavior: AutomationBehavior): Boolean = behaviors.getValue(behavior)

    public val allowsAutoApproval: Boolean get() = supervision != AutomationSupervision.High && autoApprove

    public val allowsAutoMerge: Boolean get() = supervision != AutomationSupervision.High && autoMergeOnGreen

    public fun withBehavior(behavior: AutomationBehavior, enabled: Boolean): AutomationPolicy =
        AutomationPolicy(supervision, autoApprove, autoMergeOnGreen, behaviors + (behavior to enabled))

    /** Explicit in-flow consent: Balanced/Autonomous consent to automatic answers and merge-on-green. */
    public fun withSupervision(value: AutomationSupervision): AutomationPolicy {
        val on = value != AutomationSupervision.High
        return AutomationPolicy(value, on, on, behaviors)
    }

    public fun withApprovals(autoApprove: Boolean? = null, autoMergeOnGreen: Boolean? = null): AutomationPolicy =
        AutomationPolicy(supervision, autoApprove ?: this.autoApprove, autoMergeOnGreen ?: this.autoMergeOnGreen, behaviors)

    public fun toJson(): JsonObject =
        buildJsonObject {
            put("version", JsonPrimitive(1))
            put("supervision", JsonPrimitive(supervision.wire))
            put("autoApprove", JsonPrimitive(autoApprove))
            put("autoMergeOnGreen", JsonPrimitive(autoMergeOnGreen))
            put("behaviors", JsonObject(behaviors.entries.associate { it.key.wire to JsonPrimitive(it.value) }))
        }

    override fun equals(other: Any?): Boolean = other is AutomationPolicy && toJson() == other.toJson()

    override fun hashCode(): Int = toJson().hashCode()

    public companion object {
        /** Corrupt or unsupported storage cannot grant any automation. */
        public fun disabled(): AutomationPolicy = AutomationPolicy(behaviors = AutomationBehavior.entries.associateWith { false })

        /** Missing entries of an existing record stay off, so a future behaviour never opts a server in. */
        public fun fromJson(raw: String): AutomationPolicy {
            val value = try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } ?: return disabled()
            val version = (value["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.content
            if (version != "1") return disabled()
            val supervision = AutomationSupervision.entries.firstOrNull { it.wire == value.string("supervision") }
            val behaviors = value["behaviors"] as? JsonObject
            if (supervision == null || behaviors == null) return disabled()
            return AutomationPolicy(
                supervision,
                value.isTrue("autoApprove"),
                value.isTrue("autoMergeOnGreen"),
                AutomationBehavior.entries.associateWith { behaviors.isTrue(it.wire) },
            )
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.isTrue(key: String): Boolean = (this[key] as? JsonPrimitive)?.let { !it.isString && it.content == "true" } == true
    }
}

/** Where the policy lives: the preferences key `oc.automation.<profileId>`. */
public interface AutomationStore {
    public fun read(key: String): String?

    /** True when durable; false (or a throw) when refused. Nothing visible changes on a refusal. */
    public suspend fun write(key: String, value: String): Boolean
}

/**
 * One owner per profile, shared by Settings and every executor (Flutter `AutomationPolicyController`; B-SEC-017,
 * B-SEC-018). Constructing or reading never writes. Edits run in one lane, compose, and publish to [value] only after a
 * durable save; a refused or throwing write keeps the truth and a later edit may retry. The payload passes the
 * redaction boundary ([protected]) and is refused rather than altered. [pauseForDeletion] drains admitted edits while
 * keeping the owner; [prepareForDeletion] closes it for good and drains before the profile sweep removes the key.
 */
public class AutomationPolicyOwner(
    public val profileId: String,
    private val store: AutomationStore,
    private val protected: (String) -> Boolean,
) {
    init {
        require(profileId.isNotEmpty() && !protected(profileId)) { "A non-secret server profile ID is required" }
    }

    public companion object {
        public fun keyFor(profileId: String): String = "oc.automation.$profileId"
    }

    private val lane = Mutex()
    private val mutable = MutableStateFlow(read())
    public val value: StateFlow<AutomationPolicy> = mutable.asStateFlow()
    private var closed = false
    private var paused = false

    private fun read(): AutomationPolicy =
        try {
            store.read(keyFor(profileId))?.let(AutomationPolicy::fromJson) ?: AutomationPolicy()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AutomationPolicy.disabled()
        }

    public suspend fun setBehavior(behavior: AutomationBehavior, enabled: Boolean): Outcome<Unit> = change { it.withBehavior(behavior, enabled) }

    public suspend fun setSupervision(supervision: AutomationSupervision): Outcome<Unit> = change { it.withSupervision(supervision) }

    public suspend fun setAutoApprove(enabled: Boolean): Outcome<Unit> = change { it.withApprovals(autoApprove = enabled) }

    public suspend fun setAutoMergeOnGreen(enabled: Boolean): Outcome<Unit> = change { it.withApprovals(autoMergeOnGreen = enabled) }

    public suspend fun disableAll(): Outcome<Unit> = change { AutomationPolicy.disabled() }

    private suspend fun change(update: (AutomationPolicy) -> AutomationPolicy): Outcome<Unit> {
        if (closed || paused) return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        return lane.withLock {
            if (closed) return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
            val next = update(mutable.value)
            val raw = next.toJson().toString()
            if (protected(raw)) return@withLock Failure.StorageRefused().failed()
            val saved =
                try {
                    store.write(keyFor(profileId), raw)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            if (!saved) return@withLock Failure.StorageRefused().failed()
            mutable.value = next
            Unit.ok()
        }
    }

    /** Stops new edits and drains the admitted ones, keeping this owner for a cancelled deletion. */
    public suspend fun pauseForDeletion() {
        paused = true
        lane.withLock { }
    }

    /** Editing resumes after a deletion is cancelled; a closed owner stays closed. */
    public fun cancelDeletion() {
        if (!closed) paused = false
    }

    /** Closes for good and drains in-flight storage: await it BEFORE the profile sweep (no late resurrection). */
    public suspend fun prepareForDeletion() {
        closed = true
        lane.withLock { }
    }
}

/**
 * The one shared owner per profile (Flutter `AutomationPolicyController.forProfile` / `closeProfile`): Settings and
 * every executor read the same owner, so a profile never has two writers. [closeProfile] closes and drains the owner
 * BEFORE the deletion sweep removes its key; a later [forProfile] starts fresh from what storage then holds.
 */
public class AutomationPolicies(private val store: AutomationStore, private val protected: (String) -> Boolean) {
    private val owners = HashMap<String, AutomationPolicyOwner>()

    public fun forProfile(profileId: String): AutomationPolicyOwner =
        owners.getOrPut(profileId) { AutomationPolicyOwner(profileId, store, protected) }

    public suspend fun closeProfile(profileId: String) {
        val owner = owners[profileId] ?: return
        try {
            owner.prepareForDeletion()
        } finally {
            if (owners[profileId] === owner) owners.remove(profileId)
        }
    }
}
