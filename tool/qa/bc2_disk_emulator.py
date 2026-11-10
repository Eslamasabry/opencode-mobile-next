#!/usr/bin/env python3
"""Run the production SetupDiskSpace policy on emulator-5554's Android VM.

Extracts only the portable Kotlin object, compiles it in the shared build lock,
and executes a tiny disk-policy proof under the emulator lock. No APK changes,
rootfs mutations, disk filling, or app credentials are involved.
"""

import argparse
import hashlib
import pathlib
import re
import shlex
import shutil
import subprocess
import tempfile
import uuid


REPO = pathlib.Path(__file__).resolve().parents[2]
SDK = pathlib.Path.home() / "Android/Sdk"
LOCK = "/home/eslam/Storage/tmp/oc-emulator.lock"


def run(argv, *, timeout=180):
    result = subprocess.run(argv, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {result.stdout}{result.stderr}")
    return result.stdout


def adb(*args):
    return run(["flock", LOCK, "adb", "-s", "emulator-5554", *args], timeout=60)


MAIN = r'''
package io.github.eslamasabry.opencode_mobile

import java.io.File

private class SpaceFile(path: String, var bytes: Long) : File(path) {
    override fun exists() = true
    override fun isDirectory() = true
    override fun getAbsoluteFile(): File = this
    override fun getCanonicalFile(): File = this
    override fun getUsableSpace() = bytes
}

private fun guardedWrite(directory: File, required: Long, marker: File): String? {
    val error = SetupDiskSpace.error(directory, required)
    if (error == null) marker.writeText("allowed")
    return error
}

fun main(args: Array<String>) {
    val scratch = File(args.single())
    check(scratch.isDirectory)
    val marker = File(scratch, "mutation-marker")
    val install = SetupDiskSpace.MIN_INSTALL_BYTES
    val launch = SetupDiskSpace.MIN_LAUNCH_BYTES
    check(install > 0 && launch > 0)
    for ((label, minimum) in listOf("install" to install, "launch" to launch)) {
        for (bytes in listOf(0L, minimum - 1L)) {
            val simulated = SpaceFile(scratch.path, bytes)
            check(!marker.exists())
            val error = guardedWrite(simulated, minimum, marker)
            check(error != null)
            check(!error.contains("ENOSPC"))
            check(!marker.exists())
            println("PASS $label available=$bytes required=$minimum: $error; no mutation")
        }
        check(guardedWrite(SpaceFile(scratch.path, minimum), minimum, marker) == null)
        check(marker.readText() == "allowed")
        check(marker.delete())
        println("PASS $label exact threshold allows work")
    }
    val changing = SpaceFile(scratch.path, install)
    check(guardedWrite(changing, install, marker) == null)
    check(marker.delete())
    changing.bytes = install - 1
    check(guardedWrite(changing, install, marker) != null)
    check(!marker.exists())
    println("PASS fresh guard sees healthy-to-low change before next mutation")

    val missing = File(scratch, "not-created/deeper/leaf")
    check(!missing.exists())
    check(scratch.usableSpace >= install)
    check(SetupDiskSpace.error(scratch, install) == null)
    check(SetupDiskSpace.error(missing, install) == null)
    check(!File(scratch, "not-created").exists())
    println("PASS real Android filesystem and nearest existing ancestor; no leaf creation")

    check(SetupDiskSpace.requiredInstallBytes(0) >= install)
    check(SetupDiskSpace.requiredInstallBytes(Long.MAX_VALUE) == Long.MAX_VALUE)
    println("PASS download size overflow saturates instead of allowing an undersized guard")
    println("PASS: Android production disk policy blocks low space before work and allows healthy work")
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path,
                        default=REPO / "docs/qa/BC2-2026-10-07/emulator.log")
    args = parser.parse_args()
    source_file = (REPO / "android/app/src/main/kotlin/io/github/eslamasabry/"
                   "opencode_mobile/SetupRunner.kt")
    source = source_file.read_text()
    match = re.search(r"(?ms)^(?:internal )?object SetupDiskSpace\s*\{.*?^\}", source)
    if not match:
        raise RuntimeError("Production SetupDiskSpace object is not ready")
    policy = match.group(0)
    tools = sorted(SDK.glob("build-tools/*/d8"), key=lambda p: p.parent.name)
    platforms = sorted(SDK.glob("platforms/android-*/android.jar"),
                       key=lambda p: tuple(int(part) for part in
                                           p.parent.name.removeprefix("android-").split(".")))
    if not tools or not platforms or not shutil.which("kotlinc"):
        raise RuntimeError("Kotlin compiler / Android D8 / Android platform is unavailable")
    scratch = "/data/local/tmp/oc-bc2-" + uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix="oc-bc2-") as local:
        directory = pathlib.Path(local)
        kotlin = directory / "SetupDiskSpace.kt"
        kotlin.write_text("package io.github.eslamasabry.opencode_mobile\n"
                          "import java.io.File\n" + policy + "\n")
        main_file = directory / "DiskProof.kt"
        main_file.write_text(MAIN)
        jar = directory / "disk-proof.jar"
        dex_dir = directory / "dex"
        dex_dir.mkdir()
        build_lock = [str(REPO / "tool/qa/machine_lock.sh"), "build", "--"]
        run(build_lock + ["kotlinc", str(kotlin), str(main_file), "-jvm-target", "1.8",
                          "-include-runtime", "-d", str(jar)])
        run(build_lock + [str(tools[-1]), "--min-api", "26", "--lib",
                          str(platforms[-1]), "--output", str(dex_dir), str(jar)])
        adb("shell", "mkdir", "-p", scratch)
        try:
            adb("push", str(dex_dir / "classes.dex"), scratch + "/classes.dex")
            command = ["dalvikvm", "-cp", scratch + "/classes.dex",
                       "io.github.eslamasabry.opencode_mobile.DiskProofKt", scratch]
            result = adb("shell", shlex.join(command))
            metadata = ("Emulator: emulator-5554; Android dalvikvm production policy\n"
                        f"Production SetupDiskSpace SHA256: {hashlib.sha256(policy.encode()).hexdigest()}\n"
                        "Isolation: deterministic usableSpace override and real scratch directory; no disk filling\n")
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(metadata + result)
            print(result)
        finally:
            adb("shell", "rm", "-rf", scratch)


if __name__ == "__main__":
    main()
