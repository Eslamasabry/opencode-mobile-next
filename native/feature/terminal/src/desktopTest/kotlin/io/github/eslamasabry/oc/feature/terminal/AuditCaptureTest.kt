package io.github.eslamasabry.oc.feature.terminal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.engine.project.terminal.LocalShell
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalState
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalActions
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalNativeActions
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalUi
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalActions
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalServerSource
import io.github.eslamasabry.oc.feature.terminal.ui.LocalTerminalScreen
import io.github.eslamasabry.oc.feature.terminal.ui.TerminalScreen
import kotlinx.collections.immutable.persistentListOf
import java.lang.reflect.Proxy
import kotlin.test.Test

/** Audit capture only (throwaway): the server terminal and the phone's local terminal, each in a few states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "terminal"

    private fun proc(id: String, title: String, running: Boolean = true, exit: Int? = null, command: String = "bash", args: List<String> = emptyList()) =
        TerminalProcess(id, title, command, persistentListOf(*args.toTypedArray()), "/work/app", running = running, pid = 4242, exitCode = exit)

    private class Actions : TerminalActions {
        override fun back() = Unit
        override fun refresh() = Unit
        override fun create() = Unit
        override fun select(id: String) = Unit
        override fun showList() = Unit
        override fun send(id: String, text: String) = Unit
        override fun rename(id: String, title: String) = Unit
        override fun resize(id: String, rows: Int, cols: Int) = Unit
        override fun remove(id: String) = Unit
    }

    private val shell get() = proc("t1", "Shell")
    private val transcript =
        "me@desk:~/app\$ ls\nREADME.md  build  src\nme@desk:~/app\$ ./gradlew build\nBUILD SUCCESSFUL in 12s\nme@desk:~/app\$ "

    @Test
    fun serverTerminal() = Audit.guard("${m}__server__build") {
        val a = Actions()
        Audit.shoot(m, "server", "loading") { TerminalScreen(RemoteTerminalState(), a) }
        Audit.shoot(m, "server", "no-terminals") { TerminalScreen(RemoteTerminalState(loading = false), a) }
        Audit.shoot(m, "server", "list") {
            TerminalScreen(
                RemoteTerminalState(
                    loading = false,
                    terminals = persistentListOf(
                        shell,
                        proc("t2", "npm run dev", command = "npm", args = listOf("run", "dev")),
                        proc("t3", "A terminal with a title that is far too long to fit on one line of the phone", running = false, exit = 1),
                    ),
                ),
                a,
            )
        }
        Audit.shoot(m, "server", "connected") {
            TerminalScreen(RemoteTerminalState(loading = false, terminals = persistentListOf(shell), selected = shell, connected = true, transcript = transcript), a)
        }
        Audit.shoot(m, "server", "connected-menu-open", setup = {
            runCatching {
                onNodeWithContentDescription("Actions for Shell").performClick()
                waitForIdle()
                mainClock.advanceTimeBy(500)
            }
        }) {
            TerminalScreen(RemoteTerminalState(loading = false, terminals = persistentListOf(shell), selected = shell, connected = true, transcript = transcript), a)
        }
        Audit.shoot(m, "server", "connection-closed") {
            TerminalScreen(RemoteTerminalState(loading = false, terminals = persistentListOf(shell), selected = shell, connected = false, transcript = transcript), a)
        }
        Audit.shoot(m, "server", "connecting") {
            TerminalScreen(RemoteTerminalState(loading = false, terminals = persistentListOf(shell), selected = shell, working = true), a)
        }
        Audit.shoot(m, "server", "program-ended") {
            val ended = proc("t1", "Build", running = false, exit = 2, command = "npm", args = listOf("run", "build"))
            TerminalScreen(RemoteTerminalState(loading = false, terminals = persistentListOf(ended), selected = ended, transcript = transcript), a)
        }
        Audit.shoot(m, "server", "failed-offline") {
            TerminalScreen(RemoteTerminalState(loading = false, failure = Failure.Network(NetworkWhy.Offline)), a)
        }
    }

    private val localActions: LocalTerminalActions =
        Proxy.newProxyInstance(
            LocalTerminalActions::class.java.classLoader,
            arrayOf(LocalTerminalActions::class.java, LocalTerminalNativeActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "LocalTerminalActions"
                else -> null
            }
        } as LocalTerminalActions

    private fun local(id: Int, running: Boolean = true, text: String = transcript) =
        LocalShell(
            id, id, id, running, if (running) null else 0, text,
            object : PlatformViewHandle {
                override val native: Any = Unit
            },
        )

    @Test
    fun localTerminal() = Audit.guard("${m}__local__build") {
        val a = localActions
        Audit.shoot(m, "local", "starting") { LocalTerminalScreen(LocalTerminalUi(working = true), a) }
        Audit.shoot(m, "local", "one-shell") {
            LocalTerminalScreen(LocalTerminalUi(shells = listOf(local(1)), selected = 1, accessible = true), a)
        }
        Audit.shoot(m, "local", "two-shells") {
            LocalTerminalScreen(LocalTerminalUi(shells = listOf(local(1), local(2, text = "\$ top\n")), selected = 2, accessible = true), a)
        }
        Audit.shoot(m, "local", "menu-open", setup = {
            runCatching {
                onNodeWithContentDescription("More").performClick()
                waitForIdle()
                mainClock.advanceTimeBy(500)
            }
        }) {
            LocalTerminalScreen(LocalTerminalUi(shells = listOf(local(1)), selected = 1, accessible = true), a)
        }
        Audit.shoot(m, "local", "shell-ended") {
            LocalTerminalScreen(LocalTerminalUi(shells = listOf(local(1, running = false)), selected = 1, accessible = true), a)
        }
        Audit.shoot(m, "local", "stop-confirm") {
            LocalTerminalScreen(LocalTerminalUi(shells = listOf(local(1)), selected = 1, accessible = true, confirmStop = 1), a)
        }
        Audit.shoot(m, "local", "dev-server-and-servers") {
            LocalTerminalScreen(
                LocalTerminalUi(
                    shells = listOf(local(1)), selected = 1, accessible = true, devServerPort = 3000,
                    servers = listOf(TerminalServerSource(ProfileId("home"), "Home laptop"), TerminalServerSource(ProfileId("vps"), "Build VPS")),
                ),
                a,
            )
        }
        Audit.shoot(m, "local", "linux-not-set-up") { LocalTerminalScreen(LocalTerminalUi(failed = true, notInstalled = true), a) }
        Audit.shoot(m, "local", "failed") { LocalTerminalScreen(LocalTerminalUi(failed = true), a) }
    }
}
