package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The phone setup engine over its ports (port of test/setup_engine_test.dart). */
@OptIn(RedactorApi::class)
class SetupEngineTest {
    private val linux = FakeSetupHost()
    private val finishes = mutableListOf<SetupFinishRequest>()
    private var finishResult: Outcome<Unit> = Unit.ok()

    private fun TestScope.engine(
        host: SetupJobHost = linux,
        registry: SetupRegistry = FixtureRegistry,
        readTimeout: Long = 10_000,
        scope: CoroutineScope = backgroundScope,
    ) = SetupEngine(
        host = host,
        registryPort = registry,
        words = fixtureWords,
        scope = scope,
        clock = StepClock(),
        scriptPrelude = FixtureRegistry.PRELUDE,
        redact = { it },
        finisher = SetupFinisher { request ->
            finishes += request
            finishResult
        },
        pollInterval = 5.milliseconds,
        readTimeout = readTimeout.milliseconds,
    )

    private fun TestScope.pumpUntil(done: () -> Boolean) {
        repeat(2_000) {
            if (done()) return
            advanceTimeBy(5)
            runCurrent()
        }
        assertTrue(done(), "condition never held")
    }

    private val ownership = Failure.Runtime(
        RuntimeProblem.OwnershipUnknown,
        Redacted("synthetic native details must not reach progress"),
    )

    // ---- B-LINUX-192 -------------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-192 startSetup receives retained download plus known extracted payload peak`() = runTest {
        val engine = engine(registry = {
            listOf(
                SetupComponent("fixture", "Fixture", "Fixture", "false", ":", downloadBytes = 120_000_000,
                    installedBytes = 350_000_000),
            )
        })
        engine.run(setOf("fixture"))
        assertEquals(1, linux.started.size)
        assertEquals(
            mapOf("requiredFreeBytes" to "${120_000_000L + 350_000_000L + 64L * 1024 * 1024}"),
            linux.spec("fixture").data,
        )
        engine.dispose()
    }

    // ---- B-LINUX-193 -------------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-193 a check ownership failure keeps the recovery code without native details`() = runTest {
        linux.checkError = ownership
        val engine = engine()
        engine.run(setOf("aiteam"))
        assertTrue(linux.started.isEmpty())
        assertEquals(SetupState.Failed, engine.progress.value.state)
        assertEquals(SetupError.OwnershipUnknown, engine.progress.value.error)
        assertEquals("", engine.progress.value.logTail)
        engine.dispose()
    }

    @Test
    fun `B-LINUX-193 a start ownership failure keeps the recovery code without native details`() = runTest {
        linux.startError = ownership
        val engine = engine()
        engine.run(setOf("aiteam"))
        assertTrue(linux.started.isEmpty())
        assertEquals(SetupState.Failed, engine.progress.value.state)
        assertEquals(SetupError.OwnershipUnknown, engine.progress.value.error)
        assertEquals("", engine.progress.value.logTail)
        engine.dispose()
    }

    @Test
    fun `B-LINUX-193 unknown ownership during an agent check cannot trigger install`() = runTest {
        linux.agentCheckError = Failure.Runtime(RuntimeProblem.OwnershipUnknown)
        val engine = engine(registry = {
            listOf(SetupComponent("agent", "Agent", "Agent", "true", "true", agentUser = true))
        })
        engine.run(setOf("agent"))
        assertTrue(linux.started.isEmpty())
        assertEquals(1, linux.agentRuns.size)
        assertEquals(SetupState.Failed, engine.progress.value.state)
        assertEquals(SetupError.OwnershipUnknown, engine.progress.value.error)
        engine.dispose()
    }

    @Test
    fun `B-LINUX-193 a legacy native ownership cause becomes the stable recovery code`() = runTest {
        linux.checkError = Failure.Runtime(RuntimeProblem.Generic, Redacted("IllegalStateException: ownershipUnknown"))
        val engine = engine()
        engine.run(setOf("aiteam"))
        assertEquals(SetupError.OwnershipUnknown, engine.progress.value.error)
        assertTrue(linux.started.isEmpty())
        engine.dispose()
    }

    @Test
    fun `B-LINUX-193 a start step failure persists recovery for the row and the job, thrown or returned`() = runTest {
        for (returned in listOf(Failure.Runtime(RuntimeProblem.OwnershipUnknown).failed(),
            Failure.Runtime(RuntimeProblem.Generic, Redacted("runtime_ownership_unknown")).failed())) {
            val host = FakeSetupHost()
            finishResult = returned
            val engine = engine(host = host)
            engine.run(emptySet())
            host.advance("start", emptyMap())
            pumpUntil { engine.progress.value.state == SetupState.Failed }
            assertEquals("runtime_ownership_unknown", host.steps.single()["error"])
            assertEquals(false, host.steps.single()["ok"])
            assertEquals(SetupError.OwnershipUnknown, engine.progress.value.error)
            assertEquals(SetupError.OwnershipUnknown, engine.progress.value.components.last().error)
            assertTrue(SetupProgressRules.needsOwnershipRecovery(engine.progress.value))
            engine.restore()
            assertEquals(SetupError.OwnershipUnknown, engine.progress.value.error)
            engine.dispose()
        }
    }

    @Test
    fun `B-LINUX-193 a finisher that throws persists only its class name, never native text`() = runTest {
        val engine = engine()
        engine.finisher = SetupFinisher { throw IllegalStateException("ownershipUnknown") }
        engine.run(emptySet())
        linux.advance("start", emptyMap())
        pumpUntil { engine.progress.value.state == SetupState.Failed }
        // A thrown exception's message never crosses: only its class name reaches the persisted error.
        assertEquals(false, linux.steps.single()["ok"])
        assertEquals("IllegalStateException", linux.steps.single()["error"])
        assertEquals(SetupError.CannotStart, engine.progress.value.error)
        engine.dispose()
    }

    @Test
    fun `B-LINUX-193 a restored job-level recovery code survives without a failed row`() = runTest {
        linux.job = mutableMapOf(
            "jobId" to "ownership-before-step",
            "state" to "failed",
            "order" to listOf("opencode"),
            "components" to mapOf("opencode" to mapOf("state" to "pending")),
            "error" to "runtime_ownership_unknown",
        )
        val engine = engine()
        engine.restore()
        assertEquals(SetupState.Failed, engine.progress.value.state)
        assertEquals(SetupError.OwnershipUnknown, engine.progress.value.error)
        engine.dispose()
    }

    // ---- B-LINUX-194 -------------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-194 confirmed absent Ubuntu with no saved job defaults to OC2`() = runTest {
        linux.installed = false
        val engine = engine()
        engine.run(emptySet())
        assertEquals("opencode2", linux.spec("start").data["runtime"])
        assertTrue(linux.spec("opencode").script!!.contains(FixtureRegistry.OPENCODE2_PIN))
        engine.dispose()
    }

    @Test
    fun `B-LINUX-194 pre-job installed Ubuntu installs OC2 without a legacy install path`() = runTest {
        val engine = engine()
        engine.run(emptySet())
        assertEquals("opencode2", linux.spec("start").data["runtime"])
        assertTrue(linux.spec("opencode").script!!.contains(FixtureRegistry.OPENCODE2_PIN))
        assertFalse(linux.spec("opencode").script!!.contains("opencode-ai@"))
        engine.dispose()
    }

    @Test
    fun `B-LINUX-194 explicit OC2 selection wins on pre-job installed Ubuntu`() = runTest {
        val engine = engine()
        engine.run(emptySet(), mapOf("opencode" to mapOf("runtime" to "opencode2")))
        assertEquals("opencode2", linux.spec("start").data["runtime"])
        engine.dispose()
    }

    @Test
    fun `B-LINUX-194 unreadable Ubuntu presence does not become a fresh installation`() = runTest {
        linux.statusError = Failure.Runtime(RuntimeProblem.Generic)
        val engine = engine()
        engine.run(emptySet())
        assertTrue(linux.started.isEmpty())
        assertTrue(linux.runs.isEmpty())
        assertEquals(SetupState.Failed, engine.progress.value.state)
        assertTrue(engine.progress.value.canContinue)
        assertEquals(SetupError.StatusUnreadable, engine.progress.value.error)
        linux.statusError = null
        engine.run(emptySet())
        assertEquals("opencode2", linux.spec("start").data["runtime"])
        engine.dispose()
    }

    @Test
    fun `B-LINUX-194 native, malformed or timed-out status cannot turn an existing OC1 job into a fresh install`() =
        runTest {
            for (fault in listOf("native", "malformed", "timeout", "hang")) {
                val host = FakeSetupHost()
                host.job = mutableMapOf(
                    "jobId" to "legacy",
                    "state" to "interrupted",
                    "order" to listOf("opencode"),
                    "components" to mapOf("opencode" to mapOf("state" to "pending")),
                )
                host.statusFaults += fault
                val engine = engine(host = host, readTimeout = 20)
                engine.run(emptySet())
                assertEquals(SetupState.Failed, engine.progress.value.state, fault)
                assertTrue(engine.progress.value.canContinue, fault)
                assertEquals(SetupError.StatusUnreadable, engine.progress.value.error, fault)
                assertFalse(engine.progress.value.logTail.contains("unreadable setup"), fault)
                assertTrue(host.started.isEmpty(), fault)
                assertTrue(host.runs.isEmpty(), fault)
                // Once the original job can be read, retry resumes it on OC2.
                engine.run(emptySet())
                assertEquals("opencode2", host.spec("start").data["runtime"], fault)
                assertTrue(host.spec("opencode").script!!.contains(FixtureRegistry.OPENCODE2_PIN), fault)
                engine.dispose()
            }
        }

    // ---- B-LINUX-195 -------------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-195 components whose checks pass are skipped with their version, the rest get the prelude and script`() =
        runTest {
            linux.checks += mapOf(
                "linux" to (true to "24.04.5"),
                "essentials" to (true to "2.43.0"),
                "python" to (true to "3.12.3"),
            )
            val engine = engine()
            engine.run(setOf("python"))
            assertEquals(1, linux.runs.size, "one proot run for all checks")
            assertEquals(listOf("linux", "essentials", "python", "node", "opencode", "start"), linux.ids())
            for (id in listOf("linux", "essentials", "python")) assertTrue(linux.spec(id).skipped, id)
            assertEquals("2.43.0", linux.spec("essentials").version)
            assertTrue(linux.spec("node").script!!.startsWith(FixtureRegistry.PRELUDE))
            assertTrue(linux.spec("node").script!!.contains("oc_download"))
            assertEquals(mapOf("retry" to fixtureWords.stageRetrying), linux.spec("node").labels)
            assertEquals(FixtureRegistry.PRELUDE + FixtureRegistry.openCodeInstall("opencode2"), linux.spec("opencode").script)
            assertTrue(linux.spec("start").step)
            assertEquals(
                mapOf("requiredFreeBytes" to "300000000", "runtime" to "opencode2", "openCodeChanged" to "true"),
                linux.spec("start").data,
            )
            val progress = engine.progress.value
            assertEquals(SetupState.Running, progress.state)
            assertEquals(linux.started.single().jobId, progress.jobId)
            assertTrue(progress.components.take(3).all { it.state == ComponentState.Skipped })
            engine.dispose()
        }

    @Test
    fun `B-LINUX-195 nothing is checked inside Ubuntu before it is installed and the base is native with labels`() =
        runTest {
            linux.installed = false
            val engine = engine()
            engine.run(emptySet())
            assertTrue(linux.runs.isEmpty())
            val base = linux.spec("linux")
            assertTrue(base.native)
            assertFalse(base.skipped)
            assertEquals(
                mapOf("download" to fixtureWords.stageDownloadingLinux, "unpack" to fixtureWords.stageUnpackingLinux),
                base.labels,
            )
            assertFalse("python" in linux.ids(), "python is optional: not selected, not in the job")
            engine.dispose()
        }

    @Test
    fun `B-LINUX-195 the running state and the job list are published before run returns, while checks run`() =
        runTest {
            linux.holdChecks = CompletableDeferred()
            val engine = engine()
            val running = async { engine.run(setOf("python")) }
            runCurrent()
            val progress = engine.progress.value
            assertEquals(SetupState.Running, progress.state)
            assertNotNull(progress.jobId)
            assertEquals(ComponentState.Checking, progress.components.first().state)
            assertEquals("start", progress.components.last().id)
            assertEquals(ComponentState.Pending, progress.components.last().state)
            assertTrue(linux.started.isEmpty())
            linux.holdChecks!!.complete(Unit)
            running.await()
            assertEquals(1, linux.started.size)
            engine.dispose()
        }

    @Test
    fun `B-LINUX-195 an interrupted job continues with its own params unless new ones are given`() = runTest {
        linux.job = mutableMapOf(
            "jobId" to "old",
            "state" to "interrupted",
            "order" to listOf("linux", "opencode"),
            "components" to mapOf("linux" to mapOf("state" to "done"), "opencode" to mapOf("state" to "pending")),
            "params" to mapOf("opencode" to mapOf("runtime" to "opencode2")),
        )
        linux.checks["linux"] = true to "24.04.5"
        val engine = engine()
        engine.run(emptySet())
        assertEquals(mapOf("opencode" to mapOf("runtime" to "opencode2")), linux.started.single().params)
        assertTrue(linux.spec("opencode").script!!.contains(FixtureRegistry.OPENCODE2_PIN))
        assertEquals("opencode2", linux.spec("start").data["runtime"])
        // The check asked about OpenCode 2, not 1.
        assertTrue(linux.runs.single().contains("oc_bin=\$(command -v opencode2)"))

        linux.job!!["state"] = "failed"
        engine.run(emptySet(), mapOf("opencode" to mapOf("runtime" to "opencode1")))
        assertEquals(mapOf("opencode" to mapOf("runtime" to "opencode2")), linux.started.last().params)
        engine.dispose()
    }

    // ---- B-LINUX-196 -------------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-196 a first setup says so in its params and the flag survives a restart and a Continue`() = runTest {
        val engine = engine()
        engine.run(emptySet(), SetupJobParams.firstSetup)
        val expected = SetupJobParams.firstSetup + mapOf("opencode" to mapOf("runtime" to "opencode2"))
        assertEquals(expected, linux.started.single().params)
        assertTrue(engine.progress.value.firstSetup)
        engine.dispose()

        // The app is killed: a fresh engine reads only setup.json.
        linux.job!!["state"] = "interrupted"
        val restarted = engine()
        restarted.restore()
        assertTrue(restarted.progress.value.firstSetup)
        // Continue from screen B passes no params and keeps the flag.
        restarted.run(emptySet())
        assertEquals(expected, linux.started.last().params)
        assertTrue(restarted.progress.value.firstSetup)
        restarted.dispose()
    }

    @Test
    fun `B-LINUX-196 the first-setup flag is laid over a stopped job's params, never instead of them`() = runTest {
        linux.job = mutableMapOf(
            "jobId" to "old",
            "state" to "failed",
            "order" to listOf("linux", "opencode"),
            "components" to mapOf("linux" to mapOf("state" to "done"), "opencode" to mapOf("state" to "failed")),
            "params" to mapOf("opencode" to mapOf("runtime" to "opencode2")),
        )
        linux.checks["linux"] = true to "24.04.5"
        val engine = engine()
        engine.run(emptySet(), SetupJobParams.firstSetup)
        assertEquals(
            mapOf("opencode" to mapOf("runtime" to "opencode2")) + SetupJobParams.firstSetup,
            linux.started.single().params,
        )
        engine.dispose()
    }

    @Test
    fun `B-LINUX-196 jobs from the This phone card are not first setups`() = runTest {
        val engine = engine()
        engine.run(setOf("opencode"), mapOf("opencode" to mapOf("runtime" to "opencode2")))
        assertFalse(engine.progress.value.firstSetup)
        engine.restore()
        assertFalse(engine.progress.value.firstSetup)
        engine.dispose()
    }

    @Test
    fun `B-LINUX-196 a finished job preserves its runtime in the next run`() = runTest {
        linux.job = mutableMapOf(
            "jobId" to "old",
            "state" to "done",
            "order" to listOf("linux"),
            "components" to mapOf("linux" to mapOf("state" to "done")),
            "params" to mapOf("opencode" to mapOf("runtime" to "opencode2")),
        )
        val engine = engine()
        engine.run(emptySet())
        assertEquals(mapOf("opencode" to mapOf("runtime" to "opencode2")), linux.started.single().params)
        engine.dispose()
    }

    // ---- B-LINUX-197 -------------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-197 Continue after a failed OpenCode install replaces only OpenCode`() = runTest {
        linux.job = mutableMapOf(
            "jobId" to "old",
            "state" to "failed",
            "current" to "start",
            "order" to listOf("linux", "essentials", "python", "node", "opencode", "start"),
            "components" to (listOf("linux", "essentials", "python", "node", "opencode").associateWith {
                mapOf("state" to "done")
            } + mapOf("start" to mapOf("state" to "failed", "error" to "OpenCode did not answer"))),
            "params" to SetupJobParams.firstSetup,
        )
        linux.checks += mapOf(
            "linux" to (true to "24.04.5"),
            "essentials" to (true to "2.43.0"),
            "python" to (true to "3.12.3"),
            "node" to (true to "24.21.0"),
        )
        val engine = engine()
        engine.resume()
        assertEquals(listOf("linux", "essentials", "python", "node", "opencode", "start"), linux.ids())
        for (id in listOf("linux", "essentials", "python", "node")) assertTrue(linux.spec(id).skipped, id)
        assertFalse(linux.spec("opencode").skipped)
        assertTrue(linux.spec("opencode").script!!.contains(FixtureRegistry.OPENCODE2_PIN))
        assertEquals(
            mapOf("requiredFreeBytes" to "300000000", "runtime" to "opencode2", "openCodeChanged" to "true"),
            linux.spec("start").data,
        )
        // The check that failed is the native one, not a version print.
        assertTrue(linux.runs.single().contains("readlink -f"))
        engine.dispose()
    }

    @Test
    fun `B-LINUX-197 Add tools keeps OC2 despite a done or failed OC1 job and resumes with the same runtime`() =
        runTest {
            for (previousState in listOf("done", "failed")) {
                val host = FakeSetupHost()
                host.job = mutableMapOf(
                    "jobId" to "previous-oc1",
                    "state" to previousState,
                    "order" to listOf("opencode"),
                    "components" to mapOf("opencode" to mapOf("state" to previousState)),
                    "params" to mapOf("opencode" to mapOf("runtime" to "opencode1")),
                )
                host.checks += mapOf(
                    "essentials" to (true to "2.43.0"),
                    "node" to (true to "24.21.0"),
                    "opencode" to (true to "2.2.5"),
                )
                val params = SetupJobParams.adding(listOf("python", "aiteam")) +
                    mapOf("opencode" to mapOf("runtime" to "opencode2"))
                val engine = engine(host = host)
                engine.run(setOf("python", "aiteam"), params)
                assertEquals(params, host.started.single().params, previousState)
                assertEquals("Adding Python and AI Team", host.started.single().texts.title)
                assertEquals(listOf("python", "aiteam"), engine.progress.value.adding)
                assertTrue(host.runs.single().contains("command -v opencode2"))
                assertTrue(host.spec("opencode").skipped)
                assertEquals("2.2.5", host.spec("opencode").version)
                assertEquals("opencode2", host.spec("start").data["runtime"])
                assertEquals("false", host.spec("start").data["openCodeChanged"])

                // Interrupted and OpenCode needs repair: Continue still uses the OC2 archive and restarts OC2.
                host.job!!["state"] = "interrupted"
                host.checks.remove("opencode")
                engine.resume()
                assertEquals(params, host.started.last().params)
                val openCode = host.started.last().components.single { it.id == "opencode" }
                val start = host.started.last().components.single { it.id == "start" }
                assertTrue(openCode.script!!.contains(FixtureRegistry.OPENCODE2_PIN))
                assertFalse(openCode.script!!.contains("opencode-ai@"))
                assertEquals("opencode2", start.data["runtime"])
                engine.dispose()
            }
        }

    @Test
    fun `B-LINUX-197 a done or interrupted legacy job resumes on OC2`() = runTest {
        for (state in listOf("done", "interrupted")) {
            val host = FakeSetupHost()
            host.job = mutableMapOf(
                "jobId" to "legacy",
                "state" to state,
                "order" to listOf("opencode", "start"),
                "components" to mapOf("opencode" to mapOf("state" to "done"), "start" to mapOf("state" to state)),
            )
            val engine = engine(host = host)
            engine.run(emptySet())
            assertTrue(host.spec("opencode").script!!.contains(FixtureRegistry.OPENCODE2_PIN), state)
            assertEquals("opencode2", host.spec("start").data["runtime"], state)
            assertEquals(mapOf("opencode" to mapOf("runtime" to "opencode2")), host.started.single().params, state)
            engine.dispose()
        }
    }

    @Test
    fun `B-LINUX-197 a job already running is watched, not started again`() = runTest {
        linux.job = mutableMapOf(
            "jobId" to "live",
            "state" to "running",
            "current" to "node",
            "order" to listOf("node"),
            "components" to mapOf("node" to mapOf("state" to "running", "done" to 5, "total" to 10)),
        )
        val engine = engine()
        engine.run(emptySet())
        assertTrue(linux.started.isEmpty())
        assertEquals("live", engine.progress.value.jobId)
        assertEquals(5L, engine.progress.value.components.single().bytesDone)
        engine.dispose()
    }

    // ---- B-LINUX-198 -------------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-198 the start step runs the finisher once and reports it done`() = runTest {
        for (id in listOf("linux", "essentials", "node", "opencode")) linux.checks[id] = true to "1"
        val engine = engine()
        engine.run(emptySet())
        assertEquals("false", linux.spec("start").data["openCodeChanged"])
        linux.advance("start", mapOf("stage" to fixtureWords.stageStarting))
        val watcher = backgroundScope.launch { engine.progress.collect { } }
        pumpUntil { linux.steps.isNotEmpty() }
        advanceTimeBy(30)
        runCurrent()
        watcher.cancel()
        assertEquals(1, finishes.size)
        assertEquals("opencode2", finishes.single().runtime)
        assertFalse(finishes.single().openCodeChanged)
        assertEquals(SetupHostKind.Builtin, finishes.single().host)
        assertEquals(true, linux.steps.single()["ok"])
        assertEquals(SetupState.Done, engine.progress.value.state)
        assertEquals(1.0, engine.progress.value.overall)
        assertEquals(1, engine.toolsChanged.value, "a job that ended bumps the installed-tools signal once")
        engine.dispose()
    }

    @OptIn(RedactorApi::class)
    @Test
    fun `B-LINUX-198 a failed start fails the job in plain words`() = runTest {
        finishResult = Failure.Runtime(RuntimeProblem.ServerStartUnavailable, Redacted("the server stopped")).failed()
        val engine = engine()
        engine.run(emptySet())
        linux.advance("start", emptyMap())
        pumpUntil { engine.progress.value.state == SetupState.Failed }
        val start = engine.progress.value.components.last()
        assertEquals(SetupError.CannotStart, start.error)
        assertEquals(start.error, engine.progress.value.error)
        assertTrue(engine.progress.value.logTail.contains("the server stopped"))
        assertTrue(engine.progress.value.canContinue)
        assertFalse(SetupProgressRules.needsOwnershipRecovery(engine.progress.value))
        engine.dispose()
    }

    @Test
    fun `B-LINUX-198 the job keeps polling without listeners while it runs and stops once over and unwatched`() =
        runTest {
            val engine = engine()
            engine.run(emptySet())
            advanceTimeBy(40)
            runCurrent()
            val whileRunning = linux.statusReads
            advanceTimeBy(40)
            runCurrent()
            assertTrue(linux.statusReads > whileRunning)
            linux.job!!["state"] = "done"
            advanceTimeBy(40)
            runCurrent()
            val after = linux.statusReads
            advanceTimeBy(400)
            runCurrent()
            assertEquals(after, linux.statusReads)
            engine.dispose()
        }

    @Test
    fun `B-LINUX-198 polling survives a status read that fails or never answers`() = runTest {
        val engine = engine(readTimeout = 20)
        engine.run(emptySet())
        val watcher = backgroundScope.launch { engine.progress.collect { } }
        linux.statusFaults += listOf("throw", "hang")
        linux.advance("start", mapOf("stage" to fixtureWords.stageStarting))
        pumpUntil { engine.progress.value.state == SetupState.Done }
        assertTrue(linux.statusFaults.isEmpty())
        assertEquals(true, linux.steps.single()["ok"])
        watcher.cancel()
        engine.dispose()
    }

    // ---- B-LINUX-199 -------------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-199 cancel stops the native job and finished components stay`() = runTest {
        linux.installed = false
        val engine = engine()
        engine.run(emptySet())
        linux.advance("node", mapOf("stage" to "Downloading Node.js", "done" to 1))
        engine.cancel()
        assertEquals(1, linux.cancels)
        val progress = engine.progress.value
        assertEquals(SetupState.Cancelled, progress.state)
        assertTrue(progress.canContinue)
        assertEquals(ComponentState.Done, progress.components.first().state)
        engine.dispose()
    }

    @Test
    fun `B-LINUX-199 while still checking nothing is handed to the native side`() = runTest {
        linux.holdChecks = CompletableDeferred()
        val engine = engine()
        val running = async { engine.run(emptySet()) }
        runCurrent()
        engine.cancel()
        linux.holdChecks!!.complete(Unit)
        running.await()
        assertTrue(linux.started.isEmpty())
        assertEquals(0, linux.cancels)
        assertEquals(SetupState.Cancelled, engine.progress.value.state)
        engine.dispose()
    }

    @Test
    fun `B-LINUX-199 a finished job held in memory goes when Linux and its job are gone`() = runTest {
        linux.job = FakeSetupHost.jobFrom(RECORDED_INTERRUPTED).also { it["state"] = "done" }
        val engine = engine()
        engine.restore()
        assertEquals(SetupState.Done, engine.progress.value.state)
        // Reset erased Ubuntu, and setup.json with it.
        linux.job = null
        linux.installed = false
        engine.restore()
        assertEquals(SetupState.Idle, engine.progress.value.state)
        assertNull(engine.progress.value.jobId)
        engine.dispose()
    }

    @Test
    fun `B-LINUX-199 restore reads an interrupted job after a restart`() = runTest {
        linux.job = FakeSetupHost.jobFrom(RECORDED_INTERRUPTED)
        val engine = engine()
        engine.restore()
        val progress = engine.progress.value
        assertEquals("setup-1790196784299510", progress.jobId)
        assertEquals(SetupState.Interrupted, progress.state)
        assertTrue(progress.canContinue)
        assertEquals(listOf("linux", "essentials", "python", "node", "opencode", "start"), progress.components.map { it.id })
        val node = progress.components[3]
        assertEquals(ComponentState.Pending, node.state)
        assertEquals(23134208L, node.bytesDone)
        assertEquals(58088022L, node.bytesTotal)
        assertEquals("2.43.0", progress.components[1].version)
        assertTrue(progress.logTail.contains("Downloading Node.js"))
        engine.dispose()
    }

    @Test
    fun `B-LINUX-199 installedOptional runs only the optional checks`() = runTest {
        linux.checks["python"] = true to "3.12.3"
        val engine = engine()
        assertEquals(setOf("python"), (engine.installedOptional() as Outcome.Ok).value)
        assertTrue(linux.runs.single().contains("'python'"))
        assertFalse(linux.runs.single().contains("'node'"))
        linux.installed = false
        assertEquals(emptySet(), (engine.installedOptional() as Outcome.Ok).value)
        engine.dispose()
    }
}
