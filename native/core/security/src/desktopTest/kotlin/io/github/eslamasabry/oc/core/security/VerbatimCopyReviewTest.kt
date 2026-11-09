package io.github.eslamasabry.oc.core.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Port of the "redact: false is only for the person's own content (SEC-13)" group in test/redaction_test.dart. Every
 * clipboard write and every text share (`putExtra(Intent.EXTRA_TEXT, …)`) in production Kotlin is reviewed here, the
 * reviewed counts only shrink, and each entry says why it is safe: the person's own content, text already masked, or
 * the one redacting copy (`KitCopy` in :ui:kit-chat, the port of Flutter's `KitCopy.copy` with its default
 * `redact: true`). E15-S05 added share intents, the chat's copy/share and Copy transcript.
 */
class VerbatimCopyReviewTest {
    /** path (from native/) → (count, why the text is the person's own or already masked). */
    private val reviewedOwnContent: Map<String, Pair<Int, String>> =
        mapOf(
            "feature/factsbook/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/factsbook/ui/PageScreens.kt" to
                (1 to "Not saved yet: Copy gives the person back the text they typed for FactsBook that could not be saved; it is their own content, never server text"),
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitDetailsFold.kt" to
                (1 to "Copy all of a Details fold: the fold holds Redacted details and closed categories only"),
            "feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/ui/TeamIntroScreen.kt" to
                (1 to "HostGuide copies the app's own pinned setup commands (HostScripts and fixed constants); no server text"),
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitStateView.kt" to
                (1 to "copies failure.details, a Redacted value built by the one Redactor"),
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/header/KitChatHeaderParts.kt" to
                (1 to "KitClipboardEffect: the chat header masks everything but the person's own prompts first"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt" to
                (1 to "DiagnosticsExport.copy: the bundled voice licence text (an app asset), never server text"),
            // Dart twin: lib/ui/kit/kit_terminal_view.dart `_KitTerminalOscHost` (redact: false, reviewed in
            // test/redaction_test.dart). Masking here would corrupt a copy the person's own program made on purpose.
            "android/terminal/src/main/kotlin/io/github/eslamasabry/oc/android/terminal/TerminalSurfaceHost.kt" to
                (1 to "copyTerminalText: a text selection the person copied, or OSC 52 from their own terminal " +
                    "program (their shell, their content); honoured only while the terminal is on screen, ≤ 64 KiB"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AgentAccountWiring.kt" to
                (1 to "a one-time device code for the person to type (1–64 chars, no controls, current profile), flagged sensitive"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AgentTerminalWiring.kt" to
                (1 to "SignInClipboard.copySensitive: only the sign-in code found in the agent terminal, flagged sensitive"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/DiagnosticsAttentionWiring.kt" to
                (2 to "the problem report, built by ProblemReports.scrub with events masked by redact and opaqueTokens; " +
                    "the crash share sheet sends the CrashReportBuilder preview the person saw"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/MigrationWiring.kt" to
                (1 to "shares the app's own category-only update report (app, step id, failure class; no path or value)"),
            "android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/AndroidShareService.kt" to
                (1 to "shareText: its one caller (SettingsWiring.sendText) passes the app's own share message"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/HandoffDraftWiring.kt" to
                (1 to "handoff markdown from HandoffRecordBuilder, which masks every piece with redact and opaqueTokens"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/IncomingShare.kt" to
                (1 to "the words the person shared into the app from another app: their own text"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/KeptServicesWiring.kt" to
                (1 to "copyLog copies log(name), which already ran the log tail through redactor.redact"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/LinuxAddressCopyWiring.kt" to
                (1 to "only an address matching ^http://127.0.0.1:<port>/$ that the app built itself"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PhoneSignInWiring.kt" to
                (1 to "SignInClipboard: the terminal sign-in code for the person to type, flagged sensitive"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlansSignInWiring.kt" to
                (1 to "a one-time code of 4–24 A–Z, 0–9 or '-' (cannot be a key), flagged sensitive"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ServerIntegrationsWiring.kt" to
                (1 to "rawCopy holds the app's own install and adb commands (port only) and redacted names; fallback is redacted"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/TerminalWiring.kt" to
                (1 to "the person's own terminal selection or transcript (Dart kit_terminal_view.dart, verbatim there too)"),
            "feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/tools/ui/ToolsScreen.kt" to
                (1 to "the tool parameter schema after KitRedact.text"),
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/KitDiffView.kt" to
                (1 to "selected lines of the person's own diff (Dart kit_diff_view.dart, verbatim there too)"),
            "ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/log/KitLogPanel.kt" to
                (2 to "Copy all and the header copy take already-redacted lines; the caller passes the log redacted by the Redactor"),
            "ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalLinks.kt" to
                (1 to "Copy link: a link the person's own terminal printed (their shell, their content), like the terminal selection"),
            "ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalView.kt" to
                (1 to "OSC 52 from the person's own terminal program, honoured only while the terminal is on screen (TerminalOsc52 bounds it)"),
            "ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/KitViewerFrame.kt" to
                (1 to "Copy contents of the person's own project file (SEC-13, verbatim there too, like KitViewerDocument)"),
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitNotice.kt" to
                (1 to "copies d.text of a Redacted value built by the one Redactor (the Details fold), like KitStateView"),
            "ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/KitViewerDocument.kt" to
                (1 to "Copy contents of the person's own project file (Dart kit_viewer.dart, verbatim there too)"),
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/log/KitLogPanel.kt" to
                (1 to "copies mask(text); every production caller passes rememberKitMask (the Redactor)"),
        )

    /**
     * Writes that put only the result of the one redacting copy on the clipboard (E15-S04; Flutter `KitCopy.copy`,
     * `redact: true`). The kit copy parts write `mask.apply(text)`: the mask defaults to verbatim, and every caller in
     * the server-text modules must pass the redacting `KitCopy` mask ([serverTextCopies] and the module scan below).
     */
    private val redacting: Map<String, Pair<Int, String>> =
        mapOf(
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/LibraryWiring.kt" to
                (1 to "copies KitCopy.safeText(redactor, text) only: skill markdown, code blocks and MCP URIs masked"),
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitCopyButton.kt" to
                (1 to "writes mask.apply(text()); the permission prompt's command passes the redacting KitCopy mask"),
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitIconButton.kt" to
                (1 to "writes mask.apply(text()); Active Context tool input/output and reasoning pass the redacting mask"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ChatFeelWiring.kt" to
                (2 to "copy and share write what ChatPresenter hands over: replies, code blocks and tool output after " +
                    "ChatOutside.mask (KitCopy.safeText with the Redactor); the person's own prompt is masked too (E15-S06)"),
        )

    /** Known unmasked copies awaiting a fix; each names its owner. Empty: E15-S04 fixed the last three. */
    private val findings: Map<String, Pair<Int, String>> = emptyMap()

    /** Server-text copy sites (E15-S04) → what each must call, and the verbatim calls it must not make. */
    private val serverTextCopies: Map<String, Pair<List<String>, List<String>>> =
        mapOf(
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/request/KitRequestTargets.kt" to
                (listOf("rememberKitRedactedCopyAction(") to VERBATIM_PARTS),
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitContextMessagePart.kt" to
                (listOf("KitRedactedCopyIconButton(") to VERBATIM_PARTS),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/LibraryWiring.kt" to
                (listOf("KitCopy.safeText(redactor, text)", "ClipData.newPlainText(\"\", safe)") to
                    listOf("ClipData.newPlainText(\"\", text)")),
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitCopyButton.kt" to
                (listOf("mask.apply(text())", "AnnotatedString(out.text)") to listOf("AnnotatedString(text())")),
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitIconButton.kt" to
                (listOf("mask.apply(text())", "AnnotatedString(out.text)") to listOf("AnnotatedString(text())")),
            // E15-S05: the chat's message Copy/Share, code-block and tool-output copies.
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ChatFeelWiring.kt" to
                (listOf("override fun mask(text: String): String = KitCopy.safeText(redactor, text).text") to emptyList()),
            "feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/presentation/ChatPresenter.kt" to
                (listOf("val safe = out.mask(text)", "out.share(out.mask(text))") to
                    listOf("outside?.copy(text)", "outside?.share(text)", "out.copy(text)", "out.share(text)")),
            // E15-S05: Copy transcript masks every piece but the person's own prompt through the screen's Redactor.
            "feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/ui/ChatHeaderBar.kt" to
                (listOf("val mask = rememberKitMask()", "actions.copyTranscript(words, mask)") to emptyList()),
            "feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/presentation/ChatHeaderPresenter.kt" to
                (listOf("mask(piece)", "markdownOf(titleNow(), messages, words, world.hasOlder.value, counting)", "mask = counting") to
                    emptyList()),
        )

    /** Modules whose copies are server text: a kit copy part used there without the redacting mask fails. */
    private val serverTextModules = listOf("ui/kit-chat/src/commonMain", "feature/chat/src/commonMain")

    /** The one file allowed to call the kit copy parts directly in [serverTextModules]: it adds the redacting mask. */
    private val redactingHelper = "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/KitCopy.kt"

    /** A clipboard write, or text handed to another app through the share sheet. */
    private val copyCall =
        Regex("""\.(setPrimaryClip|setClipEntry)\(|\bclipboard\w*\.setText\(|\.setText\(\s*AnnotatedString\(|\bputExtra\(\s*Intent\.EXTRA_TEXT\b""")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*[\s\S]*?\*/"""), "").lines().joinToString("\n") { it.substringBefore("//") }

    private fun nativeRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "native/ root not found" }
    }

    private fun copySites(): Map<String, Int> {
        val root = nativeRoot()
        val counts = sortedMapOf<String, Int>()
        root.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") && it.name !in setOf("spikes", "third_party") }
            .filter { it.isFile && it.extension == "kt" && "/src/" in it.path && "Test" !in it.path.substringAfter("/src/").substringBefore('/') }
            .filter { f -> f.path.substringAfter("/src/").substringBefore('/').let { it.endsWith("Main") || it == "main" || it == "jvmShared" } }
            .forEach { file ->
                val n = copyCall.findAll(stripComments(file.readText())).count()
                if (n > 0) counts[file.relativeTo(root).path] = n
            }
        return counts
    }

    @Test
    fun `B-SEC-145 every verbatim copy in the app is reviewed and the reviewed lists only shrink`() {
        val sites = copySites()
        val reviewed = reviewedOwnContent + redacting + findings
        val problems = mutableListOf<String>()
        for ((path, count) in sites) {
            val entry = reviewed[path]
            if (entry == null) problems += "$path: $count new verbatim clipboard write(s) — only the person's own content copies verbatim"
            else if (count > entry.first) problems += "$path: $count verbatim copies, reviewed ${entry.first} — review the new call site"
        }
        for ((path, entry) in reviewed) {
            val count = sites[path] ?: 0
            if (count < entry.first) problems += "$path: stale — now $count, listed ${entry.first}; shrink the entry"
        }
        assertEquals(emptyList(), problems, problems.joinToString("\n"))
    }

    @Test
    fun `B-SEC-145 every reviewed entry gives a real reason and findings name their owner`() {
        for ((path, entry) in reviewedOwnContent + redacting + findings) {
            assertTrue(entry.first > 0, path)
            assertTrue(entry.second.trim().length > 10, path)
        }
        for ((path, entry) in findings) assertTrue("owner" in entry.second, "$path: a finding names its owner")
        assertTrue(reviewedOwnContent.keys.intersect(findings.keys).isEmpty())
        assertTrue(redacting.keys.intersect(reviewedOwnContent.keys + findings.keys).isEmpty())
    }

    @Test
    fun `B-SEC-145 server text copies through the one redacting copy (E15-S04)`() {
        val root = nativeRoot()
        val problems = mutableListOf<String>()
        for ((path, rule) in serverTextCopies) {
            val file = File(root, path)
            if (!file.isFile) {
                problems += "$path: missing — move its rule with the file"
                continue
            }
            val code = stripComments(file.readText())
            for (needed in rule.first) if (needed !in code) problems += "$path: must copy through `$needed`"
            for (banned in rule.second) if (banned in code) problems += "$path: copies verbatim via `$banned`"
        }
        for (module in serverTextModules) {
            File(root, module).walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                val path = file.relativeTo(root).path
                if (path == redactingHelper) return@forEach
                val code = stripComments(file.readText())
                for (part in VERBATIM_PARTS) if (part in code)
                    problems += "$path: `$part` copies server text verbatim — use KitCopy (rememberKitRedactedCopyAction, " +
                        "KitRedactedCopyIconButton)"
                // A new clipboard write in the chat's code is a finding until it is reviewed or pinned to a masking rule.
                if (copyCall.containsMatchIn(code) && path !in serverTextCopies && path !in reviewedOwnContent)
                    problems += "$path: writes the clipboard directly — server text copies through KitCopy or ChatOutside.mask"
            }
        }
        assertEquals(emptyList(), problems, problems.joinToString("\n"))
    }

    @Test
    fun `B-SEC-145 the scan finds a verbatim copy in code but not in comments`() {
        val sample =
            "// clipboard.setText(x) in a comment\n" +
                "/* manager.setPrimaryClip(clip) */\n" +
                "clipboard.setText(AnnotatedString(text))\n" +
                "manager.setPrimaryClip(ClipData.newPlainText(\"l\", text))"
        assertEquals(2, copyCall.findAll(stripComments(sample)).count())
    }

    @Test
    fun `B-SEC-145 the scan finds a share intent and a clipboard under any name`() {
        val sample =
            "Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, reply)\n" +
                "putExtra( Intent.EXTRA_TEXT, x)\n" +
                "cb.setText(AnnotatedString(reply))\n" +
                "// putExtra(Intent.EXTRA_TEXT, x) in a comment\n" +
                "intent.getStringExtra(EXTRA_TEXT)\n" +
                "iterator.setText(text)"
        assertEquals(3, copyCall.findAll(stripComments(sample)).count())
    }

    @Test
    fun `B-SEC-145 an unreviewed share of server text fails the review`() {
        val reviewed = reviewedOwnContent + redacting + findings
        val planted = "androidApp/src/main/kotlin/p/NewShare.kt"
        assertTrue(planted !in reviewed, "a new share site is not reviewed yet")
        val code = "fun s(reply: String) = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, reply)"
        assertEquals(1, copyCall.findAll(stripComments(code)).count(), "so the module scan reports it")
    }

    private companion object {
        /** The kit copy parts called without a mask (their default is verbatim). */
        val VERBATIM_PARTS = listOf("KitIconButtonCopy(", "rememberKitCopyAction(", "KitCopyButton(")
    }
}
