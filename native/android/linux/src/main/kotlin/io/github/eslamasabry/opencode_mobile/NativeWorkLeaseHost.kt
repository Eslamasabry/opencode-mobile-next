package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * CPU ownership only. A lease never overrides Android foreground-service policy.
 *
 * One lock-guarded state machine: chat scopes, adopted owners and terminal sessions share the
 * guard, the lease table and the wake lock, so splitting it would only move the lock discipline.
 */
@Suppress("TooManyFunctions")
internal class NativeWorkLeaseHost(
    private val leases: WorkLeases, private val now: () -> Long, private val wake: Wake?,
    private val terminal: Terminals, private val changed: () -> Unit,
    private val enqueue: (Long, () -> Unit) -> (() -> Unit),
) {
    interface Wake { val isHeld: Boolean; fun acquire(timeoutMs: Long); fun release() }

    /** The terminal sessions the host observes, and the call that protects new ones. */
    class Terminals(val work: () -> Map<Int, Boolean>, val protect: () -> Boolean)

    constructor(context: Context, terminalWork: () -> Map<Int, Boolean>,
        protectTerminal: () -> Boolean, changed: () -> Unit) : this(
        WorkLeases(SystemClock::elapsedRealtime), SystemClock::elapsedRealtime, androidWake(context),
        Terminals(terminalWork, protectTerminal), changed, queue())
    private val guard = Any()
    private val namedChats = mutableMapOf<String, Long?>()
    private data class AgentChat(val profile: String, val name: String)
    private val agentChats = mutableMapOf<AgentChat, Long?>()
    private data class Owner(val token: Long?, val alive: () -> Boolean, var renewAt: Long)
    private val owners = mutableMapOf<Any, Owner>()
    private val terminals = mutableSetOf<Int>()
    private var scheduled = false
    private var scheduledAt = 0L
    private var scheduleRevision = 0L
    private var cancelScheduled: (() -> Unit)? = null
    private var foregroundRevision = 0L
    private var chatAdmissionOpen = true
    val held: Boolean get() = synchronized(guard) { wake?.isHeld == true && leases.snapshot().activeCount > 0 }
    val foregroundHeld: Boolean get() = synchronized(guard) {
        leases.snapshot().counts.any { (kind, count) -> kind != WorkLeases.Kind.SETUP && count > 0 }
    }
    fun diagnostics(): Map<String, Any?> = synchronized(guard) {
        val state = leases.snapshot()
        mapOf("held" to (wake?.isHeld == true && state.activeCount > 0), "activeCount" to state.activeCount,
            "kinds" to state.counts.filterValues { it > 0 }.mapKeys { it.key.name.lowercase() })
    }

    /** Same opaque name renews the same token; expiry never silently creates a new run. */
    fun chat(
        name: String, on: Boolean, holdMs: Long, serverRunning: Boolean,
    ): Map<String, Boolean> = synchronized(guard) {
        if (!validChatName(name)) return@synchronized mapOf("held" to false, "capped" to false)
        setChat(namedChats, name, on, holdMs, serverRunning)
    }

    fun agentChat(profile: String, name: String, on: Boolean, holdMs: Long,
        helperRunning: Boolean): Map<String, Boolean> = synchronized(guard) {
        if (!validProfile(profile) || !validChatName(name)) {
            return@synchronized mapOf("held" to false, "capped" to false)
        }
        setChat(agentChats, AgentChat(profile, name), on, holdMs, helperRunning)
    }

    /** Caller holds guard. Chat scopes share one logical owner bound with native work. */
    private fun <K> setChat(chats: MutableMap<K, Long?>, key: K, on: Boolean,
        holdMs: Long, running: Boolean): Map<String, Boolean> = when {
        !on -> {
            chats.remove(key)?.let { leases.release(it) }
            updateWake()
            chatResult(held = false, capped = false)
        }
        !running || holdMs <= 0 -> {
            retainChat(chats, key)
            chatResult(held = false, capped = false)
        }
        !chatAdmissionOpen -> chatResult(held = false, capped = retainChat(chats, key))
        else -> holdChat(chats, key, holdMs)
    }

    private fun chatResult(held: Boolean, capped: Boolean) = mapOf("held" to held, "capped" to capped)

    /** Drops the key's lease but keeps its slot; returns whether a slot was retained. */
    private fun <K> retainChat(chats: MutableMap<K, Long?>, key: K): Boolean {
        chats[key]?.let { leases.release(it) }
        val retained = chats.containsKey(key)
        if (retained) chats[key] = null
        updateWake()
        return retained
    }

    private fun <K> holdChat(chats: MutableMap<K, Long?>, key: K, holdMs: Long): Map<String, Boolean> {
        if (!chats.containsKey(key)) {
            if (logicalOwnerCount() >= MAX_LOGICAL_OWNERS) return chatResult(held = false, capped = false)
            chats[key] = leases.acquire(WorkLeases.Kind.CHAT, holdMs)
        } else chats[key]?.let { leases.renew(it, holdMs) }
        val token = chats[key]
        val admitted = token != null && leases.contains(token)
        updateWake(); schedule(SOON_MS)
        return chatResult(held = admitted && wake?.isHeld == true, capped = !admitted)
    }

    fun adopt(owner: Any, kind: WorkLeases.Kind, alive: () -> Boolean) = synchronized(guard) {
        if (owners.containsKey(owner) || logicalOwnerCount() >= MAX_LOGICAL_OWNERS) return@synchronized
        owners[owner] = Owner(leases.acquire(kind, HOLD_MS), alive, now() + RENEW_MS)
        updateWake(); schedule(SOON_MS)
    }
    fun release(owner: Any) = synchronized(guard) {
        owners.remove(owner)?.token?.let { leases.release(it) }
        updateWake()
    }
    fun serverGone() = synchronized(guard) {
        namedChats.values.filterNotNull().forEach { leases.release(it) }
        namedChats.keys.toList().forEach { namedChats[it] = null }
        updateWake()
    }
    fun helperGone(profile: String) = synchronized(guard) {
        if (!validProfile(profile)) return@synchronized
        agentChats.keys.filter { it.profile == profile }.forEach { key ->
            agentChats[key]?.let { leases.release(it) }
            agentChats[key] = null
        }
        updateWake()
    }
    fun agentProfiles(): Set<String> = synchronized(guard) { agentChats.keys.map { it.profile }.toSet() }

    /** Work truth is independent of CPU caps. Unknown external state cannot prove idle. */
    fun logicalWorkBusy(): Boolean? {
        val captured = synchronized(guard) {
            if (namedChats.isNotEmpty() || agentChats.isNotEmpty()) return true
            owners.toMap()
        }
        // These providers may hold Linux/terminal monitors; never invoke them under guard.
        val running = try { terminal.work() } catch (_: Throwable) { null }
        val liveness = captured.mapValues { (_, owner) -> try { owner.alive() } catch (_: Throwable) { null } }
        return synchronized(guard) {
            val chatting = namedChats.isNotEmpty() || agentChats.isNotEmpty()
            if (chatting || running?.isNotEmpty() == true) return@synchronized true
            if (liveness.any { (key, live) -> live == true && owners[key] === captured[key] }) return@synchronized true
            val unchanged = owners.size == captured.size && captured.all { (key, owner) -> owners[key] === owner }
            if (!unchanged || running == null || liveness.values.any { it == null }) null else false
        }
    }

    private fun logicalOwnerCount() = namedChats.size + agentChats.size + owners.size
    private fun validChatName(name: String) = Regex("[A-Za-z0-9_.-]{1,80}").matches(name)
    private fun validProfile(profile: String) = Regex("[A-Za-z0-9_-]{1,80}").matches(profile)
    /** Service loss/Stop/timeout cannot be followed by a stale renewal. Setup owns its separate service. */
    fun revokeForegroundWork() = synchronized(guard) {
        chatAdmissionOpen = false
        foregroundRevision++
        listOf(WorkLeases.Kind.CHAT, WorkLeases.Kind.SIGN_IN, WorkLeases.Kind.TERMINAL)
            .forEach { leases.releaseKind(it) }
        updateWake()
    }
    fun foregroundGeneration(): Long = synchronized(guard) { foregroundRevision }
    /** Only an authored launch captured before dispatch can open this exact generation. */
    fun authorizeForegroundWork(expectedGeneration: Long): Boolean = synchronized(guard) {
        if (expectedGeneration != foregroundRevision) return@synchronized false
        chatAdmissionOpen = true
        true
    }
    fun revokeSetupWork() = synchronized(guard) { leases.releaseKind(WorkLeases.Kind.SETUP); updateWake() }
    fun observeTerminalsSoon() = synchronized(guard) { schedule(SOON_MS) }

    private fun schedule(delayMs: Long) {
        val due = now() + delayMs
        if (scheduled && scheduledAt <= due) return
        cancelScheduled?.invoke()
        val revision = ++scheduleRevision
        scheduled = true; scheduledAt = due
        try { cancelScheduled = enqueue(delayMs, task@{
            synchronized(guard) {
                if (revision != scheduleRevision) return@task
                scheduled = false; cancelScheduled = null
            }
            pulse()
        }) } catch (_: Throwable) { scheduled = false; leases.clear(); updateWake() }
    }
    private class Observation(
        val running: Map<Int, Boolean>?, val captured: Map<Any, Owner>,
        val liveness: Map<Any, Boolean?>, val added: List<Int>,
        val protected: Boolean, val revision: Long,
    )

    internal fun pulse() {
        // Read external lifecycle owners outside our guard: terminal creation holds its own monitor.
        val running = try { terminal.work() } catch (_: Throwable) { null }
        val captured = synchronized(guard) { owners.toMap() }
        val liveness = captured.mapValues { (_, owner) -> try { owner.alive() } catch (_: Throwable) { null } }
        val revision = synchronized(guard) { foregroundRevision }
        val added = synchronized(guard) { running?.keys?.filter { it !in terminals }
            .orEmpty().take(ownerRoom()) }
        val protected = added.isEmpty() || try { terminal.protect() } catch (_: Throwable) { false }
        synchronized(guard) {
            settle(Observation(running, captured, liveness, added, protected, revision))
        }
        try { changed() } catch (_: Throwable) { }
    }

    private fun ownerRoom() = (MAX_LOGICAL_OWNERS - logicalOwnerCount()).coerceAtLeast(0)

    /** Caller holds guard; applies one external observation. */
    private fun settle(seen: Observation) {
        cancelScheduled?.invoke(); cancelScheduled = null; scheduled = false; scheduleRevision++
        releaseLostOwners(seen)
        acquireTerminals(seen)
        val now = now()
        owners.values.forEach { owner ->
            if (now >= owner.renewAt) { owner.token?.let { leases.renew(it, HOLD_MS) }; owner.renewAt = now + RENEW_MS }
        }
        updateWake()
        val next = leases.snapshot().nextExpiryInMs
        if (owners.isNotEmpty()) schedule(PULSE_MS)
        else if (next != null) schedule(next.coerceIn(1, HOLD_MS))
    }

    private fun releaseLostOwners(seen: Observation) {
        val running = seen.running
        val captured = seen.captured
        val unknown = seen.liveness.filterValues { it == null }.keys
        val ended = seen.liveness.filterValues { it == false }.keys
        unknown.forEach { owner ->
            if (owners[owner] === captured[owner]) owners[owner]?.token?.let { leases.release(it) }
        }
        if (running == null) terminals.forEach { owners["terminal.$it"]?.token?.let { token -> leases.release(token) } }
        ended.forEach { owner -> if (owners[owner] === captured[owner]) release(owner) }
        terminals.filter { running != null && it !in running }.toList().forEach { id ->
            release("terminal.$id")
            terminals.remove(id)
        }
    }

    private fun acquireTerminals(seen: Observation) {
        seen.added.take(ownerRoom()).forEach { id ->
            terminals.add(id)
            val kind = if (seen.running?.get(id) == true) WorkLeases.Kind.SIGN_IN else WorkLeases.Kind.TERMINAL
            // Retain a refused/capped owner until that exact session ends, never reacquire it.
            val admitted = seen.protected && foregroundRevision == seen.revision
            owners["terminal.$id"] = Owner(if (admitted) leases.acquire(kind, HOLD_MS) else null,
                { id in terminal.work() }, now() + RENEW_MS)
        }
    }
    private fun updateWake() {
        val lock = wake ?: run { leases.clear(); return }
        val remaining = leases.snapshot().holdForMs
        try {
            if (remaining > 0) lock.acquire(remaining.coerceAtMost(HOLD_MS))
            else if (lock.isHeld) lock.release()
        } catch (_: RuntimeException) {
            // No raw policy/error text escapes. Every logical owner retains its stale token.
            leases.clear()
            try { if (lock.isHeld) lock.release() } catch (_: RuntimeException) { }
        }
    }
    companion object {
        private const val HOLD_MS = 15 * 60 * 1000L
        private const val RENEW_MS = 5 * 60 * 1000L
        private const val MAX_LOGICAL_OWNERS = 128
        private const val SOON_MS = 100L
        private const val PULSE_MS = 1000L
        private fun queue(): (Long, () -> Unit) -> (() -> Unit) {
            val executor = Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "phone-work-leases").apply { isDaemon = true }
            }
            return { delay, task ->
                val future = executor.schedule({ task() }, delay, TimeUnit.MILLISECONDS)
                val cancel: () -> Unit = { future.cancel(false); Unit }
                cancel
            }
        }
        private fun androidWake(context: Context): Wake? {
            val lock = context.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenCode:work") ?: return null
            lock.setReferenceCounted(false)
            return object : Wake {
                override val isHeld: Boolean get() = lock.isHeld
                override fun acquire(timeoutMs: Long) = lock.acquire(timeoutMs)
                override fun release() = lock.release()
            }
        }
    }
}
