package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun obj(vararg pairs: Pair<String, Any>): JsonObject =
    JsonObject(
        pairs.associate { (k, v) ->
            k to
                when (v) {
                    is Number -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                }
        },
    )

/** Scripted GitHub: answers posts in order; records every request's fields. */
private class FakeGitHub : GitHubHttp {
    val posts = mutableListOf<Pair<String, Map<String, String>>>()
    val answers = ArrayDeque<GitHubReply>()
    var user: GitHubReply = GitHubReply.Answer(200, obj("login" to "octocat", "id" to 7, "name" to "Mona"))
    var gate: CompletableDeferred<Unit>? = null

    /** Posts after this many wait for [gate]. */
    var gateAfter: Int = Int.MAX_VALUE

    override suspend fun postForm(address: String, fields: Map<String, String>): GitHubReply {
        posts += address to fields
        if (posts.size > gateAfter) gate?.await()
        return answers.removeFirst()
    }

    override suspend fun getUser(token: String): GitHubReply = user
}

internal class MemorySecrets : SecretStore {
    val values = LinkedHashMap<String, String>()
    var failWrites = false

    override suspend fun read(name: String): Outcome<String?> = Outcome.Ok(values[name])

    override suspend fun write(name: String, value: String): Outcome<Unit> {
        if (failWrites) return Outcome.Failed(Failure.SecretUnavailable())
        values[name] = value
        return Outcome.Ok(Unit)
    }

    override suspend fun delete(name: String): Outcome<Unit> {
        values.remove(name)
        return Outcome.Ok(Unit)
    }

    override suspend fun names(prefix: String): Outcome<Set<String>> = Outcome.Ok(values.keys.filter { it.startsWith(prefix) }.toSet())
}

class GitHubTest {
    private var now = 1_000_000L
    private val waits = mutableListOf<Long>()
    private val redactor = Redactor()
    private val github = FakeGitHub()
    private val flow = GitHubDeviceFlow(github, redactor, { now }, sleep = { waits += it; now += it })

    private fun code(interval: Int = 5, expires: Int = 900) =
        GitHubReply.Answer(
            200,
            obj(
                "device_code" to "dc-secret-123456",
                "user_code" to "WDJB-MJHT",
                "verification_uri" to "https://github.com/login/device",
                "expires_in" to expires,
                "interval" to interval,
            ),
        )

    private fun pending(error: String = "authorization_pending") = GitHubReply.Answer(200, obj("error" to error))

    private fun token() = GitHubReply.Answer(200, obj("access_token" to "gho_token_abcdef", "scope" to "repo", "token_type" to "bearer"))

    @Test
    fun `B-SEC-086 device sign-in uses the public client id and no secret, polls at the server interval, and returns the token`() = runTest {
        github.answers += listOf(code(), pending(), pending(), pending("slow_down"), token())
        val asked = assertIs<GitHubResult.Ok<GitHubDeviceCode>>(flow.requestCode()).value
        assertEquals(GITHUB_DEVICE_CODE_URL, github.posts[0].first)
        assertEquals(mapOf("client_id" to GITHUB_CLIENT_ID, "scope" to "repo"), github.posts[0].second)
        assertTrue(github.posts.none { "client_secret" in it.second })
        val credential = assertIs<GitHubResult.Ok<GitHubCredential>>(flow.pollForToken(asked)).value
        assertEquals(listOf(5_000L, 5_000L, 5_000L, 10_000L), waits)
        assertEquals("gho_token_abcdef", credential.accessToken)
        val profile = assertIs<GitHubResult.Ok<GitHubProfile>>(flow.fetchProfile(credential.accessToken)).value
        assertEquals("octocat", profile.login)
        // The code and the token never appear in printed text.
        assertFalse("dc-secret-123456" in asked.toString())
        assertFalse("gho_token_abcdef" in credential.toString())
        assertFalse("gho_token_abcdef" in redactor.redact("x gho_token_abcdef y"))
        assertFalse("dc-secret-123456" in redactor.redact("x dc-secret-123456 y"))
    }

    @Test
    fun `B-SEC-087 a slow-down answer with no interval adds five seconds`() = runTest {
        github.answers += listOf(pending("slow_down"), pending("slow_down"), token())
        val asked = GitHubDeviceCode("dc-secret-123456", "C", "https://github.com/login/device", now + 900_000, 5_000)
        assertIs<GitHubResult.Ok<GitHubCredential>>(flow.pollForToken(asked))
        assertEquals(listOf(5_000L, 10_000L, 15_000L), waits)
    }

    @Test
    fun `B-SEC-088 each failure ends with its typed reason`() = runTest {
        val asked = { GitHubDeviceCode("dc-secret-123456", "C", "https://github.com/login/device", now + 900_000, 5_000) }
        github.answers += pending("expired_token")
        assertEquals(GitHubSignInProblem.Expired, assertIs<GitHubResult.Problem>(flow.pollForToken(asked())).problem)
        github.answers += pending("access_denied")
        assertEquals(GitHubSignInProblem.Denied, assertIs<GitHubResult.Problem>(flow.pollForToken(asked())).problem)
        github.answers += GitHubReply.Answer(200, obj("error" to "device_flow_disabled"))
        assertEquals(GitHubSignInProblem.DeviceFlowDisabled, assertIs<GitHubResult.Problem>(flow.requestCode()).problem)
        // Expired by the local clock: no request is made.
        val before = github.posts.size
        val late = GitHubDeviceCode("dc-secret-123456", "C", "https://github.com/login/device", now + 1_000, 5_000)
        assertEquals(GitHubSignInProblem.Expired, assertIs<GitHubResult.Problem>(flow.pollForToken(late)).problem)
        assertEquals(before, github.posts.size)
        github.user = GitHubReply.Answer(401, null)
        assertEquals(GitHubSignInProblem.TokenRejected, assertIs<GitHubResult.Problem>(flow.fetchProfile("gho_token_abcdef")).problem)
    }

    @Test
    fun `B-SEC-089 brief network failures are retried, three in a row end it, cancel stops before the next poll`() = runTest {
        val asked = GitHubDeviceCode("dc-secret-123456", "C", "https://github.com/login/device", now + 900_000, 5_000)
        github.answers += listOf(GitHubReply.Unreachable, GitHubReply.Unreachable, token())
        assertIs<GitHubResult.Ok<GitHubCredential>>(flow.pollForToken(asked))
        github.answers += listOf(GitHubReply.Unreachable, GitHubReply.Unreachable, GitHubReply.Unreachable, token())
        assertEquals(GitHubSignInProblem.Network, assertIs<GitHubResult.Problem>(flow.pollForToken(asked)).problem)
        val before = github.posts.size
        assertEquals(GitHubSignInProblem.Cancelled, assertIs<GitHubResult.Problem>(flow.pollForToken(asked) { true }).problem)
        assertEquals(before, github.posts.size)
    }

    @Test
    fun `B-SEC-090 an expiring token is refreshed by rotating the pair and keeping the login`() = runTest {
        github.answers += GitHubReply.Answer(200, obj("access_token" to "ghu_new_token_1", "refresh_token" to "ghr_new_refresh_1", "expires_in" to 28_800))
        val old = GitHubCredential("ghu_old_token_1", "octocat", "repo", "ghr_old_refresh", now - 1, now + 1_000_000)
        val fresh = assertIs<GitHubResult.Ok<GitHubCredential>>(flow.refresh(old)).value
        assertEquals("ghu_new_token_1", fresh.accessToken)
        assertEquals("ghr_new_refresh_1", fresh.refreshToken)
        assertEquals("octocat", fresh.login)
        assertEquals("refresh_token", github.posts.last().second["grant_type"])
    }

    private fun account(scope: TestScope, secrets: MemorySecrets, profile: ProfileId = ProfileId("p1")) =
        GitHubAccount(profile, GitHubTokenStore(secrets, redactor), flow, scope, { now })

    @Test
    fun `B-SEC-084 sign-in walks its states and stores the token under the profile key`() = runTest {
        val secrets = MemorySecrets()
        val acct = account(this, secrets)
        acct.load()
        assertEquals(GitHubSignIn.SignedOut(), acct.state.value)
        github.answers += listOf(code(), pending(), token())
        acct.startSignIn()
        assertEquals(GitHubSignIn.RequestingCode, acct.state.value)
        advanceUntilIdle()
        assertEquals(GitHubSignIn.SignedIn("octocat"), acct.state.value)
        val stored = secrets.values["oc.githubToken.p1"]!!
        assertTrue("gho_token_abcdef" in stored && "octocat" in stored)
        assertEquals("gho_token_abcdef", acct.validToken())
    }

    @Test
    fun `B-SEC-084 a failed attempt says why and stores nothing`() = runTest {
        val secrets = MemorySecrets()
        val acct = account(this, secrets)
        acct.load()
        github.answers += listOf(code(), pending("access_denied"))
        acct.startSignIn()
        advanceUntilIdle()
        assertEquals(GitHubSignInProblem.Denied, assertIs<GitHubSignIn.SignedOut>(acct.state.value).problem)
        assertTrue(secrets.values.isEmpty())
    }

    @Test
    fun `B-SEC-084 cancel ignores a late token`() = runTest {
        val secrets = MemorySecrets()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val acct = account(scope, secrets)
        acct.load()
        github.answers += listOf(code(), token())
        github.gate = CompletableDeferred()
        github.gateAfter = 1
        acct.startSignIn()
        scope.testScheduler.runCurrent()
        // The token request is in flight (held by the gate) while the person presses Cancel.
        assertIs<GitHubSignIn.WaitingForUser>(acct.state.value)
        assertEquals(2, github.posts.size)
        acct.cancel()
        github.gate!!.complete(Unit)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(GitHubSignIn.SignedOut(), acct.state.value)
        assertTrue(secrets.values.isEmpty())
    }

    @Test
    fun `B-SEC-084 sign-out or a rejected token forgets it, an expired token is refreshed and the new pair stored`() = runTest {
        val secrets = MemorySecrets()
        val store = GitHubTokenStore(secrets, redactor)
        store.write(ProfileId("p1"), GitHubCredential("ghu_old_token_1", "octocat", "repo", "ghr_old_refresh", now - 1, now + 1_000_000))
        val acct = account(this, secrets)
        acct.load()
        assertEquals(GitHubSignIn.SignedIn("octocat"), acct.state.value)
        github.answers += GitHubReply.Answer(200, obj("access_token" to "ghu_new_token_1", "refresh_token" to "ghr_new_refresh_1", "expires_in" to 28_800))
        assertEquals("ghu_new_token_1", acct.validToken())
        assertEquals("ghu_new_token_1", (store.read(ProfileId("p1")) as Outcome.Ok).value?.accessToken)
        acct.tokenRejected()
        assertEquals(GitHubSignInProblem.TokenRejected, assertIs<GitHubSignIn.SignedOut>(acct.state.value).problem)
        assertNull(secrets.values["oc.githubToken.p1"])
        store.write(ProfileId("p1"), GitHubCredential("ghu_other_token", "octocat"))
        acct.signOut()
        assertNull(secrets.values["oc.githubToken.p1"])
    }

    @Test
    fun `B-SEC-085 removing a profile deletes its GitHub sign-in`() = runTest {
        val secrets = MemorySecrets()
        val store = GitHubTokenStore(secrets, redactor)
        store.write(ProfileId("kept"), GitHubCredential("gho_kept_token", "a"))
        store.write(ProfileId("gone"), GitHubCredential("gho_gone_token", "b"))
        secrets.values["pw.kept"] = "server-password"
        assertEquals(1, (store.sweep(setOf(ProfileId("kept"))) as Outcome.Ok).value)
        assertTrue("oc.githubToken.kept" in secrets.values)
        assertFalse("oc.githubToken.gone" in secrets.values)
        assertTrue("pw.kept" in secrets.values)
    }
}
