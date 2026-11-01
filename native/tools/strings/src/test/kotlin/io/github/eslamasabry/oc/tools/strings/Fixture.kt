package io.github.eslamasabry.oc.tools.strings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.toPath
import kotlin.io.path.writeText

/** Shared test inputs. */
object Fixture {
    /** Real ARB keys, cut verbatim by `fixture-tools/cut_fixture.py`. */
    val arbDir: Path = Fixture::class.java.getResource("/fixture/lib/l10n/app_en.arb")!!.toURI().toPath().parent

    val goldenDir: Path = Path(System.getProperty("strings.toolDir") ?: ".").resolve("src/test/resources/golden")

    val repoRoot: Path? = System.getProperty("strings.repoRoot")?.let(::Path)?.takeIf { it.resolve("lib/l10n/app_en.arb").exists() }

    val routes = Routes.parse(
        """
        key:team	team
        lib/ui/screens/chat/	chat
        lib/ui/kit/	kit
        lib/ui/widgets/	common
        lib/background/	os
        lib/ui/screens/library_screen.dart	project
        lib/ui/screens/terminal_screen.dart	phone
        """.trimIndent().lines(),
    )

    private const val IMPORT = "import 'package:opencode_mobile/l10n/app_localizations.dart';\n"

    /**
     * A small Dart `lib/` in a temp dir (not in the repo: `flutter analyze` would read it there).
     * Covers: a part file inheriting its library's import, a key read by two features (→ common), a file that
     * names a key without importing the localizations (ignored), and a key nobody reads (→ unused).
     */
    fun dartLib(): Path {
        val root = Files.createTempDirectory("arb2xml-dart").resolve("lib")
        fun file(rel: String, text: String) = root.resolve(rel).also { it.parent.createDirectories() }.writeText(text)
        file(
            "ui/screens/chat/chat_body.dart",
            IMPORT + "part 'parts/chat_more.dart';\n" +
                "Widget b(AppLocalizations l) => Column(children: [\n" +
                "  Text(l.chatUiDraftsQueued(2)), Text(l.chatUiQuestionsSummary('q', 3)),\n" +
                "  Text(l.chatTurnAllFilesMore(4)), Text(l.monitorRequestSummary('a','b','c','d')),\n" +
                "  Text(l.chatUiReferenceAlreadyAdded('x')), Text(l.chatUiRateLimitCountdown('', '1s')),\n" +
                "  Text(l.runningPhoneCpu('idle', 3)), Text(l.e7SettingsStorageSummary),\n" +
                "]);\n",
        )
        file("ui/screens/chat/parts/chat_more.dart", "part of '../chat_body.dart';\nfinal x = l.kitNeedsYouWaitingSpoken(0);\n")
        file("ui/kit/kit_badge.dart", IMPORT + "final b = l.agentAccountPercentUsed;\n")
        file("ui/widgets/shared.dart", IMPORT + "final a = l.servicesIntro; final b = l.paseoPasswordHelp;\n")
        file("ui/screens/library_screen.dart", IMPORT + "final a = l.libraryModelsAgentsTitle; final b = l.servicesIntro;\n")
        file("ui/screens/terminal_screen.dart", IMPORT + "final a = l.terminalShowEarlier(1200); final b = l.aiteamBringInDone;\n")
        file("background/exit_notice.dart", IMPORT + "final a = l.appExitServerStopped('Closed');\n")
        file("state/no_import.dart", "final servicesTitle = x.servicesTitle;\n")
        file("l10n/app_localizations.dart", "String get servicesTitle => 'generated, ignored';\n")
        return root
    }

    fun arbs() = Arb.readDir(arbDir)

    /** The two fixture keys the converter refuses, with the recipes the real hand-recipes.tsv gives them. */
    val recipes = HandRecipes.parse(
        listOf("runningPhoneCpu\tselect\tlevel words", "e7SettingsStorageSummary\tsplit-plurals\ttwo counts"),
    )

    fun conversion(recipes: List<Recipe> = emptyList()): Pair<Conversion, Routing> {
        val arbs = arbs()
        val routing = Router.route(arbs.first().messages.keys, dartLib(), routes)
        val e = Hand.expand(arbs, recipes)
        return Converter({ routing.featureByKey[e.baseOf[it] ?: it] }).convert(e.arbs) to routing
    }
}
