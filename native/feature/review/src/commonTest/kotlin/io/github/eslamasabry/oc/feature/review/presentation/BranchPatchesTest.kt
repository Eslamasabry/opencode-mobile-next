package io.github.eslamasabry.oc.feature.review.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class BranchPatchesTest {
    @Test
    fun `B-GIT-147 a branch patch is split into one diff per file with its line counts`() {
        val patch =
            "diff --git a/index.html b/index.html\nnew file mode 100644\nindex 0000000..e69de29\n--- /dev/null\n+++ b/index.html\n" +
                "@@ -0,0 +1,2 @@\n+<h1>\n+hi\n" +
                "diff --git a/src/app.py b/src/app.py\nindex 111..222 100644\n--- a/src/app.py\n+++ b/src/app.py\n@@ -1 +1 @@\n-old\n+new\n"
        val files = BranchPatches.split(patch)
        assertEquals(listOf("index.html", "src/app.py"), files.map { it.file })
        assertEquals(listOf(2, 1), files.map { it.additions })
        assertEquals(listOf(0, 1), files.map { it.deletions })
        assertEquals(emptyList(), BranchPatches.split(""))
    }
}
