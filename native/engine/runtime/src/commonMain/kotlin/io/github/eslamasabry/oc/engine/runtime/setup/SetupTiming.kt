package io.github.eslamasabry.oc.engine.runtime.setup

/** Timings measured by the runner or setup checks, recorded outside the visible job log. */
public data class SetupTiming(
    val name: String,
    val durationMillis: Long,
    val attributes: Map<String, String>,
    val failed: Boolean,
)
