#!/usr/bin/env python3
"""FX-GIT: a bare repository ``app.git`` reachable from the phone (E2E-15).

Served two ways (ports.json FX-GIT, 127.0.0.1 only; the emulator uses 10.0.2.2):

  daemon  ``git daemon --export-all --enable=receive-pack`` as a child (exact PID in children.json):
          git://127.0.0.1:<daemon>/app.git, clone and push without auth
  http    smart HTTP through ``git http-backend`` (CGI): http://127.0.0.1:<http>/app.git
          fetch/clone is open; push (git-receive-pack) needs HTTP Basic with ANY user name and the
          password FX_GIT_PASSWORD (``fx`` is the example user; GitHub-style clients send
          ``x-access-token:<token>`` or ``<login>:<token>``, and FX-GH issues FX_GIT_PASSWORD as its token).
          Only the auth scheme is logged, never the value.

The repo is seeded with one commit ("Initial commit", README.md) on ``main`` by fx <fx@example.invalid>.
State lives in ``<run-dir>/FX-GIT/``: ``repos/app.git`` (served) and ``seed/app.git`` (pristine copy).

Environment: FX_GIT_PASSWORD (set by fxctl; a random one is made when missing).

Control (POST on ctl, JSON body), besides the standard /health /state /log /reset /fault ...:
  reject-push {"on": bool}  install/remove a pre-receive hook that rejects every push with
                            "fx: push rejected by the fixture (reject-push is on)"
  refs        {}            -> {"head": "refs/heads/main", "refs": {refname: sha}, "commits": [subjects on main]}
  /reset restores the seeded repository and turns reject-push off.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import threading
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fxlib.core import (  # noqa: E402
    LOOPBACK,
    SCRUB,
    Fixture,
    FxHandler,
    basic_password,
    new_password,
    run_fixture,
    wait_for_port,
)

EMULATOR_HOST = "10.0.2.2"
REPO = "app.git"
BRANCH = "main"
IDENTITY = {"GIT_AUTHOR_NAME": "fx", "GIT_AUTHOR_EMAIL": "fx@example.invalid",
            "GIT_COMMITTER_NAME": "fx", "GIT_COMMITTER_EMAIL": "fx@example.invalid",
            "GIT_AUTHOR_DATE": "2026-01-01T00:00:00Z", "GIT_COMMITTER_DATE": "2026-01-01T00:00:00Z"}
REJECT_MESSAGE = "fx: push rejected by the fixture (reject-push is on)"
REJECT_HOOK = f"#!/bin/sh\necho '{REJECT_MESSAGE}' >&2\nexit 1\n"


class GitState:
    def __init__(self, fx: Fixture, password: str) -> None:
        self.fx = fx
        self.password = password
        self.lock = threading.RLock()
        self.repos = fx.dir / "repos"
        self.seed = fx.dir / "seed" / REPO
        home = fx.dir / "home"
        home.mkdir(parents=True, exist_ok=True)
        self.env = {
            "PATH": os.environ.get("PATH", "/usr/bin:/bin"),
            "HOME": str(home),
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_TERMINAL_PROMPT": "0",
            "LC_ALL": "C",
        }
        self.reject_push = False
        self.exec_path = subprocess.run(["git", "--exec-path"], capture_output=True, text=True, check=True,
                                        env=self.env).stdout.strip()

    @property
    def repo(self) -> Path:
        return self.repos / REPO

    def git(self, *args: str, cwd: Path | None = None) -> str:
        result = subprocess.run(["git", *args], cwd=cwd, env={**self.env, **IDENTITY}, capture_output=True,
                                text=True, timeout=60)
        if result.returncode != 0:
            raise RuntimeError(f"git {args[0]} failed: {result.stderr.strip()[-300:]}")
        return result.stdout

    def make_seed(self) -> None:
        if self.seed.exists():
            shutil.rmtree(self.seed)
        work = self.fx.dir / "seed" / "work"
        if work.exists():
            shutil.rmtree(work)
        work.mkdir(parents=True)
        self.git("init", "-q", "-b", BRANCH, str(work))
        (work / "README.md").write_text("# app\n\nSeeded by the FX-GIT fixture.\n", encoding="utf-8")
        self.git("add", "README.md", cwd=work)
        self.git("commit", "-q", "-m", "Initial commit", cwd=work)
        self.git("init", "-q", "--bare", "-b", BRANCH, str(self.seed))
        self.git("push", "-q", str(self.seed), f"{BRANCH}:{BRANCH}", cwd=work)
        shutil.rmtree(work)

    def restore(self) -> None:
        with self.lock:
            if self.repo.exists():
                shutil.rmtree(self.repo)
            self.repos.mkdir(parents=True, exist_ok=True)
            shutil.copytree(self.seed, self.repo, symlinks=True)
            # http-backend serves dumb-HTTP info too; keep it current.
            self.git("--git-dir", str(self.repo), "update-server-info")
            self.set_reject(False)

    def set_reject(self, on: bool) -> None:
        with self.lock:
            hook = self.repo / "hooks" / "pre-receive"
            hook.parent.mkdir(parents=True, exist_ok=True)
            if on:
                hook.write_text(REJECT_HOOK, encoding="utf-8")
                hook.chmod(0o755)
            else:
                hook.unlink(missing_ok=True)
            self.reject_push = on
            self.fx.set_state(reject_push=on)

    def refs(self) -> dict[str, Any]:
        with self.lock:
            git_dir = str(self.repo)
            rows = self.git("--git-dir", git_dir, "for-each-ref", "--format=%(refname) %(objectname)")
            refs = dict(line.split(" ", 1) for line in rows.splitlines() if line.strip())
            head = self.git("--git-dir", git_dir, "symbolic-ref", "HEAD").strip()
            commits = self.git("--git-dir", git_dir, "log", "--format=%s", "-n", "20", BRANCH).splitlines() \
                if f"refs/heads/{BRANCH}" in refs else []
        return {"head": head, "refs": refs, "commits": commits}


class GitHttpHandler(FxHandler):
    """Smart HTTP via ``git http-backend``; Basic auth (any user, the fixture password) for push."""

    def parse_request(self) -> bool:
        # Local workaround: core caches _body/_extra on the handler, which lives for the whole
        # keep-alive connection; clear them per request.
        self.__dict__.pop("_body", None)
        self.__dict__.pop("_extra", None)
        return super().parse_request()

    def _read_body(self) -> bytes:
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            parts = []
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass
                    break
                parts.append(self.rfile.read(size))
                self.rfile.readline()
            self._body = b"".join(parts)
            return self._body
        return self.body_bytes()

    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        state: GitState = self.fixture.git  # type: ignore[attr-defined]
        service = query.get("service") or (path.rsplit("/", 1)[-1] if path.endswith("-pack") else "")
        if service:
            self.note(service=service)
        body = self._read_body()
        user = None
        if service == "git-receive-pack":
            creds = basic_password(self.headers)
            if creds is None or creds[1] != state.password:
                self.note(authorized=False)
                self.send_response(401)
                self.send_header("WWW-Authenticate", 'Basic realm="fx-git"')
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                msg = b"push needs the fixture password\n"
                self.send_header("Content-Length", str(len(msg)))
                self.end_headers()
                if method != "HEAD":
                    self.wfile.write(msg)
                self._status = 401
                return
            self.note(authorized=True)
            user = creds[0] or "fx"
        env = {
            **state.env,
            "GIT_PROJECT_ROOT": str(state.repos),
            "GIT_HTTP_EXPORT_ALL": "1",
            "GATEWAY_INTERFACE": "CGI/1.1",
            "SERVER_PROTOCOL": "HTTP/1.1",
            "REQUEST_METHOD": method,
            "PATH_INFO": path,
            "QUERY_STRING": self.path.split("?", 1)[1] if "?" in self.path else "",
            "CONTENT_TYPE": self.headers.get("Content-Type", ""),
            "CONTENT_LENGTH": str(len(body)),
            "REMOTE_ADDR": self.client_address[0],
            "GIT_HTTP_MAX_REQUEST_BUFFER": "100M",
        }
        if user:
            env["REMOTE_USER"] = user
        for header, var in (("Content-Encoding", "HTTP_CONTENT_ENCODING"), ("Git-Protocol", "HTTP_GIT_PROTOCOL")):
            if self.headers.get(header):
                env[var] = self.headers[header]
        with state.lock:
            result = subprocess.run([os.path.join(state.exec_path, "git-http-backend")], input=body, env=env,
                                    capture_output=True, timeout=120)
        if result.stderr:
            with (self.fixture.dir / "http-backend.log").open("a", encoding="utf-8") as log:
                log.write(SCRUB(result.stderr.decode("utf-8", "replace")))
        raw = result.stdout
        head, sep, payload = raw.partition(b"\r\n\r\n")
        if not sep:
            head, sep, payload = raw.partition(b"\n\n")
        if not sep:
            self.send_text("git http-backend failed\n", 500)
            return
        status, reason, headers = 200, "OK", []
        for line in head.decode("latin-1").splitlines():
            key, _, value = line.partition(":")
            if key.lower() == "status":
                code, _, reason = value.strip().partition(" ")
                status = int(code)
            elif key:
                headers.append((key.strip(), value.strip()))
        self.send_response(status, reason or None)
        for key, value in headers:
            if key.lower() not in ("content-length", "transfer-encoding", "connection"):
                self.send_header(key, value)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if method != "HEAD":
            self.wfile.write(payload)
        self._status = status


def setup(fx: Fixture) -> dict[str, Any]:
    password = os.environ.get("FX_GIT_PASSWORD") or new_password()
    SCRUB.add(password)
    state = GitState(fx, password)
    fx.git = state  # type: ignore[attr-defined]
    state.make_seed()
    state.restore()

    daemon_port, http_port = fx.ports["daemon"], fx.ports["http"]
    fx.spawn(["git", "daemon", "--export-all", "--enable=receive-pack", "--reuseaddr", "--informative-errors",
              f"--base-path={state.repos}", f"--port={daemon_port}", f"--listen={LOOPBACK}", str(state.repos)],
             env=state.env, cwd=fx.dir, log_name="git-daemon.log")
    if not wait_for_port(daemon_port, timeout=20):
        raise RuntimeError(f"git daemon did not listen on {daemon_port}")
    fx.serve("http", GitHttpHandler)

    @fx.control("reject-push")
    def reject_push(body: dict[str, Any]) -> dict[str, Any]:
        on = body.get("on")
        if not isinstance(on, bool):
            raise ValueError("on must be true or false")
        state.set_reject(on)
        return {"reject_push": on}

    @fx.control("refs")
    def refs(_body: dict[str, Any]) -> dict[str, Any]:
        return state.refs()

    fx.on_reset_hook(state.restore)
    return {
        "repo": REPO,
        "default_branch": BRANCH,
        "seed_commit": state.refs()["refs"][f"refs/heads/{BRANCH}"],
        "clone_urls": {"git": f"git://{LOOPBACK}:{daemon_port}/{REPO}", "http": f"http://{LOOPBACK}:{http_port}/{REPO}"},
        "emulator": {"git": f"git://{EMULATOR_HOST}:{daemon_port}/{REPO}",
                     "http": f"http://{EMULATOR_HOST}:{http_port}/{REPO}"},
        "push_auth": {"scheme": "basic", "user": "any (example: fx)", "password_env": "FX_GIT_PASSWORD"},
        "reject_message": REJECT_MESSAGE,
    }


def main() -> int:
    return run_fixture("FX-GIT", setup)


if __name__ == "__main__":
    sys.exit(main())
