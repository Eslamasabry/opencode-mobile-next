package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Where the GitHub sign-in stands, for the page to show (port of lib/state/github_account.dart). */
public sealed interface GitHubSignIn {
    /** Not yet read from storage. */
    public data object Loading : GitHubSignIn

    /** No sign-in. [problem] says why the last attempt (or the stored token) ended; [details] is for Details only. */
    public data class SignedOut(val problem: GitHubSignInProblem? = null, val details: String = "") : GitHubSignIn

    /** Asking GitHub for a code. */
    public data object RequestingCode : GitHubSignIn

    /** Waiting for the person to enter [userCode] at [verificationUri] (opened through the external-link policy). */
    public data class WaitingForUser(val userCode: String, val verificationUri: String, val expiresAtMs: Long) : GitHubSignIn

    /** GitHub handed over a token; reading the account name. */
    public data object Finishing : GitHubSignIn

    public data class SignedIn(val login: String) : GitHubSignIn
}

/**
 * The GitHub sign-in token store: `oc.githubToken.<profileId>` in the [SecretStore] (JSON of [GitHubCredential], the
 * Flutter shape), so the profile deletion sweep and "reset saved sign-ins" find it. Every token read or written is
 * registered with the [Redactor] first. Never logs a value.
 */
public class GitHubTokenStore(private val secrets: SecretStore, private val redactor: Redactor) {
    public suspend fun read(profile: ProfileId): Outcome<GitHubCredential?> =
        when (val raw = secrets.read(keyFor(profile))) {
            is Outcome.Failed -> raw
            is Outcome.Ok -> Outcome.Ok(raw.value?.let(::decode)?.also(::register))
        }

    public suspend fun write(profile: ProfileId, credential: GitHubCredential): Outcome<Unit> {
        register(credential)
        return secrets.write(keyFor(profile), encode(credential))
    }

    public suspend fun delete(profile: ProfileId): Outcome<Unit> = secrets.delete(keyFor(profile))

    /** Deletes the sign-in of every profile not in [existing] (a removed server's GitHub sign-in; B-SEC-085). */
    public suspend fun sweep(existing: Set<ProfileId>): Outcome<Int> {
        val names =
            when (val listed = secrets.names(PREFIX)) {
                is Outcome.Failed -> return listed
                is Outcome.Ok -> listed.value
            }
        val keep = existing.map { keyFor(it) }.toSet()
        var removed = 0
        for (name in names) {
            if (name in keep) continue
            when (val gone = secrets.delete(name)) {
                is Outcome.Failed -> return gone
                is Outcome.Ok -> removed++
            }
        }
        return Outcome.Ok(removed)
    }

    private fun register(credential: GitHubCredential) {
        redactor.registerSecret(credential.accessToken)
        credential.refreshToken?.let(redactor::registerSecret)
    }

    override fun toString(): String = "GitHubTokenStore"

    public companion object {
        public const val PREFIX: String = "oc.githubToken."

        public fun keyFor(profile: ProfileId): String = PREFIX + profile.raw

        internal fun encode(credential: GitHubCredential): String =
            buildJsonObject {
                put("accessToken", credential.accessToken)
                put("login", credential.login)
                put("scope", credential.scope)
                credential.refreshToken?.let { put("refreshToken", it) }
                credential.expiresAtMs?.let { put("expiresAt", Instant.fromEpochMilliseconds(it).toString()) }
                credential.refreshExpiresAtMs?.let { put("refreshExpiresAt", Instant.fromEpochMilliseconds(it).toString()) }
            }.toString()

        internal fun decode(raw: String): GitHubCredential? {
            if (raw.isEmpty()) return null
            val json = ServerGitShell.parseJson(raw) as? JsonObject ?: return null
            val token = json.str("accessToken")
            if (token.isNullOrEmpty()) return null
            fun time(key: String): Long? =
                json.str(key)?.let {
                    try {
                        Instant.parse(it).toEpochMilliseconds()
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            return GitHubCredential(
                accessToken = token,
                login = json.str("login").orEmpty(),
                scope = json.str("scope").orEmpty(),
                refreshToken = json.str("refreshToken")?.takeIf { it.isNotEmpty() },
                expiresAtMs = time("expiresAt"),
                refreshExpiresAtMs = time("refreshExpiresAt"),
            )
        }
    }
}

/** What the Git page drives of a GitHub sign-in (the page never sees a token). */
public interface GitHubSignInControl {
    public val state: StateFlow<GitHubSignIn>

    public suspend fun load()

    public fun startSignIn()

    public fun cancel()

    public suspend fun signOut(): Outcome<Unit>
}

/**
 * One profile's GitHub sign-in (B-SEC-084): walks Loading → SignedOut → RequestingCode → WaitingForUser → Finishing →
 * SignedIn and stores the token under the profile key. A failed attempt says why and stores nothing; Cancel ignores a
 * late token; sign-out or a rejected token forgets it; an expired token is refreshed and the new pair stored.
 */
public class GitHubAccount(
    public val profile: ProfileId,
    private val tokens: GitHubTokenStore,
    private val flow: GitHubDeviceFlow,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) : GitHubSignInControl {
    private val mutableState = MutableStateFlow<GitHubSignIn>(GitHubSignIn.Loading)
    override val state: StateFlow<GitHubSignIn> = mutableState.asStateFlow()

    private var attempt = 0
    private var job: Job? = null

    /** Reads the stored sign-in once. A storage failure reads as signed out (the person can sign in again). */
    override suspend fun load() {
        if (mutableState.value != GitHubSignIn.Loading) return
        val stored = (tokens.read(profile) as? Outcome.Ok)?.value
        mutableState.value =
            if (stored != null && stored.login.isNotEmpty()) GitHubSignIn.SignedIn(stored.login) else GitHubSignIn.SignedOut()
    }

    /** Starts a device sign-in; a second tap while one runs does nothing. */
    override fun startSignIn() {
        if (job?.isActive == true) return
        val mine = ++attempt
        mutableState.value = GitHubSignIn.RequestingCode
        job = scope.launch { signIn(mine) }
    }

    private suspend fun signIn(mine: Int) {
        val code =
            when (val asked = flow.requestCode()) {
                is GitHubResult.Problem -> return endWith(mine, asked)
                is GitHubResult.Ok -> asked.value
            }
        if (mine != attempt) return
        mutableState.value = GitHubSignIn.WaitingForUser(code.userCode, code.verificationUri, code.expiresAtMs)
        val credential =
            when (val polled = flow.pollForToken(code) { mine != attempt }) {
                is GitHubResult.Problem -> return endWith(mine, polled)
                is GitHubResult.Ok -> polled.value
            }
        if (mine != attempt) return
        mutableState.value = GitHubSignIn.Finishing
        val who =
            when (val read = flow.fetchProfile(credential.accessToken)) {
                is GitHubResult.Problem -> return endWith(mine, read)
                is GitHubResult.Ok -> read.value
            }
        if (mine != attempt) return
        val stored = credential.withLogin(who.login)
        when (tokens.write(profile, stored)) {
            is Outcome.Failed -> mutableState.value = GitHubSignIn.SignedOut(GitHubSignInProblem.Unexpected)
            is Outcome.Ok -> mutableState.value = GitHubSignIn.SignedIn(who.login)
        }
    }

    private fun endWith(mine: Int, problem: GitHubResult.Problem) {
        if (mine != attempt || problem.problem == GitHubSignInProblem.Cancelled) return
        mutableState.value = GitHubSignIn.SignedOut(problem.problem, problem.details)
    }

    /** Stops waiting; a token that arrives later is ignored and nothing is stored. */
    override fun cancel() {
        attempt++
        job?.cancel()
        job = null
        mutableState.value = GitHubSignIn.SignedOut()
    }

    /** Forgets the sign-in on this device. */
    override suspend fun signOut(): Outcome<Unit> {
        attempt++
        job?.cancel()
        val deleted = tokens.delete(profile)
        if (deleted is Outcome.Ok) mutableState.value = GitHubSignIn.SignedOut()
        return deleted
    }

    /**
     * The profile was deleted: a sign-in still waiting must never write a token afterwards (B-SEC-085). Waits for the
     * attempt to stop, so a sweep that follows sees every write it could make.
     */
    public suspend fun forget() {
        attempt++
        job?.cancelAndJoin()
        job = null
        mutableState.value = GitHubSignIn.SignedOut()
    }

    /** GitHub refused the stored token: forget it and say so. */
    public suspend fun tokenRejected() {
        tokens.delete(profile)
        mutableState.value = GitHubSignIn.SignedOut(GitHubSignInProblem.TokenRejected)
    }

    /** The token to hand over, refreshed first when it expired (the new pair is stored). Null when signed out. */
    public suspend fun validToken(): String? {
        val current = (tokens.read(profile) as? Outcome.Ok)?.value ?: return null
        val now = nowMs()
        if (!current.expiredAt(now)) return current.accessToken
        if (!current.canRefreshAt(now)) {
            tokenRejected()
            return null
        }
        return when (val fresh = flow.refresh(current)) {
            is GitHubResult.Problem -> {
                tokenRejected()
                null
            }
            is GitHubResult.Ok -> {
                tokens.write(profile, fresh.value)
                fresh.value.accessToken
            }
        }
    }

    override fun toString(): String = "GitHubAccount(${state.value::class.simpleName})"
}
