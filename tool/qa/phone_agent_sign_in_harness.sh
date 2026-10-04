#!/usr/bin/env bash
# Standalone native auth contract checks. No Android, CLI auth, account or network.
# Coordinator: tool/qa/machine_lock.sh test -- bash tool/qa/phone_agent_sign_in_harness.sh
set -euo pipefail
auth_repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
auth_build=$(mktemp -d "${TMPDIR:-/tmp}/oc-auth-harness.XXXXXX")
trap 'rm -rf -- "$auth_build"' EXIT
auth_json_jar=${OC_AUTH_JSON_JAR:-}
if [[ -z "$auth_json_jar" ]]; then
  auth_json_cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.json/json"
  if [[ -d "$auth_json_cache" ]]; then
    auth_json_jar=$(rg --files "$auth_json_cache" | rg '/json-[^/]+\.jar$' | sort | head -n 1)
  fi
fi
if [[ -z "$auth_json_jar" || ! -f "$auth_json_jar" ]]; then
  echo 'Auth harness needs an existing org.json jar; set OC_AUTH_JSON_JAR.' >&2
  exit 66
fi
cat > "$auth_build/Harness.kt" <<'KOTLIN'
package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private const val PROFILE = "synthetic-profile"
private const val METHOD = "browserOAuthHost"
private const val URL = "https://claude.com/cai/oauth/authorize?state=synthetic-challenge"
private const val CODE = "synthetic-code#synthetic-state"
private const val PROMPT = "Paste code here if prompted > "

/** In-memory pipes, not a subprocess or terminal. Reader blocks until feed/EOF. */
class FakeInput : InputStream() {
    private val chunks = LinkedBlockingQueue<ByteArray>()
    private val end = ByteArray(0)
    private var current = ByteArray(0)
    private var offset = 0
    @Volatile private var ended = false
    fun feed(text: String) { if (!ended) chunks.put(text.toByteArray(Charsets.UTF_8)) }
    fun finish() { ended = true; chunks.offer(end) }
    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255
    }
    override fun read(target: ByteArray, start: Int, length: Int): Int {
        if (length == 0) return 0
        while (offset >= current.size) {
            if (ended && chunks.isEmpty()) return -1
            current = chunks.take(); offset = 0
            if (current === end) return -1
        }
        val count = minOf(length, current.size - offset)
        current.copyInto(target, start, offset, offset + count)
        offset += count
        return count
    }
    override fun available(): Int = current.size - offset + (chunks.peek()?.size ?: 0)
    override fun close() { finish() }
}

class FakeProcess(
    initial: String = "",
    exit: Int? = null,
    private val onCode: (() -> Unit)? = null,
    // Scripted answers, one per pasted line; null keeps the default success.
    private val answers: MutableList<String>? = null,
) : Process() {
    val pipe = FakeInput()
    val exited = CountDownLatch(1)
    @Volatile private var result: Int? = null
    private val written = ByteArrayOutputStream()
    var codeDeliveries = 0
        private set
    init { if (initial.isNotEmpty()) pipe.feed(initial); if (exit != null) finish(exit) }
    @Synchronized fun stdinText(): String = written.toString("UTF-8")
    @Synchronized fun finish(code: Int) {
        if (result != null) return
        result = code; pipe.finish(); exited.countDown()
    }
    override fun getInputStream(): InputStream = pipe
    override fun getErrorStream(): InputStream = InputStream.nullInputStream()
    override fun getOutputStream(): OutputStream = object : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(values: ByteArray, start: Int, length: Int) {
            synchronized(this@FakeProcess) {
                check(isAlive) { "private pipe closed" }
                written.write(values, start, length)
                if (values.copyOfRange(start, start + length).contains(10.toByte())) {
                    codeDeliveries++
                    val answer = answers?.removeFirstOrNull()
                    when {
                        answer == null || answer.startsWith("Login successful") -> {
                            onCode?.invoke()
                            pipe.feed("Login successful.\n")
                            finish(0)
                        }
                        answer.startsWith("Login failed") -> { pipe.feed(answer); finish(1) }
                        else -> pipe.feed(answer)
                    }
                }
            }
        }
    }
    override fun waitFor(): Int { exited.await(); return result!! }
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = exited.await(timeout, unit)
    override fun exitValue(): Int = result ?: throw IllegalThreadStateException("fake still running")
    override fun isAlive(): Boolean = result == null
    override fun destroy() { finish(137) }
    override fun destroyForcibly(): Process { destroy(); return this }
}

/** Matches only the two native helpers, recording safe argv and private stdin. */
class BuiltinLinux {
    data class Call(val profile: String, val argv: List<String>, val foreground: Boolean)
    val calls = Collections.synchronizedList(mutableListOf<Call>())
    val children = Collections.synchronizedList(mutableListOf<FakeProcess>())
    var version = "2.1.283 (Claude Code)"
    var managedKey = false
    @Volatile var signedIn = false
    @Volatile var failDrain = false
    var blockingVersion = false
    val versionStarted = CountDownLatch(1)
    val loginStarted = CountDownLatch(1)
    var loginText = "If the browser didn't open, visit: $URL\n$PROMPT"
    var loginFactory: (() -> FakeProcess)? = null
    @Volatile var login: FakeProcess? = null
    fun startAgentProcess(profileId: String, argv: List<String>, foreground: Boolean = false): Process {
        calls.add(Call(profileId, argv.toList(), foreground))
        val process = when (argv) {
            listOf("claude", "--version") -> {
                versionStarted.countDown()
                if (blockingVersion) FakeProcess() else FakeProcess("$version\n", 0)
            }
            listOf("claude", "auth", "status", "--json") -> {
                val ready = signedIn || managedKey
                val source = if (managedKey) ",\"apiKeySource\":\"/login managed key\"" else ""
                FakeProcess(
                    "{\"loggedIn\":$ready,\"authMethod\":\"${if (ready) "claude.ai" else "none"}\",\"apiProvider\":\"firstParty\",\"email\":\"synthetic-private-email\"$source}",
                    if (ready) 0 else 1,
                )
            }
            listOf("claude", "auth", "login", "--claudeai") -> {
                val child = loginFactory?.invoke() ?: FakeProcess(loginText, onCode = { signedIn = true })
                login = child; loginStarted.countDown(); child
            }
            else -> error("Unexpected auth argv")
        }
        children.add(process)
        return process
    }
    fun stopAgentProcess(process: Process) {
        process.destroy()
        check(process.waitFor(1, TimeUnit.SECONDS)) { "fake drain failed" }
        if (failDrain) error("synthetic drain unconfirmed")
    }
    fun callSnapshot(): List<Call> = synchronized(calls) { calls.toList() }
    fun assertNoLiveChildren() {
        check(synchronized(children) { children.none { it.isAlive } }) { "private child survived drain" }
    }
}

private fun awaiting(auth: PhoneAgentSignIn, run: String) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
    while (auth.challenge(PROFILE, "claude", run, METHOD)["phase"] != "awaitingCode") {
        check(System.nanoTime() < deadline) { "Challenge never became code-ready" }
        Thread.sleep(5)
    }
}
private fun phase(result: Map<String, Any?>, expected: String) {
    check(result["phase"] == expected) { "Unexpected sanitized phase" }
    check(result.keys.all { it in setOf("runId", "phase", "url", "failure", "resetAt") }) {
        "Private output field escaped"
    }
    check(result.values.none { it is String && it.contains("synthetic-private-email") }) {
        "Status metadata escaped"
    }
}
private fun start(auth: PhoneAgentSignIn, run: String) = auth.start(PROFILE, "claude", run, METHOD)
private fun cancel(auth: PhoneAgentSignIn, run: String) = auth.cancel(PROFILE, "claude", run, METHOD)
private fun status(auth: PhoneAgentSignIn, run: String) = auth.status(PROFILE, "claude", run, METHOD)

fun main() {
    var passed = 0
    fun scenario(name: String, body: () -> Unit) { body(); passed++; println("PASS $name") }
    scenario("private browser code flow and fresh inspection") {
        val host = BuiltinLinux(); val auth = PhoneAgentSignIn(host); val run = "flow"
        phase(status(auth, run), "signedOut")
        check(host.login == null)
        phase(start(auth, run), "urlReady")
        awaiting(auth, run)
        phase(auth.submit(PROFILE, "claude", run, METHOD, "sk-ant-synthetic"), "failed")
        check(host.login!!.codeDeliveries == 0)
        val accepted = auth.submit(PROFILE, "claude", run, METHOD, CODE)
        check(accepted["phase"] in setOf("awaitingCode", "signedIn"))
        phase(status(auth, run), "signedIn")
        check(host.login!!.stdinText() == CODE + "\n")
        check(host.callSnapshot().none { call -> call.argv.any { it.contains(CODE) } })
        check(host.callSnapshot().filter { it.foreground }.all { it.argv == listOf("claude", "auth", "login", "--claudeai") })
        check(host.login!!.codeDeliveries == 1)
        phase(auth.submit(PROFILE, "claude", run, METHOD, CODE), "failed")
        check(cancel(auth, run)["drained"] == true)
        host.assertNoLiveChildren()
    }
    scenario("signed-in status avoids opening a browser") {
        val host = BuiltinLinux().apply { signedIn = true }; val auth = PhoneAgentSignIn(host)
        phase(start(auth, "already-ready"), "signedIn")
        check(host.login == null)
        check(cancel(auth, "already-ready")["drained"] == true)
    }
    scenario("wrong version and managed Console key never prove subscription") {
        val wrong = BuiltinLinux().apply { version = "2.1.288 (Claude Code)" }
        val unavailable = status(PhoneAgentSignIn(wrong), "wrong-version")
        phase(unavailable, "failed"); check(unavailable["failure"] == "unavailable")
        check(wrong.calls.size == 1)
        val key = BuiltinLinux().apply { managedKey = true }
        phase(status(PhoneAgentSignIn(key), "managed-key"), "signedOut")
        check(key.login == null)
    }
    scenario("malformed and foreign authorization URLs are hidden") {
        for (value in listOf(
            "https://evil.test/cai/oauth/authorize",
            "https://claude.com.evil.test/cai/oauth/authorize",
            "https://claude.com@evil.test/cai/oauth/authorize",
            "https://claude.com:8443/cai/oauth/authorize",
            "https://claude.com/cai/oauth/authorize#fragment",
            "https://claude.com/unrelated",
        )) {
            val host = BuiltinLinux().apply { loginText = "If the browser didn't open, visit: $value\n$PROMPT" }
            val auth = PhoneAgentSignIn(host)
            val result = start(auth, "invalid-url")
            phase(result, "failed"); check(result["failure"] == "invalidChallenge")
            check("url" !in result)
            check(cancel(auth, "invalid-url")["drained"] == true)
            host.assertNoLiveChildren()
        }
    }
    scenario("split output and OSC hyperlinks preserve only complete vendor URL") {
        val host = BuiltinLinux()
        val prefix = "If the browser didn't open, visit: "
        host.loginFactory = { FakeProcess(prefix + URL.take(35)) }
        val auth = PhoneAgentSignIn(host)
        val result = CompletableFuture.supplyAsync { start(auth, "split") }
        check(host.loginStarted.await(2, TimeUnit.SECONDS))
        check(!result.isDone)
        check(auth.challenge(PROFILE, "claude", "split", METHOD)["url"] == null)
        host.login!!.pipe.feed(URL.drop(35) + "\n" + PROMPT)
        check(result.get(3, TimeUnit.SECONDS)["url"] == URL)
        awaiting(auth, "split"); check(cancel(auth, "split")["drained"] == true)
        val oscHost = BuiltinLinux().apply {
            loginText = prefix + "\u001b]8;;$URL\u001b\\$URL\u001b]8;;\u001b\\\n" + PROMPT
        }
        val osc = PhoneAgentSignIn(oscHost)
        check(start(osc, "osc")["url"] == URL)
        check(cancel(osc, "osc")["drained"] == true)
    }
    scenario("cancel races a pending start and tombstones the old ID") {
        val host = BuiltinLinux().apply { blockingVersion = true }; val auth = PhoneAgentSignIn(host)
        val starting = CompletableFuture.supplyAsync { start(auth, "racing") }
        check(host.versionStarted.await(2, TimeUnit.SECONDS))
        check(cancel(auth, "racing")["drained"] == true)
        val late = starting.get(3, TimeUnit.SECONDS)
        check("url" !in late); check(host.login == null)
        val stale = start(auth, "racing")
        phase(stale, "failed"); check(stale["failure"] == "staleRun")
        host.assertNoLiveChildren()
    }
    scenario("cancellation requires confirmed drain and respects profile identity") {
        val host = BuiltinLinux(); val auth = PhoneAgentSignIn(host)
        start(auth, "drain"); awaiting(auth, "drain")
        check(auth.cancel("other-profile", "claude", "drain", METHOD)["drained"] == false)
        check(host.login!!.isAlive)
        host.failDrain = true
        check(cancel(auth, "drain")["drained"] == false)
        host.failDrain = false
        check(cancel(auth, "drain")["drained"] == true)
        host.assertNoLiveChildren()
    }
    scenario("profile deletion drains old auth and blocks fresh identities") {
        val host = BuiltinLinux(); val auth = PhoneAgentSignIn(host)
        start(auth, "delete"); awaiting(auth, "delete")
        auth.deleteProfile(PROFILE)
        host.assertNoLiveChildren()
        val before = host.calls.size
        val refused = status(auth, "new-after-delete")
        phase(refused, "failed"); check(refused["failure"] == "staleRun")
        check(host.calls.size == before)
    }
    scenario("unknown agents remain unavailable without starting a process") {
        val host = BuiltinLinux(); val auth = PhoneAgentSignIn(host)
        val refused = auth.start(PROFILE, "gemini", "unknown", METHOD)
        phase(refused, "failed"); check(refused["failure"] == "unavailable")
        check(host.calls.isEmpty())
    }
    scenario("provider limit failure has no invented reset time") {
        val host = BuiltinLinux().apply { loginText = "Login failed: HTTP 429 rate limit\n" }
        val auth = PhoneAgentSignIn(host)
        val limited = start(auth, "limited")
        phase(limited, "limitReached")
        check(limited["resetAt"] == null)
        check("url" !in limited)
        check(cancel(auth, "limited")["drained"] == true)
        host.assertNoLiveChildren()
    }
    scenario("a refused paste keeps the login open for the next paste") {
        val host = BuiltinLinux()
        host.loginFactory = {
            FakeProcess(host.loginText, onCode = { host.signedIn = true },
                answers = mutableListOf("Invalid code. Please make sure the full code was copied.\n", "Login successful.\n"))
        }
        val auth = PhoneAgentSignIn(host); val run = "retry"
        phase(start(auth, run), "urlReady")
        val refused = auth.submit(PROFILE, "claude", run, METHOD, CODE)
        phase(refused, "awaitingCode")
        check(refused["failure"] == "invalidCode") { "Refusal not reported" }
        val second = auth.submit(PROFILE, "claude", run, METHOD, CODE)
        phase(second, "signedIn")
        check(host.login!!.codeDeliveries == 2)
        check(cancel(auth, run)["drained"] == true)
        host.assertNoLiveChildren()
    }
    scenario("a code sent before the prompt waits for it") {
        val host = BuiltinLinux()
        host.loginText = "If the browser didn't open, visit: $URL\n"
        val auth = PhoneAgentSignIn(host); val run = "early"
        phase(start(auth, run), "urlReady")
        Thread { Thread.sleep(300); host.login!!.pipe.feed(PROMPT) }.start()
        phase(auth.submit(PROFILE, "claude", run, METHOD, CODE), "signedIn")
        check(host.login!!.codeDeliveries == 1)
        check(cancel(auth, run)["drained"] == true)
    }
    scenario("an expired code is rejected, not a host failure") {
        val host = BuiltinLinux()
        host.loginFactory = {
            FakeProcess(host.loginText, answers = mutableListOf("Login failed: Request failed with status code 400\n"))
        }
        val auth = PhoneAgentSignIn(host); val run = "expired"
        phase(start(auth, run), "urlReady")
        val result = auth.submit(PROFILE, "claude", run, METHOD, CODE)
        phase(result, "failed")
        check(result["failure"] == "authenticationRejected") { "400 not mapped to a rejected code" }
        check(cancel(auth, run)["drained"] == true)
    }
    println("Phone agent sign-in: $passed fake-process scenarios passed; no Android/authentication claim")
}
KOTLIN
kotlinc -J-Xmx512m -jvm-target 17 -cp "$auth_json_jar" \
  "$auth_repo/android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentSignIn.kt" \
  "$auth_build/Harness.kt" -include-runtime -d "$auth_build/auth-harness.jar"
timeout 60s java -Xmx256m -cp "$auth_build/auth-harness.jar:$auth_json_jar" \
  io.github.eslamasabry.opencode_mobile.HarnessKt
