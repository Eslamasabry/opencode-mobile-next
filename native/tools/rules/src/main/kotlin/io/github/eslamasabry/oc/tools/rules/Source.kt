package io.github.eslamasabry.oc.tools.rules

import io.gitlab.arturbosch.detekt.api.Entity
import java.io.File
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/** One Kotlin file as the rules see it: its path from the native root, its text and where the native root is. */
internal class Source(val rel: String, val text: String, val root: File?, private val ktFile: KtFile) {
    val psi: KtFile get() = ktFile

    val name: String = rel.substringAfterLast('/')
    private val slashed = "/$rel"

    /** The first folder of the path: core, protocol, engine, ui, feature, android, androidApp, desktopApp ... */
    val layer: String = rel.substringBefore('/')

    /** `core/security`, `ui/kit` ... (androidApp stays one segment). */
    val module: String = rel.split('/').take(2).joinToString("/")

    /** The folder after `/src/` (`commonMain`, `desktopTest` ...), or "". */
    val sourceSet: String = rel.substringAfter("/src/", "").substringBefore('/')

    fun has(part: String): Boolean = part in slashed

    /** The production source sets the Konsist rules (K1..K19) and `BuildRestGates.production` scan. */
    val isProductionSet: Boolean get() = PRODUCTION_SETS.any { it in slashed }

    /** Every production source set including iOS (the K-rules' `production`). */
    val isKonsistProduction: Boolean get() = isProductionSet || "/src/iosMain/" in slashed

    /** A product module's non-test, non-qa source set (K-SEC "production"). */
    val isSecProduction: Boolean
        get() = isSecProductModule && "/src/" in slashed && !isTestSource && !isQaSource

    val isSecProductModule: Boolean
        get() = layer in SEC_LAYERS || layer == "androidApp" || layer == "desktopApp"

    val isTestSource: Boolean get() = sourceSet.contains("test", ignoreCase = true) || sourceSet == "benchmark"
    val isQaSource: Boolean get() = sourceSet == "qa" || sourceSet.startsWith("qa") || sourceSet.endsWith("Qa")

    fun packageName(): String = ktFile.packageFqName.asString()

    fun imports(): List<String> = ktFile.importDirectives.mapNotNull { it.importPath?.pathStr }

    /** The text with every comment turned into blanks (line breaks kept, so offsets and lines stay true). */
    val code: String by lazy {
        val chars = text.toCharArray()
        ktFile.collectDescendantsOfType<PsiComment>().forEach { c ->
            for (i in c.textRange.startOffset until c.textRange.endOffset.coerceAtMost(chars.size)) {
                if (chars[i] != '\n') chars[i] = ' '
            }
        }
        String(chars)
    }

    /** 1-based line of a text offset. */
    fun lineOf(offset: Int): Int = text.substring(0, offset.coerceIn(0, text.length)).count { it == '\n' } + 1

    fun offsetOfLine(line: Int): Int {
        var offset = 0
        var current = 1
        while (current < line) {
            val next = text.indexOf('\n', offset)
            if (next < 0) return text.length
            offset = next + 1
            current++
        }
        return offset
    }

    companion object {
        private val PRODUCTION_SETS = listOf("/src/commonMain/", "/src/androidMain/", "/src/desktopMain/", "/src/main/", "/src/jvmShared/")
        private val SEC_LAYERS = setOf("core", "protocol", "engine", "ui", "feature", "android")

        @Volatile private var cachedRoot: File? = null

        /** The native root (the folder with settings.gradle.kts and MODULE_OWNERS) above [absolute], or null. */
        fun nativeRoot(absolute: String): File? {
            cachedRoot?.let { if (absolute.startsWith(it.path.replace('\\', '/') + "/")) return it }
            var dir: File? = File(absolute).parentFile
            while (dir != null) {
                if (File(dir, "settings.gradle.kts").isFile && File(dir, "MODULE_OWNERS").isFile) {
                    cachedRoot = dir
                    return dir
                }
                dir = dir.parentFile
            }
            return null
        }

        fun of(file: KtFile): Source? {
            val absolute = Entity.from(file).location.filePath.absolutePath.toString().replace('\\', '/')
            if ("/build/" in absolute) return null
            val root = nativeRoot(absolute) ?: return null
            val rel = absolute.removePrefix(root.path.replace('\\', '/') + "/")
            return Source(rel, file.text, root, file)
        }
    }
}

/** A line of code as the old guard tests read it: the part before a `//`, trimmed. [null] for blank and comment lines. */
internal fun codeLine(raw: String, marker: String = "//"): String? {
    val line = raw.substringBefore(marker).trim()
    return if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*")) null else line
}

/** The rule baselines of `quality/config/rule-baseline/<rule>.txt`: files that broke the rule when the list was written. */
internal fun ruleBaseline(root: File?, rule: String): Set<String> =
    File(root ?: return emptySet(), "quality/config/rule-baseline/$rule.txt").takeIf { it.isFile }
        ?.readLines().orEmpty().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim() }.toSet()
