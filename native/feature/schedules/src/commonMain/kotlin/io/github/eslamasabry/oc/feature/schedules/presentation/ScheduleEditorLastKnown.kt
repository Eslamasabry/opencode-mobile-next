package io.github.eslamasabry.oc.feature.schedules.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.schedules.MissedRunPolicy
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the editor opens at once, on the fields and model choices it last showed for
 * this schedule (or for a new one), and the live server facts replace them in place.
 */
public interface ScheduleEditorLastKnown {
    public fun peek(profile: ProfileId, item: String): KeptScheduleEditor?

    public suspend fun read(profile: ProfileId, item: String): KeptScheduleEditor?

    public suspend fun keep(profile: ProfileId, item: String, kept: KeptScheduleEditor)
}

/** The editor's display facts for one schedule (or for a new one): fields, model choices, last run. */
@Serializable
public data class KeptScheduleEditor(
    val prompt: String = "",
    val project: String = "",
    val kind: String = RecurrenceKind.Once.wire,
    val time: String = "09:00",
    val date: String = "",
    val hours: String = "3",
    val thisConversation: Boolean = true,
    val missed: String = MissedRunPolicy.CatchUp.wire,
    val model: Model? = null,
    val models: List<Model> = emptyList(),
    val conversationTitle: String? = null,
    val continues: Boolean = false,
    val lastStatus: String? = null,
    val lastErrorCode: String? = null,
    val lastAtMs: Long? = null,
    val lastSessionId: String? = null,
    val lastLate: Boolean = false,
) {
    @Serializable
    public data class Model(val providerId: String, val modelId: String, val label: String) {
        internal fun toChoice(): ModelChoice = ModelChoice(providerId, modelId, label)
    }

    internal fun fields(): EditorFields =
        EditorFields(
            prompt = prompt,
            project = project,
            kind = RecurrenceKind.of(kind) ?: RecurrenceKind.Once,
            time = time,
            date = date,
            hours = hours,
            thisConversation = thisConversation,
            missed = MissedRunPolicy.of(missed) ?: MissedRunPolicy.CatchUp,
            model = model?.toChoice(),
        )

    /** The first frame: the editor Ready on the kept fields, the live facts still on their way. */
    internal fun frame(editing: Boolean, nowMs: Long): ScheduleEditUi =
        ScheduleEditUi(
            phase = EditPhase.Ready,
            editing = editing,
            fields = fields(),
            seed = 1,
            models = models.map { it.toChoice() }.toImmutableList(),
            conversationTitle = conversationTitle,
            continuesConversation = continues,
            canChooseConversation = continues,
            nowMs = nowMs,
            lastRun =
                RunStatus.of(lastStatus.orEmpty())?.let {
                    LastRunUi(it, lastErrorCode, lastAtMs ?: 0L, lastSessionId, lastLate)
                },
            updating = true,
        )

    public companion object {
        public const val NEW: String = "@new"
        private const val MAX_PROMPT: Int = 4_000
        private const val MAX_MODELS: Int = 60

        private fun ModelChoice.kept() = Model(providerId, modelId, label)

        internal fun of(ui: ScheduleEditUi): KeptScheduleEditor {
            val f = ui.fields
            return KeptScheduleEditor(
                prompt = f.prompt.take(MAX_PROMPT),
                project = f.project,
                kind = f.kind.wire,
                time = f.time,
                date = f.date,
                hours = f.hours,
                thisConversation = f.thisConversation,
                missed = f.missed.wire,
                model = f.model?.kept(),
                models = ui.models.take(MAX_MODELS).map { it.kept() },
                conversationTitle = ui.conversationTitle,
                continues = ui.continuesConversation,
                lastStatus = ui.lastRun?.status?.wire,
                lastErrorCode = ui.lastRun?.errorCode,
                lastAtMs = ui.lastRun?.atMs,
                lastSessionId = ui.lastRun?.sessionId,
                lastLate = ui.lastRun?.late ?: false,
            )
        }
    }
}
