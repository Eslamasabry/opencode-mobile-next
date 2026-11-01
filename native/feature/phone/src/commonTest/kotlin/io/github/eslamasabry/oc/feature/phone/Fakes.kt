package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitSource
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitStatus
import io.github.eslamasabry.oc.feature.phone.presentation.DeveloperOptionsOpened
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveInfo
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSetting
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSettings
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneNavigator
import io.github.eslamasabry.oc.feature.phone.presentation.PhonePage
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneProcessSource
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlin.time.Clock
import kotlin.time.Instant

internal fun TestScope.testClock(): Clock =
    object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
    }

internal val refused: Outcome<Unit> = Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))

internal class FakeServer(initial: PhoneServerFacts = PhoneServerFacts()) : PhoneServerSource {
    override val facts = MutableStateFlow(initial)
    val calls = mutableListOf<String>()
    var answer: Outcome<Unit> = Unit.ok()

    /** When set, a command waits for it, so a test can tap again while it runs. */
    var gate: CompletableDeferred<Unit>? = null

    private suspend fun run(name: String): Outcome<Unit> {
        calls += name
        gate?.await()
        return answer
    }

    override suspend fun refresh() {
        calls += "refresh"
    }

    override suspend fun start() = run("start")

    override suspend fun stop() = run("stop")

    override suspend fun restart() = run("restart")

    override suspend fun updateOpenCode() = run("update")
}

internal class FakeSettings(var info: KeepAliveInfo? = null, var opens: Boolean = true) : KeepAliveSettings {
    val opened = mutableListOf<KeepAliveSetting>()
    var reads = 0

    override suspend fun info(): KeepAliveInfo? {
        reads++
        return info
    }

    override suspend fun open(setting: KeepAliveSetting): Boolean {
        opened += setting
        return opens
    }
}

internal class RecordingNav : PhoneNavigator {
    val calls = mutableListOf<String>()

    override fun open(page: PhonePage) {
        calls += "open $page"
    }

    override fun back() {
        calls += "back"
    }
}

internal class FakeProcesses(var reading: Outcome<ProcessReading>) : PhoneProcessSource {
    var reads = 0
    val stopped = mutableListOf<Int>()
    var stopAnswer: Outcome<Unit> = Unit.ok()

    override suspend fun read(): Outcome<ProcessReading> {
        reads++
        return reading
    }

    override suspend fun stop(pid: Int): Outcome<Unit> {
        stopped += pid
        return stopAnswer
    }
}

internal class FakeChildLimit(
    var status: ChildLimitStatus = ChildLimitStatus(),
    var kill: Instant? = null,
    var queues: Boolean = false,
    var hidden: Instant? = null,
    var opens: DeveloperOptionsOpened = DeveloperOptionsOpened.Developer,
) : ChildLimitSource {
    var openCalls = 0

    override suspend fun status() = status

    override suspend fun openDeveloperOptions(): DeveloperOptionsOpened {
        openCalls++
        return opens
    }

    override suspend fun lastAndroidKill() = kill

    override fun queuesOften() = queues

    override suspend fun hiddenAt() = hidden

    override suspend fun hide(at: Instant) {
        hidden = at
    }
}
