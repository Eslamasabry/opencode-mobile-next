package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.engine.composer.references.ReviewReference
import io.github.eslamasabry.oc.engine.composer.references.ReviewReferenceKind
import io.github.eslamasabry.oc.engine.composer.references.ReviewReferenceScope
import io.github.eslamasabry.oc.engine.review.ChangeSet
import io.github.eslamasabry.oc.engine.review.ConversationReview
import io.github.eslamasabry.oc.engine.review.StagedReview
import io.github.eslamasabry.oc.engine.review.TurnChanges
import io.github.eslamasabry.oc.engine.review.UndoShape
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Intents of the Changes page, named after what the person wants. */
public interface ChangesActions {
    public fun back()

    public fun retry()

    public fun openFile(path: String)

    /** "Looks good": keep every change and go back. Nothing is sent. */
    public fun keepAll()

    /** "Undo all": asks first. */
    public fun undoAll()

    public fun confirmUndoAll()

    public fun dismissConfirm()

    /** An undo is waiting: review it. */
    public fun openStaged()

    /** "Send back with a note": opens the note field. */
    public fun sendBack()

    /** The note field closed with [note] (null: cancelled). */
    public fun sendBackNote(note: String?)
}

/**
 * Changes of a turn ([messageId] any of its messages) or of the whole conversation (null). The rows are tool
 * evidence from the server's records (B-CHAT-068); "Undo all" stages an undo from the changes' first prompt with
 * files back (Flutter `_undoAllAction`), then opens its review.
 */
public class ChangesPresenter(
    private val review: ConversationReview,
    private val messageId: String?,
    private val nav: ReviewNavigation,
    private val scope: CoroutineScope,
    private val sendBack: ReviewSendBack? = null,
) : ChangesActions {
    private val mutable = MutableStateFlow(ChangesUi(turn = messageId != null, canSendBack = sendBack != null))
    public val state: StateFlow<ChangesUi> = mutable.asStateFlow()
    private var set: ChangeSet = ConversationReview.Empty

    /** The undo boundary the rows were read against: Undo all refuses once it moved (B-CHAT-105). */
    private var basis: Long? = null

    init {
        scope.launch { review.state.collect { s -> mutable.update { it.copy(busy = s.running || s.working, undoAll = undoAll(it.load)) } } }
        load()
    }

    private fun load() {
        mutable.update { it.copy(load = ReviewLoad.Loading) }
        scope.launch {
            review.refresh() // the staged boundary decides Undo all; a failed read leaves it as it was
            when (val read = review.changes(messageId)) {
                is Outcome.Failed -> mutable.update { it.copy(load = ReviewLoad.Failed(read.failure)) }
                is Outcome.Ok -> {
                    set = read.value
                    basis = review.basis()
                    var rows = set.files.map { ChangeRowUi(it.path, it.name, it.folder, it.added, it.removed, it.hasDiff) }
                    // Nothing from the tools (an agent may edit through commands): the conversation's view gives way
                    // to the folder's uncommitted changes, where the server has them (B-NAV-053).
                    var working = false
                    if (rows.isEmpty() && messageId == null && review.workingTreeAvailable()) {
                        val tree = (review.workingTree() as? Outcome.Ok)?.value.orEmpty()
                        if (tree.isNotEmpty()) {
                            working = true
                            rows = tree.map { d -> workingRow(d.file, d.additions, d.deletions, !d.patch.isNullOrBlank()) }
                        }
                    }
                    mutable.update {
                        it.copy(
                            load = ReviewLoad.Ready,
                            workingTree = working,
                            files = rows.toImmutableList(),
                            complete = set.complete,
                            added = rows.sumOrNull { r -> r.added },
                            removed = rows.sumOrNull { r -> r.removed },
                            undoAll = undoAll(ReviewLoad.Ready),
                        )
                    }
                }
            }
        }
    }

    private fun undoAll(load: ReviewLoad): UndoAllUi =
        when {
            review.state.value.staged != null -> UndoAllUi.Waiting
            review.shape() != UndoShape.Staged -> UndoAllUi.Unavailable
            load != ReviewLoad.Ready || set.undoFrom == null -> UndoAllUi.Unknown
            else -> UndoAllUi.Offered
        }

    override fun back(): Unit = nav.back()

    override fun retry(): Unit = load()

    override fun openFile(path: String) {
        val s = state.value
        if (s.files.none { it.path == path && it.hasDiff }) return
        if (s.workingTree) nav.openWorkingTreeFile(path) else nav.openFile(path, staged = false)
    }

    private fun workingRow(path: String, added: Int?, removed: Int?, hasDiff: Boolean): ChangeRowUi {
        val name = path.split('/').lastOrNull { it.isNotEmpty() } ?: path
        val folder = path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
        return ChangeRowUi(path, name, folder, added, removed, hasDiff)
    }

    override fun keepAll(): Unit = nav.back()

    override fun undoAll() {
        val s = state.value
        if (s.undoAll == UndoAllUi.Offered && !s.busy && s.files.isNotEmpty()) mutable.update { it.copy(confirming = true, actionFailure = null) }
    }

    override fun dismissConfirm() {
        if (!state.value.working) mutable.update { it.copy(confirming = false) }
    }

    override fun confirmUndoAll() {
        val from = set.undoFrom ?: return
        if (state.value.working) return
        mutable.update { it.copy(working = true) }
        scope.launch {
            val result = review.stage(from, applyFiles = true, reviewed = basis)
            mutable.update { it.copy(working = false, confirming = false, actionFailure = (result as? Outcome.Failed)?.failure) }
            if (result is Outcome.Ok) {
                nav.back()
                nav.openStaged()
            }
        }
    }

    override fun openStaged(): Unit = nav.openStaged()

    override fun sendBack() {
        val s = state.value
        if (sendBack != null && s.load == ReviewLoad.Ready && s.files.isNotEmpty() && !s.working) {
            mutable.update { it.copy(noting = true, sendBackFailure = null) }
        }
    }

    override fun sendBackNote(note: String?) {
        val port = sendBack
        val text = note?.trim().orEmpty()
        if (port == null || text.isEmpty()) {
            mutable.update { it.copy(noting = false) }
            return
        }
        // The note is about these changes: a review comment on the first changed file (Flutter's default file).
        val first = set.files.firstOrNull() ?: return mutable.update { it.copy(noting = false) }
        val reference =
            ReviewReference(
                id = "comment-1",
                kind = ReviewReferenceKind.Comment,
                path = first.path,
                scope = ReviewReferenceScope.Session,
                comment = text,
                added = first.added,
                removed = first.removed,
            )
        mutable.update { it.copy(noting = false, working = true) }
        scope.launch {
            val result = port.send(ReviewReference.format(listOf(reference)))
            mutable.update { it.copy(working = false, sendBackFailure = (result as? Outcome.Failed)?.failure) }
            if (result is Outcome.Ok) nav.back()
        }
    }
}

/** One file's diff, read again from the turn's records or the staged preview (routes carry ids only). */
public class FileDiffPresenter(
    private val review: ConversationReview,
    private val path: String,
    private val messageId: String?,
    private val staged: Boolean,
    private val scope: CoroutineScope,
    private val workingTree: Boolean = false,
) {
    private val mutable = MutableStateFlow(FileDiffUi(path))
    public val state: StateFlow<FileDiffUi> = mutable.asStateFlow()

    init {
        load()
    }

    public fun retry(): Unit = load()

    private fun load() {
        mutable.update { it.copy(load = ReviewLoad.Loading) }
        scope.launch {
            if (workingTree) {
                when (val read = review.workingTree()) {
                    is Outcome.Failed -> mutable.update { it.copy(load = ReviewLoad.Failed(read.failure)) }
                    is Outcome.Ok -> {
                        val file = read.value.firstOrNull { it.file == path }
                        mutable.update {
                            it.copy(load = ReviewLoad.Ready, diff = file, missing = file == null, noDiff = file != null && file.patch.isNullOrBlank())
                        }
                    }
                }
            } else if (staged) {
                when (val read = review.refresh()) {
                    is Outcome.Failed -> mutable.update { it.copy(load = ReviewLoad.Failed(read.failure)) }
                    is Outcome.Ok -> {
                        val file = review.state.value.staged?.files?.firstOrNull { it.file == path }
                        val empty = file != null && file.patch.isNullOrBlank() && file.before == null && file.after == null
                        mutable.update { it.copy(load = ReviewLoad.Ready, diff = file, missing = file == null, noDiff = empty) }
                    }
                }
            } else {
                when (val read = review.changes(messageId)) {
                    is Outcome.Failed -> mutable.update { it.copy(load = ReviewLoad.Failed(read.failure)) }
                    is Outcome.Ok -> {
                        val file = read.value.files.firstOrNull { it.path == path }
                        mutable.update {
                            it.copy(
                                load = ReviewLoad.Ready,
                                diff = file?.takeIf { f -> f.hasDiff }?.let(TurnChanges::asFileDiff),
                                missing = file == null,
                                noDiff = file != null && !file.hasDiff,
                            )
                        }
                    }
                }
            }
        }
    }
}

public interface UndoFromHereActions {
    public fun back()

    public fun putFilesBack(apply: Boolean)

    /** "Undo and review" (staged) or "Undo now" (one step). */
    public fun confirm()
}

/**
 * "Undo from this prompt?" (Flutter `showStageRevertSheet`): staged servers hide the prompt and what follows while
 * the person reviews, with "Put files back too"; one-step servers undo at once, so the page is the confirm and lists
 * the files the agent reported editing (B-CHAT-120). A failure keeps the page open with the choice intact.
 */
public class UndoFromHerePresenter(
    private val review: ConversationReview,
    private val promptId: String,
    private val nav: ReviewNavigation,
    private val scope: CoroutineScope,
) : UndoFromHereActions {
    private val shape = review.shape()
    private val mutable = MutableStateFlow(UndoFromHereUi(staged = shape == UndoShape.Staged, available = shape != UndoShape.Unavailable))
    public val state: StateFlow<UndoFromHereUi> = mutable.asStateFlow()

    /** The undo boundary this page read; another client moving it makes the page stale (B-CHAT-105). */
    @kotlin.concurrent.Volatile private var basis: Long? = null

    init {
        scope.launch { review.state.collect { s -> mutable.update { it.copy(block = block(s.running, s.working && !it.working, s.revision, it.block)) } } }
        if (shape != UndoShape.Unavailable) load() else mutable.update { it.copy(load = ReviewLoad.Ready) }
    }

    private fun block(running: Boolean, othersWorking: Boolean, revision: Long, was: UndoBlock?): UndoBlock? =
        when {
            running || othersWorking -> UndoBlock.Busy
            was == UndoBlock.Stale -> UndoBlock.Stale
            basis.let { it != null && it != revision } && !state.value.working -> UndoBlock.Stale
            else -> null
        }

    private fun load() {
        scope.launch {
            if (shape == UndoShape.Staged) {
                review.refresh() // the boundary this page is about; a failed read leaves it as it was
                basis = review.basis()
            }
            when (val prompt = review.prompt(promptId)) {
                is Outcome.Failed -> mutable.update { it.copy(load = ReviewLoad.Ready, promptUnavailable = true) }
                is Outcome.Ok -> mutable.update { it.copy(load = ReviewLoad.Ready, prompt = prompt.value, promptUnavailable = prompt.value == null) }
            }
            if (shape == UndoShape.OneStep) {
                val count = (review.messagesAfter(promptId) as? Outcome.Ok)?.value
                val edited = (review.editedAfter(promptId) as? Outcome.Ok)?.value ?: persistentListOf()
                mutable.update { it.copy(messagesAfter = count, edited = edited) }
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun putFilesBack(apply: Boolean) {
        if (!state.value.working) mutable.update { it.copy(applyFiles = apply) }
    }

    override fun confirm() {
        val s = state.value
        if (s.working || s.block != null || !s.available || s.promptUnavailable) return
        mutable.update { it.copy(working = true, failure = null) }
        scope.launch {
            val result: Outcome<*> = if (s.staged) review.stage(promptId, s.applyFiles, reviewed = basis) else review.undoNow(promptId)
            val stale = ((result as? Outcome.Failed)?.failure as? Failure.Refused)?.reason == RefusalReason.StaleScope
            mutable.update {
                it.copy(
                    working = false,
                    failure = if (stale) null else (result as? Outcome.Failed)?.failure,
                    block = if (stale) UndoBlock.Stale else it.block,
                )
            }
            if (result is Outcome.Ok) {
                nav.back()
                if (s.staged) nav.openStaged()
            }
        }
    }
}

public interface StagedActions {
    public fun back()

    public fun retry()

    public fun openFile(path: String)

    public fun reviewLatest()

    /** "Delete the hidden messages": asks first. */
    public fun askKeep()

    /** "Put everything back": asks first. */
    public fun askPutBack()

    public fun confirm()

    public fun dismissConfirm()
}

/**
 * "Review the undo" (Flutter `StagedRevertScreen`): the prompt it starts from, the files the server lists for it
 * (each opens its diff), "Put everything back" (primary) and the destructive "Delete the hidden messages". The page
 * reviews one point-in-time state; a change on the server makes it stale until "Review latest state" (B-CHAT-105).
 */
public class StagedPresenter(
    private val review: ConversationReview,
    private val nav: ReviewNavigation,
    private val scope: CoroutineScope,
) : StagedActions {
    private val mutable = MutableStateFlow(StagedUi())
    public val state: StateFlow<StagedUi> = mutable.asStateFlow()
    private var reviewed: StagedReview? = null
    private var acting = false

    init {
        scope.launch {
            review.state.collect { s ->
                mutable.update { ui ->
                    val stale = ui.phase == StagedPhase.Review && !acting && reviewed?.let(review::isCurrent) == false
                    ui.copy(busy = s.running || s.working, title = s.title, phase = if (stale) StagedPhase.Stale else ui.phase, confirm = if (stale) null else ui.confirm)
                }
            }
        }
        load()
    }

    private fun load() {
        mutable.update { it.copy(phase = StagedPhase.Loading, loadFailure = null) }
        scope.launch {
            val read = review.refresh()
            if (read is Outcome.Failed && !review.state.value.loaded) {
                mutable.update { it.copy(loadFailure = read.failure) }
                return@launch
            }
            take(review.review())
        }
    }

    private suspend fun take(current: StagedReview?) {
        reviewed = current
        if (current == null) {
            mutable.update { it.copy(phase = StagedPhase.None, files = null) }
            return
        }
        mutable.update { it.copy(phase = StagedPhase.Review, files = current.revert.files, prompt = null, promptUnavailable = false, messagesAfter = null, failure = null) }
        val id = current.revert.messageId.raw
        val prompt = review.prompt(id)
        val count = (review.messagesAfter(id) as? Outcome.Ok)?.value
        if (reviewed != current) return
        mutable.update {
            it.copy(
                prompt = (prompt as? Outcome.Ok)?.value,
                promptUnavailable = prompt !is Outcome.Ok || prompt.value == null,
                messagesAfter = count,
            )
        }
    }

    override fun back(): Unit = nav.back()

    override fun retry(): Unit = load()

    override fun openFile(path: String): Unit = nav.openFile(path, staged = true)

    override fun reviewLatest() {
        if (state.value.busy) return
        load()
    }

    override fun askKeep() {
        if (state.value.phase == StagedPhase.Review && !state.value.busy) mutable.update { it.copy(confirm = StagedConfirm.Keep, failure = null) }
    }

    override fun askPutBack() {
        if (state.value.phase == StagedPhase.Review && !state.value.busy) mutable.update { it.copy(confirm = StagedConfirm.PutBack, failure = null) }
    }

    override fun dismissConfirm() {
        if (!state.value.working) mutable.update { it.copy(confirm = null) }
    }

    override fun confirm() {
        val choice = state.value.confirm ?: return
        val target = reviewed ?: return
        if (state.value.working) return
        acting = true
        mutable.update { it.copy(working = true) }
        scope.launch {
            val result = if (choice == StagedConfirm.Keep) review.keep(target) else review.putBack(target)
            acting = false
            when (result) {
                is Outcome.Ok ->
                    mutable.update { it.copy(working = false, confirm = null, phase = if (choice == StagedConfirm.Keep) StagedPhase.Kept else StagedPhase.Restored) }
                is Outcome.Failed -> {
                    val stale = !review.isCurrent(target)
                    mutable.update { it.copy(working = false, confirm = null, failure = result.failure, phase = if (stale) StagedPhase.Stale else it.phase) }
                }
            }
        }
    }
}

private inline fun <T> List<T>.sumOrNull(of: (T) -> Int?): Int? {
    var total = 0
    for (item in this) total += of(item) ?: return null
    return total
}
