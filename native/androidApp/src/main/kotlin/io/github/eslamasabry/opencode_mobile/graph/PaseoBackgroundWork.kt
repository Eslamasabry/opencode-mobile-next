package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundTaskBook
import io.github.eslamasabry.oc.protocol.paseo.work.PaseoBackgroundTasks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * One bounded book per server (profile and origin), shared by its reconnects; identities cannot
 * share agent ids.
 */
class PaseoBackgroundWork(spec: GatewaySpec) : PaseoBackgroundTasks {
    private val prefix =
        DartJson.sha256Hex(
            DartJson.encode(listOf(spec.profile.id.raw, spec.profile.origin.toString()))
        )
    private val book = BackgroundTaskBook()
    private val revision = MutableStateFlow(0)
    override val changes: Flow<Unit> = revision.map { Unit }

    private fun key(agent: String) = "$prefix:$agent"

    @Synchronized
    override fun replace(agentId: String, messages: List<MessageWithParts>) {
        book.replace(key(agentId), messages)
        revision.value = book.revision
    }

    @Synchronized
    override fun observe(agentId: String, message: MessageWithParts) {
        book.observe(key(agentId), message)
        revision.value = book.revision
    }

    @Synchronized
    override fun runtimeGap(agentId: String, atMs: Long) {
        book.runtimeGap(key(agentId), atMs)
        revision.value = book.revision
    }

    @Synchronized
    override fun running(agentId: String, nowMs: Long): Boolean = book.running(key(agentId), nowMs)

    @Synchronized override fun turnFailed(agentId: String): Boolean = book.turnFailed(key(agentId))
}
