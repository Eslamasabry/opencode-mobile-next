package io.github.eslamasabry.oc.feature.git.rb.o46

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import io.github.eslamasabry.oc.engine.git.*
import io.github.eslamasabry.oc.feature.git.presentation.*
import io.github.eslamasabry.oc.feature.git.ui.GitScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@OptIn(ExperimentalTestApi::class)
class GitStepsTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions =
        Proxy.newProxyInstance(
            GitActions::class.java.classLoader,
            arrayOf(GitActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "GitStepActions"
                else -> {
                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as GitActions
    private val snapshot =
        GitSnapshot(
            status = GitStatus(GitBranchState("main", "abc", "origin/main")),
            branches =
                GitBranches(
                    local =
                        persistentListOf(
                            GitBranch("main", remote = false, current = true),
                            GitBranch("feature", remote = false, upstream = "origin/feature"),
                        )
                ),
        )

    @Composable
    private fun Page(sheet: GitSheet, busy: GitBusy? = null) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            GitScreen(
                GitUi(
                    view = GitPageView.Ready(snapshot),
                    sheet = sheet,
                    busy = busy,
                    directory = "/work/app",
                ),
                actions,
            )
        }
    }

    @Composable
    private fun Repository(state: GitUi) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            GitScreen(state, actions)
        }
    }

    @Test
    fun `B-NAV-021 failed save keeps draft and saved before failed send clears it`() =
        runComposeUiTest {
            val file = GitFileChange("app.kt", unstaged = GitChangeKind.Modified)
            val ready = snapshot.copy(status = snapshot.status.copy(files = persistentListOf(file)))
            val state =
                mutableStateOf(
                    GitUi(view = GitPageView.Ready(ready), selected = persistentSetOf(file.path))
                )
            setContent { Repository(state.value) }
            val field = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-message")))
            field.performScrollTo().performTextReplacement("My saved work")
            runOnIdle {
                state.value =
                    state.value.copy(
                        generation = 1,
                        result =
                            GitActionResult.failure(
                                GitAction.Commit,
                                GitFailure(GitProblem.EmptyMessage),
                            ),
                    )
            }
            field.assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString("My saved work"),
                )
            )
            runOnIdle {
                state.value =
                    state.value.copy(
                        generation = 2,
                        savedNotSent = true,
                        result =
                            GitActionResult.failure(
                                GitAction.Push,
                                GitFailure(GitProblem.EnvironmentUnavailable),
                            ),
                    )
            }
            field.assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
            )
        }

    @Test
    fun `B-NAV-020 explicitly edited suggestion survives changed selection`() = runComposeUiTest {
        val file = GitFileChange("app.kt", unstaged = GitChangeKind.Modified)
        val second = GitFileChange("other.kt", unstaged = GitChangeKind.Modified)
        val ready =
            snapshot.copy(status = snapshot.status.copy(files = persistentListOf(file, second)))
        val state =
            mutableStateOf(
                GitUi(view = GitPageView.Ready(ready), selected = persistentSetOf(file.path))
            )
        setContent { Repository(state.value) }
        val field = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-message")))
        field.performScrollTo().performSemanticsAction(SemanticsActions.SetText) { edit ->
            edit(AnnotatedString(""))
            edit(AnnotatedString("Update app.kt"))
        }
        runOnIdle {
            state.value = state.value.copy(selected = persistentSetOf(file.path, second.path))
        }
        field.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString("Update app.kt"),
            )
        )
    }

    @Test
    fun `B-NAV-020 half finished operations hold pull and branch switching`() = runComposeUiTest {
        setContent {
            Repository(
                GitUi(view = GitPageView.Ready(snapshot.copy(operation = GitOperation.Rebase)))
            )
        }
        onNodeWithTag("git-branch-row").assertIsNotEnabled()
        onNodeWithTag("git-pull-row").assertIsNotEnabled()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `B-NAV-020 an unchecked remote never claims up to date`() = runComposeUiTest {
        val ready =
            snapshot.copy(
                remotes =
                    persistentListOf(
                        GitRemote("origin", GitRemoteKind.Other, "https://example.invalid/app.git")
                    )
            )
        val state = mutableStateOf(GitUi(view = GitPageView.Ready(ready), check = GitCheck.Idle))
        setContent { Repository(state.value) }
        onNodeWithText("Up to date").assertDoesNotExist()
        runOnIdle { state.value = state.value.copy(check = GitCheck.Done) }
        onNodeWithText("Up to date").assertExists()
    }

    @Test
    fun `B-NAV-020 conflict elsewhere holds saving even with a selected clean path`() =
        runComposeUiTest {
            val file = GitFileChange("app.kt", unstaged = GitChangeKind.Modified)
            val conflicted = GitFileChange("conflict.kt", conflict = GitConflictKind.BothModified)
            val ready =
                snapshot.copy(
                    status = snapshot.status.copy(files = persistentListOf(file, conflicted))
                )
            setContent {
                Repository(
                    GitUi(view = GitPageView.Ready(ready), selected = persistentSetOf(file.path))
                )
            }
            onNodeWithTag("git-save").performScrollTo().assertIsNotEnabled()
            assertEquals(emptyList(), calls)
        }

    @Test
    fun `B-NAV-020 branch list shows tracking and dispatches the chosen branch once`() =
        runComposeUiTest {
            setContent { Page(GitSheet.Branches) }
            onNodeWithText("origin/feature").assertExists()
            onNodeWithText("feature").performClick()
            assertEquals(listOf<Pair<String, List<Any?>>>("switchBranch" to listOf("feature")), calls)
        }

    @Test
    fun `B-NAV-020 invalid branch is refused before the action and valid name is trimmed`() =
        runComposeUiTest {
            setContent { Page(GitSheet.Branches) }
            val field = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-new-branch")))
            field.performTextInput("bad name")
            onNodeWithText("New branch from main").assertIsNotEnabled()
            field.performTextReplacement(" feature ")
            onNodeWithText("New branch from main").performClick()
            assertEquals(listOf<Pair<String, List<Any?>>>("createBranch" to listOf("feature")), calls)
        }

    @Test
    fun `B-NAV-020 busy branch list cannot start another switch`() = runComposeUiTest {
        setContent { Page(GitSheet.Branches, GitBusy.Pull) }
        onNodeWithText("feature").assertIsNotEnabled()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `B-BUILD-014 clone suggests a visible folder and preserves an intentionally cleared name`() =
        runComposeUiTest {
            setContent { Page(GitSheet.Clone) }
            val url = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-clone-url")))
            val folder =
                onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-clone-folder")))
            url.performTextInput("https://example.invalid/repo.git")
            folder.assertTextContains("repo")
            folder.performTextReplacement("")
            url.performTextReplacement("https://example.invalid/another.git")
            folder.assertTextEquals("")
            // Enabled state lives on the button's merged node, not on its inner label text.
            onNodeWithText("Clone").assertIsNotEnabled()
            folder.performTextInput("mine")
            onNode(hasText("Clone") and hasClickAction()).performClick()
            assertEquals(
                listOf<Pair<String, List<Any?>>>("clone" to listOf("https://example.invalid/another.git", "mine")),
                calls,
            )
        }

    @Test
    fun `B-NAV-020 identity holds invalid input and saves the trimmed author`() = runComposeUiTest {
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                GitScreen(
                    GitUi(view = GitPageView.Ready(snapshot.copy(identity = GitIdentity()))),
                    actions,
                )
            }
        }
        val save = onNodeWithText("Save name and email")
        save.performScrollTo().assertIsNotEnabled()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-identity-name")))
            .performTextInput(" Owner ")
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-identity-email")))
            .performTextInput(" owner@example.invalid ")
        save.performScrollTo().performClick()
        assertEquals(listOf<Pair<String, List<Any?>>>("setIdentity" to listOf("Owner", "owner@example.invalid")), calls)
    }

    @Test
    fun `B-NAV-020 both save actions hold whitespace and preserve the entered message`() =
        runComposeUiTest {
            val file = GitFileChange("app.kt", unstaged = GitChangeKind.Modified)
            val ready =
                snapshot.copy(
                    status = snapshot.status.copy(files = persistentListOf(file)),
                    remotes =
                        persistentListOf(
                            GitRemote(
                                "origin",
                                GitRemoteKind.GitHubHttps,
                                "https://github.com/example/app.git",
                            )
                        ),
                )
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    GitScreen(
                        GitUi(
                            view = GitPageView.Ready(ready),
                            selected = persistentSetOf(file.path),
                        ),
                        actions,
                    )
                }
            }
            val message = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-message")))
            message.performScrollTo().performTextReplacement("  \n ")
            onNodeWithTag("git-save").performScrollTo().assertIsNotEnabled()
            onNodeWithTag("git-save-send").performScrollTo().assertIsNotEnabled()
            assertEquals(emptyList(), calls)
            message.performScrollTo().performTextReplacement(" Keep this message ")
            onNodeWithTag("git-save").assertIsEnabled()
            onNodeWithTag("git-save-send").performScrollTo().performClick()
            assertEquals(listOf<Pair<String, List<Any?>>>("commitAndPush" to listOf(" Keep this message ")), calls)
        }
}
