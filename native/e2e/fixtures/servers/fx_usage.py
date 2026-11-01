#!/usr/bin/env python3
"""FX-USAGE - plan-usage (quota) snapshots for E2E-33.

The app reads ``<server>/ocmn/quota/v1`` (Codex) and ``/ocmn/quota/v1/<provider>``
(MiniMax, GLM, Claude) with the saved server's Basic credentials
(lib/quota/provider_quota_client.dart). The FX-OC2 / FX-OC2-B front proxies
forward ``/ocmn/quota/`` here, so this stub checks the Basic password against
FX_OC2_PASSWORD and FX_OC2_B_PASSWORD (any user). A Bearer token is accepted
when it equals FX_USAGE_TOKEN (the in-app collector's read token), if set.

Every 'ok' answer follows ProviderQuotaSnapshot.fromJson
(lib/domain/provider_quota.dart): schemaVersion 1, the provider's source, a
64-hex account ref, Codex 'matched' with a plan and ordinaryUsageAllowed,
others 'sourceBound'; windows only when status is ok.

Control: set {"provider": codex|minimax|glm|claude, "mode": MODE, "used"?: float}
  MODE = ok | stale (old fetch, a reset in the past) | expired (authRequired,
  the 'Sign in again' state) | unconfigured | unavailable | offline (hard
  close: the network is gone) | unsupported (404) | denied (401).
GET state -> modes and per-provider read counts. Defaults: codex and minimax ok,
glm unconfigured, claude unsupported.
"""

from __future__ import annotations

import os
import sys
import threading
import time
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fxlib.core import SCRUB, Fixture, FxHandler, basic_password, run_fixture  # noqa: E402

PREFIX = "/ocmn/quota/v1"
SOURCES = {"codex": "codex.wham", "minimax": "minimax.tokenPlan", "glm": "glm.codingPlan", "claude": "claude.oauth"}
MODES = {"ok", "stale", "expired", "unconfigured", "unavailable", "offline", "unsupported", "denied"}
DEFAULT_MODES = {"codex": "ok", "minimax": "ok", "glm": "unconfigured", "claude": "unsupported"}
DEFAULT_USED = {"codex": 25.5, "minimax": 40.0, "glm": 10.0, "claude": 50.0}
ACCOUNT_REF = {
    "codex": "c0de" * 16,
    "minimax": "a1b2" * 16,
    "glm": "9e1a" * 16,
    "claude": "c1a0" * 16,
}


def snapshot(provider: str, mode: str, used: float, now_ms: int) -> dict[str, Any]:
    codex = provider == "codex"
    status = {"ok": "ok", "stale": "ok", "expired": "authRequired", "unconfigured": "unconfigured",
              "unavailable": "unavailable"}[mode]
    fetched = now_ms - (6 * 3600 * 1000 if mode == "stale" else 0)
    attributed = status == "ok"
    account: dict[str, Any] = {"status": ("matched" if codex else "sourceBound") if attributed else "unverified"}
    if attributed:
        account["ref"] = ACCOUNT_REF[provider]
    if codex and attributed:
        account["plan"] = "plus"
    windows: list[dict[str, Any]] = []
    if attributed:
        # stale: the primary window's reset has already passed ("flags a passed reset")
        reset = fetched + (3600 * 1000 if mode == "stale" else 5 * 3600 * 1000)
        windows = [
            {"id": "primary", "status": "reported", "usedPercent": used, "durationSeconds": 18000, "resetsAtMs": reset},
            {"id": "secondary", "status": "reported", "usedPercent": round(used / 2, 1),
             "durationSeconds": 604800, "resetsAtMs": fetched + 3 * 86400 * 1000},
        ]
    return {
        "schemaVersion": 1,
        "provider": provider,
        "source": SOURCES[provider],
        "status": status,
        "freshness": "stale" if mode == "stale" else ("fresh" if attributed else "none"),
        "fetchedAtMs": fetched,
        "expiresAtMs": fetched + 60000,
        "account": account,
        "ordinaryUsageAllowed": True if (codex and attributed) else None,
        "windows": windows,
    }


class State:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.passwords = {p for p in (os.environ.get("FX_OC2_PASSWORD"), os.environ.get("FX_OC2_B_PASSWORD")) if p}
        self.token = os.environ.get("FX_USAGE_TOKEN") or None
        SCRUB.add(*self.passwords, self.token)
        self.reset()

    def reset(self) -> None:
        with self.lock:
            self.modes = dict(DEFAULT_MODES)
            self.used = dict(DEFAULT_USED)
            self.reads: dict[str, int] = {}

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            return {"modes": dict(self.modes), "used": dict(self.used), "reads": dict(self.reads)}


class Handler(FxHandler):
    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        st: State = self.fixture.usage  # type: ignore[attr-defined]
        tail = path[len(PREFIX):].strip("/") if path.startswith(PREFIX) else None
        provider = "codex" if tail == "" else tail
        if method != "GET" or provider not in SOURCES:
            return self.send_json({"error": "not found"}, 404)
        if not self.authorized(st):
            return self.send_json({"error": "unauthorized"}, 401, {"WWW-Authenticate": 'Basic realm="opencode"'})
        with st.lock:
            st.reads[provider] = st.reads.get(provider, 0) + 1
            mode, used = st.modes[provider], st.used[provider]
        self.note(provider=provider, mode=mode)
        if mode == "offline":
            return self.hard_close()
        if mode == "unsupported":
            return self.send_json({"error": "not found"}, 404)
        if mode == "denied":
            return self.send_json({"error": "unauthorized"}, 401)
        self.send_json(snapshot(provider, mode, used, int(time.time() * 1000)))

    def authorized(self, st: State) -> bool:
        header = self.headers.get("Authorization", "")
        if header.lower().startswith("bearer ") and st.token:
            return header[7:].strip() == st.token
        creds = basic_password(self.headers)
        return creds is not None and creds[1] in st.passwords


def setup(fx: Fixture) -> dict[str, Any]:
    st = State()
    fx.usage = st  # type: ignore[attr-defined]
    fx.state = st.snapshot  # type: ignore[method-assign]
    fx.on_reset_hook(st.reset)

    @fx.control("set")
    def set_mode(body: dict[str, Any]) -> dict[str, Any]:
        provider, mode = body.get("provider"), body.get("mode")
        if provider not in SOURCES:
            raise ValueError(f"provider must be one of {sorted(SOURCES)}")
        if mode is not None and mode not in MODES:
            raise ValueError(f"mode must be one of {sorted(MODES)}")
        with st.lock:
            if mode:
                st.modes[provider] = mode
            if "used" in body:
                used = float(body["used"])
                if not 0 <= used <= 100:
                    raise ValueError("used must be 0..100")
                st.used[provider] = used
        return st.snapshot()

    fx.serve("http", Handler)
    return {"url": f"http://127.0.0.1:{fx.ports['http']}{PREFIX}"}


if __name__ == "__main__":
    sys.exit(run_fixture("FX-USAGE", setup))
