package io.github.eslamasabry.oc.engine.runtime.procs

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Port of test/support/running_on_phone_fakes.dart: the scenes the Running on this phone tests use. */
internal fun proc(
    pid: Int,
    ppid: Int,
    name: String,
    owner: PhoneProcessOwner = PhoneProcessOwner.OpenCode,
    cpu: Double = 0.0,
    mb: Long = 20,
    elapsed: Duration = 30.minutes,
    command: String? = null,
    session: String? = null,
): RunningProcess = RunningProcess(pid, ppid, name, command ?: name, cpu, mb * 1024 * 1024, elapsed, owner, session)

internal const val CHAT_SESSION = "4f1c2b7e-0d5a-4c1e-9a2e-6b3f1d0c8a11"
internal const val DEAD_SESSION = "9a2e5c1d-77b0-4f3e-8d21-0c6a9e4b1f20"

/** The app, OpenCode running a test, one Claude Code conversation running Flutter tests, and a terminal. */
internal fun normalProcesses(testerCpu: Double = 35.0): List<RunningProcess> = listOf(
    proc(9000, 600, "opencode_mobile", owner = PhoneProcessOwner.App, cpu = 4.0, mb = 180, elapsed = 2.hours),
    proc(10100, 9000, "proot", cpu = 1.0, mb = 6, elapsed = 2.hours),
    proc(
        10110, 10100, "node", cpu = 6.0, mb = 210, elapsed = 2.hours,
        command = "node /usr/lib/node_modules/opencode/bin/opencode serve",
    ),
    proc(10120, 10110, "bash", cpu = 0.4, mb = 4, elapsed = 3.minutes, command = "bash -c npm test"),
    proc(10121, 10120, "node", cpu = 12.0, mb = 96, elapsed = 3.minutes, command = "node /usr/bin/npm test"),
    proc(10200, 9000, "proot", owner = PhoneProcessOwner.Paseo, cpu = 0.5, mb = 6, elapsed = 1.hours),
    proc(
        10210, 10200, "node", owner = PhoneProcessOwner.Paseo, cpu = 2.0, mb = 120, elapsed = 1.hours,
        command = "node /root/.paseo/daemon.js --listen 127.0.0.1:6767",
    ),
    proc(
        10220, 10210, "claude", owner = PhoneProcessOwner.Paseo, cpu = 20.0, mb = 260, elapsed = 12.minutes,
        command = "claude --resume $CHAT_SESSION --output-format stream-json", session = CHAT_SESSION,
    ),
    proc(
        10230, 10220, "bash", owner = PhoneProcessOwner.Paseo, cpu = 0.2, mb = 4, elapsed = 4.minutes,
        command = "bash -lc flutter test test/login_test.dart",
    ),
    proc(
        10240, 10230, "flutter_tester", owner = PhoneProcessOwner.Paseo, cpu = testerCpu, mb = 340,
        elapsed = 4.minutes, command = "flutter_tester --enable-checked-mode test/login_test.dart",
    ),
    proc(10300, 9000, "proot", owner = PhoneProcessOwner.Terminal, cpu = 0.0, mb = 6, elapsed = 40.minutes),
    proc(
        10310, 10300, "bash", owner = PhoneProcessOwner.Terminal, cpu = 0.0, mb = 5, elapsed = 40.minutes,
        command = "-bash",
    ),
)

/** What a dead Claude Code conversation left behind: its Flutter tests and Dart spinning with nothing above them. */
internal fun leftBehindProcesses(): List<RunningProcess> = listOf(
    proc(
        15001, 1, "claude", owner = PhoneProcessOwner.Orphan, cpu = 0.3, mb = 180, elapsed = 52.minutes,
        command = "claude --resume $DEAD_SESSION --output-format stream-json", session = DEAD_SESSION,
    ),
    proc(
        15002, 15001, "bash", owner = PhoneProcessOwner.Orphan, cpu = 0.0, mb = 4, elapsed = 50.minutes,
        command = "bash -lc flutter test",
    ),
) + listOf("flutter_tester", "dart", "flutter_tester", "dart").mapIndexed { i, name ->
    proc(
        15003 + i, 15002, name, owner = PhoneProcessOwner.Orphan, cpu = 99.0 + (i % 2), mb = 300L + i * 20,
        elapsed = 49.minutes, command = "$name --enable-checked-mode test/widget_test.dart",
    )
}

/** [normalProcesses] with what was left behind, and its Flutter test heavy. */
internal fun hotProcesses(): List<RunningProcess> = normalProcesses(testerCpu = 96.0) + leftBehindProcesses()

/** 29 of Android's 32: OpenCode running many workers. */
internal fun nearLimitProcesses(workers: Int = 18): List<RunningProcess> = normalProcesses() + (0 until workers).map { i ->
    proc(
        11000 + i, 10121, "node", cpu = 3.0, mb = 60, elapsed = 2.minutes,
        command = "node /root/projects/shop/node_modules/.bin/jest --worker $i",
    )
}

/** A clock the test moves. */
internal class FakeClock {
    var now: Instant = Instant.parse("2026-10-27T10:00:00Z")

    fun advance(by: Duration) {
        now += by
    }
}

/** A watch whose readings so far span [history] at 5-second steps, so a process that stays busy is already heavy. */
internal fun watchWithHistory(snapshot: RunningSnapshot, clock: FakeClock, history: Duration): PhoneProcessWatch {
    val watch = PhoneProcessWatch()
    var t = Duration.ZERO
    while (t < history) {
        watch.record(snapshot, clock.now)
        clock.advance(5.seconds)
        t += 5.seconds
    }
    return watch
}
