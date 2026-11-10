"""Final-pass live preparation; called only inside the runner's reservation."""

import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time

from tool.qa.bd7_device_ui import Bd7Ui
from tool.qa.fq9.common import PACKAGE, DriverFailure
from tool.qa.fq9.ports import AndroidPorts


def result(context, row, status, reason, facts):
    path = context.output / (row + "-preparation.json")
    path.write_text(json.dumps(facts, indent=2) + "\n")
    return {"status": status, "reason": reason, "receipts": [str(path)]}


def resources():
    memory = dict(
        line.split(":", 1) for line in Path("/proc/meminfo").read_text().splitlines()
    )
    facts = {
        "availableRamBytes": int(memory["MemAvailable"].split()[0]) * 1024,
        "storageFreeBytes": shutil.disk_usage("/home/eslam/Storage").free,
        "rootFreeBytes": shutil.disk_usage("/").free,
        "kvmAccessible": os.access("/dev/kvm", os.R_OK | os.W_OK),
    }
    facts["admitted"] = (
        facts["availableRamBytes"] >= 6 * 1024**3
        and facts["storageFreeBytes"] >= 16 * 1024**3
        and facts["rootFreeBytes"] >= 2 * 1024**3
        and facts["kvmAccessible"]
    )
    return facts


def fresh(context):
    facts = resources()
    if not facts["admitted"]:
        return result(context, "fb1", "blocked", "fresh_avd_resource_budget", facts)
    root = Path("/home/eslam/Storage") / ("android-qa-fb1-" + context.run_id)
    if root.exists():
        return result(context, "fb1", "blocked", "fresh_avd_directory_exists", facts)
    held = []
    try:
        for port in (5556, 5557):
            sock = socket.socket()
            held.append(sock)
            sock.bind(("127.0.0.1", port))
    except OSError:
        return result(context, "fb1", "blocked", "fresh_avd_ports_busy", facts)
    finally:
        for sock in held:
            sock.close()
    env = dict(os.environ)
    paths = {
        "ANDROID_USER_HOME": "user",
        "ANDROID_EMULATOR_HOME": "user",
        "ANDROID_AVD_HOME": "avd",
        "TMPDIR": "tmp",
        "XDG_CACHE_HOME": "cache",
        "XDG_CONFIG_HOME": "config",
        "XDG_DATA_HOME": "data",
        "XDG_RUNTIME_DIR": "runtime",
    }
    root.mkdir(mode=0o700)
    for key, folder in paths.items():
        path = root / folder
        path.mkdir(mode=0o700, exist_ok=True)
        env[key] = str(path)
    sdk = Path("/home/eslam/Android/Sdk")
    env.update(
        ANDROID_HOME=str(sdk),
        ANDROID_SDK_ROOT=str(sdk),
        JAVA_TOOL_OPTIONS=f"-Djava.io.tmpdir={root / 'tmp'} -Duser.home={root / 'user'}",
    )
    avd = "fb1-2203-final-api35"
    emulator = None
    try:
        with (root / "provision.log").open("wb") as log:
            created = subprocess.run(
                [
                    str(sdk / "cmdline-tools/latest/bin/avdmanager"),
                    "create",
                    "avd",
                    "--name",
                    avd,
                    "--package",
                    "system-images;android-35;google_apis;x86_64",
                    "--device",
                    "pixel_6",
                    "--path",
                    str(root / "avd" / (avd + ".avd")),
                ],
                input=b"no\n",
                stdout=log,
                stderr=subprocess.STDOUT,
                env=env,
                timeout=120,
            )
        if created.returncode:
            return result(
                context, "fb1", "blocked", "fresh_avd_provision_failed", facts
            )
        config = root / "avd" / (avd + ".avd") / "config.ini"
        updates = {
            "disk.dataPartition.size": "8G",
            "hw.ramSize": "3072",
            "hw.cpu.ncore": "2",
            "fastboot.forceColdBoot": "yes",
        }
        lines = [
            line
            for line in config.read_text().splitlines()
            if line.partition("=")[0].strip() not in updates
        ]
        config.write_text(
            "\n".join(lines + [f"{k}={v}" for k, v in updates.items()]) + "\n"
        )
        with (root / "emulator.log").open("wb") as log:
            emulator = subprocess.Popen(
                [
                    str(sdk / "emulator/emulator"),
                    "-avd",
                    avd,
                    "-ports",
                    "5556,5557",
                    "-memory",
                    "3072",
                    "-cores",
                    "2",
                    "-no-snapshot-load",
                    "-no-snapshot-save",
                    "-no-window",
                    "-gpu",
                    "swiftshader_indirect",
                ],
                env=env,
                stdout=log,
                stderr=subprocess.STDOUT,
            )
        ready = False
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline and emulator.poll() is None:
            probe = context.command(
                [
                    "adb",
                    "-s",
                    "emulator-5556",
                    "shell",
                    "getprop",
                    "sys.boot_completed",
                ],
                timeout=10,
            )
            if probe.returncode == 0 and probe.stdout.strip() == b"1":
                ready = True
                break
            time.sleep(2)
        if not ready:
            return result(context, "fb1", "blocked", "fresh_avd_boot_failed", facts)
        context.command(["adb", "-s", "emulator-5556", "root"], timeout=20)
        for _ in range(30):
            probe = context.command(
                ["adb", "-s", "emulator-5556", "shell", "id", "-u"], timeout=10
            )
            if probe.stdout.strip() == b"0":
                break
            time.sleep(1)
        from tool.qa.fq9.common import digest_file

        output = root / "evidence"
        completed = context.command(
            [
                sys.executable,
                "tool/qa/fb1_first_run.py",
                "--execute",
                "--lock-fd",
                str(context.lock_fd),
                "--serial",
                "emulator-5556",
                "--avd",
                avd,
                "--run-id",
                "fb1-2203-final",
                "--apk",
                str(context.candidate),
                "--build",
                "2203",
                "--version",
                "1.2.0",
                "--sha256",
                digest_file(context.candidate),
                "--output",
                str(output),
            ],
            timeout=1800,
        )
        report = output / "report.json"
        if not report.is_file():
            return result(context, "fb1", "fail", "fresh_avd_report_missing", facts)
        value = json.loads(report.read_text())
        return {
            "status": "pass"
            if completed.returncode == 0 and value.get("result") == "PASS"
            else "blocked"
            if value.get("result") == "BLOCKED"
            else "fail",
            "reason": "fresh_first_reply_verified"
            if value.get("result") == "PASS"
            else value.get("failureCode", "fresh_first_reply_failed"),
            "receipts": [str(report)],
        }
    except (OSError, subprocess.SubprocessError):
        return result(context, "fb1", "fail", "fresh_avd_driver_failed", facts)
    finally:
        if emulator is not None and emulator.poll() is None:
            emulator.terminate()
            try:
                emulator.wait(timeout=30)
            except subprocess.TimeoutExpired:
                emulator.kill()
                emulator.wait(timeout=10)


def device_ui(context):
    def execute(argv, timeout=15):
        call = context.command(["adb", "-s", "emulator-5554", *argv], timeout=timeout)
        if call.returncode:
            raise DriverFailure("final_ui_command_failed")
        return call.stdout

    return execute, Bd7Ui(execute)


def app_root(execute, ui):
    execute(["shell", "am", "start", "-n", PACKAGE + "/.MainActivity"])
    for _ in range(10):
        if ui.find("Settings") is not None:
            return
        execute(["shell", "input", "keyevent", "4"])
        time.sleep(0.5)
    raise DriverFailure("final_app_navigation_failed")


def enter(execute, ui, value):
    nodes = [
        n
        for n in ui.nodes()
        if n.get("class") == "android.widget.EditText"
        and n.get("enabled", "true") == "true"
    ]
    if len(nodes) != 1:
        raise DriverFailure("final_text_field_unavailable")
    x, y = ui.centre(nodes[0])
    execute(["shell", "input", "tap", str(x), str(y)])
    execute(["shell", "input", "keycombination", "113", "29"])
    # shell input gets one shell-quoted argument; do not interpolate an executable command.
    import shlex

    execute(["shell", "input", "text", shlex.quote(value.replace(" ", "%s"))])
    execute(["shell", "input", "keyevent", "111"])


def background(context, config):
    """The historical OC1 background fixture is no longer executable."""
    return result(context, "fq9-background", "blocked", "legacy_fixture_retired",
                  {"deviceTouched": False, "fixtureCreated": False})


def demo(context):
    """Record only the product's explicitly simulated, account-free demo route."""
    from tool.qa import record_demo_gif as recorder
    from tool.qa.final_pass_misc import _borrowed

    execute, ui = device_ui(context)
    try:
        app_root(execute, ui)
        ui.tap("Settings")
        ui.scroll_find("Setup guide")
        ui.tap("Setup guide")
        ui.scroll_find("Try the demo")
        ui.tap("Try the demo")
        time.sleep(1)
        if ui.find("Simulated · nothing is saved") is None:
            raise DriverFailure("demo_simulated_route_unverified")
        options = recorder.parser().parse_args([])
        options.record = True
        options.attest_synthetic_demo = True
        options.candidate_apk = None

        def cue(message):
            if message == "0s: show demo installation/setup.":
                if ui.find("Simulated · nothing is saved") is None:
                    raise DriverFailure("demo_simulated_route_unverified")
            elif message == "Pick the demo agent now.":
                if ui.find("Send sample prompt") is not None:
                    ui.tap("Send sample prompt")
                else:
                    enter(execute, ui, "Make the welcome message friendlier.")
                    ui.tap("Send")
            elif message == "Approve a harmless demo tool now.":
                if ui.find("Allow simulated edit") is not None:
                    ui.tap("Allow simulated edit")
                elif ui.find("Allow once") is not None:
                    ui.tap("Allow once")
                else:
                    raise DriverFailure("demo_approval_unavailable")

        gif = recorder.record(
            options, lock=lambda: _borrowed(context), prompt=lambda _: None, emit=cue
        )
        value = result(
            context,
            "demo",
            "blocked",
            "manual_review_required",
            {
                "captured": True,
                "reviewedForExport": False,
                "exported": False,
                "privateGif": str(gif),
                "scope": "product_offline_simulated_demo",
            },
        )
        ui.tap("Leave demo")
        return value
    except Exception as error:
        return result(
            context,
            "demo",
            "blocked",
            error.code
            if isinstance(error, DriverFailure)
            else "demo_preparation_failed",
            {"captured": False, "exported": False},
        )
