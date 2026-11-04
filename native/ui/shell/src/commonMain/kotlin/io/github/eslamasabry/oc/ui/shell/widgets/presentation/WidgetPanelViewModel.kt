package io.github.eslamasabry.oc.ui.shell.widgets.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public enum class WidgetPanelMode { Style, Project, Unavailable }
public enum class WidgetPanelProblem { Stale, Unavailable }

/** An opaque acknowledged style revision, never a conversation dispatch target. */
public data class WidgetPanelSnapshot(
    val widgetId: Int,
    val owner: String,
    val scope: String,
    val background: String,
    val accent: String,
    val project: String,
    val projects: List<String>,
    val revision: String?,
)

public data class WidgetPanelPalette(val background: Int, val text: Int, val muted: Int, val accent: Int)

public interface WidgetPanelSource {
    public fun palette(background: String, accent: String): WidgetPanelPalette
    public suspend fun load(widgetId: Int): Outcome<WidgetPanelSnapshot>
    public suspend fun saveStyle(snapshot: WidgetPanelSnapshot, background: String, accent: String): Outcome<Unit>
    public suspend fun saveProject(snapshot: WidgetPanelSnapshot, project: String): Outcome<Unit>
}

public data class WidgetPanelUi(
    val mode: WidgetPanelMode,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val background: String = "solid",
    val accent: String = "wallpaper",
    val project: String = "",
    val projects: List<String> = emptyList(),
    val widePreview: Boolean = false,
    val palette: WidgetPanelPalette? = null,
    val problem: WidgetPanelProblem? = null,
    val saved: Boolean = false,
    val dismissed: Boolean = false,
)

public interface WidgetPanelActions {
    public fun close()
    public fun background(value: String)
    public fun accent(value: String)
    public fun preview(wide: Boolean)
    public fun saveStyle()
    public fun project(value: String)
}

/** Cosmetic saves keep their owning widget and profile; they never connect a server or dispatch a prompt. */
public class WidgetPanelViewModel(
    private val source: WidgetPanelSource,
    private val widgetId: Int,
    mode: WidgetPanelMode,
) : ViewModel(), WidgetPanelActions {
    private val mutable = MutableStateFlow(WidgetPanelUi(mode = mode))
    public val state: StateFlow<WidgetPanelUi> = mutable.asStateFlow()
    private var snapshot: WidgetPanelSnapshot? = null

    init {
        viewModelScope.launch {
            if (mode == WidgetPanelMode.Unavailable) {
                mutable.update { it.copy(loading = false, problem = WidgetPanelProblem.Unavailable) }
            } else when (val loaded = source.load(widgetId)) {
                is Outcome.Failed -> mutable.update { it.copy(loading = false, problem = problem(loaded.failure)) }
                is Outcome.Ok -> {
                    snapshot = loaded.value
                    mutable.update {
                        it.copy(
                            loading = false,
                            background = loaded.value.background,
                            accent = loaded.value.accent,
                            project = loaded.value.project,
                            projects = loaded.value.projects,
                            palette = source.palette(loaded.value.background, loaded.value.accent),
                        )
                    }
                }
            }
        }
    }

    /** Re-resolve the real renderer palette after an Appearance or system brightness change. */
    public fun refreshPalette() {
        if (!mutable.value.loading) mutable.update { it.copy(palette = source.palette(it.background, it.accent)) }
    }

    override fun close() {
        if (!mutable.value.saving) mutable.update { it.copy(dismissed = true) }
    }

    override fun background(value: String) {
        if (!mutable.value.saving && value in BACKGROUNDS) mutable.update { it.copy(background = value, palette = source.palette(value, it.accent)) }
    }

    override fun accent(value: String) {
        if (!mutable.value.saving && value in ACCENTS) mutable.update { it.copy(accent = value, palette = source.palette(it.background, value)) }
    }

    override fun preview(wide: Boolean) { mutable.update { it.copy(widePreview = wide) } }

    override fun saveStyle() {
        val old = snapshot ?: return
        val now = mutable.value
        if (now.loading || now.saving || now.problem == WidgetPanelProblem.Stale || now.mode != WidgetPanelMode.Style) return
        save { source.saveStyle(old, now.background, now.accent) }
    }

    override fun project(value: String) {
        val old = snapshot ?: return
        val now = mutable.value
        if (now.loading || now.saving || now.problem == WidgetPanelProblem.Stale || now.mode != WidgetPanelMode.Project) return
        if (value.isNotEmpty() && value !in now.projects) return
        mutable.update { it.copy(project = value) }
        save { source.saveProject(old, value) }
    }

    private fun save(command: suspend () -> Outcome<Unit>) {
        mutable.update { it.copy(saving = true, problem = null) }
        viewModelScope.launch {
            when (val result = command()) {
                is Outcome.Ok -> mutable.update { it.copy(saving = false, saved = true) }
                is Outcome.Failed -> mutable.update { it.copy(saving = false, problem = problem(result.failure)) }
            }
        }
    }

    private fun problem(failure: Failure): WidgetPanelProblem =
        if (failure is Failure.Refused && failure.reason == RefusalReason.StaleScope) WidgetPanelProblem.Stale
        else WidgetPanelProblem.Unavailable

    public companion object {
        public val BACKGROUNDS: Set<String> = setOf("solid", "tinted", "transparent")
        public val ACCENTS: Set<String> = setOf("wallpaper", "app", "green", "amber", "blue", "teal")
    }
}
