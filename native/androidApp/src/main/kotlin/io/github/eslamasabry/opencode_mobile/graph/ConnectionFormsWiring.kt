package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.core.domain.FormGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.FormAnswer
import io.github.eslamasabry.oc.core.model.FormState
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.security.*
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.requests.forms.*
import io.github.eslamasabry.oc.feature.chat.forms.*
import io.github.eslamasabry.oc.ui.routes.ConnectionFormRoute
import java.util.concurrent.ConcurrentHashMap
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/**
 * One form lane per exact live graph/location, shared by monitor sheets and the conversation card.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(
    AppScope::class,
    binding = binding<ConnectionForms>(),
    replaces = [UnavailableConnectionForms::class],
)
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class NativeConnectionForms(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
) : ConnectionForms, ProfileCloser {
    override val owner: String = "connection-forms"
    override val drainOrder: Int = -90
    private val blocked = ConcurrentHashMap.newKeySet<ProfileId>()
    private val lane = dispatchers.serial("connection-forms")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private val holders = mutableMapOf<Pair<ConnectionGraph, ServerLocation>, Holder>()
    private val sessions = mutableSetOf<Session>()

    override fun open(route: ConnectionFormRoute): ConnectionFormSession =
        synchronized(sessions) {
            Session(route).also {
                sessions.add(it)
                scope.launch { it.load() }
            }
        }

    override fun stopAdmission(profile: ProfileId) {
        blocked.add(profile)
    }

    override fun reopen(profile: ProfileId) {
        blocked.remove(profile)
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> =
        withContext(lane) {
            holders
                .filter { it.key.first.profile.id == profile }
                .values
                .forEach { it.engine.flushHeld(null) }
            synchronized(sessions) {
                sessions.filter { it.profile == profile }.forEach { it.changed() }
                sessions.removeAll { it.profile == profile }
            }
            holders.keys.removeAll { it.first.profile.id == profile }
            Unit.ok()
        }

    private fun current(
        profile: ProfileId,
        graph: ConnectionGraph,
        location: ServerLocation,
    ): Boolean =
        profile !in blocked &&
            stores.profiles.profile(profile) != null &&
            registry.live.value[profile] === graph &&
            graph.session.server.location.value == location

    private inner class Holder(val graph: ConnectionGraph, val location: ServerLocation) {
        val drafts = FormDrafts()
        val modelScope = CoroutineScope(graph.scope.coroutineContext + lane)
        val gateway = graph.session.server
        val engine =
            FormRequests(
                object : FormGateway {
                    private fun valid() =
                        current(graph.profile.id, graph, location) &&
                            gateway.capabilities.value.forms

                    override suspend fun pendingForms():
                        Outcome<ImmutableList<io.github.eslamasabry.oc.core.model.FormSpec>> =
                        if (valid()) gateway.pendingForms()
                        else Failure.Refused(RefusalReason.StaleScope).failed()

                    override suspend fun replyForm(
                        session: SessionId,
                        form: FormId,
                        answer: FormAnswer,
                    ): Outcome<Unit> =
                        if (valid()) gateway.replyForm(session, form, answer)
                        else Failure.Refused(RefusalReason.StaleScope).failed()

                    override suspend fun cancelForm(
                        session: SessionId,
                        form: FormId,
                    ): Outcome<Unit> =
                        if (valid()) gateway.cancelForm(session, form)
                        else Failure.Refused(RefusalReason.StaleScope).failed()

                    override suspend fun formState(
                        session: SessionId,
                        form: FormId,
                    ): Outcome<FormState> =
                        if (valid()) gateway.formState(session, form)
                        else Failure.Refused(RefusalReason.StaleScope).failed()
                },
                modelScope,
            )

        init {
            modelScope.launch {
                gateway.events.project.collect { envelope ->
                    if (current(graph.profile.id, graph, location)) {
                        if (envelope.event == GatewayEvent.Connected) engine.refresh()
                        else engine.onEvent(envelope.event)
                    }
                }
            }
        }
    }

    private inner class Session(private val route: ConnectionFormRoute) : ConnectionFormSession {
        val profile: ProfileId
            get() = route.profile

        private val mutable = MutableStateFlow(ConnectionFormUi())
        override val state: StateFlow<ConnectionFormUi> = mutable.asStateFlow()
        private var holder: Holder? = null
        private var identity: String? = null
        private var revision: String? = null
        private var pendingLink: ApprovedLink? = null
        private var watch: Job? = null
        private var locationWatch: Job? = null
        private var seeded = false

        private fun valid(): Boolean =
            holder?.let { current(route.profile, it.graph, it.location) } == true

        fun changed() {
            mutable.update { it.copy(phase = ConnectionFormPhase.Changed, linkHost = null) }
            pendingLink = null
        }

        suspend fun load() {
            val warmed = stores.warm()
            if (warmed is Outcome.Failed) {
                mutable.update {
                    it.copy(failure = warmed.failure, phase = ConnectionFormPhase.Refused)
                }
                return
            }
            val graph = registry.live.value[route.profile]
            if (
                graph == null || !graph.session.capabilities.value.forms || route.profile in blocked
            ) {
                mutable.update { it.copy(phase = ConnectionFormPhase.Unavailable) }
                return
            }
            val location = graph.session.server.location.value
            if (
                (route.directory != null && route.directory != location.directory) ||
                    (route.workspace != null && route.workspace != location.workspace)
            ) {
                changed()
                return
            }
            val next = holders.getOrPut(graph to location) { Holder(graph, location) }
            holder = next
            when (val read = next.engine.refresh()) {
                is Outcome.Failed -> {
                    mutable.update {
                        it.copy(failure = read.failure, phase = ConnectionFormPhase.Refused)
                    }
                    return
                }
                is Outcome.Ok -> Unit
            }
            if (!valid()) {
                changed()
                return
            }
            val form =
                next.engine.pending.value.firstOrNull {
                    it.id.raw == route.formId && it.wire.sessionId.raw == route.sessionId
                }
            if (form == null) {
                changed()
                return
            }
            if (!seeded) {
                revision = form.spec.revision
                identity =
                    FormRevision.identityKey(
                        route.profile.raw,
                        location.directory.orEmpty(),
                        location.workspace,
                        route.sessionId,
                        route.formId,
                        form.spec.revision,
                    )
                val kept = next.drafts.get(identity!!) ?: readDraft(form.spec, identity!!)
                mutable.update {
                    it.copy(
                        form = form.spec,
                        draft = kept,
                        phase = ConnectionFormPhase.Waiting,
                        failure = null,
                    )
                }
                seeded = true
            }
            watch?.cancel()
            watch =
                next.modelScope.launch {
                    combine(next.engine.pending, next.engine.phases, next.engine.notices) { _, _, _
                            ->
                            Unit
                        }
                        .collect { publish() }
                }
            locationWatch?.cancel()
            locationWatch =
                next.modelScope.launch { next.gateway.location.collect { if (!valid()) changed() } }
            publish()
        }

        private fun publish() {
            val next = holder ?: return
            if (!valid()) {
                changed()
                return
            }
            val formId = FormId(route.formId)
            val form =
                next.engine.pending.value.firstOrNull {
                    it.id == formId && it.wire.sessionId.raw == route.sessionId
                }
            val notice =
                next.engine.notices.value.firstOrNull {
                    it is FormNotice.AnsweredElsewhere && it.formId == formId
                }
            if (form == null) {
                if (seeded) {
                    mutable.update {
                        it.copy(
                            phase =
                                if (notice == null) ConnectionFormPhase.Settled
                                else ConnectionFormPhase.AnsweredElsewhere,
                            failure = null,
                        )
                    }
                    clearDraft()
                }
                return
            }
            if (form.spec.revision != revision) {
                changed()
                return
            }
            val phase = next.engine.phases.value[formId]
            mutable.update {
                it.copy(
                    form = form.spec,
                    evaluation = FormRules.evaluate(form.spec, it.draft),
                    phase =
                        when (phase) {
                            is AnswerPhase.Held -> ConnectionFormPhase.Held
                            is AnswerPhase.Sending -> ConnectionFormPhase.Sending
                            is AnswerPhase.Refused -> ConnectionFormPhase.Refused
                            null -> ConnectionFormPhase.Waiting
                        },
                    failure = (phase as? AnswerPhase.Refused<*>)?.failure,
                    slow = (phase as? AnswerPhase.Sending<*>)?.slow == true,
                )
            }
        }

        override fun edit(key: String, value: FormValue) {
            scope.launch {
                if (
                    !valid() ||
                        state.value.phase !in
                            setOf(ConnectionFormPhase.Waiting, ConnectionFormPhase.Refused)
                )
                    return@launch
                val form = state.value.form ?: return@launch
                if (form.fields.none { it.key == key && !it.hidden }) return@launch
                mutable.update {
                    it.copy(
                        draft = (it.draft + (key to value)).toImmutableMap(),
                        evaluation = FormRules.evaluate(form, it.draft + (key to value)),
                    )
                }
                identity?.let { holder?.drafts?.put(it, state.value.draft) }
                persistDraft()
            }
        }

        override fun send() {
            scope.launch {
                if (!valid()) {
                    changed()
                    return@launch
                }
                val next = holder ?: return@launch
                when (val read = next.engine.refresh()) {
                    is Outcome.Failed -> {
                        mutable.update {
                            it.copy(failure = read.failure, phase = ConnectionFormPhase.Refused)
                        }
                        return@launch
                    }
                    is Outcome.Ok -> Unit
                }
                if (
                    !valid() ||
                        next.engine.pending.value
                            .firstOrNull { it.id.raw == route.formId }
                            ?.spec
                            ?.revision != revision
                ) {
                    changed()
                    return@launch
                }
                next.engine.answer(FormId(route.formId), state.value.draft)
                publish()
            }
        }

        override fun askDecline() {
            if (
                state.value.phase in setOf(ConnectionFormPhase.Waiting, ConnectionFormPhase.Refused)
            )
                mutable.update { it.copy(declineQuestion = true) }
        }

        override fun decline() {
            scope.launch {
                if (!valid() || !state.value.declineQuestion) return@launch
                mutable.update { it.copy(declineQuestion = false) }
                val next = holder ?: return@launch
                when (val read = next.engine.refresh()) {
                    is Outcome.Failed -> {
                        mutable.update { it.copy(failure = read.failure) }
                        return@launch
                    }
                    is Outcome.Ok -> Unit
                }
                if (
                    !valid() ||
                        next.engine.pending.value
                            .firstOrNull { it.id.raw == route.formId }
                            ?.spec
                            ?.revision != revision
                ) {
                    changed()
                    return@launch
                }
                next.engine.decline(FormId(route.formId))
                publish()
            }
        }

        override fun dismiss() {
            pendingLink = null
            mutable.update { it.copy(declineQuestion = false, linkHost = null) }
        }

        override fun undo() {
            scope.launch {
                if (valid()) {
                    holder?.engine?.undo(FormId(route.formId))
                    publish()
                }
            }
        }

        override fun retry() {
            scope.launch { load() }
        }

        override fun openLink(key: String) {
            scope.launch {
                if (!valid()) return@launch
                val url =
                    state.value.form
                        ?.fields
                        ?.firstOrNull { it.key == key && it.kind == FieldKind.ExternalLink }
                        ?.url ?: return@launch
                when (val decision = policy.check(url, LinkOrigin.ServerContent)) {
                    is LinkDecision.Open -> opener.open(decision.link)
                    is LinkDecision.AskFirst -> {
                        pendingLink = decision.link
                        mutable.update { it.copy(linkHost = decision.shownHost) }
                    }
                    is LinkDecision.Refuse ->
                        mutable.update {
                            it.copy(failure = Failure.Refused(RefusalReason.StaleScope))
                        }
                }
            }
        }

        override fun approveLink() {
            scope.launch {
                val link = pendingLink
                dismiss()
                if (valid() && link != null) opener.open(link)
            }
        }

        override fun close() {
            scope.launch {
                persistDraft()
                watch?.cancel()
                locationWatch?.cancel()
                synchronized(sessions) { sessions.remove(this@Session) }
            }
        }

        private fun readDraft(form: FormSpec, key: String): FormDraft {
            val root = draftRoot() ?: return FormRules.initialDraft(form)
            val saved = root[key] as? JsonObject ?: return FormRules.initialDraft(form)
            val allowed =
                form.fields
                    .filter { it.kind == FieldKind.Multiline && !FormRules.isSensitive(it) }
                    .map { it.key }
                    .toSet()
            return (FormRules.initialDraft(form) +
                    saved
                        .filterKeys { it in allowed }
                        .mapNotNull { (field, value) ->
                            (value as? JsonPrimitive)
                                ?.takeIf { it.isString }
                                ?.content
                                ?.let { field to FormValue.Text(it) }
                        }
                        .toMap())
                .toImmutableMap()
        }

        private fun draftRoot(): JsonObject? {
            val value =
                stores.prefs.string("oc.formDrafts.${route.profile.raw}")
                    ?: return JsonObject(emptyMap())
            return try {
                Json.parseToJsonElement(value) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private suspend fun persistDraft() {
            if (!valid()) return
            val form = state.value.form ?: return
            val key = identity ?: return
            if (
                state.value.phase in
                    setOf(ConnectionFormPhase.Settled, ConnectionFormPhase.AnsweredElsewhere)
            )
                return
            val root = draftRoot() ?: return
            val persisted =
                form.fields
                    .filter { it.kind == FieldKind.Multiline && !FormRules.isSensitive(it) }
                    .mapNotNull { field ->
                        (state.value.draft[field.key] as? FormValue.Text)?.let {
                            field.key to JsonPrimitive(it.text)
                        }
                    }
                    .toMap()
            val result =
                stores.prefs.commit(stores.lanes.forProfile(route.profile)) {
                    putString(
                        "oc.formDrafts.${route.profile.raw}",
                        JsonObject(root + (key to JsonObject(persisted))).toString(),
                    )
                }
            if (result is Outcome.Failed) mutable.update { it.copy(failure = result.failure) }
        }

        private fun clearDraft() {
            val key = identity ?: return
            holder?.drafts?.clear(key)
            scope.launch {
                if (!valid()) return@launch
                val root = draftRoot() ?: return@launch
                val remaining = root - key
                stores.prefs.commit(stores.lanes.forProfile(route.profile)) {
                    if (remaining.isEmpty()) remove("oc.formDrafts.${route.profile.raw}")
                    else
                        putString(
                            "oc.formDrafts.${route.profile.raw}",
                            JsonObject(remaining).toString(),
                        )
                }
            }
        }
    }

    companion object {
        fun register(scope: ProfileScope) {
            scope.register(
                KeyShape(
                    "oc.formDrafts.",
                    KeyScope.Profile,
                    PrefType.Str,
                    "connection-forms",
                    "Non-sensitive multiline form answers until sent or declined",
                )
            )
        }
    }
}
