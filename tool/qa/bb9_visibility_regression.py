#!/usr/bin/env python3
"""Pure BB9 JVM red controls; run under machine_lock.sh test -- python3 <file>.

The coordinator must freeze the two temporarily mutated native sources. This
script restores their original bytes in finally and never invokes Gradle/ADB.
"""
from pathlib import Path
import hashlib
import os
import re
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile"
TEST = ROOT / "android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile"
EVIDENCE = ROOT / "docs/qa/BB9-2026-10-07"
PACKAGE = "io.github.eslamasabry.opencode_mobile."
JDK = Path("/home/eslam/Storage/tmp/codex-audit3-temurin17/jdk-17.0.20.1+1")
KOTLIN = Path("/home/eslam/.sdkman/candidates/kotlin/2.3.20")
CACHE = Path("/home/eslam/.gradle/caches/modules-2/files-2.1")
JUNIT = CACHE / "junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar"
HAMCREST = CACHE / "org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar"
CLASSES = ("NativeInstallerVisibilityTest", "NativeInstallerOwnershipTest")
MUTATIONS = (
    ("integration_missing_gone", "NativeInstallerOwnership.kt",
     "        NativeInstallerVisibility.requireMissingGone(\n"
     "            listOfNotNull(ticket.ownership.root, ticket.ownership.leader) + ticket.observed,\n"
     "            inventory, absenceConfirmed)", "        Unit",
     "NativeInstallerOwnershipTest", "missingRecordedProcessMustBeIndependentlyConfirmedGone"),
    ("policy_absence_proof", "NativeInstallerVisibility.kt", "            require(gone) { SAFE }", "            Unit",
     "NativeInstallerVisibilityTest", "aliveOrUnreadableMissingProcessRefuses"),
    ("policy_reused_pid", "NativeInstallerVisibility.kt",
     "            require(visible == null || identity.sameProcess(visible)) { SAFE }", "            Unit",
     "NativeInstallerVisibilityTest", "reusedPidRefusesBeforeAnyOtherMissingIdentityIsProbed"),
    ("policy_unique_inventory", "NativeInstallerVisibility.kt", "        require(current.size == inventory.size) { SAFE }",
     "        Unit", "NativeInstallerVisibilityTest", "duplicateInventoryPidsRefuseEvenWithIdenticalEntries"),
)
RUNNER = """import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.Test
fun main(args: Array<String>) {
    val result = if (args.size == 2 && !args[1].contains('.')) {
        val type = Class.forName(args[0])
        check(type.getDeclaredMethod(args[1]).getAnnotation(Test::class.java) != null)
        JUnitCore().run(Request.method(type, args[1]))
    } else {
        JUnitCore().run(Request.classes(*args.map { Class.forName(it) }.toTypedArray()))
    }
    println("RESULT run=${result.runCount} failures=${result.failureCount} successful=${result.wasSuccessful()}")
    if (!result.wasSuccessful()) kotlin.system.exitProcess(1)
}
"""


def require(condition, code):
    if not condition:
        raise RuntimeError(code)


def record(path, line):
    with path.open("a") as output:
        output.write(line + "\n")
    print(line, flush=True)


class Checks:
    def __init__(self, directory):
        self.directory = directory
        self.jar = directory / "visibility-tests.jar"
        self.runner = directory / "Bb9VisibilityRunner.kt"
        self.runner.write_text(RUNNER)
        self.environment = os.environ.copy()
        for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS", "KOTLIN_RUNNER"):
            self.environment.pop(key, None)
        self.environment["JAVA_HOME"] = str(JDK)
        self.files = [MAIN / (name + ".kt") for name in
                      ("NativeRuntimeOwnership", "NativeInstallerVisibility", "NativeInstallerOwnership")]
        self.files += [TEST / (name + ".kt") for name in CLASSES] + [self.runner]

    def invoke(self, command, timeout):
        # Compiler/test output stays private and is bounded before interpretation.
        result = subprocess.run(command, cwd=ROOT, env=self.environment, capture_output=True,
                                text=True, timeout=timeout)
        require(len(result.stdout.encode()) + len(result.stderr.encode()) <= 65536, "jvm_output_overflow")
        return result

    def compile(self):
        result = self.invoke([str(KOTLIN / "bin/kotlinc"), "-J-Xmx256m", "-J-XX:MaxMetaspaceSize=192m",
                              "-jvm-target", "17", "-cp", str(JUNIT), *map(str, self.files),
                              "-d", str(self.jar)], 120)
        require(result.returncode == 0, "pure_kotlin_compile_failed")

    def run(self, klass=None, method=None):
        arguments = [PACKAGE + klass, method] if klass else [PACKAGE + name for name in CLASSES]
        classpath = os.pathsep.join(map(str, (self.jar, JUNIT, HAMCREST, KOTLIN / "lib/kotlin-stdlib.jar")))
        result = self.invoke([str(JDK / "bin/java"), "-Xmx128m", "-XX:MaxMetaspaceSize=96m",
                              "-cp", classpath, "Bb9VisibilityRunnerKt", *arguments], 30)
        match = re.fullmatch(r"RESULT run=([0-9]+) failures=([0-9]+) successful=(true|false)\n?", result.stdout)
        require(match is not None, "junit_result_invalid")
        return result.returncode, int(match[1]), int(match[2]), match[3] == "true"


def main():
    paths = {name: MAIN / name for name in {mutation[1] for mutation in MUTATIONS}}
    originals = {name: path.read_bytes() for name, path in paths.items()}
    for artifact in (JDK / "bin/java", KOTLIN / "bin/kotlinc", JUNIT, HAMCREST, KOTLIN / "lib/kotlin-stdlib.jar"):
        require(artifact.is_file(), "pinned_jvm_artifact_missing")
    for _, name, before, _, _, _ in MUTATIONS:
        require(originals[name].decode().count(before) == 1, "mutation_guard_not_unique")
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    red = EVIDENCE / "visibility-jvm-red.txt"
    restored = EVIDENCE / "visibility-jvm-restored.txt"
    red.write_text("BB9 visibility removed-fix controls; pure Kotlin/JUnit; machine test lock.\n")
    restored.write_text("BB9 visibility restored focused gate; pure Kotlin/JUnit; machine test lock.\n")
    record(red, "toolchain=Temurin17.0.20.1+1 Kotlin2.3.20 JUnit4.13.2 compilerHeapMiB=256 testHeapMiB=128")
    with tempfile.TemporaryDirectory(prefix="bb9-visibility-jvm-") as directory:
        checks = Checks(Path(directory))
        failure = None
        try:
            for label, name, before, after, klass, method in MUTATIONS:
                print("RUN red=" + label, flush=True)
                try:
                    paths[name].write_bytes(originals[name].replace(before.encode(), after.encode(), 1))
                    checks.compile()
                    code, count, failures, successful = checks.run(klass, method)
                    record(red, f"control={label} test={klass}.{method} exit={code} run={count} failures={failures}")
                    require(code == 1 and count == 1 and failures == 1 and not successful, "removed_fix_not_observed")
                finally:
                    paths[name].write_bytes(originals[name])
        except BaseException as error:
            failure = error
            record(red, "controls_complete=false")
        finally:
            for name, path in paths.items():
                path.write_bytes(originals[name])
                require(path.read_bytes() == originals[name], "source_restoration_failed")
                record(restored, f"source={name} originalBytesRestored=true sha256={hashlib.sha256(originals[name]).hexdigest()}")
            print("RUN restored focused classes", flush=True)
            checks.compile()
            code, count, failures, successful = checks.run()
            record(restored, f"classes={','.join(CLASSES)} exit={code} run={count} failures={failures}")
            require(code == 0 and count > 0 and failures == 0 and successful, "restored_focused_gate_failed")
        if failure is not None:
            raise failure
    record(red, "PASS controls=4 expectedRegressionFailures=4")
    record(restored, "PASS originalSourceBytes=true focusedClasses=2")


def restored_only():
    """Repeat only an interrupted/restored green gate without mutating sources."""
    path = EVIDENCE / "visibility-jvm-restored.txt"
    paths = [MAIN / name for name in sorted({mutation[1] for mutation in MUTATIONS})]
    originals = {source: source.read_bytes() for source in paths}
    record(path, "Restored-only gate; temporary runner class-selection correction; no source mutations.")
    with tempfile.TemporaryDirectory(prefix="bb9-visibility-jvm-") as directory:
        checks = Checks(Path(directory))
        checks.compile()
        code, count, failures, successful = checks.run()
        record(path, f"classes={','.join(CLASSES)} exit={code} run={count} failures={failures}")
        require(code == 0 and count > 0 and failures == 0 and successful, "restored_focused_gate_failed")
    for source, original in originals.items():
        require(source.read_bytes() == original, "restored_source_changed_during_gate")
        record(path, f"source={source.name} originalBytesRestored=true sha256={hashlib.sha256(original).hexdigest()}")
    record(path, "PASS originalSourceBytes=true focusedClasses=2")
    red = EVIDENCE / "visibility-jvm-red.txt"
    if red.is_file():
        recorded = red.read_text()
        if all(f"control={mutation[0]} " in recorded for mutation in MUTATIONS):
            record(red, "PASS controls=4 expectedRegressionFailures=4 restoredGatePassed=true")


if __name__ == "__main__":
    require(sys.argv[1:] in ([], ["--restored-only"]), "unsupported_arguments")
    restored_only() if sys.argv[1:] else main()
