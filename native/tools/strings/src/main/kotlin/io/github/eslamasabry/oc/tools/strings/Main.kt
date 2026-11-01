package io.github.eslamasabry.oc.tools.strings

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.system.exitProcess

private const val USAGE = """usage: arb2xml convert --arb <lib/l10n> --dart-lib <lib> --routes <feature-routes.tsv>
                       --out <composeResources dir> --report <dir>
                       [--unused-out <dir>] [--template en] [--skip-unsupported]

Converts every app_<locale>.arb to CMP resource XML: values[-<lang>]/strings_<feature>.xml.
Exit codes: 0 converted; 1 messages it cannot convert (listed; nothing written unless --skip-unsupported);
2 bad input (usage, unrouted Dart files, malformed ARB)."""

fun main(args: Array<String>) {
    exitProcess(run(args.toList(), System.out, System.err))
}

fun run(args: List<String>, out: java.io.PrintStream, err: java.io.PrintStream): Int {
    if (args.firstOrNull() != "convert") { err.println(USAGE); return 2 }
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
    val required = listOf("--arb", "--dart-lib", "--routes", "--out", "--report")
    val absent = required.filter { it !in opts }
    if (absent.isNotEmpty()) { err.println("missing ${absent.joinToString()}\n$USAGE"); return 2 }
    val template = opts["--template"] ?: "en"
    val skip = "--skip-unsupported" in flags

    return try {
        val arbs = Arb.readDir(Path(opts.getValue("--arb")), template)
        val routes = Routes.read(Path(opts.getValue("--routes")))
        val routing = Router.route(arbs.first().messages.keys, Path(opts.getValue("--dart-lib")), routes)
        val conversion = Converter({ routing.featureByKey[it] }, template).convert(arbs)
        val report: Path = Path(opts.getValue("--report"))

        if (conversion.problems.isNotEmpty()) {
            err.println("arb2xml: ${conversion.problems.size} messages cannot be converted to CMP XML:")
            for ((kind, ps) in conversion.problems.groupBy { it.kind }) {
                err.println("  ${kind.name}: ${kind.label}")
                for ((key, kps) in ps.groupBy { it.key }) {
                    err.println("    $key [${kps.joinToString(",") { it.locale }}] ${kps.first().detail.take(120)}")
                }
            }
            if (!skip) {
                err.println("Nothing written. Rewrite these as code (see the report), or rerun with --skip-unsupported to convert the rest.")
                ReportWriter.write(conversion, routing, report, skippedProblems = false)
                return 1
            }
        }
        XmlWriter.write(conversion, Path(opts.getValue("--out")), opts["--unused-out"]?.let(::Path))
        ReportWriter.write(conversion, routing, report, skippedProblems = skip)
        for (l in conversion.locales) {
            out.println(
                "${l.locale}: in ${l.keysIn}, converted ${l.convertedKeys(false).size}, " +
                    "unused ${l.files[LocaleResult.UNUSED].orEmpty().map { it.key }.toSet().size}, " +
                    "not convertible ${l.skipped.size}, orphans ${l.orphans.size}, missing ${l.missing.size}",
            )
        }
        0
    } catch (e: UnroutedPathsException) {
        err.println("arb2xml: ${e.message}"); 2
    } catch (e: ArbFormatException) {
        err.println("arb2xml: ${e.message}"); 2
    } catch (e: IllegalArgumentException) {
        err.println("arb2xml: ${e.message}"); 2
    }
}
