package io.github.eslamasabry.opencode_mobile

/** Warm admission only: the caller holds the installer lock and supplies exact ownership proof. */
internal object NativeInstallerAdmission {
    /** The installer ticket read first, and whether the cached owner is still alive. */
    data class Claim(val ticket: InstallerTicket?, val ownerLive: Boolean)

    fun reclaim(
        claim: Claim,
        ownerCurrent: () -> Boolean,
        readTicket: () -> InstallerTicket?,
        quiescent: (InstallerTicket) -> Boolean,
        clearTicket: (InstallerTicket) -> Boolean,
    ): Boolean {
        val ticket = claim.ticket
        // Exit or missing /proc entries alone cannot authorize clearing a writer.
        // Re-evaluate the complete UID inventory immediately before the durable change.
        return ticket != null && !claim.ownerLive && ownerCurrent() &&
            readTicket() == ticket && quiescent(ticket) &&
            ownerCurrent() && readTicket() == ticket &&
            quiescent(ticket) && ownerCurrent() && readTicket() == ticket &&
            clearTicket(ticket)
    }
}
