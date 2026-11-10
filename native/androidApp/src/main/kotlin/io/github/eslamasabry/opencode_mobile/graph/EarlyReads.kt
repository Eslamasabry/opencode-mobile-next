package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.data.kv.EarlyReadPolicy
import java.util.concurrent.atomic.AtomicInteger

/**
 * What a read of the saved settings does before the migration gate has opened (plan 05 §2.2).
 *
 * Test and debug builds throw, so the early reader is found and fixed. A release must never do that: it counts the
 * read and lets it answer. The app was built with the throwing rule in every build, so on the first open after the
 * update from 2.x a home-screen widget's refresh, which reads its saved rows as soon as Android asks, ended the whole
 * app (owner's phone, 3.0.0+10020: GateNotOpen from UsageWidgetProvider.onUpdate, six seconds after the update).
 */
internal object EarlyReads {
    private val seen = AtomicInteger()

    /** How many early reads this process has answered; for diagnostics. */
    val count: Int get() = seen.get()

    fun policy(debuggable: Boolean): EarlyReadPolicy =
        if (debuggable) EarlyReadPolicy.Throw else EarlyReadPolicy { seen.incrementAndGet() }
}
