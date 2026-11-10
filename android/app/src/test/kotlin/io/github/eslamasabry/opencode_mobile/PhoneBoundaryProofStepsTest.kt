package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #111: a failed boundary proof names its step, and only static codes. */
class PhoneBoundaryProofStepsTest {
    @Test
    fun androidSigsysBeforeAnyReportIsNamedAsASignal() {
        // The pre-fix probe on Android 16: SIGSYS (31) from the app seccomp
        // policy at io_uring_setup, nothing printed; Java reports 128+31.
        assertEquals("native.signal-31", PhoneBoundaryProofSteps.native(emptyList(), emptyList(), 159))
        assertEquals("boundary_proof_failed:native.signal-31",
            PhoneBoundaryProofSteps.code(PhoneBoundaryProofSteps.native(emptyList(), emptyList(), 159)))
    }

    @Test
    fun firstFailingNativeControlWinsWithItsShape() {
        val out = listOf(
            "boundary-control:io-uring:signal:11",
            "boundary-control:chmod:result:-1:errno:38",
            "native-boundary-probes-failed",
        )
        assertEquals("native.io-uring.signal-11", PhoneBoundaryProofSteps.native(out, emptyList(), 1))
        assertEquals("native.chmod.errno-38", PhoneBoundaryProofSteps.native(out.drop(1), emptyList(), 1))
        assertEquals("native.open-0-0.allowed",
            PhoneBoundaryProofSteps.native(listOf("boundary-control:open-0-0:result:3:errno:0"), emptyList(), 1))
        assertEquals("native.worker-write.errno-13",
            PhoneBoundaryProofSteps.native(listOf("boundary-control:worker-write:errno:13"), emptyList(), 1))
        assertEquals("native.descendant.signal-31", PhoneBoundaryProofSteps.native(
            listOf("boundary-control:descendant:signal:31", "boundary-control:descendant:failed"), emptyList(), 1))
        assertEquals("native.descendant",
            PhoneBoundaryProofSteps.native(listOf("boundary-control:descendant:failed"), emptyList(), 1))
    }

    @Test
    fun sandboxRefusalKeepsOnlyItsStaticCode() {
        assertEquals("native.sandbox.landlock_rule_failed",
            PhoneBoundaryProofSteps.native(emptyList(), listOf("landlock_rule_failed"), 78))
        // Anything that is not a bare static code is dropped, never echoed.
        assertEquals("native.sandbox",
            PhoneBoundaryProofSteps.native(emptyList(), listOf("/data/user/0/secret path"), 78))
        assertEquals("native.no-answer", PhoneBoundaryProofSteps.native(emptyList(), emptyList(), null))
        assertNull(PhoneBoundaryProofSteps.native(listOf("native-boundary-probes-passed"), emptyList(), 0))
    }

    @Test
    fun pathsOrOddTextInControlLinesAreNeverEchoed() {
        val step = PhoneBoundaryProofSteps.native(
            listOf("boundary-control:/data/data/x/files:result:0", "unrelated /data/data/x"), emptyList(), 1)
        assertEquals("native.exit-1", step)
    }

    @Test
    fun ubuntuStepIsTheLastMarkerReached() {
        val out = listOf("proof-step:enter-worker", "proof-step:worker-write", "proof-step:git-init",
            "proof-step:git-commit")
        assertEquals("ubuntu.git-commit.exit-128", PhoneBoundaryProofSteps.ubuntu(out, 128))
        assertEquals("ubuntu.start.signal-9", PhoneBoundaryProofSteps.ubuntu(emptyList(), 137))
        assertEquals("ubuntu.git-init.no-answer", PhoneBoundaryProofSteps.ubuntu(out.take(3), null))
        assertNull(PhoneBoundaryProofSteps.ubuntu(out, 0))
    }

    @Test
    fun prootTierNamesTheFirstControlThatDidNotPass() {
        val labels = listOf("positiveWorkerClone", "daemonProcDenied", "fdHygiene")
        assertEquals("proot-tier.daemonProcDenied", PhoneBoundaryProofSteps.prootTier(labels,
            mapOf("positiveWorkerClone" to true, "daemonProcDenied" to false), true, 0))
        assertEquals("proot-tier.no-answer", PhoneBoundaryProofSteps.prootTier(labels,
            labels.associateWith { true }, false, null))
        assertNull(PhoneBoundaryProofSteps.prootTier(labels, labels.associateWith { true }, true, 0))
    }

    @Test
    fun codeRoundTripsAndRejectsUnsafeSteps() {
        assertEquals("boundary_proof_failed", PhoneBoundaryProofSteps.code(null))
        assertEquals("boundary_proof_failed", PhoneBoundaryProofSteps.code("has space"))
        assertEquals("ubuntu.git-commit.exit-1",
            PhoneBoundaryProofSteps.stepOf(PhoneBoundaryProofSteps.code("ubuntu.git-commit.exit-1")))
        assertNull(PhoneBoundaryProofSteps.stepOf("boundary_proof_failed"))
        assertNull(PhoneBoundaryProofSteps.stepOf("restart_required"))
    }
}
