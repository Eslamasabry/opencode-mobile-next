package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val PROFILE = "synthetic-profile"
private const val PRIVATE = "synthetic-private-sentinel"

private class AuthProcess(
    output: String,
    private val code: Int = 0,
    private val brokenReader: Boolean = false,
    private val outputRead: CountDownLatch? = null,
) : Process() {
    var alive = true
    var stdinClosed = false
    private val bytes = output.toByteArray(Charsets.UTF_8)
    override fun getInputStream(): InputStream = if (brokenReader) {
        object : InputStream() {
            override fun read(): Int = throw IOException(PRIVATE)
        }
    } else object : ByteArrayInputStream(bytes) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count < 0) outputRead?.countDown()
            return count
        }
    }
    override fun getErrorStream(): InputStream = ByteArrayInputStream(PRIVATE.toByteArray())
    override fun getOutputStream(): OutputStream = object : ByteArrayOutputStream() {
        override fun close() { stdinClosed = true; super.close() }
    }
    override fun waitFor(): Int { alive = false; return code }
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean { alive = false; return true }
    override fun exitValue(): Int = if (alive) throw IllegalThreadStateException() else code
    override fun isAlive(): Boolean = alive
    override fun destroy() { alive = false }
    override fun destroyForcibly(): Process { destroy(); return this }
}

private fun request(
    fixtures: File,
    agent: String = "claude",
    action: String = "probe",
    profile: String = PROFILE,
): Map<String, Any?> {
    val script = File(fixtures, "$agent-$action.txt").readText()
        .replace("/home/oc/.oc-profiles/$PROFILE", "/home/oc/.oc-profiles/$profile")
    return mapOf("profileId" to profile, "agentId" to agent, "action" to action,
        "script" to script, "timeoutSeconds" to if (action == "probe") 10 else 20)
}

private fun failure(result: Map<String, Any?>, code: String) {
    check(result == mapOf("state" to "error", "error" to code)) { "Wrong fixed failure projection" }
    check(result.values.none { it.toString().contains(PRIVATE) }) { "Private output escaped" }
}

private fun processTreeScenario(scenario: String) {
    val child = AuthProcess("{}")
    var now = 0L
    val signals = mutableListOf<Pair<Int, Int>>()
    val root = PhoneAgentAuthProcessIdentity(110, 1, "root-start", 'S')
    val leaf = PhoneAgentAuthProcessIdentity(120, 110, "child-start", 'S')
    val unrelated = PhoneAgentAuthProcessIdentity(130, 1, "unrelated-start", 'S')
    var records = when (scenario) {
        "tree-root-only-then-orphan" -> listOf(root, unrelated)
        "tree-failed-start-unknown" -> listOf(leaf.copy(parent = 1), unrelated)
        else -> listOf(root, leaf, unrelated)
    }
    var unreadable = false
    var calls = 0
    val tree = PhoneAgentAuthProcessTree(
        pid = { if (scenario == "tree-missing-identity") null else 110 },
        inventory = {
            calls++
            check(calls < 1000) { "Drain exceeded its deterministic work budget" }
            if (unreadable) throw IOException(PRIVATE)
            records
        },
        signal = { pid, signal ->
            check(pid != unrelated.pid) { "Unrelated process was signalled" }
            check(signal == 19 || signal == 9) { "Unexpected process signal" }
            signals.add(pid to signal)
            if (scenario == "tree-signal-reuse" && pid == root.pid && signal == 19) {
                records = records.map { if (it.pid == leaf.pid) it.copy(start = "reused-start") else it }
            }
            if (signal == 9 && scenario != "tree-unconfirmed-drain") {
                records = records.filter { it.pid != pid }
            }
        },
        nano = { now },
        pause = { millis -> check(millis > 0); now += TimeUnit.MILLISECONDS.toNanos(millis) },
    )
    val tracked = if (scenario == "tree-failed-start-unknown") {
        tree.track(child, setOf(unrelated.pid to unrelated.start))
    } else tree.track(child)
    when (scenario) {
        "tree-failed-start-unknown" -> {
            check(!tracked)
            child.alive = false
            check(!tree.stop(child)) { "Failed startup forgot its unowned orphan delta" }
            check(signals.none { it.first == leaf.pid }) { "Unconfirmed startup child was signalled" }
            records = listOf(unrelated)
            check(tree.stop(child)) { "Failed-start baseline was not retained for later drainage" }
        }
        "tree-root-only-then-orphan" -> {
            records = listOf(leaf.copy(parent = 1), unrelated)
            child.alive = false
            check(!tree.stop(child)) { "Unattributed new task after root exit was declared drained" }
            check(signals.none { it.first == leaf.pid }) { "Unknown reparented task was signalled" }
        }
        "tree-signal-reuse" -> {
            tree.stop(child)
            check(signals.any { it == (root.pid to 19) })
            check(signals.none { it.first == leaf.pid }) { "PID reused between signals received an old-owner signal" }
        }
        "tree-dead-root" -> {
            records = listOf(leaf, unrelated)
            child.alive = false
            check(tree.stop(child)) { "Dead root prevented exact child drainage" }
            check(signals.contains(leaf.pid to 19) && signals.contains(leaf.pid to 9))
            check(signals.none { it.first == root.pid })
            check(records == listOf(unrelated))
        }
        "tree-pid-reuse" -> {
            records = listOf(leaf.copy(start = "reused-start"), unrelated)
            child.alive = false
            check(!tree.stop(child)) { "New reused PID identity was incorrectly certified unrelated" }
            check(signals.isEmpty()) { "Reused PID received a signal for the old process" }
            check(records.size == 2)
        }
        "tree-zombie" -> {
            records = listOf(root.copy(state = 'Z'), leaf.copy(state = 'Z'), unrelated)
            child.alive = false
            check(tree.stop(child)) { "Zombie was mistaken for a running owned child" }
            check(signals.isEmpty())
        }
        "tree-unconfirmed-drain" -> {
            records = listOf(leaf, unrelated)
            child.alive = false
            check(!tree.stop(child)) { "Surviving exact child was declared drained" }
            check(now in 1..TimeUnit.SECONDS.toNanos(2)) { "Drain exceeded the two-second deadline" }
            check(signals.contains(leaf.pid to 19) && signals.contains(leaf.pid to 9))
            check(signals.none { it.first == root.pid })
            records = listOf(unrelated)
            check(tree.stop(child)) { "Retained tree could not confirm its later drainage" }
        }
        "tree-missing-identity" -> {
            check(!tree.stop(child)) { "Unknown root identity was declared drained" }
            check(signals.isEmpty())
        }
        "tree-inventory-failure" -> {
            unreadable = true
            check(!tree.stop(child)) { "Unobservable tree was declared drained" }
            check(signals.isEmpty())
            unreadable = false
            records = listOf(leaf, unrelated)
            child.alive = false
            check(tree.stop(child)) { "Inventory error discarded owned tree identities" }
            check(signals.contains(leaf.pid to 9))
        }
        else -> error("Unknown authored tree scenario")
    }
    check(signals.none { it.first == unrelated.pid })
}

private fun outsideTreeScenario(scenario: String) {
    val process = AuthProcess("{}")
    val root = PhoneAgentAuthProcessIdentity(110, 1, "private-root", 'S')
    val leaf = PhoneAgentAuthProcessIdentity(120, 110, "private-child", 'S')
    val helper = PhoneAgentAuthProcessIdentity(300, 1, "helper-root", 'S')
    val helperChild = PhoneAgentAuthProcessIdentity(310, 300, "helper-child", 'S')
    val unknown = PhoneAgentAuthProcessIdentity(320, 1, "unknown-child", 'S')
    var records = listOf(root, leaf)
    var registered = setOf(helper.pid to helper.start)
    var registryReadable = true
    var now = 0L
    val signals = mutableListOf<Pair<Int, Int>>()
    val tree = PhoneAgentAuthProcessTree(
        pid = { root.pid }, inventory = { records },
        signal = { pid, signal ->
            check(pid == root.pid || pid == leaf.pid) { "Independent helper or unknown task was signalled" }
            signals.add(pid to signal)
            if (signal == 9) records = records.filter { it.pid != pid }
        },
        nano = { now }, pause = { now += TimeUnit.MILLISECONDS.toNanos(it) },
        outsideOwners = {
            if (!registryReadable) throw IOException(PRIVATE)
            registered
        },
    )
    check(tree.track(process))
    records += listOf(helper, helperChild)
    when (scenario) {
        "outside-helper-after-baseline" -> {
            check(tree.stop(process)) { "Registered helper falsely retired a completed private auth process" }
            check(records == listOf(helper, helperChild))
        }
        "outside-unknown-orphan" -> {
            records += unknown
            check(!tree.stop(process)) { "Unknown orphan was mistaken for a registered helper child" }
        }
        "outside-wrong-root-cookie" -> {
            registered = setOf(helper.pid to "wrong-cookie")
            check(!tree.stop(process)) { "Unmatched helper identity excluded unrelated new tasks" }
        }
        "outside-reused-root-cookie" -> {
            records = records.map { if (it.pid == helper.pid) it.copy(start = "reused-cookie") else it }
            check(!tree.stop(process)) { "Reused helper PID excluded a new process tree" }
        }
        "outside-private-identities-win" -> {
            registered += setOf(root.pid to root.start, leaf.pid to leaf.start)
            check(tree.stop(process)) { "Outside callback claimed captured private identities" }
            check(signals.contains(root.pid to 9) && signals.contains(leaf.pid to 9))
            check(records == listOf(helper, helperChild))
        }
        "outside-retained-helper-child", "outside-reused-helper-child" -> {
            tree.capture(process)
            records = records.filter { it.pid != helper.pid }.map {
                if (it.pid == helperChild.pid) it.copy(parent = 1,
                    start = if (scenario == "outside-reused-helper-child") "reused-child" else it.start) else it
            }
            registered = emptySet()
            if (scenario == "outside-retained-helper-child") {
                check(tree.stop(process)) { "Observed helper descendant lost attribution when its parent exited" }
                check(records == listOf(helperChild.copy(parent = 1)))
            } else {
                check(!tree.stop(process)) { "Reused helper descendant PID kept another process's attribution" }
            }
        }
        "outside-registry-failure" -> {
            records = listOf(root, leaf)
            registryReadable = false
            check(!tree.stop(process)) { "Unobservable outside ownership certified private drainage" }
        }
        else -> error("Unknown authored outside-owner scenario")
    }
    check(signals.none { it.first == helper.pid || it.first == helperChild.pid || it.first == unknown.pid })
}

private fun privateWithHelper(valid: Map<String, Any?>) {
    val helper = PhoneAgentAuthProcessIdentity(300, 1, "helper-root", 'S')
    val helperChild = PhoneAgentAuthProcessIdentity(310, 300, "helper-child", 'S')
    var records = emptyList<PhoneAgentAuthProcessIdentity>()
    var starts = 0
    var now = 0L
    val tree = PhoneAgentAuthProcessTree(pid = { 110 }, inventory = { records }, signal = { pid, signal ->
        check(pid == 110 || pid == 120) { "Private auth signalled the live independent helper" }
        if (signal == 9) records = records.filter { it.pid != pid }
    }, nano = { now }, pause = { now += TimeUnit.MILLISECONDS.toNanos(it) },
        outsideOwners = { setOf(helper.pid to helper.start) })
    val probe = PhoneAgentAuthProbe(start = { _, _ ->
        starts++
        val process = AuthProcess("{\"state\":\"signedIn\"}")
        records += listOf(PhoneAgentAuthProcessIdentity(110, 1, "auth-root-$starts", 'S'),
            PhoneAgentAuthProcessIdentity(120, 110, "auth-child-$starts", 'S'))
        check(tree.track(process))
        process
    }, waitFor = { process, seconds ->
        if (records.none { it.pid == helper.pid }) records += listOf(helper, helperChild)
        val finished = process.waitFor(seconds, TimeUnit.SECONDS)
        records = records.filter { it.pid != 110 }.map { if (it.pid == 120) it.copy(parent = 1) else it }
        finished
    }, stop = { tree.stop(it) })
    check(probe.run(valid) == mapOf("state" to "signedIn")) {
        "Concurrent owned helper replaced a signed-in auth result with a host failure"
    }
    check(probe.run(valid) == mapOf("state" to "signedIn") && starts == 2) {
        "Completed auth was retained and blocked a later request while the helper ran"
    }
    check(records == listOf(helper, helperChild))
}

private fun inventoryScenario(scenario: String) {
    val root = Files.createTempDirectory("bb8-private-inventory-").toFile()
    val directory = File(root, "110").apply { check(mkdir()) }
    var reads = 0
    val fields = listOf("S", "1") + List(17) { "0" } + listOf("10101")
    val inventory = PhoneAgentAuthInventory(
        directories = { listOf(directory) },
        sameUid = { scenario != "inventory-cross-uid" },
        read = {
            reads++
            when (scenario) {
                "inventory-unreadable", "inventory-cross-uid" -> throw IOException(PRIVATE)
                "inventory-vanished" -> { check(directory.delete()); throw IOException(PRIVATE) }
                "inventory-malformed" -> "malformed private stat"
                else -> "110 (synthetic (name)) ${fields.joinToString(" ")}"
            }
        },
    )
    try {
        when (scenario) {
            "inventory-valid" -> check(inventory.snapshot() ==
                listOf(PhoneAgentAuthProcessIdentity(110, 1, "10101", 'S')))
            "inventory-unreadable", "inventory-malformed" -> {
                check(runCatching { inventory.snapshot() }.isFailure) {
                    "Existing same-UID task with unobservable identity was silently omitted"
                }
                check(directory.exists())
            }
            "inventory-vanished" -> {
                check(inventory.snapshot().isEmpty())
                check(!directory.exists())
            }
            "inventory-cross-uid" -> {
                check(inventory.snapshot().isEmpty())
                check(reads == 0) { "Another UID's process identity was read" }
            }
            else -> error("Unknown authored inventory scenario")
        }
    } finally { root.deleteRecursively() }
}

private fun lockScenario(scenario: String) {
    val root = Files.createTempDirectory("bb8-private-lock-").toFile()
    val config = File(root, "linux/ubuntu/home/oc/.oc-profiles/$PROFILE/claude")
        .apply { check(mkdirs()) }
    val lock = File(config, ".oauth_refresh.lock")
    val account = File(config, "synthetic-account.json").apply { writeText(PRIVATE) }
    try {
        when (scenario) {
            "lock-empty-exact" -> {
                check(lock.mkdir())
                check(PhoneAgentAuthLock.clear(root, PROFILE))
                check(!lock.exists()) { "Exact empty stale lock was retained" }
            }
            "lock-nonempty-retained" -> {
                check(lock.mkdir())
                val contents = File(lock, "synthetic-private-state").apply { writeText(PRIVATE) }
                check(!PhoneAgentAuthLock.clear(root, PROFILE))
                check(lock.isDirectory && contents.readText() == PRIVATE) {
                    "Nonempty lock contents were deleted"
                }
            }
            "lock-foreign-symlink" -> {
                val foreign = File(root, "linux/ubuntu/home/oc/.oc-profiles/another-profile/claude")
                    .apply { check(mkdirs()) }
                val target = File(foreign, "empty-directory").apply { check(mkdir()) }
                val foreignAccount = File(foreign, "synthetic-account.json").apply { writeText(PRIVATE) }
                Files.createSymbolicLink(lock.toPath(), target.toPath())
                check(!runCatching { PhoneAgentAuthLock.clear(root, PROFILE) }.getOrDefault(false))
                check(Files.isSymbolicLink(lock.toPath()) && target.isDirectory) {
                    "Foreign lock symlink or target was deleted"
                }
                check(foreignAccount.readText() == PRIVATE)
            }
            else -> error("Unknown authored lock scenario")
        }
        check(config.isDirectory && account.readText() == PRIVATE) { "Account data was modified" }
    } finally { root.deleteRecursively() }
}

fun main(args: Array<String>) {
    val scenario = args[0]
    val fixtures = File(args[1])
    val valid = request(fixtures)
    when (scenario) {
        "outside-helper-after-baseline", "outside-unknown-orphan", "outside-wrong-root-cookie",
        "outside-reused-root-cookie", "outside-private-identities-win", "outside-retained-helper-child",
        "outside-reused-helper-child", "outside-registry-failure" -> outsideTreeScenario(scenario)
        "private-success-helper-concurrent" -> privateWithHelper(valid)
        "lock-empty-exact", "lock-nonempty-retained", "lock-foreign-symlink" -> lockScenario(scenario)
        "inventory-valid", "inventory-unreadable", "inventory-malformed", "inventory-vanished",
        "inventory-cross-uid" -> inventoryScenario(scenario)
        "deadline-absolute-probe" -> {
            var now = 0L
            val deadline = PhoneAgentAuthDeadline(10) { now }
            check(deadline.executionMillis() == 7600L && deadline.drainNanos() == 2_000_000_000L)
            now = 2_000_000_000L
            check(deadline.executionMillis() == 5600L) { "Launch time restarted the full execution deadline" }
            now = 9_500_000_000L
            check(deadline.executionMillis() == 0L && deadline.drainNanos() == 100_000_000L)
            now = 10_000_000_000L
            check(deadline.executionMillis() == 0L && deadline.drainNanos() == 0L)
        }
        "deadline-absolute-logout" -> {
            var now = 0L
            val deadline = PhoneAgentAuthDeadline(20) { now }
            check(deadline.executionMillis() == 17600L && deadline.drainNanos() == 2_000_000_000L)
            now = 20_000_000_000L
            check(deadline.executionMillis() == 0L && deadline.drainNanos() == 0L)
        }
        "cold-registered-owner" -> {
            check(PhoneAgentAuthColdOwner.permitsLockCleanup(setOf(300), 110) { setOf(110, 300) })
            check(PhoneAgentAuthColdOwner.permitsLockCleanup(emptySet(), 110) { setOf(110) })
        }
        "cold-unknown-owner" -> {
            check(!PhoneAgentAuthColdOwner.permitsLockCleanup(emptySet(), 110) { setOf(110, 320) }) {
                "Empty fresh-process map admitted a cold unregistered agent owner"
            }
            check(!PhoneAgentAuthColdOwner.permitsLockCleanup(setOf(300), 110) { setOf(110, 300, 320) })
            check(!PhoneAgentAuthColdOwner.permitsLockCleanup(setOf(300), 110) { throw IOException(PRIVATE) }) {
                "Unobservable same-UID ownership admitted login-lock cleanup"
            }
        }
        "tree-dead-root", "tree-pid-reuse", "tree-zombie", "tree-unconfirmed-drain",
        "tree-missing-identity", "tree-inventory-failure", "tree-root-only-then-orphan",
        "tree-signal-reuse", "tree-failed-start-unknown" -> processTreeScenario(scenario)
        "request-admission" -> {
            val parsed = checkNotNull(PhoneAgentAuthRequest.parse(valid))
            check(parsed.profileId == PROFILE && parsed.agentId == "claude" && parsed.action == "probe")
            check(parsed.timeoutSeconds == 10L && parsed.script == valid["script"])
            for (agent in listOf("claude", "codex", "gemini", "qwen", "goose", "omp-acp", "fx")) {
                check(PhoneAgentAuthRequest.parse(request(fixtures, agent)) != null)
            }
            for (agent in listOf("claude", "fx")) {
                val logout = checkNotNull(PhoneAgentAuthRequest.parse(request(fixtures, agent, "logout")))
                check(logout.timeoutSeconds == 20L && logout.action == "logout")
            }
        }
        "request-rejection" -> {
            val invalid = listOf(
                valid - "script", valid + ("unknown" to PRIVATE),
                valid + ("profileId" to "../escape"), valid + ("profileId" to ""),
                valid + ("profileId" to "a".repeat(81)), valid + ("profileId" to 5),
                valid + ("agentId" to "opencode"), valid + ("agentId" to "opencode2"),
                valid + ("agentId" to "unknown"), valid + ("action" to "delete"),
                valid + ("timeoutSeconds" to 20), valid + ("timeoutSeconds" to 10.0),
                valid + ("timeoutSeconds" to "10"), valid + ("script" to "echo '$PRIVATE'"),
                valid + ("script" to "a".repeat(65537)),
                valid + ("script" to (valid["script"] as String).replace("signedIn", PRIVATE)),
                valid + ("script" to ((valid["script"] as String) + "\necho '$PRIVATE'")),
                valid + ("script" to (valid["script"] as String).replace(PROFILE, "another-profile")),
                request(fixtures, "fx", "logout") + ("timeoutSeconds" to 10),
                request(fixtures, "codex") + ("action" to "logout") + ("timeoutSeconds" to 20),
            )
            for (item in invalid) check(PhoneAgentAuthRequest.parse(item) == null) { "Invalid request admitted" }
        }
        "projection-valid" -> {
            check(PhoneAgentAuthProjection.parse("{\"state\":\"signedIn\"}".toByteArray(), 0) ==
                mapOf("state" to "signedIn"))
            check(PhoneAgentAuthProjection.parse(
                "{\"state\":\"signedIn\",\"accountDisplayName\":\"synthetic-account\"}".toByteArray(), 0) ==
                mapOf("state" to "signedIn", "accountDisplayName" to "synthetic-account"))
            val emoji = "\uD83D\uDE00".repeat(80)
            check(PhoneAgentAuthProjection.parse(
                "{\"state\":\"signedIn\",\"accountDisplayName\":\"$emoji\"}".toByteArray(), 0) ==
                mapOf("state" to "signedIn", "accountDisplayName" to emoji)) {
                "Valid 160-unit account label was rejected"
            }
            check(PhoneAgentAuthProjection.parse("{\"state\":\"signedOut\"}".toByteArray(), 0) ==
                mapOf("state" to "signedOut"))
            for (code in listOf("probeUnsupported", "invalidResponse", "timedOut", "hostUnavailable",
                "notInstalled", "invalidContext", "signInExpired", "signOutFailed")) {
                failure(PhoneAgentAuthProjection.parse("{\"state\":\"error\",\"error\":\"$code\"}".toByteArray(), 0), code)
                failure(PhoneAgentAuthProjection.error(code), code)
            }
        }
        "projection-rejection" -> {
            val invalid = listOf("", "null", "[]", "{}", "not-json", "{\"state\":true}",
                "{\"state\":\"loading\"}", "{\"state\":\"signedIn\",\"unknown\":\"$PRIVATE\"}",
                "{\"state\":\"signedIn\",\"error\":\"timedOut\"}",
                "{\"state\":\"signedOut\",\"accountDisplayName\":\"$PRIVATE\"}",
                "{\"state\":\"signedOut\",\"error\":\"timedOut\"}",
                "{\"state\":\"error\",\"error\":\"$PRIVATE\"}",
                "{\"state\":\"error\",\"error\":\"timedOut\",\"accountDisplayName\":\"$PRIVATE\"}",
                "{\"state\":\"signedIn\",\"accountDisplayName\":null}",
                "{\"state\":\"signedIn\",\"accountDisplayName\":7}",
                "{\"state\":\"signedIn\",\"accountDisplayName\":\"\"}",
                "{\"state\":\"signedIn\",\"accountDisplayName\":\"   \"}",
                "{\"state\":\"signedIn\",\"accountDisplayName\":\"bad\\nlabel\"}",
                "{\"state\":\"signedIn\",\"accountDisplayName\":\"\\ud800\"}",
                "{\"state\":\"signedIn\",\"accountDisplayName\":\"\\udc00\"}",
                "{\"state\":\"signedIn\",\"accountDisplayName\":\"${"\uD83D\uDE00".repeat(81)}\"}",
                "{\"state\":\"signedIn\",\"accountDisplayName\":\"${"a".repeat(161)}\"}",
                "{\"state\":\"signedOut\",\"state\":\"signedIn\"}",
                "{\"state\":\"signedIn\"} trailing", "{\"state\":\"signedIn\"}{\"state\":\"signedOut\"}")
            for (output in invalid) failure(PhoneAgentAuthProjection.parse(output.toByteArray(), 0), "invalidResponse")
            failure(PhoneAgentAuthProjection.parse(ByteArray(65537) { 32 }, 0), "invalidResponse")
            failure(PhoneAgentAuthProjection.parse("{\"state\":\"signedIn\"}".toByteArray(), 7), "invalidResponse")
            failure(PhoneAgentAuthProjection.parse(byteArrayOf(0xc3.toByte(), 0x28), 0), "invalidResponse")
            failure(PhoneAgentAuthProjection.error(PRIVATE), "invalidResponse")
        }
        "private-success" -> {
            val child = AuthProcess("{\"state\":\"signedIn\"}")
            val seen = mutableListOf<Process>()
            var prepared = false
            val probe = PhoneAgentAuthProbe(start = { profile, argv ->
                check(prepared && profile == PROFILE)
                check(argv == listOf("/bin/sh", "-c", valid["script"] as String))
                child
            }, stop = { seen.add(it); it.destroy(); true }, prepare = { profile, agent ->
                check(profile == PROFILE && agent == "claude"); prepared = true; true
            })
            check(probe.run(valid) == mapOf("state" to "signedIn"))
            check(child.stdinClosed && !child.isAlive && seen.all { it === child })
        }
        "invalid-no-launch" -> {
            var launches = 0
            val probe = PhoneAgentAuthProbe(start = { _, _ -> launches++; error(PRIVATE) }, stop = { true })
            failure(probe.run(valid + ("action" to "delete")), "invalidContext")
            check(launches == 0)
        }
        "prepare-denied" -> {
            var launches = 0
            val probe = PhoneAgentAuthProbe(start = { _, _ -> launches++; error(PRIVATE) }, stop = { true },
                prepare = { _, _ -> false })
            failure(probe.run(valid), "hostUnavailable")
            check(launches == 0)
        }
        "start-failure" -> {
            val probe = PhoneAgentAuthProbe(start = { _, _ -> throw IOException(PRIVATE) }, stop = { true })
            failure(probe.run(valid), "hostUnavailable")
        }
        "timeout", "logout-timeout" -> {
            val command = if (scenario == "logout-timeout") request(fixtures, "fx", "logout") else valid
            val child = AuthProcess("{\"state\":\"signedIn\"}")
            var drained = false
            val probe = PhoneAgentAuthProbe(start = { _, _ -> child }, stop = {
                check(it === child); it.destroy(); drained = true; true
            }, waitFor = { process, seconds ->
                check(process === child && seconds == (command["timeoutSeconds"] as Int).toLong()); false
            })
            failure(probe.run(command), "timedOut")
            check(drained && !child.isAlive)
        }
        "output-overflow-timeout" -> {
            val consumed = CountDownLatch(1)
            val child = AuthProcess("a".repeat(65537), outputRead = consumed)
            var drained = false
            val probe = PhoneAgentAuthProbe(start = { _, _ -> child }, stop = {
                check(it === child); it.destroy(); drained = true; true
            }, waitFor = { process, seconds ->
                check(process === child && seconds == 10L)
                check(consumed.await(3, TimeUnit.SECONDS)) { "Capture did not consume the overflowing output" }
                false
            })
            failure(probe.run(valid), "invalidResponse")
            check(drained && !child.isAlive)
        }
        "wait-failure", "reader-failure", "output-overflow" -> {
            val child = AuthProcess(if (scenario == "output-overflow") "a".repeat(65537)
                else "{\"state\":\"signedIn\"}", brokenReader = scenario == "reader-failure")
            var stops = 0
            val probe = PhoneAgentAuthProbe(start = { _, _ -> child }, stop = {
                check(it === child); stops++; it.destroy(); true
            }, waitFor = { process, seconds ->
                if (scenario == "wait-failure") throw IOException(PRIVATE)
                process.waitFor(seconds, TimeUnit.SECONDS)
            })
            failure(probe.run(valid), if (scenario == "wait-failure") "hostUnavailable" else "invalidResponse")
            check(stops >= 1 && !child.isAlive)
        }
        "blocked-profile" -> {
            var starts = 0
            val probe = PhoneAgentAuthProbe(start = { _, _ -> starts++; AuthProcess("{\"state\":\"signedOut\"}") },
                stop = { it.destroy(); true })
            check(probe.blockProfile(PROFILE))
            failure(probe.run(valid), "hostUnavailable")
            check(starts == 0)
            check(probe.run(request(fixtures, profile = "another-profile")) == mapOf("state" to "signedOut"))
        }
        "retained-dead-root" -> {
            val child = AuthProcess("{\"state\":\"signedOut\"}")
            var attempts = 0
            val probe = PhoneAgentAuthProbe(start = { _, _ -> child }, stop = {
                check(it === child); attempts++; it.destroy(); attempts > 1
            }, waitFor = { _, _ -> false })
            failure(probe.run(valid), "hostUnavailable")
            check(!child.isAlive && attempts == 1)
            check(probe.blockProfile(PROFILE))
            check(attempts == 2) { "Dead root ownership was forgotten before drainage" }
        }
        "deletion-during-run" -> {
            val child = AuthProcess("{\"state\":\"signedIn\"}")
            val waiting = CountDownLatch(1)
            val release = CountDownLatch(1)
            val result = AtomicReference<Map<String, Any?>>()
            val failed = AtomicReference<Throwable?>()
            var stopped = false
            val probe = PhoneAgentAuthProbe(start = { _, _ -> child }, stop = {
                check(it === child); it.destroy(); stopped = true; true
            }, waitFor = { _, _ ->
                waiting.countDown(); check(release.await(3, TimeUnit.SECONDS)); true
            })
            val running = Thread {
                try { result.set(probe.run(valid)) } catch (error: Throwable) { failed.set(error) }
            }
            running.start()
            try {
                check(waiting.await(3, TimeUnit.SECONDS)) { "Probe did not reach the owned wait boundary" }
                check(probe.blockProfile(PROFILE))
                check(stopped && !child.isAlive)
            } finally { release.countDown(); running.join(5000) }
            check(!running.isAlive && failed.get() == null)
            failure(checkNotNull(result.get()), "hostUnavailable")
        }
        else -> error("Unknown authored scenario")
    }
    println("PASS $scenario")
}
