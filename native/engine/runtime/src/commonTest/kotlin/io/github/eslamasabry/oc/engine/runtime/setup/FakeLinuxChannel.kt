package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok

/** A recorded Linux channel: every call is kept, [answer] decides the reply (null reply by default). */
class FakeLinuxChannel(var answer: (method: String, arguments: Map<String, Any?>?) -> Outcome<Any?> = { _, _ -> null.ok() }) :
    LinuxChannel {
    data class Call(val method: String, val arguments: Map<String, Any?>?)

    val calls = mutableListOf<Call>()

    override suspend fun invoke(method: String, arguments: Map<String, Any?>?): Outcome<Any?> {
        calls += Call(method, arguments)
        return answer(method, arguments)
    }

    companion object {
        /** No plugin answers (an older app, a platform without the channel). */
        val missingPlugin: (String, Map<String, Any?>?) -> Outcome<Any?> =
            { _, _ -> Failure.Unavailable(LinuxChannel.BUILTIN_LINUX).failed() }
    }
}
