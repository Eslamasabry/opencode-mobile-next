package io.github.eslamasabry.oc.android.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of `test/notification_text_test.dart` (plan 16 §1.8: no secrets in notification text). */
class NotificationTextTest {
    private fun redact(s: String, max: Int = 140) = NotificationText.redact(s, max)

    @Test
    fun `B-SEC-106 ordinary commands stay whole`() {
        assertEquals("npm install --save left-pad", redact("npm install --save left-pad"))
        assertEquals("git push origin main", redact("git   push\n origin main"))
    }

    @Test
    fun `B-SEC-106 bearer tokens provider keys and passwords are masked and the key name stays`() {
        // Same as Dart: the bearer pattern masks the token, then the header name keeps only its key.
        assertEquals("curl -H Authorization=••• •••", redact("curl -H Authorization: Bearer abcdefghijklmnop"))
        assertEquals("Bearer •••", redact("Bearer abcdefghij"))
        assertEquals("export OPENAI_API_KEY=•••", redact("export OPENAI_API_KEY=sk-live1234567890abcdef"))
        assertEquals("login --password •••", redact("login --password hunter22"))
        assertEquals("echo •••", redact("echo sk-proj1234567890"))
        assertEquals("push •••", redact("push ghp_" + "a".repeat(20)))
        assertEquals("use •••", redact("use AKIA" + "ABCDEFGHIJKLMNOP"))
        assertEquals("key •••", redact("key AIza" + "b".repeat(24)))
        assertEquals("slack •••", redact("slack " + "xox" + "b-1234567890-abc")) // built at runtime: the push scanner
    }

    @Test
    fun `B-SEC-106 URL credentials are masked keeping the host`() {
        val withCredentials = "git clone https://" + "me" + ":" + "s3cret" + "@example.com/repo.git" // built at runtime
        assertEquals("git clone https://•••@example.com/repo.git", redact(withCredentials))
    }

    @Test
    fun `B-SEC-106 long token-like runs are masked and the line is one ellipsised line`() {
        assertEquals("id •••", redact("id " + "Zx9".repeat(12)))
        val long = redact("word ".repeat(60), max = 20)
        assertEquals(20, long.length)
        assertTrue(long.endsWith("…"))
        assertFalse('\n' in redact("a\nb\tc"))
    }

    @Test
    fun `B-SEC-105 a permission names the program and sub-command, never a secret`() {
        assertEquals("npm install", NotificationText.commandHead("npm install --save left-pad"))
        assertEquals("git push", NotificationText.commandHead("git push origin main"))
        assertEquals("pytest", NotificationText.commandHead("/usr/bin/pytest -q"))
        assertEquals("deploy", NotificationText.commandHead("TOKEN=abc123456 API_KEY=zzz deploy"))
        val head = NotificationText.commandHead("curl --token sk-abcdefghijklmnop https://x")
        assertFalse("sk-abcdefghijklmnop" in head)
    }

    @Test
    fun `B-SEC-105 the file gets the right verb and an ask with nothing to show has no target`() {
        assertEquals(PermissionAsk(AskAction.Run, "npm test"), NotificationText.permissionAsk("bash", "npm test", null))
        assertEquals(PermissionAsk(AskAction.Read, "settings.dart"), NotificationText.permissionAsk("read", null, "/lib/settings.dart"))
        assertEquals(PermissionAsk(AskAction.Folder, "outside"), NotificationText.permissionAsk("external_directory", null, "/tmp/outside/"))
        assertEquals(PermissionAsk(AskAction.Edit, "a.kt"), NotificationText.permissionAsk("edit", "  ", "C:\\src\\a.kt"))
        assertNull(NotificationText.permissionAsk("webfetch", null, null))
    }

    @Test
    fun `B-SEC-105 the subtitle is server and project with either optional`() {
        assertEquals("MacBook · api-server", NotificationText.subtitle(" MacBook ", "api-server"))
        assertEquals("MacBook", NotificationText.subtitle("MacBook", "  "))
        assertEquals("api", NotificationText.subtitle(null, "api"))
        assertEquals("", NotificationText.subtitle(null, null))
    }

    @Test
    fun `B-ANDR-048 the finished line is the first line of the newest reply joining a list intro`() {
        assertEquals("Done. All tests pass.", NotificationText.firstReplyLine(listOf("old", "## Done. All tests pass.\nmore")))
        assertEquals("Changes made: Added a counter", NotificationText.firstReplyLine(listOf("Changes made:\n- Added a counter\n- Fixed")))
        assertEquals("Summary", NotificationText.firstReplyLine(listOf("Summary:")))
        assertEquals("earlier", NotificationText.firstReplyLine(listOf("earlier", "  \n ")))
        assertEquals("", NotificationText.firstReplyLine(emptyList()))
        assertEquals("token=•••", NotificationText.firstReplyLine(listOf("token: abcdef123456")))
    }

    @Test
    fun `B-ANDR-048 stopped reasons become plain tokens`() {
        assertEquals(StopReason.Auth, NotificationText.stoppedReason("ProviderAuthError"))
        assertEquals(StopReason.Context, NotificationText.stoppedReason("ContextOverflowError"))
        assertEquals(StopReason.Length, NotificationText.stoppedReason("MessageOutputLengthError"))
        assertEquals(StopReason.Filter, NotificationText.stoppedReason("content_filter"))
        assertEquals(StopReason.Model, NotificationText.stoppedReason("ProviderModelNotFoundError"))
        assertEquals(StopReason.Other, NotificationText.stoppedReason("MessageAbortedError"))
        assertEquals(StopReason.Other, NotificationText.stoppedReason("Something the server said: secret"))
        assertEquals(StopReason.Other, NotificationText.stoppedReason(null))
    }
}
