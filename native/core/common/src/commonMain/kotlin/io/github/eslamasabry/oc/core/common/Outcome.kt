package io.github.eslamasabry.oc.core.common

import kotlin.coroutines.cancellation.CancellationException

/** The result of every engine command and gateway call (ADR §9.1, plan 03 §10.1). */
public sealed interface Outcome<out T> {
    public data class Ok<out T>(val value: T) : Outcome<T>

    public data class Failed(val failure: Failure) : Outcome<Nothing>
}

public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(transform(value))
        is Outcome.Failed -> this
    }

public inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> =
    when (this) {
        is Outcome.Ok -> transform(value)
        is Outcome.Failed -> this
    }

public fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Ok)?.value

public fun Outcome<*>.failureOrNull(): Failure? = (this as? Outcome.Failed)?.failure

/**
 * The only sanctioned way to call code that may throw (Konsist K3 bans runCatching around suspend calls).
 * Cancellation is rethrown, never turned into a Failure. [classify] is the boundary's translator.
 */
public suspend inline fun <T> attempt(classify: (Throwable) -> Failure, block: () -> T): Outcome<T> =
    try {
        Outcome.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Outcome.Failed(classify(e))
    }
