package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.github.eslamasabry.oc.feature.chat.presentation.FactsClock

/** Reads the current wall-clock time from [FactsClock], subscribing only the caller's recomposition scope. */
@Composable
internal fun FactsClock.read(): Long = nowMs.collectAsState().value
