package io.github.eslamasabry.opencode_mobile

/** A missing /proc entry never proves that a recorded installer process died. */
internal object NativeInstallerVisibility {
    private const val SAFE = "ownershipUnknown"

    fun requireMissingGone(known: List<RuntimeProcessIdentity>, inventory: List<RuntimeProcessIdentity>,
        confirmedGone: (Int) -> Boolean) {
        require(known.size <= 130 && inventory.size <= 128) { SAFE }
        val current = inventory.associateBy { it.pid }
        require(current.size == inventory.size) { SAFE }
        val identities = linkedMapOf<Int, RuntimeProcessIdentity>()
        for (identity in known) {
            val previous = identities[identity.pid]
            require(previous == null || previous.sameProcess(identity)) { SAFE }
            identities[identity.pid] = identity
        }
        // Refuse every reused PID before performing even an absence probe for
        // another identity. Registered readers cannot excuse PID reuse.
        for (identity in identities.values) {
            val visible = current[identity.pid]
            require(visible == null || identity.sameProcess(visible)) { SAFE }
        }
        for (identity in identities.values) if (identity.pid !in current) {
            val gone = try { confirmedGone(identity.pid) } catch (_: Throwable) { false }
            require(gone) { SAFE }
        }
    }
}
