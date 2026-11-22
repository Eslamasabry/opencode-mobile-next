package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
 * no list shows the id — so the writer replaces it instead of refusing; the script never decides this itself.
 */
public data class CustomProviderDeclaration(
    val providerId: String,
    val name: String,
    val block: JsonObject,
    val replaceDeclared: Boolean = false,
) {
    /** The run's stdin: the only place the person's words travel (never the script, a process argument). */
    public fun input(): String =
        buildJsonObject {
                put("id", providerId)
                put("block", block)
                put("replaceDeclared", replaceDeclared)
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
    /** The file already declares this id and the run was not told to replace it. */
    AlreadyDeclared(code = 24),
    /** A hand-kept `opencode.jsonc` sits beside it; the app leaves hand-kept settings alone. */
    HandManaged(code = 25),
    /** The write itself failed, or the input was refused. */
    Failed(code = 26),
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
 * this file; a hand-kept `opencode.jsonc` is never overwritten; temp file, fsync, atomic replace.
 */
public object CustomProviderScript {
    public const val CONFIG_DIR: String = "/root/.oc-opencode2/config/opencode"

    /** The card installer's OpenCode 2 folder; its `lock` serialises the two writers of the same file. */
    public const val CARDS_DIR: String = "/root/.oc-genui/openCode2"

    public fun script(): String =
        "set -eu\n" +
            "umask 077\n" +
            "# Python is a system executable, never a provider- or agent-owned runtime. The block arrives on stdin.\n" +
            "[ -x /usr/bin/python3 ] || exit 20\n" +
            "exec /usr/bin/python3 -I -c '" + PROGRAM + "' '" + CONFIG_DIR + "' '" + CARDS_DIR + "' 0 2>/dev/null\n"

    /** argv: settings folder, card installer folder, the engine's uid. stdin: `{"id": …, "block": {…}, "replaceDeclared": bool}`. */
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
def main():
    global OWNER
    folder, cards, OWNER = sys.argv[1], sys.argv[2], int(sys.argv[3])
    if os.getuid() != OWNER: fail(22)
    raw = sys.stdin.buffer.read(262145)
    if len(raw) > 262144: fail(26)
    try: payload = json.loads(raw)
    except Exception: fail(26)
    if not isinstance(payload, dict): fail(26)
    identity, block = payload.get("id"), payload.get("block")
    replace_entry = payload.get("replaceDeclared", False)
    if not isinstance(identity, str) or not re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,63}", identity): fail(26)
    if not isinstance(block, dict) or set(block) != {"name", "package", "settings", "models"}: fail(26)
    if not isinstance(replace_entry, bool): fail(26)
    if not checked(folder, True): fail(20)
    locks = [hold(folder + "/.oc-provider.lock")]
    if checked(cards, True): locks.append(hold(cards + "/lock"))
    if os.path.lexists(folder + "/opencode.jsonc"): fail(25)
    path = folder + "/opencode.json"
    raw = read(path)
    config = {}
    if raw is not None and raw.strip():
        try: config = json.loads(raw)
        except Exception: fail(21)
        if not isinstance(config, dict): fail(21)
    providers = config.get("providers", {})
    if not isinstance(providers, dict): fail(21)
    if identity in providers and not replace_entry: fail(24)
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

    /** Restarts this phone's OpenCode through its one restart path (stop, then the manual start). */
    public suspend fun restart(): Outcome<Unit>
}

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
    Failed,
}

/** Where an add (or its restart) ended. "Saved" and "loaded" are never one state. */
public sealed interface CustomProviderOutcome {
    /** In the settings file, and the server lists it: its key comes next. */
    public data object Loaded : CustomProviderOutcome

    /** In the settings file; the running server has not picked it up by itself. */
    public data object SavedNotLoaded : CustomProviderOutcome

    /** In the settings file; the restart failed ([detail] is a stable technical label). */
    public data class RestartFailed(val detail: String) : CustomProviderOutcome

    /** In the settings file; the server restarted and still does not list it. */
    public data object NotLoadedAfterRestart : CustomProviderOutcome

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
    }
