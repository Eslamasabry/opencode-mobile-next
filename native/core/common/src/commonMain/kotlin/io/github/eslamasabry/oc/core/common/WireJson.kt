package io.github.eslamasabry.oc.core.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** Wire JSON (network-data §4.3): one instance; never echoes input into exceptions. */
@OptIn(ExperimentalSerializationApi::class)
public val WireJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = false
        exceptionsWithDebugInfo = false
    }
