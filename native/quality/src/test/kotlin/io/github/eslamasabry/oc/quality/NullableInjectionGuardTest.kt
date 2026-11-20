package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class NullableInjectionGuardTest {
    @Test
    fun `B-QUAL-001 nullable injection has an explicit binding or a deferred row`() {
        val root = File(System.getProperty("oc.root"))
        val modules = File(root, "MODULE_OWNERS").readLines().filter { it.startsWith(':') }
            .map { File(root, it.substringBefore('\t').drop(1).replace(':', '/')) }
        val baseline = checkNotNull(javaClass.getResource("/nullable-injection-baseline.txt")).readText().lines()
            .filter(String::isNotBlank).toSet()
        val problems = NullableInjectionRule.problems(modules, baseline)
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
