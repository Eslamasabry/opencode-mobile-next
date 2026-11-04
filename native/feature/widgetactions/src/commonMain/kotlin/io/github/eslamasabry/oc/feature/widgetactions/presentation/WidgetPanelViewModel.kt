package io.github.eslamasabry.oc.feature.widgetactions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public enum class WidgetPanelMode {
    Style,
    Project,
}

public data class WidgetPanelUi(
    val mode: WidgetPanelMode = WidgetPanelMode.Style,
    val background: String = "solid",
    val accent: String = "wallpaper",
    val projects: List<String> = emptyList(),
    val project: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
    val closed: Boolean = false,
    val saved: Boolean = false,
)

public fun interface WidgetPanelPort {
    public suspend fun save(state: WidgetPanelUi): Boolean
}

public interface WidgetPanelActions {
    public fun background(value: String)

    public fun accent(value: String)

    public fun project(value: String)

    public fun mode(value: WidgetPanelMode)

    public fun save()

    public fun close()
}

public class WidgetPanelViewModel(initial: WidgetPanelUi, private val port: WidgetPanelPort) :
    ViewModel(), WidgetPanelActions {
    private val mutable = MutableStateFlow(initial)
    public val state: StateFlow<WidgetPanelUi> = mutable.asStateFlow()

    override fun background(value: String) {
        if (!mutable.value.saving && value in listOf("solid", "tinted", "transparent"))
            mutable.value = mutable.value.copy(background = value, failed = false)
    }

    override fun accent(value: String) {
        if (
            !mutable.value.saving &&
                value in listOf("wallpaper", "app", "green", "teal", "blue", "amber")
        )
            mutable.value = mutable.value.copy(accent = value, failed = false)
    }

    override fun project(value: String) {
        if (!mutable.value.saving && (value.isEmpty() || value in mutable.value.projects))
            mutable.value = mutable.value.copy(project = value, failed = false)
    }

    override fun mode(value: WidgetPanelMode) {
        if (!mutable.value.saving) mutable.value = mutable.value.copy(mode = value)
    }

    override fun save() {
        if (mutable.value.saving) return
        mutable.value = mutable.value.copy(saving = true, failed = false)
        viewModelScope.launch {
            val saved = port.save(mutable.value)
            mutable.value =
                mutable.value.copy(saving = false, failed = !saved, closed = saved, saved = saved)
        }
    }

    override fun close() {
        mutable.value = mutable.value.copy(closed = true)
    }
}
