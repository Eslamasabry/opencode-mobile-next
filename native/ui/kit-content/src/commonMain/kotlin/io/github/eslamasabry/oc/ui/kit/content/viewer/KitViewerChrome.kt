package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/** One tool a text document offers its page: Copy contents, Find, Wrap lines, Show source. */
internal data class ViewerTool(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /** Non-null for a toggle: its state, drawn as a check in the menu. */
    val checked: Boolean?,
)

/**
 * What a document body tells the frame it sits in: its tools, so the frame can put them in the page's top bar and
 * menu instead of the body painting its own rows. [tools] is what the bar draws; the callbacks are kept apart so a new
 * lambda does not redraw the bar. A body with no frame around it (no chrome) keeps drawing its own tools.
 */
internal class ViewerChrome {
    var tools: List<ViewerTool> by mutableStateOf(emptyList())

    /** The document's Find bar is open: the frame steps its bottom button aside, so the text keeps the room. */
    var finding: Boolean by mutableStateOf(false)
    private var calls: Map<String, () -> Unit> = emptyMap()

    fun offer(tools: List<ViewerTool>, calls: Map<String, () -> Unit>) {
        this.calls = calls
        if (this.tools != tools) this.tools = tools
    }

    fun clear() {
        calls = emptyMap()
        tools = emptyList()
        finding = false
    }

    fun run(id: String) {
        calls[id]?.invoke()
    }
}

internal val LocalViewerChrome = staticCompositionLocalOf<ViewerChrome?> { null }
