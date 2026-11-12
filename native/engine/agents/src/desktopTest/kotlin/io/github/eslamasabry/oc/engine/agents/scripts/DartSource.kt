package io.github.eslamasabry.oc.engine.agents.scripts

import java.io.File

/** The repository root (the Flutter reference app lives beside `native/`). */
internal val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
    .first { File(it, "lib/domain/agent_catalog.dart").isFile }

/**
 * Every `static const NAME = r'''…''';` (or top-level `const NAME = r'''…''';`) in a Dart file, with Dart's rule that a
 * multi-line string drops its first line when that line is empty.
 */
internal fun dartRawStrings(path: String): Map<String, String> = rawStrings(File(repoRoot, path).readText())

/**
 * The released Flutter app's copy of [path] (2.1.4+62, `resources/flutter-2.1.4`). Use it where the engine follows a
 * released change that this repository's older `lib/` snapshot does not have yet (#117: setup prelude, Paseo Node
 * unpack); see that folder's README.
 */
internal fun releasedDartSource(path: String): String =
    checkNotNull(object {}.javaClass.getResource("/flutter-2.1.4/$path.txt")) { "missing released twin $path" }.readText()

/**
 * The Flutter freeze tip's copy of [path] (`resources/flutter-freeze-tip`, the native candidate's Flutter tree before
 * master was merged in): the source the agent pins were ported from, which `lib/` and `test/` no longer hold. See
 * that folder's README.
 */
internal fun freezeTipSource(path: String): String =
    checkNotNull(object {}.javaClass.getResource("/flutter-freeze-tip/$path.txt")) { "missing freeze-tip twin $path" }.readText()

/** [dartRawStrings] over the released Flutter copy of [path]. */
internal fun releasedDartRawStrings(path: String): Map<String, String> = rawStrings(releasedDartSource(path))

private fun rawStrings(text: String): Map<String, String> {
    return Regex("""const (\w+) = r'''(.*?)''';""", RegexOption.DOT_MATCHES_ALL).findAll(text).associate {
        val body = it.groupValues[2]
        it.groupValues[1] to (if (body.startsWith("\n")) body.substring(1) else body)
    }
}
