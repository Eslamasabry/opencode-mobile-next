package io.github.eslamasabry.oc.engine.runtime.setup.registry

import io.github.eslamasabry.oc.engine.runtime.components.SetupPrelude
import io.github.eslamasabry.oc.engine.runtime.install.OpenCodePins
import io.github.eslamasabry.oc.engine.runtime.setup.FakeSetupHost
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.runtime.setup.SetupEngine
import io.github.eslamasabry.oc.engine.runtime.setup.SetupJobParams
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPlanning
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.runtime.setup.StepClock
import io.github.eslamasabry.oc.engine.runtime.setup.fixtureWords
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The ported first-setup registry (lib/builtin/setup/components.dart, test/setup_components_test.dart). */
class PhoneSetupRegistryTest {
    private val words =
        PhoneSetupRegistryWords(
            linuxTitle = "Linux base",
            essentialsTitle = "Git, curl and SSH",
            essentialsShort = "Git and SSH",
            startTitle = "Start OpenCode",
        )
    private val registry = PhoneSetupRegistry(words)
    private val all = registry.components(emptyMap())

    @Test
    fun `B-LINUX-200 the real registry lists every dependency before its dependent with Flutter's ids and flags`() {
        assertEquals(listOf("linux", "essentials", "python", "node", "opencode", "start"), all.map { it.id })
        val seen = mutableSetOf<String>()
        for (component in all) {
            assertTrue(seen.containsAll(component.dependsOn), "${component.id} comes before what it needs")
            seen += component.id
        }
        assertEquals(setOf("linux", "essentials", "node", "opencode", "start"), all.filter { it.required }.map { it.id }.toSet())
        assertEquals(listOf("python"), all.filter { it.defaultOn }.map { it.id })
        assertTrue(all.single { it.id == "linux" }.native)
        assertTrue(all.single { it.id == "start" }.jobStep)
        assertEquals(listOf(15, 60, 40, 20, 100, 15), all.map { it.estimatedSeconds })
        assertEquals(listOf(30_000_000L, 45_000_000L, 25_000_000L, 58_000_000L, 50_000_000L, null), all.map { it.downloadBytes })
    }

    @Test
    fun `B-LINUX-200 the first setup selects the required and default components and expands in order`() {
        val ids = PhoneSetupRegistry.defaultSelection(all)
        assertEquals(setOf("linux", "essentials", "python", "node", "opencode", "start"), ids)
        assertEquals(all.map { it.id }, SetupPlanning.expandSelection(all, ids).map { it.id })
        // "~208 MB to download", "Includes Git and SSH, Python and Node.js."
        assertEquals(208_000_000L, PhoneSetupRegistry.downloadBytes(all))
        assertEquals(250, PhoneSetupRegistry.estimatedSeconds(all))
        assertEquals(listOf("Git and SSH", "Python", "Node.js"), PhoneSetupRegistry.includedNames(all))
    }

    @Test
    fun `B-LINUX-111 the in-app OpenCode install picks the pinned archive for this CPU and checks the native program`() {
        val opencode = all.single { it.id == SetupComponentIds.OPENCODE }
        for (pin in listOf(OpenCodePins.v2Arm64, OpenCodePins.v2X64)) {
            assertTrue(opencode.installScript.contains("oc_url='${pin.url}'; oc_sha=${pin.sha256}"), pin.url)
        }
        assertTrue(opencode.installScript.contains("aarch64|arm64)"))
        assertTrue(opencode.installScript.contains("x86_64|amd64)"))
        assertFalse(opencode.installScript.contains("npm install -g") || opencode.installScript.contains("OC_PROOT_SETUP"), "never npm in the app's own Ubuntu")
        assertTrue(opencode.checkScript.contains("= '/opt/opencode2/bin/opencode2' ]"))
        assertTrue(opencode.checkScript.contains("[ \"\$installed\" = '2.2.5' ]"))
        // An unpinned or malformed version is refused by the install, never a crash of the registry.
        val other = registry.components(mapOf("opencode" to mapOf("version" to "9.9.9"))).single { it.id == "opencode" }
        assertTrue(other.installScript.contains("This app can only install its own pinned OpenCode"))
        val bad = registry.components(mapOf("opencode" to mapOf("version" to "1; rm -rf /"))).single { it.id == "opencode" }
        assertTrue(bad.installScript.contains("exit 64"))
        assertEquals("exit 1\n", bad.checkScript)
    }

    @Test
    fun `B-LINUX-114 Node downloads the pinned artifact per ABI and only the pinned Node passes its check`() {
        val node = all.single { it.id == SetupComponentIds.NODE }
        assertTrue(node.checkScript.contains("[ \"\$(node --version)\" = 'v24.21.0' ]"))
        assertTrue(node.installScript.contains("aarch64|arm64) node_arch=arm64; node_sha=724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5 ;;"))
        assertTrue(node.installScript.contains("x86_64|amd64) node_arch=x64; node_sha=6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff ;;"))
        assertTrue(node.installScript.contains("oc_stage 'Downloading Node.js 24.21.0'\n"))
        assertTrue(
            node.installScript.contains(
                "oc_download \"https://nodejs.org/dist/\$node_version/node-\$node_version-linux-\$node_arch.tar.gz\" \\\n" +
                    "  \"\$node_file\" \"\$node_sha\"\n",
            ),
        )
    }

    @Test
    fun `B-LINUX-181 the essentials and Python install through the repairing apt helper, verbatim`() {
        val essentials = all.single { it.id == SetupComponentIds.ESSENTIALS }
        assertEquals(
            "set -eu\noc_apt_install curl ca-certificates git openssh-client\noc_version \"\$(git --version | cut -d ' ' -f 3)\"\n",
            essentials.installScript,
        )
        assertEquals(
            "set -eu\noc_apt_install python3 python3-venv python3-pip\noc_version \"\$(python3 --version | cut -d ' ' -f 2)\"\n",
            all.single { it.id == SetupComponentIds.PYTHON }.installScript,
        )
        assertEquals(
            "[ -s /etc/os-release ] && . /etc/os-release && echo \"\${VERSION%% *}\"",
            all.single { it.id == SetupComponentIds.LINUX }.checkScript,
        )
        assertTrue(SetupPrelude.withSetupPrelude("").contains("oc_apt_install()"))
    }

    @Test
    fun `B-LINUX-195 resuming with the real registry skips what passes and hands the rest with the prelude`() = runTest {
        val host = FakeSetupHost()
        host.checks["linux"] = true to "24.04"
        host.checks["essentials"] = true to "2.43.0"
        host.checks["python"] = true to "3.12.3"
        host.checks["node"] = true to "24.21.0"
        val prelude = SetupPrelude.withSetupPrelude("")
        val engine =
            SetupEngine(
                host = host,
                registryPort = registry,
                words = fixtureWords,
                scope = backgroundScope,
                clock = StepClock(),
                scriptPrelude = prelude,
                redact = { it },
                pollInterval = 5.milliseconds,
            )
        engine.run(PhoneSetupRegistry.defaultSelection(registry.components(SetupJobParams.firstSetup)), SetupJobParams.firstSetup)
        assertEquals(all.map { it.id }, host.ids())
        assertEquals(listOf(true, true, true, true, false, false), host.started.last().components.map { it.skipped })
        assertEquals("24.21.0", host.spec("node").version)
        val opencode = host.spec("opencode")
        assertTrue(opencode.script!!.startsWith(prelude))
        assertTrue(opencode.script!!.endsWith(all.single { it.id == "opencode" }.installScript))
        assertTrue(host.spec("start").step)
        assertNull(host.spec("start").script)
        assertTrue(SetupJobParams.isFirstSetup(host.started.last().params))
        assertEquals(SetupState.Running, engine.progress.value.state)
        engine.dispose()
    }
}
