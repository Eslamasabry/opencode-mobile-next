package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.terminal.NativeLocalTerminal
import io.github.eslamasabry.oc.android.terminal.TerminalLaunch
import io.github.eslamasabry.oc.android.terminal.TerminalLinuxMissing
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.engine.project.terminal.LocalTerminalPort
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.LocalTerminal
import java.io.File

@BindingContainer
@ContributesTo(AppScope::class)
object LocalTerminalWiring {
    @Provides
    @SingleIn(AppScope::class)
    fun terminals(
        app: Application,
        dispatchers: AppDispatchers,
        scope: AppCoroutineScope,
    ): NativeLocalTerminal {
        val linux = BuiltinLinux.get(app)
        val terminal =
            NativeLocalTerminal(
                app,
                dispatchers,
                scope,
                launchShell = {
                    if (!linux.installed) throw TerminalLinuxMissing()
                    TerminalLaunch(
                        linux.prootLaunchPath,
                        app.filesDir.absolutePath,
                        linux.prootCommand(listOf("/bin/bash", "-l")),
                        (System.getenv() + linux.prootEnvironment()).map { (key, value) ->
                            "$key=$value"
                        },
                        linux.prootIsConfined,
                    )
                },
                stopTree = { pid, expectedStart ->
                    val process = File("/proc/$pid")
                    if (process.exists()) {
                        val current =
                            checkNotNull(NativeLocalTerminal.startTicks(pid)) {
                                "terminal_identity_unreadable"
                            }
                        if (current == expectedStart)
                            BuiltinLinux.stopPidTree(
                                pid,
                                { wait ->
                                    val deadline = android.os.SystemClock.elapsedRealtime() + wait
                                    while (
                                        NativeLocalTerminal.startTicks(pid) == expectedStart &&
                                            android.os.SystemClock.elapsedRealtime() < deadline
                                    ) Thread.sleep(20)
                                    NativeLocalTerminal.startTicks(pid) != expectedStart
                                },
                            )
                    }
                },
            )
        LocalTerminal.get(app)
            .install(
                object : LocalTerminal.Backend {
                    override fun start(
                        rows: Int,
                        cols: Int,
                        signInProfile: String?,
                        signInProgram: List<String>?,
                    ): LocalTerminal.Session {
                        require(signInProfile == null && signInProgram == null) {
                            "terminal_signin_requires_claim"
                        }
                        val id = terminal.startFromBridge(rows, cols)
                        return list().first { it.id == id }
                    }

                    override fun list(): List<LocalTerminal.Session> =
                        terminal.runningSessions().map { (id, pid, confined) ->
                            LocalTerminal.Session(
                                id,
                                pid,
                                confined,
                                live = { terminal.shells.value.any { it.id == id && it.running } },
                                stopSession = { terminal.stopFromBridge(id) },
                            )
                        }

                    override fun remove(id: Int) = terminal.removeFromBridge(id)
                }
            )
        return terminal
    }

    @Provides fun localTerminalPort(terminal: NativeLocalTerminal): LocalTerminalPort = terminal
}
