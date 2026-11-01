package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** 07 §7.2. Lives in AppScope; requests submitted before the graph is ready simply wait inside it (07 §7.1). */
public interface EntryRouter {
    /** Never blocks, never throws; callable from any thread. One slot per channel, newer wins (rule 1). */
    public fun submit(request: EntryRequest)

    /** 03 S12: state plus acknowledgement. One handoff at a time; the next waits until this one is consumed. */
    public val pending: StateFlow<EntryHandoff?>

    /** Called by the shell's `EntryHandoffCollector` after it navigated (rule 8). Unknown or stale ids are ignored. */
    public fun consume(handoffId: Long)

    /** Drives the one "Waiting for <server>…" status line. */
    public val waiting: StateFlow<EntryWaiting?>

    /** App resume: re-evaluate (a scheduled-run tap is re-evaluated on every resume, 07 §7.9). */
    public fun resume()

    /** The session link a [EntryTarget.SessionAddress] ticket holds (memory only, rule 9); null once forgotten. */
    public fun linkFor(ticket: Long): ParsedLink.Session?

    public fun forgetLink(ticket: Long)
}

/**
 * The effect port (07 §7.3 rule 6), bound by the app graph over the connection registry, the composer and the
 * monitors. Expected failures come back as [Outcome.Failed]; the router never shows exception text.
 */
public interface EntryEffects {
    public suspend fun connect(profileId: ProfileId): Outcome<Unit>

    public suspend fun resolveRef(ref: ConversationRef): Outcome<ConversationRef>

    /** Creates one empty session, guarded: profile, location and repository revision must not change meanwhile. */
    public suspend fun createSession(profileId: ProfileId): Outcome<ConversationRef>

    public suspend fun seedDraft(ref: ConversationRef, text: String): Outcome<Unit>

    /** Checks [token] against the stored monitor token first; a mismatch is a failure. */
    public suspend fun resolveMonitoredRequest(
        token: MonitorToken,
        profileId: ProfileId,
        sessionId: SessionId,
    ): Outcome<ConversationRef>

    /** Null = not found (→ Land(null)). */
    public suspend fun resolveWidgetConversation(profileId: ProfileId, sessionId: SessionId): Outcome<ConversationRef?>

    /** False = the token no longer belongs to a source. */
    public suspend fun verifyQuotaToken(profileId: ProfileId?, token: String): Outcome<Boolean>
}

/**
 * The one entry mechanism (07 §7.3). Every decision runs on one serial lane; [submit], [consume] and [resume] only
 * update state and signal the lane. Rules: one slot per channel, newer wins (1); re-evaluate on every change (2);
 * nothing before the gate (3); waiting vs final (4); consume once and only if still the same generation (5); effects
 * run here, guarded (6); the shell acknowledges (8); memory only (9).
 */
public class DefaultEntryRouter(
    private val worldSource: EntryWorldSource,
    private val effects: EntryEffects,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val lane: CoroutineDispatcher,
) : EntryRouter {
    public constructor(
        worldSource: EntryWorldSource,
        effects: EntryEffects,
        clock: Clock,
        appScope: AppCoroutineScope,
        dispatchers: AppDispatchers,
    ) : this(worldSource, effects, clock, appScope as CoroutineScope, dispatchers.serial("entry"))

    private class Slot(val request: EntryRequest, val generation: Long)

    private data class Slots(val byChannel: Map<EntryChannel, Slot> = emptyMap(), val nextGeneration: Long = 1)

    private val slots = MutableStateFlow(Slots())
    private val mutablePending = MutableStateFlow<EntryHandoff?>(null)
    private val mutableWaiting = MutableStateFlow<EntryWaiting?>(null)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val tickets = MutableStateFlow<Map<Long, ParsedLink.Session>>(emptyMap())
    private var nextHandoffId = 1L // lane-confined
    private var nextTicket = 1L // lane-confined
    private var tick: Job? = null // lane-confined

    override val pending: StateFlow<EntryHandoff?> = mutablePending.asStateFlow()
    override val waiting: StateFlow<EntryWaiting?> = mutableWaiting.asStateFlow()

    init {
        scope.launch(lane) {
            launch { worldSource.world.collect { signal.trySend(Unit) } }
            for (ignored in signal) evaluate()
        }
    }

    override fun submit(request: EntryRequest) {
        slots.update { s ->
            s.copy(
                byChannel = s.byChannel + (request.channel to Slot(request, s.nextGeneration)),
                nextGeneration = s.nextGeneration + 1,
            )
        }
        log.d { "entry request on ${request.channel}" }
        signal.trySend(Unit)
    }

    override fun consume(handoffId: Long) {
        mutablePending.update { if (it?.id == handoffId) null else it }
        signal.trySend(Unit)
    }

    override fun resume() {
        signal.trySend(Unit)
    }

    override fun linkFor(ticket: Long): ParsedLink.Session? = tickets.value[ticket]

    override fun forgetLink(ticket: Long) {
        tickets.update { it - ticket }
    }

    private suspend fun evaluate() {
        val world = worldSource.world.value
        var wait: EntryWaiting? = null
        var settling = false
        val ordered = slots.value.byChannel.values.sortedWith(compareBy<Slot> { it.request.receivedAt }.thenBy { it.generation })
        for (slot in ordered) {
            when (val decision = EntryDecider.decide(slot.request, world, clock.now())) {
                is EntryDecision.Wait -> {
                    if (wait == null) wait = EntryWaiting(decision.reason, decision.profileId)
                    if (decision.reason == WaitReason.ScheduleSettling) settling = true
                }
                is EntryDecision.Drop ->
                    if (decision.notice == null) {
                        clearIfSame(slot)
                    } else if (mutablePending.value == null && clearIfSame(slot)) {
                        publish(null, decision.notice)
                    }
                is EntryDecision.Go -> {
                    if (mutablePending.value != null) continue // one handoff at a time; consume() re-signals
                    val (target, notice) = run(decision)
                    // Rule 5: a newer request on this channel arrived while effects ran → it wins, this result is dropped.
                    if (clearIfSame(slot)) publish(target, notice) else signal.trySend(Unit)
                }
            }
        }
        mutableWaiting.value = wait
        armTick(settling)
    }

    /** A 1 s tick only while a scheduled-run tap waits for the start-up connection (rule 2). */
    private fun armTick(settling: Boolean) {
        if (!settling) {
            tick?.cancel()
            tick = null
        } else if (tick?.isActive != true) {
            tick =
                scope.launch(lane) {
                    delay(TICK_MS)
                    tick = null
                    signal.trySend(Unit)
                }
        }
    }

    private fun clearIfSame(slot: Slot): Boolean {
        var cleared = false
        slots.update { s ->
            val current = s.byChannel[slot.request.channel]
            if (current?.generation == slot.generation) {
                cleared = true
                s.copy(byChannel = s.byChannel - slot.request.channel)
            } else {
                cleared = false
                s
            }
        }
        return cleared
    }

    private fun publish(target: EntryTarget?, notice: EntryNotice?) {
        if (target == null && notice == null) return
        mutablePending.value = EntryHandoff(nextHandoffId++, target, notice)
    }

    private suspend fun run(go: EntryDecision.Go): Pair<EntryTarget?, EntryNotice?> {
        var target: EntryTarget = go.target
        for (effect in go.effects) {
            target =
                apply(effect, target)
                    ?: return if (effect.onFailure.keepTarget) {
                        target to effect.onFailure.notice
                    } else {
                        effect.onFailure.target to effect.onFailure.notice
                    }
        }
        return target to go.notice
    }

    /** Null = the effect failed. */
    private suspend fun apply(effect: EntryEffect, target: EntryTarget): EntryTarget? =
        when (effect) {
            is EntryEffect.Connect -> call { effects.connect(effect.profileId) }?.let { target }
            is EntryEffect.ResolveRef -> call { effects.resolveRef(effect.ref) }?.let { target.replacingRef(effect.ref, it) }
            is EntryEffect.CreateSession ->
                call { effects.createSession(effect.profileId) }?.let {
                    EntryTarget.Chat(it, focusComposer = effect.focusComposer, opened = Opened.NewlyCreated)
                }
            is EntryEffect.SeedDraft -> {
                val chat = target as? EntryTarget.Chat
                if (chat == null) null else call { effects.seedDraft(chat.ref, effect.text) }?.let { target }
            }
            is EntryEffect.ResolveMonitoredRequest ->
                call { effects.resolveMonitoredRequest(effect.token, effect.profileId, effect.sessionId) }?.let {
                    target.replacingRef(ConversationRef(effect.profileId, effect.sessionId), it)
                }
            is EntryEffect.ResolveWidgetConversation -> {
                val found = attempt({ Failure.Unexpected() }) { effects.resolveWidgetConversation(effect.profileId, effect.sessionId) }
                when (val outcome = found.flatten()) {
                    is Outcome.Failed -> null
                    is Outcome.Ok ->
                        outcome.value?.let { EntryTarget.Chat(it, focusComposer = effect.focusComposer) } ?: EntryTarget.Land(null)
                }
            }
            is EntryEffect.VerifyQuotaToken ->
                call { effects.verifyQuotaToken(effect.profileId, effect.token) }?.let { belongs -> if (belongs) target else null }
            is EntryEffect.ParkSessionLink -> {
                val ticket = nextTicket++
                tickets.update { it + (ticket to ParsedLink.Session(effect.profileId, effect.sessionId)) }
                EntryTarget.SessionAddress(ticket)
            }
        }

    /** The port returns Outcome; a throwing binding is a bug, still turned into a failure (never crashes the lane). */
    private suspend inline fun <T : Any> call(crossinline block: suspend () -> Outcome<T>): T? =
        when (val outcome = attempt({ Failure.Unexpected() }) { block() }.flatten()) {
            is Outcome.Ok -> outcome.value
            is Outcome.Failed -> {
                log.i { "entry effect failed: ${outcome.failure::class.simpleName}" }
                null
            }
        }

    private fun <T> Outcome<Outcome<T>>.flatten(): Outcome<T> =
        when (this) {
            is Outcome.Ok -> value
            is Outcome.Failed -> this
        }

    private companion object {
        const val TICK_MS = 1_000L
        val log = AppLog.tagged("entry")
    }
}

/** Rewrites every Chat on [from] (nested in Land, SwitchServerThen, ConfirmConnect too) to the resolved [to]. */
internal fun EntryTarget.replacingRef(from: ConversationRef, to: ConversationRef): EntryTarget =
    when (this) {
        is EntryTarget.Chat -> if (ref == from) copy(ref = to) else this
        is EntryTarget.Land -> copy(then = then?.replacingRef(from, to))
        is EntryTarget.SwitchServerThen -> copy(then = then.replacingRef(from, to))
        is EntryTarget.ConfirmConnect -> copy(then = then.replacingRef(from, to))
        else -> this
    }
