package io.github.eslamasabry.oc.engine.termuxhost

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** test/termux_scripts_test.dart, run with the real bash like the Flutter test. */
class TermuxScriptsTest {
    private val manager = TermuxScripts.managerScript

    @AfterTest
    fun cleanUp() = Shell.cleanUp()

    private fun assertHas(text: String, needle: String) = assertTrue(text.contains(needle), "missing: $needle")

    private fun assertLacks(text: String, needle: String) = assertFalse(text.contains(needle), "unexpected: $needle")

    private fun between(text: String, start: String, end: String): String = text.substring(text.indexOf(start), text.indexOf(end))

    // ---- B-LINUX-227 setup scripts parse in the shell

    @Test
    fun `B-LINUX-227 generated termux scripts pass bash syntax validation`() {
        val scripts =
            mapOf(
                "install.sh" to TermuxScripts.installAndServeScript(port = 4096, password = "test-password"),
                "install-opencode2.sh" to
                    TermuxScripts.installAndServeScript(password = "test-password", runtime = TermuxRuntime.OpenCode2),
                "manager.sh" to manager,
                "restart.sh" to TermuxScripts.restartScript(port = 4096, operationId = "test-1"),
                "diagnostics.sh" to TermuxScripts.diagnosticsScript(),
                "snapshot.sh" to TermuxScripts.setupSnapshotScript(),
                "status.sh" to TermuxScripts.statusScript(),
                "installation.sh" to TermuxScripts.installationScript(),
                "wake-lock.sh" to TermuxScripts.ensureWakeLockScript,
                "unlock.sh" to TermuxScripts.unlockCommand,
                "stop.sh" to TermuxScripts.stopScript(port = 4096),
            )
        for ((name, text) in scripts) {
            val result = Shell.syntax("bash", text)
            assertEquals(0, result.exitCode, "$name: ${result.stderr}")
        }
    }

    // ---- B-LINUX-242 setup clock and status

    @Test
    fun `B-LINUX-242 install dispatch starts its clock only after accepting the launch`() {
        val script = TermuxScripts.installAndServeScript(password = "test-password")
        val dispatch = script.substring(script.indexOf("\nOC_MANAGER_EOF\n"))
        val clock = dispatch.indexOf("started_at=\$(date +%s)")
        assertTrue(clock > dispatch.indexOf("trap cleanup_dispatch EXIT"))
        assertTrue(clock > dispatch.lastIndexOf("manager-already-running:"))
        assertTrue(clock < dispatch.indexOf("printf 'phase=queued"))
        assertHas(dispatch, "started_at=%s")
        assertEquals(1, dispatch.split("\$(date +%s)").size - 1)
    }

    @Test
    fun `B-LINUX-242 manager clock survives phase writes and a fresh status process`() {
        val result = Shell.managerProbe(Shell.resource("clock-survives.sh"))
        assertEquals(0, result.exitCode, result.toString())
        val status = TermuxSetupStatus.parse(result.stdout)!!
        assertEquals("failed", status.phase)
        assertEquals(100L, status.startedAtEpochSeconds)
    }

    @Test
    fun `B-LINUX-242 legacy manager state stays without an invented clock`() {
        val result = Shell.managerProbe(Shell.resource("clock-legacy.sh"))
        assertEquals(0, result.exitCode, result.toString())
        assertNull(TermuxSetupStatus.parse(result.stdout)!!.startedAtEpochSeconds)
    }

    @Test
    fun `B-LINUX-242 restart renews the clock only after launch rejection gates`() {
        val result = Shell.managerProbe(Shell.resource("clock-restart.sh"))
        assertEquals(0, result.exitCode, result.toString())
    }

    @Test
    fun `B-LINUX-242 runtime selection survives phase writes and a fresh status process`() {
        val result = Shell.managerProbe(Shell.resource("runtime-survives.sh"))
        assertEquals(0, result.exitCode, result.toString())
        val status = TermuxSetupStatus.parse(result.stdout)!!
        assertEquals(TermuxRuntime.OpenCode2, status.runtime)
        assertEquals(100L, status.startedAtEpochSeconds)
        assertEquals("failed", status.phase)
    }

    @Test
    fun `B-LINUX-242 live snapshot converts manager errors into a failed status`() {
        val script = TermuxScripts.setupSnapshotScript()
        assertHas(script, "Could not read setup manager status")
        assertHas(script, "manager_error=\"\$manager_output\"")
        assertHas(script, "[ -f \"\$OC_DIR/server-log.active\" ]")
    }

    @Test
    fun `B-LINUX-242 unexpected manager errors persist the active setup stage`() {
        assertHas(manager, "stage=\$(read_state_value message)")
        assertHas(manager, "write_state failed \"\$stage failed (exit \$code; setup line \$line)\"")
        assertHas(manager, "fail_setup 'Could not complete the safe Termux package upgrade'")
    }

    // ---- B-LINUX-243 setup script safety

    @Test
    fun `B-LINUX-243 restart validates ownership and reuses the installed server`() {
        val script = TermuxScripts.restartScript(port = 4096, operationId = "test-1")
        val restart = between(manager, "restart() {", "\nstatus() {")
        val verifiedStop = between(manager, "stop_verified_server_process() {", "\nstop_verified_setup_process() {")
        assertHas(script, "\"\$MANAGER\" restart '4096' 'test-1' &")
        assertHas(script, "another-managed-operation-is-running")
        assertLacks(script, "server.password.tmp")
        assertHas(restart, "ubuntu_usable")
        assertHas(restart, "The tracked process is not the managed")
        assertTrue(
            verifiedStop.indexOf("server_process \"\$pid\" \"\$port\"") < verifiedStop.indexOf("kill -KILL -- \"-\$group\""),
        )
        assertHas(verifiedStop, "process_start \"\$pid\"")
        assertHas(verifiedStop, "kill -STOP \"\$pid\"")
        assertHas(restart, "/dev/tcp/127.0.0.1/\$CURRENT_PORT")
        assertHas(restart, "start_server \"\$installed_version\" restarting")
        assertLacks(restart, "prepare_termux_dependencies")
        assertLacks(restart, "install_opencode")
        assertTrue(restart.indexOf("write_state restarting") < restart.indexOf("ubuntu_usable ||"))
        assertHas(manager, "operation_result=%s")
        assertHas(manager, "not_performed")
        assertHas(manager, "[ \"\${args[1]:-}\" = \"\$MANAGER\" ]")
        assertHas(manager, "[ \"\${args[2]:-}\" = restart ]")
    }

    @Test
    fun `B-LINUX-243 restart operation ids cannot inject shell syntax`() {
        assertFailsWith<IllegalArgumentException> { TermuxScripts.restartScript(operationId = "bad'; echo injected") }
        assertFailsWith<IllegalArgumentException> { TermuxScripts.restartScript(operationId = "") }
    }

    @Test
    fun `B-LINUX-243 manager is installed before the single-flight lock is acquired`() {
        val script = TermuxScripts.installAndServeScript(port = 4096, password = "test-password")
        val managerInstall = script.indexOf("cat > \"\$manager_tmp\"")
        val lockAcquire = script.indexOf("if mkdir \"\$LOCK\"")
        val passwordWrite = script.indexOf("printf '%s' 'test-password'")
        assertTrue(managerInstall >= 0)
        assertTrue(lockAcquire > managerInstall)
        assertTrue(passwordWrite > lockAcquire)
        assertHas(script, "\$LOCK/owner")
        assertLacks(script, "ln \"\$lock_candidate\"")
        assertHas(script, "if [ -f \"\$LOCK\" ]")
        assertHas(script, "\"\$\$\" \"\$self_start\"")
        assertHas(script, "\"\$MANAGER\" rotate-log install")
        assertHas(script, "> >(\"\$MANAGER\" write-log install) 2>&1")
        assertHas(script, "rm -f \"\$OC_DIR/server-log.active\"")
    }

    @Test
    fun `B-LINUX-243 managed server URL detection is narrow`() {
        assertTrue(TermuxScripts.managesServerUrl("http://127.0.0.1:4096"))
        assertFalse(TermuxScripts.managesServerUrl("http://localhost:4096"))
        assertFalse(TermuxScripts.managesServerUrl("http://127.0.0.1:4747"))
        assertFalse(TermuxScripts.managesServerUrl("https://127.0.0.1:4096"))
    }

    @Test
    fun `B-LINUX-243 manager claims the dispatcher lock before package work`() {
        val claim = manager.indexOf("if ! claim_setup_lock")
        assertTrue(claim >= 0)
        assertTrue(manager.indexOf("termux-wake-lock") > claim)
    }

    @Test
    fun `B-LINUX-243 server owns the setup wake lock until it stops or exits`() {
        val cleanup = between(manager, "cleanup_setup() {", "ubuntu_rootfs_exists()")
        assertHas(cleanup, "termux-wake-unlock")
        assertTrue(cleanup.indexOf("termux-wake-unlock") > cleanup.indexOf("if [ \"\${SETUP_SUCCEEDED:-0}\" != 1 ]"))
        assertHas(cleanup, "if [ \"\${SERVER_STARTED:-0}\" = 1 ]")
        assertHas(manager, "\"\$manager\" server-exited \"\$port\" \"\$\$\" \"\$code\"")
        assertHas(manager, "server_exited() {")
        assertHas(manager, "server-exited) shift; server_exited \"\$@\" ;;")
        assertHas(manager, "stop() {")
        assertTrue(manager.split("termux-wake-unlock").size - 1 >= 4)
    }

    @Test
    fun `B-LINUX-243 wake lock refresh script is safe to invoke repeatedly`() {
        assertHas(TermuxScripts.ensureWakeLockScript, "termux-wake-lock")
        assertHas(TermuxScripts.ensureWakeLockScript, "opencode-server-wake-lock-held")
    }

    @Test
    fun `B-LINUX-243 setup reserves disk space and only cleans app-owned partial data`() {
        assertHas(manager, "DISK_RESERVE_KIB=524288")
        assertHas(manager, "FRESH_SETUP_REQUIRED_KIB=1572864")
        assertHas(manager, "require_setup_space")
        assertHas(manager, "df -Pk \"\$HOME\"")
        assertHas(manager, "including a \${reserve_mib} MiB safety reserve")
        assertHas(manager, "cleanup_app_owned_partial_install")
        assertHas(manager, "[ -f \"\$UBUNTU_INSTALL_MARKER\" ]")
        assertHas(manager, "proot-distro remove \"\$PROOT_NAME\"")
        assertLacks(manager, "rm -rf \"\$PREFIX/var/lib/proot-distro\"")
    }

    @Test
    fun `B-LINUX-243 unhealthy setup repairs packages without allowing removals`() {
        val health = manager.indexOf("termux_dependencies_healthy()")
        val prepare = manager.indexOf("prepare_termux_dependencies()")
        val prepareCall = manager.indexOf("\n  prepare_termux_dependencies\n")
        val ubuntu = manager.indexOf("write_state installing_ubuntu")
        assertTrue(health >= 0)
        assertTrue(prepare > health)
        assertTrue(prepareCall > prepare)
        assertTrue(ubuntu > prepareCall)
        assertHas(manager, "deb https://packages.termux.dev/apt/termux-main stable main")
        assertHas(manager, "\$source_file.oc-before-opencode")
        assertHas(manager, "DEBIAN_FRONTEND=noninteractive")
        assertHas(manager, "--no-remove")
        assertHas(manager, "--fix-broken install")
        assertHas(manager, "Dpkg::Options::=\"--force-confold\" upgrade")
        assertTrue(manager.indexOf("--fix-broken install") < manager.indexOf("Dpkg::Options::=\"--force-confold\" upgrade"))
        assertTrue(
            manager.indexOf("Dpkg::Options::=\"--force-confold\" upgrade") <
                manager.indexOf("install \\\n    proot-distro curl openssl"),
        )
        assertHas(manager, "curl --version >/dev/null 2>&1")
        assertLacks(manager, "full-upgrade")
        assertLacks(manager, "pkg install -y proot-distro curl")
    }

    @Test
    fun `B-LINUX-243 healthy custom repository skips apt and drains long named-container help`() {
        val directory = Shell.temp("oc-termux-health-")
        val home = File(directory, "home").apply { mkdirs() }
        val prefix = File(directory, "prefix").apply { mkdirs() }
        val apt = File(prefix, "etc/apt").apply { mkdirs() }
        val sources = File(apt, "sources.list").apply { writeText("deb https://healthy.example.test/termux-main stable main\n") }
        val bin = File(directory, "bin").apply { mkdirs() }
        File(bin, "curl").writeText("#!/usr/bin/env bash\nif [ \"\${1:-}\" = --version ]; then echo 'curl healthy'; exit 0; fi\nexit 64\n")
        File(bin, "proot-distro").writeText(
            "#!/usr/bin/env bash\nif [ \"\${1:-}\" = install ] && [ \"\${2:-}\" = --help ]; then\n  i=0\n" +
                "  while [ \"\$i\" -lt 10000 ]; do printf 'long help line %s\\n' \"\$i\"; i=\$((i + 1)); done\n" +
                "  printf '%s\\n' '  --name NAME'\n  exit 0\nfi\nexit 64\n",
        )
        val aptMarker = File(directory, "apt-called")
        File(bin, "apt-get").writeText("#!/usr/bin/env bash\n: > \"\$APT_MARKER\"\nexit 99\n")
        bin.listFiles()!!.forEach { it.setExecutable(true) }
        val functionPrefix = manager.substring(0, manager.indexOf("\ninstall_ubuntu_base() {"))
        val probe = File(directory, "probe.sh").apply { writeText("$functionPrefix\nCURRENT_PORT=4096\nprepare_termux_dependencies\n") }
        val result =
            Shell.bashFile(
                probe,
                env =
                    mapOf(
                        "HOME" to home.path,
                        "PREFIX" to prefix.path,
                        "APT_MARKER" to aptMarker.path,
                        "PATH" to "${bin.path}:${System.getenv("PATH") ?: ""}",
                    ),
            )
        assertEquals(0, result.exitCode, result.toString())
        assertHas(result.stdout, "package upgrade skipped")
        assertFalse(aptMarker.exists())
        assertEquals("deb https://healthy.example.test/termux-main stable main\n", sources.readText())
        assertFalse(File(sources.path + ".oc-before-opencode").exists())
    }

    @Test
    fun `B-LINUX-243 install and server logs are bounded and rotated by executable code`() {
        val directory = Shell.temp("oc-log-test-")
        val home = File(directory, "home").apply { mkdirs() }
        val managerFile = File(directory, "manager.sh").apply { writeText(manager) }
        val input = File(directory, "input.log")
        val line = "x".repeat(1023) + "\n"
        input.writeText(line.repeat(2300))
        val result =
            Shell.run(
                listOf("bash", "-c", "bash \"\$1\" write-log install < \"\$2\"", "_", managerFile.path, input.path),
                mapOf("HOME" to home.path),
            )
        assertEquals(0, result.exitCode, result.toString())
        val logs = File(home, ".oc").listFiles()!!.filter { it.isFile && it.name.contains("install.log") }
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.size <= 3)
        for (log in logs) assertTrue(log.length() <= 1048576, log.path)
    }

    @Test
    fun `B-LINUX-243 server runs through the bounded logger and stop validates its runner`() {
        assertHas(manager, "2>&1 | \"\$manager\" write-log server")
        assertHas(manager, "[ \"\${args[1]:-}\" = \"\$SERVER_RUNNER\" ]")
        assertHas(manager, "[ \"\${args[2]:-}\" = \"\$port\" ]")
        assertHas(manager, "printf '%s %s\\n' \"\$server_pid\" \"\$server_start\"")
        assertHas(manager, "group_is_managed_tree")
        assertHas(manager, "kill -KILL -- \"-\$group\"")
    }

    private fun processStart(pid: Long): String {
        val stat = File("/proc/$pid/stat").readText()
        return stat.substring(stat.lastIndexOf(") ") + 2).split(' ')[19]
    }

    @Test
    fun `B-LINUX-243 manager stops its verified process group but refuses a decoy`() {
        val home = Shell.temp("oc-manager-stop-")
        val oc = File(home, ".oc").apply { mkdirs() }
        val managerFile = File(oc, "manager.sh").apply { writeText(manager) }
        val runner = File(oc, "server-runner.sh").apply { writeText("#!/usr/bin/env bash\nsleep 60 &\nwait\n") }
        val password = File(oc, "server.password").apply { writeText("test") }
        managerFile.setExecutable(true)
        runner.setExecutable(true)
        val managed = ProcessBuilder("setsid", runner.path, "4096", password.path, managerFile.path).start()
        try {
            Thread.sleep(100)
            File(oc, "server.pid").writeText("${managed.pid()} ${processStart(managed.pid())}\n")
            val stopped = Shell.run(listOf("bash", managerFile.path, "stop", "4096"), mapOf("HOME" to home.path))
            assertEquals(0, stopped.exitCode, stopped.toString())
            assertTrue(managed.waitFor(2, java.util.concurrent.TimeUnit.SECONDS))
            assertNotEquals(0, managed.exitValue())
        } finally {
            Shell.run(listOf("kill", "-KILL", "--", "-${managed.pid()}"))
        }
        val decoy = ProcessBuilder("setsid", "sleep", "60").start()
        try {
            Thread.sleep(50)
            File(oc, "server.pid").writeText("${decoy.pid()} ${processStart(decoy.pid())}\n")
            val refused = Shell.run(listOf("bash", managerFile.path, "stop", "4096"), mapOf("HOME" to home.path))
            assertNotEquals(0, refused.exitCode)
            assertEquals(0, Shell.run(listOf("kill", "-0", "${decoy.pid()}")).exitCode)
        } finally {
            decoy.destroyForcibly().waitFor()
        }
    }

    @Test
    fun `B-LINUX-243 the managed server never runs from the container home folder`() {
        assertHas(manager, "mkdir -p /root/projects")
        assertHas(manager, "proot-distro login --work-dir /root/projects opencode-ubuntu -- env")
        assertLacks(manager, "proot-distro login opencode-ubuntu -- env")
    }

    @Test
    fun `B-LINUX-243 only app-owned partial Ubuntu installs can be removed`() {
        assertHas(manager, "UBUNTU_INSTALL_MARKER=\"\$OC_DIR/opencode-ubuntu-installing\"")
        assertHas(manager, "[ -f \"\$UBUNTU_INSTALL_MARKER\" ]")
        assertHas(manager, "[ -f \"\$UBUNTU_INSTALL_MARKER\" ] || ! ubuntu_usable")
        assertHas(manager, "setup will not delete it")
        assertHas(manager, "proot-distro remove \"\$PROOT_NAME\"")
        assertHas(manager, "Could not remove the interrupted app-owned Ubuntu install")
    }

    @Test
    fun `B-LINUX-243 only explicit manager launch markers are accepted`() {
        assertTrue(TermuxScripts.isLaunchAcknowledged("manager-started:123"))
        assertTrue(TermuxScripts.isLaunchAcknowledged("manager-already-running:456"))
        assertFalse(TermuxScripts.isLaunchAcknowledged(""))
        assertFalse(TermuxScripts.isLaunchAcknowledged("setup-lock-unavailable"))
    }

    @Test
    fun `B-LINUX-243 manager-missing stop validates the lock owner before clearing`() {
        val script = TermuxScripts.stopScript(port = 4096)
        assertHas(script, "read -r lock_pid lock_start")
        assertHas(script, "[ \"\$lock_start\" = \"\$live_start\" ]")
        assertHas(script, "[ \"\${args[1]:-}\" = \"\$OC_DIR/manager.sh\" ]")
        assertHas(script, "[ \"\${args[2]:-}\" = setup ]")
        assertHas(script, "[ \"\$stopped_start\" != \"\$lock_start\" ]")
        assertHas(script, "kill -KILL -- \"-\$pid\"")
        assertHas(script, "[ \"\$lock_owned\" = 1 ]")
    }

    @Test
    fun `B-LINUX-243 project folders are created only as one safe name under projects`() {
        val script = TermuxScripts.createProjectFolderScript("my-app")
        assertHas(script, "proot-distro login opencode-ubuntu -- sh -c")
        assertHas(script, "dir=\"/root/projects/\$name\"")
        assertHas(script, "-- 'my-app'")
        assertHas(script, "*/*|.*) echo \"invalid-folder-name\"")
    }

    // ---- B-LINUX-244 Ubuntu bootstrap

    @Test
    fun `B-LINUX-244 default setup installs the pinned OpenCode release`() {
        val script = TermuxScripts.installAndServeScript(port = 4096, password = "test-password")
        val pinned = TermuxScripts.DEFAULT_OPENCODE_VERSION
        assertTrue(Regex("^\\d+\\.\\d+\\.\\d+$").matches(pinned))
        assertHas(script, "setup '4096' '$pinned'")
        assertEquals(TermuxRuntime.DefaultForNewInstall.pinnedVersion, pinned)
        assertHas(manager, "opencode2) requested_version=$pinned")
        assertHas(script, "\"\$self_start\" 'opencode2'")
        assertHas(manager, "local main_package=@opencode/cli")
        assertHas(manager, "\"\$main_package@\$OC_REQUESTED_VERSION\"")
        assertHas(manager, "npm_cache=\$(mktemp -d /tmp/opencode-mobile-npm.XXXXXX)")
        assertHas(manager, "--cache \"\$npm_cache\"")
        assertHas(manager, "rm -rf -- \"\$npm_cache\"")
        assertHas(manager, "cleanup_legacy_npm_cache")
        assertHas(manager, "proot-distro login \"\$PROOT_NAME\" -- npm cache clean --force")
        assertLacks(manager, "opencode models --refresh")
        assertHas(manager, "refreshing_models")
    }

    @Test
    fun `B-LINUX-244 Ubuntu bootstrap disables io_uring before Node and npm run`() {
        val start = manager.indexOf("bash -s <<'OC_PROOT_SETUP'")
        val bootstrap = manager.substring(start, manager.indexOf("\nOC_PROOT_SETUP", start))
        val compatibility = bootstrap.indexOf("export UV_USE_IO_URING=0")
        assertTrue(compatibility >= 0)
        assertTrue(compatibility < bootstrap.indexOf("node -p"))
        assertTrue(compatibility < bootstrap.indexOf("npm install -g"))
    }

    @Test
    fun `B-LINUX-244 Ubuntu bootstrap trims recommendations but retains coding tools`() {
        val bootstrap = between(manager, "bash -s <<'OC_PROOT_SETUP'", "\ninstall_opencode() {")
        val joined = bootstrap.replace(Regex("\\\\\\r?\\n"), " ")
        val install = Regex("apt-get install[^\\r\\n]*").find(joined)!!.value
        val arguments = install.split(Regex("\\s+"))
        assertTrue("--no-install-recommends" in arguments)
        assertTrue("Acquire::Retries=5" in arguments)
        assertTrue(arguments.containsAll(listOf("nodejs", "npm", "curl", "ca-certificates", "git", "openssh-client")))
        for (command in listOf("node", "npm", "curl", "git", "ssh")) {
            assertHas(bootstrap, "! command -v $command >/dev/null 2>&1")
        }
        assertHas(bootstrap, "[ ! -s /etc/ssl/certs/ca-certificates.crt ]")
        assertHas(bootstrap, "apt-get update -y -o Acquire::Retries=5")
        assertLacks(bootstrap, "--allow-unauthenticated")
    }

    @Test
    fun `B-LINUX-244 OpenCode 2 readiness accepts the stable route and the beta one`() {
        assertHas(manager, "opencode2) health_path='/api/info /api/health' ;;")
        assertHas(manager, "for path in \$OC_HEALTH_PATH; do")
        assertHas(manager, "[ \"\$unauth \$auth\" != '401 200' ] || break")
    }

    @Test
    fun `B-LINUX-244 Ubuntu npm install requires its matching pinned binary and cleans cache`() {
        val block = between(manager, "install_opencode() {", "\ninstall_opencode ||")
        val directory = Shell.temp("oc-npm-test-")
        val calls = File(directory, "calls")
        val runtime = TermuxRuntime.OpenCode2
        val mainPackage = "@opencode/cli"
        for ((arch, npmExit, binary) in listOf(
            Triple("arm64", 0, "@opencode/cli-linux-arm64"),
            Triple("x64", 0, "@opencode/cli-linux-x64-baseline"),
            Triple("arm64", 7, "@opencode/cli-linux-arm64"),
            Triple("arm", 0, ""),
        )) {
            calls.writeText("")
            File(directory, "bin").mkdirs()
            File(directory, "oc2").deleteRecursively()
            File(directory, "bin/opencode2").delete()
            val script = Shell.resource("npm-prelude.sh") + block + "\ninstall_opencode\n"
            val result =
                Shell.bash(
                    script,
                    mapOf(
                        "MOCK_ARCH" to arch,
                        "MOCK_NPM_EXIT" to "$npmExit",
                        "NPM_CALLS" to calls.path,
                        "OC_RUNTIME" to runtime.wireName,
                        "OC_REQUESTED_VERSION" to runtime.pinnedVersion,
                        "OC2_PREFIX" to "${directory.path}/oc2",
                        "OC2_LINK_DIR" to "${directory.path}/bin",
                    ),
                )
            assertEquals(if (arch == "arm") 64 else npmExit, result.exitCode, result.toString())
            val arguments = calls.readLines()
            if (arch == "arm") {
                assertTrue(arguments.isEmpty())
                continue
            }
            assertTrue(
                arguments.containsAll(
                    listOf(
                        "install", "-g", "--include=optional", "--foreground-scripts", "--allow-scripts=$mainPackage",
                        "--fetch-retries=5", "--fetch-timeout=300000", "$binary@${runtime.pinnedVersion}",
                        "$mainPackage@${runtime.pinnedVersion}",
                    ),
                ),
                arguments.toString(),
            )
            val prefixAt = arguments.indexOf("--prefix")
            assertTrue(prefixAt >= 0 && arguments[prefixAt + 1] == "${directory.path}/oc2")
            assertFalse("uninstall" in arguments)
            if (npmExit == 0) {
                assertEquals(
                    "${directory.path}/oc2/bin/opencode2",
                    java.nio.file.Files.readSymbolicLink(File(directory, "bin/opencode2").toPath()).toString(),
                )
            }
            assertTrue(arguments.none { it.contains("musl") })
            assertFalse("--force" in arguments)
            val cache = arguments[arguments.indexOf("--cache") + 1]
            assertTrue(cache.startsWith("/tmp/opencode-mobile-npm."))
            assertFalse(File(cache).exists())
        }
    }

    @Test
    fun `B-LINUX-244 runtime version uses the recorded command and normalizes its prefix`() {
        val result = Shell.managerProbe(Shell.resource("runtime-version.sh"))
        assertEquals(0, result.exitCode, result.toString())
    }

    @Test
    fun `B-LINUX-244 installation inventory accepts only a single sanitized version`() {
        val absent = TermuxInstallation.parse("ubuntu=absent\nversion=\n")!!
        assertFalse(absent.ubuntuInstalled)
        assertNull(absent.openCodeVersion)
        val ubuntu = TermuxInstallation.parse("ubuntu=installed\nversion=\n")!!
        assertTrue(ubuntu.ubuntuInstalled)
        assertNull(ubuntu.openCodeVersion)
        assertEquals("1.18.29", TermuxInstallation.parse("ubuntu=installed\nversion=1.18.29\n")!!.openCodeVersion)
        for (output in listOf(
            "",
            "ubuntu=absent\nversion=1.18.29",
            "ubuntu=installed\nversion=not a version",
            "ubuntu=installed\nversion=1.18.29\nextra output",
        )) {
            assertNull(TermuxInstallation.parse(output), output)
        }
    }

    @Test
    fun `B-LINUX-244 installation inventory distinguishes absence from a failed probe`() {
        val directory = Shell.temp("oc-inventory-test-")
        val calls = File(directory, "calls")
        File(directory, "proot-distro").apply {
            writeText(Shell.resource("fake-proot-inventory.sh"))
            setExecutable(true)
        }
        val script = TermuxScripts.installationScript()
        fun probe(code: Int) =
            Shell.bash(
                script,
                mapOf(
                    "PREFIX" to directory.path,
                    "PROOT_CALLS" to calls.path,
                    "PATH" to "${directory.path}:${System.getenv("PATH")}",
                    "PROBE_EXIT" to "$code",
                ),
            )
        val absent = probe(0)
        assertEquals(0, absent.exitCode)
        assertFalse(TermuxInstallation.parse(absent.stdout)!!.ubuntuInstalled)
        assertFalse(calls.exists())
        File(directory, "var/lib/proot-distro/containers/opencode-ubuntu/rootfs").mkdirs()
        val installed = probe(0)
        assertEquals(0, installed.exitCode, installed.stderr)
        assertEquals("1.18.29", TermuxInstallation.parse(installed.stdout)!!.openCodeVersion)
        assertEquals(listOf("login", "opencode-ubuntu", "--", "bash", "-c"), calls.readLines())
        assertEquals(9, probe(9).exitCode)
    }

    @Test
    fun `B-LINUX-244 the npm dist-tag is reachable only by asking for it`() {
        assertEquals("latest", TermuxScripts.LATEST_OPENCODE_VERSION)
        val optedIn =
            TermuxScripts.installAndServeScript(port = 4096, password = "test-password", version = TermuxScripts.LATEST_OPENCODE_VERSION)
        assertHas(optedIn, "setup '4096' 'latest'")
        assertLacks(TermuxScripts.installAndServeScript(port = 4096, password = "p"), "'latest'")
    }

    @Test
    fun `B-LINUX-244 Ubuntu setup bypasses registries and verifies Canonical archives`() {
        assertLacks(manager, "proot-distro install ubuntu")
        assertHas(manager, "cdimage.ubuntu.com/ubuntu-base/releases/24.04")
        assertHas(manager, "ubuntu-base-24.04.5-base-arm64.tar.gz")
        assertHas(manager, "ubuntu-base-24.04.5-base-armhf.tar.gz")
        assertHas(manager, "ubuntu-base-24.04.5-base-amd64.tar.gz")
        assertHas(manager, "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2")
        assertHas(manager, "printf '%s  %s\\n' \"\$checksum\"")
        assertHas(manager, "sha256sum -c -")
        assertHas(manager, "proot-distro install \"\$archive\" --name \"\$PROOT_NAME\"")
    }

    @Test
    fun `B-LINUX-244 OpenCode Ubuntu is isolated in its own versioned container`() {
        assertHas(manager, "PROOT_NAME=opencode-ubuntu")
        assertHas(manager, "containers/\$PROOT_NAME/rootfs")
        assertHas(manager, "installed-rootfs/\$PROOT_NAME")
        assertHas(manager, "proot-distro login \"\$PROOT_NAME\" -- true")
        assertLacks(manager, "proot-distro login ubuntu")
        assertLacks(manager, "proot-distro remove ubuntu")
    }

    @Test
    fun `B-LINUX-244 npm setup prefers IPv4 and retries transient downloads`() {
        assertHas(manager, "--dns-result-order=ipv4first")
        assertHas(manager, "--fetch-retries=5")
        assertHas(manager, "--fetch-timeout=300000")
        assertHas(manager, "install_opencode ||")
        assertHas(manager, "retrying in 10 seconds")
    }

    // ---- B-LINUX-230 OpenCode 2 is the only installable runtime

    @Test
    fun `B-LINUX-230 manager setup defaults only fresh state to OC2`() {
        val result = Shell.managerProbe(Shell.resource("setup-runtime.sh"))
        assertEquals(0, result.exitCode, result.toString())
    }

    @Test
    fun `B-LINUX-230 direct Ubuntu setup only selects OC2 and refuses legacy requests`() {
        val selection = between(manager, "# Only OpenCode 2 is installable.", "\ninstall_opencode() {")
        for (runtime in listOf("", "opencode2", "opencode1")) {
            val result =
                Shell.run(
                    listOf("bash", "-c", "$selection\nprintf \"%s\" \"\$OC_RUNTIME\""),
                    mapOf("OC_RUNTIME" to runtime, "PATH" to (System.getenv("PATH") ?: "")),
                )
            assertEquals(if (runtime == "opencode1") 64 else 0, result.exitCode, result.toString())
            if (runtime != "opencode1") assertEquals("opencode2", result.stdout)
        }
    }

    @Test
    fun `B-LINUX-230 fresh OC2 default cannot silently migrate an installation`() {
        val script = TermuxScripts.installAndServeScript(password = "test-password")
        val guard = script.substring(script.indexOf("\nold_runtime="), script.indexOf("\npassword_tmp="))
        assertHas(script, "setup '4096' '2.2.5'")
        assertHas(script, "\"\$self_start\" 'opencode2'")
        assertTrue(script.indexOf(guard) > script.indexOf("trap cleanup_dispatch EXIT"))
        data class Case(val runtime: String, val version: String, val exit: Int)
        for (case in listOf(
            Case("", "", 0),
            Case("opencode1", "", 64),
            Case("", "1.18.29", 64),
            Case("opencode2", "0.0.0-beta-18600", 0),
        )) {
            val directory = Shell.temp("oc-runtime-guard-")
            if (case.runtime.isNotEmpty()) File(directory, "runtime").writeText(case.runtime)
            File(directory, "state").writeText("version=${case.version}\n")
            val result =
                Shell.run(
                    listOf("bash", "-c", "set -eu\n$guard"),
                    mapOf("OC_DIR" to directory.path, "PATH" to (System.getenv("PATH") ?: "")),
                )
            assertEquals(case.exit, result.exitCode, result.toString())
            val recorded = File(directory, "runtime")
            assertEquals(if (case.exit == 0) "opencode2" else case.runtime, if (recorded.exists()) recorded.readText() else "")
        }
    }

    @Test
    fun `B-LINUX-230 runtime inventory preserves legacy default and explicit beta choice`() {
        val legacy = TermuxInstallation.parse("ubuntu=installed\nversion=1.18.29\n")!!
        assertEquals(TermuxRuntime.OpenCode1, legacy.runtime)
        assertFalse(legacy.runtimeSelected)
        val beta = TermuxInstallation.parse("ubuntu=installed\nversion=0.0.0-beta-18600\nruntime=opencode2\n")!!
        assertEquals(TermuxRuntime.OpenCode2, beta.runtime)
        assertTrue(beta.runtimeSelected)
        assertEquals("0.0.0-beta-18600", beta.openCodeVersion)
        val queued = TermuxInstallation.parse("ubuntu=absent\nversion=\nruntime=opencode2\n")!!
        assertFalse(queued.ubuntuInstalled)
        assertTrue(queued.runtimeSelected)
        assertEquals(TermuxRuntime.OpenCode1, TermuxSetupStatus.parse("phase=ready")!!.runtime)
        assertNull(TermuxSetupStatus.parse("phase=ready\nruntime=unknown"))
        for (suffix in listOf("runtime=unknown", "runtime=opencode2\nextra=1")) {
            assertNull(TermuxInstallation.parse("ubuntu=installed\nversion=\n$suffix"))
        }
    }

    @Test
    fun `B-LINUX-230 legacy setup requests fail before generating any install script`() {
        assertFailsWith<IllegalArgumentException> {
            TermuxScripts.installAndServeScript(password = "test-only", runtime = TermuxRuntime.OpenCode1)
        }
        assertEquals("opencode1_migration_required", TermuxRuntime.OpenCode1.supportProblem()?.code)
        assertEquals(TermuxRuntime.OpenCode1, TermuxRuntime.parse(null))
        assertEquals(TermuxRuntime.OpenCode1, TermuxRuntime.parse(""))
    }
}
