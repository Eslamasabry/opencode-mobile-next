package io.github.eslamasabry.oc.engine.usagereaders

import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.usagereaders.computer.COMPUTER_USAGE_TITLE
import io.github.eslamasabry.oc.engine.usagereaders.computer.ComputerUsageProblem
import io.github.eslamasabry.oc.engine.usagereaders.computer.ComputerUsageSource
import io.github.eslamasabry.oc.engine.usagereaders.computer.classifyComputerUsageOutput
import io.github.eslamasabry.oc.engine.usagereaders.computer.parseComputerUsageOutput
import io.github.eslamasabry.oc.engine.usagereaders.computer.runComputerUsageCheck
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MIN = 60_000L

class ComputerUsageSourceTest {
    private fun server(id: String, backend: BackendKind = BackendKind.OpenCode2, needsPassword: Boolean = false) =
        UsageServer(id, backend, inApp = id == "inapp", needsPassword = needsPassword)

    private class Rig(val switches: MemoryUsageSwitches, val source: ComputerUsageSource, val ports: List<FakeComputerPort>) {
        var now: Long = COMPUTER_NOW
    }

    private fun TestScope.rig(
        runs: List<List<FakeStep>>,
        servers: List<UsageServer>? = null,
        timeoutMs: Long = 3_000,
        switches: Map<String, Boolean> = emptyMap(),
    ): Rig {
        val store = MemoryUsageSwitches(mapOf("oc.usageComputerReader.pc" to true) + switches)
        val ports = mutableListOf<FakeComputerPort>()
        var run = 0
        lateinit var rig: Rig
        val source = ComputerUsageSource(
            switches = store,
            servers = { servers ?: listOf(server("pc")) },
            isReadable = { true },
            createPort = {
                FakeComputerPort(runs[minOf(run, runs.size - 1)]).also {
                    run++
                    ports += it
                }
            },
            loadSource = { "SCRIPT" },
            clock = { rig.now },
            elapsedMs = { testScheduler.currentTime },
            timeoutMs = timeoutMs,
        )
        rig = Rig(store, source, ports)
        return rig
    }

    private fun context(
        force: Boolean = true,
        background: Boolean = false,
        now: Long = COMPUTER_NOW,
        lastReading: ((String, String) -> UsageReading?)? = null,
        signedIn: ((String) -> Set<String>)? = null,
    ) = UsageReadContext(now, force, background, lastReading, signedIn)

    private val ok = listOf(listOf(FakeStep(output = listOf("${line(CODEX_OK)}\r\n"))))

    // ---- B-AGENT-071 reading the answer

    @Test
    fun `B-AGENT-071 only the exact marker line counts, through echo and ANSI codes`() {
        val answerLine = line(CODEX_OK)
        val noisy = "\u001B[?2004h\u001B[1;32muser@pc\u001B[0m:~\$ node --input-type=module " +
            "-e \"const MARKER = 'OCMN_USAGE_V1'\"\r\n" +
            "OCMN_USAGE_V1 not base64 at all\r\n" +
            "\u001B[2K\r$answerLine\u001B[0m\r\n\u001B[?2004h"
        val result = assertNotNull(parseComputerUsageOutput(noisy))
        assertTrue(result.ok)
        assertEquals("oauth", result.auth[QuotaProvider.Codex])
        assertEquals("plus", result.snapshots.getValue(QuotaProvider.Codex).account.plan)
        assertNull(parseComputerUsageOutput("echo OCMN_USAGE_V1 abcd\r\n"))
        assertNull(parseComputerUsageOutput("nothing here"))
        assertNull(parseComputerUsageOutput(""))
    }

    @Test
    fun `B-AGENT-071 a line split across chunks is read once it is whole`() {
        val answerLine = line(CODEX_OK)
        assertNull(parseComputerUsageOutput(answerLine.substring(0, 30)))
        assertNotNull(parseComputerUsageOutput("$answerLine\r\n"))
    }

    @Test
    fun `B-AGENT-071 the error codes and a bad provider are fixed outcomes`() {
        val old = assertNotNull(parseComputerUsageOutput(line(answer(error = "nodeTooOld"))))
        assertEquals(ComputerUsageProblem.NodeTooOld, old.problem)
        val failed = assertNotNull(parseComputerUsageOutput(line(answer(error = "failed"))))
        assertEquals(ComputerUsageProblem.NoAnswer, failed.problem)
        // A snapshot the app cannot trust is dropped, the others stay.
        val bad = snapshot("glm", ref = REF).apply { put("schemaVersion", 9L) }
        val mixed = assertNotNull(
            parseComputerUsageOutput(
                line(
                    answer(
                        providers = mapOf("codex" to snapshot("codex", ref = REF), "glm" to bad),
                        auth = mapOf("codex" to "oauth", "glm" to "api"),
                    ),
                ),
            ),
        )
        assertEquals(listOf(QuotaProvider.Codex), mixed.snapshots.keys.toList())
        assertNull(parseComputerUsageOutput("OCMN_USAGE_V1 ${"A".repeat(200_000)}"))
    }

    @Test
    fun `B-AGENT-071 why Node did not run is chosen from words, never stored`() {
        assertEquals(ComputerUsageProblem.NodeMissing, classifyComputerUsageOutput("bash: node: command not found"))
        assertEquals(ComputerUsageProblem.NodeMissing, classifyComputerUsageOutput("'node' is not recognized as a command"))
        assertEquals(ComputerUsageProblem.NodeTooOld, classifyComputerUsageOutput("SyntaxError: Unexpected token"))
        assertEquals(ComputerUsageProblem.NoAnswer, classifyComputerUsageOutput("\u001B[31mhello\u001B[0m"))
    }

    // ---- B-AGENT-072 one check

    @Test
    fun `B-AGENT-072 runs node first, reads the line, and removes the terminal`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(output = listOf("\u001B[0m", "${line(CODEX_OK)}\r\n")))))
        val result = rig.source.readNow(server("pc"))
        assertTrue(result.ok)
        val port = rig.ports.single()
        assertEquals(1, port.started.size)
        assertEquals("node", port.started.single().first)
        assertEquals(listOf("--input-type=module", "-e", "SCRIPT"), port.started.single().second)
        assertEquals(COMPUTER_USAGE_TITLE, port.started.single().third)
        assertEquals(listOf("pty_1"), port.removed)
        assertEquals(1, port.closed)
    }

    @Test
    fun `B-AGENT-072 a silent reader times out and the terminal is still removed`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(hang = true))), timeoutMs = 300)
        val result = rig.source.readNow(server("pc"))
        assertEquals(ComputerUsageProblem.NoAnswer, result.problem)
        // Never wait the full time again for a second try with nothing left.
        for (port in rig.ports) {
            assertTrue(port.removed.isNotEmpty())
            assertEquals(port.started.size, port.removed.size)
            assertEquals(1, port.closed)
        }
        assertEquals(1, rig.ports.single().started.size)
    }

    @Test
    fun `B-AGENT-072 the login shell runs when node itself fails before output`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(connectError = true), FakeStep(output = listOf("${line(CODEX_OK)}\r\n")))))
        assertTrue(rig.source.readNow(server("pc")).ok)
        val port = rig.ports.single()
        assertEquals(listOf("node", "bash"), port.started.map { it.first })
        val args = port.started.last().second
        assertEquals("-lc", args.first())
        assertTrue("node --input-type=module -e \"\$1\"" in args[1])
        assertEquals("_", args[2])
        assertEquals("SCRIPT", args.last())
        assertEquals(listOf("pty_1", "pty_2"), port.removed)
    }

    @Test
    fun `B-AGENT-072 the login shell also runs when the server refuses the command`() = runTest {
        val rig = rig(
            listOf(
                listOf(
                    FakeStep(startError = ComputerUsageProblem.NodeMissing),
                    FakeStep(output = listOf("${line(CODEX_OK)}\r\n")),
                ),
            ),
        )
        assertTrue(rig.source.readNow(server("pc")).ok)
        assertEquals(listOf("node", "bash"), rig.ports.single().started.map { it.first })
    }

    @Test
    fun `B-AGENT-072 no Node anywhere is needs Node 20`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(connectError = true), FakeStep(output = listOf("node: command not found\r\n")))))
        assertEquals(ComputerUsageProblem.NodeMissing, rig.source.readNow(server("pc")).problem)
        assertEquals(ComputerUsageProblem.NodeMissing, rig.source.problemFor("pc"))
    }

    @Test
    fun `B-AGENT-072 the reader saying Node is too old is kept as that reason`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(output = listOf("${line(answer(error = "nodeTooOld"))}\r\n")))))
        assertEquals(ComputerUsageProblem.NodeTooOld, rig.source.readNow(server("pc")).problem)
        assertEquals(ComputerUsageProblem.NodeTooOld, rig.source.problemFor("pc"))
    }

    @Test
    fun `B-AGENT-072 a server without the terminal API says so and does not retry`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(startError = ComputerUsageProblem.ServerTooOld))))
        assertEquals(ComputerUsageProblem.ServerTooOld, rig.source.readNow(server("pc")).problem)
        assertEquals(1, rig.ports.single().started.size)
    }

    @Test
    fun `B-AGENT-072 an unreachable server does not retry either`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(startError = ComputerUsageProblem.Unreachable))))
        assertEquals(ComputerUsageProblem.Unreachable, rig.source.readNow(server("pc")).problem)
        assertEquals(1, rig.ports.single().started.size)
        assertEquals(1, rig.ports.single().closed)
    }

    @Test
    fun `B-AGENT-072 the terminal is removed even when removing it throws`() = runTest {
        val port = FakeComputerPort(listOf(FakeStep(output = listOf("${line(CODEX_OK)}\r\n")))).apply { removeThrows = true }
        val result = runComputerUsageCheck(port, "SCRIPT", { testScheduler.currentTime })
        assertTrue(result.ok)
        assertEquals(listOf("pty_1"), port.removed)
    }

    @Test
    fun `B-AGENT-072 a socket that never closes does not keep the terminal alive`() = runTest {
        val port = FakeComputerPort(listOf(FakeStep(output = listOf("${line(CODEX_OK)}\r\n"), closeHangs = true)))
        val result = runComputerUsageCheck(port, "SCRIPT", { testScheduler.currentTime })
        assertTrue(result.ok)
        assertEquals(listOf("pty_1"), port.removed)
    }

    // ---- B-AGENT-074 cadence

    @Test
    fun `B-AGENT-074 background reads wait 15 minutes, a forced read never waits`() = runTest {
        val rig = rig(ok)
        rig.source.read(context(background = true))
        assertEquals(1, rig.ports.size)
        rig.now = COMPUTER_NOW + 14 * MIN
        rig.source.read(context(background = true, force = false, now = COMPUTER_NOW + 14 * MIN))
        assertEquals(1, rig.ports.size)
        rig.source.read(context(background = true, force = false, now = COMPUTER_NOW + 16 * MIN))
        assertEquals(2, rig.ports.size)
        rig.source.read(context(background = true, force = true))
        assertEquals(3, rig.ports.size)
    }

    @Test
    fun `B-AGENT-074 opening Usage in the foreground reads at most once a minute`() = runTest {
        val rig = rig(ok)
        rig.source.read(context(force = false))
        assertEquals(1, rig.ports.size)
        rig.source.read(context(force = false, now = COMPUTER_NOW + 30_000))
        assertEquals(1, rig.ports.size)
        rig.source.read(context(force = false, now = COMPUTER_NOW + 90_000))
        assertEquals(2, rig.ports.size)
    }

    // ---- B-AGENT-075 what the app learns

    @Test
    fun `B-AGENT-075 Codex, GLM and MiniMax become readings with the plan ref`() = runTest {
        val rig = rig(
            listOf(
                listOf(
                    FakeStep(
                        output = listOf(
                            line(
                                answer(
                                    providers = mapOf(
                                        "codex" to snapshot("codex", ref = REF, plan = "plus"),
                                        "glm" to snapshot("glm", ref = REF2, accountStatus = "sourceBound"),
                                        "minimax" to snapshot("minimax", ref = REF2, accountStatus = "sourceBound"),
                                    ),
                                    auth = mapOf("codex" to "oauth", "glm" to "api", "minimax" to "api"),
                                ),
                            ) + "\r\n",
                        ),
                    ),
                ),
            ),
        )
        val result = rig.source.read(context())
        assertEquals(setOf("codex", "glm", "minimax"), result.readings.map { it.providerId }.toSet())
        val codex = result.readings.first { it.providerId == "codex" }
        assertEquals("pc", codex.connectionId)
        assertEquals(REF, codex.accountRef)
        assertEquals("plus", codex.plan)
        assertEquals(1, codex.windows.size)
        assertTrue(result.signIns.all { it.method == SignInMethod.Plan })
        assertEquals(listOf(QuotaProvider.Codex, QuotaProvider.Minimax, QuotaProvider.Glm), rig.source.foundOn("pc"))
    }

    @Test
    fun `B-AGENT-075 an OpenAI API key is a pay-as-you-go sign-in with no reading`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(output = listOf(line(answer(auth = mapOf("codex" to "api"))) + "\r\n")))))
        val result = rig.source.read(context())
        assertTrue(result.readings.isEmpty())
        val signIn = result.signIns.single()
        assertEquals("codex", signIn.providerId)
        assertEquals(SignInMethod.ApiKey, signIn.method)
        assertTrue(rig.source.foundOn("pc").isEmpty())
    }

    @Test
    fun `B-AGENT-075 a MiniMax pay-as-you-go key is the same, a plan key is not`() = runTest {
        val rig = rig(
            listOf(
                listOf(
                    FakeStep(
                        output = listOf(
                            line(
                                answer(
                                    providers = mapOf("minimax" to snapshot("minimax", status = "unsupported", ref = REF)),
                                    auth = mapOf("minimax" to "api"),
                                ),
                            ) + "\r\n",
                        ),
                    ),
                ),
            ),
        )
        val result = rig.source.read(context())
        assertEquals(SignInMethod.ApiKey, result.signIns.single().method)
        assertTrue(result.readings.isEmpty())
    }

    @Test
    fun `B-AGENT-075 an expired sign-in, a rate limit and a broken answer are states`() = runTest {
        val rig = rig(
            listOf(
                listOf(
                    FakeStep(
                        output = listOf(
                            line(
                                answer(
                                    providers = mapOf(
                                        "codex" to snapshot("codex", status = "authRequired"),
                                        "glm" to snapshot("glm", status = "rateLimited"),
                                        "minimax" to snapshot("minimax", status = "invalidResponse"),
                                    ),
                                    auth = mapOf("codex" to "oauth", "glm" to "api", "minimax" to "api"),
                                ),
                            ) + "\r\n",
                        ),
                    ),
                ),
            ),
        )
        val result = rig.source.read(context())
        assertEquals(UsageReadingStatus.AuthRequired, result.readings.single().status)
        assertEquals(
            setOf("glm" to UsageProblem.RateLimited, "minimax" to UsageProblem.Broken),
            result.failures.map { it.providerId to it.problem }.toSet(),
        )
    }

    @Test
    fun `B-AGENT-075 signed out now, but read here before, is not signed in`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(output = listOf(line(answer()) + "\r\n")))))
        val before = UsageReading("glm", "GLM", "pc", UsageReadingStatus.Ok, COMPUTER_NOW)
        val result = rig.source.read(context(lastReading = { c, p -> if (c == "pc" && p == "glm") before else null }))
        assertEquals(UsageReadingStatus.NotSignedIn, result.readings.single().status)
        assertEquals("glm", result.readings.single().providerId)
    }

    @Test
    fun `B-AGENT-075 an unreachable server marks the connection offline`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(startError = ComputerUsageProblem.Unreachable))))
        val result = rig.source.read(context())
        assertEquals(setOf("pc"), result.unreached)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `B-AGENT-075 a broken reader fails the plans signed in there, no others`() = runTest {
        val rig = rig(listOf(listOf(FakeStep(connectError = true), FakeStep(output = listOf("node: command not found\r\n")))))
        val result = rig.source.read(context(signedIn = { c -> if (c == "pc") setOf("codex", "copilot") else emptySet() }))
        assertEquals(listOf("codex"), result.failures.map { it.providerId })
        assertEquals(UsageProblem.Broken, result.failures.single().problem)
        assertTrue(result.unreached.isEmpty())
    }

    @Test
    fun `B-AGENT-075 nothing the person owns is kept in memory beyond the numbers`() = runTest {
        val rig = rig(ok)
        rig.source.readNow(server("pc"))
        val dump = listOf(
            rig.source.problemFor("pc").toString(),
            rig.source.foundOn("pc").toString(),
            rig.switches.keys().joinToString(","),
        ).joinToString(" ")
        assertFalse("SCRIPT" in dump)
    }
}
