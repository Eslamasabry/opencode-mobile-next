package io.github.eslamasabry.oc.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val platformIoDispatcher: CoroutineDispatcher = Dispatchers.IO

public actual val platformName: String = "ios"

internal actual fun stackFramesOf(error: Throwable): List<String> = emptyList() // Kotlin/Native frames are addresses; shape type only
