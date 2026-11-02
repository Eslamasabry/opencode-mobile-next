package io.github.eslamasabry.oc.feature.git.presentation

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.git.GitFailure
import io.github.eslamasabry.oc.engine.git.GitHubSignIn
import io.github.eslamasabry.oc.engine.git.GitHubSignInControl
import io.github.eslamasabry.oc.engine.git.GitPage
import io.github.eslamasabry.oc.engine.git.GitPageState
import io.github.eslamasabry.oc.engine.git.GitPageView
import io.github.eslamasabry.oc.engine.git.GitServices
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** The app-side helpers the presenter uses; [GitServices] in the app, plain lambdas in tests. */
public class GitPagePorts(
    public val details: (GitFailure?) -> Redacted?,
    public val signInDetails: (String) -> Redacted?,
    public val openVerification: (String) -> Boolean,
    public val manageAccess: () -> Boolean,
) {
    public constructor(services: GitServices) :
        this(services::details, services::details, services::openVerification, services::openManageAccess)
}

/**
 * The Git page's behaviour (port of lib/ui/git/git_sheet*.dart over lib/state/git_page_controller.dart). Every changed
 * file starts ticked except a conflicted one; the person unticks what to leave out. Writes are ignored while another
 * action runs (the engine admits one at a time), so a double tap never queues a second commit.
 */
public class GitPresenter(
    private val page: GitPage,
    private val account: GitHubSignInControl,
    private val ports: GitPagePorts,
    private val launch: (suspend () -> Unit) -> Unit,
) {
    public constructor(page: GitPage, account: GitHubSignInControl, services: GitServices, launch: (suspend () -> Unit) -> Unit) :
        this(page, account, GitPagePorts(services), launch)

    private data class Local(
        val unticked: Set<String> = emptySet(),
        val sheet: GitSheet? = null,
        val confirm: GitConfirm? = null,
    )

    private val local = MutableStateFlow(Local())

    public val states: Flow<GitUi> = combine(page.state, account.state, local) { p, a, l -> build(p, a, l) }

    public fun initial(): GitUi = build(page.state.value, account.state.value, local.value)

    public fun start() {
        launch { page.refresh() }
        launch { account.load() }
    }

    private fun build(p: GitPageState, a: GitHubSignIn, l: Local): GitUi {
        val files = (p.view as? GitPageView.Ready)?.snapshot?.status?.files.orEmpty()
        val selected = files.filter { !it.isConflicted && it.path !in l.unticked }.map { it.path }.toImmutableSet()
        return GitUi(
            view = p.view,
            busy = p.busy,
            result = p.result,
            resultDetails = ports.details(p.result?.failure),
            savedNotSent = p.savedNotSent,
            refreshFailure = p.refreshFailure,
            refreshDetails = ports.details(p.refreshFailure),
            viewDetails = ports.details((p.view as? GitPageView.Failed)?.failure),
            selected = selected,
            diff = p.diff,
            signIn = a,
            signInDetails = (a as? GitHubSignIn.SignedOut)?.details?.let(ports.signInDetails),
            placeName = p.placeName,
            sheet = l.sheet,
            confirm = l.confirm,
            generation = p.generation,
        )
    }

    private fun current(): GitUi = initial()

    public fun refresh() {
        launch { page.refresh() }
    }

    public fun pull() {
        if (current().working) return
        launch { page.pull() }
    }

    public fun push() {
        if (current().working) return
        launch { page.push() }
    }

    public fun toggleFile(path: String) {
        local.update { if (path in it.unticked) it.copy(unticked = it.unticked - path) else it.copy(unticked = it.unticked + path) }
    }

    public fun commit(message: String) {
        val now = current()
        if (now.working) return
        val paths = now.selected.toList()
        launch {
            page.commit(paths, message)
            local.update { it.copy(unticked = emptySet()) }
        }
    }

    public fun commitAndPush(message: String) {
        val now = current()
        if (now.working) return
        val paths = now.selected.toList()
        launch {
            page.commitAndPush(paths, message)
            local.update { it.copy(unticked = emptySet()) }
        }
    }

    public fun openBranches() {
        local.update { it.copy(sheet = GitSheet.Branches) }
    }

    public fun closeSheet() {
        local.update { it.copy(sheet = null) }
    }

    public fun switchBranch(name: String) {
        if (current().working) return
        local.update { it.copy(sheet = null) }
        launch { page.switchBranch(name) }
    }

    public fun createBranch(name: String) {
        if (current().working) return
        local.update { it.copy(sheet = null) }
        launch { page.createBranch(name.trim()) }
    }

    public fun openDiff(path: String) {
        val file = current().snapshot?.status?.files?.firstOrNull { it.path == path } ?: return
        launch { page.openDiff(file) }
    }

    public fun closeDiff() {
        page.closeDiff()
    }

    public fun askDiscard(path: String) {
        val file = current().snapshot?.status?.files?.firstOrNull { it.path == path } ?: return
        local.update { it.copy(confirm = GitConfirm.Discard(file)) }
    }

    public fun confirm() {
        val asked = local.value.confirm ?: return
        local.update { it.copy(confirm = null) }
        when (asked) {
            is GitConfirm.Discard -> {
                page.closeDiff()
                launch { page.discard(asked.file) }
            }
            GitConfirm.SignOut -> launch { account.signOut() }
        }
    }

    public fun dismissConfirm() {
        local.update { it.copy(confirm = null) }
    }

    public fun setIdentity(name: String, email: String) {
        if (current().working) return
        launch { page.setIdentity(name, email) }
    }

    public fun signIn() {
        account.startSignIn()
    }

    public fun cancelSignIn() {
        account.cancel()
    }

    public fun askSignOut() {
        local.update { it.copy(confirm = GitConfirm.SignOut) }
    }

    public fun openGitHub() {
        val waiting = account.state.value as? GitHubSignIn.WaitingForUser ?: return
        ports.openVerification(waiting.verificationUri)
    }

    public fun manageGitHubAccess() {
        ports.manageAccess()
    }

    public fun dismissResult() {
        page.dismissResult()
    }
}
