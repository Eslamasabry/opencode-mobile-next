"""Bounded pinned CLI account reads in an empty home. No login/provider actions.

Run only under the coordinator's runtime lease. The device flow is exercised
separately by synthetic Dart fixtures; this script never starts or cancels login.
"""
import json
import os
from pathlib import Path
import queue
import re
import subprocess
import threading
import time
import uuid


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "traycer" / "codex-account-proof"
CLI = Path(r"C:\Users\Eslam\AppData\Local\Programs\OpenAI\Codex\bin\codex.exe")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    scratch = OUT / ("empty-home-" + uuid.uuid4().hex)
    scratch.mkdir()
    # Only ordinary Windows runtime variables are inherited. Account/API tokens
    # and user configuration are not copied into this subprocess environment.
    env = {key: os.environ[key] for key in
           ("SystemRoot", "WINDIR", "COMSPEC", "PATHEXT", "PATH", "TEMP", "TMP")
           if key in os.environ}
    env.update({"CODEX_HOME": str(scratch), "HOME": str(scratch),
                "USERPROFILE": str(scratch), "APPDATA": str(scratch / "Roaming"),
                "LOCALAPPDATA": str(scratch / "Local"),
                "HTTP_PROXY": "http://127.0.0.1:9", "HTTPS_PROXY": "http://127.0.0.1:9",
                "ALL_PROXY": "http://127.0.0.1:9", "NO_PROXY": ""})
    (scratch / "config.toml").write_text(
        'cli_auth_credentials_store = "file"\n[analytics]\nenabled = false\n'
        '[feedback]\nenabled = false\n', encoding="utf-8")
    hidden = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    version = subprocess.run([str(CLI), "--version"], env=env, cwd=scratch,
                             capture_output=True, text=True, timeout=10,
                             creationflags=hidden)
    assert version.returncode == 0 and version.stdout.strip() == "codex-cli 0.153.4", "Pinned CLI mismatch"
    proc = subprocess.Popen([str(CLI), "app-server", "--listen", "stdio://"],
                            cwd=scratch, env=env, stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                            text=True, encoding="utf-8", bufsize=1,
                            creationflags=hidden)
    inbox = queue.Queue()
    result = {"version": "0.153.4", "transport": "stdio", "checks": {}}
    deadline = time.monotonic() + 40

    def consume():
        for line in proc.stdout:
            if len(line) > 1024 * 1024:
                inbox.put(None)
                return
            try:
                inbox.put(json.loads(line))
            except ValueError:
                inbox.put(None)

    threading.Thread(target=consume, daemon=True).start()

    def send(frame):
        proc.stdin.write(json.dumps(frame) + "\n")
        proc.stdin.flush()

    def request(number, method, params):
        send({"id": number, "method": method, "params": params})
        while True:
            remaining = min(10, deadline - time.monotonic())
            assert remaining > 0, "Proof deadline"
            frame = inbox.get(timeout=remaining)
            assert isinstance(frame, dict), "Invalid protocol frame"
            if frame.get("id") == number:
                return frame

    try:
        init = request(1, "initialize", {"clientInfo": {"name": "oc_account_proof", "version": "1"},
                                         "capabilities": {"experimentalApi": False}})
        identity = init.get("result", {}).get("userAgent", "")
        assert re.search(r"(^|/)0\.153\.4(?:\s|$)", identity), "Pinned initialize identity missing"
        result["checks"]["initialize"] = "pinned result"
        send({"method": "initialized", "params": {}})
        account = request(2, "account/read", {"refreshToken": False})
        account_result = account.get("result", {})
        assert "account" in account_result and account_result["account"] is None and isinstance(account_result.get("requiresOpenaiAuth"), bool), "Empty-home account read failed"
        result["checks"]["account/read"] = {"account": None, "requiresOpenaiAuth": account_result["requiresOpenaiAuth"]}
        for number, method in enumerate(("account/rateLimits/read", "account/usage/read"), 3):
            reply = request(number, method, {})
            error = reply.get("error", {})
            assert isinstance(error.get("code"), int) and error["code"] != -32601, "Account method missing or unexpected authenticated result"
            assert re.search(r"authenticat|not signed in|requires.*(?:chatgpt|account)|login|log in",
                             str(error.get("message", "")), re.I), "Expected explicit unauthenticated rejection"
            # Keep only the code. Raw error messages may contain paths or data.
            result["checks"][method] = {"outcome": "unauthenticated rejection", "code": error["code"]}
    finally:
        if proc.poll() is None:
            if os.name == "nt":
                subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                               timeout=5, creationflags=hidden)
            else:
                proc.terminate()
            proc.wait(timeout=5)
        assert proc.poll() is not None, "Owned proof process remained active"
        result["processClosed"] = True
        (OUT / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("Pinned account read/unauthenticated metrics proof passed; owned process closed.")


if __name__ == "__main__":
    main()
