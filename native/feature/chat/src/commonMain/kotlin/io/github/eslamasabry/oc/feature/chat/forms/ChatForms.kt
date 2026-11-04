package io.github.eslamasabry.oc.feature.chat.forms

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.requests.ConnectionRequests
import io.github.eslamasabry.oc.engine.requests.forms.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.forms.FieldKind
import io.github.eslamasabry.oc.engine.requests.forms.FormAnswerResult
import io.github.eslamasabry.oc.engine.requests.forms.FormDraft
import io.github.eslamasabry.oc.engine.requests.forms.FormNotice
import io.github.eslamasabry.oc.engine.requests.forms.FormReply
import io.github.eslamasabry.oc.engine.requests.forms.FormRevision
import io.github.eslamasabry.oc.engine.requests.forms.FormRules
import io.github.eslamasabry.oc.engine.requests.forms.FormValue
import io.github.eslamasabry.oc.engine.requests.forms.PendingForm
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Only non-sensitive multiline draft fields reach disk. Keys include the full source and schema
 * identity.
 */
public interface FormDraftStorage {
    public suspend fun read(profile: ProfileId, identity: String): Outcome<Map<String, String>>

    public suspend fun write(
        profile: ProfileId,
        identity: String,
        values: Map<String, String>,
    ): Outcome<Unit>
}

@ContributesTo(AppScope::class)
public interface FormDraftBindings {
    @Multibinds(allowEmpty = true) public val formDraftStores: Set<FormDraftStorage>
}

public data class ChatFormUi(
    val form: PendingForm? = null,
    val phase: AnswerPhase<FormReply>? = null,
    val draft: FormDraft = persistentMapOf(),
    val open: Boolean = false,
    val confirmDecline: Boolean = false,
    val ready: Boolean = false,
    val failure: Failure? = null,
    val notice: FormNotice? = null,
)

@SingleIn(AppScope::class)
@Inject
public class ChatForms(private val registry: ConnectionRegistry, stores: Set<FormDraftStorage>) {
    private val storage = stores.singleOrNull()

    public fun open(ref: ConversationRef): ChatFormsModel = ChatFormsModel(ref, registry, storage)
}

/**
 * UI state only; graph-owned FormRequests owns pending asks, answer holds and the transport
 * mutation.
 */
public class ChatFormsModel
internal constructor(
    private val ref: ConversationRef,
    private val registry: ConnectionRegistry,
    private val storage: FormDraftStorage?,
) {
    private val scope = CoroutineScope(registry.appScope.coroutineContext + registry.mainContext)
    private val mutable = MutableStateFlow(ChatFormUi())
    public val state: StateFlow<ChatFormUi> = mutable.asStateFlow()
    private var bound: ConnectionGraph? = null
    private var source: Any? = null
    private var connection: ConnectionRequests? = null
    private val jobs = ArrayList<Job>()
    private var sheet: PendingForm? = null
    private var sheetIdentity: String? = null
    private var draft: FormDraft = persistentMapOf()
    private var ready = false
    private var failure: Failure? = null
    private var confirm = false
    private var closed = false
    private val writes = Mutex()
    private var known = emptyMap<String, PendingForm>()
    private val memory = HashMap<String, FormDraft>()
    private val watcher = scope.launch {
        registry.live.collect {
            val graph = registry.graphFor(ref)
            if (graph !== bound) bind(graph)
        }
    }

    private fun current(): Boolean =
        !closed &&
            bound != null &&
            registry.graphFor(ref) === bound &&
            bound?.session?.server?.location?.value == source

    private fun matches(a: PendingForm, b: PendingForm): Boolean =
        a.id == b.id && a.wire.sessionId == b.wire.sessionId && a.spec.revision == b.spec.revision

    private fun forms(): List<PendingForm> =
        if (!current() || bound?.session?.server?.capabilities?.value?.forms != true) emptyList()
        else
            connection?.forms?.pending?.value.orEmpty().filter {
                it.sessionId == ref.sessionId || it.sessionId == null
            }

    private fun identity(form: PendingForm): String {
        val location = bound?.session?.server?.location?.value
        val schema =
            FormRevision.identityKey(
                ref.profileId.raw,
                location?.directory.orEmpty(),
                location?.workspace,
                form.wire.sessionId.raw,
                form.id.raw,
                form.spec.revision,
            )
        return DartJson.sha256Hex(
            DartJson.encode(
                listOf(schema, bound?.profile?.origin?.toString(), bound?.profile?.username)
            )
        )
    }

    private fun bind(graph: ConnectionGraph?) {
        jobs.forEach { it.cancel() }
        jobs.clear()
        connection?.watchingFormSession(ref.sessionId, false)
        bound = graph
        source = graph?.session?.server?.location?.value
        connection = (graph as? ChatGraphAccessors)?.chatRequests
        sheet = null
        sheetIdentity = null
        draft = persistentMapOf()
        ready = false
        confirm = false
        failure = null
        known = emptyMap()
        publish()
        val owner = connection ?: return
        owner.watchingFormSession(ref.sessionId, true)
        jobs += scope.launch {
            owner.forms.pending.collect { all ->
                if (!current()) {
                    publish()
                    return@collect
                }
                val next = forms().associateBy(::identity)
                // Forget a saved draft only when the ask itself left the owner, never because it
                // dropped out of this chat's filtered view.
                for ((id, form) in known - next.keys) {
                    if (all.none { matches(it, form) }) {
                        memory.remove(id)
                        save(id, emptyMap())
                    }
                }
                known = next
                publish()
            }
        }
        jobs += scope.launch { owner.forms.phases.collect { publish() } }
        jobs += scope.launch { owner.forms.notices.collect { publish() } }
        jobs += scope.launch {
            graph?.session?.server?.location?.collect {
                // A folder change rebinds to the new source instead of hiding this chat's forms
                // for good (the registry does not replace the graph for a folder change).
                if (it != source && !closed && registry.graphFor(ref) === graph) bind(graph)
                else publish()
            }
        }
    }

    private fun publish() {
        val pending = forms()
        val selected = sheet
        if (selected != null && pending.none { matches(it, selected) }) {
            sheet = null
            sheetIdentity = null
            draft = persistentMapOf()
            confirm = false
        }
        val form = pending.firstOrNull()
        mutable.value =
            ChatFormUi(
                form,
                form?.let { connection?.forms?.phases?.value?.get(it.id) },
                draft,
                sheet != null,
                confirm,
                ready,
                failure,
                connection?.forms?.notices?.value?.firstOrNull(),
            )
    }

    public fun open(id: FormId, revision: String) {
        val form = forms().firstOrNull { it.id == id && it.spec.revision == revision } ?: return
        val phase = connection?.forms?.phases?.value?.get(id)
        if (phase is AnswerPhase.Held || phase is AnswerPhase.Sending) return
        sheet = form
        val key = identity(form)
        sheetIdentity = key
        draft = memory[key] ?: FormRules.initialDraft(form.spec)
        ready = memory.containsKey(key)
        confirm = false
        failure = null
        publish()
        if (ready) return
        scope.launch {
            val saved =
                storage?.read(ref.profileId, key) ?: Outcome.Failed(Failure.StorageRefused())
            if (!current() || sheetIdentity != key || sheet?.let { matches(it, form) } != true)
                return@launch
            when (saved) {
                is Outcome.Failed -> failure = saved.failure
                is Outcome.Ok -> {
                    val fields =
                        form.spec.fields
                            .filter {
                                it.kind == FieldKind.Multiline &&
                                    !it.hidden &&
                                    !FormRules.isSensitive(it)
                            }
                            .map { it.key }
                            .toSet()
                    draft =
                        (draft +
                                saved.value
                                    .filterKeys { it in fields }
                                    .mapValues { FormValue.Text(it.value) })
                            .toImmutableMap()
                    ready = true
                }
            }
            publish()
        }
    }

    public fun edit(key: String, value: FormValue) {
        val form = sheet ?: return
        if (!current() || !ready || forms().none { matches(it, form) }) return
        if (form.spec.fields.none { it.key == key && !it.hidden }) return
        draft = (draft + (key to value)).toImmutableMap()
        sheetIdentity?.let { memory[it] = draft }
        publish()
        val stored =
            form.spec.fields
                .filter {
                    it.kind == FieldKind.Multiline && !it.hidden && !FormRules.isSensitive(it)
                }
                .mapNotNull {
                    (draft[it.key] as? FormValue.Text)?.let { value -> it.key to value.text }
                }
                .toMap()
        sheetIdentity?.let { save(it, stored) }
    }

    private fun save(identity: String, values: Map<String, String>) {
        scope.launch {
            writes.withLock {
                val result =
                    storage?.write(ref.profileId, identity, values)
                        ?: Outcome.Failed(Failure.StorageRefused())
                if (result is Outcome.Failed && !closed && sheetIdentity == identity) {
                    failure = result.failure
                    publish()
                }
            }
        }
    }

    public fun dismiss() {
        sheet = null
        sheetIdentity = null
        confirm = false
        publish()
    }

    public fun send() {
        val form = sheet ?: return
        if (!ready || !FormRules.evaluate(form.spec, draft).canSend) return
        decide(form, draft, decline = false)
    }

    public fun askDecline() {
        if (sheet != null && current()) {
            confirm = true
            publish()
        }
    }

    public fun cancelDecline() {
        confirm = false
        publish()
    }

    public fun confirmDecline() {
        val form = sheet ?: return
        confirm = false
        decide(form, draft, decline = true)
    }

    private fun decide(form: PendingForm, values: FormDraft, decline: Boolean) {
        if (!current()) return
        val graph = bound
        val expectedSource = source
        val owner = connection ?: return
        owner.formAction { engine ->
            if (
                registry.graphFor(ref) !== graph ||
                    graph?.session?.server?.location?.value != expectedSource
            )
                return@formAction
            val present =
                engine.pending.value.firstOrNull { matches(it, form) } ?: return@formAction
            val answer =
                if (decline) engine.decline(present.id) else engine.answer(present.id, values)
            if (answer == FormAnswerResult.Held) scope.launch { dismiss() }
        }
    }

    public fun undo(id: FormId, revision: String) {
        val owner = connection ?: return
        if (forms().none { it.id == id && it.spec.revision == revision }) return
        owner.formAction {
            if (
                current() && it.pending.value.any { f -> f.id == id && f.spec.revision == revision }
            )
                it.undo(id)
        }
    }

    public fun acknowledgeNotice() {
        val notice = state.value.notice ?: return
        connection?.formAction { it.acknowledge(notice) }
    }

    public fun close() {
        if (closed) return
        connection?.formAction { it.flushHeld(ref.sessionId) }
        closed = true
        watcher.cancel()
        jobs.forEach { it.cancel() }
        jobs.clear()
        connection?.watchingFormSession(ref.sessionId, false)
        connection = null
        bound = null
        sheet = null
        draft = persistentMapOf()
        mutable.value = ChatFormUi()
    }
}
