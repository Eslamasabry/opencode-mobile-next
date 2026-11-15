"""Hosted, offline end-to-end proof using the actual pinned faux engine."""
import json
import os
from pathlib import Path
import queue
import subprocess
import sys
import threading
import time


package, data, project = map(lambda p: Path(p).resolve(), sys.argv[1:4])
assert not Path("/src").exists()
frames = []


class Client:
    def __init__(self, responses):
        self.queue = queue.Queue()
        env = {
            "HOME": "/trial/home", "PATH": "/usr/bin:/bin", "PYTHONDONTWRITEBYTECODE": "1",
            "UNHARNESSED_CODING_AGENT_DIR": str(data),
            "UNHARNESSED_ADAPTER_TESTING": "1", "UNHARNESSED_OFFLINE": "1",
            "UNHARNESSED_FAUX_SCRIPT": json.dumps({"responses": responses, "tokensPerSecond": 10000}),
        }
        self.error = open("/facts/adapter-stderr.txt", "a")
        self.process = subprocess.Popen(
            [sys.executable, "-B", str(package / "adapter.py")], cwd=project, env=env,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=self.error,
            text=True, bufsize=1,
        )
        def read():
            for line in self.process.stdout:
                self.queue.put(json.loads(line))
            self.queue.put({"ended": True})
        self.reader = threading.Thread(target=read, daemon=True)
        self.reader.start()

    def request(self, number, method, params):
        message = {"jsonrpc": "2.0", "id": number, "method": method, "params": params}
        frames.append({"sent": message})
        self.process.stdin.write(json.dumps(message) + "\n")
        self.process.stdin.flush()
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            frame = self.queue.get(timeout=max(0.1, deadline - time.monotonic()))
            frames.append({"received": frame})
            assert not frame.get("ended"), "Adapter stopped"
            if frame.get("id") == number and "method" not in frame:
                return frame
        raise TimeoutError(method)

    def stop(self):
        self.process.stdin.close()
        try:
            self.process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait(timeout=5)
            raise AssertionError("Adapter did not stop its child")
        self.reader.join(timeout=5)
        self.error.close()
        assert self.process.returncode == 0


def tool(number, code):
    return {"content": [{"type": "toolCall", "name": "ipython", "id": "cell-" + str(number), "arguments": {"code": code}}]}


client = None
try:
    code = "from pathlib import Path\nPath('hello.py').write_text(\"print('hello from phone')\\n\")\nresult = await bash('python3 hello.py')\nprint(result.output)"
    client = Client([tool(1, code), "Created hello.py and ran it."])
    assert "result" in client.request(1, "initialize", {"protocolVersion": 1, "clientCapabilities": {}})
    new = client.request(2, "session/new", {"cwd": str(project), "mcpServers": []})
    public = new["result"]["sessionId"]
    saved = new["result"]["_meta"]["unharnessed"]["saved"]
    prompt = [{"type": "text", "text": "Create hello.py, run it, and tell me what it printed."}]
    answer = client.request(3, "session/prompt", {"sessionId": public, "prompt": prompt})
    assert answer.get("result", {}).get("stopReason") == "end_turn", answer
    assert (project / "hello.py").read_text() == "print('hello from phone')\n"
    assert any("hello from phone" in json.dumps(f.get("received", {}).get("params", {}).get("update", {}).get("content")) for f in frames)
    assert "result" in client.request(4, "session/close", {"sessionId": public})
    client.stop()
    client = None
    path = Path(saved["path"])
    before = path.read_bytes()
    index = json.loads((data / "acp-sessions.json").read_text())
    assert index["sessions"][public]["id"] == saved["sessionId"]
    followup = "from pathlib import Path\nassert Path('hello.py').is_file()\nresult = await bash('python3 hello.py')\nprint('continued: ' + result.output)"
    client = Client([tool(2, followup), "Continued the saved conversation and ran hello.py again."])
    assert "result" in client.request(5, "initialize", {"protocolVersion": 1, "clientCapabilities": {}})
    replay_start = len(frames)
    loaded = client.request(6, "session/load", {"sessionId": public, "cwd": str(project), "mcpServers": []})
    assert loaded.get("result", {}).get("sessionId") == public, loaded
    assert loaded["result"]["_meta"]["unharnessed"]["saved"]["path"] == saved["path"]
    replay = [f["received"] for f in frames[replay_start:] if "received" in f and f["received"].get("method") == "session/update"]
    assert any(f["params"]["update"]["sessionUpdate"] == "user_message_chunk" for f in replay), replay
    assert any(f["params"]["update"]["sessionUpdate"] == "agent_message_chunk" for f in replay), replay
    answer = client.request(7, "session/prompt", {"sessionId": public, "prompt": [{"type": "text", "text": "Continue: run the same file again."}]})
    assert answer.get("result", {}).get("stopReason") == "end_turn", answer
    assert "result" in client.request(8, "session/close", {"sessionId": public})
    after = path.read_bytes()
    assert len(after) > len(before) and after.startswith(before), "Follow-up did not append to the same saved file"
    assert "error" in client.request(9, "session/load", {"sessionId": "unknown", "cwd": str(project), "mcpServers": []})
    client.stop()
    client = None
    for item in frames:
        frame = item.get("received", {})
        if frame.get("method") == "session/update":
            assert frame["params"]["sessionId"] == public, frame
    Path("/facts/reopen-proof.json").write_text(json.dumps({"passed": True, "publicId": public, "persistentId": saved["sessionId"], "savedFileBytesBefore": len(before), "savedFileBytesAfter": len(after), "network": "disabled", "sourceCheckout": "absent"}, indent=2) + "\n")
finally:
    if client:
        client.stop()
    Path("/facts/turn-frames.json").write_text(json.dumps(frames, indent=2) + "\n")
