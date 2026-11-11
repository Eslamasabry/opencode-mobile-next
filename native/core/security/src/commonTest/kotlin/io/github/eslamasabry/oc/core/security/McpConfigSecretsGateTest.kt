package io.github.eslamasabry.oc.core.security

import io.github.eslamasabry.oc.core.common.KnownSecretMask
import io.github.eslamasabry.oc.core.common.KnownSecrets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * w4-mcp-secrets gate: the shapes the fixer's walker missed (lists, quoted command lines, fragments, references with a
 * literal, long numbers, LSP/formatter/provider/agent values), and the other direction: the app's own words
 * ("OpenCode", "production", "application/json") are never registered from a header or env value. Fakes are built from
 * adjacent literals; failure messages name the place, never the value.
 */
class McpConfigSecretsGateTest {
    private fun read(answer: String): Redactor = Redactor().apply { registerCredentialValues(Json.parseToJsonElement(answer)) }

    private fun assertMasked(redactor: Redactor, kind: String, secret: String) {
        assertTrue(redactor.containsSecret(secret), "$kind is not registered")
        val out = redactor.redact("Created /root/projects/out.txt with $secret")
        assertFalse(secret in out, "$kind leaks in a preview")
        assertTrue("/root/projects/out.txt" in out, "the preview lost its folder next to the $kind")
    }

    private fun assertReadable(redactor: Redactor, word: String) =
        assertEquals("$word in /root/projects", redactor.redact("$word in /root/projects"), "an ordinary value is masked")

    @Test
    fun `W4-MCP-GATE env and headers given as lists are read like maps`() {
        val listEnv = "le-" + "GateListEnv" + "1a2b"
        val listHeader = "lh-" + "GateListHeader" + "3c4d"
        val pairEnv = "pe-" + "GatePairEnv" + "5e6f"
        val pairHeader = "ph-" + "GatePairHeader" + "7g8h"
        val redactor =
            read(
                """
                {"mcpServers":{
                  "a":{"command":"npx","env":["SESSION_VALUE=$listEnv","NODE_ENV=production"],"headers":["X-Workspace: $listHeader"]},
                  "b":{"type":"stdio","command":["node","s.js"],"env":[{"name":"TEAM","value":"$pairEnv"}],
                       "headers":[{"name":"X-Org","value":"$pairHeader"}]}}}
                """.trimIndent()
            )
        assertMasked(redactor, "a NAME=value env list item", listEnv)
        assertMasked(redactor, "a Name: value header list item", listHeader)
        assertMasked(redactor, "a {name,value} env item", pairEnv)
        assertMasked(redactor, "a {name,value} header item", pairHeader)
        for (word in listOf("SESSION_VALUE", "NODE_ENV", "production", "X-Workspace", "TEAM", "X-Org")) assertReadable(redactor, word)
    }

    @Test
    fun `W4-MCP-GATE numbers, paths and references that hide a secret are masked`() {
        val account = "4829" + "1375" + "60"
        val grouped = "1234-" + "5678-" + "9012-" + "3456"
        val digitUuid = "12345678-" + "1234-1234-1234-" + "123456789012"
        val pin = "48" + "29" + "13"
        val pathSecret = "/s3cret" + "Passw0rd"
        val literal = "lit-" + "GateLiteral" + "9i0j"
        val redactor =
            read(
                """
                {"mcp":{"servers":{"bank":{"type":"local","command":["bank-mcp"],"environment":{
                  "ACCOUNT_NUMBER":"$account","CARD":"$grouped","TENANT":"$digitUuid","SLACK_PIN":"$pin",
                  "SESSION":"$pathSecret","DB_LOGIN":"{env:DB_USER}:$literal","TIMEOUT":"300000","RELEASED":"2025-06-18",
                  "HOME_DIR":"/usr/local/var/log","TOKEN_REF":"{env:BANK_TOKEN}"}}}}}
                """.trimIndent()
            )
        assertMasked(redactor, "a long account number under an ordinary name", account)
        assertMasked(redactor, "a grouped card-like number", grouped)
        assertMasked(redactor, "an all-digit UUID", digitUuid)
        assertMasked(redactor, "a six-digit PIN under a PIN name", pin)
        assertMasked(redactor, "a path-shaped password", pathSecret)
        assertMasked(redactor, "the literal next to a reference", literal)
        for (word in listOf("300000", "2025-06-18", "/usr/local/var/log", "{env:BANK_TOKEN}", "{env:DB_USER}", "ACCOUNT_NUMBER"))
            assertReadable(redactor, word)
    }

    @Test
    fun `W4-MCP-GATE a string command line is split like a shell, quotes kept together`() {
        val quotedHeader = "qh-" + "GateQuotedHeader" + "2k3l"
        val quotedBearer = "qb-" + "GateQuotedBearer" + "4m5n"
        val shellToken = "st-" + "GateShellToken" + "6p7q"
        val schemeSplit = "ss-" + "GateSchemeSplit" + "8r9s"
        val positional = "Zq" + "GatePositional" + "0123456789"
        val envWord = "ew-" + "GateEnvWord" + "1t2u"
        val redactor =
            read(
                """
                {"mcp":{"servers":{
                  "notes":{"type":"local","command":"npx mcp-remote https://notes.example/mcp --header 'X-Api-Access: $quotedHeader' --header \"Authorization: Bearer $quotedBearer\""},
                  "wrapped":{"type":"local","command":["sh","-c","exec tracker-mcp --api-token $shellToken"]},
                  "split":{"type":"local","command":["cli-mcp","--token","Bearer","$schemeSplit","$positional"]},
                  "env":{"type":"local","command":["env","NOTION_AUTH=$envWord","LOG_LEVEL=debug","notion-mcp"]}}}}
                """.trimIndent()
            )
        assertMasked(redactor, "a quoted header value in a string command", quotedHeader)
        assertMasked(redactor, "a quoted bearer in a string command", quotedBearer)
        assertMasked(redactor, "a flag value inside an sh -c line", shellToken)
        assertMasked(redactor, "the credential after a scheme word", schemeSplit)
        assertMasked(redactor, "a token-like positional word", positional)
        assertMasked(redactor, "an env-style NAME=value word", envWord)
        for (word in listOf("Bearer", "debug", "notion-mcp", "mcp-remote", "https://notes.example/mcp", "tracker-mcp"))
            assertReadable(redactor, word)
    }

    @Test
    fun `W4-MCP-GATE URL fragments and last path segments are read`() {
        val fragmentValue = "fv" + "GateFragment" + "1234567890"
        val bareFragment = "Bf" + "GateBareFragment" + "123456789"
        val lastSegment = "Ls" + "GateLastSegment" + "987654321"
        val redactor =
            read(
                """
                {"mcp":{"servers":{
                  "a":{"type":"remote","url":"https://a.example/cb#access_token=$fragmentValue&type=bearer"},
                  "b":{"type":"remote","url":"https://b.example/share#$bareFragment"},
                  "c":{"type":"remote","url":"https://c.example/hooks/$lastSegment"}}}}
                """.trimIndent()
            )
        assertMasked(redactor, "a fragment access_token", fragmentValue)
        assertMasked(redactor, "a bare token fragment", bareFragment)
        assertMasked(redactor, "a last path segment token", lastSegment)
        assertReadable(redactor, "https://c.example/hooks/")
    }

    @Test
    fun `W4-MCP-GATE LSP env, formatter environment and provider, model, variant and agent headers are read`() {
        val lspEnv = "lsp-" + "GateLspEnv" + "3v4w"
        val formatterEnv = "fmt-" + "GateFormatterEnv" + "5x6y"
        val providerHeader = "pvh-" + "GateProviderHeader" + "7z8a"
        val modelHeader = "mdh-" + "GateModelHeader" + "9b0c"
        val variantHeader = "vrh-" + "GateVariantHeader" + "1d2e"
        val agentHeader = "agh-" + "GateAgentHeader" + "3f4g"
        val olderHeader = "olh-" + "GateOlderHeader" + "5h6i"
        val redactor =
            read(
                """
                [{"type":"document","path":"/root/projects/opencode.json","info":{
                  "lsp":{"pyright":{"command":["pyright-langserver","--stdio"],"env":{"PYRIGHT_SESSION":"$lspEnv","MODE":"strict"}}},
                  "formatter":{"ruff":{"command":["ruff","format"],"environment":{"RUFF_CACHE":"$formatterEnv"}}},
                  "providers":{"gateway":{"name":"Gateway","settings":{"baseURL":"https://llm.example/v1"},
                    "headers":{"X-Org-Scope":"$providerHeader","X-Title":"OpenCode","HTTP-Referer":"https://opencode.ai/"},
                    "models":{"m1":{"name":"Model one","headers":{"X-Model-Scope":"$modelHeader"},
                      "variants":[{"id":"fast","headers":{"X-Variant-Scope":"$variantHeader"}}]}}}},
                  "agents":{"review":{"request":{"headers":{"X-Agent-Scope":"$agentHeader","User-Agent":"OpenCode Mobile"}}}}}},
                 {"type":"document","path":"/root/.config/opencode/opencode.json","info":{
                  "provider":{"older":{"options":{"headers":{"X-Older-Scope":"$olderHeader"}}}}}}]
                """.trimIndent()
            )
        assertMasked(redactor, "an LSP env value", lspEnv)
        assertMasked(redactor, "a formatter environment value", formatterEnv)
        assertMasked(redactor, "a provider header", providerHeader)
        assertMasked(redactor, "a model header", modelHeader)
        assertMasked(redactor, "a model variant header", variantHeader)
        assertMasked(redactor, "an agent request header", agentHeader)
        assertMasked(redactor, "an older provider options header", olderHeader)
        for (word in listOf("OpenCode", "OpenCode Mobile", "https://opencode.ai/", "strict", "Gateway", "Model one", "fast"))
            assertReadable(redactor, word)
    }

    @Test
    fun `W4-MCP-GATE the app's own words survive header and env values`() {
        val redactor =
            read(
                """
                {"mcp":{"servers":{"router":{"type":"remote","url":"https://router.example/mcp",
                  "headers":{"X-Title":"OpenCode","HTTP-Referer":"https://opencode.ai/","Content-Type":"application/json",
                    "Accept":"application/json, text/event-stream","User-Agent":"Mozilla/5.0 (X11; Linux x86_64) OpenCode/2.2",
                    "X-Client":"OpenCode Mobile","X-Env":"production","X-Model":"gpt-4o-mini","X-Host":"mcp.router.example:8443",
                    "Origin":"https://router.example"},
                  "environment":{"APP_NAME":"OpenCode","STAGE":"production","LANG":"en_US.UTF-8","MODEL":"claude-sonnet-4-5",
                    "PROVIDER":"Anthropic","LEVEL":"Debug","OWNER":"GitHub","USE_OAUTH":"enabled"}}}}}
                """.trimIndent()
            )
        val appText =
            "OpenCode Mobile · production · application/json · claude-sonnet-4-5 on Anthropic via GitHub (enabled, Debug)"
        assertEquals(appText, redactor.redact(appText), "an app word was registered from a header or env value")
        for (word in listOf("OpenCode", "Mozilla/5.0 (X11; Linux x86_64) OpenCode/2.2", "gpt-4o-mini", "mcp.router.example:8443", "en_US.UTF-8"))
            assertReadable(redactor, word)
    }

    @Test
    fun `W4-MCP-GATE token-shaped values are masked whatever the name, standard headers included`() {
        val inTitle = "Tt" + "GateTitleToken" + "1234567890"
        val inAgent = "Ua" + "GateAgentToken" + "0987654321"
        val randomCase = "xKqPz" + "LmNvBtRw"
        val mixedShort = "ab3dE5" + "fG9h"
        val bearer = "sb-" + "GateStandardBearer" + "2j3k"
        val redactor =
            read(
                """
                {"mcp":{"servers":{"r":{"type":"remote","url":"https://r.example/mcp",
                  "headers":{"X-Title":"$inTitle","User-Agent":"$inAgent","X-Workspace":"$randomCase","X-Team":"$mixedShort",
                             "Accept":"Bearer $bearer"}}}}}
                """.trimIndent()
            )
        assertMasked(redactor, "a token in a standard header", inTitle)
        assertMasked(redactor, "a token in User-Agent", inAgent)
        assertMasked(redactor, "a random-case letters-only value", randomCase)
        assertMasked(redactor, "a short mixed letters-and-digits value", mixedShort)
        assertMasked(redactor, "a bearer under a standard header", bearer)
    }

    @Test
    fun `W4-MCP-GATE the config text rule in both directions`() {
        for (value in listOf("OpenCode", "OpenCode Mobile", "GitHub", "production", "application/json", "*/*",
            "text/event-stream, application/json", "mcp.example.com", "localhost:4096", "en_US.UTF-8", "gpt-4o-mini",
            "claude-sonnet-4-5", "us-east-1", "8080", "1.2.3"))
            assertTrue(ConfigValues.isOrdinaryConfigText(value), "\"$value\" should count as ordinary text")
        for (value in listOf("xKqPz" + "LmNvBtRw", "ab3dE5" + "fG9h", "4829" + "137560", "abcd-efgh-" + "ijkl-mnop",
            "correct horse battery " + "staple", "my-gcp-" + "project42", "Bearer " + "abcdefgh"))
            assertFalse(ConfigValues.isOrdinaryConfigText(value), "a possible secret counts as ordinary text")
        // The typed-answer rule stays narrow: a word or a slug is not "plainly not secret" there.
        for (value in listOf("OpenCode", "gpt-4o-mini", "mcp.example.com", "4829" + "137560"))
            assertFalse(ConfigValues.isPlainlyNotSecret(value), "the typed-answer rule widened")
        for (name in listOf("NOTION_AUTH", "SMTP_PASS", "SLACK_PIN", "--otp", "DB_PASSPHRASE", "MYSQL_PWD"))
            assertTrue(ConfigValues.isCredentialName(name), "$name is not a credential name")
        for (name in listOf("PWD", "OLDPWD", "SPIN", "author", "MAX_TOKENS"))
            assertFalse(ConfigValues.isCredentialName(name), "$name counts as a credential name")
    }

    @Test
    fun `W4-MCP-GATE the shade mask hides registered values and nothing else`() {
        val redactor = Redactor()
        val value = "sv-" + "GateShadeValue" + "4l5m"
        redactor.registerSecret(value)
        assertEquals("sent ••• to OpenCode", redactor.maskKnown("sent $value to OpenCode"))
        // Patterns are NotificationText's own business: maskKnown leaves secret-shaped text alone.
        assertEquals("password=hunter2", redactor.maskKnown("password=hunter2"))
        try {
            KnownSecrets.install(KnownSecretMask(redactor::maskKnown))
            assertEquals("sent ••• to OpenCode", KnownSecrets.mask("sent $value to OpenCode"))
        } finally {
            KnownSecrets.install(null)
        }
        assertEquals("sent $value", KnownSecrets.mask("sent $value"), "nothing installed: text passes unchanged")
    }
}
