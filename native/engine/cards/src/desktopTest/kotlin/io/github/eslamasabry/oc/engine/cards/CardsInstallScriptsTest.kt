package io.github.eslamasabry.oc.engine.cards

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Flutter `gen_ui_install_test.dart` "isolated persistent ownership transaction" (B-CHAT-041, B-CHAT-042): the real
 * installer Python from [CardsInstallScripts] runs against a temporary tree with only host identity and runtime
 * execution replaced (the same harness the Flutter test uses), never the file logic. Needs /usr/bin/python3.
 */
class CardsInstallScriptsTest {
    private val root: File = Files.createTempDirectory("genui-install-").toFile()

    @AfterTest
    fun clean() {
        root.deleteRecursively()
    }

    private val managed get() = File(root, "managed")
    private val marker get() = File(managed, "enabled")
    private val settings get() = File(root, "config/settings.json")

    private fun apply(
        owner: String,
        enabled: Boolean = true,
        failCheck: Boolean = false,
        verify: Boolean = false,
        wrongVersion: Boolean = false,
        requireMarker: Boolean = false,
        runtimes: Set<String> = setOf("/usr/bin/node"),
        unsafePath: String? = null,
        foreignOwner: Boolean = false,
        agent: CardAgent = CardAgent.OpenCode2,
    ): Int {
        val script = if (verify) CardsInstallScripts.verification(owner, agent) else CardsInstallScripts.install(owner, agent, enabled)
        val encoded = Regex("python3 - '([^']+)'").find(script)!!.groupValues[1]
        val data = Json.parseToJsonElement(Base64.getDecoder().decode(encoded).decodeToString()).jsonObject.toMutableMap()
        data["directory"] = JsonPrimitive("${root.path}/managed")
        data["config"] = JsonPrimitive("${root.path}/config/opencode.json")
        val source = script.split("<<'OC_GENUI_SETUP' 2>/dev/null\n")[1].split("\nOC_GENUI_SETUP")[0]
        val py = { b: Boolean -> if (b) "True" else "False" }
        val runtimesJson = JsonArray(runtimes.map(::JsonPrimitive)).toString()
        val unsafeJson = JsonPrimitive(unsafePath ?: "").toString()
        val harness =
            source.replaceFirst(
                "try: main()",
                """
os.getuid = lambda: ${if (agent == CardAgent.Claude) 1000 else 0}
runtime_paths = {'/usr/bin/node', '/opt/node/bin/node', '/opt/oc-node/bin/node'}
available = set($runtimesJson)
unsafe_path = $unsafeJson
original_access = os.access
os.access = lambda path, mode: (path in available) if path in runtime_paths else (True if (path.startswith('/home/oc/.local/share/oc-agents/claude/') or path == '/home/oc/.local/node/bin/node') else original_access(path, mode))
original_stat = os.stat
def owned_stat(path, *args, **kwargs):
    path = str(path)
    try: info = original_stat(path, *args, **kwargs)
    except FileNotFoundError:
        if path not in runtime_paths and not path.startswith('/opt/'): raise
        info = os.stat_result([stat.S_IFREG | 0o755,0,0,1,0,0,0,0,0,0])
    values = list(info)
    values[4] = 1000 if path == unsafe_path and ${py(foreignOwner)} else 0
    values[0] &= ~0o022
    if path == unsafe_path and not ${py(foreignOwner)}: values[0] |= 0o020
    return os.stat_result(values)
os.stat = owned_stat
original_safe = safe
safe = lambda path, *args, **kwargs: (path in available) if str(path) in runtime_paths else (True if (str(path).startswith('/home/oc/.local/share/oc-agents/claude/') or str(path) == '/home/oc/.local/node/bin/node') else original_safe(path, *args, **kwargs))
def check(*args, **kwargs):
    data = json.loads(base64.b64decode(sys.argv[1]))
    argv = args[0]
    if ${py(requireMarker)} and read(data['directory'] + '/enabled') != b'enabled\n':
        return subprocess.CompletedProcess(args, 1, b'')
    output = b'{"jsonrpc":"2.0","id":1,"result":{}}\n'
    if argv == [data['cli'], '--version']:
        output = ('${if (wrongVersion) "0.0.0" else "2.1.316"} (Claude Code)\n').encode()
    elif argv == [data['node'], '--version']:
        output = (data['nodeVersion']+'\n').encode()
    elif argv == [data['cli'], 'mcp', 'list']:
        output = ('Checking MCP server health…\n\noc-ui: '+data['node']+' '+data['directory']+'/server.cjs - ✔ Connected\n').encode()
    elif data['verify']:
        messages = [json.loads(row) for row in kwargs['input'].splitlines()]
        assert messages[-1]['method'] == 'tools/list'
        output += b'{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"show","inputSchema":{}},{"name":"find_connectors","inputSchema":{}}]}}\n'
    return subprocess.CompletedProcess(args, ${if (failCheck) 1 else 0}, output)
subprocess.run = check
def fake_cli(args, env):
    data = json.loads(base64.b64decode(sys.argv[1]))
    if env['HOME'] != data['home'] or env['CLAUDE_CONFIG_DIR'] != str(pathlib.Path(data['config']).parent):
        return False
    if args[1:3] not in [['mcp','add'], ['mcp','remove']]: return False
    config = read_json(data['config'])
    entries = slot(config, 'claude', True)
    if args[2] == 'add':
        if args[3:] != ['--scope','user','--transport','stdio','oc-ui','--',data['node'],data['directory']+'/server.cjs']: return False
        entries['oc-ui'] = {'type':'stdio','command':data['node'],'args':[data['directory']+'/server.cjs'],'env':{}}
    else:
        if args[3:] != ['--scope','user','oc-ui']: return False
        entries.pop('oc-ui', None)
    save_json(data['config'], config)
    return True
cli = fake_cli
try: main()""",
            )
        val process =
            ProcessBuilder("/usr/bin/python3", "-", Base64.getEncoder().encodeToString(JsonObject(data).toString().encodeToByteArray()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        process.outputStream.use { it.write(harness.encodeToByteArray()) }
        check(process.waitFor(60, TimeUnit.SECONDS)) { "installer did not finish" }
        return process.exitValue()
    }

    private fun config(): JsonObject = Json.parseToJsonElement(File(root, "config/opencode.json").readText()).jsonObject

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun JsonElement.at(vararg keys: String): JsonElement? =
        keys.fold(this as JsonElement?) { acc, key -> (acc as? JsonObject)?.get(key) }

    private fun mkConfig() = File(root, "config").mkdirs()

    @Test
    fun `B-CHAT-041 user JSONC and unowned entries are never overwritten`() {
        mkConfig()
        val jsonc = File(root, "config/opencode.jsonc").apply { writeText("""{"model":"user/model"}""") }
        assertEquals(21, apply("one"))
        assertEquals("""{"model":"user/model"}""", jsonc.readText())
        assertFalse(marker.exists())
        jsonc.delete()
        val file = File(root, "config/opencode.json")
        val original = """{"mcp":{"servers":{"oc-ui":{"command":["custom"]}}},"keep":42}"""
        file.writeText(original)
        assertEquals(21, apply("one"))
        assertEquals(original, file.readText())
        assertFalse(marker.exists())
    }

    @Test
    fun `B-CHAT-041 OpenCode 2 persists direct tools in its own mcp servers config and is never verified ready`() {
        assertEquals(0, apply("one", runtimes = setOf("/opt/node/bin/node")))
        assertEquals(
            json("""{"type":"local","command":["/opt/node/bin/node","${root.path}/managed/server.cjs"],"disabled":false,"codemode":false}"""),
            config().at("mcp", "servers", "oc-ui"),
        )
        assertEquals(24, apply("one", verify = true, runtimes = setOf("/opt/node/bin/node")))
        assertEquals(10, apply("one", enabled = false, runtimes = emptySet()))
        assertEquals(JsonObject(emptyMap()), config().at("mcp", "servers"))
    }

    @Test
    fun `B-CHAT-041 symlinked or foreign-owned directories and an unsafe runtime refuse writes`() {
        assertEquals(22, apply("one", runtimes = setOf("/opt/node/bin/node", "/usr/bin/node"), unsafePath = "/opt/node/bin/node"))
        assertFalse(marker.exists())
        managed.mkdirs()
        assertEquals(22, apply("one", unsafePath = managed.path))
        assertEquals(22, apply("one", unsafePath = managed.path, foreignOwner = true))
        assertFalse(File(managed, "server.cjs").exists())
        managed.deleteRecursively()
        val outside = File(root, "outside").apply { mkdirs() }
        Files.createSymbolicLink(managed.toPath(), outside.toPath())
        assertEquals(22, apply("one"))
        assertEquals(0, outside.list()!!.size)
    }

    @Test
    fun `B-CHAT-041 two owners install idempotently and the last disable removes only its own entry`() {
        mkConfig()
        File(root, "config/opencode.json").writeText("""{"model":"keep","mcp":{"servers":{"other":{"disabled":true}}}}""")
        assertEquals(0, apply("one"))
        assertEquals(0, apply("one"))
        assertEquals(0, apply("two"))
        assertEquals(10, apply("one", enabled = false))
        assertTrue(config().at("mcp", "servers", "oc-ui") != null)
        assertEquals("enabled\n", marker.readText())
        assertEquals(10, apply("two", enabled = false))
        assertFalse(marker.exists())
        assertEquals(json("""{"model":"keep","mcp":{"servers":{"other":{"disabled":true}}}}"""), config())
    }

    @Test
    fun `B-CHAT-041 an edited owned entry is never removed`() {
        assertEquals(0, apply("one"))
        val file = File(root, "config/opencode.json")
        file.writeText(file.readText().replace(Regex("\"oc-ui\":\\{[^}]*\\}"), "\"oc-ui\":{\"command\":[\"user-edit\"]}"))
        assertEquals(21, apply("one", enabled = false))
        assertEquals(json("""{"command":["user-edit"]}"""), config().at("mcp", "servers", "oc-ui"))
    }

    @Test
    fun `B-CHAT-041 a failed self-check rolls back only the new writes`() {
        mkConfig()
        File(root, "config/opencode.json").writeText("""{"keep":42}""")
        assertEquals(24, apply("one", failCheck = true))
        assertEquals(JsonPrimitive(42), config()["keep"])
        assertEquals(JsonObject(emptyMap()), config().at("mcp", "servers"))
        assertFalse(marker.exists())
        assertFalse(File(managed, "owners.json").exists())
        assertFalse(File(managed, "server.cjs").exists())
    }

    @Test
    fun `B-CHAT-041 corrupt or orphaned helper files are repaired or left alone`() {
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        val helper = File(managed, "server.cjs")
        val expected = helper.readText()
        helper.writeText("damaged helper")
        marker.writeText("damaged marker")
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        assertEquals(expected, helper.readText())
        assertEquals("enabled\n", marker.readText())
        // A modified owned helper cannot block disable.
        helper.writeText("damaged helper")
        assertEquals(10, apply("one", agent = CardAgent.Claude, enabled = false))
        assertFalse(marker.exists())
    }

    @Test
    fun `B-CHAT-041 an unknown orphan helper stays an unowned collision`() {
        managed.mkdirs()
        val helper = File(managed, "server.cjs").apply { writeText("unrelated executable") }
        assertEquals(21, apply("one", agent = CardAgent.Claude))
        assertEquals("unrelated executable", helper.readText())
        assertFalse(File(managed, "owners.json").exists())
    }

    @Test
    fun `B-CHAT-041 an exact helper orphan from an interrupted first install is recovered`() {
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        File(managed, "owners.json").delete()
        marker.delete()
        File(root, "config/opencode.json").writeText("""{"unrelated":42}""")
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        assertEquals(JsonPrimitive(42), config()["unrelated"])
        assertEquals(0, apply("one", agent = CardAgent.Claude, verify = true))
    }

    @Test
    fun `B-CHAT-041 disabling where nothing is installed creates nothing and runs nothing`() {
        assertEquals(11, apply("one", enabled = false, failCheck = true))
        assertEquals(0, root.list()!!.size)
    }

    @Test
    fun `B-CHAT-042 Claude registration uses the profile environment at user scope`() {
        assertEquals(0, apply("profile-one", agent = CardAgent.Claude))
        assertTrue(config().at("mcpServers", "oc-ui") != null)
        assertEquals(10, apply("profile-one", agent = CardAgent.Claude, enabled = false))
        assertEquals(JsonObject(emptyMap()), config()["mcpServers"])
        assertFalse(marker.exists())
    }

    @Test
    fun `B-CHAT-042 the show rule is exact, idempotent, keeps user rules and is removed on disable`() {
        mkConfig()
        val original = """{"theme":"dark","permissions":{"allow":["Read","mcp__other__show"],"ask":["Bash"],"deny":["Write"]}}"""
        settings.writeText(original)
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        val allow = json("""["Read","mcp__other__show","mcp__oc-ui__show"]""")
        assertEquals(allow, json(settings.readText()).at("permissions", "allow"))
        assertEquals(json("""["Bash"]"""), json(settings.readText()).at("permissions", "ask"))
        assertEquals(json("""["Write"]"""), json(settings.readText()).at("permissions", "deny"))
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        assertEquals(allow, json(settings.readText()).at("permissions", "allow"))
        assertEquals(10, apply("one", agent = CardAgent.Claude, enabled = false))
        assertEquals(json(original), json(settings.readText()))
        // A rule the person already had stays theirs.
        val userOwned = """{"permissions":{"allow":["mcp__oc-ui__show","Read"]}}"""
        settings.writeText(userOwned)
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        assertEquals(10, apply("one", agent = CardAgent.Claude, enabled = false))
        assertEquals(json(userOwned), json(settings.readText()), "ownership resets after disable")
    }

    @Test
    fun `B-CHAT-042 a missing rule is found by verification and repaired`() {
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        settings.writeText("""{"permissions":{"allow":["Read"]}}""")
        assertEquals(24, apply("one", agent = CardAgent.Claude, verify = true))
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        assertEquals(0, apply("one", agent = CardAgent.Claude, verify = true))
        assertEquals(json("""["Read","mcp__oc-ui__show"]"""), json(settings.readText()).at("permissions", "allow"))
    }

    @Test
    fun `B-CHAT-042 malformed or symlinked settings are refused and a failed self-check rolls the rule back`() {
        mkConfig()
        settings.writeText("""{"permissions":{"allow":"Read"}}""")
        assertEquals(21, apply("one", agent = CardAgent.Claude))
        assertFalse(marker.exists())
        settings.delete()
        val outside = File(root, "outside.json").apply { writeText("{}") }
        Files.createSymbolicLink(settings.toPath(), outside.toPath())
        assertEquals(22, apply("one", agent = CardAgent.Claude))
        assertEquals("{}", outside.readText())
        Files.delete(settings.toPath())
        val original = """{"permissions":{"allow":["Read"]},"theme":"dark"}"""
        settings.writeText(original)
        assertEquals(24, apply("one", agent = CardAgent.Claude, failCheck = true))
        assertEquals(json(original), json(settings.readText()))
        assertFalse(marker.exists())
    }

    @Test
    fun `B-CHAT-042 an older registration migrates to the show rule`() {
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        val manifest = File(managed, "owners.json")
        val old = json(manifest.readText()).jsonObject.toMutableMap().apply { remove("showPermissionAdded") }
        manifest.writeText(JsonObject(old).toString())
        settings.writeText("{}")
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        assertEquals(json("""["mcp__oc-ui__show"]"""), json(settings.readText()).at("permissions", "allow"))
        assertEquals(10, apply("one", agent = CardAgent.Claude, enabled = false))
        assertEquals(json("{}"), json(settings.readText()))
    }

    @Test
    fun `B-CHAT-042 readiness checks the pinned CLI and discovery in the owned profile`() {
        assertEquals(0, apply("one", agent = CardAgent.Claude))
        assertEquals(0, apply("one", agent = CardAgent.Claude, verify = true))
        assertEquals(24, apply("one", agent = CardAgent.Claude, verify = true, wrongVersion = true))
        val helper = File(managed, "server.cjs")
        val modified = helper.lastModified()
        assertEquals(0, apply("one", agent = CardAgent.Claude, requireMarker = true))
        assertEquals(modified, helper.lastModified(), "an unchanged enable keeps the marker and helper")
        marker.delete()
        assertEquals(24, apply("one", agent = CardAgent.Claude, verify = true))
    }
}
