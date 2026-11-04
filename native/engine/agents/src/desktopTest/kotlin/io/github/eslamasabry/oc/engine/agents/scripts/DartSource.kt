package io.github.eslamasabry.oc.engine.agents.scripts

import java.io.File

/** The repository root (the Flutter reference app lives beside `native/`). */
internal val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
    .first { File(it, "lib/domain/agent_catalog.dart").isFile }

/**
 * Every `static const NAME = r'''…''';` (or top-level `const NAME = r'''…''';`) in a Dart file, with Dart's rule that a
 * multi-line string drops its first line when that line is empty.
 */
internal fun dartRawStrings(path: String): Map<String, String> {
    val text = File(repoRoot, path).readText()
    return Regex("""const (\w+) = r'''(.*?)''';""", RegexOption.DOT_MATCHES_ALL).findAll(text).associate {
        val body = it.groupValues[2]
        it.groupValues[1] to (if (body.startsWith("\n")) body.substring(1) else body)
    }
}
