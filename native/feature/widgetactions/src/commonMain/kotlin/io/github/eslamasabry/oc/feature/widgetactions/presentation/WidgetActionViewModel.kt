package io.github.eslamasabry.oc.feature.widgetactions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public enum class WidgetActionPhase {
    Editing,
    Sending,
    Accepted,
    Rejected,
    Uncertain,
}

public enum class WidgetActionDestination {
    Close,
    Conversation,
}

public data class WidgetActionUi(
    val phase: WidgetActionPhase = WidgetActionPhase.Editing,
    val destination: WidgetActionDestination? = null,
)

/**
 * Text lives only in the open field and this one call; it is never saved in a widget or navigation
 * extra.
 */
public fun interface WidgetActionPort {
    public suspend fun send(text: String): WidgetActionPhase
}

public interface WidgetActionActions {
    public fun send(text: String)

    public fun close()

    public fun openConversation()
}

public class WidgetActionViewModel(private val port: WidgetActionPort) :
    ViewModel(), WidgetActionActions {
    private val mutable = MutableStateFlow(WidgetActionUi())
    public val state: StateFlow<WidgetActionUi> = mutable.asStateFlow()

    override fun send(text: String) {
        if (
            mutable.value.phase != WidgetActionPhase.Editing ||
                text.isBlank() ||
                text.length > MAX_TEXT
        )
            return
        mutable.value = WidgetActionUi(WidgetActionPhase.Sending)
        viewModelScope.launch {
            val result = port.send(text)
            mutable.value =
                mutable.value.copy(
                    phase =
                        if (result in setOf(WidgetActionPhase.Accepted, WidgetActionPhase.Rejected))
                            result
                        else WidgetActionPhase.Uncertain
                )
        }
    }

    override fun close() {
        mutable.value = mutable.value.copy(destination = WidgetActionDestination.Close)
    }

    override fun openConversation() {
        mutable.value = mutable.value.copy(destination = WidgetActionDestination.Conversation)
    }

    public fun acknowledgeDestination() {
        mutable.value = mutable.value.copy(destination = null)
    }

    public companion object {
        public const val MAX_TEXT: Int = 8000
    }
}
