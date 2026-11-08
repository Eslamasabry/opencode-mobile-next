package io.github.eslamasabry.oc.feature.files.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem

/**
 * Why a file would not open, in the file's own terms (B-184, twin of Flutter's file categories in
 * `lib/domain/product_failure.dart`). Each one used to read as "couldn't reach your computer". Chosen from the closed
 * failure only; the server's own text stays under Details.
 */
public enum class FileReadReason {
    /** Over the size the viewer reads. */
    TooBig,

    /** Moved or deleted. */
    Missing,

    /** The server or this phone would not let the app read it. */
    Denied,

    /** A kind the viewer cannot show (binary). */
    NotShowable;

    /** A second try cannot change the answer: the way out is another app, or another file. */
    public val retryHelps: Boolean get() = this == Denied

    public companion object {
        public fun of(failure: Failure?): FileReadReason? =
            when (failure) {
                is Failure.Media ->
                    when (failure.problem) {
                        MediaProblem.FileTooBig -> TooBig
                        MediaProblem.FileMissing -> Missing
                        MediaProblem.FileDenied -> Denied
                        MediaProblem.FileNotShowable -> NotShowable
                        else -> null
                    }
                is Failure.NotFound -> Missing
                is Failure.Rejected ->
                    when (failure.status) {
                        413 -> TooBig
                        403 -> Denied
                        404, 410 -> Missing
                        else -> null
                    }
                else -> null
            }
    }
}
