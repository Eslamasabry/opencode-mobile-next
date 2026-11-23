package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// Ported from pull request 142 (the contributor's design): a provider endpoint the server's catalog does not list is
// declared in the server's own settings file, where the app owns that file (this phone's own OpenCode 2). OpenCode 2
// has no settings-write endpoint, so a remote server never offers it. The declaration never holds a key: the engine
// gives every declared provider a key method, and the key goes through the existing key flow afterwards.

/** The one runtime package the form declares: an API that speaks the OpenAI request format (built into 2.2.6). */
public const val CUSTOM_PROVIDER_PACKAGE: String = "@opencode/ai/providers/openai-compatible"

/** The form field a problem belongs to. */
public enum class CustomProviderField {
    Name,
    Id,
    Address,
    Models,
}

/** What the add-a-provider form refuses, one per field at most; never a value. */
public enum class CustomProviderProblem(public val field: CustomProviderField) {
    NameMissing(CustomProviderField.Name),
    NameInvalid(CustomProviderField.Name),
    IdMissing(CustomProviderField.Id),
    IdInvalid(CustomProviderField.Id),
    /** The server already lists a provider with this id: a declaration would patch it (and could reroute its key). */
    IdTaken(CustomProviderField.Id),
    AddressInvalid(CustomProviderField.Address),
    /** A user name or password in the address: it would sit in a settings file other tools read. */
    AddressHasCredentials(CustomProviderField.Address),
    ModelsMissing(CustomProviderField.Models),
    ModelInvalid(CustomProviderField.Models),
    TooManyModels(CustomProviderField.Models),
}

/** What the person typed, as typed. [models] holds one model id per line. */
public data class CustomProviderDraft(
    val name: String,
    val id: String,
    val address: String,
    val models: String,
) {
    /** The id as it is written: trimmed and lower case. */
    val providerId: String
        get() = id.trim().lowercase()

    /** Every problem, field by field, against the ids the server already has ([taken], any case). */
    public fun problems(taken: Set<String>): List<CustomProviderProblem> = check(taken).first

    /** The declaration, or null when [problems] is not empty. */
    public fun declaration(taken: Set<String>): CustomProviderDeclaration? = check(taken).second

    private fun check(taken: Set<String>): Pair<List<CustomProviderProblem>, CustomProviderDeclaration?> {
        val shownName = name.trim()
        val wanted = providerId
        val url = customProviderAddress(address)
        val ids = models.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val problems =
            listOfNotNull(nameProblem(shownName), idProblem(wanted, taken), url.problem, modelsProblem(ids))
        if (problems.isNotEmpty()) return problems to null
        val block = buildJsonObject {
            put("name", shownName)
            put("package", CUSTOM_PROVIDER_PACKAGE)
            putJsonObject("settings") { put("baseURL", url.value) }
            putJsonObject("models") { ids.forEach { putJsonObject(it) {} } }
        }
        return problems to CustomProviderDeclaration(wanted, shownName, block)
    }

    public companion object {
        /** OpenCode 2 takes any id without `/` or `#`; this narrower rule also keeps it a plain file key. */
        public val ID: Regex = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        public const val MAX_NAME: Int = 80
        public const val MAX_MODEL: Int = 128
        public const val MAX_MODELS: Int = 64
        public const val MAX_ADDRESS: Int = 2048
    }
}

private fun nameProblem(name: String): CustomProviderProblem? =
    when {
        name.isEmpty() -> CustomProviderProblem.NameMissing
        name.length > CustomProviderDraft.MAX_NAME || name.any(::control) -> CustomProviderProblem.NameInvalid
        else -> null
    }

private fun idProblem(id: String, taken: Set<String>): CustomProviderProblem? =
    when {
        id.isEmpty() -> CustomProviderProblem.IdMissing
        !CustomProviderDraft.ID.matches(id) -> CustomProviderProblem.IdInvalid
        taken.any { it.lowercase() == id } -> CustomProviderProblem.IdTaken
        else -> null
    }

/** One printable id per line: no space, no control character and no `#` (OpenCode 2 reads `#` as a variant). */
private fun modelsProblem(ids: List<String>): CustomProviderProblem? =
    when {
        ids.isEmpty() -> CustomProviderProblem.ModelsMissing
        ids.any(::modelInvalid) -> CustomProviderProblem.ModelInvalid
        ids.size > CustomProviderDraft.MAX_MODELS -> CustomProviderProblem.TooManyModels
        else -> null
    }

private fun modelInvalid(id: String): Boolean =
    id.length > CustomProviderDraft.MAX_MODEL || id.any { it <= ' ' || it == '#' || control(it) }

/**
 * The declared `providers.<providerId>` block: name, package, `settings.baseURL` and models. Never a key.
 * [replaceDeclared] is the engine's decision (issue 152) that the settings file's entry under this id is stranded —
 * no live connection and no catalog entry rides on it — so the writer replaces it instead of refusing; the script
 * never decides this itself, and still refuses when the old entry is not this app's own shape (a field the writer
 * never writes, e.g. an inline key), so nothing a person wrote by hand is lost. [requireDeclared] is the same
 * decision's other half: the write goes through only when the file really holds an entry under this id — the
 * script, the file's one reader, refuses when it does not.
 */
public data class CustomProviderDeclaration(
    val providerId: String,
    val name: String,
    val block: JsonObject,
    val replaceDeclared: Boolean = false,
    val requireDeclared: Boolean = false,
) {
    /** The run's stdin: the only place the person's words travel (never the script, a process argument). */
    public fun input(): String =
        buildJsonObject {
                put("id", providerId)
                put("block", block)
                put("replaceDeclared", replaceDeclared)
                put("requireDeclared", requireDeclared)
            }
            .toString()
}

internal data class CheckedAddress(val value: String?, val problem: CustomProviderProblem?)

/**
 * An http(s) base address with a host, no user name or password, no fragment, no space or control character. The
 * scheme is written lower case; the rest stays as typed.
 */
internal fun customProviderAddress(raw: String): CheckedAddress {
    val value = raw.trim()
    val problem = addressProblem(value)
    val scheme = value.substringBefore("://", "").lowercase()
    return if (problem == null) CheckedAddress(scheme + "://" + value.substringAfter("://"), null)
    else CheckedAddress(null, problem)
}

private fun addressProblem(value: String): CustomProviderProblem? {
    val scheme = value.substringBefore("://", "").lowercase()
    val authority = value.substringAfter("://", "").takeWhile { it != '/' && it != '?' }
    return when {
        value.isEmpty() || value.length > CustomProviderDraft.MAX_ADDRESS -> CustomProviderProblem.AddressInvalid
        value.any { it <= ' ' || it == '\\' || it == '#' || control(it) } -> CustomProviderProblem.AddressInvalid
        scheme != "http" && scheme != "https" -> CustomProviderProblem.AddressInvalid
        '@' in authority -> CustomProviderProblem.AddressHasCredentials
        !hostAndPortValid(authority) -> CustomProviderProblem.AddressInvalid
        else -> null
    }
}

/** `host`, `host:port`, `[v6]` or `[v6]:port`; host names in ASCII (an international name is typed as punycode). */
private fun hostAndPortValid(authority: String): Boolean {
    val close = if (authority.startsWith('[')) authority.indexOf(']') else -1
    val bracketed = close > 1
    val host =
        when {
            bracketed -> authority.substring(1, close)
            authority.startsWith('[') -> ""
            else -> authority.substringBefore(':')
        }
    val after = if (bracketed) authority.substring(close + 1) else authority.removePrefix(host)
    val port = after.removePrefix(":")
    val hostOk = host.isNotEmpty() && host.all { if (bracketed) v6Char(it) else hostChar(it) }
    val portOk =
        after.isEmpty() ||
            (after.startsWith(':') &&
                port.length in 1..MAX_PORT_DIGITS &&
                port.all(Char::isDigit) &&
                port.toInt() in 1..MAX_PORT)
    return hostOk && portOk
}

private fun hostChar(c: Char): Boolean =
    c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '-' || c == '_'

private fun v6Char(c: Char): Boolean = c in 'a'..'f' || c in 'A'..'F' || c in '0'..'9' || c == ':' || c == '.'

private fun control(c: Char): Boolean = c.code < FIRST_PRINTABLE || c.code in DELETE..LAST_C1

private const val FIRST_PRINTABLE = 0x20
private const val DELETE = 0x7f
private const val LAST_C1 = 0x9f
private const val MAX_PORT_DIGITS = 5
private const val MAX_PORT = 65_535

/** What the settings-file write reported (its exit status is the whole protocol; output is never read). */
public enum class CustomProviderWrite(public val code: Int) {
    Saved(code = 0),
    /** The settings folder or the system Python is missing: OpenCode on this phone is not set up. */
    NotSetUp(code = 20),
    /** The file is not a JSON object (comments, hand edits), `providers` is not one, or it is over 1 MiB. */
    Unreadable(code = 21),
    /** A symlink, a foreign owner, a folder others can write, or a wrong file type on the way. */
    Unsafe(code = 22),
    /** Another settings change holds the lock. */
    Busy(code = 23),
    /**
     * The file already declares this id and the run was not told to replace it, the entry is not this app's own
     * shape, or a run that required a declared entry found none.
     */
    AlreadyDeclared(code = 24),
    /** A hand-kept `opencode.jsonc` sits beside it; the app leaves hand-kept settings alone. */
    HandManaged(code = 25),
    /** The write itself failed, or the input was refused. */
    Failed(code = 26),
    /** The file holds no entry under this id (the remove step). */
    NoEntry(code = 27),
    ;

    public companion object {
        /** Any other status (a time-out, a missing shell) is [Failed]. */
        public fun ofExitCode(code: Int): CustomProviderWrite = entries.firstOrNull { it.code == code } ?: Failed
    }
}

/**
 * The fixed script that writes one declaration into this phone's OpenCode 2 settings file (`OPENCODE_CONFIG_DIR` of
 * the start script and the cold-start recipe), run as the engine's own (proot) root through the built-in Linux run
 * path. The script holds no word the person typed: the declaration is the run's stdin
 * ([CustomProviderDeclaration.input]) and the Python program is fixed text in single quotes ([PROGRAM] has none).
 *
 * Guards (the card installer's, `CardToolJs.INSTALLER_PY`): every ancestor checked for symlinks, owner and others'
 * write; reads with `O_NOFOLLOW` under a 1 MiB ceiling; the card installer's lock is taken too, because both rewrite
 * this file; a hand-kept `opencode.jsonc` is never overwritten; temp file, fsync, atomic replace. A told replace
 * still takes only an entry whose every field is one this app's writer writes (name, package, settings.baseURL,
 * models): a hand-written entry, down to one extra field, keeps the refusal. A run told to require a declaration
 * keeps the same refusal when the file holds no entry under the id.
 */
public object CustomProviderScript {
    public const val CONFIG_DIR: String = "/root/.oc-opencode2/config/opencode"

    /** The card installer's OpenCode 2 folder; its `lock` serialises the two writers of the same file. */
    public const val CARDS_DIR: String = "/root/.oc-genui/openCode2"

    /** The read step's whole stdin: the request names the step and nothing else. */
    public const val READ_INPUT: String = """{"op":"read"}"""

    /** The remove step's stdin for one id: the id travels on stdin only, never a command line. */
    public fun removeInput(id: String): String =
        buildJsonObject {
                put("op", "remove")
                put("id", id)
            }
            .toString()

    public fun script(): String =
        "set -eu\n" +
            "umask 077\n" +
            "# Python is a system executable, never a provider- or agent-owned runtime. The block arrives on stdin.\n" +
            "[ -x /usr/bin/python3 ] || exit 20\n" +
            "exec /usr/bin/python3 -I -c '" + PROGRAM + "' '" + CONFIG_DIR + "' '" + CARDS_DIR + "' 0 2>/dev/null\n"

    /**
     * argv: settings folder, card installer folder, the engine's uid. stdin is one of three steps: the write's
     * `{"id": …, "block": {…}, "replaceDeclared": bool, "requireDeclared": bool}`, [READ_INPUT] (no lock, one JSON
     * line per entry) or [removeInput] (both locks). Any other `"op"` is refused.
     */
    public const val PROGRAM: String =
        """import os, sys, json, stat, pathlib, tempfile, fcntl, re
class Stop(Exception):
    def __init__(self, code): self.code = code
def fail(code): raise Stop(code)
LIMIT = 1048576
OWNER = 0
def checked(path, folder):
    path = pathlib.Path(path)
    for part in [*reversed(path.parents), path]:
        try: info = os.lstat(part)
        except FileNotFoundError: return False
        if stat.S_ISLNK(info.st_mode) or info.st_uid != OWNER or info.st_mode & 0o022: fail(22)
        if part != path or folder:
            if not stat.S_ISDIR(info.st_mode): fail(22)
        elif not stat.S_ISREG(info.st_mode): fail(22)
    return True
def hold(path):
    checked(path, False)
    try: handle = os.open(path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    except OSError: fail(22)
    try: fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError: fail(23)
    return handle
def read(path):
    if not checked(path, False): return None
    handle = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(handle, "rb") as stream: raw = stream.read(LIMIT + 1)
    if len(raw) > LIMIT: fail(21)
    return raw
def replace(folder, path, data):
    handle, temporary = tempfile.mkstemp(prefix=".oc-provider-", dir=folder)
    try:
        with os.fdopen(handle, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.lexists(temporary): os.unlink(temporary)
    try:
        directory = os.open(folder, os.O_RDONLY)
        try: os.fsync(directory)
        finally: os.close(directory)
    except OSError: pass
def app_shaped(entry):
    if not isinstance(entry, dict) or not set(entry) <= {"name", "package", "settings", "models"}: return False
    if not all(isinstance(entry[key], str) for key in ("name", "package") if key in entry): return False
    settings = entry.get("settings")
    if settings is not None and (not isinstance(settings, dict) or not set(settings) <= {"baseURL"} or not all(isinstance(value, str) for value in settings.values())): return False
    models = entry.get("models")
    if models is not None and (not isinstance(models, dict) or any(value != {} for value in models.values())): return False
    return True
def app_written(entry):
    if not isinstance(entry, dict) or set(entry) != {"name", "package", "settings", "models"}: return False
    if not all(isinstance(entry[key], str) for key in ("name", "package")): return False
    settings = entry.get("settings")
    if not isinstance(settings, dict) or set(settings) != {"baseURL"}: return False
    if not isinstance(settings.get("baseURL"), str): return False
    models = entry.get("models")
    if not isinstance(models, dict) or any(value != {} for value in models.values()): return False
    return True
def loaded(folder):
    raw = read(folder + "/opencode.json")
    config = {}
    if raw is not None and raw.strip():
        try: config = json.loads(raw)
        except Exception: fail(21)
        if not isinstance(config, dict): fail(21)
    return config
def read_all(folder):
    if not checked(folder, True): fail(20)
    if os.path.lexists(folder + "/opencode.jsonc"): fail(25)
    providers = loaded(folder).get("providers", {})
    if not isinstance(providers, dict): fail(21)
    for identity in sorted(providers):
        entry = providers[identity]
        if app_written(entry):
            line = {"id": identity, "appShaped": True, "name": entry["name"], "baseURL": entry["settings"]["baseURL"]}
            line["models"] = list(entry["models"])
        else:
            line = {"id": identity, "appShaped": False}
        print(json.dumps(line, separators=(",", ":")))
def remove_one(folder, cards, payload):
    identity = payload.get("id")
    if not isinstance(identity, str) or not re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,63}", identity): fail(26)
    if not checked(folder, True): fail(20)
    locks = [hold(folder + "/.oc-provider.lock")]
    if checked(cards, True): locks.append(hold(cards + "/lock"))
    if os.path.lexists(folder + "/opencode.jsonc"): fail(25)
    config = loaded(folder)
    providers = config.get("providers", {})
    if not isinstance(providers, dict): fail(21)
    if identity not in providers: fail(27)
    if not app_written(providers[identity]): fail(24)
    del providers[identity]
    config["providers"] = providers
    data = (json.dumps(config, indent=2) + "\n").encode()
    if len(data) > LIMIT: fail(21)
    try: replace(folder, folder + "/opencode.json", data)
    except Exception: fail(26)
def main():
    global OWNER
    folder, cards, OWNER = sys.argv[1], sys.argv[2], int(sys.argv[3])
    if os.getuid() != OWNER: fail(22)
    raw = sys.stdin.buffer.read(262145)
    if len(raw) > 262144: fail(26)
    try: payload = json.loads(raw)
    except Exception: fail(26)
    if not isinstance(payload, dict): fail(26)
    op = payload.get("op")
    if op == "read": return read_all(folder)
    if op == "remove": return remove_one(folder, cards, payload)
    if op is not None: fail(26)
    identity, block = payload.get("id"), payload.get("block")
    replace_entry = payload.get("replaceDeclared", False)
    require_declared = payload.get("requireDeclared", False)
    if not isinstance(identity, str) or not re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,63}", identity): fail(26)
    if not isinstance(block, dict) or set(block) != {"name", "package", "settings", "models"}: fail(26)
    if not isinstance(replace_entry, bool): fail(26)
    if not isinstance(require_declared, bool): fail(26)
    if not checked(folder, True): fail(20)
    locks = [hold(folder + "/.oc-provider.lock")]
    if checked(cards, True): locks.append(hold(cards + "/lock"))
    if os.path.lexists(folder + "/opencode.jsonc"): fail(25)
    path = folder + "/opencode.json"
    config = loaded(folder)
    providers = config.get("providers", {})
    if not isinstance(providers, dict): fail(21)
    if require_declared and not (identity in providers and app_shaped(providers[identity])): fail(24)
    if identity in providers and not (replace_entry and app_shaped(providers[identity])): fail(24)
    providers[identity] = block
    config["providers"] = providers
    data = (json.dumps(config, indent=2) + "\n").encode()
    if len(data) > LIMIT: fail(21)
    try: replace(folder, path, data)
    except Exception: fail(26)
try: main()
except Stop as stop: sys.exit(stop.code)
except Exception: sys.exit(26)
"""
}

/**
 * Present only where the app owns the server's settings file: this phone's own OpenCode. A remote server has none:
 * its [LibrarySource.customProviders] is null and its `ServerCapabilities.customProviders` is false.
 */
public interface CustomProviderHost {
    /** Runs [CustomProviderScript] with [declaration] on stdin; the run's exit status, or a failed run. */
    public suspend fun write(declaration: CustomProviderDeclaration): Outcome<Int>

    /**
     * The read step: the run's exit status and its stdout (one JSON line per entry, the script's whole output).
     * Never logged: a hand-written entry's values are not the app's to print.
     */
    public suspend fun read(): Outcome<Pair<Int, String>>

    /** The remove step for one id on stdin; the run's exit status, or a failed run. */
    public suspend fun remove(id: String): Outcome<Int>

    /** Restarts this phone's OpenCode through its one restart path (stop, then the manual start). */
    public suspend fun restart(): Outcome<Unit>
}

/**
 * One `providers.<id>` entry as the read step reports it. A hand-written entry carries its id and
 * [appShaped] false only: its values are not the app's to read, change or print.
 */
public data class DeclaredProvider(
    val id: String,
    val appShaped: Boolean,
    val name: String? = null,
    val baseUrl: String? = null,
    val models: List<String> = emptyList(),
)

/** Parses the read step's output: one JSON object per line, sorted by id; a bad line is skipped. */
public fun parseDeclaredProviders(output: String): List<DeclaredProvider> =
    output
        .lineSequence()
        .mapNotNull { line ->
            try {
                Json.parseToJsonElement(line)
            } catch (_: kotlinx.serialization.SerializationException) {
                null
            }
        }
        .mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val id = (entry["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val shaped = (entry["appShaped"] as? JsonPrimitive)?.booleanOrNull == true
            if (!shaped) return@mapNotNull DeclaredProvider(id, appShaped = false)
            DeclaredProvider(
                id,
                appShaped = true,
                name = (entry["name"] as? JsonPrimitive)?.contentOrNull,
                baseUrl = (entry["baseURL"] as? JsonPrimitive)?.contentOrNull,
                models =
                    (entry["models"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .orEmpty(),
            )
        }
        .sortedBy { it.id }
        .toList()

/** Why nothing was saved. */
public enum class CustomProviderRefusal {
    /** This server keeps its own settings: the app cannot write them. */
    Unavailable,
    /** The server or project changed while the page was open. */
    Changed,
    /** The server did not answer, so the id could not be checked as free. */
    CouldNotCheck,
    NotSetUp,
    Unreadable,
    HandManaged,
    Unsafe,
    Busy,
    AlreadyDeclared,
    /** The entry under this id is not one this app wrote (or is gone): hand-written settings are left alone. */
    ChangedByHand,
    Failed,
}

/** Where an add, an edit or a remove (or its restart) ended. "Saved" and "loaded" are never one state. */
public sealed interface CustomProviderOutcome {
    /** In the settings file, and the server lists it: its key comes next. */
    public data object Loaded : CustomProviderOutcome

    /** In the settings file; the running server has not picked it up by itself. */
    public data object SavedNotLoaded : CustomProviderOutcome

    /** The id is already an app-written entry: Save asks before anything is written (4A). */
    public data object NeedsReplaceAnswer : CustomProviderOutcome

    /** In the settings file; the restart failed ([detail] is a stable technical label). */
    public data class RestartFailed(val detail: String) : CustomProviderOutcome

    /** In the settings file; the server restarted and still does not list it. */
    public data object NotLoadedAfterRestart : CustomProviderOutcome

    /** Removed from the settings file; the server still lists it until OpenCode restarts. */
    public data object RemovedNotRestarted : CustomProviderOutcome

    /**
     * The saved key is gone but the entry stayed: the file write refused or failed
     * ([detail] is a stable technical label). A second remove finishes the job.
     */
    public data class RemovePartial(val detail: String) : CustomProviderOutcome

    /** Removed and the restarted server no longer lists it. */
    public data object Removed : CustomProviderOutcome

    /** Removed from the settings file, but the restarted server still lists it. */
    public data object NotGoneAfterRestart : CustomProviderOutcome

    /** Nothing was changed. [detail] is a stable technical label for the Details fold, when there is one. */
    public data class Refused(val reason: CustomProviderRefusal, val detail: String? = null) : CustomProviderOutcome

    /** The form has problems; nothing was sent. */
    public data class Invalid(val problems: List<CustomProviderProblem>) : CustomProviderOutcome
}

internal fun CustomProviderWrite.refusal(): CustomProviderRefusal =
    when (this) {
        CustomProviderWrite.Saved,
        CustomProviderWrite.Failed -> CustomProviderRefusal.Failed
        CustomProviderWrite.NotSetUp -> CustomProviderRefusal.NotSetUp
        CustomProviderWrite.Unreadable -> CustomProviderRefusal.Unreadable
        CustomProviderWrite.Unsafe -> CustomProviderRefusal.Unsafe
        CustomProviderWrite.Busy -> CustomProviderRefusal.Busy
        CustomProviderWrite.AlreadyDeclared -> CustomProviderRefusal.AlreadyDeclared
        CustomProviderWrite.HandManaged -> CustomProviderRefusal.HandManaged
        // The entry is gone (27) or not the app's own shape (24) any more: a hand changed it.
        CustomProviderWrite.NoEntry -> CustomProviderRefusal.ChangedByHand
    }
