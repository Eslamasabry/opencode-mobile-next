package io.github.eslamasabry.oc.protocol.paseo.work

import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.arr
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoPush
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Helper-wide work truth (B-AGENT-168), a port of `lib/paseo/gateway/idle_work.dart` (`_PaseoWorkInventory`).
 *
 * The helper's inventory is separate from the visible project's session cache. Only a complete snapshot plus ordered
 * turn boundaries prove idle; time and expiring leases are never evidence that work finished. `null` (unknown) never
 * authorizes idle admission.
 *
 * ## Wiring (gateway with `trackLocalWork = true`; all calls on the gateway's lane)
 *
 * | Gateway moment | Call |
 * |---|---|
 * | every transport push (`PaseoTransportListener.onPush`), before the gateway's own handling | [event] |
 * | a `fetch_agents_request` page: capture [revision] **before** sending, then after the answer | [observePage] with `listChildren` = `transport.request("agent.provider_subagents.list.request", {parentAgentId}, timeoutMs = 2_000, expectedEpoch = epoch)` and `stillCurrent` = `!closed && transport.epoch == epoch` |
 * | transport disconnect (`onDisconnect`) | [invalidate] |
 * | any change the gateway makes to its own turn sets | [changed] |
 * | `localWorkBusy` | [read] (`connected = transport.connected`, `epoch = transport.epoch`, `gatewayBusy` = pending payload work, awaiting-turn, turn-active or uncertain sends) — return null when the gateway is closed or does not track local work |
 * | `localWorkChanges` | [changes] |
 *
 * Not thread-safe by design: one serial lane, like every engine (native/AGENTS.md rule 4).
 */
public class PaseoWorkInventory(private val timeSource: TimeSource = TimeSource.Monotonic) {
    private val changeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Inventory changes (no payload); conflated. */
    public val changes: Flow<Unit> = changeFlow.asSharedFlow()

    private class Child(val parent: String, val busy: Boolean?)

    private class Sequence(val epoch: String, val seq: Long)

    private val agents = LinkedHashMap<String, Boolean?>()
    private val turns = mutableSetOf<String>()
    private val permissions = LinkedHashMap<String, String>()
    private val resolvedPermissions = mutableSetOf<String>()
    private val livePermissions = LinkedHashMap<String, String>()
    private val activeChildren = mutableSetOf<String>()
    private val coveredParents = mutableSetOf<String>()
    private val parents = mutableSetOf<String>()
    private val retiredStreamEpochs = HashMap<String, MutableSet<String>>()
    private val children = LinkedHashMap<String, Child>()
    private val touched = HashMap<String, Int>()
    private val childTouched = HashMap<String, Int>()
    private val sequences = HashMap<String, Sequence>()
    private var epoch = -1
    private var brokenRevision = -1
    private var complete = false
    private var scan: Scan? = null

    /** Monotonic change counter. Capture it before a list request and pass it to [observePage]. */
    public var revision: Int = 0
        private set

    /**
     * true = known work somewhere on the helper; false = a complete, current inventory proves idle; null = unknown.
     * [gatewayBusy] is the gateway's own evidence (payload work, a sent prompt awaiting its turn, an open turn).
     */
    public fun read(connected: Boolean, currentEpoch: Int, gatewayBusy: Boolean = false): Boolean? {
        if (gatewayBusy) return true
        if (turns.isNotEmpty() || activeChildren.isNotEmpty() || permissions.isNotEmpty() ||
            agents.values.contains(true) || children.values.any { it.busy == true }
        ) {
            return true
        }
        if (!connected || epoch != currentEpoch || !complete || !coveredParents.containsAll(parents) ||
            brokenRevision >= 0 || agents.values.contains(null) || children.values.any { it.busy == null }
        ) {
            return null
        }
        return false
    }

    public fun changed() {
        changeFlow.tryEmit(Unit)
    }

    /** The socket was lost: nothing is proven until a fresh complete list. */
    public fun invalidate() {
        complete = false
        scan = null
        changed()
    }

    private fun malformed() {
        brokenRevision = ++revision
        changed()
    }

    private fun status(value: JsonElement?): Boolean? =
        when (value.stringOrNull()) {
            "initializing", "running", "busy", "waiting", "waiting_permission", "awaiting_permission" -> true
            "idle", "closed", "error", "completed", "canceled", "cancelled", "failed" -> false
            else -> null
        }

    private fun remove(id: String, explicit: Boolean = true) {
        agents.remove(id)
        parents.remove(id)
        coveredParents.remove(id)
        if (explicit) {
            turns.remove(id)
            permissions.entries.removeAll { it.value == id }
            livePermissions.entries.removeAll { it.value == id }
            activeChildren.removeAll { children[it]?.parent == id }
            children.entries.removeAll { it.value.parent == id }
            touched[id] = ++revision
        } else {
            // A lagging archived/absent list row is not a live turn boundary.
            permissions.entries.removeAll { it.value == id && !livePermissions.containsKey(it.key) }
            children.entries.removeAll { it.value.parent == id && it.key !in activeChildren && it.value.busy != true }
        }
    }

    private fun observeParent(id: String) {
        parents += id
        if (id !in coveredParents) complete = false
    }

    private fun agent(raw: JsonElement?, snapshotRevision: Int? = null): Boolean {
        val obj = raw.objectOrNull() ?: return false
        val id = obj.str("id")?.takeIf { it.isNotEmpty() } ?: return false
        if (snapshotRevision != null && (touched[id] ?: -1) > snapshotRevision) return true
        if (obj.str("archivedAt") != null) {
            remove(id, explicit = snapshotRevision == null)
            return true
        }
        // A saved record the helper has not loaded (no `activeTurn`) has no process, so it runs nothing even when its
        // last stored status was running; loading it just to reset that would start its process.
        val busy = if (obj.containsKey("activeTurn")) status(obj["status"]) else false
        agents[id] = busy
        observeParent(id)
        val pending = obj["pendingPermissions"] as? JsonArray ?: return false
        permissions.entries.removeAll { it.value == id && !livePermissions.containsKey(it.key) }
        for (request in pending) {
            val requestId = request.objectOrNull()?.str("id") ?: return false
            if (requestId !in resolvedPermissions) permissions[requestId] = id
        }
        if (snapshotRevision == null) touched[id] = ++revision
        return busy != null
    }

    private fun child(raw: JsonElement?, snapshotRevision: Int? = null): Boolean {
        val obj = raw.objectOrNull() ?: return false
        val id = obj.str("id") ?: return false
        val parent = obj.str("parentAgentId") ?: return false
        observeParent(parent)
        if (snapshotRevision != null && (childTouched[id] ?: -1) > snapshotRevision) return true
        val busy = status(obj["status"])
        if (snapshotRevision == null) {
            if (busy == true) activeChildren += id else if (busy == false) activeChildren -= id
        }
        children[id] = Child(parent, busy)
        if (snapshotRevision == null) childTouched[id] = ++revision
        return busy != null
    }

    private fun startEpoch(currentEpoch: Int) {
        if (epoch == currentEpoch) return
        epoch = currentEpoch
        agents.clear()
        turns.clear()
        permissions.clear()
        resolvedPermissions.clear()
        children.clear()
        activeChildren.clear()
        coveredParents.clear()
        parents.clear()
        retiredStreamEpochs.clear()
        livePermissions.clear()
        touched.clear()
        childTouched.clear()
        sequences.clear()
        brokenRevision = -1
        complete = false
        scan = null
    }

    private inner class Scan(val revision: Int) {
        val seen = mutableSetOf<String>()
        val cursors = mutableSetOf<String>()
        val parents = mutableSetOf<String>()
        val started = timeSource.markNow()
        var checkedParents = 0
        var nextCursor: String? = null
        var valid = true
        var lastPage = false
    }

    private fun page(result: JsonObject, cursor: String?, currentEpoch: Int, requestRevision: Int): Scan? {
        startEpoch(currentEpoch)
        if (cursor == null) {
            complete = false
            scan = Scan(requestRevision)
        }
        val active = scan
        if (active == null || cursor != active.nextCursor) {
            malformed()
            return null
        }
        val rows = result["entries"] as? JsonArray
        val pageInfo = result.obj("pageInfo")
        val hasMore = pageInfo?.get("hasMore").boolOrNull()
        if (rows == null || rows.size > 200 || pageInfo == null || hasMore == null) {
            active.valid = false
            changed()
            return active
        }
        active.parents.clear()
        for (row in rows) {
            val raw = row.objectOrNull()?.get("agent")
            if (!agent(raw, snapshotRevision = active.revision)) active.valid = false
            val obj = raw.objectOrNull() ?: continue
            val id = obj.str("id") ?: continue
            active.seen += id
            if (obj.str("archivedAt") == null) {
                // The helper answers a sub-agent list by loading the parent, which starts its process. A saved agent it
                // has not loaded runs no process and so no sub-agent: covered without asking.
                if (obj.containsKey("activeTurn")) active.parents += id else coveredParents += id
            }
        }
        val next = pageInfo["nextCursor"].stringOrNull()
        if (hasMore && (next.isNullOrEmpty() || !active.cursors.add(next))) active.valid = false
        active.nextCursor = if (hasMore && !next.isNullOrEmpty()) next else null
        active.lastPage = !hasMore
        changed()
        return active
    }

    private fun finish(active: Scan) {
        if (scan !== active) return
        if (active.lastPage && active.valid) {
            val removed = agents.keys.filter { it !in active.seen && (touched[it] ?: -1) <= active.revision }
            for (id in removed) remove(id, explicit = false)
            complete = coveredParents.containsAll(parents)
            if (brokenRevision <= active.revision) brokenRevision = -1
            scan = null
        }
        changed()
    }

    /**
     * Observes one `fetch_agents_response` page ([result]) read with [cursor] on transport [epoch], whose request was
     * sent at [requestRevision]. Loaded parents on the page get their sub-agents listed through [listChildren] (four at a
     * time, at most 64 parents and 8 s per scan; a bounded observation that cannot finish stays unknown). [listChildren]
     * throws (any exception) when the list fails; [stillCurrent] is false once the gateway closed or the socket changed.
     */
    public suspend fun observePage(
        result: JsonObject,
        cursor: String?,
        epoch: Int,
        requestRevision: Int,
        listChildren: suspend (parentAgentId: String) -> JsonObject,
        stillCurrent: () -> Boolean,
    ) {
        val active = page(result, cursor, epoch, requestRevision) ?: return
        val parentList = active.parents.toList()
        var offset = 0
        while (offset < parentList.size) {
            if (active.checkedParents >= MAX_PARENTS || active.started.elapsedNow() >= SCAN_BUDGET) {
                active.valid = false
                break
            }
            val batch = parentList.drop(offset).take(BATCH)
            offset += BATCH
            active.checkedParents += batch.size
            coroutineScope {
                batch.map { parent -> async { childPage(active, parent, listChildren, stillCurrent) } }.awaitAll()
            }
        }
        if (stillCurrent()) finish(active)
    }

    private suspend fun childPage(
        active: Scan,
        parent: String,
        listChildren: suspend (String) -> JsonObject,
        stillCurrent: () -> Boolean,
    ) {
        val answer = try {
            listChildren(parent)
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaseoException) {
            active.valid = false
            return
        } catch (_: Exception) {
            active.valid = false
            return
        }
        if (!stillCurrent() || scan !== active) return
        val rows = answer.arr("subagents")
        val error = answer["error"]
        if ((error != null && error !is JsonNull) || answer.str("parentAgentId") != parent || rows == null || rows.size > 500) {
            active.valid = false
            return
        }
        coveredParents += parent
        val seen = mutableSetOf<String>()
        for (row in rows) {
            val obj = row.objectOrNull()
            if (obj == null || obj.str("parentAgentId") != parent || !child(obj, snapshotRevision = active.revision)) {
                active.valid = false
            } else {
                seen += obj.str("id")!!
            }
        }
        children.entries.removeAll {
            it.value.parent == parent && it.key !in seen && it.key !in activeChildren &&
                (childTouched[it.key] ?: -1) <= active.revision
        }
    }

    /** Every daemon push of the gateway's transport, in arrival order. */
    public fun event(push: PaseoPush) {
        startEpoch(push.epoch)
        val p = push.payload
        val id = (p.str("agentId") ?: p.obj("agent")?.str("id"))?.takeIf { it.isNotEmpty() }
        when (push.type) {
            "agent_update" ->
                if (p.str("kind") == "remove" && id != null) remove(id) else if (!agent(p["agent"])) malformed()
            "agent_deleted", "agent_archived" -> if (id != null) remove(id) else malformed()
            "agent_stream" -> if (!stream(id, p)) return
            "agent_permission_request" -> {
                val requestId = p.obj("request")?.str("id")
                if (id != null && requestId != null) {
                    observeParent(id)
                    permissions[requestId] = id
                    livePermissions[requestId] = id
                    touched[id] = ++revision
                } else {
                    malformed()
                }
            }
            "agent_permission_resolved" -> resolve(p["requestId"])
            "agent.provider_subagents.update" ->
                when (p.str("kind")) {
                    "upsert" -> if (!child(p["subagent"])) malformed()
                    "remove" -> {
                        val childId = p.str("subagentId")
                        if (childId != null) {
                            children.remove(childId)
                            activeChildren.remove(childId)
                            childTouched[childId] = ++revision
                        } else {
                            malformed()
                        }
                    }
                    "timeline" -> Unit
                    else -> malformed()
                }
        }
        changed()
    }

    /** Returns false when the push was dropped without a change notice (Dart's early `return`). */
    private fun stream(id: String?, p: JsonObject): Boolean {
        val inner = p.obj("event")
        val type = inner?.str("type")
        if (id == null || inner == null || type == null) {
            malformed()
            return true
        }
        observeParent(id)
        val seq = p["seq"].longOrNull()
        val streamEpoch = p.str("epoch")
        val boundary = type in BOUNDARIES
        val ordered = seq != null && seq >= 0 && !streamEpoch.isNullOrEmpty()
        if (boundary && !ordered) {
            // Starting work is safe to retain even without an ordered boundary; an unorderable completion can never
            // authorize idle.
            if (type == "turn_started") {
                turns += id
                touched[id] = ++revision
            }
            malformed()
            return false
        }
        if (ordered) {
            val previous = sequences[id]
            val retired = retiredStreamEpochs.getOrPut(id) { mutableSetOf() }
            if (streamEpoch!! in retired) return false
            if (previous != null) {
                if (previous.epoch == streamEpoch && seq!! <= previous.seq) return false
                if (previous.epoch != streamEpoch) {
                    if (type != "turn_started") {
                        malformed()
                        return false
                    }
                    retired += previous.epoch
                }
            }
            sequences[id] = Sequence(streamEpoch, seq!!)
        }
        when (type) {
            "turn_started" -> {
                turns += id
                touched[id] = ++revision
            }
            "turn_completed", "turn_canceled", "turn_failed" -> {
                turns.remove(id)
                agents[id] = false
                touched[id] = ++revision
            }
            "permission_requested" -> {
                val requestId = inner.obj("request")?.str("id")
                if (requestId != null) {
                    permissions[requestId] = id
                    livePermissions[requestId] = id
                    touched[id] = ++revision
                } else {
                    malformed()
                }
            }
            "permission_resolved" -> resolve(inner["requestId"])
        }
        return true
    }

    private fun resolve(requestId: JsonElement?) {
        val rid = requestId.stringOrNull()
        if (rid == null) {
            malformed()
            return
        }
        val parent = permissions.remove(rid)
        livePermissions.remove(rid)
        resolvedPermissions += rid
        if (parent != null) touched[parent] = ++revision
    }

    private companion object {
        val BOUNDARIES = setOf("turn_started", "turn_completed", "turn_canceled", "turn_failed")
        const val BATCH = 4
        const val MAX_PARENTS = 64
        val SCAN_BUDGET = 8.seconds
    }
}
