package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.Outcome
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

/**
 * A project folder's uncommitted changes, read with no conversation (Flutter `pushWorkingTreeReview` opened from
 * Files: `listVcsDiffs(workingTree)`). The hub binds it to the profile and folder.
 */
public fun interface ReviewProjectRead {
    public suspend fun read(): Outcome<ImmutableList<FileDiff>>
}

/**
 * "Changes" of a whole project: the uncommitted files, nothing else. It has no conversation, so there is no Undo all,
 * no send-back and no comments for an agent; the scope is the working tree and is not a choice.
 */
public class ProjectChangesPresenter(
    private val tree: ReviewProjectRead,
    private val nav: ReviewNavigation,
    private val scope: CoroutineScope,
) : ChangesActions {
    private val mutable =
        MutableStateFlow(
            ChangesUi(
                turn = false,
                workingTree = true,
                scope = ReviewScope.WorkingTree,
                scopes = persistentListOf(ReviewScope.WorkingTree),
                conversation = false,
            )
        )
    public val state: StateFlow<ChangesUi> = mutable.asStateFlow()

    init {
        load()
    }

    private fun load() {
        mutable.update { it.copy(load = ReviewLoad.Loading) }
        scope.launch {
            when (val read = tree.read()) {
                is Outcome.Failed -> mutable.update { it.copy(load = ReviewLoad.Failed(read.failure)) }
                is Outcome.Ok -> {
                    val rows = read.value.map(::row).toImmutableList()
                    mutable.update {
                        it.copy(
                            load = ReviewLoad.Ready,
                            files = rows,
                            added = rows.sumOrNull { r -> r.added },
                            removed = rows.sumOrNull { r -> r.removed },
                        )
                    }
                }
            }
        }
    }

    private fun row(d: FileDiff): ChangeRowUi {
        val name = d.file.split('/').lastOrNull { it.isNotEmpty() } ?: d.file
        val folder = d.file.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
        return ChangeRowUi(d.file, name, folder, d.additions, d.deletions, !d.patch.isNullOrBlank())
    }

    private fun <T> List<T>.sumOrNull(pick: (T) -> Int?): Int? {
        var total = 0
        for (item in this) total += pick(item) ?: return null
        return total
    }

    override fun back(): Unit = nav.back()

    override fun retry(): Unit = load()

    override fun openFile(path: String): Unit = nav.openWorkingTreeFile(path)

    /** "Looks good": nothing to keep or send; the person is back where they came from. */
    override fun keepAll(): Unit = nav.back()

    override fun undoAll(): Unit = Unit

    override fun confirmUndoAll(): Unit = Unit

    override fun dismissConfirm(): Unit = Unit

    override fun openStaged(): Unit = Unit

    override fun sendBack(): Unit = Unit

    override fun sendBackNote(note: String?): Unit = Unit
}

/** One file of a project's uncommitted changes: its diff, read again with Try again. */
public class ProjectFileDiffPresenter(
    private val tree: ReviewProjectRead,
    private val path: String,
    private val scope: CoroutineScope,
) : FileDiffActions {
    private val mutable = MutableStateFlow(FileDiffUi(path))
    public val state: StateFlow<FileDiffUi> = mutable.asStateFlow()

    init {
        load()
    }

    override fun back(): Unit = Unit

    override fun retry(): Unit = load()

    private fun load() {
        mutable.update { it.copy(load = ReviewLoad.Loading) }
        scope.launch {
            when (val read = tree.read()) {
                is Outcome.Failed -> mutable.update { it.copy(load = ReviewLoad.Failed(read.failure)) }
                is Outcome.Ok -> {
                    val file = read.value.firstOrNull { it.file == path }
                    mutable.update {
                        it.copy(
                            load = ReviewLoad.Ready,
                            diff = file,
                            missing = file == null,
                            noDiff = file != null && file.patch.isNullOrBlank(),
                        )
                    }
                }
            }
        }
    }
}
