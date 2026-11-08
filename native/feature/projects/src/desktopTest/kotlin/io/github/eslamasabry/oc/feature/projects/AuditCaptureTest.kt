package io.github.eslamasabry.oc.feature.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageBlock
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.files.ProjectScanEnd
import io.github.eslamasabry.oc.feature.projects.presentation.ChatTitle
import io.github.eslamasabry.oc.feature.projects.presentation.FolderRowUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectActions
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectDialog
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectStep
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenRecentUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenServerStatus
import io.github.eslamasabry.oc.feature.projects.presentation.OpenServerUi
import io.github.eslamasabry.oc.feature.projects.presentation.PhoneFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectAttentionUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectChatUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPagePhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectStorageActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectStorageStatus
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectStorageUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerUi
import io.github.eslamasabry.oc.feature.projects.presentation.ServerChoiceUi
import io.github.eslamasabry.oc.feature.projects.terminal.TerminalActions
import io.github.eslamasabry.oc.feature.projects.terminal.TerminalPhase
import io.github.eslamasabry.oc.feature.projects.terminal.TerminalScreen
import io.github.eslamasabry.oc.feature.projects.terminal.TerminalUi
import io.github.eslamasabry.oc.feature.projects.ui.OpenProjectScreen
import io.github.eslamasabry.oc.feature.projects.ui.ProjectPageScreen
import io.github.eslamasabry.oc.feature.projects.ui.ProjectStorageScreen
import io.github.eslamasabry.oc.feature.projects.ui.ProjectsPickerScreen
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Audit capture only: the project screens in every state their models allow. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "projects"

    private inline fun <reified T : Any> noop(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "NoopActions"
                else -> null
            }
        } as T

    private val openActions: OpenProjectActions = noop()
    private val pickerActions: ProjectsPickerActions = noop()
    private val pageActions: ProjectPageActions = noop()
    private val storageActions: ProjectStorageActions = noop()
    private val terminalActions: TerminalActions = noop()

    @Composable
    private fun Wrap(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) { content() }
    }

    private val pc = ProfileId("pc")
    private val longName = "a-really-long-project-folder-name-that-will-not-fit-on-one-line-of-a-phone"

    // ---- Open a project

    private val folders =
        listOf(
            OpenFolderUi("demo", "/root/projects/demo", known = true),
            OpenFolderUi("landing-page", "/root/projects/landing-page", git = true, known = true),
            OpenFolderUi("notes", "/root/projects/notes"),
            OpenFolderUi("opencode-mobile", "/root/projects/opencode-mobile", git = true, known = true),
            OpenFolderUi(longName, "/root/projects/$longName", git = true),
        ).toImmutableList()

    private val projectsPage =
        OpenProjectUi(
            step = OpenProjectStep.Projects,
            serversLoading = false,
            serverName = "This phone",
            onThisPhone = true,
            folders = folders,
            makeIn = "/root/projects",
            canMake = true,
            canClone = true,
        )

    private val inside =
        projectsPage.copy(
            step = OpenProjectStep.Folder,
            path = "/root/projects/opencode-mobile",
            makeIn = "/root/projects/opencode-mobile",
            folders = listOf("android", "assets", "docs", "lib", "packages", "test").map { OpenFolderUi(it, "/root/projects/opencode-mobile/$it") }.toImmutableList(),
        )

    @Test
    fun openProject() = Audit.guard("${m}__open-project__build") {
        Audit.shoot(m, "open-project", "servers-loading") { Wrap { OpenProjectScreen(OpenProjectUi(), openActions) } }
        Audit.shoot(m, "open-project", "servers-none") { Wrap { OpenProjectScreen(OpenProjectUi(serversLoading = false), openActions) } }
        Audit.shoot(m, "open-project", "servers-states") {
            Wrap {
                OpenProjectScreen(
                    OpenProjectUi(
                        serversLoading = false,
                        servers = persistentListOf(
                            OpenServerUi(ProfileId("phone"), "This phone", true, OpenServerStatus.Connected),
                            OpenServerUi(pc, "studio-pc", false, OpenServerStatus.Connecting),
                            OpenServerUi(ProfileId("vps"), "vps-with-a-quite-long-display-name-for-wrapping", false, OpenServerStatus.NotConnected),
                        ),
                    ),
                    openActions,
                )
            }
        }
        Audit.shoot(m, "open-project", "servers-recent") {
            Wrap {
                OpenProjectScreen(
                    OpenProjectUi(
                        serversLoading = false,
                        servers = persistentListOf(OpenServerUi(pc, "studio-pc", false, OpenServerStatus.Connected)),
                        recent = persistentListOf(
                            OpenRecentUi(pc, "app", "/srv/app", serverName = "studio-pc"),
                            OpenRecentUi(pc, longName, "/srv/$longName", serverName = "studio-pc"),
                        ),
                    ),
                    openActions,
                )
            }
        }
        Audit.shoot(m, "open-project", "projects") { Wrap { OpenProjectScreen(projectsPage, openActions) } }
        Audit.shoot(m, "open-project", "projects-empty") { Wrap { OpenProjectScreen(projectsPage.copy(folders = persistentListOf()), openActions) } }
        Audit.shoot(m, "open-project", "projects-no-make") { Wrap { OpenProjectScreen(projectsPage.copy(canMake = false, canClone = false, makeIn = null), openActions) } }
        Audit.shoot(m, "open-project", "projects-opened-before") {
            Wrap { OpenProjectScreen(projectsPage.copy(openedBefore = folders.take(2).toImmutableList()), openActions) }
        }
        Audit.shoot(m, "open-project", "folder") { Wrap { OpenProjectScreen(inside, openActions) } }
        Audit.shoot(m, "open-project", "folder-loading") { Wrap { OpenProjectScreen(inside.copy(folders = persistentListOf(), loading = true), openActions) } }
        Audit.shoot(m, "open-project", "folder-slow") { Wrap { OpenProjectScreen(inside.copy(folders = persistentListOf(), loading = true, slow = true), openActions) } }
        Audit.shoot(m, "open-project", "folder-empty") { Wrap { OpenProjectScreen(inside.copy(folders = persistentListOf()), openActions) } }
        Audit.shoot(m, "open-project", "folder-denied") {
            Wrap { OpenProjectScreen(inside.copy(folders = persistentListOf(), failure = Failure.Folder(FolderProblem.Denied)), openActions) }
        }
        Audit.shoot(m, "open-project", "folder-missing") {
            Wrap { OpenProjectScreen(inside.copy(folders = persistentListOf(), failure = Failure.Folder(FolderProblem.Missing)), openActions) }
        }
        Audit.shoot(m, "open-project", "folder-network-error") {
            Wrap { OpenProjectScreen(inside.copy(folders = persistentListOf(), failure = Failure.Network()), openActions) }
        }
        Audit.shoot(m, "open-project", "folder-long-path") {
            Wrap { OpenProjectScreen(inside.copy(path = "/root/projects/$longName/$longName"), openActions) }
        }
        Audit.shoot(m, "open-project", "dialog-new-folder") { Wrap { OpenProjectScreen(projectsPage.copy(dialog = OpenProjectDialog.NewFolder), openActions) } }
        Audit.shoot(m, "open-project", "dialog-clone") { Wrap { OpenProjectScreen(projectsPage.copy(dialog = OpenProjectDialog.Clone), openActions) } }
        Audit.shoot(m, "open-project", "dialog-path") {
            Wrap { OpenProjectScreen(projectsPage.copy(dialog = OpenProjectDialog.Path, pathStart = "/root/projects/"), openActions) }
        }
        Audit.shoot(m, "open-project", "dialog-working") {
            Wrap { OpenProjectScreen(projectsPage.copy(dialog = OpenProjectDialog.NewFolder, working = true), openActions) }
        }
        Audit.shoot(m, "open-project", "dialog-failed") {
            Wrap {
                OpenProjectScreen(projectsPage.copy(dialog = OpenProjectDialog.NewFolder, dialogFailure = Failure.Folder(FolderProblem.Denied)), openActions)
            }
        }
        Audit.shoot(m, "open-project", "missing-path") {
            Wrap { OpenProjectScreen(projectsPage.copy(dialog = OpenProjectDialog.Path, missingPath = "/root/projects/new-thing"), openActions) }
        }
    }

    // ---- Projects picker

    private val rows =
        persistentListOf(
            FolderRowUi("/work/app", "app", conversations = 4, running = 1, isGit = true),
            FolderRowUi("/work/site", "site", conversations = 0, isGit = false),
            FolderRowUi("/work/$longName", longName, conversations = 12, running = 2, isGit = true),
        )

    private val picker = ProjectsPickerUi(phase = ProjectsPhase.Ready, serverName = "studio-pc", projects = rows, canCreate = true)

    @Test
    fun picker() = Audit.guard("${m}__picker__build") {
        Audit.shoot(m, "picker", "no-server") { Wrap { ProjectsPickerScreen(ProjectsPickerUi(), pickerActions) } }
        Audit.shoot(m, "picker", "ready") { Wrap { ProjectsPickerScreen(picker, pickerActions) } }
        Audit.shoot(m, "picker", "loading") { Wrap { ProjectsPickerScreen(picker.copy(projects = persistentListOf(), projectsLoading = true), pickerActions) } }
        Audit.shoot(m, "picker", "empty") { Wrap { ProjectsPickerScreen(picker.copy(projects = persistentListOf()), pickerActions) } }
        Audit.shoot(m, "picker", "failed") { Wrap { ProjectsPickerScreen(picker.copy(projects = persistentListOf(), projectsFailure = Failure.Network()), pickerActions) } }
        Audit.shoot(m, "picker", "opened-before") { Wrap { ProjectsPickerScreen(picker.copy(openedBefore = rows.take(2).toImmutableList()), pickerActions) } }
        Audit.shoot(m, "picker", "several-servers") {
            Wrap {
                ProjectsPickerScreen(
                    picker.copy(
                        servers = persistentListOf(
                            ServerChoiceUi(pc, "studio-pc", true),
                            ServerChoiceUi(ProfileId("vps"), "vps", false),
                            ServerChoiceUi(ProfileId("phone"), "This phone", false),
                        ),
                    ),
                    pickerActions,
                )
            }
        }
        Audit.shoot(m, "picker", "search") { Wrap { ProjectsPickerScreen(picker.copy(showSearch = true, query = "app"), pickerActions) } }
        Audit.shoot(m, "picker", "phone-scan-offered") {
            Wrap { ProjectsPickerScreen(picker.copy(canFindPhoneProjects = true, canBrowsePhone = true, canBrowseBuiltin = true), pickerActions) }
        }
        Audit.shoot(m, "picker", "phone-scanning") {
            Wrap { ProjectsPickerScreen(picker.copy(canFindPhoneProjects = true, findingPhoneProjects = true), pickerActions) }
        }
        Audit.shoot(m, "picker", "phone-scan-found") {
            Wrap {
                ProjectsPickerScreen(
                    picker.copy(canFindPhoneProjects = true, phoneProjects = rows, scanEnd = ProjectScanEnd.Completed),
                    pickerActions,
                )
            }
        }
        Audit.shoot(m, "picker", "phone-scan-timed-out") {
            Wrap { ProjectsPickerScreen(picker.copy(canFindPhoneProjects = true, phoneProjects = rows, scanEnd = ProjectScanEnd.TimedOut), pickerActions) }
        }
        Audit.shoot(m, "picker", "phone-scan-limited") {
            Wrap { ProjectsPickerScreen(picker.copy(canFindPhoneProjects = true, phoneProjects = rows, scanEnd = ProjectScanEnd.Limited), pickerActions) }
        }
        Audit.shoot(m, "picker", "dialog-path") {
            Wrap { ProjectsPickerScreen(picker.copy(askingPath = true, openedBefore = rows.take(1).toImmutableList(), pathStart = "/work/"), pickerActions) }
        }
        Audit.shoot(m, "picker", "dialog-create") {
            Wrap { ProjectsPickerScreen(picker.copy(askingCreate = true, createName = "my-new-project"), pickerActions) }
        }
        Audit.shoot(m, "picker", "dialog-create-failed") {
            Wrap {
                ProjectsPickerScreen(
                    picker.copy(askingCreate = true, createName = "x", folderFailure = Failure.Folder(FolderProblem.Denied)),
                    pickerActions,
                )
            }
        }
        Audit.shoot(m, "picker", "dialog-missing-path") {
            Wrap { ProjectsPickerScreen(picker.copy(askingPath = true, missingPath = "/work/new-thing"), pickerActions) }
        }
        Audit.shoot(m, "picker", "dialog-rename") {
            Wrap { ProjectsPickerScreen(picker.copy(canRename = true, renaming = rows[0]), pickerActions) }
        }
    }

    @Test
    fun pickerPhoneFolders() = Audit.guard("${m}__picker-phone__build") {
        val phone = picker.copy(
            canBrowsePhone = true,
            phonePath = "/storage/emulated/0/app",
            phoneParent = "/storage/emulated/0",
            phoneFolders = persistentListOf(
                PhoneFolderUi("src", "/storage/emulated/0/app/src", "main.kt"),
                PhoneFolderUi("docs", "/storage/emulated/0/app/docs"),
                PhoneFolderUi(longName, "/storage/emulated/0/app/$longName", "README.md"),
            ),
        )
        Audit.shoot(m, "phone-folders", "listing") { Wrap { ProjectsPickerScreen(phone, pickerActions) } }
        Audit.shoot(m, "phone-folders", "root-empty") { Wrap { ProjectsPickerScreen(picker.copy(phonePath = "/storage/emulated/0"), pickerActions) } }
        Audit.shoot(m, "phone-folders", "loading") { Wrap { ProjectsPickerScreen(phone.copy(phoneFolders = persistentListOf(), phoneLoading = true), pickerActions) } }
        Audit.shoot(m, "phone-folders", "missing") {
            Wrap { ProjectsPickerScreen(phone.copy(phoneFolders = persistentListOf(), phoneFailure = Failure.Folder(FolderProblem.Missing)), pickerActions) }
        }
        Audit.shoot(m, "phone-folders", "denied") {
            Wrap { ProjectsPickerScreen(phone.copy(phoneFolders = persistentListOf(), phoneFailure = Failure.Folder(FolderProblem.Denied)), pickerActions) }
        }
        Audit.shoot(m, "phone-folders", "builtin") { Wrap { ProjectsPickerScreen(phone.copy(browsingBuiltin = true, canBrowseBuiltin = true), pickerActions) } }
    }

    // ---- Project page

    private fun ref(id: String) = ConversationRef(pc, SessionId(id))

    private val chats =
        persistentListOf(
            ProjectChatUi(ref("s1"), ChatTitle.Text("Fix the login redirect loop"), "app", running = true),
            ProjectChatUi(ref("s2"), ChatTitle.Text("Write release notes for the March release candidate and send them to the whole team"), "app", running = false),
            ProjectChatUi(ref("s3"), ChatTitle.New, "app", running = false),
            ProjectChatUi(ref("s4"), ChatTitle.Untitled, "app", running = false),
        )

    private val page =
        ProjectPageUi(directory = "/srv/app", name = "app", serverName = "studio-pc", phase = ProjectPagePhase.Ready, conversations = chats)

    @Test
    fun projectPage() = Audit.guard("${m}__project-page__build") {
        Audit.shoot(m, "project-page", "opening") { Wrap { ProjectPageScreen(ProjectPageUi("/srv/app", "app"), pageActions) } }
        Audit.shoot(m, "project-page", "no-server") { Wrap { ProjectPageScreen(page.copy(phase = ProjectPagePhase.NoServer, conversations = persistentListOf()), pageActions) } }
        Audit.shoot(m, "project-page", "failed") {
            Wrap { ProjectPageScreen(page.copy(phase = ProjectPagePhase.Failed, failure = Failure.Network(), conversations = persistentListOf()), pageActions) }
        }
        Audit.shoot(m, "project-page", "ready") { Wrap { ProjectPageScreen(page, pageActions) } }
        Audit.shoot(m, "project-page", "ready-all-tools") {
            Wrap {
                ProjectPageScreen(
                    page.copy(canBrowseFiles = true, canHealth = true, canSchedule = true, canStartIsolated = true, changesCount = 7, devServerPort = 3000),
                    pageActions,
                    onDevServices = {},
                    onTerminal = {},
                )
            }
        }
        Audit.shoot(m, "project-page", "empty") { Wrap { ProjectPageScreen(page.copy(conversations = persistentListOf()), pageActions) } }
        Audit.shoot(m, "project-page", "creating") { Wrap { ProjectPageScreen(page.copy(creating = true), pageActions) } }
        Audit.shoot(m, "project-page", "create-failed") { Wrap { ProjectPageScreen(page.copy(createFailure = Failure.Network()), pageActions) } }
        Audit.shoot(m, "project-page", "load-more") { Wrap { ProjectPageScreen(page.copy(canLoadMore = true), pageActions) } }
        Audit.shoot(m, "project-page", "loading-more") { Wrap { ProjectPageScreen(page.copy(canLoadMore = true, loadingMore = true), pageActions) } }
        Audit.shoot(m, "project-page", "load-more-failed") { Wrap { ProjectPageScreen(page.copy(canLoadMore = true, loadMoreFailure = Failure.Network()), pageActions) } }
        Audit.shoot(m, "project-page", "other-projects") {
            Wrap {
                ProjectPageScreen(
                    page.copy(
                        elsewhere = persistentListOf(ProjectChatUi(ref("o1"), ChatTitle.Text("Review the docs"), "site", running = true)),
                        elsewhereAttention = persistentListOf(
                            ProjectAttentionUi("/srv/site", "site", waiting = 2, running = 1),
                            ProjectAttentionUi("/srv/api", "api", waiting = 0, running = 0, failed = 1),
                            ProjectAttentionUi("/srv/old", "old", waiting = 1, running = 0, stale = true),
                        ),
                    ),
                    pageActions,
                )
            }
        }
        Audit.shoot(m, "project-page", "copies") {
            Wrap {
                ProjectPageScreen(
                    page.copy(
                        canStartIsolated = true,
                        copies = listOf(WorktreeInfo("review-copy", "/work/review-copy", "review", ready = true), WorktreeInfo("spike", "/work/spike", null, ready = false)),
                    ),
                    pageActions,
                )
            }
        }
        Audit.shoot(m, "project-page", "copies-loading") { Wrap { ProjectPageScreen(page.copy(canStartIsolated = true, copiesLoading = true), pageActions) } }
        Audit.shoot(m, "project-page", "copies-failed") { Wrap { ProjectPageScreen(page.copy(canStartIsolated = true, copiesFailure = Failure.Network()), pageActions) } }
        Audit.shoot(m, "project-page", "isolated-task-form") {
            Wrap { ProjectPageScreen(page.copy(canStartIsolated = true, isolatedShown = true, isolatedTask = "Try the new parser", isolatedName = "parser-spike"), pageActions) }
        }
        Audit.shoot(m, "project-page", "storage-blocked") {
            Wrap { ProjectPageScreen(page.copy(storageBlock = SharedStorageBlock.AppAccess), pageActions) }
        }
        Audit.shoot(m, "project-page", "long-project-name") {
            Wrap { ProjectPageScreen(page.copy(name = longName, directory = "/srv/$longName"), pageActions) }
        }
    }

    @Test
    fun workspaceSheet() = Audit.guard("${m}__workspace__build") {
        val tree = WorktreeInfo("review-copy", "/work/review-copy", "review", ready = true)
        val ws = ProjectWorkspaceUi(
            available = true,
            visible = true,
            canReadChanges = true,
            canReadWorktrees = true,
            canCreate = true,
            health = VersionControlHealth("main", "main", persistentListOf(VersionControlFile("lib/a.kt", "modified", 3, 1)), VersionControlSetupState.Git),
            changes = persistentListOf(FileDiff("lib/a.kt", patch = "@@ -1 +1 @@\n-old\n+new", additions = 1, deletions = 1, status = "modified")),
            worktrees = persistentListOf(tree, WorktreeInfo("spike", "/work/spike", "spike", ready = false)),
        )
        Audit.shoot(m, "workspace-sheet", "ready") { Wrap { ProjectPageScreen(page.copy(workspace = ws), pageActions) } }
        Audit.shoot(m, "workspace-sheet", "busy") { Wrap { ProjectPageScreen(page.copy(workspace = ws.copy(busy = true)), pageActions) } }
        Audit.shoot(m, "workspace-sheet", "failed") { Wrap { ProjectPageScreen(page.copy(workspace = ws.copy(failure = Failure.Network())), pageActions) } }
        Audit.shoot(m, "workspace-sheet", "empty") {
            Wrap { ProjectPageScreen(page.copy(workspace = ws.copy(changes = persistentListOf(), worktrees = persistentListOf())), pageActions) }
        }
        Audit.shoot(m, "workspace-sheet", "removal") {
            Wrap { ProjectPageScreen(page.copy(workspace = ws.copy(removal = tree, removalChanges = persistentListOf())), pageActions) }
        }
        Audit.shoot(m, "workspace-sheet", "removal-with-changes") {
            Wrap {
                ProjectPageScreen(
                    page.copy(workspace = ws.copy(removal = tree, removalChanges = persistentListOf(VersionControlFile("lib/a.kt", "modified", 3, 1)))),
                    pageActions,
                )
            }
        }
        Audit.shoot(m, "workspace-sheet", "reset") {
            Wrap {
                ProjectPageScreen(
                    page.copy(workspace = ws.copy(canReset = true, resetting = tree, resetChanges = persistentListOf())),
                    pageActions,
                )
            }
        }
    }

    // ---- Storage and terminal

    @Test
    fun storage() = Audit.guard("${m}__storage__build") {
        Audit.shoot(m, "project-storage", "checking") { Wrap { ProjectStorageScreen(ProjectStorageUi(), storageActions) } }
        Audit.shoot(m, "project-storage", "needs-access") {
            Wrap { ProjectStorageScreen(ProjectStorageUi(status = ProjectStorageStatus.NeedsAccess, checking = false), storageActions) }
        }
        Audit.shoot(m, "project-storage", "needs-access-refused") {
            Wrap { ProjectStorageScreen(ProjectStorageUi(status = ProjectStorageStatus.NeedsAccess, checking = false, refused = true), storageActions) }
        }
        Audit.shoot(m, "project-storage", "needs-restart") {
            Wrap { ProjectStorageScreen(ProjectStorageUi(status = ProjectStorageStatus.NeedsRestart(workRunning = false), checking = false), storageActions) }
        }
        Audit.shoot(m, "project-storage", "needs-restart-work-running") {
            Wrap { ProjectStorageScreen(ProjectStorageUi(status = ProjectStorageStatus.NeedsRestart(workRunning = true), checking = false), storageActions) }
        }
        Audit.shoot(m, "project-storage", "failed") {
            Wrap { ProjectStorageScreen(ProjectStorageUi(status = ProjectStorageStatus.NeedsAccess, checking = false, failure = Failure.Network()), storageActions) }
        }
    }

    @Test
    fun terminal() = Audit.guard("${m}__terminal__build") {
        val process = TerminalProcess("term", "build-server", "bash", persistentListOf(), "/project", true, 12, null)
        val ended = TerminalProcess("old", "finished job", "make", persistentListOf(), "/project", false, 99, 1)
        val connected = TerminalUi(
            "/project",
            serverName = "studio-pc",
            phase = TerminalPhase.Connected,
            processes = persistentListOf(process, ended),
            selected = "term",
            transcript = "$ git status\nOn branch main\nnothing to commit, working tree clean\n$ ls\nREADME.md  lib  test\n$ ",
        )
        Audit.shoot(m, "terminal", "connected") { Wrap { TerminalScreen(connected, terminalActions) } }
        Audit.shoot(m, "terminal", "connected-ended-selected") { Wrap { TerminalScreen(connected.copy(selected = "old"), terminalActions) } }
        Audit.shoot(m, "terminal", "opening") { Wrap { TerminalScreen(TerminalUi("/project"), terminalActions) } }
        Audit.shoot(m, "terminal", "unavailable") { Wrap { TerminalScreen(TerminalUi("/project", phase = TerminalPhase.Unavailable), terminalActions) } }
        Audit.shoot(m, "terminal", "ready-no-processes") { Wrap { TerminalScreen(TerminalUi("/project", phase = TerminalPhase.Ready), terminalActions) } }
        Audit.shoot(m, "terminal", "connecting") { Wrap { TerminalScreen(connected.copy(phase = TerminalPhase.Connecting), terminalActions) } }
        Audit.shoot(m, "terminal", "closed") { Wrap { TerminalScreen(connected.copy(phase = TerminalPhase.Closed), terminalActions) } }
        Audit.shoot(m, "terminal", "paused") { Wrap { TerminalScreen(connected.copy(phase = TerminalPhase.Paused), terminalActions) } }
        Audit.shoot(m, "terminal", "failed") { Wrap { TerminalScreen(connected.copy(phase = TerminalPhase.Failed, failure = Failure.Network()), terminalActions) } }
        Audit.shoot(m, "terminal", "stale") { Wrap { TerminalScreen(connected.copy(phase = TerminalPhase.Stale), terminalActions) } }
        Audit.shoot(m, "terminal", "working") { Wrap { TerminalScreen(connected.copy(working = true), terminalActions) } }
        Audit.shoot(m, "terminal", "rename") { Wrap { TerminalScreen(connected.copy(rename = "build-server"), terminalActions) } }
        Audit.shoot(m, "terminal", "remove-confirm") { Wrap { TerminalScreen(connected.copy(removing = persistentListOf(process)), terminalActions) } }
        Audit.shoot(m, "terminal", "control-alt") { Wrap { TerminalScreen(connected.copy(control = true, alt = true), terminalActions) } }
        Audit.shoot(m, "terminal", "tip") { Wrap { TerminalScreen(connected.copy(teaching = true), terminalActions) } }
        Audit.shoot(m, "terminal", "readable-mode") { Wrap { TerminalScreen(connected.copy(accessibleMode = true), terminalActions) } }
        Audit.shoot(m, "terminal", "clipboard-failed") { Wrap { TerminalScreen(connected.copy(clipboardFailed = true), terminalActions) } }
    }
}
