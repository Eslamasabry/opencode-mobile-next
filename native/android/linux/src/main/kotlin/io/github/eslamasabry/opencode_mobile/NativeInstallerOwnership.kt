package io.github.eslamasabry.opencode_mobile

private const val MAX_INSTALLER_IDENTITIES = 128

internal enum class InstallerOperation { CHECK, INSTALL }
internal enum class InstallerTarget { OPENCODE1, OPENCODE2, PASEO, CLAUDE, LEGACY_CLAUDE }

/** Private lifecycle metadata only: never persist a launch script, output or account values. */
internal data class InstallerTicket(
    val id: String,
    val rootfsGeneration: String,
    val targets: Set<InstallerTarget>,
    val operation: InstallerOperation,
    val ownership: NativeRuntimeReceipt,
    val observed: List<RuntimeProcessIdentity> = emptyList(),
) {
    init {
        require(HEX.matches(id) && HEX.matches(rootfsGeneration)) { SAFE }
        require(targets.isNotEmpty() && targets.size <= InstallerTarget.entries.size) { SAFE }
        require(BOOT.matches(ownership.boot)) { SAFE }
        require(observed.size <= MAX_INSTALLER_IDENTITIES && observed.map { it.pid }.distinct().size == observed.size) {
            SAFE
        }
        require(!ownership.prepared || observed.isEmpty()) { SAFE }
        require(observed.none { observation -> ownership.other.any { it.pid == observation.pid } }) { SAFE }
        for (identity in listOfNotNull(ownership.root, ownership.leader)) {
            require(observed.none { it.pid == identity.pid && !identity.sameProcess(it) }) { SAFE }
        }
    }

    fun map(): Map<String, Any?> = mapOf(
        "version" to 1, "id" to id, "rootfsGeneration" to rootfsGeneration,
        "targets" to targets.sortedBy { it.ordinal }.map { it.name }, "operation" to operation.name,
        "ownership" to ownership.map(), "observed" to observed.map { it.map() },
    )

    companion object {
        private const val SAFE = "ownershipUnknown"
        private val HEX = Regex("[0-9a-f]{64}")
        private val BOOT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        fun read(value: Map<*, *>): InstallerTicket = try {
            require(value.keys == setOf(
                "version", "id", "rootfsGeneration", "targets", "operation", "ownership", "observed",
            )) { SAFE }
            val version = value["version"]
            require((version is Int || version is Long) && (version as Number).toLong() == 1L) { SAFE }
            val targetNames = value["targets"] as List<*>
            require(targetNames.isNotEmpty() && targetNames.size <= InstallerTarget.entries.size &&
                targetNames.distinct().size == targetNames.size) { SAFE }
            val identities = value["observed"] as List<*>
            require(identities.size <= MAX_INSTALLER_IDENTITIES) { SAFE }
            InstallerTicket(value["id"] as String, value["rootfsGeneration"] as String,
                targetNames.map { InstallerTarget.valueOf(it as String) }.toSet(),
                InstallerOperation.valueOf(value["operation"] as String),
                NativeRuntimeReceipt.read(value["ownership"] as Map<*, *>),
                identities.map { RuntimeProcessIdentity.read(it as Map<*, *>) })
        } catch (_: Exception) { throw IllegalArgumentException(SAFE) }
    }
}

/** The integration supplies a complete same-UID inventory and validates UID at every signal. */
internal object NativeInstallerOwnership {
    private const val SAFE = "ownershipUnknown"

    fun targetsForComponent(id: String): Set<InstallerTarget> = when (id) {
        "opencode" -> setOf(InstallerTarget.OPENCODE1, InstallerTarget.OPENCODE2)
        "agent-paseo" -> setOf(InstallerTarget.PASEO)
        "agent-claude" -> setOf(InstallerTarget.CLAUDE)
        else -> InstallerTarget.entries.toSet()
    }

    /** Header/session/ancestry and UID are proved by the native gate before this transition. */
    fun committed(
        ticket: InstallerTicket, root: RuntimeProcessIdentity, leader: RuntimeProcessIdentity,
    ): InstallerTicket {
        require(ticket.ownership.prepared && root.pid != leader.pid &&
            leader.pid == leader.session && leader.pid == leader.group) { SAFE }
        return ticket.copy(ownership = ticket.ownership.copy(root = root, leader = leader))
    }

    /** Ephemeral live-plan input only. Never persist these additional reader exclusions. */
    fun withCurrentRuntimePeers(ticket: InstallerTicket, peers: List<RuntimeProcessIdentity>,
        inventory: List<RuntimeProcessIdentity>): InstallerTicket {
        return ticket.copy(ownership = withCurrentRuntimePeers(ticket.ownership, ticket.observed, peers, inventory))
    }

    /** Shared live-only receipt policy. The caller proves peers through current tracked native processes. */
    fun withCurrentRuntimePeers(receipt: NativeRuntimeReceipt, observed: List<RuntimeProcessIdentity>,
        peers: List<RuntimeProcessIdentity>, inventory: List<RuntimeProcessIdentity>): NativeRuntimeReceipt {
        require(observed.size <= MAX_INSTALLER_IDENTITIES && observed.map { it.pid }.distinct().size == observed.size) {
            SAFE
        }
        require(!receipt.prepared || observed.isEmpty()) { SAFE }
        require(observed.none { observation -> receipt.other.any { it.pid == observation.pid } }) { SAFE }
        for (identity in listOfNotNull(receipt.root, receipt.leader)) {
            require(observed.none { it.pid == identity.pid && !identity.sameProcess(it) }) { SAFE }
        }
        require(peers.size <= MAX_INSTALLER_IDENTITIES) { SAFE }
        require(inventory.size <= MAX_INSTALLER_IDENTITIES &&
            inventory.map { it.pid }.distinct().size == inventory.size) { SAFE }
        require(!receipt.prepared || peers.isEmpty()) { SAFE }
        val current = inventory.associateBy { it.pid }
        // This check precedes peer admission, so a reused observation cannot become a reader.
        for (observation in observed) {
            require(current[observation.pid] == null || observation.sameProcess(current[observation.pid])) { SAFE }
        }
        val ownedIdentities = listOfNotNull(receipt.root, receipt.leader) + observed
        val forbidden = ownedIdentities.map { it.pid }.toMutableSet()
        val installer = ownedIdentities.filter { it.sameProcess(current[it.pid]) }.map { it.pid }.toMutableSet()
        expand(inventory, installer)
        forbidden.addAll(installer)
        val other = receipt.other.associateBy { it.pid }.toMutableMap()
        for (peer in peers) {
            val exact = current[peer.pid]
            require(peer.sameProcess(exact)) { SAFE }
            // Use the current kernel shape, never stale caller parent/session fields.
            require(exact!!.pid !in forbidden && exact.session != receipt.leader?.session) { SAFE }
            val old = other[exact.pid]
            require(old == null || old.sameProcess(exact)) { SAFE }
            other[exact.pid] = exact
        }
        require(other.size <= MAX_INSTALLER_IDENTITIES) { SAFE }
        return receipt.copy(other = other.values.toList())
    }

    fun drainPlan(ticket: InstallerTicket, rootfsGeneration: String, boot: String,
        inventory: List<RuntimeProcessIdentity>, registeredReaders: Set<Int>,
        absenceConfirmed: (Int) -> Boolean, nonceMatches: (Int, String) -> Boolean,
    ): NativeRuntimeOwnership.Drain {
        require(ticket.rootfsGeneration == rootfsGeneration && ticket.ownership.boot == boot) { SAFE }
        require(inventory.size <= MAX_INSTALLER_IDENTITIES &&
            inventory.map { it.pid }.distinct().size == inventory.size) { SAFE }
        NativeInstallerVisibility.requireMissingGone(
            listOfNotNull(ticket.ownership.root, ticket.ownership.leader) + ticket.observed,
            inventory, absenceConfirmed)
        val current = inventory.associateBy { it.pid }
        // A reused observed PID is never converted into ownership, even when listed as a reader.
        for (observation in ticket.observed) {
            require(current[observation.pid] == null || observation.sameProcess(current[observation.pid])) { SAFE }
        }
        val base = NativeRuntimeOwnership.plan(ticket.ownership, boot, inventory, registeredReaders,
            requireCompleteInventory = false, nonceMatches = nonceMatches)
        val installer = base.server.map { it.pid }.toMutableSet()
        installer.addAll(ticket.observed.filter { it.sameProcess(current[it.pid]) }.map { it.pid })
        expand(inventory, installer)
        require(installer.intersect(base.other).isEmpty() && installer.intersect(registeredReaders).isEmpty()) { SAFE }
        require(inventory.all { it.pid in installer || it.pid in base.other || it.pid in registeredReaders }) { SAFE }
        // Completion requires a fresh plan with server.isEmpty(), not a missing parent or exit code.
        return NativeRuntimeOwnership.Drain(inventory.filter { it.pid in installer }
            .sortedBy { if (it.pid == ticket.ownership.root?.pid) 1 else 0 }, base.other)
    }

    private fun expand(inventory: List<RuntimeProcessIdentity>, installer: MutableSet<Int>) {
        var changed: Boolean
        do {
            changed = false
            for (identity in inventory) {
                if (identity.parent in installer && installer.add(identity.pid)) changed = true
            }
        } while (changed)
    }
}
