"""ACP identity bridge. Standard library only; launched by /usr/bin/python3.

The real agent may only be executed on GitHub-hosted runners during development.
Unit tests inject a fake child; they never discover or start an installed agent.
"""

import asyncio
import contextlib
import fcntl
import json
import os
from pathlib import Path
import signal
import stat
import sys
import tempfile
import uuid

MAX_FRAME = 16 * 1024 * 1024
MAX_INDEX = 1024 * 1024
DELETE = "_unharnessed/sessions/delete"


class MappingError(Exception):
    pass


class SessionIndex:
    def __init__(self, root):
        self.root = Path(root).absolute()
        self.root.mkdir(mode=0o700, parents=True, exist_ok=True)
        if self.root.is_symlink():
            raise MappingError("Agent data must be a private folder.")
        self.root = self.root.resolve()
        self.path = self.root / "acp-sessions.json"

    @contextlib.contextmanager
    def locked(self):
        fd = os.open(self.root / "acp-sessions.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX)
            yield
        finally:
            os.close(fd)

    def read(self):
        try:
            fd = os.open(self.path, os.O_RDONLY | os.O_NOFOLLOW)
        except FileNotFoundError:
            return {}
        with os.fdopen(fd, "r") as stream:
            if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
                raise MappingError("Saved conversation index is unavailable.")
            text = stream.read(MAX_INDEX + 1)
        if len(text) > MAX_INDEX:
            raise MappingError("Saved conversation index is too large.")
        value = json.loads(text)
        if value.get("version") != 1 or not isinstance(value.get("sessions"), dict):
            raise MappingError("Saved conversation index is unavailable.")
        return value["sessions"]

    def validate(self, entry, existing=False):
        if not isinstance(entry, dict) or set(entry) != {"id", "path"}:
            raise MappingError("Saved conversation is unavailable.")
        if not isinstance(entry["id"], str) or not entry["id"] or len(entry["id"]) > 256:
            raise MappingError("Saved conversation is unavailable.")
        if not isinstance(entry["path"], str):
            raise MappingError("Saved conversation is unavailable.")
        path = Path(entry["path"])
        if not path.is_absolute() or path.suffix != ".jsonl" or path.is_symlink():
            raise MappingError("Saved conversation is unavailable.")
        sessions = self.root / "sessions"
        if sessions.is_symlink() or not path.resolve().is_relative_to(sessions):
            raise MappingError("Saved conversation is outside agent data.")
        if existing and (not path.is_file() or not stat.S_ISREG(path.stat().st_mode)):
            raise MappingError("Saved conversation file is unavailable.")
        return {"id": entry["id"], "path": str(path)}

    def get(self, public):
        with self.locked():
            entry = self.read().get(public)
            if entry is None:
                raise MappingError("Conversation is unknown. Start a new conversation.")
            return self.validate(entry, existing=True)

    def put(self, public, entry):
        self.change(public, self.validate(entry))

    def remove(self, public):
        self.change(public, None)

    def change(self, public, entry):
        if not isinstance(public, str) or not public or len(public) > 256:
            raise MappingError("Conversation identity is unavailable.")
        with self.locked():
            entries = self.read()
            if entry is None:
                entries.pop(public, None)
            else:
                entries[public] = entry
            encoded = json.dumps({"version": 1, "sessions": entries}, separators=(",", ":"))
            if len(encoded) > MAX_INDEX:
                raise MappingError("Saved conversation index is full.")
            fd, name = tempfile.mkstemp(prefix=".acp-sessions-", dir=self.root)
            try:
                with os.fdopen(fd, "w") as stream:
                    stream.write(encoded)
                    stream.flush()
                    os.fsync(stream.fileno())
                os.replace(name, self.path)
                directory = os.open(self.root, os.O_RDONLY | os.O_DIRECTORY)
                try:
                    os.fsync(directory)
                finally:
                    os.close(directory)
            finally:
                Path(name).unlink(missing_ok=True)


def framed(line):
    if len(line) > MAX_FRAME:
        raise MappingError("Agent message is too large.")
    value = json.loads(line)
    if not isinstance(value, dict) or value.get("jsonrpc") != "2.0":
        raise MappingError("Agent sent an invalid message.")
    return value


def session_id(frame, old, new):
    """Change only the ACP envelope identity, never arbitrary tool/prompt data."""
    result = dict(frame)
    for key in ("params", "result"):
        body = result.get(key)
        if isinstance(body, dict) and body.get("sessionId") == old:
            result[key] = dict(body, sessionId=new)
    return result


class Adapter:
    def __init__(self, root, command, env=None):
        self.index = SessionIndex(root)
        self.command = command
        self.env = env
        self.child = None
        self.generation = 0
        self.events = asyncio.Queue()
        self.pending = {}
        self.public = None
        self.real = None
        self.initialize = None
        self.internal = None

    def emit(self, frame):
        sys.stdout.write(json.dumps(frame, separators=(",", ":")) + "\n")
        sys.stdout.flush()

    def error(self, request, message):
        if "id" in request:
            self.emit({"jsonrpc": "2.0", "id": request["id"], "error": {"code": -32000, "message": message}})

    async def stop(self):
        self.generation += 1
        if self.child:
            with contextlib.suppress(ProcessLookupError):
                os.killpg(self.child.pid, signal.SIGTERM)
            try:
                await asyncio.wait_for(self.child.wait(), 5)
            except asyncio.TimeoutError:
                with contextlib.suppress(ProcessLookupError):
                    os.killpg(self.child.pid, signal.SIGKILL)
                await self.child.wait()
            # A kernel or shell descendant must not outlive its ACP runtime.
            with contextlib.suppress(ProcessLookupError):
                os.killpg(self.child.pid, signal.SIGKILL)
        self.child = None

    async def start(self, resume=None):
        await self.stop()
        args = self.command + (["--resume", resume] if resume else [])
        self.child = await asyncio.create_subprocess_exec(
            *args, stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.DEVNULL, env=self.env, limit=MAX_FRAME + 1, start_new_session=True,
        )
        generation, child = self.generation, self.child

        async def read():
            try:
                while line := await child.stdout.readline():
                    await self.events.put(("child", generation, framed(line)))
            except (ValueError, MappingError):
                pass
            finally:
                await self.events.put(("exit", generation, None))

        asyncio.create_task(read())

    async def send(self, frame):
        if self.child is None or self.child.returncode is not None:
            raise MappingError("Agent stopped. Reopen the conversation to continue.")
        self.child.stdin.write((json.dumps(frame) + "\n").encode())
        await self.child.stdin.drain()

    async def request(self, frame):
        method, params = frame.get("method"), frame.get("params", {})
        if not isinstance(params, dict):
            self.error(frame, "Conversation request is invalid.")
            return
        if method == "initialize":
            if self.pending or self.public:
                raise MappingError("Agent is already initialized.")
            self.initialize = frame
            await self.start()
        elif method == "session/load":
            if self.public or self.pending or self.internal:
                raise MappingError("Close the running conversation before reopening another.")
            entry = self.index.get(params.get("sessionId"))
            if self.initialize is None:
                raise MappingError("Initialize the agent before reopening a conversation.")
            await self.start(entry["path"])
            self.public, self.real = params["sessionId"], entry["id"]
            internal_id = "adapter-initialize-" + uuid.uuid4().hex
            self.internal = (internal_id, frame)
            await self.send(dict(self.initialize, id=internal_id))
            return
        forwarded = frame
        if method == DELETE:
            entry = self.index.get(params.get("sessionId"))
            forwarded = session_id(frame, params["sessionId"], entry["id"])
        if "id" in frame and "method" in frame:
            key = json.dumps(frame["id"])
            if key in self.pending:
                raise MappingError("Conversation request is already pending.")
            self.pending[key] = frame
        await self.send(session_id(forwarded, self.public, self.real) if self.public else forwarded)

    async def received(self, frame):
        if self.internal and frame.get("id") == self.internal[0]:
            _, request = self.internal
            self.internal = None
            if "error" in frame:
                self.error(request, "Agent could not reopen this conversation.")
                self.public = self.real = None
                return
            self.pending[json.dumps(request["id"])] = request
            await self.send(session_id(request, self.public, self.real))
            return
        request = None
        if "id" in frame and "method" not in frame:
            request = self.pending.pop(json.dumps(frame["id"]), None)
        if request and "result" in frame:
            method = request.get("method")
            if method == "session/new":
                result = frame["result"]
                saved = result.get("_meta", {}).get("unharnessed", {}).get("saved", {})
                entry = {"id": saved.get("sessionId"), "path": saved.get("path")}
                try:
                    self.index.put(result.get("sessionId"), entry)
                except (MappingError, TypeError, ValueError, OSError):
                    self.error(request, "Conversation could not be saved. Try a new conversation.")
                    await self.stop()
                    return
                self.public = self.real = result["sessionId"]
            elif method == DELETE:
                self.index.remove(request.get("params", {}).get("sessionId"))
        outgoing = session_id(frame, self.real, self.public) if self.public else frame
        self.emit(outgoing)
        if request and request.get("method") == "session/load" and "error" in frame:
            self.public = self.real = None
        if request and request.get("method") == "session/close" and "result" in frame:
            # ACP close unloads; it does not delete the stored conversation.
            self.public = self.real = None

    async def run(self):
        for signum in (signal.SIGTERM, signal.SIGHUP):
            asyncio.get_running_loop().add_signal_handler(signum, self.events.put_nowait, ("eof", 0, None))
        reader = asyncio.StreamReader(limit=MAX_FRAME + 1)
        protocol = asyncio.StreamReaderProtocol(reader)
        await asyncio.get_running_loop().connect_read_pipe(lambda: protocol, sys.stdin.buffer)

        async def input_frames():
            try:
                while line := await reader.readline():
                    await self.events.put(("input", 0, framed(line)))
            except (ValueError, MappingError):
                await self.events.put(("invalid", 0, None))
            finally:
                await self.events.put(("eof", 0, None))

        asyncio.create_task(input_frames())
        try:
            while True:
                kind, generation, frame = await self.events.get()
                if kind == "eof":
                    break
                if kind in ("child", "exit") and generation != self.generation:
                    continue
                try:
                    if kind == "input":
                        await self.request(frame)
                    elif kind == "child":
                        await self.received(frame)
                    elif kind == "exit":
                        for request in self.pending.values():
                            self.error(request, "Agent stopped. Reopen the conversation to continue.")
                        if self.internal:
                            self.error(self.internal[1], "Agent stopped while reopening the conversation.")
                        self.pending.clear()
                        self.internal = None
                        self.public = self.real = None
                    elif kind == "invalid":
                        self.emit({"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "Invalid ACP message."}})
                except (MappingError, OSError, ValueError, TypeError):
                    if kind == "input":
                        self.pending.pop(json.dumps(frame.get("id")), None)
                        self.error(frame, "Conversation is unavailable. Reopen it or start a new conversation.")
                    else:
                        for request in self.pending.values():
                            self.error(request, "Agent message could not be read. Reopen the conversation.")
                        self.pending.clear()
                        await self.stop()
        finally:
            await self.stop()


def launch():
    package = Path(__file__).resolve().parent
    root = Path(os.environ.get("UNHARNESSED_CODING_AGENT_DIR", str(Path.home() / ".unharnessed")))
    # No inherited provider/other-agent tokens. Credentials belong to this
    # agent's auth.json/models.json only. The venv was installed from the lock.
    env = {key: os.environ[key] for key in ("HOME", "LANG", "TERM") if key in os.environ}
    env.update({
        "PATH": str(root / "kernel-venv/bin") + ":" + str(package / "bin") + ":/usr/bin:/bin",
        "UNHARNESSED_CODING_AGENT_DIR": str(root),
        "UNHARNESSED_PACKAGE_DIR": str(package),
        "UNHARNESSED_KERNEL_PYTHON": str(root / "kernel-venv/bin/python"),
        "UNHARNESSED_KERNEL_VENV": str(root / "kernel-venv"),
        "XDG_CACHE_HOME": str(root / "cache"),
        "XDG_DATA_HOME": str(root / "data"),
        "UV_OFFLINE": "1",
        "PYTHONDONTWRITEBYTECODE": "1",
    })
    if os.environ.get("UNHARNESSED_ADAPTER_TESTING") == "1":
        for key in ("UNHARNESSED_FAUX_SCRIPT", "UNHARNESSED_OFFLINE"):
            if key in os.environ:
                env[key] = os.environ[key]
    asyncio.run(Adapter(root, [str(package / "unharnessed"), "--mode", "acp"], env).run())


if __name__ == "__main__":
    launch()
