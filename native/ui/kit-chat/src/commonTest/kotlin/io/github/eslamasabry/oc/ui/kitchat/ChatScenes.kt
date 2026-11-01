package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffView
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.TestStreamingMarkdown
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import kotlinx.collections.immutable.persistentSetOf

/**
 * The S2-KCB renderer scenes, shared by the desktop tests and the Robolectric goldens. Copy is sample copy (tests only);
 * screens take theirs from `:ui:words`. Arabic scenes keep code, commands and paths in English: they must stay LTR.
 */
class ChatSceneWords(
    val reply: String,
    val copy: String,
    val more: (Int) -> String,
    val toolRead: String,
    val toolRan: String,
    val toolEdit: String,
    val done: String,
    val stopped: String,
    val running: String,
    val seconds: (String) -> String,
    val showAll: String,
    val lines: (Int, Int) -> String,
    val unchanged: (Int) -> String,
    val exit: String,
    val askTitle: String,
    val askSummary: String,
    val allowOnce: String,
    val always: String,
    val deny: String,
) {
    companion object {
        val En =
            ChatSceneWords(
                reply =
                    """
                    ## Plan

                    I read `lib/main.dart:42` and found the **bug**: the list *never* scrolls. See [the guide](https://example.com).

                    1. Fix the scroll
                    2. Run the tests
                      - unit tests first

                    > Keep the change small.

                    | file | lines |
                    | --- | ---: |
                    | main.dart | 12 |
                    | list.dart | 140 |

                    ```kotlin
                    fun main() {
                        val name = "chat" // a comment
                        println(name.length + 1)
                    }
                    ```
                    """.trimIndent(),
                copy = "Copy", more = { "Show all, $it more lines" },
                toolRead = "Read main.dart", toolRan = "Ran a command", toolEdit = "Edited list.dart",
                done = "Done", stopped = "Stopped", running = "Running", seconds = { "$it s" }, showAll = "Show all", exit = "Exit code 1",
                lines = { a, b -> "Lines $a–$b" }, unchanged = { "$it unchanged lines" },
                askTitle = "Run npm install in neon-breaker?", askSummary = "The agent wants to run a command.",
                allowOnce = "Allow once", always = "Always allow", deny = "Deny",
            )

        val Ar =
            ChatSceneWords(
                reply =
                    """
                    ## الخطة

                    قرأت `lib/main.dart:42` ووجدت **الخطأ**: القائمة *لا* تتحرك. انظر [الدليل](https://example.com).

                    1. أصلح التمرير
                    2. شغّل الاختبارات
                      - اختبارات الوحدة أولًا

                    > أبقِ التغيير صغيرًا.

                    | الملف | الأسطر |
                    | --- | ---: |
                    | main.dart | 12 |
                    | list.dart | 140 |

                    ```kotlin
                    fun main() {
                        val name = "chat" // a comment
                        println(name.length + 1)
                    }
                    ```
                    """.trimIndent(),
                copy = "نسخ", more = { "عرض الكل، $it أسطر أخرى" },
                toolRead = "قرأ main.dart", toolRan = "شغّل أمرًا", toolEdit = "عدّل list.dart",
                done = "تم", stopped = "أُوقف", running = "يعمل", seconds = { "$it ث" }, showAll = "عرض الكل", exit = "رمز الخروج 1",
                lines = { a, b -> "الأسطر $a–$b" }, unchanged = { "$it أسطر بلا تغيير" },
                askTitle = "تشغيل npm install في neon-breaker؟", askSummary = "يريد الوكيل تشغيل أمر.",
                allowOnce = "السماح مرة", always = "السماح دائمًا", deny = "رفض",
            )

        fun of(tag: String): ChatSceneWords = if (tag.startsWith("ar")) Ar else En
    }
}

private val sceneActions =
    KitMarkdownActions(onLink = {}, confirmedPaths = persistentSetOf("lib/main.dart"), copyCodeLabel = null)

@Composable
private fun Page(tag: String, content: @Composable () -> Unit) {
    Column(
        Modifier.testTag(tag).fillMaxWidth().background(LocalKitColors.current.ground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** A settled reply with every block kind. */
@Composable
fun MarkdownScene(words: ChatSceneWords, live: Boolean = false) {
    Page("markdown") { KitMarkdown(TestStreamingMarkdown.parse(words.reply), sceneActions, live = live) }
}

/** Code (coloured), a command with a heredoc (one `$` per command) and capped output. */
@Composable
fun CodeScene(words: ChatSceneWords) {
    Page("code") {
        KitCodeBlock(
            source = "import os\n\ndef main():\n    print(\"hello\", 42)  # greet\n",
            kind = KitCodeKind.Code, language = CodeLanguage.Python, copyLabel = words.copy, onCopy = {},
        )
        KitCodeBlock(
            source = "cat > /tmp/a.py <<'EOF'\nimport os\nEOF\npython3 /tmp/a.py && \\\n  echo done",
            kind = KitCodeKind.Command, copyLabel = words.copy, onCopy = {},
        )
        KitCodeBlock(
            source = (1..16).joinToString("\n") { "test $it passed" },
            kind = KitCodeKind.Output, moreLabel = words.more, onMore = {},
        )
    }
}

/** Tool steps: a done read, an opened shell step with its command box, a stopped edit with counts. */
@Composable
fun ToolScene(words: ChatSceneWords) {
    Page("tools") {
        KitToolRow(
            KitToolView("t1", KitToolKind.Read, words.toolRead, "lib/main.dart", null, KitToolStatus.Done, words.seconds("0.4"), null, null, words.done),
            opened = false, onToggle = {},
        )
        KitToolRow(
            KitToolView("t2", KitToolKind.Shell, words.toolRan, "npm test", null, KitToolStatus.Failed, words.seconds("3.1"), null, null, words.exit),
            opened = true, onToggle = {},
        ) {
            KitCommandBox(
                command = "npm test", output = (1..14).joinToString("\n") { "  ✓ case $it" },
                moreLabel = words.showAll, onMore = {}, exitLabel = words.exit,
            )
        }
        KitToolRow(
            KitToolView("t3", KitToolKind.Edit, words.toolEdit, "lib/list.dart", null, KitToolStatus.Stopped, null, 4, 1, words.stopped),
            opened = false, onToggle = {},
        )
    }
}

/** The permission card shell with all three answers. */
@Composable
fun PermissionScene(words: ChatSceneWords) {
    Page("permission") {
        KitPermissionCard(
            title = words.askTitle, summary = words.askSummary, subject = "npm install --save-dev vitest",
            allowOnceLabel = words.allowOnce, alwaysLabel = words.always, denyLabel = words.deny,
            onAllowOnce = {}, onAlways = {}, onDeny = {},
        )
    }
}

private val sceneBefore = (1..30).joinToString("\n") { "fun step$it() = run($it)" }
private val sceneAfter =
    sceneBefore.replace("fun step8() = run(8)", "fun step8() = run(8, fast = true)")
        .replace("fun step21() = run(21)\n", "")

/** An edit step opened on its inline diff (≤ 12 lines, LTR, folded to 3 context lines). */
@Composable
fun DiffScene(words: ChatSceneWords) {
    Page("diff") {
        KitToolRow(
            KitToolView("t4", KitToolKind.Edit, words.toolEdit, "lib/list.dart", null, KitToolStatus.Done, null, 1, 2, words.done),
            opened = true, onToggle = {},
        ) {
            KitDiffView(
                file = DiffModel.fromBeforeAfter("lib/list.dart", sceneBefore, sceneAfter),
                hunkLabel = { a, b, _, _ -> words.lines(a, b) },
                gapLabel = words.unchanged,
            )
        }
    }
}
