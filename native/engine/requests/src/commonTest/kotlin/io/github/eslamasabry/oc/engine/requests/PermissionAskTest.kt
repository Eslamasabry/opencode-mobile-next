package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.engine.requests.ask.AlwaysGrant
import io.github.eslamasabry.oc.engine.requests.ask.AskTarget
import io.github.eslamasabry.oc.engine.requests.ask.AskVerb
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ports of `test/vision_chat_approval_test.dart` (B-CHAT-122) and `permission_card_words_test.dart` (B-CHAT-073). */
class PermissionAskTest {
    private val dir = "/home/me/neon-breaker"
    private val joined = "neon⁠-⁠breaker"

    private fun of(request: PermissionRequest, project: String? = null, toolName: String? = null, target: String? = null) =
        PermissionAsk.of(request, project = project, directory = dir, toolName = toolName, target = target)

    @Test
    fun `B-CHAT-122 a short shell command shows its words and the project, unbroken at hyphens`() {
        val ask = of(permission("p", patterns = listOf("npm install"), always = listOf("npm install *")), project = "neon-breaker")
        assertEquals(AskVerb.Run, ask.verb)
        assertEquals("npm install", ask.name)
        assertEquals(joined, ask.where)
        assertEquals(listOf(AskTarget("npm install")), ask.targets)
        assertEquals(AlwaysGrant.Named("npm install"), ask.always)
    }

    @Test
    fun `B-CHAT-122 a long command reads by the family its pattern saves, and the block keeps the whole command`() {
        val command = "npm install --save-dev vite @types/node eslint"
        val ask =
            of(
                permission("p", permission = "bash", patterns = listOf(command), always = listOf("npm install *"), metadata = mapOf("command" to command)),
                project = "neon-breaker",
            )
        assertEquals("npm install", ask.name)
        assertEquals(joined, ask.where)
        assertEquals(listOf(AskTarget(command)), ask.targets)
    }

    @Test
    fun `B-CHAT-122 a long command with no family reads as a command`() {
        val command = "find . -name \"*.tmp\" -mtime +3 -exec rm {} +"
        val ask = of(permission("p", patterns = listOf(command), always = listOf(command)), project = "neon-breaker")
        assertEquals(AskVerb.Run, ask.verb)
        assertNull(ask.name)
        assertEquals(joined, ask.where)
        assertEquals(AlwaysGrant.Commands, ask.always)
    }

    @Test
    fun `B-CHAT-122 a Paseo agent says where it runs, and a bare command has no place`() {
        val ask = of(permission("p", metadata = mapOf("cwd" to "/srv/api-server/")), project = "neon-breaker")
        assertEquals("api⁠-⁠server", ask.where)
        val bare = of(permission("p", patterns = emptyList(), always = listOf("*")))
        assertNull(bare.name)
        assertNull(bare.where)
        assertEquals(AlwaysGrant.AnyCommand, bare.always)
    }

    @Test
    fun `B-CHAT-122 OpenCode 2 apply_patch lists every path and no size it does not send`() {
        val paths = listOf("src/game.js", "src/levels.js", "src/paddle.js", "src/sounds.js", "index.html")
        val request = permission("p", permission = "edit", patterns = paths, always = listOf("*"))
        val ask = of(request)
        assertEquals(AskVerb.Edit, ask.verb)
        assertEquals(5, ask.count)
        assertEquals(paths, ask.targets.map { it.text })
        assertEquals(List(5) { null }, ask.targets.map { it.added })
        assertEquals(AlwaysGrant.Edits, ask.always)
        assertEquals(paths.joinToString(" · "), PermissionAsk.summary(request))
    }

    @Test
    fun `B-CHAT-122 an OpenCode 1 edit shows the file's name and its diff's size`() {
        val ask =
            of(
                permission(
                    "p",
                    permission = "edit",
                    patterns = listOf("notes.txt"),
                    always = listOf("*"),
                    metadata = mapOf("filepath" to "$dir/notes.txt", "diff" to "--- notes.txt\n+++ notes.txt\n@@ -1,2 +1,3 @@\n-a\n+b\n+c\n d\n"),
                ),
            )
        assertEquals(AskVerb.Edit, ask.verb)
        assertEquals(1, ask.count)
        assertEquals("notes.txt", ask.name)
        assertEquals(listOf(AskTarget("notes.txt", added = 2, removed = 1, file = true)), ask.targets)
    }

    @Test
    fun `B-CHAT-122 a path the agent names absolutely reads from the project, and no star pattern means no named grant`() {
        val ask =
            of(permission("p", permission = "edit", patterns = listOf("$dir/src/game.js"), always = emptyList(), metadata = mapOf("filePath" to "$dir/src/game.js")))
        assertEquals("src/game.js", ask.targets.single().text)
        assertEquals("game.js", ask.name)
        assertNull(ask.always)
    }

    @Test
    fun `B-CHAT-122 Delete only when the request says every file is deleted`() {
        fun files(vararg kinds: String) =
            JsonArray(
                kinds.mapIndexed { i, kind ->
                    JsonObject(
                        mapOf(
                            "relativePath" to JsonPrimitive(listOf("build/a.js", "build/b.css")[i]),
                            "type" to JsonPrimitive(kind),
                            "additions" to JsonPrimitive(0),
                            "deletions" to JsonPrimitive(listOf(40, 12)[i]),
                        ),
                    )
                },
            )

        fun request(vararg kinds: String) =
            permission("p", permission = "edit", patterns = listOf("build/a.js", "build/b.css"), always = listOf("*"))
                .copy(metadata = JsonObject(mapOf("files" to files(*kinds))))

        val deleted = of(request("delete", "delete"))
        assertEquals(AskVerb.Delete, deleted.verb)
        assertEquals(2, deleted.count)
        assertEquals(listOf(0 to 40, 0 to 12), deleted.targets.map { it.added to it.removed })
        assertEquals(AskVerb.Edit, of(request("delete", "update")).verb)
        assertEquals(AskVerb.Create, of(request("add", "add")).verb)
    }

    @Test
    fun `B-CHAT-122 a read names its file, relative to the project`() {
        val ask = of(permission("p", permission = "read", patterns = listOf("$dir/README.md"), always = emptyList()))
        assertEquals(AskVerb.Read, ask.verb)
        assertEquals("README.md", ask.name)
        assertEquals(listOf(AskTarget("README.md", file = true)), ask.targets)
    }

    @Test
    fun `B-CHAT-073 every kind the card renders asks its own question`() {
        fun verb(kind: String) = of(permission("p", permission = kind, patterns = emptyList(), always = emptyList())).verb
        assertEquals(AskVerb.Run, verb("bash"))
        assertEquals(AskVerb.Edit, verb("edit"))
        assertEquals(AskVerb.Edit, verb("write"))
        assertEquals(AskVerb.Read, verb("read"))
        assertEquals(AskVerb.Fetch, verb("webfetch"))
        assertEquals(AskVerb.WebSearch, verb("websearch"))
        assertEquals(AskVerb.Search, verb("grep"))
        assertEquals(AskVerb.Search, verb("glob"))
        assertEquals(AskVerb.Task, verb("task"))
        assertEquals(AskVerb.Skill, verb("skill"))
        assertEquals(AskVerb.Mode, verb("mode"))
        assertEquals(AskVerb.DoomLoop, verb("doom_loop"))
        assertEquals(AskVerb.Generic, verb(" "))
        val tool = of(permission("p", permission = "mcp_linear_create", patterns = emptyList(), always = emptyList()))
        assertEquals(AskVerb.Use, tool.verb)
        assertEquals("mcp_linear_create", tool.tool)
    }

    @Test
    fun `B-CHAT-073 outside the project the question names what the call does and shows the exact path`() {
        val outside = permission("p", permission = "external_directory", patterns = listOf("/etc/*"), always = emptyList())
        assertEquals(AskVerb.OutsideWrite, of(outside, toolName = "write").verb)
        assertEquals(AskVerb.OutsideWrite, of(outside, toolName = "edit").verb)
        assertEquals(AskVerb.OutsideRead, of(outside, toolName = "read").verb)
        assertEquals(AskVerb.OutsideRead, of(outside, toolName = "grep").verb)
        assertEquals(AskVerb.OutsideRun, of(outside, toolName = "bash").verb)
        assertEquals(AskVerb.Outside, of(outside).verb)
        assertEquals(AskVerb.Outside, of(outside, toolName = "mystery").verb)
        assertEquals(listOf("/etc/x"), of(outside, toolName = "write", target = "/etc/x").targets.map { it.text })
        assertEquals(listOf("/etc/*"), of(outside).targets.map { it.text })
    }

    private fun permission(
        id: String,
        permission: String = "shell",
        patterns: List<String> = listOf("npm install"),
        always: List<String> = listOf("npm install *"),
        metadata: Map<String, String> = emptyMap(),
    ): PermissionRequest =
        permission(id, S1, permission, patterns, always, metadata).copy(id = RequestId(id), patterns = patterns.toImmutableList())
}
