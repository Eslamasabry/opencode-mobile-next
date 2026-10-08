"""Read-only app-managed runtime observations and exact owned-turn cleanup.

OC1 is the live-survival scope; OC2 HTTP reads support static preservation only.
No server start, HTTP prompts, provider config reads or credential persistence.
"""

import base64
import hashlib
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request

from .common import PACKAGE, DriverFailure
from .observations import (
    ongoing_notification,
    process_start,
    service_foreground,
    socket_identity,
)

FILES = f"/data/user/0/{PACKAGE}/files"
ID_PATTERN = r"[A-Za-z0-9_-]{1,100}"
FGS = PACKAGE + "/.BackgroundConnectionService"
BUILTIN_FGS = PACKAGE + "/.BuiltinServerService"


class AndroidRuntimeMixin:
    def find_live_receipt(self, directory):
        """Export only the dedicated app-started fixture's public identities."""
        self._connect("opencode")
        if self._runtime_version != "1.18.32":
            raise DriverFailure("app_managed_engine_unavailable")
        title = self.run_id + "-background"
        catalog = self.protocol(
            "GET", "/session", query={"directory": directory, "limit": "100"}
        )
        if (
            type(catalog) is not list
            or len(catalog) >= 100
            or any(type(s) is not dict for s in catalog)
        ):
            raise DriverFailure("live_snapshot_invalid")
        matches = [
            s
            for s in catalog
            if s.get("title") == title and s.get("directory") == directory
        ]
        if (
            len(matches) != 1
            or not isinstance(matches[0].get("id"), str)
            or not re.fullmatch(ID_PATTERN, matches[0]["id"])
        ):
            raise DriverFailure("live_fixture_not_unique")
        session = {"id": matches[0]["id"], "title": title}
        receipt = {"engine": "opencode", "directory": directory, "sessions": [session]}
        history = self._session_history(receipt, session)
        users = [m for m in history if m.get("info", {}).get("role") == "user"]
        if (
            not users
            or not isinstance(users[-1]["info"].get("id"), str)
            or not re.fullmatch(ID_PATTERN, users[-1]["info"]["id"])
        ):
            raise DriverFailure("live_prompt_mismatch")
        session["promptID"] = users[-1]["info"]["id"]
        self.turn = receipt
        snapshot = self.live_snapshot()
        if (
            not all(
                snapshot[k]
                for k in (
                    "ownedTurn",
                    "appManaged",
                    "appVisible",
                    "appAlive",
                    "serverAlive",
                    "foregroundService",
                    "ongoingNotification",
                    "turnActive",
                )
            )
            or snapshot["progressCounter"] < 1
            or snapshot["completed"]
            or snapshot["failed"]
        ):
            raise DriverFailure("live_fixture_not_ready")
        return receipt

    def _connect(self, engine):
        if self._forward is None:
            socket = socket_identity(
                self.text("shell", "cat", "/proc/net/tcp", "/proc/net/tcp6"), self._uid
            )
            if not service_foreground(self.services(), BUILTIN_FGS):
                raise DriverFailure("app_managed_engine_unavailable")
            if self._socket is not None and self._socket != socket:
                raise DriverFailure("app_managed_socket_unavailable")
            self._socket = socket
            password = self.private_bytes(
                FILES + "/linux/ubuntu/root/.oc-builtin/server.password", limit=512
            )
            if (
                not password
                or len(password) > 512
                or b"\n" in password
                or b"\r" in password
            ):
                raise DriverFailure("runtime_password_unavailable")
            self._password = password
            forwarded = self.text("forward", "tcp:0", "tcp:4097")
            if not forwarded.isdigit() or not 1024 <= int(forwarded) <= 65535:
                raise DriverFailure("forward_failed")
            self._forward = int(forwarded)
        health = self.protocol(
            "GET", "/api/health" if engine == "opencode2" else "/global/health"
        )
        version = health.get("version") if type(health) is dict else None
        if (
            type(health) is not dict
            or health.get("healthy") is not True
            or not isinstance(version, str)
            or not re.fullmatch(
                r"[12]\.[0-9]{1,5}\.[0-9]{1,5}(?:[-+][A-Za-z0-9.-]{1,40})?", version
            )
            or not version.startswith("2." if engine == "opencode2" else "1.")
        ):
            raise DriverFailure("app_managed_engine_unavailable")
        self._runtime_version = version

    def protocol(self, method, path, *, query=None):
        if self._forward is None or self._password is None:
            raise DriverFailure("protocol_unavailable")
        url = f"http://127.0.0.1:{self._forward}{path}"
        if query:
            url += "?" + urllib.parse.urlencode(query)
        header = base64.b64encode(b"opencode:" + self._password).decode("ascii")
        request = urllib.request.Request(
            url, method=method, headers={"Authorization": "Basic " + header}
        )
        try:
            # No env proxy or redirect may receive the in-memory credential.
            opener = urllib.request.build_opener(
                urllib.request.ProxyHandler({}), _NoRedirect()
            )
            with opener.open(request, timeout=5) as response:
                raw = response.read(2 * 1024 * 1024 + 1)
            if len(raw) > 2 * 1024 * 1024:
                raise DriverFailure("history_too_large")
            return json.loads(raw)
        except DriverFailure:
            raise
        except (OSError, ValueError, urllib.error.URLError):
            raise DriverFailure("protocol_response_invalid") from None

    def _session_history(self, receipt, session):
        engine, directory = receipt["engine"], receipt["directory"]
        self._connect(engine)
        prefix = "/api" if engine == "opencode2" else ""
        query = (
            {"location[directory]": directory} if prefix else {"directory": directory}
        )
        info = self.protocol("GET", f"{prefix}/session/{session['id']}", query=query)
        if prefix:
            info = info.get("data") if type(info) is dict else None
        if (
            type(info) is not dict
            or info.get("id") != session["id"]
            or info.get("title") != session["title"]
            or (
                info.get("location", {}).get("directory")
                if prefix
                else info.get("directory")
            )
            != directory
        ):
            raise DriverFailure("history_scope_mismatch")
        history = self.protocol(
            "GET",
            f"{prefix}/session/{session['id']}/message",
            query={**query, "limit": "200", **({"order": "asc"} if prefix else {})},
        )
        if prefix:
            history = history.get("data") if type(history) is dict else None
        if (
            type(history) is not list
            or not history
            or len(history) >= 200
            or any(type(m) is not dict for m in history)
        ):
            raise DriverFailure("history_fixture_missing")
        for message in history:
            owner = (
                message.get("sessionID")
                if prefix
                else message.get("info", {}).get("sessionID")
            )
            if (owner is not None and owner != session["id"]) or (
                not prefix and owner is None
            ):
                raise DriverFailure("history_scope_mismatch")
        return history

    def _history_digests(self):
        if self.history is None:
            raise DriverFailure("preservation_fixture_required")
        result = []
        for session in self.history["sessions"]:
            history = self._session_history(self.history, session)
            result.append(
                hashlib.sha256(json.dumps(history, sort_keys=True).encode()).digest()
            )
        return result

    def _main_process(self):
        pid = self.text("shell", "pidof", PACKAGE)
        if not pid.isdigit():
            raise DriverFailure("live_snapshot_invalid")
        stat = self.text("shell", "cat", f"/proc/{pid}/stat")
        start = process_start(stat)
        if start is None:
            raise DriverFailure("live_snapshot_invalid")
        return pid, start

    def live_snapshot(self):
        if self.turn is None:
            raise DriverFailure("live_receipt_required")
        if self.turn["engine"] != "opencode":
            raise DriverFailure("live_oc2_observation_unavailable")
        session = self.turn["sessions"][0]
        if self._uid is None:
            self.installed_identity()
        history = self._session_history(self.turn, session)
        if self._runtime_version != "1.18.32":
            raise DriverFailure("app_managed_engine_unavailable")
        prompt = next(
            (m for m in history if m.get("info", {}).get("id") == session["promptID"]),
            None,
        )
        users = [m for m in history if m.get("info", {}).get("role") == "user"]
        if (
            not prompt
            or prompt["info"].get("role") != "user"
            or users[-1]["info"]["id"] != session["promptID"]
        ):
            raise DriverFailure("live_prompt_mismatch")
        prompt_text = "".join(
            p.get("text", "")
            for p in prompt.get("parts", [])
            if p.get("type") == "text"
        )
        if "FQ9_BACKGROUND_FIXTURE" not in prompt_text:
            raise DriverFailure("live_prompt_mismatch")
        replies = [
            m
            for m in history
            if m.get("info", {}).get("role") == "assistant"
            and m["info"].get("parentID") == session["promptID"]
        ]
        tools = []
        for message in replies:
            for part in message.get("parts", []):
                if part.get("type") == "tool":
                    if (
                        part.get("messageID") != message["info"].get("id")
                        or part.get("synthetic") is True
                        or part.get("executed") is False
                        or part.get("state", {}).get("executed") is False
                    ):
                        raise DriverFailure("live_scope_mismatch")
                    tools.append(part)
        transitions = set()
        for tool in tools:
            if tool.get("sessionID") != session["id"] or tool.get("tool") != "bash":
                raise DriverFailure("live_scope_mismatch")
            state = tool.get("state", {})
            command = state.get("input", {}).get("command")
            if command != "sleep 120":
                raise DriverFailure("live_fixture_command_invalid")
            call = tool.get("callID")
            if not isinstance(call, str) or not re.fullmatch(ID_PATTERN, call):
                raise DriverFailure("live_scope_mismatch")
            if state.get("status") in ("running", "completed"):
                transitions.add((call, state["status"]))
        status = self.protocol(
            "GET", "/session/status", query={"directory": self.turn["directory"]}
        )
        pending = []
        for path in ("/permission", "/question"):
            requests = self.protocol(
                "GET", path, query={"directory": self.turn["directory"]}
            )
            if type(requests) is not list or any(
                type(r) is not dict or not isinstance(r.get("sessionID"), str)
                for r in requests
            ):
                raise DriverFailure("live_snapshot_invalid")
            pending.extend(r for r in requests if r["sessionID"] == session["id"])
        active = (
            type(status) is dict
            and type(status.get(session["id"])) is dict
            and status[session["id"]].get("type") == "busy"
        )
        process = self._main_process()
        if self._app_identity is None:
            self._app_identity = process
        socket = socket_identity(
            self.text("shell", "cat", "/proc/net/tcp", "/proc/net/tcp6"), self._uid
        )
        services = self.services()
        failed = any(m["info"].get("error") is not None for m in replies) or any(
            t.get("state", {}).get("status") == "error" for t in tools
        )
        completed = not active and any(
            m["info"].get("finish") == "stop"
            and m["info"].get("time", {}).get("completed")
            for m in replies
        )
        return {
            "ownedTurn": True,
            "appManaged": service_foreground(services, BUILTIN_FGS)
            and socket == self._socket,
            "appVisible": self.app_visible(),
            "appAlive": process == self._app_identity,
            "serverAlive": socket == self._socket,
            "foregroundService": service_foreground(services, FGS),
            "ongoingNotification": ongoing_notification(
                self.text("shell", "dumpsys", "notification")
            ),
            # Busy is the OC1 live execution state; retry is a distinct
            # state and never qualifies. Allow inference gaps between real
            # fixture tool calls, still requiring increasing tool progress
            # at both checkpoints and no newer prompt in this session.
            "turnActive": active
            and bool(transitions)
            and not failed
            and not completed
            and not pending,
            "progressCounter": len(transitions),
            "completed": bool(completed),
            "failed": failed,
        }

    def cleanup_turn(self):
        session = self.turn["sessions"][0]

        def revalidate():
            history = self._session_history(self.turn, session)
            users = [m for m in history if m.get("info", {}).get("role") == "user"]
            if (
                session["title"] != self.run_id + "-background"
                or not users
                or users[-1]["info"].get("id") != session["promptID"]
            ):
                # A later prompt is somebody else's work even in our fixture
                # row. Never interrupt/delete it merely because the title fits.
                raise DriverFailure("owned_turn_cleanup_failed")

        revalidate()
        if (
            self.protocol(
                "POST",
                f"/session/{session['id']}/abort",
                query={"directory": self.turn["directory"]},
            )
            is not True
        ):
            raise DriverFailure("owned_turn_cleanup_failed")
        for _ in range(10):
            status = self.protocol(
                "GET", "/session/status", query={"directory": self.turn["directory"]}
            )
            if (
                type(status) is dict
                and status.get(session["id"], {}).get("type", "idle") == "idle"
            ):
                break
            time.sleep(0.2)
        else:
            raise DriverFailure("owned_turn_cleanup_failed")
        revalidate()
        if (
            self.protocol(
                "DELETE",
                f"/session/{session['id']}",
                query={"directory": self.turn["directory"]},
            )
            is not True
        ):
            raise DriverFailure("owned_turn_cleanup_failed")


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise DriverFailure("protocol_response_invalid")
