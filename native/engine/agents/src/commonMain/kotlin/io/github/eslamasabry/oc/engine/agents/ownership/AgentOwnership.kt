package io.github.eslamasabry.oc.engine.agents.ownership

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.gate.PhoneGate
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Owner aliasing (STATE-DATA-05, plan 17 §1.6), ported from `lib/state/profiles/phone_agent_owner.dart`. A protocol
 * profile owns server settings; the in-app Ubuntu owns one agent home. Every profile of this phone's own server maps to
 * one owner through `oc.phoneAgentOwner.<profileId>`; the caller passes only those profiles (remote and Termux servers
 * are never phone homes), in creation order.
 */
public class AgentOwnership(private val prefs: AgentPrefs) {
    /** The owner of [profile]'s agent home; the profile itself when no mapping is saved. */
    public fun ownerOf(profile: ProfileId): OwnerId =
        prefs.string(AgentKeys.phoneAgentOwner(profile))?.let(::ProfileId) ?: profile

    /**
     * Load-time migration (`_migratePhoneAgentOwners`): the first saved valid mapping wins; otherwise the highest score
     * (checked home +4, cached chats +2, used +1), ties to creation order. Legacy gates are then adopted once per donor,
     * and every phone profile is mapped to the owner. A refused write returns `Failure.Storage` and leaves receipts
     * unwritten, so the retry starts from persisted truth.
     */
    public suspend fun migrate(phoneProfiles: List<ProfileId>): Outcome<OwnerId?> {
        if (phoneProfiles.isEmpty()) return null.ok()
        val owner = phoneProfiles.firstNotNullOfOrNull { p ->
            prefs.string(AgentKeys.phoneAgentOwner(p))?.takeIf { VALID_ID.matches(it) }?.let(::ProfileId)
        } ?: phoneProfiles.drop(1).fold(phoneProfiles.first()) { best, p -> if (score(p) > score(best)) p else best }
        when (val adopted = adoptLegacyGates(owner, phoneProfiles)) {
            is Outcome.Failed -> return adopted
            is Outcome.Ok -> Unit
        }
        val missing = phoneProfiles.filter { prefs.string(AgentKeys.phoneAgentOwner(it)) != owner.raw }
        if (missing.isNotEmpty()) {
            when (val r = prefs.commit { missing.forEach { putString(AgentKeys.phoneAgentOwner(it), owner.raw) } }) {
                is Outcome.Failed -> return r
                is Outcome.Ok -> Unit
            }
        }
        return owner.ok()
    }

    private fun score(p: ProfileId): Int {
        var result = if (prefs.bool(AgentKeys.phoneAgentsUsed(p)) == true) 1 else 0
        val gates = AgentJson.parseOrNull(prefs.string(AgentKeys.agentPhoneGate(p)) ?: "{}")
        val feed = AgentJson.parseOrNull(prefs.string(AgentKeys.agentFeed(p)) ?: "[]")
        // Dart computes both inside one try: malformed storage never establishes a checked home.
        if (gates == null || feed == null) return result
        if (gates is JsonObject && gates.values.any { g -> g is JsonObject && (g["fingerprint"] as? JsonPrimitive)?.isString == true }) {
            result += 4
        }
        if (feed is JsonArray && feed.isNotEmpty()) result += 2
        return result
    }

    private suspend fun adoptLegacyGates(owner: OwnerId, local: List<ProfileId>): Outcome<Unit> {
        val receiptKey = AgentKeys.phoneAgentGateMigration(owner)
        val storedReceipts = prefs.stringList(receiptKey)
        if (storedReceipts == null && receiptKey in prefs.keys(receiptKey)) return storage()
        val receipts = storedReceipts?.toSet() ?: emptySet()
        if (receipts.any { !VALID_ID.matches(it) }) return storage()
        val gates: LinkedHashMap<String, JsonElement> = gatesFor(owner)
        var changed = false
        val donors = mutableListOf<String>()
        for (profile in local) {
            if (profile == owner || profile.raw in receipts) continue
            donors += profile.raw
            for ((agent, gate) in gatesFor(profile)) {
                val fp = ((gate as? JsonObject)?.get("fingerprint") as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (agent !in gates && !fp.isNullOrEmpty()) {
                    gates[agent] = gate
                    changed = true
                }
            }
        }
        if (changed) {
            when (val r = PhoneGate.write(prefs, owner, gates)) {
                is Outcome.Failed -> return storage()
                is Outcome.Ok -> Unit
            }
        }
        if (donors.isNotEmpty()) {
            when (prefs.commit { putStringList(receiptKey, receipts.toList() + donors) }) {
                is Outcome.Failed -> return storage()
                is Outcome.Ok -> Unit
            }
        }
        return Unit.ok()
    }

    private fun gatesFor(p: ProfileId): LinkedHashMap<String, JsonElement> =
        (AgentJson.parseOrNull(prefs.string(AgentKeys.agentPhoneGate(p)) ?: "{}") as? JsonObject)?.let { LinkedHashMap(it) }
            ?: LinkedHashMap()

    /** Deleting [profile] keeps the agents when another phone profile still maps to the same owner. */
    public fun retainedAfterRemoving(profile: ProfileId, phoneProfiles: List<ProfileId>): Boolean {
        val owner = ownerOf(profile)
        return phoneProfiles.any { it != profile && ownerOf(it) == owner }
    }

    /** The owner's agent keys (all of them, regardless of whether they are retained). */
    public fun ownerPreferenceKeys(owner: OwnerId): Set<String> =
        prefs.keys("oc.").filterTo(LinkedHashSet()) { key ->
            key == AgentKeys.agentPhoneGate(owner) || key == AgentKeys.phoneAgentGateMigration(owner) ||
                key == AgentKeys.agentInstall(owner) || key == AgentKeys.agentFeed(owner) ||
                key == AgentKeys.phoneAgentsUsed(owner) || key.endsWith(".${owner.raw}${AgentKeys.AGENTS_SUFFIX}")
        }

    /** Keys the profile sweep must keep when [profile] is removed but the home stays (`retainedPhoneAgentPreferenceKeys`). */
    public fun retainedPreferenceKeys(profile: ProfileId, phoneProfiles: List<ProfileId>): Set<String> =
        if (retainedAfterRemoving(profile, phoneProfiles)) ownerPreferenceKeys(ownerOf(profile)) else emptySet()

    private fun storage(): Outcome<Nothing> = Failure.Storage(StorageProblem.InvalidValue).failed()

    public companion object {
        public val VALID_ID: Regex = Regex("^[A-Za-z0-9_-]{1,80}$")
    }
}
