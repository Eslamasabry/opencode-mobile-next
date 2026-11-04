package io.github.eslamasabry.oc.engine.termuxhost

/** An `oc/termux` channel that records calls and answers through [handler] (null: no handler registered). */
internal class FakeTermuxChannel(var handler: (suspend (String, Map<String, Any?>?) -> ChannelReply)? = null) :
    TermuxChannel {
    val calls = mutableListOf<Pair<String, Map<String, Any?>?>>()

    override suspend fun invoke(method: String, arguments: Map<String, Any?>?): ChannelReply {
        calls += method to arguments
        return handler?.invoke(method, arguments) ?: ChannelReply.MissingHandler
    }
}

internal class RecordingWait : TermuxRetryWait {
    val pauses = mutableListOf<Long>()

    override suspend fun pause(millis: Long) {
        pauses += millis
    }
}
