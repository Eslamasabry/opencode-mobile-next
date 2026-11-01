package io.github.eslamasabry.oc.android.platform

// Field recorder, part 3 (plan 13 §3.6, WP-13.4; unit S2-FIELD): whether the speed-up (baseline) profile is compiled,
// shown as "Speed-up: ready / waits for the phone to rest". The reading comes from profileinstaller's
// `ProfileVerifier.getCompilationStatusAsync()` (1.4.1). This module cannot add that library (S2-FIELD writes new files
// only), so the reading enters through [ProfileReadingSource]; the binding that calls ProfileVerifier lives with the
// module that has profileinstaller on its classpath (catalog request in the S2-FIELD report). The mapping is here.

/** What the Performance row says about the speed-up profile. */
public enum class SpeedUpProfile {
    /** The app runs compiled with its profile. */
    Ready,

    /** The profile is installed and waits for the phone to rest (idle maintenance compiles it). */
    WaitsForRest,

    /** No profile is installed yet (the installer runs a few seconds after the first launch of a new version). */
    NotInstalled,

    /** The phone's Android cannot report it (profileinstaller's unsupported-API result). */
    Unsupported,

    /** The verifier failed or has not answered. */
    Unknown,
}

/** The three fields of profileinstaller's `ProfileVerifier.CompilationStatus`, without the library type. */
public data class ProfileReading(
    val isCompiledWithProfile: Boolean,
    val hasProfileEnqueuedForCompilation: Boolean,
    val resultCode: Int,
)

/** Calls `ProfileVerifier.getCompilationStatusAsync()` and returns its status; null when it failed. Never throws. */
public fun interface ProfileReadingSource {
    public suspend fun read(): ProfileReading?
}

public object ProfileStatus {
    // ProfileVerifier.CompilationStatus result codes (profileinstaller 1.4.1). Error codes are shifted left by 16.
    public const val RESULT_CODE_NO_PROFILE: Int = 0
    public const val RESULT_CODE_COMPILED_WITH_PROFILE: Int = 1
    public const val RESULT_CODE_PROFILE_ENQUEUED_FOR_COMPILATION: Int = 2
    public const val RESULT_CODE_COMPILED_WITH_PROFILE_NON_MATCHING: Int = 3
    public const val RESULT_CODE_ERROR_UNSUPPORTED_API_VERSION: Int = 4 shl 16
    private const val ERROR_SHIFT_FLOOR: Int = 1 shl 16

    /** The row for one reading; a null reading (failure, no answer) is [SpeedUpProfile.Unknown]. */
    public fun of(reading: ProfileReading?): SpeedUpProfile = when {
        reading == null -> SpeedUpProfile.Unknown
        reading.isCompiledWithProfile -> SpeedUpProfile.Ready
        reading.resultCode == RESULT_CODE_ERROR_UNSUPPORTED_API_VERSION -> SpeedUpProfile.Unsupported
        reading.resultCode >= ERROR_SHIFT_FLOOR -> SpeedUpProfile.Unknown
        reading.hasProfileEnqueuedForCompilation -> SpeedUpProfile.WaitsForRest
        reading.resultCode == RESULT_CODE_PROFILE_ENQUEUED_FOR_COMPILATION -> SpeedUpProfile.WaitsForRest
        reading.resultCode == RESULT_CODE_NO_PROFILE -> SpeedUpProfile.NotInstalled
        else -> SpeedUpProfile.Unknown
    }

    /** Reads once, on demand (when the rows are shown); a source that throws reads as [SpeedUpProfile.Unknown]. */
    public suspend fun read(source: ProfileReadingSource): SpeedUpProfile {
        val reading = try {
            source.read()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return of(reading)
    }
}
