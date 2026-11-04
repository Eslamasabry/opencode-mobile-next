package io.github.eslamasabry.oc.feature.widgetpanels.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.routes.WidgetPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class WidgetPanelData(
    val background: String = "solid",
    val accent: String = "wallpaper",
    val project: String = "",
    val projects: List<String> = emptyList(),
    val steerMode: String = "",
    val deliveryUncertain: Boolean = false,
)

public enum class WidgetPanelProblem {
    Stale,
    Unavailable,
    Unsupported,
    Busy,
    Invalid,
    Uncertain,
    DictationUnavailable,
}

public enum class WidgetComposePhase {
    Ready,
    Dictating,
    Sending,
    Sent,
}

public data class WidgetSubmission(val accepted: Boolean, val problem: WidgetPanelProblem? = null)

/**
 * Activity-scoped operations; private routing and drafts never enter a route or persistent UI
 * state.
 */
public interface WidgetPanelHost {
    public suspend fun load(): Outcome<WidgetPanelData>

    public suspend fun saveStyle(background: String, accent: String): Outcome<Unit>

    public suspend fun saveProject(project: String): Outcome<Unit>

    public suspend fun submit(text: String): WidgetSubmission

    public suspend fun dictate(): Outcome<String>

    public fun close()
}

public data class WidgetPanelUi(
    val route: WidgetPanel,
    val loading: Boolean = true,
    val background: String = "solid",
    val accent: String = "wallpaper",
    val project: String = "",
    val projects: List<String> = emptyList(),
    val previewSize: String = "card",
    val steerMode: String = "",
    val phase: WidgetComposePhase = WidgetComposePhase.Ready,
    val saving: Boolean = false,
    val problem: WidgetPanelProblem? = null,
    val uncertain: Boolean = false,
    /** Only dictated additions. The platform field owns the draft and IME composition. */
    val dictated: String? = null,
) {
    public val composeBlocked: Boolean
        get() =
            loading ||
                uncertain ||
                (route is WidgetPanel.Steer &&
                    !route.voice &&
                    steerMode !in setOf("steer", "afterReply"))
}

public interface WidgetPanelActions {
    public fun close()

    public fun chooseBackground(id: String)

    public fun chooseAccent(id: String)

    public fun choosePreviewSize(id: String)

    public fun saveStyle()

    public fun chooseProject(project: String)

    public fun dictate()

    public fun consumeDictation()

    public fun send(text: String)
}

/**
 * One panel per launch. Failed saves preserve choices; uncertain delivery permanently fences this
 * launch.
 */
public class WidgetPanelViewModel(
    private val route: WidgetPanel,
    private val host: WidgetPanelHost,
) : ViewModel(), WidgetPanelActions {
    private val mutable = MutableStateFlow(WidgetPanelUi(route))
    public val state: StateFlow<WidgetPanelUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            when (val read = host.load()) {
                is Outcome.Failed ->
                    mutable.update { it.copy(loading = false, problem = problem(read.failure)) }
                is Outcome.Ok -> {
                    val data = read.value
                    mutable.update {
                        it.copy(
                            loading = false,
                            background = data.background,
                            accent = data.accent,
                            project = data.project,
                            projects = data.projects,
                            steerMode = data.steerMode,
                            uncertain = data.deliveryUncertain,
                            problem =
                                if (data.deliveryUncertain) WidgetPanelProblem.Uncertain else null,
                        )
                    }
                    if (route is WidgetPanel.Steer && route.voice) dictate()
                }
            }
        }
    }

    override fun close(): Unit = host.close()

    override fun chooseBackground(id: String) {
        if (!mutable.value.saving && id in setOf("solid", "tinted", "transparent"))
            mutable.update { it.copy(background = id, problem = null) }
    }

    override fun chooseAccent(id: String) {
        if (
            !mutable.value.saving &&
                id in setOf("wallpaper", "app", "green", "teal", "blue", "amber")
        )
            mutable.update { it.copy(accent = id, problem = null) }
    }

    override fun choosePreviewSize(id: String) {
        if (id in setOf("compact", "card", "list")) mutable.update { it.copy(previewSize = id) }
    }

    override fun saveStyle() {
        val current = mutable.value
        if (route !is WidgetPanel.Style || current.loading || current.saving) return
        mutable.update { it.copy(saving = true, problem = null) }
        viewModelScope.launch {
            when (val saved = host.saveStyle(current.background, current.accent)) {
                is Outcome.Ok -> host.close()
                is Outcome.Failed ->
                    mutable.update { it.copy(saving = false, problem = problem(saved.failure)) }
            }
        }
    }

    override fun chooseProject(project: String) {
        val current = mutable.value
        if (
            route !is WidgetPanel.Project ||
                current.loading ||
                current.saving ||
                (project.isNotEmpty() && project !in current.projects)
        )
            return
        mutable.update { it.copy(saving = true, project = project, problem = null) }
        viewModelScope.launch {
            when (val saved = host.saveProject(project)) {
                is Outcome.Ok -> host.close()
                is Outcome.Failed ->
                    mutable.update { it.copy(saving = false, problem = problem(saved.failure)) }
            }
        }
    }

    override fun dictate() {
        val current = mutable.value
        if (current.composeBlocked || current.phase != WidgetComposePhase.Ready) return
        mutable.update { it.copy(phase = WidgetComposePhase.Dictating, problem = null) }
        viewModelScope.launch {
            when (val heard = host.dictate()) {
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            phase = WidgetComposePhase.Ready,
                            dictated = heard.value.trim().take(8000).takeIf(String::isNotEmpty),
                        )
                    }
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(
                            phase = WidgetComposePhase.Ready,
                            problem = WidgetPanelProblem.DictationUnavailable,
                        )
                    }
            }
        }
    }

    override fun consumeDictation() {
        mutable.update { it.copy(dictated = null) }
    }

    override fun send(text: String) {
        val current = mutable.value
        if (
            route !is WidgetPanel.Steer ||
                current.composeBlocked ||
                current.phase != WidgetComposePhase.Ready
        )
            return
        val body = text.trim()
        if (body.isEmpty() || body.length > 8000) {
            mutable.update { it.copy(problem = WidgetPanelProblem.Invalid) }
            return
        }
        mutable.update { it.copy(phase = WidgetComposePhase.Sending, problem = null) }
        viewModelScope.launch {
            val sent = host.submit(body)
            if (sent.accepted) {
                mutable.update { it.copy(phase = WidgetComposePhase.Sent) }
                delay(700)
                host.close()
            } else
                mutable.update {
                    it.copy(
                        phase = WidgetComposePhase.Ready,
                        problem = sent.problem ?: WidgetPanelProblem.Unavailable,
                        uncertain = sent.problem == WidgetPanelProblem.Uncertain,
                    )
                }
        }
    }

    private fun problem(failure: Failure): WidgetPanelProblem =
        if (failure is Failure.Refused) WidgetPanelProblem.Stale else WidgetPanelProblem.Unavailable
}
