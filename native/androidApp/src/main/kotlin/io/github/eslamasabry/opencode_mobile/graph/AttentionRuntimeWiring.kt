package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.notifications.*
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.FormSpec
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.attention.live.LiveStatusComposer
import io.github.eslamasabry.oc.engine.attention.live.LiveWords
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.text.DateFormat
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The live service's single row, fed by the same real connections and requests as the app. */
@SingleIn(AppScope::class)
@Inject
class AttentionRuntime(
    private val application: Application,
    private val registry: ConnectionRegistry,
    private val runtime: PreviewRuntime,
    private val stores: PreviewStores,
    private val background: BackgroundHub,
    private val preferences: AppPreferences,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val words = NotifyWords(application) { preferences.localeTag.value }
    private val statusWords = AndroidStatusWords(words)
    private val toolWords = AndroidLiveWords(words)
    private val renderer = StatusRenderer(application, words)
    private val throttle = StatusThrottle(120)
    private val controllers = linkedMapOf<ProfileId, StatusController>()
    private val observers = mutableMapOf<ProfileId, Job>()
    private val observedGraphs = mutableMapOf<ProfileId, ConnectionGraph>()
    private val eventRuns = mutableMapOf<ProfileId, MutableMap<String, Boolean>>()
    private val toolNames = mutableMapOf<Pair<ProfileId, String>, String>()
    private var started = false
    private var trailing: Job? = null
    private var latest = StatusModel.build(StatusInput(), statusWords, clock.now(), ::formatTime)
    private var serverRunning = false
    private var idleStopped = false
    private var setup: StatusSetup? = null

    /**
     * Called once after migration/prefs warmup; no service or monitoring consent is enabled here.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            stores.warm()
            launch {
                combine(runtime.snapshot, background.live, preferences.localeTag) { _, _, _ ->
                        Unit
                    }
                    .collect { refresh() }
            }
            launch {
                registry.live.collect { live ->
                    for (id in observers.keys.filter { observedGraphs[it] !== live[it] }) {
                        observers.remove(id)?.cancel()
                        observedGraphs.remove(id)
                        controllers.remove(id)
                        eventRuns.remove(id)
                        toolNames.keys.removeAll { it.first == id }
                    }
                    for ((id, graph) in live) if (id !in observers) {
                        observedGraphs[id] = graph
                        observers[id] = observe(id, graph)
                    }
                    refresh()
                }
            }
            // Refresh elapsed times and native setup/server facts only while the live service is
            // active.
            launch {
                while (true) {
                    if (background.live.value.active) {
                        runtimeFacts()
                        refresh()
                    }
                    delay(5.seconds)
                }
            }
        }
    }

    /**
     * Used synchronously to keep Android's foreground promise before its activation
     * acknowledgement.
     */
    fun notification(): Notification = render(latest)

    private fun controller(id: ProfileId): StatusController =
        controllers.getOrPut(id) {
            StatusController(statusWords, clock, ::formatTime).also { it.profileId = id.raw }
        }

    private fun observe(id: ProfileId, graph: ConnectionGraph): Job = scope.launch {
        val controller = controller(id)
        val requests = (graph as? ChatGraphAccessors)?.chatRequests?.engine
        var pendingRequests: List<PendingRequest> = requests?.pending?.value.orEmpty()
        val forms = linkedMapOf<String, FormSpec>()
        var formsRevision = 0L
        var formsRead: Job? = null
        val retryReads = mutableMapOf<String, Job>()
        val retryRevisions = mutableMapOf<String, Long>()

        fun pendingChanged() {
            controller.setPending(
                pendingRequests.filterIsInstance<PendingRequest.Permission>().map {
                    it.sessionId.raw
                },
                pendingRequests.filterIsInstance<PendingRequest.Question>().map {
                    it.sessionId.raw
                },
                forms.values.map { it.sessionId.raw },
            )
        }

        fun refreshForms() {
            formsRead?.cancel()
            if (!graph.session.server.capabilities.value.forms) return
            formsRead = launch {
                // A newer event wins over a pending snapshot. Retry once, without an enduring poll.
                repeat(2) {
                    val revision = formsRevision
                    val outcome =
                        attempt({ Failure.Unexpected() }) {
                                withTimeoutOrNull(5.seconds) { graph.session.server.pendingForms() }
                            }
                            .getOrNull()
                    val listed = outcome?.getOrNull() ?: return@launch
                    if (registry.live.value[id] !== graph) return@launch
                    if (formsRevision != revision) return@repeat
                    forms.clear()
                    listed.forEach { forms[it.id.raw] = it }
                    pendingChanged()
                    refresh()
                    return@launch
                }
            }
        }

        fun cancelRetry(session: String): Long {
            retryReads.remove(session)?.cancel()
            val revision = (retryRevisions[session] ?: 0L) + 1L
            retryRevisions[session] = revision
            return revision
        }

        pendingChanged()
        refreshForms()
        launch {
            graph.session.server.events.all.collect { envelope ->
                if (registry.live.value[id] !== graph) return@collect
                when (val event = envelope.event) {
                    GatewayEvent.Connected -> {
                        eventRuns.remove(id)
                        retryReads.keys.toList().forEach { cancelRetry(it) }
                        refreshForms()
                    }
                    is GatewayEvent.FormCreated -> {
                        formsRevision++
                        if (graph.session.server.capabilities.value.forms)
                            forms[event.form.id.raw] = event.form
                        pendingChanged()
                    }
                    is GatewayEvent.FormSettled -> {
                        formsRevision++
                        forms.remove(event.formId.raw)
                        pendingChanged()
                    }
                    is GatewayEvent.SessionDeleted -> {
                        formsRevision++
                        forms.entries.removeAll { it.value.sessionId == event.sessionId }
                        pendingChanged()
                        cancelRetry(event.sessionId.raw)
                    }
                    is GatewayEvent.RunStarted -> {
                        cancelRetry(event.sessionId.raw)
                        eventRuns.getOrPut(id) { linkedMapOf() }[event.sessionId.raw] = true
                        controller.onRun(event.sessionId.raw, StatusController.Run.Busy)
                    }
                    is GatewayEvent.RunRetrying -> {
                        val session = event.sessionId.raw
                        val revision = cancelRetry(session)
                        val next = event.nextAtMs?.let(Instant::fromEpochMilliseconds)
                        eventRuns.getOrPut(id) { linkedMapOf() }[session] = true
                        controller.onRun(session, StatusController.Run.Retry(next))
                        retryReads[session] = launch {
                            // The session's current model names the provider. Retry prose is never
                            // copy.
                            val name =
                                attempt({ Failure.Unexpected() }) {
                                        withTimeoutOrNull(5.seconds) {
                                            val observed =
                                                graph.session.server
                                                    .session(event.sessionId)
                                                    .getOrNull() ?: return@withTimeoutOrNull null
                                            val providerId =
                                                observed.selection
                                                    ?.takeIf { it.modelKnown }
                                                    ?.model
                                                    ?.providerId ?: return@withTimeoutOrNull null
                                            val catalog =
                                                graph.session.server.providers().getOrNull()
                                                    ?: return@withTimeoutOrNull null
                                            (catalog.providers + catalog.availableProviders)
                                                .firstOrNull { it.id == providerId }
                                                ?.name
                                                ?.let { NotificationText.redact(it) }
                                                ?.takeIf { it.isNotBlank() }
                                        }
                                    }
                                    .getOrNull()
                            if (
                                registry.live.value[id] === graph &&
                                    retryRevisions[session] == revision &&
                                    eventRuns[id]?.get(session) == true
                            ) {
                                controller.onRun(session, StatusController.Run.Retry(next, name))
                                refresh()
                            }
                            if (retryRevisions[session] == revision) retryReads.remove(session)
                        }
                    }
                    is GatewayEvent.RunEnded -> {
                        cancelRetry(event.sessionId.raw)
                        eventRuns.getOrPut(id) { linkedMapOf() }[event.sessionId.raw] = false
                        if (
                            event.outcome is RunOutcome.Succeeded ||
                                event.outcome is RunOutcome.Idle
                        ) {
                            controller.onRun(event.sessionId.raw, StatusController.Run.Idle)
                        } else controller.onError(event.sessionId.raw)
                        toolNames.remove(id to event.sessionId.raw)
                    }
                    is GatewayEvent.PartUpserted -> {
                        val part = event.part
                        if (part.type == PartType.Tool) {
                            val key = id to part.sessionId
                            if (
                                part.tool?.status == ToolStatus.Running ||
                                    part.tool?.status == ToolStatus.Pending
                            ) {
                                toolNames[key] = part.toolName.orEmpty()
                            } else toolNames.remove(key)
                            if (part.toolName?.lowercase() in setOf("todowrite", "todoread")) {
                                val todos =
                                    (part.tool?.input?.get("todos")
                                        ?: part.tool?.metadata?.get("todos"))
                                        as? JsonArray
                                controller.onPlan(
                                    part.sessionId,
                                    todos?.mapNotNull { row ->
                                        val item = row as? JsonObject ?: return@mapNotNull null
                                        val content =
                                            (item["content"] as? JsonPrimitive)?.content
                                                ?: return@mapNotNull null
                                        val status =
                                            (item["status"] as? JsonPrimitive)?.content
                                                ?: return@mapNotNull null
                                        StatusController.Todo(
                                            NotificationText.redact(content),
                                            status,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    else -> Unit
                }
                refresh()
            }
        }
        launch { graph.session.server.events.status.collect { refresh() } }
        requests?.pending?.collect { pending ->
            pendingRequests = pending
            pendingChanged()
            refresh()
        }
    }

    private suspend fun runtimeFacts() {
        withContext(dispatchers.io) {
            val read =
                attempt({ Failure.Unexpected() }) {
                    val linux = BuiltinLinux.get(application)
                    val idle = linux.serverIdleStatus()
                    val raw = SetupRunner.get(application).status()
                    Triple(linux.serverRunning, idle["serverIdleStopped"] == true, raw)
                }
            if (read is Outcome.Ok)
                withContext(dispatchers.main) {
                    serverRunning = read.value.first
                    idleStopped = read.value.second
                    setup =
                        read.value.third?.let { raw ->
                            val value =
                                try {
                                    org.json.JSONObject(raw)
                                } catch (_: org.json.JSONException) {
                                    null
                                }
                            if (value?.optString("state") != "running") null
                            else {
                                val order = value.optJSONArray("order")
                                val current = value.optString("current")
                                val index =
                                    (0 until (order?.length() ?: 0)).indexOfFirst {
                                        order?.optString(it) == current
                                    }
                                val percent =
                                    value
                                        .optDouble("overall", Double.NaN)
                                        .takeIf { it.isFinite() }
                                        ?.let { (it * 100).toInt() }
                                StatusSetup(
                                    (index + 1).coerceAtLeast(1),
                                    (order?.length() ?: 1).coerceAtLeast(1),
                                    name = words.text(R.string.notify_status_lock_setup),
                                    percent = percent,
                                )
                            }
                        }
                }
        }
    }

    private fun refresh() {
        val live = registry.live.value
        val feed = runtime.snapshot.value
        for ((id, graph) in live) {
            val controller = controller(id)
            val rows = feed.items.filter { it.profileId == id }
            controller.setSessions(
                rows.map {
                    StatusController.Session(
                        it.sessionId.raw,
                        NotificationText.redact(it.title.orEmpty()),
                        it.directory,
                        it.parentId?.raw,
                        it.lastActivityMs,
                    )
                }
            )
            // Event starts cover the interval before the next session-page read; a later idle event
            // removes them.
            val polledBusy =
                rows
                    .filter { it.status == ChatStatus.Running || it.status == ChatStatus.NeedsYou }
                    .map { it.sessionId.raw }
                    .filter { eventRuns[id]?.get(it) != false }
                    .toSet()
            controller.setBusy(polledBusy + eventRuns[id].orEmpty().filterValues { it }.keys)
            for (row in rows) controller.onToolLine(
                row.sessionId.raw,
                toolNames[id to row.sessionId.raw]?.let {
                    LiveStatusComposer.toolSentence(it, toolWords)
                },
            )
            controller.serverName =
                NotificationText.redact(stores.profiles.profile(id)?.profile?.name.orEmpty())
            controller.liveServerCount = live.size.coerceAtLeast(1)
            controller.reconnecting =
                graph.session.server.events.status.value is StreamStatus.Reconnecting
            controller.androidLimit = background.live.value.stoppedByAndroid
            controller.phoneServerRunning = serverRunning
            controller.setup = setup
        }
        val inputs = controllers.filterKeys { it in live }.map { it.value.input() }
        val workingByOwner =
            inputs
                .flatMap { owner -> owner.working.map { owner to it } }
                .sortedByDescending { (owner, chat) ->
                    feed.items
                        .firstOrNull {
                            it.profileId.raw == owner.profileId && it.sessionId.raw == chat.id
                        }
                        ?.lastActivityMs ?: 0L
                }
        val primary =
            inputs.firstOrNull { it.pendingCount > 0 }
                ?: workingByOwner.firstOrNull()?.first
                ?: inputs.firstOrNull()
        var input =
            (primary ?: StatusInput()).copy(
                working = workingByOwner.map { it.second },
                pendingCount = inputs.sumOf { it.pendingCount },
                retries = inputs.flatMap { it.retries },
                phoneServerRunning = serverRunning,
                setup = setup,
                pause =
                    when {
                        background.live.value.stoppedByAndroid -> PauseReason.AndroidLimit
                        idleStopped -> PauseReason.Idle
                        else -> primary?.pause ?: PauseReason.None
                    },
            )
        if (input.working.size == 1) {
            val owner = inputs.firstOrNull { it.working.isNotEmpty() }
            if (owner != null) input = input.copy(profileId = owner.profileId)
        }
        if (input.working.isEmpty() && input.pendingCount == 0 && input.retries.isNotEmpty()) {
            val retry = input.retries.sortedWith(compareBy(nullsLast()) { it.nextTry }).first()
            val owner = inputs.firstOrNull { retry in it.retries }
            if (owner != null) input = input.copy(profileId = owner.profileId)
        }
        latest = StatusModel.build(input, statusWords, clock.now(), ::formatTime)
        if (!background.live.value.active) {
            throttle.reset()
            trailing?.cancel()
            trailing = null
            return
        }
        when (val step = throttle.offer(latest, android.os.SystemClock.elapsedRealtime())) {
            is StatusThrottle.Step.Post -> post(step.content)
            is StatusThrottle.Step.Wait ->
                if (trailing == null)
                    trailing = scope.launch {
                        delay(step.delayMs)
                        trailing = null
                        if (background.live.value.active)
                            throttle.due(android.os.SystemClock.elapsedRealtime())?.let(::post)
                    }
            StatusThrottle.Step.Skip -> Unit
        }
    }

    private fun post(content: StatusContent) {
        try {
            application
                .getSystemService(NotificationManager::class.java)
                .notify(
                    NotificationIds.STATUS,
                    render(content),
                )
        } catch (_: SecurityException) {
            /* Permission revoked: the service/controller owns admission. */
        }
    }

    private fun render(content: StatusContent): Notification {
        val row =
            renderer.build(content, ongoing = true, chip = LiveUpdatePolicy.chipFor(content, false))
        val pause =
            android.app.PendingIntent.getBroadcast(
                application,
                0x4B48,
                android.content
                    .Intent(
                        application,
                        io.github.eslamasabry.opencode_mobile.LivePauseReceiver::class.java,
                    )
                    .setAction(io.github.eslamasabry.opencode_mobile.LivePauseReceiver.ACTION),
                android.app.PendingIntent.FLAG_IMMUTABLE or
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Builder.recoverBuilder(words.localized(), row)
            .addAction(
                Notification.Action.Builder(
                        io.github.eslamasabry.oc.android.notifications.R.drawable.notify_ic_status,
                        words.text(
                            io.github.eslamasabry.opencode_mobile.R.string.andr_pauseBackground
                        ),
                        pause,
                    )
                    .build()
            )
            .build()
    }

    private fun formatTime(at: Instant): String =
        DateFormat.getTimeInstance(
                DateFormat.SHORT,
                words.localized().resources.configuration.locales[0],
            )
            .format(Date(at.toEpochMilliseconds()))
}

@ContributesTo(AppScope::class)
interface AttentionRuntimeAccessors {
    val attentionRuntime: AttentionRuntime
}

internal val Context.attentionRuntime: AttentionRuntime
    get() = (appGraph as AttentionRuntimeAccessors).attentionRuntime

private class AndroidLiveWords(private val words: NotifyWords) : LiveWords {
    override val toolCommand
        get() = words.text(R.string.notify_tool_command)

    override val toolEdit
        get() = words.text(R.string.notify_tool_edit)

    override val toolRead
        get() = words.text(R.string.notify_tool_read)

    override val toolSearch
        get() = words.text(R.string.notify_tool_search)

    override val toolWeb
        get() = words.text(R.string.notify_tool_web)

    override val toolSubagent
        get() = words.text(R.string.notify_tool_subagent)

    override val toolPlan
        get() = words.text(R.string.notify_tool_plan)

    override val toolWorking
        get() = words.text(R.string.notify_tool_working)

    override val toolCard
        get() = words.text(R.string.notify_tool_card)

    override val toolBackgroundTask
        get() = words.text(R.string.notify_tool_backgroundtask)

    override fun toolOther(tool: String) =
        words.text(R.string.notify_tool_other, NotificationText.redact(tool))

    override fun needsYou(count: Int) =
        words.plural(R.plurals.notify_status_needs_you, count, count)

    override fun chatsWorking(count: Int) = words.text(R.string.notify_status_chats_working, count)

    override val readyTitle
        get() = words.text(R.string.notify_status_ready_title)
}
