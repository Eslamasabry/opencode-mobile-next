package io.github.eslamasabry.oc.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val platformIoDispatcher: CoroutineDispatcher = Dispatchers.IO

public actual val platformName: String = "android"

internal actual fun stackFramesOf(error: Throwable): List<String> = error.stackTrace.map { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
