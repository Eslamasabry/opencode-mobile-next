package oc.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The release naming rule: the public build carries the plain name, every other build `<name>-test.<build>`. */
class ReleaseVersionNameTest {
    @Test
    fun `a build that is not the public release carries -test and its build number`() {
        assertEquals("3.0.1-test.10081", ReleaseVersionName.forBuild("3.0.1", 10081, publicBuild = false))
    }

    @Test
    fun `the public release carries the plain name`() {
        assertEquals("3.0.1", ReleaseVersionName.forBuild("3.0.1", 10080, publicBuild = true))
    }

    @Test
    fun `a name that already has a suffix is left alone`() {
        assertEquals("3.0.0-dev", ReleaseVersionName.forBuild("3.0.0-dev", 10003, publicBuild = false))
    }

    @Test
    fun `a public build with -test in its name is refused`() {
        val problems = ReleaseVersionName.problems(listOf("3.0.1-test.10081"), 10081, publicBuild = true)
        assertEquals(1, problems.size, problems.toString())
        assertTrue("never carries -test.N" in problems.single(), problems.toString())
    }

    @Test
    fun `a public build with the plain name passes`() {
        assertEquals(emptyList(), ReleaseVersionName.problems(listOf("3.0.1", "3.0.1"), 10080, publicBuild = true))
    }

    @Test
    fun `a build that is not public and has -test and its own build number passes`() {
        assertEquals(emptyList(), ReleaseVersionName.problems(listOf("3.0.1-test.10081"), 10081, publicBuild = false))
    }

    @Test
    fun `a build that is not public refuses the plain name, another build number and mixed names`() {
        assertEquals(1, ReleaseVersionName.problems(listOf("3.0.1"), 10081, publicBuild = false).size)
        assertEquals(1, ReleaseVersionName.problems(listOf("3.0.1-test.10070"), 10081, publicBuild = false).size)
        assertEquals(1, ReleaseVersionName.problems(listOf("3.0.1-test.10081", "3.0.1"), 10081, publicBuild = false).size)
    }

    @Test
    fun `a public build below 3_0_0 is refused`() {
        assertEquals(1, ReleaseVersionName.problems(listOf("2.9.9"), 10080, publicBuild = true).size)
    }
}
