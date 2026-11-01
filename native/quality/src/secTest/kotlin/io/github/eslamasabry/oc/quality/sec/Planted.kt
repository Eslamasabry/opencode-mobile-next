package io.github.eslamasabry.oc.quality.sec

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A throwaway tree laid out like `native/` (P9 style): plant files, then run the same rule the live tree runs. */
class Planted {
    val root: File = createTempDirectory("k-sec-planted").toFile().also { it.deleteOnExit() }

    fun file(rel: String, text: String): Planted = apply { File(root, rel).apply { parentFile.mkdirs() }.writeText(text.trimIndent() + "\n") }

    fun files(): List<SecFile> = SecTree.load(root)

    fun declarations(): SecDeclarationRules = SecDeclarationRules(root, files())
}

/** The live tree and its configuration. */
object Live {
    val root: File = File(System.getProperty("oc.root") ?: error("oc.root is not set; run through Gradle"))
    val repo: File = root.parentFile
    val files: List<SecFile> by lazy { SecTree.load(root).also { assertTrue(it.size > 50, "the scan saw ${it.size} files; the root is wrong") } }
    val config: SecConfig by lazy { SecConfig.of(root) }
    val declarations: SecDeclarationRules by lazy { SecDeclarationRules(root, files) }
}

/** The real config, for planted runs (the allowlists are part of what is proven). */
val liveConfig: SecConfig get() = Live.config

fun assertClean(violations: List<Any>, what: String) = assertTrue(violations.isEmpty(), "$what:\n" + violations.joinToString("\n"))

fun assertCaught(violations: List<Violation>, rule: String, count: Int, what: String) {
    assertTrue(violations.all { it.rule == rule }, "$what: wrong rule ids in $violations")
    assertEquals(count, violations.size, "$what: expected $count hits, got\n" + violations.joinToString("\n"))
}
