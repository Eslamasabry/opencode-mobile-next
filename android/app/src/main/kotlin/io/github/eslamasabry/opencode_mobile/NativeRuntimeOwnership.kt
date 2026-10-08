package io.github.eslamasabry.opencode_mobile

private const val PROC_STAT_MIN_FIELDS = 20
private const val PROC_STAT_START_TICKS_INDEX = 19
private const val PROC_STAT_SESSION_INDEX = 3
private const val MAX_OTHER_RUNTIME_IDENTITIES = 128
private const val MAX_STICKY_ATTEMPTS = 3

/** Kernel identity is never a PID alone. These records contain no argv, output or credentials. */
internal data class RuntimeProcessIdentity(
    val pid: Int, val startTicks: Long, val parent: Int, val group: Int, val session: Int,
) {
    // Android app/PRoot roots can inherit session zero; gate leaders have a separate strict proof.
    init { require(pid > 1 && startTicks > 0 && parent >= 0 && group > 0 && session >= 0) }
    fun sameProcess(other: RuntimeProcessIdentity?) = other?.pid == pid && other.startTicks == startTicks
    fun map(): Map<String, Any> = mapOf("pid" to pid, "startTicks" to startTicks,
        "parent" to parent, "group" to group, "session" to session)
    companion object {
        fun stat(value: String): RuntimeProcessIdentity {
            val end = value.lastIndexOf(')')
            require(end > value.indexOf('('))
            val pid = value.substringBefore(' ').toInt()
            val fields = value.substring(end + 1).trim().split(Regex("\\s+"))
            require(fields.size >= PROC_STAT_MIN_FIELDS)
            return RuntimeProcessIdentity(pid, fields[PROC_STAT_START_TICKS_INDEX].toLong(), fields[1].toInt(),
                fields[2].toInt(), fields[PROC_STAT_SESSION_INDEX].toInt())
        }
        fun read(value: Map<*, *>): RuntimeProcessIdentity {
            require(value.keys == setOf("pid", "startTicks", "parent", "group", "session"))
            fun number(key: String): Long {
                val item = value[key]; require(item is Int || item is Long)
                return (item as Number).toLong()
            }
            fun int(key: String): Int { val n = number(key); require(n in 0..Int.MAX_VALUE.toLong()); return n.toInt() }
            return RuntimeProcessIdentity(int("pid"), number("startTicks"), int("parent"), int("group"), int("session"))
        }
    }
}

internal data class NativeRuntimeReceipt(
    val boot: String, val nonce: String, val generation: Long,
    val root: RuntimeProcessIdentity?, val leader: RuntimeProcessIdentity?,
    val other: List<RuntimeProcessIdentity>,
) {
    init {
        require(Regex("[0-9a-f-]{36}").matches(boot))
        require(Regex("[0-9a-f]{64}").matches(nonce) && generation > 0)
        require(other.size <= MAX_OTHER_RUNTIME_IDENTITIES && other.map { it.pid }.distinct().size == other.size)
        require((root == null) == (leader == null))
        if (leader != null) require(leader.pid == leader.session && leader.pid == leader.group)
    }
    val prepared get() = root == null
    fun map(): Map<String, Any?> = mapOf("version" to 1, "boot" to boot, "nonce" to nonce,
        "generation" to generation, "root" to root?.map(), "leader" to leader?.map(),
        "other" to other.map { it.map() })
    companion object {
        fun read(value: Map<*, *>): NativeRuntimeReceipt {
            require(value.keys == setOf("version", "boot", "nonce", "generation", "root", "leader", "other"))
            require(value["version"] == 1 || value["version"] == 1L)
            val generation = value["generation"]; require(generation is Int || generation is Long)
            val other = value["other"] as? List<*> ?: error("ownershipUnknown")
            return NativeRuntimeReceipt(value["boot"] as String, value["nonce"] as String,
                (generation as Number).toLong(),
                value["root"]?.let { RuntimeProcessIdentity.read(it as Map<*, *>) },
                value["leader"]?.let { RuntimeProcessIdentity.read(it as Map<*, *>) },
                other.map { RuntimeProcessIdentity.read(it as Map<*, *>) })
        }
    }
}

/** Pure admission: all same-UID children must have a positive, current ownership proof. */
internal object NativeRuntimeOwnership {
    /** A cold worker cannot adopt a later Start or clear another worker's schedule. */
    data class ColdWorker(val generation: Long, val scheduleId: Long) {
        fun runIfCurrent(currentGeneration: Long, currentScheduleId: Long, action: () -> Unit): Boolean {
            if (generation != currentGeneration || scheduleId != currentScheduleId) return false
            action()
            return true
        }
        fun runIfIdle(currentGeneration: Long, currentScheduleId: Long, liveServer: Boolean,
            action: () -> Unit): Boolean = !liveServer && runIfCurrent(currentGeneration, currentScheduleId, action)
    }
    fun denialMayDrain(receiptGeneration: Long, processBirthGeneration: Long, liveServer: Boolean) =
        receiptGeneration > 0 && receiptGeneration <= processBirthGeneration && !liveServer
    fun revocationMayCommit(expectedRevision: Long?, currentRevision: Long) =
        expectedRevision != null && expectedRevision > 0L && expectedRevision == currentRevision
    fun manualRecipeEligible(requestProfile: String?, owner: String?, enabled: Boolean,
        migrationValid: Boolean, policyAllows: Boolean) =
        requestProfile != null && requestProfile == owner && enabled && migrationValid && policyAllows
    fun stickyAllowed(recipeValid: Boolean, identityCommitted: Boolean, admitted: Boolean,
        attempts: Int, liveServer: Boolean) = recipeValid && identityCommitted && admitted &&
        attempts in 0..MAX_STICKY_ATTEMPTS && (attempts < MAX_STICKY_ATTEMPTS || liveServer)
    fun manualFallbackAllowed(released: Boolean, drained: Boolean, wanted: Boolean) = !released && drained && wanted
    data class Drain(val server: List<RuntimeProcessIdentity>, val other: Set<Int>)
    fun plan(receipt: NativeRuntimeReceipt, boot: String,
        current: List<RuntimeProcessIdentity>, registered: Set<Int>,
        requireCompleteInventory: Boolean = true, nonceMatches: (Int, String) -> Boolean,
    ): Drain {
        require(receipt.boot == boot) { "ownershipUnknown" }
        val byPid = current.associateBy { it.pid }
        receipt.root?.let { root ->
            require(byPid[root.pid] == null || root.sameProcess(byPid[root.pid])) { "ownershipUnknown" }
        }
        receipt.leader?.let { leader ->
            require(byPid[leader.pid] == null || leader.sameProcess(byPid[leader.pid])) { "ownershipUnknown" }
        }
        val knownOther = receipt.other.filter { it.sameProcess(byPid[it.pid]) }.map { it.pid }.toMutableSet()
        expand(current, knownOther)
        val server = mutableSetOf<Int>()
        receipt.root?.takeIf { it.sameProcess(byPid[it.pid]) }?.let { server.add(it.pid) }
        receipt.leader?.let { leader ->
            for (p in current) if (p.session == leader.session && nonceMatches(p.pid, receipt.nonce)) server.add(p.pid)
        }
        expand(current, server)
        require(server.intersect(knownOther).isEmpty()) { "ownershipUnknown" }
        require(!requireCompleteInventory || current.all {
            it.pid in registered || it.pid in server || it.pid in knownOther
        }) { "ownershipUnknown" }
        // A prepared gate has no executable workload. Unknown survivors still fail closed above.
        return Drain(
            current.filter { it.pid in server }.sortedBy { if (it.pid == receipt.root?.pid) 1 else 0 }, knownOther,
        )
    }

    private fun expand(current: List<RuntimeProcessIdentity>, roots: MutableSet<Int>) {
        var changed: Boolean
        do {
            changed = false
            for (process in current) {
                if (process.parent in roots && roots.add(process.pid)) changed = true
            }
        } while (changed)
    }
}

/** A partially written permit may already have executed; it can never enable legacy fallback. */
internal class NativeRuntimeGate {
    private var identityDurable = false
    var released = false
        private set
    fun identityCommitted() { check(!released); identityDurable = true }
    fun release(writePermit: () -> Unit) {
        check(identityDurable && !released) { "ownershipUnknown" }
        released = true
        writePermit()
    }
}
