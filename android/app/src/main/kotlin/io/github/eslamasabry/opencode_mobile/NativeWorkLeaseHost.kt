package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** CPU ownership only. A lease never overrides Android foreground-service policy. */
internal class NativeWorkLeaseHost(
    private val leases: WorkLeases, private val now: () -> Long, private val wake: Wake?,
    private val terminalWork: () -> Map<Int, Boolean>, private val protectTerminal: () -> Boolean,
    private val changed: () -> Unit, private val enqueue: (Long, () -> Unit) -> (() -> Unit),
) {
    interface Wake { val isHeld: Boolean; fun acquire(timeoutMs: Long); fun release() }
    constructor(context: Context, terminalWork: () -> Map<Int, Boolean>,
        protectTerminal: () -> Boolean, changed: () -> Unit) : this(
        WorkLeases(SystemClock::elapsedRealtime), SystemClock::elapsedRealtime, androidWake(context),
        terminalWork, protectTerminal, changed, queue())
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
    fun chat(name: String, on: Boolean, holdMs: Long, serverRunning: Boolean): Map<String, Boolean> = synchronized(guard) {
        if (!validChatName(name)) return@synchronized mapOf("held" to false, "capped" to false)
        setChat(namedChats, name, on, holdMs, serverRunning)
    }

    fun agentChat(profile: String, name: String, on: Boolean, holdMs: Long,
        helperRunning: Boolean): Map<String, Boolean> = synchronized(guard) {
        if (!validProfile(profile) || !validChatName(name)) return@synchronized mapOf("held" to false, "capped" to false)
        setChat(agentChats, AgentChat(profile, name), on, holdMs, helperRunning)
    }

    /** Caller holds guard. Chat scopes share one logical owner bound with native work. */
    private fun <K> setChat(chats: MutableMap<K, Long?>, key: K, on: Boolean,
        holdMs: Long, running: Boolean): Map<String, Boolean> {
        if (!on) {
            chats.remove(key)?.let { leases.release(it) }
            updateWake()
            return mapOf("held" to false, "capped" to false)
        }
        if (!running || holdMs <= 0) {
            chats[key]?.let { leases.release(it) }
            if (chats.containsKey(key)) chats[key] = null
            updateWake()
            return mapOf("held" to false, "capped" to false)
        }
        if (!chatAdmissionOpen) {
            chats[key]?.let { leases.release(it) }
            val retained = chats.containsKey(key)
            if (retained) chats[key] = null
            updateWake()
            return mapOf("held" to false, "capped" to retained)
        }
        if (!chats.containsKey(key)) {
            if (logicalOwnerCount() >= 128) return mapOf("held" to false, "capped" to false)
            chats[key] = leases.acquire(WorkLeases.Kind.CHAT, holdMs)
        } else chats[key]?.let { leases.renew(it, holdMs) }
        val token = chats[key]
        val admitted = token != null && leases.contains(token)
        updateWake(); schedule(100)
        return mapOf("held" to (admitted && wake?.isHeld == true), "capped" to !admitted)
    }

    fun adopt(owner: Any, kind: WorkLeases.Kind, alive: () -> Boolean) = synchronized(guard) {
        if (owners.containsKey(owner) || logicalOwnerCount() >= 128) return@synchronized
        owners[owner] = Owner(leases.acquire(kind, HOLD_MS), alive, now() + RENEW_MS)
        updateWake(); schedule(100)
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
        val running = try { terminalWork() } catch (_: Throwable) { null }
        val liveness = captured.mapValues { (_, owner) -> try { owner.alive() } catch (_: Throwable) { null } }
        return synchronized(guard) {
            if (namedChats.isNotEmpty() || agentChats.isNotEmpty() || running?.isNotEmpty() == true) return@synchronized true
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
        listOf(WorkLeases.Kind.CHAT, WorkLeases.Kind.SIGN_IN, WorkLeases.Kind.TERMINAL).forEach { leases.releaseKind(it) }
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
    fun observeTerminalsSoon() = synchronized(guard) { schedule(100) }

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
    internal fun pulse() {
        // Read external lifecycle owners outside our guard: terminal creation holds its own monitor.
        val running = try { terminalWork() } catch (_: Throwable) { null }
        val captured = synchronized(guard) { owners.toMap() }
        val liveness = captured.mapValues { (_, owner) -> try { owner.alive() } catch (_: Throwable) { null } }
        val ended = liveness.filterValues { it == false }.keys
        val unknown = liveness.filterValues { it == null }.keys
        val revision = synchronized(guard) { foregroundRevision }
        val added = synchronized(guard) { running?.keys?.filter { it !in terminals }
            .orEmpty().take((128 - logicalOwnerCount()).coerceAtLeast(0)) }
        val protected = added.isEmpty() || try { protectTerminal() } catch (_: Throwable) { false }
        synchronized(guard) {
            cancelScheduled?.invoke(); cancelScheduled = null; scheduled = false; scheduleRevision++
            unknown.forEach { owner -> if (owners[owner] === captured[owner]) owners[owner]?.token?.let { leases.release(it) } }
            if (running == null) terminals.forEach { owners["terminal.$it"]?.token?.let { token -> leases.release(token) } }
            ended.forEach { owner -> if (owners[owner] === captured[owner]) release(owner) }
            terminals.filter { running != null && it !in running }.toList().forEach { id -> release("terminal.$id"); terminals.remove(id) }
            added.take((128 - logicalOwnerCount()).coerceAtLeast(0)).forEach { id ->
                terminals.add(id)
                val kind = if (running?.get(id) == true) WorkLeases.Kind.SIGN_IN else WorkLeases.Kind.TERMINAL
                // Retain a refused/capped owner until that exact session ends, never reacquire it.
                owners["terminal.$id"] = Owner(if (protected && foregroundRevision == revision) leases.acquire(kind, HOLD_MS) else null,
                    { id in terminalWork() }, now() + RENEW_MS)
            }
            val now = now()
            owners.values.forEach { owner ->
                if (now >= owner.renewAt) { owner.token?.let { leases.renew(it, HOLD_MS) }; owner.renewAt = now + RENEW_MS }
            }
            updateWake()
            val next = leases.snapshot().nextExpiryInMs
            if (owners.isNotEmpty()) schedule(1000)
            else if (next != null) schedule(next.coerceIn(1, HOLD_MS))
        }
        try { changed() } catch (_: Throwable) { }
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
