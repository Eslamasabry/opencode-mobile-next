#!/bin/sh
# Invoked over a host-key-pinned admin SSH connection. Pairing JSON stays on stdin.
# The caller verifies the archive digest BEFORE extracting/running this file.
set -eu
umask 077
exec python3 - "$@" 3<&0 <<'PY'
import argparse
import base64
import fcntl
import hashlib
import http.client
import importlib.util
import ipaddress
import json
import os
from pathlib import Path
import platform
import pwd
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time

sys.dont_write_bytecode = True


class InstallError(Exception):
    pass


def check_host():
    try:
        release = dict(
            line.split("=", 1)
            for line in Path("/etc/os-release").read_text().splitlines()
            if "=" in line
        )
        if (
            os.getuid() == 0
            or platform.system() != "Linux"
            or release.get("ID", "").strip('"') != "ubuntu"
            or release.get("VERSION_ID", "").strip('"') != "24.04"
        ):
            raise ValueError()
    except (OSError, ValueError):
        raise InstallError("unsupportedHost") from None


def command(arguments, check=True):
    result = subprocess.run(
        arguments,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        timeout=30,
    )
    if check and result.returncode:
        raise InstallError("userServiceUnavailable")
    return result


def check_ssh_policy(username, home):
    """Prove the existing on-disk sshd policy for this admin connection.

    authorized_keys permitopen does NOT restrict reverse/Unix socket forwards.
    No sshd configuration is changed and no privilege is escalated here.
    """
    context = os.environ.get("SSH_CONNECTION", "").split()
    try:
        if len(context) != 4:
            raise ValueError()
        source, source_port, destination, destination_port = context
        ipaddress.ip_address(source)
        ipaddress.ip_address(destination)
        if not all(
            port.isdigit() and 0 < int(port) <= 65535
            for port in (source_port, destination_port)
        ):
            raise ValueError()
        if not re.fullmatch(r"[A-Za-z0-9_-]+", username):
            raise ValueError()
        executable = shutil.which("sshd") or "/usr/sbin/sshd"
        # sshd -T otherwise tries reading root-only host private keys. Override
        # only that parsing input with a throwaway key; never run/reload a daemon.
        with tempfile.TemporaryDirectory(prefix="oc-byo-sshd-check-") as temporary:
            key = str(Path(temporary) / "host-key")
            generated = command(
                ["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-f", key], check=False
            )
            if generated.returncode:
                raise ValueError()
            result = command(
                [
                    executable,
                    "-T",
                    "-h",
                    key,
                    "-C",
                    "user="
                    + username
                    + ",host="
                    + source
                    + ",addr="
                    + source
                    + ",laddr="
                    + destination
                    + ",lport="
                    + destination_port,
                ],
                check=False,
            )
        if result.returncode:
            raise ValueError()
        options = dict(
            line.split(None, 1)
            for line in result.stdout.decode().splitlines()
            if " " in line
        )
        local_allowed = options.get("allowtcpforwarding") in ("local", "yes", "all")
        reverse_denied = (
            options.get("allowtcpforwarding") == "local"
            or options.get("permitlisten") == "none"
        )
        keys = options.get("authorizedkeysfile", "").split()
        expected_key_paths = {
            ".ssh/authorized_keys",
            str(home / ".ssh/authorized_keys"),
            "%h/.ssh/authorized_keys",
        }
        if (
            not local_allowed
            or not reverse_denied
            or options.get("allowstreamlocalforwarding") != "no"
            or options.get("permittunnel") != "no"
            or options.get("usedns") != "no"
            or options.get("disableforwarding") != "no"
            or options.get("pubkeyauthentication") != "yes"
            or not expected_key_paths.intersection(keys)
        ):
            raise ValueError()
    except (OSError, ValueError, UnicodeError, subprocess.SubprocessError):
        raise InstallError("sshPolicyRequired") from None


def safe_bundle(archive_path, expected, destination):
    # Read the authenticated bytes once to avoid hash/extraction TOCTOU.
    import io

    with open(archive_path, "rb") as source:
        raw = source.read(512 * 1024 * 1024 + 1)
    if len(raw) > 512 * 1024 * 1024 or hashlib.sha256(raw).hexdigest() != expected:
        raise InstallError("checksumMismatch")
    names = {"manifest.json", "supervisor.py", "install.sh", "opencode"}
    with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as archive:
        entries = archive.getmembers()
        if (
            len(entries) != len(names)
            or {entry.name for entry in entries} != names
            or any(
                not entry.isfile() or entry.size <= 0 or entry.size > 512 * 1024 * 1024
                for entry in entries
            )
            or sum(entry.size for entry in entries) > 512 * 1024 * 1024
        ):
            raise InstallError("unsafeArchive")
        manifest_entry = archive.getmember("manifest.json")
        if manifest_entry.size > 16384:
            raise InstallError("unsafeArchive")
        manifest = json.load(archive.extractfile(manifest_entry))
        machine = {"x86_64": "amd64", "aarch64": "arm64"}.get(platform.machine())
        if (
            manifest.get("schemaVersion") != 1
            or manifest.get("bundleVersion") != "1.0.0"
            or manifest.get("openCodeVersion") != "1.18.32"
            or manifest.get("port") != 4096
            or machine is None
            or manifest.get("architecture") != machine
            or set(manifest.get("files", {})) != names - {"manifest.json"}
        ):
            raise InstallError("unsupportedBundle")
        for entry in entries:
            data = archive.extractfile(entry).read()
            if (
                entry.name != "manifest.json"
                and hashlib.sha256(data).hexdigest() != manifest["files"][entry.name]
            ):
                raise InstallError("checksumMismatch")
            target = destination / entry.name
            target.write_bytes(data)
            target.chmod(0o600 if entry.name == "manifest.json" else 0o700)
        return manifest


def unit_text(home, directory, release, port):
    # systemd % specifiers/$ expansion are not shell escaping. Reject control
    # characters, and quote paths; do not interpolate user data into a command.
    def quoted(path):
        text = str(path)
        if any(ord(char) < 32 for char in text):
            raise InstallError("unsafePath")
        return (
            '"'
            + text.replace("\\", "\\\\")
            .replace('"', '\\"')
            .replace("%", "%%")
            .replace("$", "$$")
            + '"'
        )

    return (
        "[Unit]\nDescription=OpenCode Mobile private host\nAfter=network.target\n\n"
        "[Service]\nType=simple\nExecStart=/usr/bin/python3 "
        + quoted(release / "supervisor.py")
        + " serve --state-dir "
        + quoted(directory)
        + " --authorized-keys "
        + quoted(home / ".ssh/authorized_keys")
        + " --opencode "
        + quoted(release / "opencode")
        + " --port "
        + str(port)
        + "\nWorkingDirectory="
        + quoted(home)
        + "\nRestart=on-failure\nRestartSec=3\nUMask=0077\nNoNewPrivileges=yes\n"
        "StandardOutput=null\nStandardError=null\nKillMode=control-group\n\n"
        "[Install]\nWantedBy=default.target\n"
    )


def probe(port, payload, descriptor):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=1)
    try:
        basic = base64.b64encode(
            (payload["deviceId"] + ":" + payload["token"]).encode()
        ).decode()
        connection.request(
            "GET", "/_oc/host", headers={"Authorization": "Basic " + basic}
        )
        response = connection.getresponse()
        data = response.read(16385)
        return response.status == 200 and json.loads(data) == descriptor
    except (OSError, ValueError, http.client.HTTPException):
        return False
    finally:
        connection.close()


def install(args, payload):
    if not re.fullmatch(r"[0-9a-f]{64}", args.sha256) or args.port != 4096:
        raise InstallError("invalidArguments")
    check_host()
    home = Path.home()
    directory = home / ".local/share/oc-byo-host"
    for path in (home / ".local", home / ".local/share", directory, home / ".ssh"):
        if path.is_symlink():
            raise InstallError("unsafePath")
        path.mkdir(mode=0o700, parents=True, exist_ok=True)
    directory.chmod(0o700)
    lock = os.open(
        directory / "install.lock", os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600
    )
    try:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise InstallError("hostBusy") from None
        with tempfile.TemporaryDirectory(
            prefix=".install-", dir=directory
        ) as temporary:
            stage = Path(temporary)
            manifest = safe_bundle(args.archive, args.sha256, stage)
            spec = importlib.util.spec_from_file_location(
                "oc_byo_supervisor", stage / "supervisor.py"
            )
            supervisor = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(supervisor)
            supervisor.validate_pair(payload)  # Before service/host mutations.
            state = supervisor.State(
                directory, home / ".ssh/authorized_keys", args.port
            )
            if state.path.exists():
                state.info()  # Fail closed if version/port changes.
            username = pwd.getpwuid(os.getuid()).pw_name
            check_ssh_policy(username, home)
            linger = command(
                ["loginctl", "show-user", username, "--property=Linger", "--value"],
                check=False,
            )
            if linger.returncode or linger.stdout.strip() != b"yes":
                # Never invoke sudo or an interactive polkit/password prompt.
                command(
                    ["loginctl", "--no-ask-password", "enable-linger", username],
                    check=False,
                )
                linger = command(
                    ["loginctl", "show-user", username, "--property=Linger", "--value"],
                    check=False,
                )
                if linger.returncode or linger.stdout.strip() != b"yes":
                    raise InstallError("lingerRequired")
            command(["systemctl", "--user", "show-environment"])
            release = directory / ("bundle-" + args.sha256)
            if release.exists():
                if release.is_symlink() or not release.is_dir():
                    raise InstallError("unsafePath")
                for name in ("supervisor.py", "opencode", "install.sh"):
                    file = release / name
                    if (
                        file.is_symlink()
                        or hashlib.sha256(file.read_bytes()).hexdigest()
                        != manifest["files"][name]
                    ):
                        raise InstallError("hostConflict")
            else:
                # Never overlay an existing release, nor restart to upgrade it.
                os.rename(stage, release)
            unit = home / ".config/systemd/user/oc-byo-host.service"
            for parent in (home / ".config", home / ".config/systemd", unit.parent):
                if parent.is_symlink():
                    raise InstallError("unsafePath")
                parent.mkdir(mode=0o700, exist_ok=True)
            text = unit_text(home, directory, release, args.port)
            if unit.is_symlink() or (unit.exists() and unit.read_text() != text):
                raise InstallError("hostConflict")
            active = (
                command(
                    [
                        "systemctl",
                        "--user",
                        "is-active",
                        "--quiet",
                        "oc-byo-host.service",
                    ],
                    check=False,
                ).returncode
                == 0
            )
            if active and not unit.exists():
                raise InstallError("hostConflict")
            result = state.pair(payload)
            descriptor = {
                key: value for key, value in result.items() if key != "deviceId"
            }
            if not unit.exists():
                supervisor.atomic_write(unit, text.encode())
                command(["systemctl", "--user", "daemon-reload"])
            # enable does not restart; start is used only for an inactive service.
            command(["systemctl", "--user", "enable", "oc-byo-host.service"])
            if not active:
                command(["systemctl", "--user", "start", "oc-byo-host.service"])
            deadline = time.monotonic() + 35
            while time.monotonic() < deadline:
                if probe(args.port, payload, descriptor):
                    return result
                time.sleep(0.2)
            raise InstallError("hostNotReady")
    finally:
        os.close(lock)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--port", type=int, default=4096)
    args = parser.parse_args()
    try:
        with os.fdopen(3, "rb") as input_stream:
            raw = input_stream.read(16385)
        if len(raw) > 16384:
            raise InstallError("invalidPairing")
        result = install(args, json.loads(raw))
        print(json.dumps(result, sort_keys=True))
        return 0
    except InstallError as error:
        print(json.dumps({"error": str(error)}))
        if str(error) == "sshPolicyRequired":
            return 73
    except Exception as error:
        # Imported supervisor errors also carry fixed codes, never request data.
        allowed = {
            "invalidPairing",
            "invalidPublicKey",
            "deviceConflict",
            "deviceKeyConflict",
            "hostConflict",
            "unsafePath",
            "hostBusy",
            "notInstalled",
        }
        code = (
            str(error)
            if type(error).__name__ == "ProtocolError" and str(error) in allowed
            else "installFailed"
        )
        print(json.dumps({"error": code}))
    return 1


if __name__ == "__main__":
    sys.exit(main())
PY
