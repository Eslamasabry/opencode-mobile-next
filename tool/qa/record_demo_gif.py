#!/usr/bin/env python3
"""Plan or record a private, operator-driven emulator demo.

Default: print a plan, without running subprocesses, locking, or writing files.
Recording requires --record --attest-synthetic-demo. The operator must prepare
synthetic/demo data, disable notifications, and keep all real accounts, names,
tokens, and sign-in screens off camera. The script cannot certify screen content.

Example (plan only): python3 tool/qa/record_demo_gif.py --duration 75
Example (explicit capture): add --record --attest-synthetic-demo

Artifacts remain in a private directory outside this checkout. Review both the
raw MP4 and cropped GIF manually before any separate publication/export. This
script has no public-output/export option and never fabricates or masks app UI.
"""

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import signal
import stat
import subprocess
import time
import uuid


SERIAL = "emulator-5554"
LOCK = Path("/home/eslam/Storage/tmp/oc-emulator.lock")
NORMAL_APK = Path("/home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk")
REPO = Path(__file__).resolve().parents[2]
PRIVATE_ROOT = Path.home() / ".local/share/opencode/private-demo-recordings"


class RecorderError(Exception):
    """Only fixed, safe messages from this module are surfaced to the operator."""


def bounded_integer(low, high):
    def parse(value):
        try:
            number = int(value)
        except ValueError:
            raise argparse.ArgumentTypeError("Expected a whole number") from None
        if not low <= number <= high:
            raise argparse.ArgumentTypeError(f"Expected {low} through {high}")
        return number
    return parse


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--record", action="store_true", help="Explicitly run capture")
    result.add_argument(
        "--attest-synthetic-demo", action="store_true",
        help="Confirm synthetic data only, no live accounts or account values on camera",
    )
    result.add_argument("--duration", type=bounded_integer(60, 90), default=75)
    result.add_argument("--crop-top", type=bounded_integer(0, 500), default=64)
    result.add_argument("--crop-bottom", type=bounded_integer(0, 500), default=96)
    result.add_argument("--candidate-apk", type=Path)
    result.add_argument("--private-dir", type=Path, default=PRIVATE_ROOT)
    return result


def no_symlinks(path):
    if any(ord(char) < 32 or ord(char) == 127 for char in str(path)) or ".." in path.parts:
        raise RecorderError("Control characters and parent traversal are not permitted in paths")
    path = path.expanduser().absolute()
    if any(part.is_symlink() for part in (path, *path.parents)):
        raise RecorderError("Symlink paths are not permitted")
    return path


def private_path(path):
    path = no_symlinks(path)
    if path == REPO or REPO in path.parents:
        raise RecorderError("Capture must remain outside the checkout")
    if path.exists():
        mode = path.stat()
        if not stat.S_ISDIR(mode.st_mode) or mode.st_uid != os.getuid():
            raise RecorderError("Private output directory must be owned by this user")
        if mode.st_mode & 0o077:
            raise RecorderError("Private output directory must have mode 0700")
    return path


def apk_path(path):
    path = no_symlinks(path)
    if path.suffix.lower() != ".apk" or not path.is_file():
        raise RecorderError("APK must be an existing regular .apk file")
    return path


def adb(*arguments):
    return ["adb", "-s", SERIAL, *map(str, arguments)]


def conversion_commands(raw, palette, gif, top, bottom):
    # Two passes avoid buffering the whole recording in a split filter graph.
    crop = f"crop=iw:ih-{top}-{bottom}:0:{top},fps=10,scale=480:-2:flags=lanczos"
    common = ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-n",
              "-threads", "2", "-filter_threads", "1", "-filter_complex_threads", "1"]
    return [
        [*common, "-i", str(raw), "-vf", crop + ",palettegen=max_colors=128:stats_mode=diff",
         "-frames:v", "1", str(palette)],
        [*common, "-i", str(raw), "-i", str(palette), "-lavfi",
         f"[0:v]{crop}[v];[v][1:v]paletteuse=dither=bayer:bayer_scale=3",
         "-loop", "0", str(gif)],
    ]


def plan(options):
    return {
        "mode": "record" if options.record else "dry-run",
        "serial": SERIAL,
        "duration_seconds": options.duration,
        "lock": str(LOCK),
        "lock_wait_seconds": 3600,
        "candidate_install": options.candidate_apk is not None,
        "restore_after_candidate": str(NORMAL_APK),
        "private_directory": str(options.private_dir.expanduser().absolute()),
        "crop_pixels": {"top": options.crop_top, "bottom": options.crop_bottom},
        "gif": {"width": 480, "fps": 10, "threads": 2},
        "operator_sequence": [
            "Prepare synthetic setup/install screen; hide notifications and all real account values.",
            "At 0 seconds: show installation/setup using demo data only.",
            "At about one third: pick the demo agent.",
            "At about two thirds: approve a harmless demo tool, then show its result.",
            "Manually review private MP4 and GIF before any separate export.",
        ],
    }


@contextmanager
def emulator_lock(path=LOCK, *, clock=time.monotonic, sleep=time.sleep):
    """Exclusive flock, equivalent to flock -w 3600, held through restoration."""
    flags = os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(path, flags, 0o600)
    acquired = False
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise RecorderError("Emulator lock must be a regular file")
        deadline = clock() + 3600
        while True:
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                acquired = True
                break
            except BlockingIOError:
                if clock() >= deadline:
                    raise RecorderError("Timed out waiting for the emulator lock") from None
                sleep(0.25)
        yield
    finally:
        if acquired:
            fcntl.flock(fd, fcntl.LOCK_UN)
        os.close(fd)


def checked(run, command, label, timeout=60):
    try:
        result = run(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                     timeout=timeout, check=False)
    except (OSError, subprocess.SubprocessError):
        raise RecorderError(f"{label} failed") from None
    if result.returncode:
        raise RecorderError(f"{label} failed")
    return result.stdout


def stop_owned(process):
    """Signal only the subprocess object created for this recording."""
    if process is None or process.poll() is not None:
        return
    process.send_signal(signal.SIGINT)
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.terminate()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=3)


def record(options, *, run=subprocess.run, popen=subprocess.Popen,
           clock=time.monotonic, sleep=time.sleep, lock=emulator_lock,
           prompt=input, emit=print):
    if not options.record or not options.attest_synthetic_demo:
        raise RecorderError("Recording requires --record and --attest-synthetic-demo")
    root = private_path(options.private_dir)
    candidate = apk_path(options.candidate_apk) if options.candidate_apk else None
    normal = apk_path(NORMAL_APK) if candidate else None
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    private_path(root)
    tag = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex
    session = root / ("demo-" + tag)
    session.mkdir(mode=0o700, exist_ok=False)
    raw, palette, gif = (session / name for name in ("capture.mp4", "palette.png", "demo.gif"))
    remote = "/sdcard/oc-demo-" + uuid.uuid4().hex + ".mp4"
    process = None
    began = None
    remote_owned = False
    restore = False
    failure = None
    cleanup = []
    with lock():
        try:
            if checked(run, adb("get-state"), "Emulator availability").strip() != b"device":
                raise RecorderError("The dedicated emulator is unavailable")
            if candidate:
                restore = True  # A partial install must still trigger restoration.
                checked(run, adb("install", "-r", candidate), "Candidate installation", 180)
            emit("Use synthetic/demo data only. Hide notifications and every real account value.")
            prompt("Prepare the setup/install screen, then press Enter to start recording: ")
            remote_owned = True
            process = popen(
                adb("shell", "screenrecord", "--time-limit", options.duration,
                    "--bit-rate", "4000000", remote),
                stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
            began = clock()
            emit("0s: show demo installation/setup.")
            markers = [(options.duration / 3, "Pick the demo agent now."),
                       (options.duration * 2 / 3, "Approve a harmless demo tool now.")]
            while clock() - began < options.duration:
                elapsed = clock() - began
                if process.poll() is not None and elapsed < options.duration - 1:
                    raise RecorderError("Recording ended before the demo interval")
                while markers and elapsed >= markers[0][0]:
                    emit(markers.pop(0)[1])
                sleep(min(0.25, options.duration - elapsed))
            try:
                code = process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                raise RecorderError("Recording did not finish within its time limit") from None
            if code:
                raise RecorderError("Screen recording failed")
            checked(run, adb("pull", remote, raw), "Recording download", 90)
            os.chmod(raw, 0o600)
            for command in conversion_commands(raw, palette, gif, options.crop_top, options.crop_bottom):
                checked(run, command, "GIF conversion", 300)
            for artifact in (palette, gif):
                os.chmod(artifact, 0o600)
            with (session / "REVIEW_REQUIRED.json").open("x") as output:
                json.dump({"synthetic_demo_attested": True, "reviewed_for_export": False,
                           "duration_requested_seconds": options.duration,
                           "crop_top": options.crop_top, "crop_bottom": options.crop_bottom,
                           "instruction": "Review MP4 and GIF for account values before manual export."}, output)
            os.chmod(session / "REVIEW_REQUIRED.json", 0o600)
        except BaseException as error:
            failure = error
        finally:
            try:
                stop_owned(process)
            except (OSError, subprocess.SubprocessError):
                cleanup.append("owned recorder cleanup")
            # Killing adb cannot prove remote shell termination. Keep the lock
            # until this uniquely named screenrecord's built-in limit expires.
            if began is not None:
                remaining = began + options.duration + 1 - clock()
                if remaining > 0:
                    sleep(remaining)
            if remote_owned:
                try:
                    checked(run, adb("shell", "rm", "-f", remote), "Remote recording cleanup")
                except RecorderError:
                    cleanup.append("remote recording cleanup")
            if restore:
                try:
                    checked(run, adb("install", "-r", normal), "Normal app restoration", 180)
                except RecorderError:
                    cleanup.append("normal app restoration")
    if cleanup:
        raise RecorderError("Manual attention required: " + ", ".join(cleanup))
    if failure is not None:
        raise failure
    emit("Private GIF recorded. Review capture.mp4 and demo.gif before manual export.")
    emit(str(session))
    return gif


def main(argv=None):
    options = parser().parse_args(argv)
    if not options.record:
        print(json.dumps(plan(options), indent=2))
        return 0
    previous_term = signal.getsignal(signal.SIGTERM)
    def interrupted(_signum, _frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, interrupted)
    try:
        record(options)
        return 0
    except KeyboardInterrupt:
        print("Recording cancelled; owned-resource cleanup was attempted.")
        return 130
    except RecorderError as error:
        print(str(error))
        return 1
    except (OSError, subprocess.SubprocessError):
        # No raw command output, paths from tool errors, or account data.
        print("Recording failed. Check private files and confirm normal app restoration before retrying.")
        return 1
    finally:
        signal.signal(signal.SIGTERM, previous_term)


if __name__ == "__main__":
    raise SystemExit(main())
