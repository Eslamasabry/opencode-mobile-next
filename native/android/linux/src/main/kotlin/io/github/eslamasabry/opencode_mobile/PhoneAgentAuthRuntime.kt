package io.github.eslamasabry.opencode_mobile

import android.app.ActivityManager
import android.content.Context
import android.os.Process as AndroidProcess
import android.system.Os
import java.io.File
import java.util.concurrent.TimeUnit

/** Android glue only: no account files, output receipts or diagnostic logging. */
internal class PhoneAgentAuthRuntime(
    context: Context,
    launch: (String, List<String>) -> Process,
    clearLock: (String, () -> Boolean) -> Unit,
    otherAgentOwners: () -> Set<Pair<Int, String>> = { emptySet() },
    /**
     * signin-truth: whether a Claude Code CLI of this profile's agents runs now (a conversation, a sign-in terminal).
     * Its own work is the evidence then, and a check beside it could refresh the same sign-in at the same moment: no
     * check starts, and the app keeps the last answer.
     */
    private val claudeBusy: (String) -> Boolean = { false },
    /** How long ago [profile]'s stored Claude sign-in was written, or null (see [PhoneAgentAuthHoldBack]). */
    private val claudeSignInAge: (String) -> Long? = { null },
) {
    private companion object {
        const val POLL_MILLIS = 20L
        const val CLEANUP_SECONDS = 3L
    }
    private val deadline = ThreadLocal<PhoneAgentAuthDeadline>()
    private val before = ThreadLocal<Set<Pair<Int, String>>>()
    private val processInventory = PhoneAgentAuthInventory(
        { checkNotNull(File("/proc").listFiles()).toList() }, ::sameUid)
    private val tree = PhoneAgentAuthProcessTree(::processId, ::inventory, { pid, signal ->
        try { Os.kill(pid, signal) } catch (_: android.system.ErrnoException) { /* Exit raced the signal. */ }
    }, outsideOwners = otherAgentOwners)
    private val probe = PhoneAgentAuthProbe(
        start = { profile, argv ->
            val process = launch(profile, argv)
            // Even incomplete startup remains in the exact owner ledger.
            tree.track(process, before.get())
            process
        },
        stop = { tree.stop(it, checkNotNull(deadline.get()).drainNanos()) },
        prepare = { profile, agent ->
            val home = PhoneAgentPaths.resolve(context.filesDir, "linux/ubuntu/home/oc/.oc-profiles/$profile")
            check(home.isDirectory)
            if (agent == "claude") clearLock(profile) { coldQuiescent(context) }
            before.set(inventory().map { it.pid to it.start }.toSet())
            true
        },
        waitFor = { process, seconds -> waitTracked(process, seconds) },
        // A drain asked for by a stopped read runs on that step's thread, with the same cleanup budget as a block.
        elsewhere = { drain ->
            deadline.set(PhoneAgentAuthDeadline(CLEANUP_SECONDS))
            try { drain() } finally { deadline.remove() }
        },
    )

    /**
     * [afterSignIn]: the sign-in page's check right after the agent's own sign-in ended; it may run beside a loaded
     * Claude Code CLI when that sign-in was just written ([PhoneAgentAuthHoldBack]).
     */
    fun run(arguments: Map<*, *>, stop: ProbeStop? = null, afterSignIn: Boolean = false): Map<String, Any?> {
        val request = PhoneAgentAuthRequest.parse(arguments) ?: return PhoneAgentAuthProjection.error("invalidContext")
        // signin-truth-2: said as its own answer ("agentBusy"), so the app keeps the last answer and says why; before,
        // it read as "the phone did not answer" and the agent as "Sign-in not confirmed" for as long as a Claude Code
        // conversation stayed loaded.
        val heldBack = PhoneAgentAuthHoldBack.holdsBack(
            request.agentId,
            { claudeBusy(request.profileId) },
            afterSignIn,
            { claudeSignInAge(request.profileId) },
        )
        if (heldBack) return PhoneAgentAuthProjection.error("agentBusy")
        deadline.set(PhoneAgentAuthDeadline(request.timeoutSeconds))
        return try { probe.run(arguments, stop) }
        finally { deadline.remove(); before.remove() }
    }
    fun blockProfile(profileId: String): Boolean {
        deadline.set(PhoneAgentAuthDeadline(CLEANUP_SECONDS))
        return try { probe.blockProfile(profileId) } finally { deadline.remove() }
    }

    private fun waitTracked(process: Process, seconds: Long): Boolean {
        check(seconds > 0)
        val budget = checkNotNull(deadline.get())
        do {
            tree.capture(process)
            if (process.waitFor(minOf(POLL_MILLIS, budget.executionMillis()), TimeUnit.MILLISECONDS)) {
                tree.capture(process)
                return true
            }
        } while (budget.executionMillis() > 0)
        return false
    }

    private fun processId(process: Process): Int? = try {
        process.javaClass.getDeclaredField("pid").run { isAccessible = true; getInt(process) }
    } catch (_: Exception) { null }

    private fun inventory(): List<PhoneAgentAuthProcessIdentity> = processInventory.snapshot()

    /**
     * The cold-start process census. Conservative across app restart: any other same-UID task keeps the lock. Also what
     * a helper start reads before it clears the lock (BuiltinLinux.clearStaleAgentLoginLock).
     */
    fun coldQuiescent(): Boolean = coldQuiescent(appContext)

    private val appContext = context

    private fun coldQuiescent(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java)
        val registered = manager?.runningAppProcesses.orEmpty().filter {
            it.uid == AndroidProcess.myUid() && it.pkgList?.contains(context.packageName) == true
        }.map { it.pid }.toSet()
        return PhoneAgentAuthColdOwner.permitsLockCleanup(registered, AndroidProcess.myPid()) {
            val directories = File("/proc").listFiles() ?: error("Private inventory unavailable")
            directories.mapNotNull { directory ->
                directory.name.toIntOrNull()?.takeIf { sameUid(directory) }
            }.toSet()
        }
    }

    private fun sameUid(directory: File): Boolean = try {
        val uid = Os.stat(directory.absolutePath).st_uid
        if (uid == 0) {
            val value = File(directory, "status").readLines().firstOrNull { it.startsWith("Uid:") }
                ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toIntOrNull()
            value == null || value == AndroidProcess.myUid()
        } else uid == AndroidProcess.myUid()
    } catch (_: Exception) { directory.exists() }
}
