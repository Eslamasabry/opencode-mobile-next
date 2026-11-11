package io.github.eslamasabry.oc.feature.review.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.feature.review.ReviewAgent
import io.github.eslamasabry.oc.feature.review.ReviewDrafts
import io.github.eslamasabry.oc.feature.review.ReviewHub
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import kotlinx.coroutines.flow.StateFlow

/** Maps the pages' destinations to routes; [messageId] names the change set a file opens from. */
private class RouteNavigation(private val nav: AppNavigator, private val ref: ConversationRef, private val messageId: String?) : ReviewNavigation {
    override fun back(): Unit = nav.back()

    override fun openFile(path: String, staged: Boolean): Unit = nav.open(ReviewRoute.File(ref, path, messageId, staged))

    override fun openWorkingTreeFile(path: String): Unit = nav.open(ReviewRoute.File(ref, path, messageId, workingTree = true))

    // ReviewRoute.File has no branch field yet (:ui:routes is not this module's): the reserved id names the source.
    override fun openBranchFile(path: String): Unit = nav.open(ReviewRoute.File(ref, path, BRANCH_FILE))

    override fun openStaged(): Unit = nav.open(ReviewRoute.Staged(ref))
}

/** The [ReviewRoute.File.messageId] that names the whole branch's diff (never a server message id: they have no NUL). */
internal const val BRANCH_FILE: String = "\u0000branch"

/** "Changes": one per entry; all behaviour is [ChangesPresenter]'s. */
@AssistedInject
public class ChangesViewModel(
    @Assisted route: ReviewRoute.Changes,
    hub: ReviewHub,
    nav: AppNavigator,
    drafts: ReviewDrafts,
    agent: ReviewAgent,
    kept: Set<ReviewLastKnown> = emptySet(),
) : ViewModel(), ChangesActions {
    private val presenter =
        hub.notes(route.ref).let { notes ->
            ChangesPresenter(
                hub.review(route.ref),
                route.messageId,
                RouteNavigation(nav, route.ref, route.messageId),
                viewModelScope,
                drafts.sendBack(route.ref),
                ReviewAgentPorts(notes.comments, agent.sender(route.ref), agent.guideAsk(route.ref), notes.guides),
                hub.branch(route.ref),
                kept.firstOrNull()?.let { ReviewShelf(it, route.ref) },
                hub.conversationSetup(route.ref),
            )
        }
    public val state: StateFlow<ChangesUi> = presenter.state

    override fun back(): Unit = presenter.back()

    override fun retry(): Unit = presenter.retry()

    override fun openFile(path: String): Unit = presenter.openFile(path)

    override fun keepAll(): Unit = presenter.keepAll()

    override fun undoAll(): Unit = presenter.undoAll()

    override fun confirmUndoAll(): Unit = presenter.confirmUndoAll()

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    override fun openStaged(): Unit = presenter.openStaged()

    override fun chooseWorkingTree(on: Boolean): Unit = presenter.chooseWorkingTree(on)

    override fun chooseScope(scope: ReviewScope): Unit = presenter.chooseScope(scope)

    override fun sendBack(): Unit = presenter.sendBack()

    override fun sendBackNote(note: String?): Unit = presenter.sendBackNote(note)

    override fun sendComments(): Unit = presenter.sendComments()

    override fun guided(on: Boolean): Unit = presenter.guided(on)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ReviewRoute.Changes): ChangesViewModel
    }
}

/** One file's diff. */
@AssistedInject
public class FileDiffViewModel(
    @Assisted route: ReviewRoute.File,
    hub: ReviewHub,
    private val nav: AppNavigator,
    agent: ReviewAgent,
    drafts: ReviewDrafts,
    kept: Set<ReviewLastKnown> = emptySet(),
) : ViewModel(), FileDiffActions {
    private val presenter =
        FileDiffPresenter(
            hub.review(route.ref),
            route.path,
            route.messageId.takeUnless { it == BRANCH_FILE },
            when {
                route.staged -> FileDiffSource.Staged
                route.workingTree -> FileDiffSource.WorkingTree
                route.messageId == BRANCH_FILE -> FileDiffSource.Branch
                else -> FileDiffSource.Changes
            },
            viewModelScope,
            ReviewAgentPorts(hub.notes(route.ref).comments, agent.sender(route.ref)),
            hub.branch(route.ref),
            kept.firstOrNull()?.let { ReviewShelf(it, route.ref) },
            drafts.addFile(route.ref).takeUnless { route.staged },
        )
    public val state: StateFlow<FileDiffUi> = presenter.state

    override fun back(): Unit = nav.back()

    override fun retry(): Unit = presenter.retry()

    override fun comment(anchor: CommentAnchor?): Unit = presenter.comment(anchor)

    override fun commentText(text: String?): Unit = presenter.commentText(text)

    override fun removeComment(id: String): Unit = presenter.removeComment(id)

    override fun sendComments(): Unit = presenter.sendComments()

    override fun addToPrompt(): Unit = presenter.addToPrompt()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ReviewRoute.File): FileDiffViewModel
    }
}

/** "Undo from this prompt?". */
@AssistedInject
public class UndoFromHereViewModel(@Assisted route: ReviewRoute.UndoFromHere, hub: ReviewHub, nav: AppNavigator) : ViewModel(), UndoFromHereActions {
    private val presenter = UndoFromHerePresenter(hub.review(route.ref), route.messageId, RouteNavigation(nav, route.ref, null), viewModelScope)
    public val state: StateFlow<UndoFromHereUi> = presenter.state

    override fun back(): Unit = presenter.back()

    override fun putFilesBack(apply: Boolean): Unit = presenter.putFilesBack(apply)

    override fun confirm(): Unit = presenter.confirm()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ReviewRoute.UndoFromHere): UndoFromHereViewModel
    }
}

/** "Review the undo". */
@AssistedInject
public class StagedViewModel(
    @Assisted route: ReviewRoute.Staged,
    hub: ReviewHub,
    nav: AppNavigator,
    kept: Set<ReviewLastKnown> = emptySet(),
) : ViewModel(), StagedActions {
    private val presenter =
        StagedPresenter(
            hub.review(route.ref),
            RouteNavigation(nav, route.ref, null),
            viewModelScope,
            kept.firstOrNull()?.let { ReviewShelf(it, route.ref) },
        )
    public val state: StateFlow<StagedUi> = presenter.state

    override fun back(): Unit = presenter.back()

    override fun retry(): Unit = presenter.retry()

    override fun openFile(path: String): Unit = presenter.openFile(path)

    override fun reviewLatest(): Unit = presenter.reviewLatest()

    override fun askKeep(): Unit = presenter.askKeep()

    override fun askPutBack(): Unit = presenter.askPutBack()

    override fun confirm(): Unit = presenter.confirm()

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ReviewRoute.Staged): StagedViewModel
    }
}

/** Maps a project review's destinations to routes: only its working-tree files open. */
private class ProjectNavigation(private val nav: AppNavigator, private val route: ReviewRoute.Project) : ReviewNavigation {
    override fun back(): Unit = nav.back()

    override fun openFile(path: String, staged: Boolean): Unit = openWorkingTreeFile(path)

    override fun openWorkingTreeFile(path: String): Unit = nav.open(ReviewRoute.ProjectFile(route.profile, route.directory, path))

    override fun openStaged(): Unit = Unit
}

/** "Changes" of a whole project opened from Files, with no conversation. */
@AssistedInject
public class ProjectChangesViewModel(
    @Assisted route: ReviewRoute.Project,
    hub: ReviewHub,
    nav: AppNavigator,
) : ViewModel(), ChangesActions {
    private val presenter = ProjectChangesPresenter(hub.projectTree(route.profile, route.directory), ProjectNavigation(nav, route), viewModelScope, hub.projectSetup(route.profile, route.directory))
    public val state: StateFlow<ChangesUi> = presenter.state

    override fun back(): Unit = presenter.back()

    override fun retry(): Unit = presenter.retry()

    override fun openFile(path: String): Unit = presenter.openFile(path)

    override fun keepAll(): Unit = presenter.keepAll()

    override fun undoAll(): Unit = presenter.undoAll()

    override fun confirmUndoAll(): Unit = presenter.confirmUndoAll()

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    override fun openStaged(): Unit = presenter.openStaged()

    override fun sendBack(): Unit = presenter.sendBack()

    override fun sendBackNote(note: String?): Unit = presenter.sendBackNote(note)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ReviewRoute.Project): ProjectChangesViewModel
    }
}

/** One file of a project's uncommitted changes. */
@AssistedInject
public class ProjectFileDiffViewModel(
    @Assisted route: ReviewRoute.ProjectFile,
    hub: ReviewHub,
    private val nav: AppNavigator,
) : ViewModel(), FileDiffActions {
    private val presenter = ProjectFileDiffPresenter(hub.projectTree(route.profile, route.directory), route.path, viewModelScope)
    public val state: StateFlow<FileDiffUi> = presenter.state

    override fun back(): Unit = nav.back()

    override fun retry(): Unit = presenter.retry()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ReviewRoute.ProjectFile): ProjectFileDiffViewModel
    }
}
