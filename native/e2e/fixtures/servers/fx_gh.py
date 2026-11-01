#!/usr/bin/env python3
"""FX-GH - GitHub device-flow, /user and releases/latest stub (E2E-15 s4, E2E-39 s13).

The app hard-codes github.com and api.github.com; on native the stableQa build
points QaHooks.githubBaseUrl / updateFeedUrl here (plan 14 §4.4). Both hosts'
paths are served from one origin:

  POST /login/device/code         form client_id, scope -> device_code, user_code, verification_uri (https), ...
  POST /login/oauth/access_token  device_code grant -> authorization_pending | slow_down | access_denied |
                                  expired_token | incorrect_device_code | the token; refresh_token grant too
  GET  /user                      Bearer token -> {"login": "fx-octocat", ...} or 401
  GET  /repos/<owner>/<repo>/releases/latest

Control (ctl port): approve {user_code?}, deny {user_code?}, expire {user_code?},
slow-down {interval?}, reject-tokens {on}, disable-device-flow {on},
set-release {tag_name, body?, assets?}, GET state, plus the shared ones.
Without user_code, approve/deny/expire act on the newest pending code.

The access token is FX_GH_TOKEN, else FX_GIT_PASSWORD (so a sign-in followed by
a push to FX-GIT over smart HTTP works), else a random per-run value. Tokens and
device codes never reach the log.
"""

from __future__ import annotations

import os
import secrets
import sys
import threading
import time
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fxlib.core import SCRUB, Fixture, FxHandler, run_fixture  # noqa: E402

LOGIN = "fx-octocat"
DEFAULT_RELEASE = {
    "tag_name": "v2.1.0",
    "name": "2.1.0",
    "body": "Fixture release notes.",
    "draft": False,
    "prerelease": False,
}


class State:
    def __init__(self, fx: Fixture) -> None:
        self.fx = fx
        self.lock = threading.Lock()
        self.token = os.environ.get("FX_GH_TOKEN") or os.environ.get("FX_GIT_PASSWORD") or "gho_" + secrets.token_hex(18)
        SCRUB.add(self.token)
        self.reset()

    def reset(self) -> None:
        with self.lock:
            self.codes: dict[str, dict[str, Any]] = {}   # device_code -> record
            self.reject_tokens = False
            self.device_flow_disabled = False
            self.slow_down: int | None = None
            self.refresh_tokens: set[str] = set()
            self.release = dict(DEFAULT_RELEASE)

    def pick(self, body: dict[str, Any]) -> dict[str, Any]:
        want = body.get("user_code")
        rows = [r for r in self.codes.values() if r["state"] == "pending"]
        if want:
            rows = [r for r in self.codes.values() if r["user_code"] == want]
        if not rows:
            raise ValueError("no pending device code")
        return max(rows, key=lambda r: r["created"])

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            return {
                "pending": [{"user_code": r["user_code"], "state": r["state"]}
                            for r in sorted(self.codes.values(), key=lambda r: -r["created"]) if r["state"] == "pending"],
                "codes": [{"user_code": r["user_code"], "state": r["state"]} for r in self.codes.values()],
                "reject_tokens": self.reject_tokens,
                "device_flow_disabled": self.device_flow_disabled,
                "release": self.release.get("tag_name"),
            }


def user_code() -> str:
    alphabet = "BCDFGHJKLMNPQRSTVWXZ23456789"
    pick = lambda n: "".join(secrets.choice(alphabet) for _ in range(n))  # noqa: E731
    return f"{pick(4)}-{pick(4)}"


class Handler(FxHandler):
    @property
    def st(self) -> State:
        return self.fixture.gh  # type: ignore[attr-defined]

    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        st = self.st
        if method == "POST" and path == "/login/device/code":
            form = self.body_form()
            with st.lock:
                if st.device_flow_disabled:
                    return self.send_json({"error": "device_flow_disabled",
                                           "error_description": "Device Flow must be explicitly enabled for this App"})
                if not form.get("client_id"):
                    return self.send_json({"error": "unauthorized_client"}, 200)
                device_code = secrets.token_hex(20)
                SCRUB.add(device_code)
                record = {"device_code": device_code, "user_code": user_code(), "state": "pending",
                          "created": time.monotonic(), "scope": form.get("scope", "")}
                st.codes[device_code] = record
            self.note(user_code=record["user_code"])
            return self.send_json({"device_code": device_code, "user_code": record["user_code"],
                                   "verification_uri": "https://github.com/login/device",
                                   "expires_in": 900, "interval": 1})
        if method == "POST" and path == "/login/oauth/access_token":
            return self.token_grant(self.body_form())
        if method == "GET" and path == "/user":
            header = self.headers.get("Authorization", "")
            token = header.split(" ", 1)[1].strip() if " " in header else ""
            with st.lock:
                ok = token == st.token and not st.reject_tokens
            if not ok:
                return self.send_json({"message": "Bad credentials", "status": "401"}, 401)
            return self.send_json({"login": LOGIN, "id": 4242, "name": "FX Octocat", "type": "User"})
        if method == "GET" and path.startswith("/repos/") and path.endswith("/releases/latest"):
            host = self.headers.get("Host", "127.0.0.1")
            with st.lock:
                release = dict(st.release)
            tag = release.get("tag_name", "v0")
            release.setdefault("html_url", f"https://github.com{path[len('/repos'):-len('/latest')]}/tag/{tag}")
            release.setdefault("assets", [{
                "name": f"opencode-mobile-{tag}.apk",
                "content_type": "application/vnd.android.package-archive",
                "size": 1024,
                "browser_download_url": f"http://{host}/downloads/opencode-mobile-{tag}.apk",
            }])
            return self.send_json(release)
        self.send_json({"message": "Not Found"}, 404)

    def token_grant(self, form: dict[str, str]) -> None:
        st = self.st
        grant = form.get("grant_type", "")
        with st.lock:
            if grant == "refresh_token":
                if form.get("refresh_token") in st.refresh_tokens and not st.reject_tokens:
                    return self.send_json(self.credential())
                return self.send_json({"error": "bad_refresh_token"})
            record = st.codes.get(form.get("device_code", ""))
            if record is None:
                return self.send_json({"error": "incorrect_device_code",
                                       "error_description": "The device_code provided is not valid."})
            self.note(user_code=record["user_code"], state=record["state"])
            if record["state"] == "pending" and st.slow_down is not None:
                interval, st.slow_down = st.slow_down, None
                return self.send_json({"error": "slow_down", "interval": interval})
            if record["state"] == "pending":
                return self.send_json({"error": "authorization_pending"})
            if record["state"] == "denied":
                return self.send_json({"error": "access_denied"})
            if record["state"] == "approved":
                record["state"] = "redeemed"
                return self.send_json(self.credential())
            return self.send_json({"error": "expired_token"})

    def credential(self) -> dict[str, Any]:
        refresh = "ghr_" + secrets.token_hex(18)
        SCRUB.add(refresh)
        self.st.refresh_tokens.add(refresh)
        return {"access_token": self.st.token, "token_type": "bearer", "scope": "repo",
                "refresh_token": refresh, "expires_in": 28800, "refresh_token_expires_in": 15811200}


def setup(fx: Fixture) -> dict[str, Any]:
    st = State(fx)
    fx.gh = st  # type: ignore[attr-defined]
    fx.on_reset_hook(st.reset)

    def mark(state: str):
        def action(body: dict[str, Any]) -> dict[str, Any]:
            with st.lock:
                record = st.pick(body)
                record["state"] = state
                return {"user_code": record["user_code"], "state": state}
        return action

    fx.actions["approve"] = mark("approved")
    fx.actions["deny"] = mark("denied")
    fx.actions["expire"] = mark("expired")

    @fx.control("slow-down")
    def slow_down(body: dict[str, Any]) -> dict[str, Any]:
        with st.lock:
            st.slow_down = int(body.get("interval", 10))
        return {"ok": True}

    @fx.control("reject-tokens")
    def reject(body: dict[str, Any]) -> dict[str, Any]:
        with st.lock:
            st.reject_tokens = bool(body.get("on", True))
        return {"reject_tokens": st.reject_tokens}

    @fx.control("disable-device-flow")
    def disable(body: dict[str, Any]) -> dict[str, Any]:
        with st.lock:
            st.device_flow_disabled = bool(body.get("on", True))
        return {"device_flow_disabled": st.device_flow_disabled}

    @fx.control("set-release")
    def set_release(body: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(body.get("tag_name"), str):
            raise ValueError("tag_name is required")
        with st.lock:
            st.release = {**DEFAULT_RELEASE, **body}
        return {"tag_name": body["tag_name"]}

    fx.state = st.snapshot  # type: ignore[method-assign]
    fx.serve("http", Handler)
    return {"url": f"http://127.0.0.1:{fx.ports['http']}", "login": LOGIN}


if __name__ == "__main__":
    sys.exit(run_fixture("FX-GH", setup))
