package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.net.ws.PlatformWsConnections
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.feature.codexprobe.*
import io.github.eslamasabry.oc.feature.servers.presentation.SetupCommands
import io.github.eslamasabry.oc.protocol.codex.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A person-opened read-only connection; never registered as a full chat gateway. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CodexProbeHost>())
public class AppCodexReads(
    private val app: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
) : CodexProbeHost {
    private class Live(val reader: CodexReads, val scope: CoroutineScope, val id: String)

    private val live = AtomicReference<Live?>(null)
    private val operations = Mutex()
    private val local = MutableStateFlow(CodexProbeUi())
    override val state: StateFlow<CodexProbeUi> = local.asStateFlow()
    override val startCommand: String
        get() = SetupCommands.CODEX_START

    private val sockets = PlatformWsConnections(dispatchers)

    public fun connections(): List<UsageConnection> =
        live
            .get()
            ?.takeIf { it.reader.connected }
            ?.let {
                listOf(UsageConnection(it.id, "Codex", UsageConnectionKind.CodexServer))
            }
            .orEmpty()

    public val usageSource: UsageSource = UsageSource { context -> readUsage(context) }

    override suspend fun probe(address: String, directory: String, token: String): Outcome<Unit> =
        operate(address, directory, token, keep = false)

    override suspend fun open(address: String, directory: String, token: String): Outcome<Unit> =
        operate(address, directory, token, keep = true)

    private suspend fun operate(
        address: String,
        directory: String,
        token: String,
        keep: Boolean,
    ): Outcome<Unit> = operations.withLock {
        local.update { it.copy(busy = true, probed = false, failure = null) }
        var opened: CodexReads? = null
        val scope =
            CoroutineScope(
                supervisedContext(app.coroutineContext[Job], dispatchers.default, "codex-read-only")
            )
        var retained = false
        try {
            val target =
                when (val parsed = CodexTarget.parse(address, directory, token)) {
                    is Outcome.Failed -> return@withLock publish(parsed)
                    is Outcome.Ok -> parsed.value
                }
            redactor.registerSecret(token)
            val reader =
                when (val result = CodexReads.open(target, sockets, scope, dispatchers)) {
                    is Outcome.Failed -> return@withLock publish(result)
                    is Outcome.Ok -> result.value
                }
            opened = reader
            when (val result = reader.probe()) {
                is Outcome.Failed -> publish(result)
                is Outcome.Ok -> {
                    if (keep) {
                        disconnect()
                        val current =
                            Live(
                                reader,
                                scope,
                                "codex-reader:" +
                                    DartJson.sha256Hex(target.url + "\n" + target.directory),
                            )
                        live.set(current)
                        retained = true
                        local.update {
                            it.copy(connected = true, accountQualified = reader.accountQualified)
                        }
                        scope.launch {
                            reader.awaitClosed()
                            if (live.compareAndSet(current, null)) {
                                local.update {
                                    it.copy(connected = false, accountQualified = false)
                                }
                                scope.cancel()
                            }
                        }
                    } else local.update { it.copy(probed = true) }
                    Unit.ok()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            publish(Outcome.Failed(Failure.Network(NetworkWhy.Reset)))
        } finally {
            if (!retained)
                withContext(NonCancellable) {
                    opened?.close()
                    scope.cancel()
                }
            local.update { it.copy(busy = false) }
        }
    }

    private fun publish(result: Outcome.Failed): Outcome.Failed {
        local.update { it.copy(failure = result.failure) }
        return result
    }

    private suspend fun disconnect() {
        val old = live.getAndSet(null)
        if (old != null) {
            old.reader.close()
            old.scope.cancel()
        }
        local.update { it.copy(connected = false, accountQualified = false) }
    }

    override suspend fun close() {
        operations.withLock { disconnect() }
    }

    /** No connection means no request. A failed reader never becomes a zero-use plan. */
    private suspend fun readUsage(context: UsageReadContext): Outcome<UsageSourceResult> {
        val current = live.get() ?: return UsageSourceResult().ok()
        if (context.connections.none { it.id == current.id }) return UsageSourceResult().ok()
        fun failed(failure: Failure): Outcome<UsageSourceResult> =
            if (failure is Failure.Network || !current.reader.connected || live.get() !== current)
                UsageSourceResult(unreached = setOf(current.id)).ok()
            else
                UsageSourceResult(
                        failures = listOf(UsageFailure(current.id, "codex", UsageProblem.Broken))
                    )
                    .ok()
        val account =
            when (val result = current.reader.account()) {
                is Outcome.Failed -> return failed(result.failure)
                is Outcome.Ok -> result.value
            }
        val accountRef =
            account.email?.takeIf { it.isNotEmpty() }?.let { DartJson.sha256Hex("codex-account:$it") }
        if (live.get() !== current) return UsageSourceResult(unreached = setOf(current.id)).ok()
        val accountType = account.type
        if (accountType == null) {
            val wanted = account.requiresSignIn || context.lastReading(current.id, "codex") != null
            return UsageSourceResult(
                    readings =
                        if (wanted)
                            listOf(
                                UsageReading(
                                    "codex",
                                    "Codex",
                                    current.id,
                                    UsageReadingStatus.NotSignedIn,
                                    context.now,
                                    method = SignInMethod.Plan,
                                )
                            )
                        else emptyList(),
                    authoritativeSignIns = setOf(current.id),
                )
                .ok()
        }
        if (accountType.lowercase().contains("key"))
            return UsageSourceResult(
                    signIns =
                        listOf(
                            UsageSignIn(
                                "codex",
                                current.id,
                                method = SignInMethod.ApiKey,
                                displayName = "OpenAI",
                            )
                        ),
                    authoritativeSignIns = setOf(current.id),
                )
                .ok()
        val buckets =
            when (val result = current.reader.limits()) {
                is Outcome.Failed -> return failed(result.failure)
                is Outcome.Ok -> result.value
            }
        if (live.get() !== current) return UsageSourceResult(unreached = setOf(current.id)).ok()
        val first = buckets.firstOrNull()
        val windows =
            listOfNotNull(
                first?.primary?.let { window("primary", it) },
                first?.secondary?.let { window("secondary", it) },
            )
        val plan =
            account.plan?.lowercase()?.takeIf {
                it in setOf("free", "go", "plus", "pro", "team", "business", "enterprise", "edu")
            }
        return UsageSourceResult(
                readings =
                    listOf(
                        UsageReading(
                            "codex",
                            "Codex",
                            current.id,
                            if (windows.isEmpty()) UsageReadingStatus.NoLimits
                            else UsageReadingStatus.Ok,
                            context.now,
                            windows,
                            accountRef,
                            plan,
                            SignInMethod.Plan,
                        )
                    ),
                signIns =
                    listOf(
                        UsageSignIn(
                            "codex",
                            current.id,
                            method = SignInMethod.Plan,
                            displayName = "Codex",
                        )
                    ),
                authoritativeSignIns = setOf(current.id),
            )
            .ok()
    }

    private fun window(id: String, value: CodexLimitWindow): UsageWindow =
        UsageWindow(
            id,
            value.usedPercent.toDouble(),
            value.durationMinutes?.takeIf { it <= Long.MAX_VALUE / 60 }?.times(60),
            value.resetsAtSeconds?.let { Instant.fromEpochMilliseconds(it * 1000) },
        )
}
