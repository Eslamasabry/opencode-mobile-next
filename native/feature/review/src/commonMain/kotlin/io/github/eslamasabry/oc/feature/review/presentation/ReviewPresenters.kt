package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.failed
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
import io.github.eslamasabry.oc.core.model.FileDiff
import kotlinx.collections.immutable.ImmutableList
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

    /**
     * The change-scope picker: true shows the folder's uncommitted changes, false the changes this
     * conversation (or turn) made.
     */
    public fun chooseWorkingTree(on: Boolean) {
        chooseScope(if (on) ReviewScope.WorkingTree else ReviewScope.Conversation)
    }

    /** The change-scope picker: this conversation, uncommitted, or the whole branch (Flutter `_selectScope`). */
    public fun chooseScope(scope: ReviewScope) {}

    /** "Open Git for this project": the Git page, from the not-a-Git-project state. */
    public fun openGit() {}

    /** "Send back with a note": opens the note field. */
    public fun sendBack()

    /** The note field closed with [note] (null: cancelled). */
    public fun sendBackNote(note: String?)

    /** "Send N comments": every draft line comment of this conversation, in one prompt to its agent (B-156). */
    public fun sendComments() {}

    /** The "Guided" switch (B-155): the agent's own ordering of its change, or the plain file list. */
    public fun guided(on: Boolean) {}
}

/**
 * Changes of a turn ([messageId] any of its messages) or of the whole conversation (null). The rows
 * are tool evidence from the server's records (B-CHAT-068); "Undo all" stages an undo from the
 * changes' first prompt with files back (Flutter `_undoAllAction`), then opens its review.
 */
public class ChangesPresenter(
    private val review: ConversationReview,
    private val messageId: String?,
    private val nav: ReviewNavigation,
    private val scope: CoroutineScope,
    private val sendBack: ReviewSendBack? = null,
    agent: ReviewAgentPorts = ReviewAgentPorts(),
    private val branch: ReviewBranchRead? = null,
    private val shelf: ReviewShelf? = null,
    private val setup: ReviewProjectSetup = ReviewProjectSetup { VersionControlSetupState.Unknown },
) : ChangesActions {
    private val runner = agent.runner(review)
    private val guideAsk = agent.guideAsk
    private val guides = agent.guides
    private val itemKey = ReviewShelf.changes(messageId)
    private val mutable =
        MutableStateFlow(
            shelf?.peek(itemKey)?.changes?.frame(turn = messageId != null, canSendBack = sendBack != null)
                ?: ChangesUi(turn = messageId != null, canSendBack = sendBack != null)
        )
    public val state: StateFlow<ChangesUi> = mutable.asStateFlow()
    private var set: ChangeSet = ConversationReview.Empty

    /**
     * The undo boundary the rows were read against: Undo all refuses once it moved (B-CHAT-105).
     */
    private var basis: Long? = null

    /** The person's scope choice; null keeps the automatic one (the folder's changes when the tools reported none). */
    private var pickedScope: ReviewScope? = null

    /** Whether the server answers the branch read; null until it was asked once. */
    private var branchAnswers: Boolean? = null

    /**
     * Files whose diff was opened in this visit, by scope and path, with what the diff was then (Flutter
     * `_viewedFiles`): a file the agent changed again is not viewed any more.
     */
    private val viewed = HashMap<String, String>()

    /** What each row on screen looks like now, to compare with [viewed]. */
    private var shown: Map<String, String> = emptyMap()

    init {
        if (shelf != null && state.value.load == ReviewLoad.Loading) {
            // A cold start has only the disk copy: draw it when read, unless the live read was faster.
            scope.launch {
                val was = shelf.read(itemKey)?.changes ?: return@launch
                mutable.update { if (it.load == ReviewLoad.Loading) was.frame(it.turn, it.canSendBack) else it }
            }
        }
        scope.launch {
            review.state.collect { s ->
                mutable.update {
                    it.copy(busy = s.running || s.working, undoAll = undoAll(it.load))
                }
            }
        }
        runner?.let { r ->
            scope.launch {
                r.book.comments.collect { list ->
                    mutable.update { ui ->
                        ui.copy(
                            drafts = list.count { it.status == CommentStatus.Draft },
                            waiting =
                                list.count { it.status == CommentStatus.Pending || it.status == CommentStatus.Sending },
                        )
                    }
                }
            }
        }
        load()
        // The list follows the agent's edits (B-156): read again, keep the rows meanwhile, resolve comments.
        scope.launch {
            var first = true
            review.edits.collect {
                if (first) {
                    first = false
                } else if (!state.value.working && state.value.load != ReviewLoad.Loading) {
                    load(quiet = true)
                    runner?.reconcile()
                }
            }
        }
    }

    private fun load(quiet: Boolean = false) {
        if ((!quiet || state.value.load !is ReviewLoad.Ready) && !state.value.updating) {
            mutable.update { it.copy(load = ReviewLoad.Loading) }
        }
        scope.launch {
            review.refresh() // the staged boundary decides Undo all; a failed read leaves it as it
            // was
            when (val read = review.changes(messageId)) {
                is Outcome.Failed ->
                    if (state.value.updating) mutable.update { it.copy(updating = false) } // the kept rows stay
                    else if (!(quiet && state.value.load == ReviewLoad.Ready))
                        mutable.update { it.copy(load = ReviewLoad.Failed(read.failure)) }
                is Outcome.Ok -> {
                    set = read.value
                    basis = review.basis()
                    val toolRows =
                        set.files.map {
                            Scoped(
                                ChangeRowUi(it.path, it.name, it.folder, it.added, it.removed, it.hasDiff),
                                "${it.added}|${it.removed}|${it.patches.hashCode()}|${it.after.hashCode()}",
                            )
                        }
                    val (scopedFiles, chosen) =
                        when (val scoped = scopedRows(toolRows)) {
                            is Outcome.Failed -> {
                                mutable.update {
                                    if (it.updating) it.copy(updating = false) else it.copy(load = ReviewLoad.Failed(scoped.failure))
                                }
                                return@launch
                            }
                            is Outcome.Ok -> scoped.value
                        }
                    val rows = markViewed(chosen, scopedFiles)
                    val working = chosen != ReviewScope.Conversation
                    val scopes = scopes()
                    // An empty folder or branch view says why: not under Git, or a clean tree (the agent's reported
                    // edits are the Conversation view's own words).
                    val notRepository =
                        rows.isEmpty() && working && setup.setup() == VersionControlSetupState.Absent
                    mutable.update {
                        it.copy(
                            load = ReviewLoad.Ready,
                            notRepository = notRepository,
                            workingTree = chosen == ReviewScope.WorkingTree,
                            scope = chosen,
                            scopes = scopes,
                            canPickScope = scopes.size > 1,
                            files = rows.toImmutableList(),
                            complete = set.complete,
                            added = rows.sumOrNull { r -> r.added },
                            removed = rows.sumOrNull { r -> r.removed },
                            undoAll = undoAll(ReviewLoad.Ready),
                            canGuide = guideAsk != null && !working && rows.isNotEmpty(),
                            updating = false,
                        )
                    }
                    shelf?.keep(itemKey, KeptReview(changes = KeptChanges.of(state.value)))
                    followGuide()
                }
            }
        }
    }

    /** A row and what its diff is now (compared with what it was when viewed). */
    private class Scoped(val row: ChangeRowUi, val fingerprint: String)

    /**
     * The rows in the chosen scope, and that scope. With nothing from the tools (an agent may edit through commands)
     * the conversation's view gives way to the first view with changes, uncommitted then the whole branch, where the
     * server has them (B-NAV-053; Flutter `_viewWithChanges`, the same order).
     */
    private suspend fun scopedRows(toolRows: List<Scoped>): Outcome<Pair<List<Scoped>, ReviewScope>> {
        val picked = pickedScope
        val treeOk = review.workingTreeAvailable()
        val nothingFromTools = toolRows.isEmpty() && messageId == null
        return when {
            picked == ReviewScope.WorkingTree && treeOk ->
                when (val tree = review.workingTree()) {
                    is Outcome.Failed -> tree
                    is Outcome.Ok -> Outcome.Ok(tree.value.map(::diffRow) to ReviewScope.WorkingTree)
                }
            picked == ReviewScope.Branch && branch != null ->
                when (val read = readBranch()) {
                    is Outcome.Failed -> read
                    is Outcome.Ok -> Outcome.Ok(read.value.map(::diffRow) to ReviewScope.Branch)
                }
            picked == null && nothingFromTools -> {
                val tree = if (treeOk) (review.workingTree() as? Outcome.Ok)?.value.orEmpty() else emptyList()
                val whole = if (tree.isEmpty() && branch != null) (readBranch() as? Outcome.Ok)?.value.orEmpty() else emptyList()
                Outcome.Ok(
                    when {
                        tree.isNotEmpty() -> tree.map(::diffRow) to ReviewScope.WorkingTree
                        whole.isNotEmpty() -> whole.map(::diffRow) to ReviewScope.Branch
                        else -> toolRows to ReviewScope.Conversation
                    }
                )
            }
            else -> Outcome.Ok(toolRows to ReviewScope.Conversation)
        }
    }

    /** The branch read; an "unavailable" answer takes the scope off the picker for this visit. */
    private suspend fun readBranch(): Outcome<List<FileDiff>> {
        val read = branch?.read() ?: return Failure.Unavailable(BRANCH_FEATURE).failed()
        branchAnswers = !(read is Outcome.Failed && read.failure is Failure.Unavailable)
        return read
    }

    /** The scopes to offer: the branch one once the server answered it (asked once, ahead of the first pick). */
    private suspend fun scopes(): ImmutableList<ReviewScope> {
        if (branch != null && branchAnswers == null) readBranch()
        return buildList {
            add(ReviewScope.Conversation)
            if (review.workingTreeAvailable()) add(ReviewScope.WorkingTree)
            if (branchAnswers == true) add(ReviewScope.Branch)
        }.toImmutableList()
    }

    /** Rows with their Viewed marks; a mark whose file changed since is dropped (Flutter `_apply`). */
    private fun markViewed(scope: ReviewScope, rows: List<Scoped>): List<ChangeRowUi> {
        val now = rows.associate { key(scope, it.row.path) to it.fingerprint }
        viewed.keys.removeAll { k -> k.startsWith("${scope.name}:") && viewed[k] != now[k] }
        shown = now
        return rows.map { it.row.copy(viewed = viewed[key(scope, it.row.path)] == it.fingerprint) }
    }

    private fun key(scope: ReviewScope, path: String): String = "${scope.name}:$path"

    private fun diffRow(d: FileDiff): Scoped =
        Scoped(
            workingRow(d.file, d.additions, d.deletions, !d.patch.isNullOrBlank()),
            "${d.additions}|${d.deletions}|${d.status}|${d.patch.hashCode()}",
        )

    /** Guided is on: show the cached guide for these changes, or ask for one (the files changed, or none yet). */
    private fun followGuide() {
        val s = state.value
        if (s.guide == GuideUi.Off || s.guide == GuideUi.Making) return
        if (!s.canGuide) {
            mutable.update { it.copy(guide = GuideUi.Off) }
            return
        }
        val cached = guides[ReviewGuidePrompt.key(s.files)]
        // A guide of older files is made again; one that waited for the agent's turn is asked once the turn ends.
        val again = s.guide is GuideUi.Ready || (s.guide as? GuideUi.Fallback)?.failure is Failure.Busy
        when {
            cached != null -> mutable.update { it.copy(guide = GuideUi.Ready(cached)) }
            again -> makeGuide()
        }
    }

    override fun guided(on: Boolean) {
        val s = state.value
        if (!on) {
            mutable.update { it.copy(guide = GuideUi.Off) }
            return
        }
        if (!s.canGuide || s.guide != GuideUi.Off) return
        val cached = guides[ReviewGuidePrompt.key(s.files)]
        if (cached != null) mutable.update { it.copy(guide = GuideUi.Ready(cached)) } else makeGuide()
    }

    private fun makeGuide() {
        val ask = guideAsk ?: return
        if (review.state.value.running) {
            // Never joins a running turn: the guide is asked when the agent is done (the next read follows).
            mutable.update { it.copy(guide = GuideUi.Fallback(Failure.Busy(BusyKind.SessionBusy))) }
            return
        }
        val files = state.value.files
        val key = ReviewGuidePrompt.key(files)
        val paths = files.map { it.path }
        mutable.update { it.copy(guide = GuideUi.Making) }
        scope.launch {
            val marker = ReviewGuidePrompt.marker(key)
            val result = ask.ask(ReviewGuidePrompt.prompt(marker, paths), marker)
            val guide = (result as? Outcome.Ok)?.value?.let { ReviewGuidePrompt.parse(it, paths) }
            if (guide != null) guides[key] = guide
            mutable.update {
                if (it.guide != GuideUi.Making) it // turned off meanwhile
                else if (guide != null) it.copy(guide = GuideUi.Ready(guide))
                else {
                    val why = (result as? Outcome.Failed)?.failure ?: Failure.UnreadableReply()
                    it.copy(guide = GuideUi.Fallback(why))
                }
            }
        }
    }

    override fun sendComments() {
        val r = runner ?: return
        val s = state.value
        if (s.sendingComments || s.drafts == 0) return
        mutable.update { it.copy(sendingComments = true, commentsFailure = null) }
        scope.launch {
            val result = r.sendDrafts()
            mutable.update { it.copy(sendingComments = false, commentsFailure = (result as? Outcome.Failed)?.failure) }
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

    override fun chooseScope(scope: ReviewScope) {
        val s = state.value
        if (s.working || s.load == ReviewLoad.Loading || s.scope == scope || scope !in s.scopes) return
        pickedScope = scope
        load()
    }

    override fun openFile(path: String) {
        val s = state.value
        if (s.files.none { it.path == path && it.hasDiff }) return
        // Its diff is on screen now: Viewed from here on, until the file changes again.
        val k = key(s.scope, path)
        shown[k]?.let { viewed[k] = it }
        mutable.update { ui -> ui.copy(files = ui.files.map { if (it.path == path) it.copy(viewed = true) else it }.toImmutableList()) }
        when (s.scope) {
            ReviewScope.WorkingTree -> nav.openWorkingTreeFile(path)
            ReviewScope.Branch -> nav.openBranchFile(path)
            ReviewScope.Conversation -> nav.openFile(path, staged = false)
        }
    }

    private fun workingRow(
        path: String,
        added: Int?,
        removed: Int?,
        hasDiff: Boolean,
    ): ChangeRowUi {
        val name = path.split('/').lastOrNull { it.isNotEmpty() } ?: path
        val folder = path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
        return ChangeRowUi(path, name, folder, added, removed, hasDiff)
    }

    override fun keepAll(): Unit = nav.back()

    override fun undoAll() {
        val s = state.value
        if (s.undoAll == UndoAllUi.Offered && !s.busy && s.files.isNotEmpty())
            mutable.update { it.copy(confirming = true, actionFailure = null) }
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
            mutable.update {
                it.copy(
                    working = false,
                    confirming = result is Outcome.Failed,
                    actionFailure = (result as? Outcome.Failed)?.failure,
                )
            }
            if (result is Outcome.Ok) {
                nav.back()
                nav.openStaged()
            }
        }
    }

    override fun openStaged(): Unit = nav.openStaged()

    override fun openGit(): Unit = nav.openGit()

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
        // The note is about these changes: a review comment on the first changed file (Flutter's
        // default file).
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
            mutable.update {
                it.copy(working = false, sendBackFailure = (result as? Outcome.Failed)?.failure)
            }
            if (result is Outcome.Ok) nav.back()
        }
    }
}

/** Where one file's diff is read from. */
public enum class FileDiffSource {
    /** The turn's (or the conversation's) tool records. */
    Changes,

    /** The staged undo's preview. */
    Staged,

    /** The conversation folder's uncommitted changes. */
    WorkingTree,

    /** The whole branch's changes, compared with the main branch. */
    Branch,
}

/** Intents of one file's diff page. */
public interface FileDiffActions {
    public fun back()

    public fun retry()

    /** "Comment" on the selected lines ([anchor] null: the selection could not be placed, nothing opens). */
    public fun comment(anchor: CommentAnchor?) {}

    /** The comment field closed with [text] (null: cancelled). */
    public fun commentText(text: String?) {}

    /** Removes a draft comment. */
    public fun removeComment(id: String) {}

    /** "Add <file> to the prompt": an `@path` mention in the conversation's message; the person stays here. */
    public fun addToPrompt() {}

    /** "Send N comments": every draft in this conversation, one prompt to its agent. */
    public fun sendComments() {}
}

/**
 * One file's diff, read again from the turn's records or the staged preview (routes carry ids
 * only). It reads again whenever the conversation's files may have changed (B-156: the diff follows the agent's edits),
 * keeping the last diff on screen meanwhile. Lines can be commented for the agent where a [sender] exists.
 */
public class FileDiffPresenter(
    private val review: ConversationReview,
    private val path: String,
    private val messageId: String?,
    source: FileDiffSource,
    private val scope: CoroutineScope,
    agent: ReviewAgentPorts = ReviewAgentPorts(),
    private val branch: ReviewBranchRead? = null,
    private val shelf: ReviewShelf? = null,
    private val addFile: ReviewAddFile? = null,
) : FileDiffActions {
    private val staged = source == FileDiffSource.Staged
    private val workingTree = source == FileDiffSource.WorkingTree
    private val wholeBranch = source == FileDiffSource.Branch
    private val runner = if (staged) null else agent.runner(review)
    private val itemKey = ReviewShelf.file(source, messageId, path)
    private val mutable =
        MutableStateFlow(
            shelf?.peek(itemKey)?.file?.let { frame(it) } ?: FileDiffUi(path, canComment = runner != null, canAddFile = addFile != null)
        )
    public val state: StateFlow<FileDiffUi> = mutable.asStateFlow()

    private fun frame(kept: KeptFile): FileDiffUi =
        FileDiffUi(
            path,
            load = ReviewLoad.Ready,
            diff = kept.diff().takeUnless { kept.missing },
            missing = kept.missing,
            noDiff = kept.noDiff,
            canComment = runner != null,
            canAddFile = addFile != null,
            updating = true,
        )

    init {
        if (shelf != null && state.value.load == ReviewLoad.Loading) {
            scope.launch {
                val was = shelf.read(itemKey)?.file ?: return@launch
                mutable.update { if (it.load == ReviewLoad.Loading) frame(was).copy(comments = it.comments, drafts = it.drafts) else it }
            }
        }
        load()
        runner?.let { r ->
            scope.launch {
                r.book.comments.collect { list ->
                    mutable.update { ui ->
                        ui.copy(
                            comments = list.filter { it.anchor.path == path }.toImmutableList(),
                            drafts = list.count { it.status == CommentStatus.Draft },
                        )
                    }
                }
            }
        }
        scope.launch {
            var first = true
            review.edits.collect {
                if (first) {
                    first = false
                } else {
                    load(quiet = true)
                    runner?.reconcile()
                }
            }
        }
    }

    /** The ViewModel goes back; the presenter has nowhere to go. */
    override fun back(): Unit = Unit

    override fun retry(): Unit = load()

    override fun comment(anchor: CommentAnchor?) {
        if (runner == null || anchor == null || anchor.path != path) return
        mutable.update { it.copy(commenting = anchor, sendFailure = null) }
    }

    override fun commentText(text: String?) {
        val anchor = state.value.commenting
        mutable.update { it.copy(commenting = null) }
        val r = runner ?: return
        if (anchor == null || text.isNullOrBlank()) return
        val raw =
            state.value.diff
                ?.let { CommentLines.range(CommentResolve.diffFile(it), anchor.side, anchor.start, anchor.end) }
                .orEmpty()
        r.book.add(anchor, text, raw)
    }

    override fun removeComment(id: String) {
        runner?.book?.remove(id)
    }

    override fun addToPrompt() {
        val add = addFile ?: return
        scope.launch {
            val notice =
                when (val result = add.add(path)) {
                    is Outcome.Ok -> if (result.value == AddedFile.Added) FileNotice.Added else FileNotice.AlreadyThere
                    is Outcome.Failed -> FileNotice.Failed(result.failure)
                }
            mutable.update { it.copy(fileNotice = notice) }
        }
    }

    override fun sendComments() {
        val r = runner ?: return
        if (state.value.sending || state.value.drafts == 0) return
        mutable.update { it.copy(sending = true, sendFailure = null) }
        scope.launch {
            val result = r.sendDrafts()
            mutable.update { it.copy(sending = false, sendFailure = (result as? Outcome.Failed)?.failure) }
        }
    }

    private fun load(quiet: Boolean = false) {
        if (!quiet && !state.value.updating) mutable.update { it.copy(load = ReviewLoad.Loading) }
        scope.launch {
            val read = read()
            // A quiet read that fails keeps the last diff on screen.
            if (read is ReviewLoad.Failed && quiet && state.value.load == ReviewLoad.Ready) return@launch
            if (read is ReviewLoad.Failed) {
                mutable.update { if (it.updating) it.copy(updating = false) else it.copy(load = read) }
            } else {
                KeptFile.of(state.value)?.let { shelf?.keep(itemKey, KeptReview(file = it)) }
            }
        }
    }

    /** Reads the file and updates the page; returns the failure when the read failed. */
    private suspend fun read(): ReviewLoad {
        val read =
            when {
                workingTree -> readWorkingTree(review, path)
                wholeBranch -> readBranch(branch, path)
                staged -> readStaged(review, path)
                else -> readChanges(review, path, messageId)
            }
        return when (read) {
            is Outcome.Failed -> ReviewLoad.Failed(read.failure)
            is Outcome.Ok -> {
                mutable.update { read.value(it).copy(load = ReviewLoad.Ready, updating = false) }
                ReviewLoad.Ready
            }
        }
    }
}

/** One file's diff page reads, each returning how the page changes. */
private typealias PageChange = (FileDiffUi) -> FileDiffUi

private suspend fun readWorkingTree(review: ConversationReview, path: String): Outcome<PageChange> =
    when (val read = review.workingTree()) {
        is Outcome.Failed -> read
        is Outcome.Ok -> {
            val file = read.value.firstOrNull { it.file == path }
            val noDiff = file != null && file.patch.isNullOrBlank()
            Outcome.Ok { ui -> ui.copy(diff = file, missing = file == null, noDiff = noDiff) }
        }
    }

private suspend fun readBranch(branch: ReviewBranchRead?, path: String): Outcome<PageChange> =
    when (val read = branch?.read() ?: Failure.Unavailable(BRANCH_FEATURE).failed()) {
        is Outcome.Failed -> read
        is Outcome.Ok -> {
            val file = read.value.firstOrNull { it.file == path }
            val noDiff = file != null && file.patch.isNullOrBlank()
            Outcome.Ok { ui -> ui.copy(diff = file, missing = file == null, noDiff = noDiff) }
        }
    }

private suspend fun readStaged(review: ConversationReview, path: String): Outcome<PageChange> =
    when (val read = review.refresh()) {
        is Outcome.Failed -> read
        is Outcome.Ok -> {
            val file = review.state.value.staged?.files?.firstOrNull { it.file == path }
            val empty = file != null && file.patch.isNullOrBlank() && file.before == null && file.after == null
            Outcome.Ok { ui -> ui.copy(diff = file, missing = file == null, noDiff = empty) }
        }
    }

private suspend fun readChanges(review: ConversationReview, path: String, messageId: String?): Outcome<PageChange> =
    when (val read = review.changes(messageId)) {
        is Outcome.Failed -> read
        is Outcome.Ok -> {
            val file = read.value.files.firstOrNull { it.path == path }
            val diff = file?.takeIf { f -> f.hasDiff }?.let(TurnChanges::asFileDiff)
            val noDiff = file != null && !file.hasDiff
            Outcome.Ok { ui -> ui.copy(diff = diff, missing = file == null, noDiff = noDiff) }
        }
    }

public interface UndoFromHereActions {
    public fun back()

    public fun putFilesBack(apply: Boolean)

    /** "Undo and review" (staged) or "Undo now" (one step). */
    public fun confirm()
}

/**
 * "Undo from this prompt?" (Flutter `showStageRevertSheet`): staged servers hide the prompt and
 * what follows while the person reviews, with "Put files back too"; one-step servers undo at once,
 * so the page is the confirm and lists the files the agent reported editing (B-CHAT-120). A failure
 * keeps the page open with the choice intact.
 */
public class UndoFromHerePresenter(
    private val review: ConversationReview,
    private val promptId: String,
    private val nav: ReviewNavigation,
    private val scope: CoroutineScope,
) : UndoFromHereActions {
    private val shape = review.shape()
    private val mutable =
        MutableStateFlow(
            UndoFromHereUi(
                staged = shape == UndoShape.Staged,
                available = shape != UndoShape.Unavailable,
            )
        )
    public val state: StateFlow<UndoFromHereUi> = mutable.asStateFlow()

    /**
     * The undo boundary this page read; another client moving it makes the page stale (B-CHAT-105).
     */
    @kotlin.concurrent.Volatile private var basis: Long? = null

    init {
        scope.launch {
            review.state.collect { s ->
                mutable.update {
                    it.copy(
                        block = block(s.running, s.working && !it.working, s.revision, it.block)
                    )
                }
            }
        }
        if (shape != UndoShape.Unavailable) load()
        else mutable.update { it.copy(load = ReviewLoad.Ready) }
    }

    private fun block(
        running: Boolean,
        othersWorking: Boolean,
        revision: Long,
        was: UndoBlock?,
    ): UndoBlock? =
        when {
            running || othersWorking -> UndoBlock.Busy
            was == UndoBlock.Stale -> UndoBlock.Stale
            basis.let { it != null && it != revision } && !state.value.working -> UndoBlock.Stale
            else -> null
        }

    private fun load() {
        scope.launch {
            if (shape == UndoShape.Staged) {
                review
                    .refresh() // the boundary this page is about; a failed read leaves it as it was
                basis = review.basis()
            }
            when (val prompt = review.prompt(promptId)) {
                is Outcome.Failed ->
                    mutable.update { it.copy(load = ReviewLoad.Ready, promptUnavailable = true) }
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            load = ReviewLoad.Ready,
                            prompt = prompt.value,
                            promptUnavailable = prompt.value == null,
                        )
                    }
            }
            if (shape == UndoShape.OneStep) {
                val count = (review.messagesAfter(promptId) as? Outcome.Ok)?.value
                val edited =
                    (review.editedAfter(promptId) as? Outcome.Ok)?.value ?: persistentListOf()
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
            val result: Outcome<*> =
                if (s.staged) review.stage(promptId, s.applyFiles, reviewed = basis)
                else review.undoNow(promptId)
            val stale =
                ((result as? Outcome.Failed)?.failure as? Failure.Refused)?.reason ==
                    RefusalReason.StaleScope
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
 * "Review the undo" (Flutter `StagedRevertScreen`): the prompt it starts from, the files the server
 * lists for it (each opens its diff), "Put everything back" (primary) and the destructive "Delete
 * the hidden messages". The page reviews one point-in-time state; a change on the server makes it
 * stale until "Review latest state" (B-CHAT-105).
 */
public class StagedPresenter(
    private val review: ConversationReview,
    private val nav: ReviewNavigation,
    private val scope: CoroutineScope,
    private val shelf: ReviewShelf? = null,
) : StagedActions {
    private val mutable = MutableStateFlow(shelf?.peek(ReviewShelf.STAGED)?.staged?.frame() ?: StagedUi())
    public val state: StateFlow<StagedUi> = mutable.asStateFlow()
    private var reviewed: StagedReview? = null
    private var acting = false

    init {
        if (shelf != null && state.value.phase == StagedPhase.Loading) {
            scope.launch {
                val was = shelf.read(ReviewShelf.STAGED)?.staged ?: return@launch
                mutable.update { if (it.phase == StagedPhase.Loading) was.frame().copy(busy = it.busy, title = it.title ?: was.title) else it }
            }
        }
        scope.launch {
            review.state.collect { s ->
                mutable.update { ui ->
                    val stale =
                        ui.phase == StagedPhase.Review &&
                            !acting &&
                            reviewed?.let(review::isCurrent) == false
                    ui.copy(
                        busy = s.running || s.working,
                        title = s.title,
                        phase = if (stale) StagedPhase.Stale else ui.phase,
                        confirm = if (stale) null else ui.confirm,
                    )
                }
            }
        }
        load()
    }

    private fun load() {
        mutable.update { if (it.updating) it.copy(loadFailure = null) else it.copy(phase = StagedPhase.Loading, loadFailure = null) }
        scope.launch {
            val read = review.refresh()
            if (read is Outcome.Failed && !review.state.value.loaded) {
                mutable.update { if (it.updating) it.copy(updating = false) else it.copy(loadFailure = read.failure) }
                return@launch
            }
            take(review.review())
        }
    }

    private suspend fun take(current: StagedReview?) {
        reviewed = current
        if (current == null) {
            mutable.update { it.copy(phase = StagedPhase.None, files = null, updating = false) }
            return
        }
        mutable.update {
            it.copy(
                phase = StagedPhase.Review,
                updating = false,
                files = current.revert.files,
                prompt = null,
                promptUnavailable = false,
                messagesAfter = null,
                failure = null,
            )
        }
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
        shelf?.keep(ReviewShelf.STAGED, KeptReview(staged = KeptStaged.of(state.value)))
    }

    override fun back(): Unit = nav.back()

    override fun retry(): Unit = load()

    override fun openFile(path: String): Unit = nav.openFile(path, staged = true)

    override fun reviewLatest() {
        if (state.value.busy) return
        load()
    }

    override fun askKeep() {
        if (state.value.phase == StagedPhase.Review && !state.value.busy && !state.value.updating)
            mutable.update { it.copy(confirm = StagedConfirm.Keep, failure = null) }
    }

    override fun askPutBack() {
        if (state.value.phase == StagedPhase.Review && !state.value.busy && !state.value.updating)
            mutable.update { it.copy(confirm = StagedConfirm.PutBack, failure = null) }
    }

    override fun dismissConfirm() {
        if (!state.value.working) mutable.update { it.copy(confirm = null) }
    }

    override fun confirm() {
        val choice = state.value.confirm ?: return
        val target = reviewed ?: return
        if (state.value.working) return
        acting = true
        mutable.update { it.copy(working = true, failure = null) }
        scope.launch {
            val result =
                if (choice == StagedConfirm.Keep) review.keep(target) else review.putBack(target)
            acting = false
            when (result) {
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            working = false,
                            confirm = null,
                            phase =
                                if (choice == StagedConfirm.Keep) StagedPhase.Kept
                                else StagedPhase.Restored,
                        )
                    }
                is Outcome.Failed -> {
                    val stale = !review.isCurrent(target)
                    // Like Undo all: a failed answer keeps its question open for a retry; a
                    // review that went stale closes it (there is nothing current to retry).
                    mutable.update {
                        it.copy(
                            working = false,
                            confirm = if (stale) null else choice,
                            failure = result.failure,
                            phase = if (stale) StagedPhase.Stale else it.phase,
                        )
                    }
                }
            }
        }
    }
}

/** The feature the branch read names when nothing can answer it. */
internal val BRANCH_FEATURE: FeatureId = FeatureId("vcsBranchDiff")

private inline fun <T> List<T>.sumOrNull(of: (T) -> Int?): Int? {
    var total = 0
    for (item in this) total += of(item) ?: return null
    return total
}
