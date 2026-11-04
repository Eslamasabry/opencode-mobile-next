package io.github.eslamasabry.oc.engine.attention.notify

import io.github.eslamasabry.oc.engine.attention.notify.NotificationText.AskTitle
import io.github.eslamasabry.oc.engine.attention.notify.NotificationText.PermissionAsk
import io.github.eslamasabry.oc.engine.attention.notify.NotificationText.QuestionPrompt
import io.github.eslamasabry.oc.engine.attention.notify.NotificationText.ReplyMessage
import io.github.eslamasabry.oc.engine.attention.notify.NotificationText.ReplyPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationTextTest {
    private fun redact(text: String, max: Int = NotificationText.MAX_LENGTH) = NotificationText.redact(text, max)

    private fun reply(role: String, text: String, synthetic: Boolean = false) = ReplyMessage(role, listOf(ReplyPart("text", text, synthetic)))

    @Test
    fun `B-SEC-106 an ordinary command is kept whole`() {
        assertEquals("npm run build", redact("npm run build"))
        assertEquals("flutter test test/tv/tv_focus_test.dart", redact("flutter test test/tv/tv_focus_test.dart"))
    }

    @Test
    fun `B-SEC-106 bearer tokens and authorization values are masked`() {
        val out = redact("""curl -H "Authorization: Bearer abcdef1234567890" https://x.test""")
        assertFalse(out.contains("abcdef1234567890"))
        assertTrue(out.contains("•••"))
    }

    @Test
    fun `B-SEC-106 provider keys, tokens and passwords are masked`() {
        for (secret in listOf("sk-ant-api03-ABCDEFGHIJKLMNOP", "ghp_ABCDEFGHIJKLMNOPQRSTUV", "xoxb-1234567890-abcdefghij", "AKIAABCDEFGHIJKLMNOP", "hunter2hunter2")) {
            assertFalse(redact("deploy --token $secret && echo PASSWORD=$secret").contains(secret), secret)
        }
    }

    @Test
    fun `B-SEC-106 credentials inside a URL are masked and the host is kept`() {
        val out = redact("git clone https://me:s3cretpass@example.com/a/b.git")
        assertFalse(out.contains("s3cretpass"))
        assertTrue(out.contains("example.com"))
    }

    @Test
    fun `B-SEC-106 a long unbroken token-looking run is masked`() {
        assertFalse(redact("echo ${"a1B2".repeat(12)} done").contains("a1B2a1B2a1B2"))
    }

    @Test
    fun `B-SEC-106 the name of a masked key stays so the line still reads`() {
        assertEquals("API_KEY=••• run", redact("API_KEY=abc123xyz run"))
    }

    @Test
    fun `B-SEC-106 notification text is one line cut with an ellipsis`() {
        val out = redact("a\nb\n${"word ".repeat(80)}", 40)
        assertFalse(out.contains("\n"))
        assertTrue(out.length <= 40)
        assertTrue(out.endsWith("…"))
    }

    @Test
    fun `B-SEC-106 the first reply line is the newest assistant reply's, joined with a list item, never a secret`() {
        assertEquals(
            "Dark theme added to Settings.",
            NotificationText.firstReplyLine(listOf(reply("assistant", "Old reply"), reply("user", "next"), reply("assistant", "\n## Dark theme added to Settings.\nMore"))),
        )
        assertEquals(
            "Done. The site now follows the system theme by default: Light and dark both work",
            NotificationText.firstReplyLine(
                listOf(reply("assistant", "Done. The site now follows the system theme by default:\n\n- Light and dark both work\n- The toggle stays")),
            ),
        )
        assertEquals("Changes made", NotificationText.firstReplyLine(listOf(reply("assistant", "Changes made:"))))
        assertTrue(NotificationText.firstReplyLine(listOf(reply("assistant", "Intro that ends with a colon:\n- ${"x".repeat(200)}")), 60).length <= 60)
        assertEquals("", NotificationText.firstReplyLine(listOf(reply("user", "hello"))))
        assertEquals("", NotificationText.firstReplyLine(listOf(reply("assistant", "hidden", synthetic = true))))
        assertEquals("", NotificationText.firstReplyLine(emptyList()))
        assertFalse(NotificationText.firstReplyLine(listOf(reply("assistant", "Set token=ghp_ABCDEFGHIJKLMNOPQRSTUV now"))).contains("ghp_"))
    }

    @Test
    fun `B-SEC-106 a stopped reason is a plain token, never raw text, and buttons follow the short-choice rule`() {
        assertEquals("auth", NotificationText.stoppedReasonToken("ProviderAuthError"))
        assertEquals("context", NotificationText.stoppedReasonToken("ContextOverflowError"))
        assertEquals("length", NotificationText.stoppedReasonToken("MessageOutputLengthError"))
        assertEquals("model", NotificationText.stoppedReasonToken("ModelNotFoundError"))
        assertEquals("other", NotificationText.stoppedReasonToken("SomethingNew"))
        assertEquals("other", NotificationText.stoppedReasonToken(null))
        fun q(labels: List<String>, multiple: Boolean = false) = listOf(QuestionPrompt(multiple, labels))
        assertEquals(listOf("Yes", "No"), NotificationText.optionsOf(q(listOf("Yes", "No"))))
        assertEquals(listOf("a", "b", "c"), NotificationText.optionsOf(q(listOf("a", "b", "c"))))
        assertTrue(NotificationText.optionsOf(q(listOf("a", "b", "c", "d"))).isEmpty())
        assertTrue(NotificationText.optionsOf(q(listOf("Use the long answer option now"))).isEmpty())
        assertTrue(NotificationText.optionsOf(q(listOf("a", "b"), multiple = true)).isEmpty())
        assertTrue(NotificationText.optionsOf(q(listOf("a", "a"))).isEmpty())
        assertTrue(NotificationText.optionsOf(q(emptyList())).isEmpty())
    }

    @Test
    fun `B-SEC-105 a command asks to run its program and sub-command`() {
        assertEquals(AskTitle("run", "npm install"), NotificationText.permissionAskOf(PermissionAsk("bash", mapOf("command" to "npm install --save left-pad"))))
        assertEquals("git push", NotificationText.commandHead("git push origin main"))
        assertEquals("pytest", NotificationText.commandHead("/usr/bin/pytest -q"))
        assertEquals("rm", NotificationText.commandHead("rm -rf build"))
        assertEquals("make test", NotificationText.commandHead("CI=1 FOO=bar make test"))
    }

    @Test
    fun `B-SEC-105 a secret never reaches the title`() {
        assertFalse(NotificationText.commandHead("sk-live-ABCDEFGHIJ1234 --token x").contains("sk-live"))
    }

    @Test
    fun `B-SEC-105 a file asks by its name in the right verb`() {
        assertEquals(AskTitle("edit", "settings.dart"), NotificationText.permissionAskOf(PermissionAsk("edit", patterns = listOf("lib/ui/settings.dart"))))
        assertEquals(AskTitle("read", ".env"), NotificationText.permissionAskOf(PermissionAsk("read", patterns = listOf("config/.env"))))
        assertEquals(AskTitle("folder", "api"), NotificationText.permissionAskOf(PermissionAsk("external_directory", patterns = listOf("/srv/api/"))))
    }

    @Test
    fun `B-SEC-105 an ask with nothing to show has no target`() {
        assertEquals(AskTitle("", ""), NotificationText.permissionAskOf(PermissionAsk("webfetch")))
    }

    @Test
    fun `B-SEC-105 the subtitle is server · project with either part optional`() {
        assertEquals("MacBook · api-server", NotificationText.subtitle("MacBook", "api-server"))
        assertEquals("api-server", NotificationText.subtitle("", "api-server"))
        assertEquals("MacBook", NotificationText.subtitle("MacBook", null))
        assertEquals("", NotificationText.subtitle(null, " "))
    }

    @Test
    fun `B-SEC-105 a permission alert on the shade asks to run the command it shows`() {
        val copy = PermissionShadeCopy.of(PermissionAsk("bash", mapOf("command" to "npm run build")), server = null, project = "api-server", chatTitle = "Fix login")
        assertEquals("run", copy.askAction)
        assertEquals("npm run", copy.askTarget)
        assertEquals("npm run build", copy.detail)
        assertEquals("api-server", copy.subtext)
        assertEquals("Fix login", copy.chatTitle)
        assertTrue(copy.shadeOnly)
    }

    @Test
    fun `B-SEC-105 a secret in the command never leaves the app`() {
        val copy =
            PermissionShadeCopy.of(
                PermissionAsk("bash", mapOf("command" to """curl -H "Authorization: Bearer sk-live-ABCDEFGHIJ1234" https://api.example.com --password hunter2hunter2""")),
                server = "Server", project = "api", chatTitle = null,
            )
        val text = copy.toString()
        assertFalse(text.contains("sk-live-ABCDEFGHIJ1234"))
        assertFalse(text.contains("hunter2hunter2"))
        assertTrue(text.contains("curl"))
    }

    @Test
    fun `B-SEC-105 a file permission asks to edit the file by name`() {
        // OpenCode 2's permission.v2.asked: action edit, resources [lib/main.dart].
        val copy = PermissionShadeCopy.of(PermissionAsk("edit", patterns = listOf("lib/main.dart")), null, null, null)
        assertEquals("edit", copy.askAction)
        assertEquals("main.dart", copy.askTarget)
        assertEquals("lib/main.dart", copy.detail)
        assertEquals("", copy.subtext)
    }
}
