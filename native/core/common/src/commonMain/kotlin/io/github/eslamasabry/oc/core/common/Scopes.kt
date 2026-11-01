package io.github.eslamasabry.oc.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/** Metro scope marker for everything that lives as long as one live server connection (ADR §9.1). */
public abstract class ConnectionScope private constructor()

/** Process-lifetime scope: SupervisorJob + default dispatcher + a handler that logs (never crashes the process). */
public class AppCoroutineScope(override val coroutineContext: CoroutineContext) : CoroutineScope

/** One per live connection; a child of AppCoroutineScope. Cancelled by ConnectionRegistry.close(). */
public class ConnectionCoroutineScope(override val coroutineContext: CoroutineContext) : CoroutineScope

public fun supervisedContext(parent: Job?, dispatcher: CoroutineDispatcher, name: String): CoroutineContext =
    SupervisorJob(parent) + dispatcher + CoroutineName(name) +
        CoroutineExceptionHandler { _, e -> AppLog.tagged(name).e(e) { "uncaught failure in $name" } }
