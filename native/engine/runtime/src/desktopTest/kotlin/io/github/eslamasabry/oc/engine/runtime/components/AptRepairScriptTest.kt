package io.github.eslamasabry.oc.engine.runtime.components

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Port of test/setup_apt_repair_test.dart: the ported [SetupPrelude] runs `oc_apt_install` against fake apt-get,
 * dpkg and dpkg-query in real dash and bash. The fakes are the Flutter test's, verbatim (`§` stands for `$`).
 */
class AptRepairScriptTest {
    private lateinit var temporary: File
    private lateinit var environment: Map<String, String>
    private val log get() = File(environment.getValue("TEST_LOG"))

    private fun script(text: String) = text.replace("§", "$")

    @BeforeTest
    fun setUp() {
        temporary = Files.createTempDirectory("oc-apt-repair-").toFile()
        val bin = File(temporary, "bin").apply { mkdir() }
        val lists = File(temporary, "lists").apply { mkdir() }
        File(lists, "test_Packages").writeText("")
        File(temporary, "stamp").writeText("")
        File(bin, "dpkg").writeText(
            script(
                """#!/bin/sh
printf 'dpkg %s\n' "§*" >> "§TEST_LOG"
case "§1" in
  --configure)
    [ "§TEST_MODE" != repair ] || [ -f "§TEST_LOG.repaired" ] || exit 1
    [ "§TEST_MODE" != broken ] || exit 1 ;;
  --audit)
    if [ "§TEST_MODE" = broken ] || [ "§TEST_MODE" = audit ]; then
      echo 'The following packages are only half configured: test-package'
    fi
    case "§TEST_MODE" in
      reinstall*)
        if [ ! -f "§TEST_LOG.reinstalled" ] || [ "§TEST_MODE" = reinstall-still-broken ]; then
          echo 'The following packages are in a mess due to serious problems during installation.'
          echo 'They must be reinstalled for them (and any packages that depend on them) to function properly:'
          echo ' libonig5:amd64        regular expressions library'
          echo 'The following packages are missing the list control file in the database:'
          echo ' libonig5:amd64        regular expressions library'
          echo 'The following packages are missing the md5sums control file in the database:'
          echo ' libonig5:amd64        regular expressions library'
        fi ;;
    esac ;;
  -s)
    [ "§TEST_MODE" != missing ] || exit 1
    if [ "§TEST_MODE" = half ]; then
      echo 'Status: install ok half-configured'
    else
      echo 'Status: install ok installed'
    fi ;;
esac
""",
            ),
        )
        File(bin, "dpkg-query").writeText(
            script(
                """#!/bin/sh
printf 'dpkg-query %s\n' "§*" >> "§TEST_LOG"
case "§TEST_MODE" in
  reinstall-query-failed) exit 2 ;;
  reinstall-invalid-name)
    printf '%s\t%s\t%s\n' '--allow-remove-essential' 'install reinstreq half-installed' '6.9.9-1build1' ;;
  reinstall-invalid-version)
    printf '%s\t%s\t%s\n' 'libonig5:amd64' 'install reinstreq half-installed' '6.9.9;echo PRIVATE' ;;
  reinstall*)
    printf '%s\t%s\t%s\n' 'libonig5:amd64' 'install reinstreq half-installed' '6.9.9-1build1'
    printf '%s\t%s\t%s\n' 'healthy:amd64' 'install ok installed' '1.0' ;;
esac
""",
            ),
        )
        File(bin, "apt-get").writeText(
            script(
                """#!/bin/sh
printf 'apt %s\n' "§*" >> "§TEST_LOG"
case " §* " in
  *" --reinstall "*)
    [ "§TEST_MODE" != reinstall-failed ] || exit 100
    case "§TEST_MODE §*" in
      "reinstall-version-gone "*=6.9.9-1build1*) exit 100 ;;
    esac
    touch "§TEST_LOG.reinstalled" ;;
  *" -f "*)
    [ "§TEST_MODE" != broken ] || exit 100
    touch "§TEST_LOG.repaired" ;;
esac
""",
            ),
        )
        listOf("dpkg", "dpkg-query", "apt-get").forEach { File(bin, it).setExecutable(true, false) }
        environment =
            mapOf(
                "PATH" to "${bin.path}:/usr/bin:/bin",
                "OC_APT_STAMP" to File(temporary, "stamp").path,
                "OC_APT_LISTS" to lists.path,
                "TEST_LOG" to File(temporary, "calls").path,
            )
    }

    @AfterTest
    fun tearDown() {
        temporary.deleteRecursively()
    }

    private fun install(mode: String, shell: String): ShellRun =
        runShell(
            shell,
            "set -eu\n" + SetupPrelude.withSetupPrelude("oc_apt_install test-package\necho FINISHED"),
            environment + ("TEST_MODE" to mode),
        )

    /** dash (Ubuntu's /bin/sh, what the phone runs) and bash. Both are installed on the build machine. */
    private val shells: List<String>
        get() {
            val dash = shellOnPath("dash")
            assertNotNull(dash, "dash is needed: the phone runs these scripts with Ubuntu's /bin/sh (dash)")
            return listOf(dash, assertNotNull(shellOnPath("bash")))
        }

    private fun calls() = log.readLines()

    private fun reinstallLine(versioned: Boolean) =
        "apt -o APT::Status-Fd=3 --reinstall --no-remove install -y " +
            "--no-install-recommends -o Acquire::Retries=5 " +
            if (versioned) "libonig5:amd64=6.9.9-1build1" else "libonig5:amd64"

    private fun reset() {
        temporary.listFiles()?.filter { it.name.startsWith("calls") }?.forEach(File::delete)
    }

    // ---- B-LINUX-181

    @Test
    fun `B-LINUX-181 dash and bash reinstall an interrupted unpack before Python installation`() {
        for (shell in shells) {
            reset()
            val result = install("reinstall", shell)
            assertEquals(0, result.exitCode, "$shell\n${result.stdout}\n${result.stderr}")
            val calls = calls()
            val repair = calls.indexOfFirst { it.contains(" --reinstall ") }
            assertTrue(repair > 0, shell)
            assertEquals(reinstallLine(versioned = true), calls[repair], shell)
            assertEquals("dpkg --configure -a", calls[repair + 1], shell)
            assertEquals("dpkg --audit", calls[repair + 2], shell)
            assertEquals("dpkg -s test-package", calls.last(), shell)
            assertTrue(result.stdout.contains("missing the list control file"), shell)
            assertTrue(result.stdout.contains("missing the md5sums control file"), shell)
            assertTrue(result.stdout.contains("FINISHED"), shell)
        }
    }

    @Test
    fun `B-LINUX-181 dash and bash reinstall at the offered version when the recorded one is gone`() {
        // An upgrade killed mid-unpack records a version the archive no longer has.
        for (shell in shells) {
            reset()
            val result = install("reinstall-version-gone", shell)
            assertEquals(0, result.exitCode, "$shell\n${result.stdout}\n${result.stderr}")
            assertEquals(
                listOf(reinstallLine(versioned = true), reinstallLine(versioned = false)),
                calls().filter { it.contains(" --reinstall ") },
                shell,
            )
            assertTrue(result.stdout.contains("FINISHED"), shell)
        }
    }

    @Test
    fun `B-LINUX-181 dash and bash repair dependency interruption before installing`() {
        for (shell in shells) {
            reset()
            val result = install("repair", shell)
            assertEquals(0, result.exitCode, "$shell ${result.stderr}")
            val calls = calls()
            val repair = calls.indexOfFirst { it.startsWith("apt ") && it.contains(" -f ") }
            assertTrue(repair > 0, shell)
            assertEquals("dpkg --configure -a", calls[repair + 1], shell)
            assertEquals("dpkg -s test-package", calls.last(), shell)
            assertTrue(result.stdout.contains("FINISHED"), shell)
        }
    }

    // ---- B-LINUX-182

    @Test
    fun `B-LINUX-182 dash and bash refuse installation after an unsuccessful reinstall`() {
        for (shell in shells) {
            for (mode in listOf("reinstall-failed", "reinstall-still-broken")) {
                reset()
                val result = install(mode, shell)
                assertNotEquals(0, result.exitCode, "$shell $mode")
                assertTrue(result.stdout.contains("dpkg --audit"), "$shell $mode")
                assertTrue(result.stdout.contains("libonig5:amd64"), "$shell $mode")
                assertFalse(result.stdout.contains("FINISHED"), "$shell $mode")
                val calls = log.readText()
                assertTrue(calls.contains(" --reinstall --no-remove "), "$shell $mode")
                assertFalse(calls.contains("dpkg -s test-package"), "$shell $mode")
            }
        }
    }

    @Test
    fun `B-LINUX-182 dash and bash refuse untrusted repair targets`() {
        for (shell in shells) {
            for (mode in listOf("reinstall-query-failed", "reinstall-invalid-name", "reinstall-invalid-version")) {
                reset()
                val result = install(mode, shell)
                assertNotEquals(0, result.exitCode, "$shell $mode")
                assertTrue(result.stdout.contains("damaged packages could not be checked"), "$shell $mode")
                assertFalse(result.stdout.contains("FINISHED"), "$shell $mode")
                assertFalse(log.readText().contains("apt "), "$shell $mode")
            }
        }
    }

    @Test
    fun `B-LINUX-182 dash and bash refuse a failed repair and give dpkg audit details`() {
        for (shell in shells) {
            reset()
            val result = install("broken", shell)
            assertNotEquals(0, result.exitCode, shell)
            val all = "${result.stdout}\n${result.stderr}"
            assertTrue(all.contains("dpkg --audit"), shell)
            assertTrue(all.contains("half configured: test-package"), shell)
            assertFalse(result.stdout.contains("FINISHED"), shell)
        }
    }

    @Test
    fun `B-LINUX-182 dash and bash catch audit findings even when audit exits zero`() {
        for (shell in shells) {
            reset()
            val result = install("audit", shell)
            assertNotEquals(0, result.exitCode, shell)
            assertTrue("${result.stdout}\n${result.stderr}".contains("dpkg --audit"), shell)
            assertFalse(log.readText().contains("apt "), shell)
        }
    }

    @Test
    fun `B-LINUX-182 dash and bash verify dpkg status after apt claims success`() {
        for (shell in shells) {
            for (mode in listOf("missing", "half")) {
                reset()
                val result = install(mode, shell)
                assertNotEquals(0, result.exitCode, "$shell $mode")
                assertFalse(result.stdout.contains("FINISHED"), "$shell $mode")
                assertTrue(log.readText().contains("dpkg -s test-package"), "$shell $mode")
            }
        }
    }
}
