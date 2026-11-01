package io.github.eslamasabry.oc.android.voice.speech

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking

/**
 * The Kokoro streaming callback (plan 10 §8.5). A NAMED class, never a lambda: sherpa's JNI looks up
 * `invoke([F)Ljava/lang/Integer;` by name, which an `invokedynamic` lambda does not have, and R8 may rename a lambda's
 * method. Kept by `:androidApp`'s `sherpa.keep` rule
 * (`-keepclassmembers class io.github.eslamasabry.oc.android.voice.speech.KokoroSink { java.lang.Integer invoke(float[]); }`).
 * Returns 1 to continue, 0 to stop synthesis (the job was cancelled or the reader is gone).
 */
public class KokoroSink(private val job: Job, private val chunks: SendChannel<FloatArray>) : (FloatArray) -> Int {
    override fun invoke(p1: FloatArray): Int = if (job.isActive && chunks.trySendBlocking(p1).isSuccess) 1 else 0
}
