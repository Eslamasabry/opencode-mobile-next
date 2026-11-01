package io.github.eslamasabry.oc.core.data.scope

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType

/** Plan 05 §3.3. `Owner` = keyed by a phone-agent owner id. */
public enum class KeyScope { Profile, Owner, App, Shared, Other }

/**
 * One registered key shape (a ledger row). [prefix] is `"oc.model."` for a per-profile family or the exact key
 * (`"oc.activeProfile"`) for an app-wide one.
 */
public data class KeyShape(
    val prefix: String,
    val scope: KeyScope,
    val type: PrefType,
    val ownerModule: String,
    val why: String,
)

/** Everything a profile owns, found late (after the lanes drained). Names and counts only, never values. */
public data class OwnedData(
    val prefKeys: Set<String>,
    val secretNames: Set<String>,
    val tables: List<String>,
)

/** What a sweep removed: counts per owning module (`"unregistered"` for keys no shape claims). */
public data class SweepReceipt(val removedKeys: Int, val retainedKeys: Int, val removedByOwner: Map<String, Int>)

/**
 * The per-profile deletion registry (plan 05 §3.3–3.5, AGENTS.md rule 9). Owning modules register their shapes
 * (later through a Metro `Set<KeyShape>` multibinding); `:quality` checks the registered set against the ledger.
 *
 * The deletion TRANSACTION (close lanes, queue/drafts/stash steps, profile row + secrets) belongs to
 * `:engine:profiles`; this type provides the late key computation and the generic sweep (step 7).
 */
public interface ProfileScope {
    public fun register(shape: KeyShape)

    public fun registerSecret(prefix: String, ownerModule: String)

    /** Room tables with a `profile_id` column (switch+1). */
    public fun registerTable(table: String, profileColumn: String = "profile_id")

    public fun shapes(): List<KeyShape>

    /** The Flutter sweep rule (see [ProfileKeyRule]), computed now over the live store. */
    public fun scopedKeys(profileId: ProfileId): Set<String>

    public suspend fun ownedData(profileId: ProfileId): Outcome<OwnedData>

    /** Every dotted segment of every registered app-wide key; generated ids never take one (plan 05 §3.2). */
    public fun reservedSegments(): Set<String>

    /**
     * Plan 05 §3.5 step 7: removes [scopedKeys] minus [retained] in ONE commit. Call it only after the profile's lanes
     * were closed and drained (steps 1–6). Idempotent: a second sweep removes nothing.
     */
    public suspend fun sweep(profileId: ProfileId, retained: Set<String> = emptySet()): Outcome<SweepReceipt>
}
