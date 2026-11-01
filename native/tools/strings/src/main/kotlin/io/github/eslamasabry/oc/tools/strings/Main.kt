package io.github.eslamasabry.oc.tools.strings

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val USAGE = """usage: strings convert --repo <repository root> [--skip-unsupported]
       strings check   --repo <repository root>

convert: every lib/l10n/app_<locale>.arb (with the hand recipes) and the Flutter app's native Android strings
  become CMP resource XML in :ui:words: values[-<lang>]/strings_<feature>.xml, strings_os.xml also generated
  into Android res, HandWords.kt, the unused keys and the report. Copy added in :ui:words after the conversion
  is kept. Exit 0 converted; 1 messages it cannot convert (listed; nothing written unless --skip-unsupported);
  2 bad input (usage, unrouted Dart files, malformed ARB, a recipe that does not fit).
check: the drift check (ARB and Android strings vs :ui:words XML, plus the XML's own rules). Exit 0 clean, 1 findings.

Every location defaults from --repo and can be overridden:
  --arb lib/l10n  --dart-lib lib  --android-res android/app/src/main/res (--android-res none to skip)
  --routes, --hand: native/tools/strings/{feature-routes.tsv,hand-recipes.tsv}
  --out native/ui/words/src/commonMain/composeResources  --android-res-out native/ui/words/android-res
  --kotlin-out native/ui/words/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/words/HandWords.kt
  --unused-out native/tools/strings/unused  --report native/tools/strings/report  --template en"""

fun main(args: Array<String>) {
    exitProcess(run(args.toList(), System.out, System.err))
}

private class Options(val opts: Map<String, String>, val flags: Set<String>) {
    private val repo = opts["--repo"]?.let(::Path)

    fun path(flag: String, default: String?): Path? {
        val v = opts[flag]
        if (v == "none") return null
        if (v != null) return Path(v)
        return if (repo != null && default != null) repo.resolve(default) else null
    }

    fun need(flag: String, default: String?): Path =
        path(flag, default) ?: throw IllegalArgumentException("missing $flag (or --repo)")
}

fun run(args: List<String>, out: java.io.PrintStream, err: java.io.PrintStream): Int {
    val command = args.firstOrNull()
    if (command != "convert" && command != "check") { err.println(USAGE); return 2 }
    val opts = HashMap<String, String>()
    val flags = HashSet<String>()
    var i = 1
    while (i < args.size) {
        val a = args[i]
        when {
            a == "--skip-unsupported" -> flags += a
            a.startsWith("--") && i + 1 < args.size -> { opts[a] = args[i + 1]; i++ }
            else -> { err.println("unknown argument: $a\n$USAGE"); return 2 }
        }
        i++
    }
    val o = Options(opts, flags)
    val template = opts["--template"] ?: "en"
    return try {
        if (command == "check") check(o, template, out, err) else convert(o, template, out, err)
    } catch (e: UnroutedPathsException) {
        err.println("strings: ${e.message}"); 2
    } catch (e: ArbFormatException) {
        err.println("strings: ${e.message}"); 2
    } catch (e: HandRecipeException) {
        err.println("strings: ${e.message}"); 2
    } catch (e: IllegalArgumentException) {
        err.println("strings: ${e.message}"); 2
    }
}

private fun check(o: Options, template: String, out: java.io.PrintStream, err: java.io.PrintStream): Int {
    val inputs = Drift.Inputs(
        arbDir = o.need("--arb", Paths.ARB),
        recipes = o.path("--hand", Paths.HAND_RECIPES)?.let(HandRecipes::read).orEmpty(),
        androidRes = o.path("--android-res", Paths.ANDROID_RES)?.takeIf { it.isDirectory() },
        words = o.need("--out", Paths.WORDS),
        unused = o.path("--unused-out", Paths.UNUSED),
        androidResOut = o.path("--android-res-out", Paths.WORDS_ANDROID_RES),
        template = template,
    )
    val findings = Drift.check(inputs)
    if (findings.isEmpty()) { out.println("strings check: no drift"); return 0 }
    err.println("strings check: ${findings.size} findings")
    for ((kind, fs) in findings.groupBy { it.kind }) {
        err.println("  ${kind.name}: ${kind.label} (${fs.size})")
        for (f in fs.take(50)) err.println("    ${f.locale} ${f.name} ${f.detail}")
        if (fs.size > 50) err.println("    … ${fs.size - 50} more")
    }
    return 1
}

private fun convert(o: Options, template: String, out: java.io.PrintStream, err: java.io.PrintStream): Int {
    val skip = "--skip-unsupported" in o.flags
    val arbs = Arb.readDir(o.need("--arb", Paths.ARB), template)
    val recipes = o.path("--hand", Paths.HAND_RECIPES)?.let(HandRecipes::read).orEmpty()
    val routes = Routes.read(o.need("--routes", Paths.ROUTES))
    val routing = Router.route(arbs.first().messages.keys, o.need("--dart-lib", Paths.DART_LIB), routes)
    val expansion = Hand.expand(arbs, recipes, template)
    val featureOf = { k: String -> routing.featureByKey[expansion.baseOf[k] ?: k] }
    val native = o.path("--android-res", Paths.ANDROID_RES)?.takeIf { it.isDirectory() }
        ?.let { AndroidStrings.readDir(it, template) }.orEmpty()
    val conversion = Converter(featureOf, template).convert(expansion.arbs).withNativeOs(native)
    val report: Path = o.need("--report", Paths.REPORT)

    if (conversion.problems.isNotEmpty()) {
        err.println("strings: ${conversion.problems.size} messages cannot be converted to CMP XML:")
        for ((kind, ps) in conversion.problems.groupBy { it.kind }) {
            err.println("  ${kind.name}: ${kind.label}")
            for ((key, kps) in ps.groupBy { it.key }) {
                err.println("    $key [${kps.joinToString(",") { it.locale }}] ${kps.first().detail.take(120)}")
            }
        }
        if (!skip) {
            err.println("Nothing written. Add a hand recipe (hand-recipes.tsv), or rerun with --skip-unsupported to convert the rest.")
            ReportWriter.write(conversion, routing, report, skippedProblems = false, expansion = expansion)
            return 1
        }
    }
    val templateKeys = arbs.first().messages.keys
    val claim = XmlWriter.defaultClaim(conversion)
    val fromArb = { name: String -> claim(name) || expansion.baseKey(name) in templateKeys }
    XmlWriter.write(
        conversion,
        o.need("--out", Paths.WORDS),
        o.path("--unused-out", Paths.UNUSED),
        androidResOut = o.path("--android-res-out", Paths.WORDS_ANDROID_RES),
        fromArb = fromArb,
    )
    o.path("--kotlin-out", Paths.HAND_WORDS_KT)?.let { kt ->
        kt.parent.createDirectories()
        kt.writeText(HandWordsKotlin.render(conversion, expansion, shipped = { routing.featureByKey[it] != null }))
    }
    ReportWriter.write(conversion, routing, report, skippedProblems = skip, expansion = expansion)
    val base = { k: String -> expansion.baseKey(k) } // counts in ARB keys
    for (l in conversion.locales) {
        out.println(
            "${l.locale}: in ${l.keysInBase(base)}, converted ${l.convertedKeys(false).map(base).toSet().size}, " +
                "unused ${l.files[LocaleResult.UNUSED].orEmpty().map { base(it.key) }.toSet().size}, " +
                "not convertible ${l.skipped.size}, orphans ${l.orphans.size}, missing ${l.missing.map(base).toSet().size}, " +
                "android strings ${conversion.nativeOs[l.locale].orEmpty().size}",
        )
    }
    return 0
}
