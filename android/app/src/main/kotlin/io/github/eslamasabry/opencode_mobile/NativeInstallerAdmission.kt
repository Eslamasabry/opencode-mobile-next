package io.github.eslamasabry.opencode_mobile

/** Warm admission only: the caller holds the installer lock and supplies exact ownership proof. */
internal object NativeInstallerAdmission {
    fun reclaim(
        ticket: InstallerTicket?,
        ownerLive: Boolean,
        ownerCurrent: () -> Boolean,
        readTicket: () -> InstallerTicket?,
        quiescent: (InstallerTicket) -> Boolean,
        clearTicket: (InstallerTicket) -> Boolean,
    ): Boolean {
        if (ticket == null || ownerLive || !ownerCurrent()) return false
        if (readTicket() != ticket || !quiescent(ticket)) return false
        if (!ownerCurrent() || readTicket() != ticket) return false
        // Exit or missing /proc entries alone cannot authorize clearing a writer.
        // Re-evaluate the complete UID inventory immediately before the durable change.
        if (!quiescent(ticket) || !ownerCurrent() || readTicket() != ticket) return false
        return clearTicket(ticket)
    }
}
