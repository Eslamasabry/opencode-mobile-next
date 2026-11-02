package io.github.eslamasabry.oc.engine.agents.signin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/agent_sign_in_output_test.dart (B-AGENT-026). */
class SignInOutputReaderTest {
    private fun rows(text: String) = text.split('\n').map { SignInRow(it, wrapped = false) }

    private fun read(text: String, width: Int? = null) = SignInOutputReader.read(rows(text), width)

    @Test
    fun `B-AGENT-026 fx - the Vercel device page and its code, through colours`() {
        val found = read(
            "\u001B[1m> \u001B[22mVisit \u001B[36mhttps://vercel.com/oauth/device?user_code=ABCD-EFGH\u001B[39m to sign in.\n" +
                "  Your code: \u001B[1mABCD-EFGH\u001B[22m",
        )
        assertEquals("https://vercel.com/oauth/device?user_code=ABCD-EFGH", found.page?.url)
        assertEquals("ABCD-EFGH", found.code)
    }

    @Test
    fun `B-AGENT-026 Codex device sign-in - the code on the line after code`() {
        val found = read(
            "Follow these steps to sign in with ChatGPT using device code authorization:\n" +
                "\n" +
                "1. Open this link in your browser and sign in to your account\n" +
                "   \u001B[94mhttps://auth.openai.com/codex/device\u001B[0m\n" +
                "\n" +
                "2. Enter this one-time code \u001B[90m(expires in 15 minutes)\u001B[0m\n" +
                "   \u001B[94mABCD-12345\u001B[0m\n" +
                "\n" +
                "Device codes are a common phishing target. Never share this code.",
        )
        assertEquals("https://auth.openai.com/codex/device", found.page?.url)
        assertEquals("ABCD-12345", found.code)
    }

    @Test
    fun `B-AGENT-026 Claude - its page and no code to copy`() {
        val found = read(
            "Browser didn't open? Use the url below to sign in:\n\n" +
                "https://claude.ai/oauth/authorize?code=true&client_id=9d1c250a" +
                "&response_type=code&code_challenge=AbCd-EfGh123&state=XyZ\n\n" +
                "Paste code here if prompted >",
        )
        assertEquals("claude.ai", found.page?.host)
        assertEquals("XyZ", found.page?.queryParameter("state"))
        assertNull(found.code)
    }

    @Test
    fun `B-AGENT-026 a page a program broke at the edge is put back together`() {
        val url = "https://claude.ai/oauth/authorize?code=true&client_id=9d1c250a" +
            "&response_type=code&redirect_uri=https%3A%2F%2Fconsole.anthropic" +
            ".com%2Foauth%2Fcode%2Fcallback&state=last"
        val width = 40
        val pieces = (url.indices step width).map { url.substring(it, minOf(it + width, url.length)) }
        val found = read(pieces.joinToString("\n") + "\n\nPaste code here if prompted >", width)
        assertEquals(url, found.page?.url)
    }

    @Test
    fun `B-AGENT-026 rows the terminal wrapped are one line`() {
        val found = SignInOutputReader.read(
            listOf(
                SignInRow("Visit https://vercel.com/oauth/devi", wrapped = false),
                SignInRow("ce?user_code=WXYZ-QRST now", wrapped = true),
            ),
            width = 35,
        )
        assertEquals("https://vercel.com/oauth/device?user_code=WXYZ-QRST", found.page?.url)
        assertEquals("WXYZ-QRST", found.code)
    }

    @Test
    fun `B-AGENT-026 the next sentence is not read as the rest of a page`() {
        val line = "https://auth.example.com/abcde" // exactly 30 columns
        assertEquals(line, read("$line\nPaste code here", 30).page?.url)
    }

    @Test
    fun `B-AGENT-026 takes the newest page`() {
        val found = read(
            "https://vercel.com/oauth/device?user_code=AAAA-BBBB\n" +
                "That code expired. Visit\n" +
                "https://vercel.com/oauth/device?user_code=CCCC-DDDD",
        )
        assertEquals("CCCC-DDDD", found.page?.queryParameter("user_code"))
        assertEquals("CCCC-DDDD", found.code)
    }

    @Test
    fun `B-AGENT-026 never a plain http or this phone's own page`() {
        assertNull(
            read(
                "Listening on http://localhost:1455/auth/callback\n" +
                    "Open https://localhost:8080/start or https://127.0.0.1/x\n" +
                    "or https://app.localhost/x or http://example.com/login or https://[::1]:9/x",
            ).page,
        )
        val found = read("https://auth.openai.com/codex/device\nWaiting on http://localhost:1455/auth/callback")
        assertEquals("https://auth.openai.com/codex/device", found.page?.url)
        assertNull(read("https://user:pw@auth.example.com/device").page, "user info is never a sign-in page")
    }

    @Test
    fun `B-AGENT-026 drops sentence punctuation after a page and reads OSC links`() {
        val found = read(
            "Open (\u001B]8;;https://auth.openai.com/codex/device\u0007" +
                "https://auth.openai.com/codex/device\u001B]8;;\u0007).",
        )
        assertEquals("https://auth.openai.com/codex/device", found.page?.url)
    }

    @Test
    fun `B-AGENT-026 a code is only one the output calls a code`() {
        assertNull(read("Mode: READ-ONLY\nBUILD-2026 ok").code)
        assertEquals("ABCD-EFGH", read("Enter the code:\n  ABCD-EFGH").code)
        assertNull(read("code 2026-1007").code, "digits only")
    }

    @Test
    fun `B-AGENT-026 merge keeps what scrolled away and a new page brings its code`() {
        val first = SignInOutput(page = null, code = "ABCD-EFGH")
        val page = read("https://a.example/device").page
        val withPage = first.merge(SignInOutput(page = page))
        assertNull(withPage.code)
        assertEquals(withPage, withPage.merge(SignInOutput()))
        assertTrue(SignInOutput().isEmpty)
        assertFalse(withPage.isEmpty)
    }

    @Test
    fun `B-AGENT-026 nothing read is ever printed`() {
        val found = read("Visit https://vercel.com/oauth/device?user_code=ABCD-EFGH")
        assertFalse("ABCD" in found.toString())
        assertFalse("vercel" in found.page.toString())
        assertFalse("vercel" in SignInRow("https://vercel.com").toString())
    }
}
