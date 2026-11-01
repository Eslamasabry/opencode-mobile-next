package io.github.eslamasabry.oc.android.agenthost

import android.os.Build
import java.io.File

/**
 * What the kept agent-host Kotlin ([PhoneAgentHost], [PhoneAgentChecks], [PhoneAgentSignIn]) needs from the phone's
 * Linux. In the Flutter app these were direct calls on `BuiltinLinux`; here they are this one seam, with exactly the
 * members those three classes call (the Flutter harness stub `test/native/phone_agent_host_stubs/linux.kt` lists the
 * same surface). The production binding is [AgentProcesses]; tests use a fake.
 *
 * Plan 11 §5.3 moves this block of `BuiltinLinux` (`AgentProcesses`, lines 745–1079 and 2093–2131) into
 * `:android:agenthost` behind the CR11-0 seams; [AgentProcesses] is that move, written over [AgentGuestPorts] until
 * `:android:linux` exposes `GuestLauncher` and `GuestProcessRegistry`.
 */
interface AgentLinux {
    /** `files/linux`: the guest root's parent. The launch space guard measures it. */
    val home: File

    /** `files/linux/ubuntu`. */
    val rootfs: File

    /** Whether the phone's Linux is installed. */
    val installed: Boolean

    /** The phone's first ABI, as the status map reports it (`Build.SUPPORTED_ABIS[0]`). */
    val primaryAbi: String get() = Build.SUPPORTED_ABIS.firstOrNull() ?: ""

    /** The diagnostics record of a private agent service (`agent-host.<profile>`): exits, uptime, restarts. */
    fun privateAgentDiagnostics(name: String): Map<String, Any?>

    /** Paseo `config.json` (version 1, < 64 KiB) under the profile's private Paseo home, temp + rename, mode 0600. */
    fun writeAgentConfig(profile: String, config: String)

    /** Claude's OAuth refresh lock, removed only when none of [profile]'s agent processes runs. */
    fun clearStaleAgentLoginLock(profile: String)

    /** [profile]'s private Paseo home (made if missing, never through a link). */
    fun agentPaseoHome(profile: String): File

    /** Whether anything answers on the helper's loopback [port]. */
    fun agentPortAnswers(port: Int): Boolean

    /** A private agent process: fixed uid 1000, private home, no transcript or log. */
    fun startAgentProcess(
        profile: String,
        args: List<String>,
        foreground: Boolean = false,
        privateOutput: Boolean = false,
    ): Process

    /** The Claude sign-in pipes' processes (same launch, plus the sign-in work lease). */
    fun startSignInProcess(profile: String, args: List<String>, foreground: Boolean): Process

    /** Registers a running helper or auth process as a supervised private service. */
    fun trackPrivateAgentService(name: String, child: Process, port: Int?)

    /** Refuses (`agent_host_unowned` / `agent_host_busy`) unless [process] is [profile]'s exact helper and nothing else runs. */
    fun requireAgentHostUpdateIdle(profile: String, process: Process?)

    /** Stops [child] and every descendant it had, by exact identity; throws when they do not stop. */
    fun stopAgentProcess(child: Process)

    fun stopService(name: String)

    /** No new process for [profile] after this; the running ones are drained. */
    fun blockAgentProfile(profile: String)

    /** Erases `/home/oc/.oc-profiles/<profile>` (lstat-based, never following links). */
    fun deleteAgentHome(profile: String)

    /** The Claude sign-in pipes, one per Linux. */
    val agentSignIn: PhoneAgentSignIn
}
