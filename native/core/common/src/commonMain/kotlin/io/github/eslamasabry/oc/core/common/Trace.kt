package io.github.eslamasabry.oc.core.common

/** Trace sections for Perfetto on Android; no-ops elsewhere (plan 03 §18.5). */
public interface Trace {
    public fun <T> section(name: String, block: () -> T): T
}

public expect fun platformTrace(): Trace

internal object NoTrace : Trace {
    override fun <T> section(name: String, block: () -> T): T = block()
}
