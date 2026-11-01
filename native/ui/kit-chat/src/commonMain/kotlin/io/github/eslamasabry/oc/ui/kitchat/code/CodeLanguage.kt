package io.github.eslamasabry.oc.ui.kitchat.code

/**
 * The grammars the chat colours (plan 09 §9.2). [grammar] is the SyntaxMP language id; [Plain] has none. The alias
 * table is Flutter's `_languageAliases` (`kit_code_block.dart:46`) whole; aliases with no SyntaxMP grammar map as the
 * plan says (scss → css, svg → xml, console → bash, patch → diff, gradle → kotlin). Nothing is guessed: an unknown
 * hint is [Plain].
 */
public enum class CodeLanguage(internal val grammar: String?) {
    Plain(null),
    Dart("dart"),
    JavaScript("javascript"),
    Jsx("jsx"),
    TypeScript("typescript"),
    Tsx("tsx"),
    Python("python"),
    Ruby("ruby"),
    Bash("bash"),
    Json("json"),
    Yaml("yaml"),
    Toml("toml"),
    Ini("ini"),
    Html("html"),
    Xml("xml"),
    Css("css"),
    Sql("sql"),
    Go("go"),
    Rust("rust"),
    Kotlin("kotlin"),
    Java("java"),
    Swift("swift"),
    C("c"),
    Cpp("cpp"),
    CSharp("csharp"),
    Php("php"),
    Diff("diff"),
    Dockerfile("dockerfile"),
    Makefile("makefile"),
    Protobuf("protobuf"),
    Markdown("markdown"),
    ;

    public companion object {
        private val aliases: Map<String, CodeLanguage> =
            mapOf(
                "dart" to Dart,
                "js" to JavaScript, "javascript" to JavaScript, "jsx" to Jsx,
                "ts" to TypeScript, "typescript" to TypeScript, "tsx" to Tsx,
                "py" to Python, "python" to Python,
                "rb" to Ruby, "ruby" to Ruby,
                "sh" to Bash, "bash" to Bash, "shell" to Bash, "zsh" to Bash, "console" to Bash,
                "json" to Json,
                "yaml" to Yaml, "yml" to Yaml,
                "toml" to Toml, "ini" to Ini,
                "html" to Html, "xml" to Xml, "svg" to Xml,
                "css" to Css, "scss" to Css,
                "sql" to Sql,
                "go" to Go,
                "rust" to Rust, "rs" to Rust,
                "kotlin" to Kotlin, "kt" to Kotlin, "kts" to Kotlin, "gradle" to Kotlin,
                "java" to Java,
                "swift" to Swift,
                "c" to C, "h" to C,
                "cc" to Cpp, "cpp" to Cpp, "c++" to Cpp,
                "cs" to CSharp, "csharp" to CSharp,
                "php" to Php,
                "diff" to Diff, "patch" to Diff,
                "dockerfile" to Dockerfile,
                "makefile" to Makefile,
                "proto" to Protobuf,
                "md" to Markdown, "markdown" to Markdown,
            )

        /** The language of a fence info string ("kotlin", "ts title=a.ts"): its first word, case-insensitive. */
        public fun of(info: String?): CodeLanguage {
            val hint = info?.trim()?.substringBefore(' ')?.substringBefore('{')?.lowercase().orEmpty()
            return aliases[hint] ?: Plain
        }

        /** The language of a file, from its extension (or its name, for `Dockerfile` and `Makefile`). */
        public fun forPath(path: String): CodeLanguage {
            val name = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
            val dot = name.lastIndexOf('.')
            val hint = if (dot > 0 && dot < name.length - 1) name.substring(dot + 1) else name
            return aliases[hint] ?: Plain
        }
    }
}
