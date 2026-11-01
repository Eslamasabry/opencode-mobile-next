package io.github.eslamasabry.oc.android.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** S2-FIELD speed-up profile row (plan 13 §3.6, WP-13.4). No catalog row covers it; names carry the work package. */
class ProfileStatusTest {
    private fun r(compiled: Boolean = false, enqueued: Boolean = false, code: Int = 0) = ProfileReading(compiled, enqueued, code)

    @Test
    fun `WP-13_4 FIELD compiled with the profile reads as ready`() {
        assertEquals(SpeedUpProfile.Ready, ProfileStatus.of(r(compiled = true, code = ProfileStatus.RESULT_CODE_COMPILED_WITH_PROFILE)))
        assertEquals(SpeedUpProfile.Ready, ProfileStatus.of(r(compiled = true, code = ProfileStatus.RESULT_CODE_COMPILED_WITH_PROFILE_NON_MATCHING)))
    }

    @Test
    fun `WP-13_4 FIELD an installed profile not yet compiled waits for the phone to rest`() {
        assertEquals(SpeedUpProfile.WaitsForRest, ProfileStatus.of(r(enqueued = true, code = ProfileStatus.RESULT_CODE_PROFILE_ENQUEUED_FOR_COMPILATION)))
        assertEquals(SpeedUpProfile.WaitsForRest, ProfileStatus.of(r(code = ProfileStatus.RESULT_CODE_PROFILE_ENQUEUED_FOR_COMPILATION)))
    }

    @Test
    fun `WP-13_4 FIELD no profile, an old Android and verifier errors are told apart`() {
        assertEquals(SpeedUpProfile.NotInstalled, ProfileStatus.of(r(code = ProfileStatus.RESULT_CODE_NO_PROFILE)))
        assertEquals(SpeedUpProfile.Unsupported, ProfileStatus.of(r(code = ProfileStatus.RESULT_CODE_ERROR_UNSUPPORTED_API_VERSION)))
        assertEquals(SpeedUpProfile.Unknown, ProfileStatus.of(r(code = 1 shl 16)))
        assertEquals(SpeedUpProfile.Unknown, ProfileStatus.of(r(enqueued = true, code = 3 shl 16)), "an error wins over a stale flag")
        assertEquals(SpeedUpProfile.Unknown, ProfileStatus.of(r(code = 99)))
        assertEquals(SpeedUpProfile.Unknown, ProfileStatus.of(null))
    }

    @Test
    fun `WP-13_4 FIELD a failing verifier reads as unknown and never throws`() = runTest {
        assertEquals(SpeedUpProfile.Unknown, ProfileStatus.read { error("verifier broke") })
        assertEquals(SpeedUpProfile.Unknown, ProfileStatus.read { null })
        assertEquals(SpeedUpProfile.Ready, ProfileStatus.read { r(compiled = true, code = 1) })
    }
}
