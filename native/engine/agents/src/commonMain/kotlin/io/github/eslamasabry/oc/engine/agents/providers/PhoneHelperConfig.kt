package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Codex on this phone: its OS sandbox off, and every command and file change asked for (issue 147).
 *
 * The sandbox: Codex's Linux sandbox needs Landlock/seccomp/bubblewrap, which the phone's Linux (proot, no real root)
 * does not have; with it every shell command failed to start (Galaxy S22, build 10045). The phone's Linux is itself
 * confined to the app's private storage.
 *
 * The asking (Galaxy S22, build 10050: with "Ask first" Codex wrote a file and ran a command with no request): with the
 * sandbox off, Codex 0.169.0's default `on-request` policy never asks (nothing needs to leave a sandbox). Measured with
 * the pinned app-server and a stub model: only `approvalPolicy: "untrusted"` makes it ask for every command (even `ls`)
 * and every file change, and a declined request runs nothing. That value is accepted only as a thread or turn
 * parameter: in its configuration it is refused ("approval_policy = untrusted is no longer supported"), and the helper
 * (Paseo 0.15.4) sends only its modes' own policies (`on-request`, `never`).
 *
 * So the helper starts Codex through [GUARD], a small stdio filter between the helper and `codex app-server`: on every
 * `thread/start`, `thread/resume`, `thread/fork` and `turn/start` it sets `approvalPolicy: "untrusted"`,
 * `approvalsReviewer: "user"` and the full-access sandbox. Every request then reaches the app, which shows it under
 * "Ask first" and answers it by itself under the automatic mode (its own `AutoApproval`). A thread the helper started in
 * a read-only mode (Plan) keeps no write: the filter declines its command and file-change requests itself. Any other
 * use of the program (`--version`, sign-in) runs the program directly.
 *
 * Only the phone's own helper config gets it: a computer running Paseo has its own config, which the app never writes.
 */
public object PhoneCodexSandbox {
    public const val PROVIDER: String = "codex"

    /** The program the filter runs: the app's launcher for its pinned Codex. */
    public const val CODEX: String = "/home/oc/.local/bin/codex"
    public const val PYTHON: String = "/usr/bin/python3"

    /** The filter, run as `python3 -c GUARD <codex> app-server …` (no file to install; it travels in the config). */
    public val GUARD: String = """import json, os, subprocess, sys, threading
argv = sys.argv[1:]
if len(argv) < 2 or argv[1] != "app-server":
    os.execv(argv[0], argv)
child = subprocess.Popen(argv, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
lock = threading.Lock()
read_only = set()
starts = {}
APPROVALS = ("item/commandExecution/requestApproval", "item/fileChange/requestApproval", "execCommandApproval", "applyPatchApproval")
def send(data):
    if isinstance(data, dict):
        data = (json.dumps(data) + "\n").encode()
    with lock:
        try:
            child.stdin.write(data)
            child.stdin.flush()
        except (BrokenPipeError, ValueError, OSError):
            pass
def read_only_of(params):
    sandbox = params.get("sandbox")
    policy = params.get("sandboxPolicy")
    if sandbox is None and policy is None:
        return None
    return sandbox == "read-only" or (isinstance(policy, dict) and policy.get("type") == "readOnly")
def up():
    for line in sys.stdin.buffer:
        try:
            message = json.loads(line)
        except Exception:
            send(line)
            continue
        method = message.get("method") if isinstance(message, dict) else None
        params = message.get("params") if isinstance(message, dict) else None
        if method in ("thread/start", "thread/resume", "thread/fork", "turn/start") and isinstance(params, dict):
            ro = read_only_of(params)
            thread = params.get("threadId")
            if thread is not None and ro is not None:
                if ro:
                    read_only.add(thread)
                else:
                    read_only.discard(thread)
            if method != "turn/start" and "id" in message and ro is not None:
                starts[message["id"]] = ro
            params["approvalPolicy"] = "untrusted"
            params["approvalsReviewer"] = "user"
            if method == "turn/start":
                params["sandboxPolicy"] = {"type": "dangerFullAccess"}
            else:
                params["sandbox"] = "danger-full-access"
            send(message)
            continue
        send(line)
    with lock:
        try:
            child.stdin.close()
        except OSError:
            pass
def down():
    out = sys.stdout.buffer
    for line in child.stdout:
        try:
            message = json.loads(line)
        except Exception:
            message = None
        if isinstance(message, dict):
            if "id" in message and "method" not in message and message["id"] in starts:
                ro = starts.pop(message["id"])
                thread = ((message.get("result") or {}).get("thread") or {}).get("id")
                if thread:
                    if ro:
                        read_only.add(thread)
                    else:
                        read_only.discard(thread)
            params = message.get("params") or {}
            owner = params.get("threadId") or params.get("conversationId")
            if "id" in message and message.get("method") in APPROVALS and owner in read_only:
                send({"jsonrpc": "2.0", "id": message["id"], "result": {"decision": "decline"}})
                continue
        out.write(line)
        out.flush()
threading.Thread(target=up, daemon=True).start()
down()
sys.exit(child.wait())
"""

    /** The helper's start command for Codex. */
    public val COMMAND: List<String> = listOf(PYTHON, "-c", GUARD, CODEX)

    /** [config] with the Codex entry set; the rest of it, and other keys of that entry, are kept. */
    public fun merge(config: String): String {
        val root = AgentJson.parseOrNull(config) as? JsonObject ?: return config
        val agents = root["agents"] as? JsonObject ?: JsonObject(emptyMap())
        val providers = LinkedHashMap((agents["providers"] as? JsonObject).orEmpty())
        val codex = LinkedHashMap((providers[PROVIDER] as? JsonObject).orEmpty())
        codex["command"] = JsonArray(COMMAND.map(::JsonPrimitive))
        // The sandbox is the filter's to set: an option here would hide a read-only mode from it.
        (codex["options"] as? JsonObject)?.let { options ->
            val kept = options.filterKeys { it != "sandbox_mode" && it != "approval_policy" }
            if (kept.isEmpty()) codex.remove("options") else codex["options"] = JsonObject(kept)
        }
        providers[PROVIDER] = JsonObject(codex)
        val newAgents = LinkedHashMap<String, JsonElement>(agents).apply { put("providers", JsonObject(providers)) }
        val newRoot = LinkedHashMap<String, JsonElement>(root).apply { put("agents", JsonObject(newAgents)) }
        return AgentJson.encode(JsonObject(newRoot))
    }
}

/** The phone helper's `config.json` text: the catalog's ([base], `PaseoConfig.render`), own agents, Codex's sandbox. */
public object PhoneHelperConfig {
    public fun compose(base: String, agents: List<OwnAgent>, env: Map<AgentId, Map<String, String>>): String =
        PhoneCodexSandbox.merge(OwnAgentConfig.merge(base, agents, env))
}
