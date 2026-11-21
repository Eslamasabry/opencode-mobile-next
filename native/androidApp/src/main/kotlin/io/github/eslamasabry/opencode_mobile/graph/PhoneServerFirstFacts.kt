package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts

/**
 * The phone server's facts before the first status read answers. The person's own Stop is STORED, so the very first
 * frame of Home must already draw it (the stopped state and "Start OpenCode"); nothing reads connected until the
 * server says so.
 */
internal fun firstServerFacts(storedStop: Boolean): PhoneServerFacts = PhoneServerFacts()
