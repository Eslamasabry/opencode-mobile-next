package io.github.eslamasabry.opencode_mobile

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import java.io.File
import java.util.concurrent.TimeUnit

/** Private CPU-lease acceptance. Uses an empty temporary agent home, never a real account. */
internal class BuiltinWorkLeaseAcceptance(private val instrumentation: Instrumentation) {
    private val context get() = instrumentation.targetContext
    private fun requireSafe(value: Boolean) { check(value) { "bb4_work_leases_refused" } }
    private fun count(linux: BuiltinLinux, kind: String): Int {
        val state = linux.performance()["workLeases"] as? Map<*, *> ?: return -1
        return ((state["kinds"] as? Map<*, *>)?.get(kind) as? Number)?.toInt() ?: 0
    }
    private fun waitFor(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
        requireSafe(predicate())
    }
    fun execute() {
        requireSafe(BuildConfig.BUILTIN_RUNTIME_QA)
        val linux = BuiltinLinux.get(context)
        requireSafe(linux.installed && linux.serverRunning && BuiltinServerService.isForegroundRunning)
        requireSafe(LocalTerminal.get(context).list().none { it.running })
        waitFor { linux.performance()["workHeld"] == false }
        val name = "qa.chat." + java.util.UUID.randomUUID().toString()
        val profile = "bb4qa_" + java.util.UUID.randomUUID().toString().replace("-", "")
        val home = File(linux.rootfs, "home/oc/.oc-profiles/$profile")
        requireSafe(!home.exists())
        var child: Process? = null
        var helper: Process? = null
        var installer: Process? = null
        var terminal: LocalTerminal.Session? = null
        try {
            // Actual native expiry with no Dart renewal or release command.
            requireSafe(linux.setChatWorkLease(name, true, 1000)["held"] == true)
            waitFor { !linux.workHeld }
            requireSafe(linux.setChatWorkLease(name, true, 1000)["capped"] == true)
            linux.setChatWorkLease(name, false, 1000)
            linux.withSetupWork {
                requireSafe(linux.workHeld && count(linux, "setup") == 1)
                val ticket = linux.captureAgentHostStart(profile, false, null)
                    ?: error("bb4_work_leases_refused")
                val host = linux.withAgentHostStart(ticket) {
                    linux.startAgentProcess(profile, listOf("/bin/sh", "-c", "IFS= read -r unused"))
                }
                helper = host
                linux.trackPrivateAgentService("agent-host.$profile", host, null)
                requireSafe(linux.setPhoneAgentChatWorkLease(profile, name, true, 15000)["held"] == true)
                requireSafe(linux.setPhoneAgentChatWorkLease("bb4_wrong_owner", name, true, 15000)["held"] == false)
                requireSafe(linux.setChatWorkLease(name, true, 15000)["held"] == true)
                requireSafe(count(linux, "chat") == 2)
                linux.setChatWorkLease(name, false, 15000)
                requireSafe(linux.workHeld && count(linux, "chat") == 1)
                // Same production private process launcher as PhoneAgentSignIn.launch.
                val signIn = linux.startSignInProcess(profile, listOf("/bin/sh", "-c", "IFS= read -r unused"), false)
                child = signIn
                waitFor { count(linux, "sign_in") == 1 }
                val shell = LocalTerminal.get(context).start(24, 80)
                terminal = shell
                waitFor { count(linux, "terminal") == 1 }
                requireSafe(signIn.isAlive && shell.running && linux.workHeld)
                linux.stopServer()
                requireSafe(linux.workHeld && count(linux, "setup") == 1 && count(linux, "sign_in") == 1 && count(linux, "terminal") == 1)
                requireSafe(!linux.serverRunning && BuiltinServerService.isForegroundRunning)
                // The exact helper continues after OpenCode loss; its same-named lease is independent.
                requireSafe(host.isAlive && count(linux, "chat") == 1)
                linux.setPhoneAgentChatWorkLease(profile, name, false, 15000)
                requireSafe(count(linux, "chat") == 0)
                LocalTerminal.get(context).remove(shell.id); terminal = null
                waitFor { count(linux, "terminal") == 0 }
                requireSafe(linux.workHeld && signIn.isAlive)
                linux.stopAgentProcess(signIn)
                requireSafe(signIn.waitFor(2, TimeUnit.SECONDS)); child = null
                waitFor { count(linux, "sign_in") == 0 }
                requireSafe(linux.workHeld && count(linux, "setup") == 1)
                linux.revokeSetupWork()
                requireSafe(!linux.workHeld && count(linux, "setup") == 0)
                val late = linux.startInstaller("sleep 1", setOf(InstallerTarget.OPENCODE2), InstallerOperation.CHECK)
                installer = late
                requireSafe(!linux.workHeld && count(linux, "setup") == 0)
                requireSafe(late.waitFor(5, TimeUnit.SECONDS))
                linux.finishInstaller(late); installer = null
            }
            val delayed = linux.prepareSetupWork()
            linux.revokeSetupWork()
            linux.withSetupWork(delayed) { requireSafe(!linux.workHeld && count(linux, "setup") == 0) }
            // Stop/timeout revocation denies fresh opaque IDs even before this live helper drains.
            requireSafe(helper?.isAlive == true && BuiltinServerService.isForegroundRunning)
            requireSafe(linux.setPhoneAgentChatWorkLease(profile, name, true, 15000)["held"] == true)
            linux.revokeForegroundWork()
            requireSafe(!linux.workHeld && count(linux, "chat") == 0)
            requireSafe(linux.setPhoneAgentChatWorkLease(profile, "$name.late", true, 15000)["held"] == false)
            requireSafe(linux.setPhoneAgentChatWorkLease(profile, name, true, 15000)["capped"] == true)
            linux.setPhoneAgentChatWorkLease(profile, name, false, 15000)
            linux.setPhoneAgentChatWorkLease(profile, "$name.late", false, 15000)
            linux.stopService("agent-host.$profile"); helper = null
            waitFor { !linux.workHeld }
            requireSafe(count(linux, "chat") == 0 && count(linux, "setup") == 0 && count(linux, "sign_in") == 0 && count(linux, "terminal") == 0)
            instrumentation.sendStatus(0, Bundle().apply {
                putBoolean("bb4WorkLeasesPassed", true)
                putBoolean("bb4NativeExpiryPassed", true)
                putBoolean("bb4IndependentOwnersPassed", true)
                putBoolean("bb4TerminalLifecyclePassed", true)
                putBoolean("bb4SetupRevocationPassed", true)
                putBoolean("bb4AgentScopePassed", true)
            })
        } finally {
            linux.setChatWorkLease(name, false, 1000)
            terminal?.let { LocalTerminal.get(context).remove(it.id) }
            child?.let { linux.stopAgentProcess(it) }
            linux.setPhoneAgentChatWorkLease(profile, name, false, 15000)
            linux.setPhoneAgentChatWorkLease(profile, "$name.late", false, 15000)
            helper?.let { linux.stopService("agent-host.$profile") }
            installer?.let { linux.stopInstaller(it); linux.finishInstaller(it) }
            linux.deleteAgentHome(profile)
            requireSafe(!home.exists())
        }
    }
}
