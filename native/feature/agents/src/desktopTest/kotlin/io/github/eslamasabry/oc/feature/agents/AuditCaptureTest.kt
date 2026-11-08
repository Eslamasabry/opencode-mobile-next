package io.github.eslamasabry.oc.feature.agents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.SkillInfo
import io.github.eslamasabry.oc.engine.project.terminal.TerminalKey
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputer
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputerKind
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsIssue
import io.github.eslamasabry.oc.feature.agents.hub.AgentsProblem
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.ComputerCheck
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.presentation.SignInStep
import io.github.eslamasabry.oc.feature.agents.presentation.SignInTerminalActions
import io.github.eslamasabry.oc.feature.agents.presentation.SignInTerminalUi
import io.github.eslamasabry.oc.feature.agents.providers.presentation.ProviderIssue
import io.github.eslamasabry.oc.feature.agents.providers.presentation.ProviderField
import io.github.eslamasabry.oc.feature.agents.providers.presentation.ProviderMethod
import io.github.eslamasabry.oc.feature.agents.providers.presentation.ProviderRow
import io.github.eslamasabry.oc.feature.agents.providers.presentation.ProvidersActions
import io.github.eslamasabry.oc.feature.agents.providers.presentation.ProvidersUi
import io.github.eslamasabry.oc.feature.agents.providers.ui.ProvidersScreen
import io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation.RemoteTerminalActions
import io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation.RemoteTerminalIssue
import io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation.RemoteTerminalItem
import io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation.RemoteTerminalUi
import io.github.eslamasabry.oc.feature.agents.remote_terminal.ui.RemoteTerminalScreen
import io.github.eslamasabry.oc.feature.agents.tools.presentation.ToolsActions
import io.github.eslamasabry.oc.feature.agents.tools.presentation.ToolsIssue
import io.github.eslamasabry.oc.feature.agents.tools.presentation.ToolsUi
import io.github.eslamasabry.oc.feature.agents.tools.ui.ToolsScreen
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.feature.agents.ui.SignInTerminalScreen
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test

/** Audit capture only (throwaway): the agents module's screens, each in a few states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "agents"

    @Composable
    private fun Secrets(content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}, content = content)

    private class HubActions : AgentsActions {
        override fun back() {}
        override fun startAdding() {}
        override fun cancelAdding() {}
        override fun edited() {}
        override fun check(address: String, password: String, folder: String) {}
        override fun add(name: String, address: String, password: String, folder: String) {}
        override fun readAgents(id: ProfileId) {}
        override fun newConversation(id: ProfileId) {}
        override fun refreshPhone() {}
        override fun requestRemoval(id: String) {}
        override fun cancelRemoval() {}
        override fun confirmRemoval() {}
    }

    private val desk = AgentComputer(ProfileId("p1"), "Desk", "ws://127.0.0.1:6767", "/home/me/app", connected = true)
    private val offline = AgentComputer(ProfileId("p2"), "Build box with a name that is much too long to fit on one line", "ws://box.example.net:6767", "/srv/a/very/long/project/folder/path/that/keeps/going", connected = false)
    private val codexBox = AgentComputer(ProfileId("p3"), "Codex machine", "wss://codex.example.net", "/work", connected = true, kind = AgentComputerKind.Codex)

    private val phoneAgents = persistentListOf(
        PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready, removable = true, signInAvailable = true, signOutAvailable = true),
        PhoneAgentRow("codex", "Codex", PhoneAgentStatus.SignInNeeded, removable = true, signInAvailable = true),
        PhoneAgentRow("pi", "Pi", PhoneAgentStatus.NotInstalled, installable = true, downloadBytes = 48_000_000L),
        PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.Limit, removable = true),
        PhoneAgentRow("stopped", "Antigravity", PhoneAgentStatus.Stopped, removable = true),
        PhoneAgentRow("check", "OpenCode agent", PhoneAgentStatus.Checking),
        PhoneAgentRow("away", "Another agent", PhoneAgentStatus.Unavailable),
    )

    @Test
    fun hub() = Audit.guard("${m}__hub__build") {
        val a = HubActions()
        Audit.shoot(m, "hub", "empty") { Secrets { AgentsScreen(AgentsUi(), a) } }
        Audit.shoot(m, "hub", "phone-agents") {
            Secrets { AgentsScreen(AgentsUi(phone = PhoneAgentsState(available = true, agents = phoneAgents)), a) }
        }
        Audit.shoot(m, "hub", "phone-installing") {
            Secrets {
                AgentsScreen(
                    AgentsUi(
                        phone = PhoneAgentsState(
                            available = true,
                            agents = phoneAgents,
                            installation = PhoneAgentInstallation("pi", PhoneAgentInstallPhase.Installing, 0.42),
                        ),
                    ),
                    a,
                )
            }
        }
        Audit.shoot(m, "hub", "phone-helper-stopped") {
            Secrets {
                AgentsScreen(
                    AgentsUi(phone = PhoneAgentsState(available = true, agents = phoneAgents, helperStoppedUnexpectedly = true, helperMayHaveBeenStoppedByAndroid = true, needsRestart = true)),
                    a,
                )
            }
        }
        Audit.shoot(m, "hub", "remove-confirm") {
            Secrets {
                KitSheetHost {
                    AgentsScreen(
                        AgentsUi(phone = PhoneAgentsState(available = true, agents = phoneAgents), removingAgent = phoneAgents[0]),
                        a,
                    )
                }
            }
        }
        Audit.shoot(m, "hub", "computers") {
            Secrets {
                AgentsScreen(
                    AgentsUi(
                        computers = persistentListOf(desk, offline, codexBox),
                        ready = persistentMapOf(desk.id to persistentListOf("Claude Code", "Pi")),
                    ),
                    a,
                )
            }
        }
        Audit.shoot(m, "hub", "computers-busy") {
            Secrets { AgentsScreen(AgentsUi(computers = persistentListOf(desk), busy = true), a) }
        }
        Audit.shoot(m, "hub", "computers-error-offline") {
            Secrets {
                AgentsScreen(
                    AgentsUi(
                        computers = persistentListOf(desk),
                        issue = AgentsProblem(AgentsIssue.AgentsFailed, Failure.Network(NetworkWhy.Offline)),
                    ),
                    a,
                )
            }
        }
        Audit.shoot(m, "hub", "add-form") { Secrets { AgentsScreen(AgentsUi(adding = true), a) } }
        Audit.shoot(m, "hub", "add-form-ready") {
            Secrets {
                AgentsScreen(AgentsUi(adding = true, check = ComputerCheck.Ready("0.8.0", persistentListOf("Claude Code", "Pi"))), a)
            }
        }
        Audit.shoot(m, "hub", "add-form-invalid") {
            Secrets { AgentsScreen(AgentsUi(adding = true, check = ComputerCheck.Invalid(address = true, folder = true, password = false)), a) }
        }
        Audit.shoot(m, "hub", "add-form-password-refused") {
            Secrets {
                AgentsScreen(
                    AgentsUi(adding = true, issue = AgentsProblem(AgentsIssue.CheckFailed, Failure.SignIn(SignInWhy.PasswordRejected))),
                    a,
                )
            }
        }
    }

    private class SignInActions : SignInTerminalActions {
        override fun openSignInPage() {}
        override fun confirmSignInPage() {}
        override fun dismissSignInPage() {}
        override fun copyCode() {}
        override fun send(line: String) {}
        override fun setAccessibleMode(on: Boolean) {}
        override fun finish() {}
        override fun startAgain() {}
        override fun leave() {}
    }

    @Test
    fun signIn() = Audit.guard("${m}__sign-in__build") {
        val a = SignInActions()
        Audit.shoot(m, "sign-in", "preparing") { Secrets { SignInTerminalScreen(SignInTerminalUi(agentName = "Codex"), a) } }
        Audit.shoot(m, "sign-in", "running-accessible") {
            Secrets {
                SignInTerminalScreen(
                    SignInTerminalUi(
                        agentName = "Codex",
                        step = SignInStep.Running,
                        accessibleMode = true,
                        transcript = "Welcome to Codex\nOpen the page below and enter the code.\n\nCode: DEMO-0000",
                        canOpenPage = true,
                        code = "DEMO-0000",
                        inputEnabled = true,
                    ),
                    a,
                )
            }
        }
        Audit.shoot(m, "sign-in", "not-yet") {
            Secrets { SignInTerminalScreen(SignInTerminalUi(agentName = "Claude Code", step = SignInStep.NotYet, accessibleMode = true, transcript = "Sign-in has ended"), a) }
        }
        Audit.shoot(m, "sign-in", "failed") {
            Secrets { SignInTerminalScreen(SignInTerminalUi(agentName = "Codex", step = SignInStep.Failed, failureCode = "sign-in-start"), a) }
        }
        Audit.shoot(m, "sign-in", "notifications-blocked") {
            Secrets { SignInTerminalScreen(SignInTerminalUi(agentName = "Codex", step = SignInStep.NotificationsBlocked), a) }
        }
        Audit.shoot(m, "sign-in", "provider-key-on-host") {
            Secrets { SignInTerminalScreen(SignInTerminalUi(agentName = "Pi", step = SignInStep.ProviderKeyOnHost), a) }
        }
        Audit.shoot(m, "sign-in", "unavailable") {
            Secrets { SignInTerminalScreen(SignInTerminalUi(agentName = "Codex", step = SignInStep.Unavailable), a) }
        }
    }

    private class ToolActions : ToolsActions {
        override fun back() {}
        override fun refresh() {}
        override fun providers() {}
        override fun selectSkill(id: String) {}
        override fun resume(value: Boolean) {}
        override fun activate() {}
        override fun connect(name: String) {}
        override fun disconnect(name: String) {}
        override fun add(name: String, target: String, local: Boolean) {}
    }

    @Test
    fun tools() = Audit.guard("${m}__tools__build") {
        val a = ToolActions()
        Audit.shoot(m, "tools", "loading") { Secrets { ToolsScreen(ToolsUi(loading = true), a) } }
        Audit.shoot(m, "tools", "empty") { Secrets { ToolsScreen(ToolsUi(loading = false), a) } }
        Audit.shoot(m, "tools", "populated") {
            Secrets {
                ToolsScreen(
                    ToolsUi(
                        loading = false,
                        canAdd = true,
                        catalogSupported = true,
                        hasSession = true,
                        models = persistentListOf(ModelRef("anthropic", "claude-sonnet"), ModelRef("openai", "gpt-model")),
                        model = ModelRef("anthropic", "claude-sonnet"),
                        skills = persistentListOf(
                            SkillInfo("review", "Review code", "Reads a diff and lists risks", "/skills/review", "...", true),
                            SkillInfo(null, "A skill with an extremely long name that will not fit on a single line of the phone", null, "/skills/long", "...", false),
                        ),
                        servers = persistentListOf(
                            McpServerInfo("github", "connected", null),
                            McpServerInfo("docs", "failed", "Could not reach the server"),
                            McpServerInfo("local", "disabled", null),
                        ),
                    ),
                    a,
                )
            }
        }
        Audit.shoot(m, "tools", "load-failed") { Secrets { ToolsScreen(ToolsUi(loading = false, issue = ToolsIssue.Load), a) } }
    }

    private class ProviderActions : ProvidersActions {
        override fun back() {}
        override fun refresh() {}
        override fun select(id: String) {}
        override fun selectMethod(id: String) {}
        override fun selectKey() {}
        override fun start(inputs: Map<String, String>) {}
        override fun connectKey(key: String) {}
        override fun disconnect() {}
        override fun confirmBrowser() {}
        override fun dismissBrowser() {}
        override fun check() {}
        override fun complete(code: String) {}
        override fun cancel() {}
    }

    @Test
    fun providers() = Audit.guard("${m}__providers__build") {
        val a = ProviderActions()
        val oauth = ProviderMethod("oauth", "Sign in with your account", persistentListOf())
        val form = ProviderMethod("url", "Use a custom endpoint", persistentListOf(ProviderField("url", "Endpoint address", true), ProviderField("org", "Organization", false)))
        val rows = persistentListOf(
            ProviderRow("anthropic", "Anthropic", connected = true, stored = true, key = true, methods = persistentListOf(oauth)),
            ProviderRow("openai", "OpenAI", connected = false, stored = false, key = true, methods = persistentListOf(oauth, form)),
            ProviderRow("local", "A local provider with a name long enough to wrap", connected = false, stored = false, key = false, methods = persistentListOf()),
        )
        Audit.shoot(m, "providers", "loading") { Secrets { ProvidersScreen(ProvidersUi(loading = true), a) } }
        Audit.shoot(m, "providers", "list") { Secrets { ProvidersScreen(ProvidersUi(loading = false, rows = rows), a) } }
        Audit.shoot(m, "providers", "key-entry") {
            Secrets { ProvidersScreen(ProvidersUi(loading = false, rows = rows, selected = "openai", keyEntry = true), a) }
        }
        Audit.shoot(m, "providers", "error") {
            Secrets { ProvidersScreen(ProvidersUi(loading = false, issue = ProviderIssue.Load), a) }
        }
    }

    private class TermActions : RemoteTerminalActions {
        override fun key(key: TerminalKey) {}
        override fun setAccessible(enabled: Boolean) {}
        override fun builtIn() {}
        override fun rerun() {}
        override fun refresh() {}
        override fun create() {}
        override fun connect(id: String) {}
        override fun disconnect() {}
        override fun showList() {}
        override fun send(value: String) {}
        override fun interrupt() {}
        override fun endOfInput() {}
        override fun escape() {}
        override fun tab() {}
        override fun askEnd() {}
        override fun cancelEnd() {}
        override fun confirmEnd() {}
        override fun back() {}
    }

    @Test
    fun remoteTerminal() = Audit.guard("${m}__remote-terminal__build") {
        val a = TermActions()
        val items = listOf(RemoteTerminalItem("1", "bash", true), RemoteTerminalItem("2", "npm run dev", false))
        Audit.shoot(m, "remote-terminal", "unavailable") { Secrets { RemoteTerminalScreen(RemoteTerminalUi(), a) } }
        Audit.shoot(m, "remote-terminal", "list") {
            Secrets { RemoteTerminalScreen(RemoteTerminalUi(available = true, serverName = "Desk", terminals = items), a) }
        }
        Audit.shoot(m, "remote-terminal", "connected-accessible") {
            Secrets {
                RemoteTerminalScreen(
                    RemoteTerminalUi(
                        available = true, serverName = "Desk", terminals = items, selected = items[0], connected = true,
                        accessible = true, transcript = "me@desk:~/app\$ ls\nREADME.md  build  src\nme@desk:~/app\$ ",
                    ),
                    a,
                )
            }
        }
        Audit.shoot(m, "remote-terminal", "failed") {
            Secrets { RemoteTerminalScreen(RemoteTerminalUi(available = true, serverName = "Desk", issue = RemoteTerminalIssue.Failed), a) }
        }
    }
}
