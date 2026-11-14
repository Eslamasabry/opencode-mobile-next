package io.github.eslamasabry.oc.feature.chat.requests

import io.github.eslamasabry.oc.feature.chat.requests.ui.changeBodyText
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeBodyTextTest {
    @Test
    fun `the svn-style header of a change is left out, the changed lines stay`() {
        val patch = "Index: a1.txt\n===================================================================\n--- a1.txt\n+++ a1.txt\n@@ -0,0 +1,2 @@\n+one\n+two\n"
        assertEquals("@@ -0,0 +1,2 @@\n+one\n+two\n", changeBodyText(patch))
    }

    @Test
    fun `git's header lines are left out too`() {
        val patch = "diff --git a/x.kt b/x.kt\nnew file mode 100644\nindex 0000000..e69de29\n--- /dev/null\n+++ b/x.kt\n@@ -0,0 +1 @@\n+hi"
        assertEquals("@@ -0,0 +1 @@\n+hi", changeBodyText(patch))
    }

    @Test
    fun `a changed line that looks like a header is never dropped`() {
        val patch = "@@ -1,2 +1,2 @@\n--- not a header\n+++ nor this\n context"
        assertEquals(patch, changeBodyText(patch))
    }

    @Test
    fun `a change with no header is as it is`() {
        assertEquals("+hello", changeBodyText("+hello"))
    }
}
