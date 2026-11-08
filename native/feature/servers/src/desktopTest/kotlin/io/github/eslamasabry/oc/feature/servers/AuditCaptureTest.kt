package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.SavedPermission
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.discovery.LanAccess
import io.github.eslamasabry.oc.feature.servers.discovery.LanFinding
import io.github.eslamasabry.oc.feature.servers.discovery.LanSource
import io.github.eslamasabry.oc.feature.servers.pairing.PairingFill
import io.github.eslamasabry.oc.feature.servers.pairing.PairingProblem
import io.github.eslamasabry.oc.feature.servers.pairing.PairingRead
import io.github.eslamasabry.oc.feature.servers.pairing.PairingScannerActions
import io.github.eslamasabry.oc.feature.servers.pairing.PairingScannerScreen
import io.github.eslamasabry.oc.feature.servers.pairing.PairingScannerUi
import io.github.eslamasabry.oc.feature.servers.pairing.QrScannerHandle
import io.github.eslamasabry.oc.feature.servers.pairing.QrScannerSlot
import io.github.eslamasabry.oc.feature.servers.pairing.ScanStage
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckReason
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckResult
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckState
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckStep
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckUi
import io.github.eslamasabry.oc.feature.servers.presentation.EditorTailscaleState
import io.github.eslamasabry.oc.feature.servers.presentation.FindOnNetworkUi
import io.github.eslamasabry.oc.feature.servers.presentation.FindPhase
import io.github.eslamasabry.oc.feature.servers.presentation.QueueMoveBlock
import io.github.eslamasabry.oc.feature.servers.presentation.QueueMoveConversation
import io.github.eslamasabry.oc.feature.servers.presentation.QueueMoveItem
import io.github.eslamasabry.oc.feature.servers.presentation.SavedPermissionsActions
import io.github.eslamasabry.oc.feature.servers.presentation.SavedPermissionsUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerCertificate
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorInput
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorProbe
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorRecord
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorStep
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerIssue
import io.github.eslamasabry.oc.feature.servers.presentation.ServerQueueActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerQueuePlan
import io.github.eslamasabry.oc.feature.servers.presentation.ServerQueueUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRemovalActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRemovalPlan
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRemovalUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowFacts
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowsState
import io.github.eslamasabry.oc.feature.servers.presentation.ServerSetupActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerSetupUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.ui.SavedPermissionsScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServerEditorScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServerQueueScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServerRemovalScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServerSetupScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.engine.setupassist.SetupIntent
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlin.test.Test

/** Audit capture only (throwaway): the servers module's screens, each in a few states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "servers"

    /** Shoots kept out of the default run to bound CI time; empty this set to capture them too. */
    private val skip = setOf(
        "list/populated-menu-open", "list/empty-computer-only", "list/root-first-run", "list/busy", "list/error-sign-in",
        "list/error-https-required", "list/host-setup-sheet", "list/connection-check-running",
        "editor/tailscale-step-missing", "editor/edit-existing", "editor/loading", "editor/checking-slow", "editor/saving",
        "editor/check-passed", "editor/save-failed-sign-in", "editor/discard-changes", "editor/public-http-refused",
        "editor/connect-new", "editor/find-scanning", "editor/find-none", "editor/find-blocked",
        "queue-move/loading", "queue-move/failed", "remove/loading", "remove/nothing-pending", "remove/failed",
        "permissions/loading", "permissions/confirm-revoke", "permissions/error",
        "server-setup/unavailable", "server-setup/busy", "server-setup/failed",
        "pairing-scanner/starting", "pairing-scanner/blocked", "pairing-scanner/not-a-code", "pairing-scanner/bad-code",
    )

    private fun shot(
        screen: String,
        state: String,
        advanceMs: Long = 1_000,
        setup: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {},
        content: @Composable (dark: Boolean) -> Unit,
    ) {
        if ("$screen/$state" in skip) return
        Audit.shoot(m, screen, state, advanceMs, setup, content)
    }

    private fun fill(link: String): PairingFill? = PairingFill.of(PairingRead.Link(link))

    private class ListActions(private var pending: PairingFill? = null) : ServersActions {
        override fun back() {}
        override fun allowCleartext(allowed: Boolean, address: String) {}
        override fun save(name: String, address: String, username: String, password: String) {}
        override fun connect(id: ProfileId) {}
        override fun create(id: ProfileId) {}
        override fun remove(id: ProfileId) {}
        override fun takePairing(): PairingFill? = pending.also { pending = null }
    }

    private class EditorActions : ServerEditorActions {
        var draft: ServerEditorInput? = null
        override fun keepDraft(input: ServerEditorInput) {}
        override fun takeDraft(): ServerEditorInput? = draft
        override fun refreshTailscale() {}
        override fun openTailscale() {}
        override fun installTailscale() {}
        override fun back(dirty: Boolean) {}
        override fun discard() {}
        override fun keepEditing() {}
        override fun step(step: ServerEditorStep) {}
        override fun changed() {}
        override fun check(input: ServerEditorInput) {}
        override fun save(input: ServerEditorInput, anyway: Boolean) {}
        override fun cancelCheck() {}
        override fun inspect(address: String) {}
        override fun dismissCertificate() {}
        override fun scan() {}
        override fun paste(text: String?) {}
        override fun takePairing(): PairingFill? = null
        override fun phone() {}
        override fun agents() {}
        override fun openReady() {}
        override fun findAccess(access: LanAccess, extraPorts: Set<Int>) {}
        override fun stopFind() {}
        override fun openAccessSettings() {}
        override fun pickFound(finding: LanFinding) {}
    }

    @Composable
    private fun Secrets(content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}, content = content)

    private fun saved(id: String, name: String, address: String, connected: Boolean = false) =
        SavedServer(ProfileId(id), name, address, connected)

    private fun facts(
        backend: BackendKind = BackendKind.OpenCode2,
        version: String? = "2.1.1",
        dir: String = "/home/dev/projects",
        needsSecret: Boolean = false,
        waiting: Int? = 0,
        running: Int? = 0,
        queued: Int = 0,
    ) = ServerRowFacts(backend, version, dir, needsSecret, waiting, running, queued)

    private val three = listOf(
        saved("home", "Home laptop", "https://laptop.tail1234.ts.net:4096", true),
        saved("vps", "Build VPS", "https://build.example.net:4096"),
        saved("phone", "This phone", "http://127.0.0.1:4097"),
    )

    private fun populated(extra: Map<ProfileId, ServerRowFacts> = emptyMap()): ServersUi {
        val base = persistentMapOf(
            three[0].id to facts(waiting = 1, running = 2, queued = 2),
            three[1].id to facts(dir = "/srv/build", needsSecret = true, waiting = null, running = null),
            three[2].id to facts(version = "2.0.28", dir = "/root/work"),
        )
        return ServersUi(servers = persistentListOf(*three.toTypedArray()), rows = ServerRowsState(base.putAll(extra)))
    }

    // ---------------------------------------------------------------- servers list

    @Test
    fun serversList() = Audit.guard("${m}__list__build") {
        val actions = ListActions()
        shot("list", "populated") { Secrets { ServersScreen(populated(), actions, onThisPhone = {}) } }
        shot("list", "populated-menu-open", setup = {
            runCatching {
                onAllNodesWithContentDescription("Home laptop", substring = true, useUnmergedTree = true).onLast().performClick()
                waitForIdle()
                mainClock.advanceTimeBy(500)
            }
        }) { Secrets { KitSheetHost { ServersScreen(populated(), actions, onThisPhone = {}) } } }
        shot("list", "empty-with-phone") { Secrets { ServersScreen(ServersUi(), actions, onThisPhone = {}) } }
        shot("list", "empty-computer-only") { Secrets { ServersScreen(ServersUi(), actions) } }
        shot("list", "root-first-run") { Secrets { ServersScreen(ServersUi(), actions, root = true) } }
        shot("list", "busy") { Secrets { ServersScreen(populated().copy(busy = true), actions) } }
        shot("list", "error-offline") {
            Secrets {
                ServersScreen(
                    populated().copy(issue = ServerIssue.ConnectFailed, failure = Failure.Network(NetworkWhy.Offline)),
                    actions,
                )
            }
        }
        shot("list", "error-sign-in") {
            Secrets {
                ServersScreen(
                    populated().copy(issue = ServerIssue.ConnectFailed, failure = Failure.SignIn(SignInWhy.PasswordRejected)),
                    actions,
                )
            }
        }
        shot("list", "error-https-required") {
            Secrets { ServersScreen(populated().copy(issue = ServerIssue.HttpsRequired), actions) }
        }
    }

    @Test
    fun serversListLongNames() = Audit.guard("${m}__list__long-names__build") {
        val long = listOf(
            saved("a", "My extremely long named workstation in the basement under the stairs number seven", "https://a-very-long-host-name-for-a-tailnet-machine.tail1234.ts.net:4096", true),
            saved("b", "مخدم البناء الطويل جدا في المكتب الرئيسي", "https://build.example.net:4096"),
            saved("c", "Same name", "https://one.example.net:4096"),
            saved("d", "Same name", "https://two.example.net:4096"),
        )
        val ui = ServersUi(
            servers = persistentListOf(*long.toTypedArray()),
            rows = ServerRowsState(
                persistentMapOf(
                    long[0].id to facts(dir = "/home/dev/a/very/long/path/to/the/project/directory/that/keeps/going", waiting = 12, running = 3, queued = 9),
                    long[1].id to facts(backend = BackendKind.Paseo, version = "0.11.2"),
                    long[2].id to facts(backend = BackendKind.Codex, version = null),
                    long[3].id to facts(needsSecret = true),
                ),
            ),
        )
        shot("list", "long-names") { Secrets { ServersScreen(ui, ListActions()) } }
    }

    @Test
    fun serversListSheets() = Audit.guard("${m}__list__sheets__build") {
        shot("list", "host-setup-sheet") {
            Secrets { KitSheetHost { ServersScreen(populated(), ListActions(), initialHostSetup = true) } }
        }
        shot("list", "arrival-sheet") {
            Secrets {
                KitSheetHost {
                    ServersScreen(
                        ServersUi(pairingArrival = 1),
                        ListActions(fill("https://box.tail1234.ts.net:4096")),
                    )
                }
            }
        }
        val results = ConnectionCheckStep.entries.associateWith {
            when (it) {
                ConnectionCheckStep.Address -> ConnectionCheckResult(ConnectionCheckState.Pass, ConnectionCheckReason.Ok)
                ConnectionCheckStep.SignIn -> ConnectionCheckResult(ConnectionCheckState.Fail, ConnectionCheckReason.PasswordRejected)
                ConnectionCheckStep.Version -> ConnectionCheckResult(ConnectionCheckState.Skipped, ConnectionCheckReason.EarlierFailed)
                ConnectionCheckStep.LiveUpdates -> ConnectionCheckResult(ConnectionCheckState.Skipped, ConnectionCheckReason.EarlierFailed)
                ConnectionCheckStep.Speed -> ConnectionCheckResult(ConnectionCheckState.Skipped, ConnectionCheckReason.EarlierFailed)
            }
        }
        shot("list", "connection-check-failed") {
            Secrets {
                KitSheetHost {
                    ServersScreen(
                        populated().copy(connectionCheck = ConnectionCheckUi(three[0].id, three[0].name, running = false, results = results)),
                        ListActions(),
                    )
                }
            }
        }
        shot("list", "connection-check-running") {
            Secrets {
                KitSheetHost {
                    ServersScreen(
                        populated().copy(connectionCheck = ConnectionCheckUi(three[0].id, three[0].name)),
                        ListActions(),
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- server editor

    private fun draft(address: String, name: String = "Computer") = ServerEditorInput(
        null, name, address, "opencode", BackendKind.OpenCode2, "", null, false, false, null, null, null,
    )

    private fun record(name: String, address: String) = ServerEditorRecord(
        ProfileId("home"), name, address, "opencode", BackendKind.OpenCode2, "/home/dev", true, false, false, null, null, null,
    )

    @Test
    fun editorSteps() = Audit.guard("${m}__editor__steps__build") {
        shot("editor", "kind-step") {
            Secrets { ServerEditorScreen(ServerEditorUi(step = ServerEditorStep.Kind, canPhone = true), EditorActions()) }
        }
        shot("editor", "tailscale-step-installed") {
            Secrets {
                ServerEditorScreen(
                    ServerEditorUi(step = ServerEditorStep.Tailscale, canTailscale = true, tailscaleApp = EditorTailscaleState.Installed),
                    EditorActions(),
                )
            }
        }
        shot("editor", "tailscale-step-missing") {
            Secrets {
                ServerEditorScreen(
                    ServerEditorUi(step = ServerEditorStep.Tailscale, canTailscale = true, tailscaleApp = EditorTailscaleState.Missing),
                    EditorActions(),
                )
            }
        }
        shot("editor", "connect-new", setup = {
            runCatching { onNodeWithText("Enter the address instead").performClick() }
        }) {
            Secrets { ServerEditorScreen(ServerEditorUi(step = ServerEditorStep.Connect, canScan = true), EditorActions()) }
        }
        shot("editor", "ready-step") {
            Secrets {
                ServerEditorScreen(
                    ServerEditorUi(step = ServerEditorStep.Ready, saved = true, checked = ServerEditorProbe("2.1.1"), startsConversation = true),
                    EditorActions(),
                )
            }
        }
    }

    @Test
    fun editorConnectStates() = Audit.guard("${m}__editor__connect__build") {
        val filled = EditorActions().also { it.draft = draft("https://laptop.tail1234.ts.net:4096", "Home laptop") }
        val connect = ServerEditorUi(step = ServerEditorStep.Connect, newServer = false)
        shot("editor", "connect-filled") { Secrets { ServerEditorScreen(connect, filled) } }
        shot("editor", "edit-existing") {
            Secrets {
                ServerEditorScreen(
                    connect.copy(record = record("Home laptop", "https://laptop.tail1234.ts.net:4096"), connectOnSave = false),
                    filled,
                )
            }
        }
        shot("editor", "loading") {
            Secrets { ServerEditorScreen(connect.copy(loading = true), filled) }
        }
        shot("editor", "checking") {
            Secrets { ServerEditorScreen(connect.copy(checking = true), filled) }
        }
        shot("editor", "checking-slow") {
            Secrets { ServerEditorScreen(connect.copy(checking = true, slow = true), filled) }
        }
        shot("editor", "saving") {
            Secrets { ServerEditorScreen(connect.copy(saving = true), filled) }
        }
        shot("editor", "check-passed") {
            Secrets { ServerEditorScreen(connect.copy(checked = ServerEditorProbe("2.1.1", listOf("OpenCode"))), filled) }
        }
        shot("editor", "check-failed-offline") {
            Secrets { ServerEditorScreen(connect.copy(failedCheck = true, failure = Failure.Network(NetworkWhy.Offline)), filled) }
        }
        shot("editor", "save-failed-sign-in") {
            Secrets { ServerEditorScreen(connect.copy(failure = Failure.SignIn(SignInWhy.PasswordRejected)), filled) }
        }
        shot("editor", "certificate-review") {
            Secrets {
                ServerEditorScreen(
                    connect.copy(certificate = ServerCertificate("https://laptop.tail1234.ts.net:4096", "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89", "2026-01-01", "2027-01-01")),
                    filled,
                )
            }
        }
        shot("editor", "discard-changes") {
            Secrets { KitSheetHost { ServerEditorScreen(connect.copy(discard = true), filled) } }
        }
        val lan = listOf(192, 168, 1, 8).joinToString(".")
        val http = EditorActions().also { it.draft = draft("http://$lan:4096") }
        shot("editor", "plain-http-review") {
            Secrets { ServerEditorScreen(connect, http) }
        }
        val pub = EditorActions().also { it.draft = draft("http://203.0.113.9:4096") }
        shot("editor", "public-http-refused") {
            Secrets { ServerEditorScreen(connect, pub) }
        }
        val longName = EditorActions().also {
            it.draft = draft(
                "https://an-extremely-long-host-name-for-a-machine-in-a-tailnet.tail1234.ts.net:4096/with/a/path",
                "A computer whose name is much too long to fit on one line of the phone screen",
            )
        }
        shot("editor", "long-name-address") { Secrets { ServerEditorScreen(connect, longName) } }
    }

    @Test
    fun editorFind() = Audit.guard("${m}__editor__find__build") {
        val connect = ServerEditorUi(step = ServerEditorStep.Connect, canFind = true, canScan = true)
        val found = listOf(
            LanFinding("studio.local", 4096, name = "Studio Mac", version = "2.1.1", source = LanSource.Advertised),
            LanFinding("desk", 4096, name = null, needsPassword = true),
            LanFinding("a-host-with-a-very-long-advertised-name-on-the-network.local", 4097, name = "A host with a very long advertised name"),
        )
        shot("editor", "find-scanning") {
            Secrets { ServerEditorScreen(connect.copy(find = FindOnNetworkUi(FindPhase.Scanning, found.take(1))), EditorActions()) }
        }
        shot("editor", "find-done") {
            Secrets { ServerEditorScreen(connect.copy(find = FindOnNetworkUi(FindPhase.Done, found)), EditorActions()) }
        }
        shot("editor", "find-none") {
            Secrets { ServerEditorScreen(connect.copy(find = FindOnNetworkUi(FindPhase.Done, emptyList())), EditorActions()) }
        }
        shot("editor", "find-denied") {
            Secrets { ServerEditorScreen(connect.copy(find = FindOnNetworkUi(FindPhase.Denied)), EditorActions()) }
        }
        shot("editor", "find-blocked") {
            Secrets { ServerEditorScreen(connect.copy(find = FindOnNetworkUi(FindPhase.Blocked)), EditorActions()) }
        }
    }

    // ---------------------------------------------------------------- queue move, removal, permissions, setup, scanner

    private class QueueActions : ServerQueueActions {
        override fun back() {}
        override fun refresh() {}
        override fun toggle(id: String) {}
        override fun choose(conversation: SessionId?) {}
        override fun move() {}
        override fun undo() {}
        override fun openConversation() {}
    }

    @Test
    fun queueMove() = Audit.guard("${m}__queue-move__build") {
        val plan = ServerQueuePlan(
            "ticket", "Old computer", "Laptop",
            persistentListOf(
                QueueMoveItem("one", "Fix the failing tests in the settings module and then run the whole suite again", null),
                QueueMoveItem("two", "Write the release notes", null),
                QueueMoveItem("blocked", "Already sending", QueueMoveBlock.Sending),
                QueueMoveItem("secret", "Mentions a key", QueueMoveBlock.SecretMentions),
            ),
            persistentListOf(QueueMoveConversation(SessionId("recent"), "Recent work"), QueueMoveConversation(SessionId("untitled"), null)),
        )
        shot("queue-move", "loading") { Secrets { KitSheetHost { ServerQueueScreen(ServerQueueUi(loading = true), QueueActions()) } } }
        shot("queue-move", "plan-selected") {
            Secrets { KitSheetHost { ServerQueueScreen(ServerQueueUi(loading = false, plan = plan, selected = persistentSetOf("one", "two")), QueueActions()) } }
        }
        shot("queue-move", "empty-source") {
            Secrets { KitSheetHost { ServerQueueScreen(ServerQueueUi(loading = false, plan = plan.copy(items = persistentListOf())), QueueActions()) } }
        }
        shot("queue-move", "failed") {
            Secrets { KitSheetHost { ServerQueueScreen(ServerQueueUi(loading = false, failure = Failure.Network(NetworkWhy.Offline)), QueueActions()) } }
        }
    }

    private class RemovalActions : ServerRemovalActions {
        override fun back() {}
        override fun refresh() {}
        override fun deleteQueued(value: Boolean) {}
        override fun askRemove() {}
        override fun cancel() {}
        override fun confirm() {}
    }

    @Test
    fun removal() = Audit.guard("${m}__remove__build") {
        val plan = ServerRemovalPlan("t", "Home laptop", queued = 3, uncertain = 1, drafts = 2, active = true)
        shot("remove", "loading") { Secrets { KitSheetHost { ServerRemovalScreen(ServerRemovalUi(loading = true), RemovalActions()) } } }
        shot("remove", "consequences") {
            Secrets { KitSheetHost { ServerRemovalScreen(ServerRemovalUi(loading = false, plan = plan), RemovalActions()) } }
        }
        shot("remove", "confirm") {
            Secrets { KitSheetHost { ServerRemovalScreen(ServerRemovalUi(loading = false, plan = plan, confirming = true), RemovalActions()) } }
        }
        shot("remove", "nothing-pending") {
            Secrets { KitSheetHost { ServerRemovalScreen(ServerRemovalUi(loading = false, plan = plan.copy(queued = 0, uncertain = 0, drafts = 0, active = false)), RemovalActions()) } }
        }
        shot("remove", "failed") {
            Secrets { KitSheetHost { ServerRemovalScreen(ServerRemovalUi(loading = false, failure = Failure.Unknown()), RemovalActions()) } }
        }
    }

    private class PermissionActions : SavedPermissionsActions {
        override fun back() {}
        override fun refresh() {}
        override fun askRevoke(id: String) {}
        override fun cancelRevoke() {}
        override fun confirmRevoke() {}
        override fun dismissRevoked() {}
    }

    @Test
    fun savedPermissions() = Audit.guard("${m}__permissions__build") {
        val list = persistentListOf(
            SavedPermission("1", "proj", "edit", "src/**"),
            SavedPermission("2", "proj", "bash", "git status"),
            SavedPermission("3", "proj", "read", "a/very/long/resource/path/that/keeps/going/and/going/until/it/cannot/fit/on/a/line.kt"),
        )
        shot("permissions", "loading") { Secrets { KitSheetHost { SavedPermissionsScreen(SavedPermissionsUi(loading = true), PermissionActions()) } } }
        shot("permissions", "list") { Secrets { KitSheetHost { SavedPermissionsScreen(SavedPermissionsUi(loading = false, permissions = list), PermissionActions()) } } }
        shot("permissions", "empty") { Secrets { KitSheetHost { SavedPermissionsScreen(SavedPermissionsUi(loading = false), PermissionActions()) } } }
        shot("permissions", "confirm-revoke") {
            Secrets { KitSheetHost { SavedPermissionsScreen(SavedPermissionsUi(loading = false, permissions = list, confirm = list[0]), PermissionActions()) } }
        }
        shot("permissions", "error") {
            Secrets { KitSheetHost { SavedPermissionsScreen(SavedPermissionsUi(loading = false, failure = Failure.Network(NetworkWhy.Offline)), PermissionActions()) } }
        }
    }

    private class SetupActions : ServerSetupActions {
        override fun back() {}
        override fun refresh() {}
        override fun choose(intent: SetupIntent) {}
        override fun propose(values: Map<String, String>) {}
        override fun optIn(enabled: Boolean) {}
        override fun refreshRegistry(query: String) {}
    }

    @Test
    fun serverSetup() = Audit.guard("${m}__server-setup__build") {
        shot("server-setup", "unavailable") { Secrets { ServerSetupScreen(ServerSetupUi(), SetupActions()) } }
        shot("server-setup", "busy") { Secrets { ServerSetupScreen(ServerSetupUi(busy = true), SetupActions()) } }
        shot("server-setup", "failed") { Secrets { ServerSetupScreen(ServerSetupUi(failed = true), SetupActions()) } }
        shot("server-setup", "available") {
            Secrets { ServerSetupScreen(ServerSetupUi(available = true, config = "{\n  \"model\": \"provider/model\"\n}"), SetupActions()) }
        }
    }

    private class Camera(private val stage: ScanStage) : QrScannerSlot {
        override val supported: Boolean = true

        @Composable
        override fun Viewfinder(
            handle: QrScannerHandle,
            accept: (String) -> Boolean,
            onCode: (String) -> Unit,
            onRejected: () -> Unit,
            modifier: Modifier,
        ) {
            SideEffect { handle.stage = stage }
            Box(modifier.testTag("fake-camera"))
        }
    }

    private class ScanActions : PairingScannerActions {
        override fun close() {}
        override fun scanned(text: String) {}
        override fun rejected() {}
        override fun pasted(text: String?) {}
    }

    @Test
    fun pairingScanner() = Audit.guard("${m}__pairing-scanner__build") {
        shot("pairing-scanner", "scanning") { Secrets { PairingScannerScreen(PairingScannerUi(), ScanActions(), Camera(ScanStage.Scanning)) { false } } }
        shot("pairing-scanner", "starting") { Secrets { PairingScannerScreen(PairingScannerUi(), ScanActions(), Camera(ScanStage.Starting)) { false } } }
        shot("pairing-scanner", "denied") { Secrets { PairingScannerScreen(PairingScannerUi(), ScanActions(), Camera(ScanStage.Denied)) { false } } }
        shot("pairing-scanner", "blocked") { Secrets { PairingScannerScreen(PairingScannerUi(), ScanActions(), Camera(ScanStage.Blocked)) { false } } }
        shot("pairing-scanner", "not-a-code") { Secrets { PairingScannerScreen(PairingScannerUi(rejected = true), ScanActions(), Camera(ScanStage.Scanning)) { false } } }
        shot("pairing-scanner", "bad-code") { Secrets { PairingScannerScreen(PairingScannerUi(problem = PairingProblem.PasswordMissing), ScanActions(), Camera(ScanStage.Scanning)) { false } } }
    }
}
