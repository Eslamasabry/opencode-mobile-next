"""Our bridge tests run fake children only, including through desktopTest."""

import asyncio
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("phone_adapter", Path(__file__).with_name("adapter.py"))
adapter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(adapter)


class AdapterTest(unittest.TestCase):
    def test_B_AGENT_UH_001_mapping_is_atomic_private_and_keeps_other_sessions(self):
        with tempfile.TemporaryDirectory() as temporary:
            index = adapter.SessionIndex(temporary)
            sessions = Path(temporary, "sessions")
            sessions.mkdir()
            saved = sessions / "one.jsonl"
            saved.write_text("{}\n")
            entry = {"id": "persistent", "path": str(saved)}
            index.put("public", entry)
            index.put("other", entry)
            self.assertEqual(index.get("public"), entry)
            self.assertEqual(index.path.stat().st_mode & 0o777, 0o600)
            index.remove("other")
            self.assertEqual(index.get("public"), entry)
            with self.assertRaises(adapter.MappingError):
                index.get("unknown")
            with self.assertRaises(adapter.MappingError):
                index.put("escape", {"id": "p", "path": "/elsewhere/file.jsonl"})
            link = sessions / "link.jsonl"
            link.symlink_to(saved)
            with self.assertRaises(adapter.MappingError):
                index.put("link", {"id": "p", "path": str(link)})
            self.assertFalse(list(Path(temporary).glob(".acp-sessions-*")))

    def test_B_AGENT_UH_002_framing_preserves_tools_and_only_changes_envelope_ids(self):
        frame = {"jsonrpc": "2.0", "params": {"sessionId": "real", "update": {"rawInput": {"sessionId": "real", "code": "print(1)"}}}}
        translated = adapter.session_id(frame, "real", "public")
        self.assertEqual(translated["params"]["sessionId"], "public")
        self.assertEqual(translated["params"]["update"], frame["params"]["update"])
        self.assertEqual(adapter.framed(json.dumps(frame)), frame)
        for line in ("[]", "{}", "not json", "x" * (adapter.MAX_FRAME + 1)):
            with self.assertRaises((adapter.MappingError, ValueError)):
                adapter.framed(line)

    def test_B_AGENT_UH_003_close_keeps_mapping_delete_removes_it_and_load_restarts(self):
        async def scenario():
            with tempfile.TemporaryDirectory() as temporary:
                saved = Path(temporary, "sessions/one.jsonl")
                saved.parent.mkdir()
                saved.write_text("{}\n")
                bridge = adapter.Adapter(temporary, ["never-executed"])
                sent, starts, output = [], [], []
                async def start(resume=None):
                    starts.append(resume)
                async def send(frame):
                    sent.append(frame)
                bridge.start, bridge.send, bridge.emit = start, send, output.append
                new = {"jsonrpc": "2.0", "id": 1, "method": "session/new", "params": {}}
                bridge.pending["1"] = new
                await bridge.received({"jsonrpc": "2.0", "id": 1, "result": {"sessionId": "public", "_meta": {"unharnessed": {"saved": {"sessionId": "saved", "path": str(saved)}}}}})
                close = {"jsonrpc": "2.0", "id": 2, "method": "session/close", "params": {"sessionId": "public"}}
                await bridge.request(close)
                await bridge.received({"jsonrpc": "2.0", "id": 2, "result": {}})
                self.assertEqual(bridge.index.get("public")["id"], "saved")
                bridge.initialize = {"jsonrpc": "2.0", "id": 0, "method": "initialize", "params": {"protocolVersion": 1}}
                load = {"jsonrpc": "2.0", "id": 3, "method": "session/load", "params": {"sessionId": "public"}}
                await bridge.request(load)
                self.assertEqual(starts, [str(saved)])
                internal = sent[-1]["id"]
                await bridge.received({"jsonrpc": "2.0", "id": internal, "result": {"protocolVersion": 1}})
                self.assertEqual(sent[-1]["params"]["sessionId"], "saved")
                await bridge.received({"jsonrpc": "2.0", "method": "session/update", "params": {"sessionId": "saved", "update": {"sessionUpdate": "agent_message_chunk", "content": {"type": "text", "text": "history"}}}})
                self.assertEqual(output[-1]["params"]["sessionId"], "public")
                await bridge.received({"jsonrpc": "2.0", "id": 3, "result": {"sessionId": "saved"}})
                self.assertEqual(output[-1]["result"]["sessionId"], "public")
                delete = {"jsonrpc": "2.0", "id": 4, "method": adapter.DELETE, "params": {"sessionId": "public"}}
                await bridge.request(delete)
                self.assertEqual(sent[-1]["params"]["sessionId"], "saved")
                await bridge.received({"jsonrpc": "2.0", "id": 4, "result": {}})
                with self.assertRaises(adapter.MappingError):
                    bridge.index.get("public")
        asyncio.run(scenario())


if __name__ == "__main__":
    unittest.main()
