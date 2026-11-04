package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.data.kv.GateNotOpen
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.git.GitShell
import io.github.eslamasabry.oc.engine.git.GitShellFailure
import io.github.eslamasabry.oc.engine.git.GitShellReply
import io.github.eslamasabry.oc.engine.runtime.install.BuiltinLinuxPort
import io.github.eslamasabry.oc.engine.runtime.install.BuiltinShell
import io.github.eslamasabry.oc.engine.runtime.install.LinuxBridgeAnswer
import io.github.eslamasabry.oc.engine.runtime.install.ShellAnswer
import io.github.eslamasabry.oc.engine.runtime.install.ShellFailure
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * The Git page's shell in this app's Ubuntu, bound only to the saved phone owner and its live
 * connection.
 */
internal class BuiltinGitShell(
    application: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val graph: ConnectionGraph,
    private val registry: ConnectionRegistry,
    private val ref: ConversationRef,
) : GitShell {
    private val linux by lazy { BuiltinLinux.get(application) }
    private val shell =
        BuiltinShell(
            BuiltinLinuxPort { script, timeout ->
                withContext(dispatchers.io) {
                    if (
                        !ownsCurrentProfile() ||
                            !linux.serverRunning ||
                            linux.port != graph.profile.origin.port
                    ) {
                        return@withContext LinuxBridgeAnswer.BridgeFailed("unavailable")
                    }
                    try {
                        val seconds =
                            ((timeout.inWholeMilliseconds.coerceIn(1, 600_000) + 999) / 1000)
                        // The runtime captures Git output without logging it.
                        val result = linux.run(script, seconds, logOutput = false)
                        if (result.exitCode == -1) LinuxBridgeAnswer.TimedOut
                        else LinuxBridgeAnswer.Ran(result.exitCode, result.output)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (e.message == "timeout") LinuxBridgeAnswer.TimedOut
                        else LinuxBridgeAnswer.Crashed
                    }
                }
            }
        )

    suspend fun current(): Boolean =
        withContext(dispatchers.io) {
            try {
                ownsCurrentProfile() &&
                    linux.serverRunning &&
                    linux.port == graph.profile.origin.port
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }

    private fun ownsCurrentProfile(): Boolean {
        if (registry.graphFor(ref) !== graph) return false
        return try {
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ==
                graph.profile.id.raw && stores.profiles.profile(graph.profile.id) != null
        } catch (e: GateNotOpen) {
            false
        }
    }

    override suspend fun run(script: String, timeoutMs: Long): GitShellReply =
        when (val result = shell.run(script, timeoutMs.coerceAtLeast(1).milliseconds)) {
            is ShellAnswer.Output -> GitShellReply.Output(result.text)
            is ShellAnswer.Failed ->
                GitShellReply.Failed(
                    if (result.failure == ShellFailure.TimedOut) GitShellFailure.TimedOut
                    else GitShellFailure.Unavailable
                )
        }
}
