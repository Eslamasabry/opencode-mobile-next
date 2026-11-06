package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** GitHub that only answers repository creation. */
private class PublishGitHub(var created: GitHubReply) : GitHubHttp {
    val creates = mutableListOf<Triple<String, String, Boolean>>()

    override suspend fun postForm(address: String, fields: Map<String, String>): GitHubReply = GitHubReply.Unreachable

    override suspend fun getUser(token: String): GitHubReply = GitHubReply.Unreachable

    override suspend fun listRepositories(token: String, perPage: Int): GitHubReply = GitHubReply.Unreachable

    override suspend fun createRepository(token: String, name: String, isPrivate: Boolean): GitHubReply {
        creates += Triple(token, name, isPrivate)
        return created
    }
}

private fun createdRepo(): GitHubReply =
    GitHubReply.Answer(
        201,
        JsonObject(
            mapOf(
                "full_name" to JsonPrimitive("octocat/app"),
                "html_url" to JsonPrimitive("https://github.com/octocat/app"),
                "clone_url" to JsonPrimitive("https://github.com/octocat/app.git"),
                "default_branch" to JsonPrimitive("main"),
                "private" to JsonPrimitive(true),
            ),
        ),
    )

class GitPublishTest {
    private val ref = ConversationRef(ProfileId("p1"), SessionId("s"))
    private val nul = "\u0000"
    private fun snapshot(remotes: String) =
        arrayOf(
            StepReply("status", 0, "# branch.oid abc$nul# branch.head main$nul"),
            StepReply("unstaged"),
            StepReply("staged"),
            StepReply("log"),
            StepReply("branches"),
            StepReply("operation"),
            StepReply("remotes", 0, remotes),
        )

    private fun shellFor(host: GitHost, shell: GitShell) = GitWorkspaces { GitWorkspaceResolution.Available(GitWorkspace(host, "/home/oc/my app", shell)) }

    private suspend fun signedIn(scope: TestScope, http: GitHubHttp, withToken: Boolean = true): GitHubAccount {
        val redactor = Redactor()
        val store = GitHubTokenStore(MemorySecrets(), redactor)
        if (withToken) store.write(ref.profileId, GitHubCredential("gho_secret_token", "octocat"))
        val account =
            GitHubAccount(ref.profileId, store, GitHubDeviceFlow(http, redactor, { 0L }, sleep = {}), scope, { 0L })
        account.load()
        return account
    }

    @Test
    fun `B-NAV-020 initialize runs git init -b main in the project and rereads`() = runTest {
        val shell = FakeShell.steps(StepReply("status", 128, "", "fatal: not a git repository"), StepReply("run"))
        val page = GitPage(ref, shellFor(GitHost.Server, shell))
        page.refresh()
        assertEquals(GitPageView.NotRepository, page.state.value.view)
        page.initialize()
        val ran = shell.scripts.first { runs(it, "run") }
        assertTrue("init" in ran && "-b" in ran && "main" in ran, ran)
        assertEquals(GitActionNote.Initialized, page.state.value.result?.note)
        assertEquals(GitAction.Initialize, page.state.value.result?.action)
        assertNull(page.state.value.busy)
    }

    @Test
    fun `B-NAV-020 a failed initialize says why and keeps the not-a-repository view`() = runTest {
        val shell =
            FakeShell.steps(
                StepReply("status", 128, "", "fatal: not a git repository"),
                StepReply("run", 1, "", "fatal: cannot mkdir .git: Permission denied"),
            )
        val page = GitPage(ref, shellFor(GitHost.Server, shell))
        page.refresh()
        page.initialize()
        assertFalse(page.state.value.result!!.ok)
        assertEquals(GitPageView.NotRepository, page.state.value.view)
    }

    @Test
    fun `B-SEC-082 publish creates the repository privately and connects it as origin without sending files`() = runTest {
        val http = PublishGitHub(createdRepo())
        val shell = FakeShell.steps(*snapshot(""), StepReply("run"))
        val page = GitPage(ref, shellFor(GitHost.BuiltinLinux, shell), signedIn(this, http))
        page.refresh()
        page.publish("my-app", isPrivate = true)
        assertEquals(listOf(Triple("gho_secret_token", "my-app", true)), http.creates)
        val added = shell.scripts.last { runs(it, "run") }
        assertTrue("remote" in added && "add" in added && "origin" in added && "https://github.com/octocat/app.git" in added, added)
        // Nothing is saved or sent: the project's files stay where they are.
        assertFalse(shell.scripts.any { runs(it, "push") || runs(it, "commit") || runs(it, "add") })
        val result = page.state.value.result!!
        assertEquals(GitActionNote.GitHubRepositoryCreated, result.note)
        assertEquals("octocat/app", result.target)
        assertEquals(GitAction.Publish, result.action)
    }

    @Test
    fun `B-SEC-082 publish can make a public repository`() = runTest {
        val http = PublishGitHub(createdRepo())
        val page = GitPage(ref, shellFor(GitHost.BuiltinLinux, FakeShell.steps(*snapshot(""), StepReply("run"))), signedIn(this, http))
        page.refresh()
        page.publish("my-app", isPrivate = false)
        assertEquals(false, http.creates.single().third)
    }

    @Test
    fun `B-SEC-082 publish needs the app's GitHub sign-in, which only built-in Linux offers`() = runTest {
        val http = PublishGitHub(createdRepo())
        val server = GitPage(ref, shellFor(GitHost.Server, FakeShell.steps(*snapshot(""), StepReply("run"))), signedIn(this, http))
        server.refresh()
        server.publish("my-app", true)
        assertEquals(GitProblem.AuthRequired, server.state.value.result?.problem)
        assertTrue(http.creates.isEmpty())
    }

    @Test
    fun `B-SEC-082 a missing token asks to sign in again and a refused one is forgotten`() = runTest {
        val http = PublishGitHub(createdRepo())
        val signedOut = GitPage(ref, shellFor(GitHost.BuiltinLinux, FakeShell.steps(*snapshot(""), StepReply("run"))), signedIn(this, http, withToken = false))
        signedOut.refresh()
        signedOut.publish("my-app", true)
        assertEquals(GitProblem.GitHubTokenRejected, signedOut.state.value.result?.problem)
        assertTrue(http.creates.isEmpty())

        val refused = PublishGitHub(GitHubReply.Answer(401, null))
        val account = signedIn(this, refused)
        val page = GitPage(ref, shellFor(GitHost.BuiltinLinux, FakeShell.steps(*snapshot(""), StepReply("run"))), account)
        page.refresh()
        page.publish("my-app", true)
        assertEquals(GitProblem.GitHubTokenRejected, page.state.value.result?.problem)
        assertEquals(GitHubSignInProblem.TokenRejected, assertIs<GitHubSignIn.SignedOut>(account.state.value).problem)
    }

    @Test
    fun `B-SEC-082 GitHub's answers read as plain problems and never carry the token`() = runTest {
        val cases =
            mapOf(
                GitHubReply.Answer(403, null) to GitProblem.NoPermission,
                GitHubReply.Unreachable to GitProblem.Network,
                GitHubReply.Answer(500, null) to GitProblem.Network,
                GitHubReply.Answer(422, null) to GitProblem.InvalidGitHubRepositoryName,
                GitHubReply.Answer(
                    422,
                    JsonObject(
                        mapOf(
                            "errors" to
                                JsonArray(listOf(JsonObject(mapOf("code" to JsonPrimitive("already_exists"), "field" to JsonPrimitive("name"))))),
                        ),
                    ),
                ) to GitProblem.GitHubRepositoryNameUnavailable,
            )
        for ((reply, problem) in cases) {
            val page = GitPage(ref, shellFor(GitHost.BuiltinLinux, FakeShell.steps(*snapshot(""), StepReply("run"))), signedIn(this, PublishGitHub(reply)))
            page.refresh()
            page.publish("my-app", true)
            val failure = assertNotNull(page.state.value.result?.failure)
            assertEquals(problem, failure.problem)
            assertFalse("gho_secret_token" in failure.details)
        }
    }

    @Test
    fun `B-SEC-082 a project that already has a remote is not published again`() = runTest {
        val http = PublishGitHub(createdRepo())
        val shell = FakeShell.steps(*snapshot("remote.origin.url https://github.com/o/r.git\n"), StepReply("run"))
        val page = GitPage(ref, shellFor(GitHost.BuiltinLinux, shell), signedIn(this, http))
        page.refresh()
        page.publish("my-app", true)
        assertEquals(GitProblem.RemoteExists, page.state.value.result?.problem)
        assertTrue(http.creates.isEmpty())
    }

    @Test
    fun `B-SEC-082 if the new repository cannot be connected the problem says GitHub made it`() = runTest {
        val http = PublishGitHub(createdRepo())
        val shell = FakeShell.steps(*snapshot(""), StepReply("run", 3, "", "error: remote origin already exists."))
        val page = GitPage(ref, shellFor(GitHost.BuiltinLinux, shell), signedIn(this, http))
        page.refresh()
        page.publish("my-app", true)
        val failure = assertNotNull(page.state.value.result?.failure)
        assertEquals(GitProblem.RemoteExists, failure.problem)
        assertTrue("GitHub created octocat/app" in failure.details)
    }
}
