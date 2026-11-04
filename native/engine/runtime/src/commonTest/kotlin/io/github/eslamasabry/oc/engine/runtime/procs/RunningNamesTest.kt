package io.github.eslamasabry.oc.engine.runtime.procs

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Port of test/running_on_phone_screen_test.dart 'rows use plain names for the kernel names seen on a phone' and
 * test/running_work_sheet_test.dart 'a command is named by what it runs, not where it lives'.
 */
class RunningNamesTest {
    private fun kind(name: String, command: String) = runningNameKind(proc(1, 0, name, command = command))

    @Test
    fun `B-LINUX-180 rows use plain names for the kernel names seen on a phone`() {
        // Read from a device: Node's kernel name is its main thread's, the server binary is "opencode2", Paseo
        // renames its processes.
        assertEquals(RunningNameKind.OpenCode, kind("MainThread", "/opt/node/bin/node /root/.oc-genui/openCode2/server.cjs"))
        assertEquals(RunningNameKind.AgentHelper, kind("MainThread", "node /home/oc/.local/bin/paseo daemon run"))
        assertEquals(RunningNameKind.OpenCode, kind("opencode2", "opencode2 serve --port 4097"))
        assertEquals(RunningNameKind.AgentHelper, kind("Paseo Superviso", "Paseo Supervisor"))
        assertEquals(RunningNameKind.AgentHelper, kind("Paseo Daemon", "Paseo Daemon"))
        assertEquals(RunningNameKind.Linux, kind("libproot.so", "libproot.so --root-id"))
    }

    @Test
    fun `B-LINUX-180 a command is named by what it runs, not where it lives`() {
        assertEquals(
            "flutter analyze && flutter test --concurrency=1",
            CommandNames.shortCommand(
                "/tmp/opencode/flutter/bin/flutter analyze && /tmp/opencode/flutter/bin/flutter test --concurrency=1",
            ),
        )
        assertEquals(
            "uvicorn main:app --host 0.0.0.0",
            CommandNames.shortCommand("FINANCEHUB_DB=/tmp/opencode/demo.db ./.venv/bin/uvicorn main:app --host 0.0.0.0"),
        )
        assertEquals("ls -la | grep foo", CommandNames.shortCommand("ls -la | grep foo"))
        // Arguments that are paths are left alone.
        assertEquals("cat /etc/hosts", CommandNames.shortCommand("cat /etc/hosts"))
        assertEquals("FOO=1", CommandNames.shortCommand("FOO=1"))
    }
}
