package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.*
import org.junit.Test

class NativeInstallerVisibilityTest {
    private fun identity(pid: Int, ticks: Long = 10) = RuntimeProcessIdentity(pid, ticks, 1, pid, pid)
    private fun refuse(action: () -> Unit) {
        val error = assertThrows(IllegalArgumentException::class.java) { action() }
        assertEquals("ownershipUnknown", error.message)
        assertNull(error.cause)
    }

    @Test fun visibleIdentitiesDoNotProbeAbsence() {
        val root = identity(20)
        val reparented = identity(21).copy(parent = 99)
        NativeInstallerVisibility.requireMissingGone(listOf(root, identity(21)), listOf(root, reparented)) {
            fail("Visible identities must not trigger absence probes")
            false
        }
    }

    @Test fun onlyMissingExactPidsReceiveIndependentConfirmation() {
        val probes = mutableListOf<Int>()
        NativeInstallerVisibility.requireMissingGone(listOf(identity(20), identity(21), identity(22)),
            listOf(identity(21))) { pid -> probes.add(pid); true }
        assertEquals(listOf(20, 22), probes)
    }

    @Test fun aliveOrUnreadableMissingProcessRefuses() {
        // Both a live process and unavailable kernel proof yield false.
        var calls = 0
        refuse {
            NativeInstallerVisibility.requireMissingGone(listOf(identity(20)), emptyList()) {
                calls++
                false
            }
        }
        assertEquals(1, calls)
    }

    @Test fun callbackExceptionIsReplacedWithFixedTextAndNoCause() {
        refuse {
            NativeInstallerVisibility.requireMissingGone(listOf(identity(20)), emptyList()) {
                throw IllegalStateException("private-callback-detail")
            }
        }
    }

    @Test fun callbackErrorAlsoRefusesWithoutExposingItsText() {
        refuse {
            NativeInstallerVisibility.requireMissingGone(listOf(identity(20)), emptyList()) {
                throw AssertionError("private-callback-detail")
            }
        }
    }

    @Test fun reusedPidRefusesBeforeAnyOtherMissingIdentityIsProbed() {
        var probed = false
        refuse {
            NativeInstallerVisibility.requireMissingGone(listOf(identity(20), identity(21)),
                listOf(identity(21, ticks = 11))) { probed = true; true }
        }
        assertFalse(probed)
    }

    @Test fun contradictoryKnownStartTicksRefuseBeforeProbing() {
        var probed = false
        refuse {
            NativeInstallerVisibility.requireMissingGone(listOf(identity(20), identity(20, ticks = 11)),
                emptyList()) { probed = true; true }
        }
        assertFalse(probed)
    }

    @Test fun identicalKnownDuplicatesAreConfirmedOnce() {
        val root = identity(20)
        val probes = mutableListOf<Int>()
        NativeInstallerVisibility.requireMissingGone(listOf(root, root, root), emptyList()) {
            probes.add(it); true
        }
        assertEquals(listOf(20), probes)
        NativeInstallerVisibility.requireMissingGone(listOf(root, root), listOf(root)) {
            fail("Visible duplicate identities must not be probed")
            false
        }
    }

    @Test fun duplicateInventoryPidsRefuseEvenWithIdenticalEntries() {
        var probed = false
        val root = identity(20)
        for (duplicate in listOf(root, identity(20, ticks = 11))) {
            refuse {
                NativeInstallerVisibility.requireMissingGone(listOf(identity(21)), listOf(root, duplicate)) {
                    probed = true; true
                }
            }
        }
        assertFalse(probed)
    }

    @Test fun knownBoundAcceptsRootLeaderAnd128Observations() {
        val known = List(130) { identity(it + 20) }
        val visible = known.take(128)
        val probes = mutableListOf<Int>()
        NativeInstallerVisibility.requireMissingGone(known, visible) { probes.add(it); true }
        assertEquals(listOf(148, 149), probes)
    }

    @Test fun oversizedKnownOrInventoryRefusesBeforeProbing() {
        var probed = false
        refuse {
            NativeInstallerVisibility.requireMissingGone(List(131) { identity(it + 20) }, emptyList()) {
                probed = true; true
            }
        }
        refuse {
            NativeInstallerVisibility.requireMissingGone(listOf(identity(200)), List(129) { identity(it + 20) }) {
                probed = true; true
            }
        }
        assertFalse(probed)
    }

    @Test fun firstUnconfirmedAbsenceStopsFurtherProbes() {
        val probes = mutableListOf<Int>()
        refuse {
            NativeInstallerVisibility.requireMissingGone(listOf(identity(20), identity(21)), emptyList()) {
                probes.add(it); false
            }
        }
        assertEquals(listOf(20), probes)
    }

    @Test fun emptyKnownListNeedsNoAbsenceProof() {
        NativeInstallerVisibility.requireMissingGone(emptyList(), listOf(identity(20))) {
            fail("No known identities need an absence proof")
            false
        }
    }
}
