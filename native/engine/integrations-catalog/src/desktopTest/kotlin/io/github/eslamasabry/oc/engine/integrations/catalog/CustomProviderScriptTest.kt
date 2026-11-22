package io.github.eslamasabry.oc.engine.integrations.catalog

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Pull request 142, ported: the real settings writer of [CustomProviderScript] (the Python text the phone runs, taken
 * out of the shipped script) against a temporary tree. Only the host identity is replaced (uid 0 and root-owned
 * folders, as proot reports them; one path can be made foreign), the way `CardsInstallScriptsTest` runs the card
 * installer, never the file logic. Needs /usr/bin/python3.
 */
class CustomProviderScriptTest {
    private val root: File = Files.createTempDirectory("custom-provider-").toFile().canonicalFile
    private val config = File(root, "config")
    private val cards = File(root, "cards")
    private val file = File(config, "opencode.json")

    @AfterTest
    fun clean() {
        root.deleteRecursively()
    }

    private fun declaration(name: String = "My box", id: String = "my-box") =
        CustomProviderDraft(name, id, "http://models.example.net:8080/v1", "llama-3\nqwen").declaration(emptySet())!!

    /** The program exactly as the shipped script quotes it. */
    private fun program(): String {
        val script = CustomProviderScript.script()
        val start = script.indexOf("-c '") + "-c '".length
        return script.substring(start, script.indexOf("' '", start))
    }

    private fun harness(foreign: String = ""): String =
        program()
            .replaceFirst(
                "try: main()",
                """
FOREIGN = ${JsonPrimitive(foreign)}
import os as _os
_os.getuid = lambda: 0
_real = _os.lstat
def _lstat(path, *args, **kwargs):
    info = _real(path, *args, **kwargs)
    values = list(info)
    values[4] = 1000 if str(path) == FOREIGN else 0
    values[0] &= ~0o022
    return _os.stat_result(values)
_os.lstat = _lstat
try: main()""",
            )

    private fun start(input: String, foreign: String, discard: Boolean): Process {
        val builder =
            ProcessBuilder("/usr/bin/python3", "-I", "-c", harness(foreign), config.path, cards.path, "0")
                .directory(root)
        if (discard) {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            builder.redirectError(ProcessBuilder.Redirect.DISCARD)
        }
        val process = builder.start()
        process.outputStream.use { it.write(input.encodeToByteArray()) }
        return process
    }

    private fun write(input: String = declaration().input(), foreign: String = ""): Int {
        val process = start(input, foreign, discard = true)
        check(process.waitFor(60, TimeUnit.SECONDS)) { "the writer did not finish" }
        return process.exitValue()
    }

    /** The same run with its output captured: a refusal must print nothing, least of all a hand-written key. */
    private fun writeCaptured(input: String = declaration().input()): Pair<Int, String> {
        val process = start(input, "", discard = false)
        val output =
            process.inputStream.readBytes().decodeToString() + process.errorStream.readBytes().decodeToString()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "the writer did not finish" }
        return process.exitValue() to output
    }

    /** Runs [block] while another process holds an exclusive lock on [lock]. */
    private fun <T> holding(lock: File, block: () -> T): T {
        val holder =
            ProcessBuilder(
                    "/usr/bin/python3",
                    "-I",
                    "-c",
                    "import fcntl, os, sys, time\n" +
                        "fd = os.open(sys.argv[1], os.O_RDWR | os.O_CREAT, 0o600)\n" +
                        "fcntl.flock(fd, fcntl.LOCK_EX)\n" +
                        "print(\"held\", flush=True)\n" +
                        "time.sleep(60)\n",
                    lock.path,
                )
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        try {
            check(holder.inputStream.bufferedReader().readLine() == "held") { "the lock was not taken" }
            return block()
        } finally {
            holder.destroyForcibly()
            holder.waitFor(10, TimeUnit.SECONDS)
        }
    }

    private fun json(): JsonObject = Json.parseToJsonElement(file.readText()).jsonObject

    private fun assertRefused(code: Int, before: String, run: () -> Int) {
        assertEquals(code, run())
        assertEquals(before, file.readText(), "a refused write leaves the file as it was")
    }

    /** The run's stdin when the engine has decided a declaration left in the file may be replaced (issue 152). */
    private fun replaceInput(declared: CustomProviderDeclaration = declaration()): String =
        buildJsonObject {
                put("id", declared.providerId)
                put("block", declared.block)
                put("replaceDeclared", true)
            }
            .toString()

    /** The run's stdin for an edit: both flags, so only an app-written entry under the id is replaced. */
    private fun editInput(declared: CustomProviderDeclaration = declaration()): String =
        buildJsonObject {
                put("id", declared.providerId)
                put("block", declared.block)
                put("replaceDeclared", true)
                put("requireDeclared", true)
            }
            .toString()

    /** The run's stdin for the remove step: the id travels on stdin only, never the command line. */
    private fun removeInput(id: String = "my-box"): String =
        buildJsonObject {
                put("op", "remove")
                put("id", id)
            }
            .toString()

    @Test
    fun `B-LIB-326 the read step returns name, address and models of an app-shaped entry and nothing else`() {
        config.mkdirs()
        file.writeText(
            """{"keep":42,"providers":{"hand":{"name":"Hand"},"my-box":""" + declaration().block.toString() + "}}"
        )
        val (code, output) = writeCaptured("""{"op":"read"}""")
        assertEquals(0, code)
        assertEquals(
            listOf(
                """{"id":"hand","appShaped":false}""",
                """{"id":"my-box","appShaped":true,"name":"My box",""" +
                    """"baseURL":"http://models.example.net:8080/v1","models":["llama-3","qwen"]}""",
            ),
            output.trim().lines(),
        )
        assertFalse("keep" in output, "no other setting of the file is printed")
    }

    @Test
    fun `B-LIB-327 the read step marks a hand-written entry and prints none of its values`() {
        config.mkdirs()
        file.writeText(
            """{"providers":{"hand-box":{"name":"Hand Box","npm":"yes",""" +
                """"settings":{"baseURL":"http://hand.example:9/v1","apiKey":"hand-written-inline-value"},""" +
                """"models":{"m":{"x":1}}}}}"""
        )
        val (code, output) = writeCaptured("""{"op":"read"}""")
        assertEquals(0, code)
        assertEquals(listOf("""{"id":"hand-box","appShaped":false}"""), output.trim().lines())
        for (value in listOf("Hand Box", "hand.example", "hand-written-inline-value")) {
            assertFalse(value in output, "a hand-written value is never printed: $value")
        }
    }

    @Test
    fun `B-LIB-328 Edit replaces only with both flags; a hand-written entry exits 24 and stays byte-identical`() {
        config.mkdirs()
        file.writeText(
            """{"providers":{"my-box":{"name":"Old box","package":"@opencode/ai/providers/openai-compatible",""" +
                """"settings":{"baseURL":"http://old.example:9000/v1"},"models":{"old-model":{}}}}}"""
        )
        assertEquals(0, write(editInput()))
        assertEquals(declaration().block, json().getValue("providers").jsonObject.getValue("my-box").jsonObject)
        // Requiring the entry is not enough: without the replace flag the write still refuses.
        file.writeText(
            """{"providers":{"my-box":{"name":"Old box","package":"@opencode/ai/providers/openai-compatible",""" +
                """"settings":{"baseURL":"http://old.example:9000/v1"},"models":{"old-model":{}}}}}"""
        )
        assertRefused(CustomProviderWrite.AlreadyDeclared.code, file.readText()) {
            write(
                buildJsonObject {
                        put("id", "my-box")
                        put("block", declaration().block)
                        put("replaceDeclared", false)
                        put("requireDeclared", true)
                    }
                    .toString(),
            )
        }
        // A hand-written entry keeps the refusal with both flags, and the file is untouched.
        file.writeText(
            """{"providers":{"my-box":{"name":"Old box","settings":{"baseURL":"http://old.example:9000/v1",""" +
                """"apiKey":"hand-written-inline-value"}}}}"""
        )
        assertRefused(CustomProviderWrite.AlreadyDeclared.code, file.readText()) { write(editInput()) }
    }

    @Test
    fun `B-LIB-330 remove deletes an app-shaped entry and keeps every other key of the file`() {
        config.mkdirs()
        file.writeText(
            """{"keep":42,"mcp":{"servers":{"oc-ui":{"type":"local"}}},"providers":{"my-box":""" +
                declaration().block.toString() + ""","other":{"name":"Other","npm":"no"}}}"""
        )
        assertEquals(0, write(removeInput()))
        val after = json()
        assertEquals(setOf("keep", "mcp", "providers"), after.keys)
        assertEquals(42, after.getValue("keep").jsonPrimitive.content.toInt())
        assertEquals("""{"servers":{"oc-ui":{"type":"local"}}}""", after.getValue("mcp").toString())
        assertEquals(setOf("other"), after.getValue("providers").jsonObject.keys)
        val other = after.getValue("providers").jsonObject.getValue("other").jsonObject
        assertEquals("no", other.getValue("npm").jsonPrimitive.content)
    }

    @Test
    fun `B-LIB-331 remove refuses a hand-written entry and a jsonc beside it, and the file stays byte-identical`() {
        config.mkdirs()
        file.writeText(
            """{"providers":{"my-box":{"name":"Old box","settings":{"baseURL":"http://old.example:9000/v1",""" +
                """"apiKey":"hand-written-inline-value"}}}}"""
        )
        val hand = file.readText()
        assertEquals(CustomProviderWrite.AlreadyDeclared.code, write(removeInput()))
        assertEquals(hand, file.readText(), "a refused remove leaves the file as it was")
        file.writeText("""{"providers":{"my-box":""" + declaration().block.toString() + "}}")
        val shaped = file.readText()
        File(config, "opencode.jsonc").writeText("{\"providers\":{}}")
        assertEquals(CustomProviderWrite.HandManaged.code, write(removeInput()))
        assertEquals(shaped, file.readText(), "a refused remove leaves the file as it was")
    }

    @Test
    fun `B-LIB-339 the read and remove steps take the id on stdin only and echo no key`() {
        config.mkdirs()
        val key = "sk-test-not-real"
        file.writeText(
            """{"providers":{"hand-box":{"name":"Hand","settings":{"baseURL":"http://hand.example/v1",""" +
                """"apiKey":"$key"}}}}"""
        )
        val script = CustomProviderScript.script()
        assertFalse("hand-box" in script, "the script holds no id")
        assertFalse(key in script, "the script holds no key text")
        val (readCode, readOutput) = writeCaptured("""{"op":"read"}""")
        assertEquals(0, readCode)
        assertFalse(key in readOutput, "a hand-written key is never echoed by the read step")
        assertEquals(listOf("""{"id":"hand-box","appShaped":false}"""), readOutput.trim().lines())
        val (removeCode, removeOutput) = writeCaptured(removeInput("hand-box"))
        assertEquals(CustomProviderWrite.AlreadyDeclared.code, removeCode)
        assertFalse(key in removeOutput, "a hand-written key is never echoed by the remove step")
    }

    @Test
    fun `PR142-12 the declaration is data, never code - quotes and shell words land in the file as typed`() {
        config.mkdirs()
        val name = "a'b\"; \$(touch OWNED) `id`"
        val declared = declaration(name = name)
        val script = CustomProviderScript.script()
        assertFalse("'" in program(), "the program sits in single quotes, so it holds none")
        val typed = listOf(name, "models.example.net", "llama")
        assertFalse(typed.any { it in script }, "the script never holds typed words")
        assertTrue("'${CustomProviderScript.CONFIG_DIR}' '${CustomProviderScript.CARDS_DIR}' 0" in script)
        val parsed = ProcessBuilder("sh", "-n", "-c", script).start()
        check(parsed.waitFor(30, TimeUnit.SECONDS))
        assertEquals(0, parsed.exitValue(), "the shell parses the script")
        assertEquals(0, write(declared.input()))
        val block = json().getValue("providers").jsonObject.getValue("my-box").jsonObject
        assertEquals(name, block.getValue("name").jsonPrimitive.content)
        assertEquals(declared.block, block)
        assertFalse(File(root, "OWNED").exists())
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(file.toPath()),
        )
    }

    @Test
    fun `PR142-13 every other setting and provider is kept`() {
        config.mkdirs()
        file.writeText(
            """{"mcp":{"servers":{"oc-ui":{"type":"local"}}},"providers":{"other":{"name":"Other"}},"keep":42}"""
        )
        assertEquals(0, write())
        val after = json()
        assertEquals(setOf("mcp", "providers", "keep"), after.keys)
        assertEquals(42, after.getValue("keep").jsonPrimitive.content.toInt())
        assertEquals(setOf("other", "my-box"), after.getValue("providers").jsonObject.keys)
        assertEquals("""{"servers":{"oc-ui":{"type":"local"}}}""", after.getValue("mcp").toString())
    }

    @Test
    fun `PR142-14 a second provider with the same id is refused and the first stays`() {
        config.mkdirs()
        assertEquals(0, write())
        val first = file.readText()
        assertRefused(CustomProviderWrite.AlreadyDeclared.code, first) { write(declaration(name = "Other").input()) }
    }

    @Test
    fun `B-LIB-311 adding an id that is declared but not shown replaces the old declaration`() {
        config.mkdirs()
        file.writeText(
            """{"providers":{"my-box":{"name":"Old box","package":"@opencode/ai/providers/openai-compatible",""" +
                """"settings":{"baseURL":"http://old.example:9000/v1"},"models":{"old-model":{}}}}}"""
        )
        assertEquals(0, write(replaceInput()))
        assertEquals(declaration().block, json().getValue("providers").jsonObject.getValue("my-box").jsonObject)
    }

    @Test
    fun `B-LIB-313 the file's other content survives a replace`() {
        config.mkdirs()
        file.writeText(
            """{"keep":42,"mcp":{"servers":{"oc-ui":{"type":"local"}}},"providers":{""" +
                """"my-box":{"name":"Old box","settings":{"baseURL":"http://old.example:9000/v1"}},""" +
                """"other":{"name":"Other","npm":"no"}}}"""
        )
        val before = json()
        assertEquals(0, write(replaceInput()))
        val after = json()
        assertEquals(before.getValue("keep").toString(), after.getValue("keep").toString())
        assertEquals(before.getValue("mcp").toString(), after.getValue("mcp").toString())
        assertEquals(
            before.getValue("providers").jsonObject.getValue("other").toString(),
            after.getValue("providers").jsonObject.getValue("other").toString(),
        )
        assertEquals(declaration().block, after.getValue("providers").jsonObject.getValue("my-box").jsonObject)
    }

    @Test
    fun `B-LIB-315 without the replace input the script still refuses a declared id`() {
        config.mkdirs()
        file.writeText("""{"keep":1,"providers":{"my-box":{"name":"Old box"}}}""")
        val block = declaration().block
        assertRefused(CustomProviderWrite.AlreadyDeclared.code, file.readText()) {
            write(
                buildJsonObject {
                        put("id", "my-box")
                        put("block", block)
                    }
                    .toString()
            )
        }
        assertRefused(CustomProviderWrite.AlreadyDeclared.code, file.readText()) {
            write(
                buildJsonObject {
                        put("id", "my-box")
                        put("block", block)
                        put("replaceDeclared", false)
                    }
                    .toString()
            )
        }
    }

    @Test
    fun `B-LIB-323 a run that requires a declaration refuses an id the file does not hold`() {
        config.mkdirs()
        file.writeText("""{"keep":1,"providers":{"other":{"name":"Other"}}}""")
        assertRefused(CustomProviderWrite.AlreadyDeclared.code, file.readText()) {
            write(
                buildJsonObject {
                        put("id", "my-box")
                        put("block", declaration().block)
                        put("replaceDeclared", true)
                        put("requireDeclared", true)
                    }
                    .toString()
            )
        }
    }

    @Test
    fun `B-LIB-316 an entry with one extra field is refused and left byte-for-byte unchanged`() {
        config.mkdirs()
        file.writeText(
            """{"providers":{"my-box":{"name":"Old box","package":"@opencode/ai/providers/openai-compatible",""" +
                """"settings":{"baseURL":"http://old.example:9000/v1"},"models":{"old-model":{}},""" +
                """"options":{"reasoning":true}}}}"""
        )
        assertRefused(CustomProviderWrite.AlreadyDeclared.code, file.readText()) { write(replaceInput()) }
    }

    @Test
    fun `B-LIB-317 an entry with an inline key field is refused, unchanged, and echoed nowhere`() {
        config.mkdirs()
        val inline = "hand-written-inline-value"
        file.writeText(
            """{"providers":{"my-box":{"name":"Old box","settings":{"baseURL":"http://old.example:9000/v1",""" +
                """"apiKey":"$inline"}}}}"""
        )
        val before = file.readText()
        val (code, output) = writeCaptured(replaceInput())
        assertEquals(CustomProviderWrite.AlreadyDeclared.code, code)
        assertEquals(before, file.readText(), "a refused write leaves the file as it was")
        assertFalse(inline in output, "a hand-written key is never echoed")
    }

    @Test
    fun `PR142-15 a file with comments, a providers list or over 1 MiB is unreadable and left alone`() {
        config.mkdirs()
        val unreadable = listOf("{\"model\":\"a/b\" // mine\n}", """{"providers":[]}""", "[1]", " ".repeat(1_048_577))
        for (text in unreadable) {
            file.writeText(text)
            assertRefused(CustomProviderWrite.Unreadable.code, text) { write() }
        }
    }

    @Test
    fun `PR142-16 a hand-kept opencode jsonc beside it is never overwritten`() {
        config.mkdirs()
        file.writeText("{}")
        File(config, "opencode.jsonc").writeText("{\"providers\":{}}")
        assertRefused(CustomProviderWrite.HandManaged.code, "{}") { write() }
        assertEquals("{\"providers\":{}}", File(config, "opencode.jsonc").readText())
    }

    @Test
    fun `PR142-17 a symlinked file or folder and a foreign owner are refused`() {
        config.mkdirs()
        val elsewhere = File(root, "elsewhere.json").apply { writeText("{}") }
        Files.createSymbolicLink(file.toPath(), elsewhere.toPath())
        assertEquals(CustomProviderWrite.Unsafe.code, write())
        assertEquals("{}", elsewhere.readText())
        file.delete()
        file.writeText("{}")
        assertRefused(CustomProviderWrite.Unsafe.code, "{}") { write(foreign = file.path) }
        val linked = File(root, "linked").apply { mkdirs() }
        config.deleteRecursively()
        Files.createSymbolicLink(config.toPath(), linked.toPath())
        assertEquals(CustomProviderWrite.Unsafe.code, write())
        assertFalse(File(linked, "opencode.json").exists())
    }

    @Test
    fun `PR142-18 no settings folder means not set up, and input that is not a declaration is refused`() {
        assertEquals(CustomProviderWrite.NotSetUp.code, write())
        assertFalse(config.exists())
        config.mkdirs()
        assertEquals(CustomProviderWrite.Failed.code, write("not json"))
        assertEquals(CustomProviderWrite.Failed.code, write("""{"id":"x; rm -rf /","block":{}}"""))
        assertEquals(
            CustomProviderWrite.Failed.code,
            write("""{"id":"k","block":{"name":"n","package":"p","settings":{},"models":{},"apiKey":"x"}}"""),
        )
        assertFalse(file.exists())
    }

    @Test
    fun `PR142-19 a write while another settings change holds either lock is busy and changes nothing`() {
        config.mkdirs()
        file.writeText("{}")
        assertRefused(CustomProviderWrite.Busy.code, "{}") {
            holding(File(config, ".oc-provider.lock")) { write() }
        }
        cards.mkdirs()
        assertRefused(CustomProviderWrite.Busy.code, "{}") { holding(File(cards, "lock")) { write() } }
        assertEquals(0, write())
    }
}
