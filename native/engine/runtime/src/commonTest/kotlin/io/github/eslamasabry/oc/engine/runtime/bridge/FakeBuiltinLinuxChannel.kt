package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/** The native side of the built-in Linux channel in memory: records every call and answers with [answer]. */
class FakeBuiltinLinuxChannel : BuiltinLinuxChannel {
    data class Call(val method: String, val arguments: Map<String, Any?>?)

    val calls = mutableListOf<Call>()
    var answer: (Call) -> ChannelReply = { ChannelReply.Value(null) }

    /** Holds every `status` call until completed. */
    var statusGate: CompletableDeferred<Unit>? = null

    /** Every call hangs until cancelled (a stalled native side). */
    var stalls = false

    val methods: List<String> get() = calls.map { it.method }

    override suspend fun invoke(method: String, arguments: Map<String, Any?>?): ChannelReply {
        val call = Call(method, arguments)
        calls += call
        if (method == "status") statusGate?.await()
        if (stalls) awaitCancellation()
        return answer(call)
    }

    fun answerValue(value: Any?) {
        answer = { ChannelReply.Value(value) }
    }

    fun answerError(code: String, details: String? = null) {
        answer = { ChannelReply.Error(code, details?.let(::redacted)) }
    }
}

@OptIn(RedactorApi::class)
fun redacted(text: String): Redacted = Redacted(text)
