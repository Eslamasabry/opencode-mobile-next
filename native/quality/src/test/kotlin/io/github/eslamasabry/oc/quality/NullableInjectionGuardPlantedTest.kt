package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class NullableInjectionGuardPlantedTest {
    @TempDir lateinit var module: File

    private val foo = "package types\nclass Foo"
    private val injected = "package use\nimport types.Foo\n@Inject class P(val s: Foo? = null)"
    private val provider = "package wiring\nimport types.Foo as F\n@Provides fun s(f: F): F? = f"

    private fun source(name: String, code: String, set: String = "commonMain") {
        val file = File(module, "src/$set/kotlin/$name.kt")
        file.parentFile.mkdirs()
        file.writeText(code)
    }

    private fun problems(vararg baseline: String) = NullableInjectionRule.problems(listOf(module), baseline.toSet())

    @Test
    fun `B-QUAL-002 a non-null binding leaves an injected nullable parameter unfilled`() {
        source("Foo", foo)
        source("P", injected)
        source("Wiring", "package wiring\nimport types.Foo\n@Provides fun s(): Foo = Foo()")
        source("WrongFoo", "package other\nclass Foo\n@Provides fun s(f: Foo): Foo? = f")
        source("WiringPreviews", provider)
        source("TestWiring", provider, "desktopTest")
        val failure = problems().single()
        assertTrue(failure.contains("use.P.s: `Foo?` is never filled by Metro"), failure)
    }

    @Test
    fun `B-QUAL-003 an explicit nullable provider fills the same imported type`() {
        source("Foo", foo)
        source("P", injected)
        source("Wiring", provider, "androidMain")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `B-QUAL-004 an assisted parameter may be nullable`() {
        source("Q", "package use\nimport types.Foo\n@AssistedInject class Q(@Assisted val s: Foo?)")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `B-QUAL-005 an ordinary constructor may accept null`() {
        source("R", "package use\nimport types.Foo\nclass R(val s: Foo? = null)")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `B-QUAL-006 nullable provider inputs also need a nullable binding`() {
        source("Bindings", "package wiring\nimport types.Foo\nobject Bindings { @Provides fun p(s: Foo?): Bar = Bar() }")
        assertTrue(problems().single().contains("wiring.Bindings.s: `Foo?`"))
    }

    @Test
    fun `B-QUAL-007 a baseline row must be removed after its nullable type is bound`() {
        source("P", injected)
        assertEquals(emptyList(), problems("use.P#s"))
        source("Binds", "package wiring\nimport types.Foo\ninterface Wiring { @Binds fun s(f: Foo): Foo? }")
        assertEquals(listOf("Remove stale nullable injection baseline row: use.P#s"), problems("use.P#s"))
    }
}
