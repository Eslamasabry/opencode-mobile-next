package io.github.eslamasabry.oc.engine.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitScriptTest {
    @Test
    fun `B-SEC-083 quoting stops every expansion and quotes every word`() {
        assertEquals("'plain'", shQuote("plain"))
        assertEquals("'it'\\''s'", shQuote("it's"))
        assertEquals("'\$(rm -rf /) `id` \$HOME *'", shQuote("\$(rm -rf /) `id` \$HOME *"))
        assertEquals("'a b' 'c;d'", shCommand(listOf("a b", "c;d")))
    }

    @Test
    fun `B-SEC-083 a NUL byte is refused without echoing the value`() {
        val error = assertFailsWith<GitQuoteException> { shQuote("secret-value\u0000x") }
        assertFalse("secret-value" in error.toString())
        assertFalse("secret-value" in (error.message ?: ""))
    }

    @Test
    fun `B-SEC-083 the script renders steps, quoted folder and the fixed environment`() {
        val script =
            GitScript(
                "/srv/my project",
                listOf(
                    GitStep("status", listOf("git", "status"), stopOnFailure = true),
                    GitStep("log", listOf("git", "log", "-m", "it's \$HOME")),
                ),
            ).render()
        assertTrue("cd -- '/srv/my project' 2>/dev/null || { printf '@@OCG nodir\\n'; exit 0; }" in script)
        assertTrue("command -v git >/dev/null 2>&1 || { printf '@@OCG nogit\\n'; exit 0; }" in script)
        assertTrue("GIT_TERMINAL_PROMPT='0'; export GIT_TERMINAL_PROMPT" in script)
        assertTrue("LC_ALL='C'; export LC_ALL" in script)
        assertTrue("ocg_s status 'git' 'status' || { printf '@@OCG end\\n'; exit 0; }" in script)
        assertTrue("ocg_s log 'git' 'log' '-m' 'it'\\''s \$HOME'" in script)
        assertTrue(script.trimEnd().endsWith("printf '@@OCG end\\n'"))
    }

    @Test
    fun `B-SEC-083 step and variable names are validated`() {
        assertFailsWith<IllegalArgumentException> { GitScript("/p", listOf(GitStep("bad name", listOf("git")))).render() }
        assertFailsWith<IllegalArgumentException> {
            GitScript("/p", listOf(GitStep("ok", listOf("git"))), environment = mapOf("bad-name" to "x")).render()
        }
    }

    @Test
    fun `B-SEC-083 envelopes decode while ignoring noise`() {
        val text = envelopeOf(StepReply("status", 0, "## main\n", ""), StepReply("log", 128, "", "fatal: bad\n"))
        val envelope = GitEnvelope.parse("banner line\n" + text + "trailing noise\n")
        assertNull(envelope.problem)
        assertEquals("## main\n", envelope["status"]?.stdout)
        assertEquals(128, envelope["log"]?.exitCode)
        assertEquals("fatal: bad\n", envelope["log"]?.stderr)
        assertFalse(envelope["log"]!!.ok)
    }

    @Test
    fun `B-SEC-083 cut-off answers and missing folder or git are reported`() {
        assertEquals(GitEnvelopeProblem.Truncated, GitEnvelope.parse("@@OCG begin\n@@OCG out a QQ==\n").problem)
        assertEquals(
            GitEnvelopeProblem.Truncated,
            GitEnvelope.parse("@@OCG begin\n@@OCG exit a 0\n@@OCG end\n").problem,
        )
        assertEquals(GitEnvelopeProblem.FolderMissing, GitEnvelope.parse("@@OCG begin\n@@OCG nodir\n").problem)
        assertEquals(GitEnvelopeProblem.GitMissing, GitEnvelope.parse("@@OCG begin\n@@OCG nogit\n").problem)
        assertEquals(GitEnvelopeProblem.Truncated, GitEnvelope.parse("").problem)
    }
}
