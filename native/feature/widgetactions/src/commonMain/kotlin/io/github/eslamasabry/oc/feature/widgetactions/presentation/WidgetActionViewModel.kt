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
    val newConversation: Boolean = false,
    val listening: Boolean = false,
    val dictating: Boolean = false,
    val dictatedWords: String? = null,
    val dictationFailed: Boolean = false,
    val voiceAvailable: Boolean = false,
)

/**
 * Text lives only in the open field and this one call; it is never saved in a widget or navigation
 * extra.
 */
public fun interface WidgetActionPort {
    public suspend fun send(text: String): WidgetActionPhase
}

public interface WidgetVoicePort {
    public fun foreground(active: Boolean)

    public suspend fun start(): Boolean

    public suspend fun finish(): String?

    public suspend fun cancel()
}

public interface WidgetActionActions {
    public fun dictate()

    public fun finishDictation()

    public fun takeDictatedWords()

    public fun send(text: String)

    public fun close()

    public fun openConversation()
}

public class WidgetActionViewModel(
    private val port: WidgetActionPort,
    private val voice: WidgetVoicePort? = null,
    newConversation: Boolean = false,
) : ViewModel(), WidgetActionActions {
    private val mutable =
        MutableStateFlow(
            WidgetActionUi(newConversation = newConversation, voiceAvailable = voice != null)
        )
    public val state: StateFlow<WidgetActionUi> = mutable.asStateFlow()

    public fun foreground(active: Boolean) {
        voice?.foreground(active)
    }

    override fun dictate() {
        if (
            voice == null ||
                mutable.value.phase != WidgetActionPhase.Editing ||
                mutable.value.listening ||
                mutable.value.dictating
        )
            return
        mutable.value = mutable.value.copy(dictating = true, dictationFailed = false)
        viewModelScope.launch {
            val started = voice.start()
            mutable.value =
                mutable.value.copy(
                    dictating = false,
                    listening = started,
                    dictationFailed = !started,
                )
        }
    }

    override fun finishDictation() {
        if (voice == null || !mutable.value.listening || mutable.value.dictating) return
        mutable.value = mutable.value.copy(dictating = true)
        viewModelScope.launch {
            val words = voice.finish()
            mutable.value =
                mutable.value.copy(
                    dictating = false,
                    listening = false,
                    dictatedWords = words,
                    dictationFailed = words == null,
                )
        }
    }

    override fun takeDictatedWords() {
        mutable.value = mutable.value.copy(dictatedWords = null)
    }

    override fun send(text: String) {
        if (
            mutable.value.phase != WidgetActionPhase.Editing ||
                mutable.value.listening ||
                mutable.value.dictating ||
                text.isBlank() ||
                text.length > MAX_TEXT
        )
            return
        mutable.value = mutable.value.copy(phase = WidgetActionPhase.Sending)
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
        if (voice != null) viewModelScope.launch { voice.cancel() }
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
