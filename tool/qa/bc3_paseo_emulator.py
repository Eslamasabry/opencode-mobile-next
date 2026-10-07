#!/usr/bin/env python3
"""BC3 isolated Android/PRoot proof; never installs an APK or changes the app.

The native mode compiles the real PhoneAgentHost and disk policy with only the
Linux transport stubbed, then runs controlled child processes on Android.
"""

import argparse
import hashlib
import json
import pathlib
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import uuid


REPO = pathlib.Path(__file__).resolve().parents[2]
SDK = pathlib.Path.home() / "Android/Sdk"
LOCK = "/home/eslam/Storage/tmp/oc-emulator.lock"
DART = (pathlib.Path.home() / ".shorebird/bin/cache/flutter/"
        "91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart")
PACKAGE = "io.github.eslamasabry.opencode_mobile"


def run(argv, *, timeout=180):
    result = subprocess.run(argv, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {result.stdout}{result.stderr}")
    return result.stdout


def adb(*args):
    return run(["flock", LOCK, "adb", "-s", "emulator-5554", *args], timeout=60)


def android_tools():
    tools = sorted(SDK.glob("build-tools/*/d8"), key=lambda p: p.parent.name)
    platforms = sorted(SDK.glob("platforms/android-*/android.jar"),
                       key=lambda p: tuple(int(part) for part in
                                           p.parent.name.removeprefix("android-").split(".")))
    if not tools or not platforms or not shutil.which("kotlinc"):
        raise RuntimeError("Kotlin compiler / Android D8 / Android platform is unavailable")
    return tools[-1], platforms[-1]


def native_probe(output):
    native_dir = REPO / "android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile"
    host_source = (native_dir / "PhoneAgentHost.kt").read_text()
    setup_source = (native_dir / "SetupRunner.kt").read_text()
    match = re.search(r"(?ms)^(?:internal )?object SetupDiskSpace\s*\{.*?^\}", setup_source)
    if not match:
        raise RuntimeError("Production SetupDiskSpace object is not available")
    policy = match.group(0)
    d8, platform = android_tools()
    scratch = "/data/local/tmp/oc-bc3-native-" + uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix="oc-bc3-native-") as local:
        directory = pathlib.Path(local)
        kotlin = directory / "SetupDiskSpace.kt"
        kotlin.write_text("package io.github.eslamasabry.opencode_mobile\n"
                          "import java.io.File\n" + policy + "\n")
        host = directory / "PhoneAgentHost.kt"
        host.write_text(host_source)
        jar = directory / "native-proof.jar"
        dex_dir = directory / "dex"
        dex_dir.mkdir()
        # Use Android's real Build class. The JVM os.kt stub is a Kotlin object
        # and would generate Build.INSTANCE calls unsupported by the boot class.
        run(["kotlinc", str(kotlin), str(host),
             str(REPO / "test/native/phone_agent_host_stubs/linux.kt"),
             str(REPO / "test/native/phone_agent_host_harness.kt"),
             "-classpath", str(platform), "-jvm-target", "1.8",
             "-include-runtime", "-d", str(jar)])
        run([str(d8), "--min-api", "26", "--lib", str(platform),
             "--output", str(dex_dir), str(jar)])
        adb("shell", "mkdir", "-p", scratch + "/tmp")
        try:
            adb("push", str(dex_dir / "classes.dex"), scratch + "/classes.dex")
            results = []
            for scenario in ("exited-at-once", "healthy-launch", "low-space-launch", "concurrent-start"):
                command = ["app_process", "-Djava.io.tmpdir=" + scratch + "/tmp",
                           "/system/bin",
                           "io.github.eslamasabry.opencode_mobile.Phone_agent_host_harnessKt",
                           scenario]
                results.append(adb("shell", "CLASSPATH=" + shlex.quote(scratch + "/classes.dex") +
                                   " " + shlex.join(command)))
            result = "".join(results)
            metadata = ("Emulator: emulator-5554; Android app_process; real PhoneAgentHost\n"
                        f"PhoneAgentHost source SHA256: {hashlib.sha256(host_source.encode()).hexdigest()}\n"
                        f"SetupDiskSpace source SHA256: {hashlib.sha256(policy.encode()).hexdigest()}\n"
                        "Isolation: controlled Linux transport; real Android Build and child process runtime\n")
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(metadata + result)
            print(result, end="")
        finally:
            adb("shell", "rm", "-rf", scratch)


def generated_check(source, directory, label):
    library = directory / (label + "-scripts.dart")
    library.write_text(source)
    generator = directory / (label + "-generate.dart")
    generator.write_text("import 'dart:convert';\nimport 'dart:io';\n"
                         f"import '{library.as_uri()}';\n"
                         "void main() { stdout.write(jsonEncode({"
                         "'check': PaseoPhoneScripts.check,"
                         "'directory': PaseoPhoneScripts.installDirectory,"
                         "'version': PaseoPhoneScripts.version})); }\n")
    return json.loads(run([str(DART), "--packages=" + str(REPO / ".dart_tool/package_config.json"),
                           str(generator)]))


def shell_probe(output, baseline_ref):
    source_path = "lib/builtin/agents/paseo_scripts.dart"
    baseline_source = run(["git", "-C", str(REPO), "show", f"{baseline_ref}:{source_path}"])
    fixed_source = (REPO / source_path).read_text()
    if baseline_source == fixed_source:
        raise RuntimeError("Baseline and fixed source are identical; select a pre-BC3 revision")
    scratch = "/data/local/tmp/oc-bc3-shell-" + uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix="oc-bc3-shell-") as local:
        directory = pathlib.Path(local)
        baseline = generated_check(baseline_source, directory, "baseline")
        fixed = generated_check(fixed_source, directory, "fixed")
        if baseline["directory"] != fixed["directory"]:
            raise RuntimeError("Baseline and fixed package locations differ")
        for label, data in (("baseline", baseline), ("fixed", fixed)):
            (directory / (label + ".sh")).write_text(data["check"])
        fake_body = ("import { writeFileSync } from 'node:fs';\n"
                     "writeFileSync('/bc3-qa/executed', 'yes');\n"
                     "console.log(process.argv.includes('--version') ? " +
                     json.dumps(fixed["version"]) +
                     " : 'Usage: paseo daemon run [options]\\n  --home <path>');\n")
        (directory / "fake-wrapper").write_text("#!/usr/bin/env node\n" + fake_body)
        (directory / "fake-index.js").write_text(fake_body)
        fake_import, _, fake_actions = fake_body.partition("\n")
        (directory / "fake-run.js").write_text(fake_import + "\nexport async function runCli() {\n" +
                                                 fake_actions + "return 0;\n}\n")
        installed = adb("shell", "cmd", "package", "path", PACKAGE).strip()
        apk = installed.removeprefix("package:")
        if "\n" in apk or not apk.startswith("/data/app/") or not apk.endswith("/base.apk"):
            raise RuntimeError("Unexpected installed package path")
        native = apk.removesuffix("/base.apk") + "/lib/x86_64"
        rootfs = f"/data/user/0/{PACKAGE}/files/linux/ubuntu"
        host_dir = fixed["directory"]
        targets = {
            "wrapper": ("fake-wrapper", host_dir + "/node_modules/@getpaseo/cli/bin/paseo"),
            "entry payload": ("fake-index.js", host_dir + "/node_modules/@getpaseo/cli/dist/index.js"),
            "implementation": ("fake-run.js", host_dir + "/node_modules/@getpaseo/cli/dist/run.js"),
        }
        adb("shell", "mkdir", "-p", scratch + "/tmp", scratch + "/home")
        try:
            for name in ("baseline.sh", "fixed.sh", "fake-wrapper", "fake-index.js", "fake-run.js"):
                adb("push", str(directory / name), scratch + "/" + name)
            adb("shell", "chmod", "755", scratch + "/fake-wrapper")
            environment = (f"PROOT_LOADER={shlex.quote(native + '/libproot-loader.so')} "
                           f"PROOT_TMP_DIR={shlex.quote(scratch + '/tmp')} "
                           f"LD_LIBRARY_PATH={shlex.quote(native)} ")

            def probe(label, replacement=None):
                adb("shell", "rm", "-f", scratch + "/executed")
                argv = [native + "/libproot.so", "--change-id=1000:1000", "--kill-on-exit",
                        "--link2symlink", "-L", "--sysvipc", "--rootfs=" + rootfs,
                        "--bind=/dev", "--bind=/proc", "--bind=/sys",
                        "--bind=" + scratch + ":/bc3-qa", "--bind=" + scratch + "/tmp:/tmp"]
                if replacement:
                    name, target = replacement
                    argv.append("--bind=" + scratch + "/" + name + ":" + target)
                argv += ["--cwd=/bc3-qa", "/usr/bin/env", "-i", "HOME=/bc3-qa/home",
                         "PATH=/home/oc/.local/node/bin:/usr/bin:/bin", "LANG=C.UTF-8",
                         "TMPDIR=/bc3-qa/tmp", "/bin/sh", "/bc3-qa/" + label + ".sh"]
                result = subprocess.run(["flock", LOCK, "adb", "-s", "emulator-5554", "shell",
                                         environment + shlex.join(argv)], text=True,
                                        capture_output=True, timeout=60)
                # CLI stderr/stdout can contain provider data. Do not log either;
                # assert only the bounded expected version projection on success.
                if result.returncode == 0:
                    if result.stdout.strip() != fixed["version"]:
                        raise RuntimeError("Unexpected version projection; raw CLI output withheld")
                marker = adb("shell", "if [ -e " + shlex.quote(scratch + "/executed") +
                             " ]; then echo yes; else echo no; fi").strip() == "yes"
                return result.returncode, marker

            healthy_rc, healthy_marker = probe("fixed")
            if healthy_rc != 0 or healthy_marker:
                raise RuntimeError("Genuine installed Paseo failed production pins/version/help checks")
            lines = ["PASS genuine installed Paseo: exact pins, full code tree, version and --home help\n"]
            for label, replacement in targets.items():
                old_rc, old_marker = probe("baseline", replacement)
                new_rc, new_marker = probe("fixed", replacement)
                if old_rc != 0 or not old_marker:
                    raise RuntimeError(f"Baseline did not accept the same-version replaced {label}")
                if new_rc == 0 or new_marker:
                    raise RuntimeError(f"Fixed verifier executed or accepted replaced {label}")
                lines.append(f"PASS replaced {label}: baseline exit={old_rc}, executes spoof; "
                             f"fixed exit={new_rc}, rejects before CLI execution\n")
            final_rc, final_marker = probe("fixed")
            if final_rc != 0 or final_marker:
                raise RuntimeError("Genuine installed Paseo changed after scratch binding tests")
            lines.append("PASS genuine installed Paseo remains intact after all scratch bindings\n")
            metadata = ("Emulator: emulator-5554; installed Ubuntu/PRoot agent uid 1000\n"
                        f"Baseline ref: {baseline_ref}\n"
                        f"Baseline check SHA256: {hashlib.sha256(baseline['check'].encode()).hexdigest()}\n"
                        f"Fixed check SHA256: {hashlib.sha256(fixed['check'].encode()).hexdigest()}\n"
                        "Isolation: replaced files are scratch PRoot bindings; empty HOME; no daemon/account access\n")
            output.parent.mkdir(parents=True, exist_ok=True)
            result = "".join(lines)
            output.write_text(metadata + result)
            print(result, end="")
        finally:
            adb("shell", "rm", "-rf", scratch)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-only", action="store_true")
    parser.add_argument("--baseline-ref", default="HEAD")
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--locked", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.output is None:
        args.output = REPO / "docs/qa/BC3-2026-10-07" / (
            "native-emulator.log" if args.native_only else "emulator.log")
    if not args.locked:
        argv = [str(REPO / "tool/qa/machine_lock.sh"), "test", "--",
                sys.executable, str(pathlib.Path(__file__).resolve()),
                "--locked", "--output", str(args.output), "--baseline-ref", args.baseline_ref]
        if args.native_only:
            argv.append("--native-only")
        print(run(argv), end="")
        return
    if args.native_only:
        native_probe(args.output)
    else:
        shell_probe(args.output, args.baseline_ref)


if __name__ == "__main__":
    main()
