package io.github.eslamasabry.oc.ui.shell.widgets.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class WidgetComposeLaunch(val available: Boolean, val steerMode: String)
public enum class WidgetComposePhase { Ready, Dictating, Sending, Sent, Uncertain }
public enum class WidgetComposeNotice { Stale, Unavailable, Unsupported, Busy, Invalid, Uncertain, DictationUnavailable }
public data class WidgetComposeResult(val accepted: Boolean = false, val uncertain: Boolean = false, val notice: WidgetComposeNotice? = null)

public interface WidgetComposeSource {
    public suspend fun inspect(): WidgetComposeLaunch
    public suspend fun submit(text: String): WidgetComposeResult
}

public data class WidgetComposeUi(
    val voice: Boolean,
    val loading: Boolean = true,
    val available: Boolean = false,
    val steerMode: String = "unknown",
    val text: String = "",
    val phase: WidgetComposePhase = WidgetComposePhase.Ready,
    val notice: WidgetComposeNotice? = null,
    /** A state request acknowledged by the platform host before it starts the recognizer. */
    val dictationRequested: Boolean = false,
    val finished: Boolean = false,
) {
    override fun toString(): String = "WidgetComposeUi(voice=$voice, phase=$phase, textLength=${text.length})"

    public val canEdit: Boolean get() = !loading && phase == WidgetComposePhase.Ready
    public val canSend: Boolean get() = canEdit && available && (voice || steerMode in setOf("steer", "afterReply"))
    public val mayHaveSent: Boolean get() = phase in setOf(WidgetComposePhase.Sending, WidgetComposePhase.Sent, WidgetComposePhase.Uncertain)
}

public interface WidgetComposeActions {
    public fun close()
    public fun edited(text: String)
    public fun dictate()
    public fun dictationLaunched()
    public fun dictated(text: String?, unavailable: Boolean = false)
    public fun send()
}

/**
 * A draft is process-local and only an explicit Send submits it. An interrupted/uncertain submission permanently
 * closes this box's send admission. Recreating the Android host restores that fact without persisting draft text.
 */
public class WidgetComposeViewModel(
    private val source: WidgetComposeSource,
    voice: Boolean,
    restoredMayHaveSent: Boolean = false,
) : ViewModel(), WidgetComposeActions {
    private val mutable = MutableStateFlow(WidgetComposeUi(voice = voice))
    public val state: StateFlow<WidgetComposeUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            if (restoredMayHaveSent) {
                mutable.update { it.copy(loading = false, phase = WidgetComposePhase.Uncertain, notice = WidgetComposeNotice.Uncertain) }
            } else {
                val launch = source.inspect()
                mutable.update {
                    it.copy(loading = false, available = launch.available, steerMode = launch.steerMode,
                        notice = if (launch.available) null else WidgetComposeNotice.Stale)
                }
                if (voice && launch.available) dictate()
            }
        }
    }

    override fun close() {
        if (mutable.value.phase != WidgetComposePhase.Sending) mutable.update { it.copy(finished = true, dictationRequested = false) }
    }

    override fun edited(text: String) {
        if (mutable.value.canEdit) mutable.update { it.copy(text = text.take(8000)) }
    }

    override fun dictate() {
        if (!mutable.value.canSend) return
        mutable.update { it.copy(phase = WidgetComposePhase.Dictating, notice = null) }
        viewModelScope.launch {
            val fresh = source.inspect()
            if (mutable.value.finished || mutable.value.phase != WidgetComposePhase.Dictating) return@launch
            mutable.update {
                if (fresh.available) it.copy(dictationRequested = true)
                else it.copy(phase = WidgetComposePhase.Ready, available = false, notice = WidgetComposeNotice.Stale)
            }
        }
    }

    override fun dictationLaunched(): Unit = mutable.update { it.copy(dictationRequested = false) }

    override fun dictated(text: String?, unavailable: Boolean) {
        if (mutable.value.phase != WidgetComposePhase.Dictating) return
        mutable.update {
            val heard = text.orEmpty().trim()
            val joined = if (heard.isEmpty()) it.text else if (it.text.isBlank()) heard else "${it.text.trimEnd()} $heard"
            it.copy(phase = WidgetComposePhase.Ready, text = joined.take(8000), dictationRequested = false,
                notice = if (unavailable) WidgetComposeNotice.DictationUnavailable else null)
        }
    }

    override fun send() {
        val old = mutable.value
        if (!old.canSend) return
        val text = old.text.trim()
        if (text.isEmpty()) {
            mutable.update { it.copy(notice = WidgetComposeNotice.Invalid) }
            return
        }
        mutable.update { it.copy(phase = WidgetComposePhase.Sending, notice = null) }
        viewModelScope.launch {
            val result = source.submit(text)
            when {
                result.accepted -> {
                    mutable.update { it.copy(phase = WidgetComposePhase.Sent) }
                    delay(700)
                    mutable.update { it.copy(finished = true) }
                }
                result.uncertain -> mutable.update { it.copy(phase = WidgetComposePhase.Uncertain, notice = WidgetComposeNotice.Uncertain) }
                else -> mutable.update { it.copy(phase = WidgetComposePhase.Ready, notice = result.notice ?: WidgetComposeNotice.Unavailable) }
            }
        }
    }
}
