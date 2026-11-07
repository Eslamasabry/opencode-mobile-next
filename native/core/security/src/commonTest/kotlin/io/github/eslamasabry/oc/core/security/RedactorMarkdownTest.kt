package io.github.eslamasabry.oc.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E15-S05: the go/no-go on build 3062b719a pasted a Copy transcript with fake keys in clear and an inline-code span
 * whose closing backtick the mask had eaten. These are those fakes (built from adjacent literals so the source holds no
 * whole key), in the Markdown shapes a reply uses. Failure messages name the shape, never the value.
 */
class RedactorMarkdownTest {
    private val project = "sk-proj-" + "abcdefghijklmnopqrstuvwxyz" + "0123456789ABCD"
    private val github = "ghp_" + "aBcDeFgHiJkLmNoPqRsTuVwXyZ" + "0123456789"
    private val anthropic = "sk-ant-" + "api03-G12Qx7Anth" + "Secret0a1b2c3d4e"
    private val google = "AIza" + "SyG12Hv8GoogleSecret" + "9t0u1v2w3x4y5z6A"
    private val slack = "xoxb-" + "1234567890-" + "G12SlackSecret"
    private val pat = "github_pat_" + "11ABCDEFG0123456789" + "_abcdefXYZ"
    private val env = "G12Bs0EnvSecret" + "8W9X0Y1Z2a"

    private val redactor = Redactor()

    private fun backticks(text: String) = text.count { it == '`' }

    @Test
    fun `B-SEC-144 the go-no-go fakes are masked in plain text, inline code and a fenced block`() {
        val reply =
            """
            Here are the fake keys you asked for:

            - $project
            - `$github`
            - `OPENAI_API_KEY=$project`

            ```bash
            export GITHUB_TOKEN=$github
            ANTHROPIC_API_KEY=$anthropic
            ```
            """.trimIndent()
        val safe = redactor.redact(reply)
        for ((shape, key) in listOf("sk-proj" to project, "ghp_" to github, "sk-ant" to anthropic)) {
            assertFalse(key in safe, "$shape key is still in clear")
        }
        assertFalse("abcdefghijklmnopqrstuvwxyz" in safe, "sk-proj tail is still in clear")
        assertFalse("aBcDeFgHiJkLmNoPqRsTuVwXyZ" in safe, "ghp_ tail is still in clear")
        assertEquals(backticks(reply), backticks(safe), "masking must keep every backtick")
        assertTrue("- `ghp_•••`" in safe, "inline code keeps both backticks")
        assertTrue("- `OPENAI_API_KEY=•••`" in safe, "a KEY=value in inline code keeps its closing backtick")
        assertTrue("```bash\n" in safe && safe.trimEnd().endsWith("```"), "the fence survives")
    }

    @Test
    fun `B-SEC-144 every Flutter KitRedact token shape is masked inside inline code`() {
        val shapes =
            listOf(
                "sk-proj" to project,
                "ghp_" to github,
                "sk-ant" to anthropic,
                "AIza" to google,
                "xoxb" to slack,
                "github_pat" to pat,
                "Bearer" to "Authorization: Bearer $env",
                "_KEY" to "OPENAI_API_KEY=$env",
                "_TOKEN" to "GH_TOKEN=$env",
                "_SECRET" to "CLIENT_SECRET=$env",
                "PASSWORD" to "DB_PASSWORD=$env",
                "password" to "password: $env",
            )
        for ((shape, value) in shapes) {
            val line = "Use `$value` here."
            val safe = redactor.redact(line)
            assertFalse(env in safe || (value.length > 20 && value in safe), "$shape is still in clear")
            assertEquals(2, backticks(safe), "$shape: the inline code keeps both backticks")
            assertTrue(safe.endsWith("` here."), "$shape: the text after the code survives")
        }
    }

    @Test
    fun `B-SEC-144 a PEM private key inside a fence is masked and the fence stays`() {
        val pem =
            "-----BEGIN " + "PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END " + "PRIVATE KEY-----"
        val safe = redactor.redact("```\n$pem\n```")
        assertEquals("```\n•••\n```", safe)
    }

    @Test
    fun `B-SEC-144 a quoted header value inside inline code keeps its backticks`() {
        val safe = redactor.redact("`\"Authorization\": abc$env`")
        assertFalse(env in safe)
        assertEquals(2, backticks(safe))
    }
}
