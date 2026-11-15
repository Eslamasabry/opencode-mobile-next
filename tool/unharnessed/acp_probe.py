"""Run only in the hosted workflow container; never run Unharnessed locally."""

import json
import os
import pathlib
import queue
import subprocess
import threading
import time


def main():
    assert pathlib.Path("/.dockerenv").is_file()
    assert os.uname().machine == "aarch64"
    env = {
        "HOME": "/trial/home",
        "PATH": "/usr/local/bin:/usr/bin:/bin",
        "XDG_CACHE_HOME": "/trial/cache",
        "XDG_DATA_HOME": "/trial/data",
        "UV_CACHE_DIR": "/trial/cache/uv",
        "UV_PYTHON_INSTALL_DIR": "/trial/data/python",
        "UNHARNESSED_CODING_AGENT_DIR": "/trial/home/agent",
        "UNHARNESSED_PACKAGE_DIR": "/package",
        "UNHARNESSED_OFFLINE": "1",
    }
    messages = queue.Queue()
    frames = []
    with open("/facts/acp-stderr.txt", "w") as stderr:
        process = subprocess.Popen(
            ["/package/unharnessed", "--mode", "acp"],
            cwd="/trial/project", env=env, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=stderr, text=True, bufsize=1,
        )

        def read_frames():
            for line in process.stdout:
                try:
                    messages.put(json.loads(line))
                except ValueError:
                    messages.put({"nonJsonLine": line[:1000]})
            messages.put({"processEnded": True})

        reader = threading.Thread(target=read_frames, daemon=True)
        reader.start()

        def request(number, method, params):
            frame = {"jsonrpc": "2.0", "id": number, "method": method, "params": params}
            frames.append({"sent": frame})
            process.stdin.write(json.dumps(frame) + "\n")
            process.stdin.flush()
            deadline = time.monotonic() + 90
            while time.monotonic() < deadline:
                message = messages.get(timeout=max(0.1, deadline - time.monotonic()))
                frames.append({"received": message})
                if message.get("processEnded"):
                    raise RuntimeError("ACP process ended before answering")
                if message.get("id") == number and "method" not in message:
                    return message
            raise TimeoutError(method)

        try:
            initialized = request(1, "initialize", {
                "protocolVersion": 1, "clientCapabilities": {},
                "clientInfo": {"name": "phone-feasibility", "version": "1"},
            })
            assert initialized.get("result", {}).get("protocolVersion") == 1
            session = request(2, "session/new", {"cwd": "/trial/project", "mcpServers": []})
            session_id = session.get("result", {}).get("sessionId")
            if session_id:
                request(3, "session/prompt", {"sessionId": session_id, "prompt": [
                    {"type": "text", "text": "Say hello."},
                ]})
                request(4, "session/set_mode", {"sessionId": session_id, "modeId": "default"})
                request(5, "session/set_model", {"sessionId": session_id, "modelId": "default"})
            request(6, "session/close", {"sessionId": session_id or "missing"})
            process.terminate()
            process.wait(timeout=10)
            reader.join(timeout=10)
            messages = queue.Queue()
            # Reopening uses the actual saved conversation id in a fresh
            # process, exactly as a generic ACP helper does after restart.
            process = subprocess.Popen(
                ["/package/unharnessed", "--mode", "acp"],
                cwd="/trial/project", env=env, stdin=subprocess.PIPE,
                stdout=subprocess.PIPE, stderr=stderr, text=True, bufsize=1,
            )
            reader = threading.Thread(target=read_frames, daemon=True)
            reader.start()
            request(7, "initialize", {"protocolVersion": 1, "clientCapabilities": {}})
            if session_id:
                request(8, "session/load", {"sessionId": session_id, "cwd": "/trial/project", "mcpServers": []})
        finally:
            pathlib.Path("/facts/acp-frames.json").write_text(json.dumps(frames, indent=2) + "\n")
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)


if __name__ == "__main__":
    main()
