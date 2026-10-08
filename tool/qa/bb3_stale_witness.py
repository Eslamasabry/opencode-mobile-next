"""Controlled BB3 witness. Caller owns the emulator lock; never logs private data."""
import secrets
import shlex
import time

try:
    from bb3_runtime_acceptance import PACKAGE, SERIAL, Refused, parse_stat, same_process
except ModuleNotFoundError:
    from tool.qa.bb3_runtime_acceptance import PACKAGE, SERIAL, Refused, parse_stat, same_process

MAX_READ = 32768
MAX_PROCESSES = 128
NONCE_KEY = "OC_BB3_STALE_OWNER"
START_TIMEOUT = 15
CLEANUP_TIMEOUT = 15


def require(value, code):
    if not value:
        raise Refused(code)


def identity_valid(value):
    return isinstance(value, dict) and all(type(value.get(k)) is int and value[k] > 0
        for k in ("pid", "startTicks", "group")) and value["pid"] > 1 and \
        all(type(value.get(k)) is int and value[k] >= 0 for k in ("parent", "session"))


def private_path(value):
    return isinstance(value, str) and value.startswith("/") and len(value) < 2048 and \
        not any(ord(c) < 32 or ord(c) == 127 for c in value) and \
        all(part not in (".", "..", "") for part in value.split("/")[1:])


def cgroup_paths(raw):
    result = {}
    for line in raw.splitlines():
        fields = line.split(":", 2)
        require(len(fields) == 3 and fields[0].isdecimal() and
                all(c.isalnum() or c in ",_-" for c in fields[1]) and
                (fields[2] == "/" or private_path(fields[2])), "witness_cgroup_invalid")
        key = tuple(fields[:2])
        require(key not in result, "witness_cgroup_invalid")
        result[key] = fields[2]
    require(result, "witness_cgroup_invalid")
    return result


def outside_app_cgroup(app, controlled):
    app_paths, actual_paths = cgroup_paths(app), cgroup_paths(controlled)
    require(set(app_paths) == set(actual_paths), "witness_cgroup_invalid")
    # Root memberships are universal. At least one app-specific membership
    # must exist, and the witness must be outside every such subtree.
    specific = {key: path for key, path in app_paths.items() if path != "/"}
    return bool(specific) and all(actual_paths[key] != path and
        not actual_paths[key].startswith(path + "/") for key, path in specific.items())


class ControlledStaleWitness:
    """Fixed 180-second app-UID process, outside the app's cgroup.

    Device must be the emulator-5554 Device adapter. Metadata comes only from the
    installed signed QA command step, never user/network input. No nested lock.
    """
    def __init__(self, device, metadata):
        # Device.adb pins the serial; reject adapters advertising another target.
        require(SERIAL == "emulator-5554" and getattr(device, "serial", SERIAL) == SERIAL,
                "witness_device_invalid")
        require(isinstance(metadata, dict) and set(metadata) ==
                {"version", "uid", "app", "command", "environment"}, "witness_metadata_invalid")
        require(type(metadata["version"]) is int and metadata["version"] == 1 and
                type(metadata["uid"]) is int and 10000 <= metadata["uid"] < 200000,
                "witness_metadata_invalid")
        require(identity_valid(metadata["app"]), "witness_app_invalid")
        command, environment = metadata["command"], metadata["environment"]
        require(isinstance(command, list) and 2 < len(command) <= 128 and
                all(isinstance(v, str) and 0 < len(v) < 2048 and
                    not any(ord(c) < 32 or ord(c) == 127 for c in v) for v in command) and
                sum(map(len, command)) <= MAX_READ and command[-2:] == ["/bin/sleep", "180"],
                "witness_command_invalid")
        proots = [v for v in command if v.endswith("/libproot.so")]
        require(len(proots) == 1 and private_path(proots[0]) and
                proots[0].startswith("/data/app/") and f"/{PACKAGE}-" in proots[0] and
                "/lib/" in proots[0], "witness_private_path_invalid")
        native = proots[0].rsplit("/", 1)[0]
        roots = [v.split("=", 1)[1] for v in command if v.startswith("--rootfs=")]
        require(roots == [f"/data/user/0/{PACKAGE}/files/linux/ubuntu"],
                "witness_private_path_invalid")
        require(command[0] == proots[0], "witness_capability_unavailable")
        require(isinstance(environment, dict) and set(environment) ==
                {"PROOT_LOADER", "PROOT_TMP_DIR", "LD_LIBRARY_PATH"} and
                environment["PROOT_LOADER"] == native + "/libproot-loader.so" and
                environment["LD_LIBRARY_PATH"] == native and
                environment["PROOT_TMP_DIR"] == f"/data/user/0/{PACKAGE}/cache/proot-tmp",
                "witness_environment_invalid")
        self.device = device
        self.uid = metadata["uid"]
        self.app = dict(metadata["app"])
        self.command = list(command)
        self.environment = dict(environment)
        self.nonce = secrets.token_hex(24)
        self.root = None
        self.parent = None
        self.witnessed = {}
        self.deadline = None
        self.launch_attempted = False
        self.drained = False

    def _shell(self, script):
        try:
            remaining = 2 if self.deadline is None else self.deadline - time.monotonic()
            require(remaining > 0, "witness_deadline_exceeded")
            result = self.device.adb("shell", "su", "0", "sh", "-c", shlex.quote(script),
                                     timeout=min(2, remaining))
            require(result.returncode == 0 and isinstance(result.stdout, str) and
                    len(result.stdout) <= MAX_READ, "witness_read_failed")
            return result.stdout
        except Refused:
            raise
        except Exception:
            raise Refused("witness_read_failed") from None

    def _read(self, pid, name, limit=MAX_READ):
        require(type(pid) is int and pid > 1 and name in
                {"stat", "status", "cmdline", "environ", "cgroup"}, "witness_read_invalid")
        value = self._shell(f"head -c {limit + 1} /proc/{pid}/{name} 2>/dev/null")
        require(len(value) <= limit, "witness_read_overflow")
        return value

    def _identity(self, pid):
        try:
            require(type(pid) is int and pid > 1, "witness_identity_invalid")
            raw = self._shell(f"if [ -r /proc/{pid}/stat ]; then head -c 4097 /proc/{pid}/stat; fi")
            require(len(raw) <= 4096, "witness_identity_invalid")
            return parse_stat(raw) if raw else None
        except Refused:
            raise
        except Exception:
            raise Refused("witness_identity_unavailable") from None

    def _signal(self, expected, signal):
        require(signal in {"TERM", "KILL"}, "witness_signal_invalid")
        pid, ticks = expected["pid"], expected["startTicks"]
        # Recheck exact start ticks and all four UIDs on-device immediately before
        # the signal. The controlled su parent is never a signal target.
        script = (f"s=$(cat /proc/{pid}/stat 2>/dev/null) || exit 0; "
                  "s=${s##*) }; set -- $s; shift 19; "
                  f"[ \"$1\" = {ticks} ] || exit 0; "
                  f"u=$(awk '/^Uid:/ {{print $2,$3,$4,$5}}' /proc/{pid}/status 2>/dev/null); "
                  f"[ \"$u\" = '{self.uid} {self.uid} {self.uid} {self.uid}' ] || exit 0; "
                  f"kill -{signal} {pid}")
        self._shell(script)

    def _uid(self, pid):
        values = [line.split()[1:] for line in self._read(pid, "status", 4096).splitlines()
                  if line.startswith("Uid:")]
        require(len(values) == 1 and len(values[0]) == 4 and
                all(v.isdecimal() for v in values[0][0:4]), "witness_uid_invalid")
        return [int(v) for v in values[0]]

    def _details(self, expected):
        require(same_process(expected, self._identity(expected["pid"])), "witness_identity_changed")
        pid = expected["pid"]
        uid = self._uid(pid)
        argv = self._read(pid, "cmdline").rstrip("\0").split("\0")
        environment = self._read(pid, "environ").rstrip("\0").split("\0")
        cgroup = self._read(pid, "cgroup", 4096).strip()
        cgroup_paths(cgroup)
        require(same_process(expected, self._identity(pid)), "witness_identity_changed")
        return uid, argv, environment, cgroup

    def _candidates(self):
        # Numeric proc enumeration + unique launch nonce only. No name matching.
        script = ("n=0; for d in /proc/[0-9]*; do n=$((n+1)); [ $n -le 2048 ] || exit 70; "
                  + self._uid_scan() +
                  "head -c 65536 \"$d/environ\" 2>/dev/null | tr '\\000' '\\n' | "
                  f"grep -Fx {shlex.quote(NONCE_KEY + '=' + self.nonce)} >/dev/null && printf '%s\\n' \"${{d##*/}}\"; "
                  "done; exit 0")
        pids = self._shell(script).split()
        require(len(pids) <= MAX_PROCESSES and all(v.isdecimal() and int(v) > 1 for v in pids),
                "witness_candidates_invalid")
        return [int(v) for v in pids]

    def _uid_scan(self):
        # The common proc walk must not fork once per PID. Status is kernel
        # generated; stop after 128 lines, and inspect environ only for this UID.
        return ("u=; k=0; { while read -r key ruid euid suid fsuid extra; do "
                "k=$((k+1)); [ $k -le 128 ] || break; "
                "if [ \"$key\" = 'Uid:' ]; then u=$ruid; break; fi; "
                "done < \"$d/status\"; } 2>/dev/null; "
                "if [ -z \"$u\" ]; then [ ! -d \"$d\" ] || exit 70; continue; fi; "
                f"[ \"$u\" = {self.uid} ] || continue; ")

    def _inventory(self):
        script = ("n=0; for d in /proc/[0-9]*; do n=$((n+1)); [ $n -le 2048 ] || exit 70; "
                  + self._uid_scan() +
                  "{ if IFS= read -r s < \"$d/stat\"; then printf '%s\\n' \"$s\"; "
                  "else [ ! -d \"$d\" ] || exit 70; fi; } 2>/dev/null; done; exit 0")
        lines = self._shell(script).splitlines()
        require(len(lines) <= MAX_PROCESSES, "witness_inventory_overflow")
        try:
            identities = [parse_stat(line) for line in lines]
        except Exception:
            raise Refused("witness_inventory_invalid") from None
        return {value["pid"]: value for value in identities if value["state"] not in {"Z", "X"}}

    def _discover_root(self):
        env = self.environment | {NONCE_KEY: self.nonce}
        roots = []
        for pid in self._candidates():
            identity = self._identity(pid)
            if not identity or identity["state"] in {"Z", "X"}:
                continue
            uid, argv, environment, _ = self._details(identity)
            if argv != self.command:
                continue  # An env/su transition is not the private-proot root.
            require(uid == [self.uid] * 4 and set(environment) ==
                    {f"{k}={v}" for k, v in env.items()}, "witness_root_ownership_invalid")
            require(identity["group"] == pid and identity["session"] == pid,
                    "witness_session_invalid")
            roots.append(identity)
        require(len(roots) <= 1, "witness_root_ambiguous")
        return dict(roots[0]) if roots else None

    def _capture_children(self):
        inventory = self._inventory()
        selected = {pid for pid, old in self.witnessed.items()
                    if same_process(old, inventory.get(pid))}
        selected.add(self.root["pid"])
        for _ in range(MAX_PROCESSES):
            new = {pid for pid, value in inventory.items() if value["parent"] in selected}
            if new <= selected:
                break
            selected |= new
        for pid in selected:
            value = inventory.get(pid)
            if value is None:
                continue
            old = self.witnessed.get(pid)
            require(old is None or same_process(old, value), "witness_child_identity_changed")
            require(value["session"] == self.root["session"] and value["group"] == self.root["group"] and
                    value["startTicks"] >= self.root["startTicks"], "witness_child_ownership_unknown")
            require(self._uid(pid) == [self.uid] * 4 and
                    same_process(value, self._identity(pid)), "witness_child_ownership_unknown")
            self.witnessed[pid] = dict(value)
        # A new, reparented member of the controlled session cannot be assumed owned.
        require(all(pid in self.witnessed for pid, value in inventory.items()
                    if value["session"] == self.root["session"]), "witness_child_ownership_unknown")

    def start(self):
        require(self.root is None and self.parent is None, "witness_already_started")
        self.deadline = time.monotonic() + START_TIMEOUT
        current = self._identity(self.app["pid"])
        require(same_process(self.app, current) and all(self.app[k] == current[k]
                for k in ("parent", "group", "session")), "witness_app_changed")
        require(self._uid(self.app["pid"]) == [self.uid] * 4, "witness_app_uid_invalid")
        app_cgroup = self._read(self.app["pid"], "cgroup", 4096).strip()
        cgroup_paths(app_cgroup)
        env = self.environment | {NONCE_KEY: self.nonce}
        payload = "exec /system/bin/setsid /system/bin/env -i " + shlex.join(
            [f"{key}={value}" for key, value in env.items()] + self.command)
        script = (f"su {self.uid} /system/bin/sh -c {shlex.quote(payload)} "
                  "</dev/null >/dev/null 2>&1 & p=$!; cat /proc/$p/stat 2>/dev/null")
        self.launch_attempted = True
        try:
            self.parent = parse_stat(self._shell(script).strip())
        except Exception:
            raise Refused("witness_launch_unavailable") from None
        for _ in range(20):
            root = self._discover_root()
            if root:
                # Unique nonce + current exact identity covers su/setsid forking.
                self.root = root
                self.witnessed[self.root["pid"]] = dict(self.root)
                require(outside_app_cgroup(app_cgroup,
                        self._read(self.root["pid"], "cgroup", 4096).strip()),
                        "witness_app_cgroup_shared")
                require(same_process(self.root, self._identity(self.root["pid"])), "witness_identity_changed")
                self._capture_children()
                return dict(self.root)
            time.sleep(.1)
        raise Refused("witness_start_timeout")

    def cleanup(self):
        if self.drained or not self.launch_attempted:
            return
        self.deadline = time.monotonic() + CLEANUP_TIMEOUT
        if self.root is None:
            # A timed-out discovery is not a drained launch. Rediscover only by
            # this nonce, exact argv/UID/session and current start-tick proof.
            try:
                for _ in range(20):
                    self.root = self._discover_root()
                    if self.root is not None:
                        self.witnessed[self.root["pid"]] = dict(self.root)
                        break
                    time.sleep(.1)
            except Exception:
                raise Refused("witness_cleanup_unproven") from None
            require(self.root is not None, "witness_cleanup_unproven")
        for attempt in range(20):
            self._capture_children()
            live = [value for value in self.witnessed.values()
                    if same_process(value, self._identity(value["pid"]))]
            if not live:
                # The controlled su parent is not an app-UID writer. Never signal it;
                # su observes child completion and drains naturally.
                if self.parent is not None:
                    for _ in range(20):
                        if not same_process(self.parent, self._identity(self.parent["pid"])):
                            break
                        time.sleep(.1)
                    require(not same_process(self.parent, self._identity(self.parent["pid"])),
                            "witness_parent_not_drained")
                self.root = None
                self.witnessed.clear()
                self.drained = True
                return
            for value in sorted(live, key=lambda item: item["pid"] == self.root["pid"]):
                require(self._uid(value["pid"]) == [self.uid] * 4 and
                        same_process(value, self._identity(value["pid"])), "witness_cleanup_identity_changed")
                try:
                    self._signal(value, "TERM" if attempt == 0 else "KILL")
                except Exception:
                    raise Refused("witness_cleanup_signal_failed") from None
            time.sleep(.1)
        raise Refused("witness_cleanup_timeout")
