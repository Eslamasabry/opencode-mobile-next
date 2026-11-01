package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeProcessTreeTest {
    // The emulator case of build 2400: terminal proot 12489 runs bash 12493;
    // `(python3 -m http.server 8765 &)` left python 12603 with parent 1,
    // still traced by 12489.
    private val parents = mapOf(12489 to 12037, 12493 to 12489, 12603 to 1, 900 to 1, 12700 to 12603)

    @Test fun anOrphanTracedByTheRootIsBelowIt() {
        val tracers = mapOf(12493 to 12489, 12603 to 12489, 12700 to 12489)
        assertEquals(listOf(12493, 12603, 12700),
            NativeProcessTree.descendants(12489, parents, tracers).sorted())
    }

    @Test fun anOrphanTracedByAChildIsBelowTheRootToo() {
        val tracers = mapOf(12603 to 12493)
        assertEquals(listOf(12493, 12603, 12700),
            NativeProcessTree.descendants(12489, parents, tracers).sorted())
    }

    @Test fun anOrphanOfAnotherTracerOrNoneIsNotAdopted() {
        // 900 is a system daemon under init: no tracer, or a stranger's.
        assertEquals(listOf(12493), NativeProcessTree.descendants(12489, parents, emptyMap()))
        assertEquals(listOf(12493),
            NativeProcessTree.descendants(12489, parents, mapOf(900 to 4242, 12603 to 1)))
    }

    @Test fun theRootIsNeverItsOwnDescendant() {
        val cyclic = mapOf(5 to 6, 6 to 5)
        assertEquals(listOf(6), NativeProcessTree.descendants(5, cyclic, mapOf(5 to 6)))
    }

    @Test fun tracerIsReadFromStatus() {
        assertEquals(12489, NativeProcessTree.tracerOf("Name:\tpython3\nPPid:\t1\nTracerPid:\t12489\nUid:\t0\n"))
        assertNull(NativeProcessTree.tracerOf("Name:\tbash\nTracerPid:\t0\n"))
        assertNull(NativeProcessTree.tracerOf("Name:\tbash\n"))
        assertNull(NativeProcessTree.tracerOf("TracerPid:\tx\n"))
    }
}
