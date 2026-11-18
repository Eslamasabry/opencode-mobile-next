package io.github.eslamasabry.oc.tools.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile

/**
 * Base of every OC rule: one Kotlin file in, findings out. The rules decide by the file's path (the layer and source
 * set), not by detekt's include/exclude, so the old guard tests' scopes carry over exactly. A finding says what is
 * wrong and the way forward in plain words.
 */
internal abstract class OcRule(config: Config, id: String, description: String) : Rule(config) {
    final override val issue = Issue(id, Severity.Defect, description, Debt.FIVE_MINS)
    private var current: KtFile? = null

    final override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        val src = Source.of(file) ?: return
        current = file
        check(src)
        current = null
    }

    abstract fun check(src: Source)

    /** Reports at a 1-based [line] of the file being checked. */
    protected fun flag(src: Source, line: Int, message: String) {
        val file = current ?: return
        report(CodeSmell(issue, Entity.from(file, src.offsetOfLine(line)), message))
    }

    /** Reports at the start of a file (for rules about the whole file or its imports). */
    protected fun flagFile(message: String) {
        val file = current ?: return
        report(CodeSmell(issue, Entity.from(file), message))
    }
}
