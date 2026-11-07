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

    override fun openStaged(): Unit = nav.open(ReviewRoute.Staged(ref))
}

/** "Changes": one per entry; all behaviour is [ChangesPresenter]'s. */
@AssistedInject
public class ChangesViewModel(
    @Assisted route: ReviewRoute.Changes,
    hub: ReviewHub,
    nav: AppNavigator,
    drafts: ReviewDrafts,
    agent: ReviewAgent,
) : ViewModel(), ChangesActions {
    private val presenter =
        hub.notes(route.ref).let { notes ->
            ChangesPresenter(
                hub.review(route.ref),
                route.messageId,
                RouteNavigation(nav, route.ref, route.messageId),
                viewModelScope,
                drafts.sendBack(route.ref),
                notes.comments,
                agent.sender(route.ref),
                agent.guideAsk(route.ref),
                notes.guides,
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
    nav: AppNavigator,
    agent: ReviewAgent,
) : ViewModel(), FileDiffActions {
    private val presenter =
        FileDiffPresenter(
            hub.review(route.ref),
            route.path,
            route.messageId,
            route.staged,
            viewModelScope,
            route.workingTree,
            hub.notes(route.ref).comments,
            agent.sender(route.ref),
            RouteNavigation(nav, route.ref, route.messageId),
        )
    public val state: StateFlow<FileDiffUi> = presenter.state

    override fun back(): Unit = presenter.back()

    override fun retry(): Unit = presenter.retry()

    override fun comment(anchor: CommentAnchor?): Unit = presenter.comment(anchor)

    override fun commentText(text: String?): Unit = presenter.commentText(text)

    override fun removeComment(id: String): Unit = presenter.removeComment(id)

    override fun sendComments(): Unit = presenter.sendComments()

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
public class StagedViewModel(@Assisted route: ReviewRoute.Staged, hub: ReviewHub, nav: AppNavigator) : ViewModel(), StagedActions {
    private val presenter = StagedPresenter(hub.review(route.ref), RouteNavigation(nav, route.ref, null), viewModelScope)
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
