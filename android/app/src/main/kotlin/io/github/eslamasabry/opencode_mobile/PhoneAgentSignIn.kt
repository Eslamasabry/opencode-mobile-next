package io.github.eslamasabry.opencode_mobile

import org.json.JSONObject
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Private Claude subscription sign-in pipes. Never a terminal or log source. */
class PhoneAgentSignIn(private val linux: BuiltinLinux) {
    private val lock = Any()
    private val runs = mutableMapOf<String, Run>()
    // A cancelled ID cannot be reused by a late start or status call.
    private val tombstones = mutableSetOf<String>()
    private val deletedProfiles = mutableSetOf<String>()

    private class Run(
        val profileId: String,
        val agentId: String,
        val runId: String,
        val method: String,
    ) {
        var phase = "signedOut"
        var failure: String? = null
        var url: String? = null
        var cancelled = false
        var loginRequested = false
        var acceptsCode = false
        var codeSubmitted = false
        var versionVerified = false
        var loginProcess: Process? = null
        var readerStarted = false
        val processes = mutableSetOf<Process>()
        val challengeReady = CountDownLatch(1)
        val readerDone = CountDownLatch(1)
    }

    private class AuthFailure(val kind: String) : Exception(kind)

    private fun failure(runId: String, kind: String): Map<String, Any?> =
        mapOf("runId" to runId, "phase" to "failed", "failure" to kind)

    private fun identity(
        profileId: String, agentId: String, runId: String, method: String,
        create: Boolean,
    ): Run = synchronized(lock) {
        val safe = Regex("^[a-zA-Z0-9_-]{1,128}$")
        if (!listOf(profileId, agentId, runId).all { safe.matches(it) }) {
            throw AuthFailure("invalidResponse")
        }
        if (method != "browserOAuthHost" || agentId != "claude") {
            throw AuthFailure("unavailable")
        }
        if (profileId in deletedProfiles) throw AuthFailure("staleRun")
        if (runId in tombstones) throw AuthFailure("staleRun")
        val existing = runs[runId]
        if (existing != null) {
            if (existing.profileId != profileId || existing.agentId != agentId ||
                existing.method != method || existing.cancelled) throw AuthFailure("staleRun")
            return@synchronized existing
        }
        if (!create || runs.size >= 16 || tombstones.size >= 1024) {
            throw AuthFailure("unavailable")
        }
        Run(profileId, agentId, runId, method).also { runs[runId] = it }
    }

    private fun snapshot(run: Run): Map<String, Any?> = synchronized(lock) {
        if (run.cancelled) return@synchronized failure(run.runId, "cancelled")
        mutableMapOf<String, Any?>("runId" to run.runId, "phase" to run.phase).apply {
            if (run.phase == "urlReady" || run.phase == "awaitingCode") {
                run.url?.let { put("url", it) }
            }
            run.failure?.let { put("failure", it) }
            if (run.phase == "limitReached") put("resetAt", null)
        }
    }

    private fun fail(run: Run, kind: String) = synchronized(lock) {
        if (!run.cancelled) {
            run.phase = if (kind == "limitReached") "limitReached" else "failed"
            run.failure = if (kind == "limitReached") null else kind
            run.url = null
            run.acceptsCode = false
            run.challengeReady.countDown()
        }
    }

    private fun launch(run: Run, args: List<String>, foreground: Boolean): Process =
        synchronized(lock) {
            if (run.cancelled || run.runId in tombstones || run.profileId in deletedProfiles) {
                throw AuthFailure("cancelled")
            }
            // Starting/registering atomically ensures cancel cannot miss a new child.
            linux.startSignInProcess(run.profileId, args, foreground).also {
                run.processes.add(it)
                if (foreground) { run.loginProcess = it; run.readerStarted = true }
            }
        }

    private fun stop(run: Run, process: Process) {
        linux.stopAgentProcess(process)
        synchronized(lock) { run.processes.remove(process) }
    }

    /** Captured output is native-only and bounded; metadata never leaves here. */
    private fun capture(run: Run, args: List<String>): Pair<Int, String> {
        val process = launch(run, args, false)
        val output = CompletableFuture<String>()
        try {
            Thread({
                try {
                    val buffer = StringBuilder()
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val chunk = CharArray(2048)
                        while (true) {
                            val count = reader.read(chunk)
                            if (count < 0) break
                            if (buffer.length + count > 16384) throw AuthFailure("invalidResponse")
                            buffer.append(chunk, 0, count)
                        }
                    }
                    output.complete(buffer.toString())
                    buffer.setLength(0)
                } catch (_: Throwable) {
                    output.completeExceptionally(AuthFailure("invalidResponse"))
                }
            }, "phone-agent-auth-status").apply { isDaemon = true; start() }
            if (!process.waitFor(15, TimeUnit.SECONDS)) throw AuthFailure("hostUnavailable")
            return Pair(process.exitValue(), output.get(2, TimeUnit.SECONDS))
        } finally {
            // Drain/verify the tracked process and its captured descendants too.
            stop(run, process)
        }
    }

    private fun inspect(run: Run): Boolean {
        if (!synchronized(lock) { run.versionVerified }) {
            val version = capture(run, listOf("claude", "--version"))
            if (version.first != 0 || version.second.trim() != "2.1.283 (Claude Code)") {
                throw AuthFailure("unavailable")
            }
            synchronized(lock) { run.versionVerified = true }
        }
        val captured = capture(run, listOf("claude", "auth", "status", "--json"))
        val data = JSONObject(captured.second)
        if (!data.has("loggedIn") || data.opt("loggedIn") !is Boolean ||
            data.opt("authMethod") !is String || data.opt("apiProvider") !is String) {
            throw AuthFailure("invalidResponse")
        }
        if (captured.first !in listOf(0, 1)) throw AuthFailure("hostUnavailable")
        return data.opt("loggedIn") == true &&
            data.optString("authMethod") == "claude.ai" &&
            data.optString("apiProvider") == "firstParty" &&
            (!data.has("apiKeySource") || data.isNull("apiKeySource")) && captured.first == 0
    }

    fun status(profileId: String, agentId: String, runId: String, method: String): Map<String, Any?> {
        return try {
            val run = identity(profileId, agentId, runId, method, true)
            val signedIn = inspect(run)
            synchronized(lock) {
                if (!run.cancelled && signedIn) {
                    run.phase = "signedIn"
                    run.failure = null
                    run.url = null
                    run.acceptsCode = false
                } else if (!run.cancelled && (!run.loginRequested || run.phase == "signedIn")) {
                    run.phase = "signedOut"
                    run.failure = null
                }
            }
            snapshot(run)
        } catch (error: AuthFailure) {
            failure(runId, error.kind)
        } catch (_: Throwable) {
            failure(runId, "hostUnavailable")
        }
    }

    fun start(profileId: String, agentId: String, runId: String, method: String): Map<String, Any?> {
        return try {
            val run = identity(profileId, agentId, runId, method, true)
            synchronized(lock) {
                if (run.loginRequested) throw AuthFailure("staleRun")
                run.loginRequested = true
            }
            if (inspect(run)) {
                synchronized(lock) {
                    if (!run.cancelled) { run.phase = "signedIn"; run.failure = null }
                }
                return snapshot(run)
            }
            val process = launch(run, listOf("claude", "auth", "login", "--claudeai"), true)
            readLogin(run, process)
            if (!run.challengeReady.await(15, TimeUnit.SECONDS)) {
                fail(run, "hostUnavailable")
                stop(run, process)
            }
            snapshot(run)
        } catch (error: AuthFailure) {
            failure(runId, error.kind)
        } catch (_: Throwable) {
            failure(runId, "hostUnavailable")
        }
    }

    private fun validatedUrl(value: String): String? = try {
        val uri = URI(value)
        if (value.length <= 8192 && !Regex("[\\x00-\\x20\\x7f\\\\]").containsMatchIn(value) &&
            uri.scheme == "https" && uri.host == "claude.com" && uri.port == -1 &&
            uri.rawUserInfo == null && uri.rawFragment == null &&
            uri.rawPath == "/cai/oauth/authorize") value else null
    } catch (_: Throwable) { null }

    private fun readLogin(run: Run, process: Process) {
        try {
            Thread({
                // Only a transient, bounded parser window. No stored transcript.
                val window = StringBuilder()
                try {
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val chunk = CharArray(2048)
                        while (true) {
                            val count = reader.read(chunk)
                            if (count < 0) break
                            if (synchronized(lock) { run.cancelled }) break
                            window.append(chunk, 0, count)
                            if (window.length > 32768) throw AuthFailure("invalidResponse")
                            val plain = window.toString()
                                .replace(Regex("\\u001b\\][^\\u0007\\u001b]*(?:\\u0007|\\u001b\\\\)"), "")
                                .replace(Regex("\\u001b\\[[0-?]*[ -/]*[@-~]"), "")
                            // Parse a complete printed URL line, never a partial read.
                            for (line in plain.split('\n').dropLast(1)) {
                                val marker = "If the browser didn't open, visit: "
                                if (line.startsWith(marker)) {
                                    val url = validatedUrl(line.removePrefix(marker).trim())
                                        ?: throw AuthFailure("invalidChallenge")
                                    synchronized(lock) {
                                        if (!run.cancelled && run.phase != "signedIn") {
                                            run.url = url; run.phase = "urlReady"; run.failure = null
                                            run.challengeReady.countDown()
                                        }
                                    }
                                }
                                // Claude answers on the prompt's own line ("… > Invalid code…").
                                if (line.contains("Invalid code. Please make sure the full code was copied.")) {
                                    // Claude keeps the login open and reads the next line: let
                                    // the person paste again into the same sign-in.
                                    synchronized(lock) {
                                        if (!run.cancelled && run.phase != "signedIn") {
                                            run.failure = "invalidCode"
                                            run.codeSubmitted = false
                                            run.acceptsCode = true
                                            run.phase = "awaitingCode"
                                        }
                                    }
                                }
                                if (line.contains("Login failed:")) {
                                    val kind = when {
                                        Regex("\\b429\\b|rate.?limit", RegexOption.IGNORE_CASE)
                                            .containsMatchIn(line) -> "limitReached"
                                        // 400 is Claude refusing an expired or used code.
                                        Regex("\\b40[013]\\b|invalid_grant", RegexOption.IGNORE_CASE)
                                            .containsMatchIn(line) -> "authenticationRejected"
                                        else -> "hostUnavailable"
                                    }
                                    throw AuthFailure(kind)
                                }
                            }
                            if (plain.contains("Paste code here if prompted > ")) synchronized(lock) {
                                if (!run.cancelled && run.url != null) run.acceptsCode = true
                            }
                            // Retain only the incomplete line. The sanitized URL is separate.
                            val newline = window.lastIndexOf("\n")
                            if (newline >= 0) window.delete(0, newline + 1)
                        }
                    }
                    if (!synchronized(lock) { run.cancelled }) {
                        if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0 && inspect(run)) {
                            synchronized(lock) {
                                if (!run.cancelled) {
                                    run.phase = "signedIn"; run.failure = null; run.url = null; run.acceptsCode = false
                                }
                            }
                            run.challengeReady.countDown()
                        } else fail(run, "authenticationRejected")
                    }
                } catch (error: AuthFailure) {
                    fail(run, error.kind)
                } catch (_: Throwable) {
                    fail(run, "hostUnavailable")
                } finally {
                    window.setLength(0)
                    try { stop(run, process) } catch (_: Throwable) { fail(run, "cancellationUnconfirmed") }
                    run.readerDone.countDown()
                }
            }, "phone-agent-auth-login").apply { isDaemon = true; start() }
        } catch (_: Throwable) {
            synchronized(lock) { run.readerStarted = false }
            run.readerDone.countDown()
            fail(run, "hostUnavailable")
            try { stop(run, process) } catch (_: Throwable) { fail(run, "cancellationUnconfirmed") }
            throw AuthFailure("hostUnavailable")
        }
    }

    fun challenge(profileId: String, agentId: String, runId: String, method: String): Map<String, Any?> {
        return try {
            val run = identity(profileId, agentId, runId, method, false)
            synchronized(lock) {
                if (run.phase == "urlReady" && run.acceptsCode) run.phase = "awaitingCode"
            }
            snapshot(run)
        } catch (error: AuthFailure) { failure(runId, error.kind) }
        catch (_: Throwable) { failure(runId, "hostUnavailable") }
    }

    fun submit(
        profileId: String, agentId: String, runId: String, method: String, code: String,
    ): Map<String, Any?> {
        return try {
            val run = identity(profileId, agentId, runId, method, false)
            val parts = code.split('#')
            if (code.length > 4096 || parts.size != 2 || parts.any { it.isEmpty() } ||
                code.startsWith("sk-") || Regex("[\\x00-\\x20\\x7f]").containsMatchIn(code)) {
                throw AuthFailure("invalidCode")
            }
            // Already signed in (an earlier paste went through): say so instead of
            // calling a finished login stale.
            if (synchronized(lock) { run.phase == "signedIn" }) return snapshot(run)
            // A code is already with Claude: wait for its verdict, don't send twice.
            if (synchronized(lock) { run.codeSubmitted && !run.acceptsCode && !run.cancelled }) {
                val pending = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
                while (synchronized(lock) {
                        !run.cancelled && run.phase == "awaitingCode" && !run.acceptsCode && run.failure == null
                    } && System.nanoTime() < pending) {
                    Thread.sleep(150)
                }
                return snapshot(run)
            }
            // The person can come back from the browser before Claude printed its
            // prompt: wait for it briefly instead of refusing the code.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (synchronized(lock) { !run.acceptsCode && !run.cancelled && run.phase == "urlReady" } &&
                System.nanoTime() < deadline) {
                Thread.sleep(100)
            }
            synchronized(lock) {
                if ((run.phase != "awaitingCode" && run.phase != "urlReady") ||
                    !run.acceptsCode || run.codeSubmitted || run.cancelled) {
                    throw AuthFailure("staleRun")
                }
                run.phase = "awaitingCode"
                run.failure = null
                val process = run.loginProcess?.takeIf { it.isAlive }
                    ?: throw AuthFailure("hostUnavailable")
                run.codeSubmitted = true
                // Direct pipe only. Never interpolate into command text or emit an event.
                process.outputStream.write((code + "\n").toByteArray(Charsets.UTF_8))
                process.outputStream.flush()
                run.acceptsCode = false
            }
            // Answer with the outcome, not "sent": Claude either signs in and exits,
            // refuses the code (and asks again), or the login fails.
            val outcome = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
            while (synchronized(lock) {
                    !run.cancelled && run.phase == "awaitingCode" && !run.acceptsCode && run.failure == null
                } && System.nanoTime() < outcome) {
                Thread.sleep(150)
            }
            snapshot(run)
        } catch (error: AuthFailure) { failure(runId, error.kind) }
        catch (_: Throwable) { failure(runId, "hostUnavailable") }
    }

    fun cancel(profileId: String, agentId: String, runId: String, method: String): Map<String, Any?> {
        var reader: CountDownLatch? = null
        val targets = synchronized(lock) {
            val run = runs[runId]
            if (run != null && (run.profileId != profileId || run.agentId != agentId || run.method != method)) {
                return mapOf("runId" to runId, "drained" to false)
            }
            if (!listOf(profileId, agentId, runId).all { Regex("^[a-zA-Z0-9_-]{1,128}$").matches(it) }) {
                return mapOf("runId" to runId, "drained" to false)
            }
            // Never evict tombstones and accidentally revive a cancelled run.
            if (tombstones.size >= 1024 && runId !in tombstones && profileId !in deletedProfiles) {
                return mapOf("runId" to runId, "drained" to false)
            }
            tombstones.add(runId)
            run?.cancelled = true
            run?.url = null
            run?.acceptsCode = false
            run?.challengeReady?.countDown()
            if (run?.readerStarted == true) reader = run.readerDone
            run?.processes?.toList() ?: emptyList()
        }
        var drained = true
        for (process in targets) {
            try { linux.stopAgentProcess(process) } catch (_: Throwable) { drained = false }
        }
        try {
            if (reader?.await(5, TimeUnit.SECONDS) == false) drained = false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            drained = false
        }
        if (drained) synchronized(lock) { runs.remove(runId) }
        return mapOf("runId" to runId, "drained" to drained)
    }

    /** Caller must complete this before erasing profile files or credentials. */
    fun deleteProfile(profileId: String) {
        val selected = synchronized(lock) {
            if (!Regex("^[a-zA-Z0-9_-]{1,128}$").matches(profileId)) {
                throw IllegalStateException("auth_invalid_profile")
            }
            // Prevent a racing fresh inspection/login from reopening the profile.
            deletedProfiles.add(profileId)
            runs.values.filter { it.profileId == profileId }.toList()
        }
        var drained = true
        for (run in selected) {
            if (cancel(run.profileId, run.agentId, run.runId, run.method)["drained"] != true) {
                drained = false
            }
        }
        if (!drained) throw IllegalStateException("auth_cancellation_unconfirmed")
    }
}
