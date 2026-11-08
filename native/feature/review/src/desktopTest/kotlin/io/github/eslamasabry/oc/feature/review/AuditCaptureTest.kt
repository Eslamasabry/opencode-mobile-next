package io.github.eslamasabry.oc.feature.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.review.presentation.ChangeRowUi
import io.github.eslamasabry.oc.feature.review.presentation.ChangesActions
import io.github.eslamasabry.oc.feature.review.presentation.ChangesUi
import io.github.eslamasabry.oc.feature.review.presentation.CommentAnchor
import io.github.eslamasabry.oc.feature.review.presentation.CommentSide
import io.github.eslamasabry.oc.feature.review.presentation.CommentStatus
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffActions
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffUi
import io.github.eslamasabry.oc.feature.review.presentation.GuideSection
import io.github.eslamasabry.oc.feature.review.presentation.GuideUi
import io.github.eslamasabry.oc.feature.review.presentation.LineComment
import io.github.eslamasabry.oc.feature.review.presentation.ReviewGuide
import io.github.eslamasabry.oc.feature.review.presentation.ReviewLoad
import io.github.eslamasabry.oc.feature.review.presentation.ReviewScope
import io.github.eslamasabry.oc.feature.review.presentation.StagedActions
import io.github.eslamasabry.oc.feature.review.presentation.StagedConfirm
import io.github.eslamasabry.oc.feature.review.presentation.StagedPhase
import io.github.eslamasabry.oc.feature.review.presentation.StagedUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoAllUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoBlock
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereActions
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereUi
import io.github.eslamasabry.oc.feature.review.ui.ChangesScreen
import io.github.eslamasabry.oc.feature.review.ui.FileDiffScreen
import io.github.eslamasabry.oc.feature.review.ui.StagedScreen
import io.github.eslamasabry.oc.feature.review.ui.UndoFromHereScreen
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Audit capture only: the review pages in every state their models allow. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "review"

    private class Calls : FileDiffActions, ChangesActions {
        override fun back() = Unit
        override fun retry() = Unit
        override fun removeComment(id: String) = Unit
        override fun sendComments() = Unit
        override fun openFile(path: String) = Unit
        override fun keepAll() = Unit
        override fun undoAll() = Unit
        override fun confirmUndoAll() = Unit
        override fun dismissConfirm() = Unit
        override fun openStaged() = Unit
        override fun sendBack() = Unit
        override fun sendBackNote(note: String?) = Unit
        override fun guided(on: Boolean) = Unit
    }

    private class Undo : UndoFromHereActions {
        override fun back() = Unit
        override fun putFilesBack(apply: Boolean) = Unit
        override fun confirm() = Unit
    }

    private class Staged : StagedActions {
        override fun back() = Unit
        override fun retry() = Unit
        override fun openFile(path: String) = Unit
        override fun reviewLatest() = Unit
        override fun askKeep() = Unit
        override fun askPutBack() = Unit
        override fun confirm() = Unit
        override fun dismissConfirm() = Unit
    }

    @Composable
    private fun Wrap(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) { content() }
    }

    private val longPath = "modules/feature/deeply/nested/package/structure/an_extremely_long_generated_file_name_for_wrapping_tests.kt"

    private val files =
        persistentListOf(
            ChangeRowUi("src/app/Main.kt", "Main.kt", "src/app", 3, 1, hasDiff = true),
            ChangeRowUi("README.md", "README.md", null, null, null, hasDiff = false),
            ChangeRowUi("test/upload_test.kt", "upload_test.kt", "test", 9, 0, hasDiff = true, viewed = true),
            ChangeRowUi(longPath, "an_extremely_long_generated_file_name_for_wrapping_tests.kt", "modules/feature/deeply/nested/package/structure", 120, 45, hasDiff = true),
        )

    private val ready = ChangesUi(turn = false, load = ReviewLoad.Ready, files = files, added = null, removed = null, undoAll = UndoAllUi.Offered)

    @Test
    fun changes() = Audit.guard("${m}__changes__build") {
        Audit.shoot(m, "changes", "loading") { Wrap { ChangesScreen(ChangesUi(turn = false), Calls()) } }
        Audit.shoot(m, "changes", "failed") { Wrap { ChangesScreen(ChangesUi(turn = false, load = ReviewLoad.Failed(Failure.Network())), Calls()) } }
        Audit.shoot(m, "changes", "conversation") { Wrap { ChangesScreen(ready.copy(added = 132, removed = 46), Calls()) } }
        Audit.shoot(m, "changes", "turn") { Wrap { ChangesScreen(ready.copy(turn = true), Calls()) } }
        Audit.shoot(m, "changes", "empty") { Wrap { ChangesScreen(ready.copy(files = persistentListOf()), Calls()) } }
        Audit.shoot(m, "changes", "incomplete") { Wrap { ChangesScreen(ready.copy(complete = false), Calls()) } }
        Audit.shoot(m, "changes", "undo-unavailable") { Wrap { ChangesScreen(ready.copy(undoAll = UndoAllUi.Unavailable), Calls()) } }
        Audit.shoot(m, "changes", "undo-unknown") { Wrap { ChangesScreen(ready.copy(undoAll = UndoAllUi.Unknown), Calls()) } }
        Audit.shoot(m, "changes", "undo-waiting") { Wrap { ChangesScreen(ready.copy(undoAll = UndoAllUi.Waiting), Calls()) } }
        Audit.shoot(m, "changes", "busy") { Wrap { ChangesScreen(ready.copy(busy = true), Calls()) } }
        Audit.shoot(m, "changes", "working") { Wrap { ChangesScreen(ready.copy(working = true), Calls()) } }
        Audit.shoot(m, "changes", "confirm-undo-all") { Wrap { ChangesScreen(ready.copy(confirming = true), Calls()) } }
        Audit.shoot(m, "changes", "action-failed") { Wrap { ChangesScreen(ready.copy(actionFailure = Failure.Network()), Calls()) } }
        Audit.shoot(m, "changes", "working-tree") { Wrap { ChangesScreen(ready.copy(workingTree = true, scope = ReviewScope.WorkingTree), Calls()) } }
        Audit.shoot(m, "changes", "scope-picker") {
            Wrap {
                ChangesScreen(
                    ready.copy(canPickScope = true, scopes = persistentListOf(ReviewScope.Conversation, ReviewScope.WorkingTree, ReviewScope.Branch), scope = ReviewScope.Branch),
                    Calls(),
                )
            }
        }
        Audit.shoot(m, "changes", "send-back") { Wrap { ChangesScreen(ready.copy(canSendBack = true), Calls()) } }
        Audit.shoot(m, "changes", "send-back-note") { Wrap { ChangesScreen(ready.copy(canSendBack = true, noting = true), Calls()) } }
        Audit.shoot(m, "changes", "send-back-failed") { Wrap { ChangesScreen(ready.copy(canSendBack = true, noting = true, sendBackFailure = Failure.Network()), Calls()) } }
    }

    @Test
    fun changesComments() = Audit.guard("${m}__changes-comments__build") {
        val viewedAll = persistentListOf(ChangeRowUi("src/a.kt", "a.kt", "src", 2, 1, hasDiff = true, viewed = true))
        Audit.shoot(m, "changes", "drafts") { Wrap { ChangesScreen(ready.copy(drafts = 3), Calls()) } }
        Audit.shoot(m, "changes", "drafts-and-waiting") { Wrap { ChangesScreen(ready.copy(drafts = 2, waiting = 1), Calls()) } }
        Audit.shoot(m, "changes", "all-viewed-with-drafts") { Wrap { ChangesScreen(ready.copy(files = viewedAll, drafts = 2), Calls()) } }
        Audit.shoot(m, "changes", "sending-comments") { Wrap { ChangesScreen(ready.copy(drafts = 2, sendingComments = true), Calls()) } }
        Audit.shoot(m, "changes", "comments-failed") { Wrap { ChangesScreen(ready.copy(drafts = 2, commentsFailure = Failure.Network()), Calls()) } }
    }

    @Test
    fun guided() = Audit.guard("${m}__guided__build") {
        val guide =
            ReviewGuide(
                persistentListOf(
                    GuideSection("Retry the upload", "Uploads failed on flaky networks, so they now retry.", persistentListOf("src/app/Main.kt")),
                    GuideSection("A section title that is long enough to wrap onto a second line on a phone", "A longer explanation that goes on for a while, so the paragraph wraps over several lines and shows how the page holds it.", persistentListOf("test/upload_test.kt", longPath)),
                ),
                persistentListOf("README.md"),
            )
        val base = ready.copy(canGuide = true, undoAll = UndoAllUi.Unavailable)
        Audit.shoot(m, "changes-guided", "off") { Wrap { ChangesScreen(base, Calls()) } }
        Audit.shoot(m, "changes-guided", "making") { Wrap { ChangesScreen(base.copy(guide = GuideUi.Making), Calls()) } }
        Audit.shoot(m, "changes-guided", "ready") { Wrap { ChangesScreen(base.copy(guide = GuideUi.Ready(guide), drafts = 3), Calls()) } }
        Audit.shoot(m, "changes-guided", "fallback") { Wrap { ChangesScreen(base.copy(guide = GuideUi.Fallback(Failure.Network())), Calls()) } }
    }

    // ---- One file's diff

    private val diff = FileDiff("src/a.kt", before = "val x = 1\nval y = 2\n", after = "val x = 10\nval y = 2\nval z = 3\n", additions = 2, deletions = 1)

    private fun comment(status: CommentStatus, text: String = "Call it count") =
        LineComment("c-$status", CommentAnchor("src/a.kt", CommentSide.New, 1, 1, persistentListOf("val x = 10")), text, status)

    private val page = FileDiffUi("src/a.kt", load = ReviewLoad.Ready, diff = diff, canComment = true)

    @Test
    fun fileDiff() = Audit.guard("${m}__file-diff__build") {
        Audit.shoot(m, "file-diff", "loading") { Wrap { FileDiffScreen(FileDiffUi("src/a.kt"), Calls()) } }
        Audit.shoot(m, "file-diff", "failed") { Wrap { FileDiffScreen(FileDiffUi("src/a.kt", load = ReviewLoad.Failed(Failure.Network())), Calls()) } }
        Audit.shoot(m, "file-diff", "ready") { Wrap { FileDiffScreen(page, Calls()) } }
        Audit.shoot(m, "file-diff", "ready-read-only") { Wrap { FileDiffScreen(page.copy(canComment = false), Calls()) } }
        Audit.shoot(m, "file-diff", "missing") { Wrap { FileDiffScreen(FileDiffUi("src/a.kt", load = ReviewLoad.Ready, missing = true), Calls()) } }
        Audit.shoot(m, "file-diff", "no-diff") { Wrap { FileDiffScreen(FileDiffUi("src/a.kt", load = ReviewLoad.Ready, noDiff = true), Calls()) } }
        Audit.shoot(m, "file-diff", "long-path") { Wrap { FileDiffScreen(page.copy(path = longPath), Calls()) } }
        Audit.shoot(m, "file-diff", "comment-draft") { Wrap { FileDiffScreen(page.copy(comments = persistentListOf(comment(CommentStatus.Draft)), drafts = 1), Calls()) } }
        Audit.shoot(m, "file-diff", "comment-pending") { Wrap { FileDiffScreen(page.copy(comments = persistentListOf(comment(CommentStatus.Pending))), Calls()) } }
        Audit.shoot(m, "file-diff", "comment-resolved") { Wrap { FileDiffScreen(page.copy(comments = persistentListOf(comment(CommentStatus.Resolved))), Calls()) } }
        Audit.shoot(m, "file-diff", "comment-long-text") {
            Wrap {
                FileDiffScreen(
                    page.copy(
                        comments = persistentListOf(comment(CommentStatus.Draft, "Please rename this to something that explains what is being counted, and also double check the edge case when the list is empty or the value overflows.")),
                        drafts = 4,
                    ),
                    Calls(),
                )
            }
        }
        Audit.shoot(m, "file-diff", "composing-comment") {
            Wrap { FileDiffScreen(page.copy(commenting = CommentAnchor("src/a.kt", CommentSide.New, 1, 1, persistentListOf("val x = 10"))), Calls()) }
        }
        Audit.shoot(m, "file-diff", "sending-comments") {
            Wrap { FileDiffScreen(page.copy(comments = persistentListOf(comment(CommentStatus.Draft)), drafts = 1, sending = true), Calls()) }
        }
        Audit.shoot(m, "file-diff", "send-failed") {
            Wrap { FileDiffScreen(page.copy(comments = persistentListOf(comment(CommentStatus.Draft)), drafts = 1, sendFailure = Failure.Network()), Calls()) }
        }
    }

    // ---- Undo from here and the staged undo

    @Test
    fun undoFromHere() = Audit.guard("${m}__undo-from-here__build") {
        val base = UndoFromHereUi(load = ReviewLoad.Ready, staged = true, prompt = "make it blue", messagesAfter = 3)
        Audit.shoot(m, "undo-from-here", "loading") { Wrap { UndoFromHereScreen(UndoFromHereUi(), Undo()) } }
        Audit.shoot(m, "undo-from-here", "failed") { Wrap { UndoFromHereScreen(UndoFromHereUi(load = ReviewLoad.Failed(Failure.Network())), Undo()) } }
        Audit.shoot(m, "undo-from-here", "staged") { Wrap { UndoFromHereScreen(base, Undo()) } }
        Audit.shoot(m, "undo-from-here", "staged-files-not-applied") { Wrap { UndoFromHereScreen(base.copy(applyFiles = false), Undo()) } }
        Audit.shoot(m, "undo-from-here", "one-step") {
            Wrap { UndoFromHereScreen(base.copy(staged = false, edited = persistentListOf("lib/a.dart", longPath)), Undo()) }
        }
        Audit.shoot(m, "undo-from-here", "unavailable") { Wrap { UndoFromHereScreen(base.copy(available = false), Undo()) } }
        Audit.shoot(m, "undo-from-here", "prompt-unavailable") { Wrap { UndoFromHereScreen(base.copy(prompt = null, promptUnavailable = true), Undo()) } }
        Audit.shoot(m, "undo-from-here", "attachment-only-prompt") { Wrap { UndoFromHereScreen(base.copy(prompt = "", messagesAfter = null), Undo()) } }
        Audit.shoot(m, "undo-from-here", "long-prompt") {
            Wrap { UndoFromHereScreen(base.copy(prompt = "Rewrite the whole settings screen so it groups related options, explains each one in plain words, and keeps the old behaviour for anyone who already changed something."), Undo()) }
        }
        Audit.shoot(m, "undo-from-here", "working") { Wrap { UndoFromHereScreen(base.copy(working = true), Undo()) } }
        Audit.shoot(m, "undo-from-here", "blocked-busy") { Wrap { UndoFromHereScreen(base.copy(block = UndoBlock.Busy), Undo()) } }
        Audit.shoot(m, "undo-from-here", "blocked-stale") { Wrap { UndoFromHereScreen(base.copy(block = UndoBlock.Stale), Undo()) } }
        Audit.shoot(m, "undo-from-here", "action-failed") { Wrap { UndoFromHereScreen(base.copy(failure = Failure.Network()), Undo()) } }
    }

    @Test
    fun staged() = Audit.guard("${m}__staged__build") {
        val review = StagedUi(
            phase = StagedPhase.Review,
            title = "Blue app",
            prompt = "make it blue",
            messagesAfter = 3,
            files = persistentListOf(FileDiff("src/a.kt", additions = 2, deletions = 1), FileDiff(longPath, additions = 20, deletions = 0)),
        )
        Audit.shoot(m, "staged", "loading") { Wrap { StagedScreen(StagedUi(), Staged()) } }
        Audit.shoot(m, "staged", "none") { Wrap { StagedScreen(StagedUi(phase = StagedPhase.None), Staged()) } }
        Audit.shoot(m, "staged", "review") { Wrap { StagedScreen(review, Staged()) } }
        Audit.shoot(m, "staged", "review-no-preview") { Wrap { StagedScreen(review.copy(files = null), Staged()) } }
        Audit.shoot(m, "staged", "review-no-files") { Wrap { StagedScreen(review.copy(files = persistentListOf()), Staged()) } }
        Audit.shoot(m, "staged", "review-prompt-unavailable") { Wrap { StagedScreen(review.copy(prompt = null, promptUnavailable = true), Staged()) } }
        Audit.shoot(m, "staged", "stale") { Wrap { StagedScreen(StagedUi(phase = StagedPhase.Stale), Staged()) } }
        Audit.shoot(m, "staged", "kept") { Wrap { StagedScreen(StagedUi(phase = StagedPhase.Kept), Staged()) } }
        Audit.shoot(m, "staged", "restored") { Wrap { StagedScreen(StagedUi(phase = StagedPhase.Restored), Staged()) } }
        Audit.shoot(m, "staged", "confirm-keep") { Wrap { StagedScreen(review.copy(confirm = StagedConfirm.Keep), Staged()) } }
        Audit.shoot(m, "staged", "confirm-put-back") { Wrap { StagedScreen(review.copy(confirm = StagedConfirm.PutBack), Staged()) } }
        Audit.shoot(m, "staged", "busy") { Wrap { StagedScreen(review.copy(busy = true), Staged()) } }
        Audit.shoot(m, "staged", "working") { Wrap { StagedScreen(review.copy(working = true, confirm = StagedConfirm.Keep), Staged()) } }
        Audit.shoot(m, "staged", "action-failed") { Wrap { StagedScreen(review.copy(failure = Failure.Network()), Staged()) } }
        Audit.shoot(m, "staged", "load-failed") { Wrap { StagedScreen(StagedUi(loadFailure = Failure.Network()), Staged()) } }
    }
}
