package io.github.eslamasabry.opencode_mobile

import android.os.SystemClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

private const val BOOT = "11111111-1111-1111-1111-111111111111"
private val ROOTFS = "a".repeat(64)
private fun identity(pid: Int, parent: Int = 1, session: Int = pid) =
    RuntimeProcessIdentity(pid, 10, parent, session, session)
private fun prepared() = InstallerTicket("b".repeat(64), ROOTFS, InstallerTarget.entries.toSet(),
    InstallerOperation.CHECK, NativeRuntimeReceipt(BOOT, "c".repeat(64), 7, null, null, emptyList()))
private fun committed() = NativeInstallerOwnership.committed(prepared(), identity(20), identity(21, 20))

private class FixtureProcess(private var live: Boolean) : Process() {
    override fun isAlive() = live
    override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf())
    override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
    override fun waitFor(): Int { live = false; return 0 }
    override fun exitValue(): Int { check(!live); return 0 }
    override fun destroy() { error("admission must never signal a process") }
    override fun destroyForcibly(): Process = error("admission must never signal a process")
}

private class BuiltinLinux(private val scenario: String) {
    private val installed = true
    private val componentUpdateFailure = "A component update could not be restored. Run setup again."
    private val installerGuard = Any()
    private var installerProcess: Process? = when {
        scenario.contains("cached") -> FixtureProcess(false)
        scenario.contains("living") -> FixtureProcess(true)
        else -> null
    }
    private var ticket: InstallerTicket? = when {
        scenario.startsWith("normal") -> null
        scenario.contains("prepared") -> prepared().copy(operation = InstallerOperation.INSTALL)
        scenario.contains("install") -> committed().copy(operation = InstallerOperation.INSTALL)
        else -> committed()
    }
    private var installerLaunchId: String? = installerProcess?.let { ticket!!.id }
    private var componentUpdatesRecovered = !scenario.contains("cold")
    private var pendingJournal = scenario.contains("install") && !scenario.startsWith("normal")
    private val savedGeneration = 7L
    private val savedRootfs = ROOTFS
    var proofs = 0
    var clears = 0
    var rollbacks = 0
    var admissions = 0
    var leasesReleased = 0
    var admittedTargets: Set<InstallerTarget>? = null
    var admittedOperation: InstallerOperation? = null
    private val workLeases = object {
        fun release(process: Process) {
            check(!process.isAlive)
            leasesReleased++
        }
    }
    private val installerPreferences = object {
        fun edit() = Editor()
    }
    private inner class Editor {
        private var removeTicket = false
        fun remove(key: String): Editor {
            check(key == "ticket") { "admission must preserve generation and rootfs" }
            removeTicket = true
            return this
        }
        fun commit(): Boolean {
            check(removeTicket)
            if (scenario.contains("clear-failed")) return false
            ticket = null
            clears++
            return true
        }
    }
    private fun installerTicket() = ticket
    private fun installerPlan(candidate: InstallerTicket): NativeRuntimeOwnership.Drain {
        proofs++
        val inventory = when {
            scenario.contains("orphan") -> listOf(identity(22, session = 21))
            scenario.contains("unknown") -> listOf(identity(99))
            else -> emptyList()
        }
        return NativeInstallerOwnership.drainPlan(candidate, ROOTFS, BOOT, inventory, emptySet(),
            { !scenario.contains("uncertain") }, { _, nonce -> scenario.contains("orphan") && nonce == candidate.ownership.nonce })
    }
    private fun liveInstallerPlan(candidate: InstallerTicket, expected: Process, launchId: String)
        : NativeRuntimeOwnership.Drain {
        check(installerProcess === expected && installerLaunchId == launchId && candidate.id == launchId)
        return installerPlan(candidate)
    }
    private fun recoverColdComponentUpdates() {
        if (componentUpdatesRecovered) return
        check(installerProcess == null)
        // Durable stale tickets must have been reclaimed by the production hook;
        // the stub deliberately cannot hide a missing hook with cold cleanup.
        check(ticket == null)
        if (pendingJournal) { pendingJournal = false; rollbacks++ }
        componentUpdatesRecovered = true
    }
    private fun recordAdmission(targets: Set<InstallerTarget>, operation: InstallerOperation): Process {
        check(ticket == null && installerProcess == null && componentUpdatesRecovered)
        check(!pendingJournal) { "pending INSTALL journal must recover before another guest command" }
        check(savedGeneration == 7L && savedRootfs == ROOTFS)
        admissions++
        admittedTargets = targets
        admittedOperation = operation
        return FixtureProcess(true).also { installerProcess = it }
    }
    private fun startInstaller(script: String, targets: Set<InstallerTarget>, operation: InstallerOperation,
        agentUser: Boolean) = startQualifiedInstaller(script, targets, operation, agentUser)
    fun attempt(): Process = if (scenario.startsWith("check") || scenario == "normal-check")
        admitCheckProcess("fixed-check", 50, SystemClock.elapsedRealtime(), true)
        else startQualifiedInstaller("fixed-install", setOf(InstallerTarget.CLAUDE), InstallerOperation.INSTALL, true)
    fun coldReclaim() = reclaimDeadInstaller()
    fun hasTicket() = ticket != null
    fun cachedOwnerPresent() = installerProcess != null
    fun hasPendingJournal() = pendingJournal
    fun metadataIntact() = savedGeneration == 7L && savedRootfs == ROOTFS
    /* PRODUCTION_RECLAIM */
    /* PRODUCTION_CHECK_ADMISSION */
    /* PRODUCTION_INSTALLER_ADMISSION */
    companion object {
        /* PRODUCTION_CONSTANTS */
    }
}

fun main(args: Array<String>) {
    val scenario = args.single()
    val linux = BuiltinLinux(scenario)
    if (scenario.startsWith("cold")) {
        check(!linux.coldReclaim())
        check(linux.hasTicket() && linux.proofs == 0 && linux.clears == 0)
        check(linux.hasPendingJournal() == scenario.contains("install"))
    } else {
        val admitted = runCatching { linux.attempt() }
        val refusal = listOf("living", "orphan", "unknown", "uncertain", "clear-failed")
            .any { scenario.contains(it) }
        if (refusal) {
            check(admitted.isFailure) { "unsafe installer owner was admitted" }
            check(linux.hasTicket() && linux.clears == 0 && linux.admissions == 0)
            check(linux.leasesReleased == 0 && linux.rollbacks == 0)
            if (scenario.contains("cached")) check(linux.cachedOwnerPresent())
        } else {
            check(admitted.getOrThrow().isAlive)
            check(!linux.hasTicket() && linux.admissions == 1)
            if (!scenario.startsWith("normal")) {
                check(linux.clears == 1 && linux.proofs == 2) { "dead ticket was not twice proved quiescent" }
                check(linux.leasesReleased == if (scenario.contains("cached")) 1 else 0)
                check(linux.rollbacks == if (scenario.startsWith("install")) 1 else 0)
            }
            check(linux.admittedOperation == if (scenario.contains("check")) InstallerOperation.CHECK else InstallerOperation.INSTALL)
            check(linux.admittedTargets == if (scenario.contains("check")) InstallerTarget.entries.toSet() else setOf(InstallerTarget.CLAUDE))
        }
    }
    check(linux.metadataIntact())
    println("PASS $scenario")
}
