package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Owner 2026-11-14 (a long agent turn on the phone): eleven command rows that all read `cd <project>; . <toolchain>/env.sh;
 * …` cut by an ellipsis, so they read the same. A command is judged by what ran after its set-up, and a tool row is
 * named in words, never by an id like `select:TaskStop`. The command shapes below are the owner's, with made-up paths.
 */
class ShellIntentTest {
    private fun kind(command: String): ShellKind = shellIntent(command).kind

    @Test
    fun `STEPS-CRAFT-002 the owner's command shapes say what ran after the cd and the sourced toolchain`() {
        val cd = "cd /home/dev/projects/demo_app; . /home/dev/projects/.toolchain/env.sh;"
        assertEquals(ShellKind.Tests, kind("$cd flutter test test/player_test.dart 2>&1 | tail -30"))
        assertEquals(ShellKind.Build, kind("$cd ./gradlew :app:assembleDebug -q 2>&1 | tail -5"))
        assertEquals(ShellKind.Tests, kind("$cd ./gradlew :app:testDebugUnitTest"))
        assertEquals(ShellKind.Analyze, kind("$cd flutter analyze lib/"))
        assertEquals(ShellKind.StopProcess, kind("cd /home/dev/projects/demo_app; pkill -f flutter_tester 2>/dev/null; echo done"))
        assertEquals(ShellKind.Processes, kind("pgrep -af \"flutter_teste[r]\" | cut -c1-80 | head -3; echo ---"))
        assertEquals(ShellKind.Search, kind("cd /home/dev/projects/demo_app; grep -n \"crash\\|Crash\\|onError\" lib/player.dart | head -20"))
        assertEquals(ShellKind.CheckChanges, kind("cd /home/dev/projects/demo_app && git status --short"))
        // A task's output file named by an id is "a file", never the id.
        assertEquals(ShellKind.ReadFile, kind("cat /tmp/agent-1000/-home-dev-projects-demo-app/acfb6579-2c1d-4e8a.output"))
    }

    @Test
    fun `STEPS-CRAFT-002 a read names its one file, a pipe counts its first program, set-up and quiet words give way`() {
        assertEquals(ShellIntent(ShellKind.ReadOne, "player.dart"), shellIntent("sed -n '1,80p' lib/player.dart"))
        assertEquals(ShellIntent(ShellKind.ReadOne, "notes.md"), shellIntent("tail -n 40 docs/notes.md"))
        assertEquals(ShellKind.ReadMany, kind("head -5 a.txt b.txt"))
        assertEquals(ShellIntent(ShellKind.Program, "adb"), shellIntent("export PATH=\$HOME/bin:\$PATH; adb devices"))
        assertEquals(ShellIntent(ShellKind.Program, "check_strings.py"), shellIntent("python3 tool/check_strings.py --all"))
        assertEquals(ShellKind.Tests, kind("ANDROID_HOME=/opt/sdk ./gradlew testDebugUnitTest"))
        assertEquals(ShellKind.Tests, kind("bash -lc 'cd app && npm test'"))
        assertEquals(ShellKind.Tests, kind("timeout 300 python -m pytest -q"))
        assertEquals(ShellKind.Install, kind("npm ci"))
        assertEquals(ShellKind.Delete, kind("rm -rf build/tmp"))
        assertEquals(ShellKind.Fetch, kind("curl -s https://example.test/health"))
        assertEquals(ShellKind.Wait, kind("sleep 30"))
        assertEquals(ShellKind.Search, kind("echo searching; rg TODO src"))
        assertEquals(ShellKind.Unknown, kind(""))
        assertEquals(ShellKind.Unknown, shellIntent(null).kind)
        // A program whose name is an id is "a command".
        assertEquals(ShellKind.Unknown, kind("./a1b2c3d4e5f6"))
    }

    @Test
    fun `STEPS-CRAFT-002 a line splits at semicolons, and, or and background marks, never inside quotes or at 2 and 1`() {
        assertEquals(listOf("a", "b", "c", "d", "e"), shellSegments("a; b && c || d & e"))
        assertEquals(listOf("make 2>&1 | tail"), shellSegments("make 2>&1 | tail"))
        assertEquals(listOf("echo 'a; b'", "ls"), shellSegments("echo 'a; b'; ls"))
        assertEquals(listOf("grep x f", "head -3"), shellPipeline("grep x f | head -3"))
    }

    @Test
    fun `STEPS-CRAFT-003 a shell step carries its intent, a tool search names its tools in words, a stop is a phrase`() {
        val bash =
            toolFacts(
                "bash",
                ToolState(ToolStatus.Completed, input = JsonObject(mapOf("command" to JsonPrimitive("cd /p; npm test")))),
            )
        assertEquals(ShellKind.Tests, bash.shell?.kind)
        assertNull(toolFacts("read", ToolState(ToolStatus.Completed)).shell)

        val search =
            toolFacts(
                "ToolSearch",
                ToolState(ToolStatus.Completed, input = JsonObject(mapOf("query" to JsonPrimitive("select:TaskStop,Monitor")))),
            )
        assertEquals(ToolPhrase.LoadTools, search.phrase)
        assertNull(search.subject, "the query is never the row's mono subject")
        assertEquals("Task stop, Monitor", search.detail)
        assertEquals("Task stop", toolSearchWords("select:TaskStop"))
        assertEquals("notebook tools", toolSearchWords("notebook tools"))

        // Paseo lower-cases the id ("taskstop"): the stop still has its own words.
        assertEquals(ToolPhrase.StopBackgroundTask, toolFacts("taskstop", ToolState(ToolStatus.Completed)).phrase)
        assertEquals(ToolPhrase.StopBackgroundTask, toolFacts("TaskStop", ToolState(ToolStatus.Completed)).phrase)
        assertEquals(ToolPhrase.ReadTaskOutput, toolFacts("BashOutput", ToolState(ToolStatus.Completed)).phrase)

        // A server title that only repeats the id is not the server's words: the id reads as words instead.
        val repeat = toolFacts("render_mermaid_diagram", ToolState(ToolStatus.Completed, title = "Render mermaid diagram"))
        assertEquals(ToolPhrase.Generic, repeat.phrase)
        assertTrue(repeat.titleArgs.single().startsWith("Render mermaid"), repeat.titleArgs.toString())
    }
}
