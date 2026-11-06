package io.github.eslamasabry.oc.engine.agents.scripts

import java.io.File

/** The repository root (the Flutter reference app lives beside `native/`). */
internal val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
    .first { File(it, "lib/domain/agent_catalog.dart").isFile }

/**
 * Every `static const NAME = r'''…''';` (or top-level `const NAME = r'''…''';`) in a Dart file, with Dart's rule that a
 * multi-line string drops its first line when that line is empty.
 */
internal fun dartRawStrings(path: String): Map<String, String> = dartRawStringsIn(File(repoRoot, path).readText())

/**
 * The released Flutter app (2.1.4+62, tag v2.1.4+62), the source of truth the ports follow. This branch's own lib/ is
 * an older snapshot, so a port of a 2.1.4 change (9c1e42325: the curl/tar-crash prelude) is checked against the
 * release, read from git.
 */
internal const val FLUTTER_RELEASE: String = "2dec68a250fec08909593fd3b1a8125de908f0c2"

/** [path] as the released Flutter app has it; fails plainly when the release commit is not in this clone. */
internal fun releasedSource(path: String): String {
    val process = ProcessBuilder("git", "-C", repoRoot.path, "show", "$FLUTTER_RELEASE:$path").redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "Flutter release $FLUTTER_RELEASE is not in this clone: git fetch origin master ($text)" }
    return text
}

/** [dartRawStrings] from the released Flutter app. */
internal fun releasedDartRawStrings(path: String): Map<String, String> = dartRawStringsIn(releasedSource(path))

private fun dartRawStringsIn(text: String): Map<String, String> {
    return Regex("""const (\w+) = r'''(.*?)''';""", RegexOption.DOT_MATCHES_ALL).findAll(text).associate {
        val body = it.groupValues[2]
        it.groupValues[1] to (if (body.startsWith("\n")) body.substring(1) else body)
    }
}
