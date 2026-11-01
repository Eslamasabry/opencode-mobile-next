package io.github.eslamasabry.oc.feature.chatcomposer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerFacts
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerOpeners
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerPort
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldValue
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.SendHow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records every port call; [failNextSend] makes the next send fail as a network failure. */
class FakeComposerPort(facts: ComposerFacts = ComposerFacts()) : ComposerPort {
    override val facts: MutableStateFlow<ComposerFacts> = MutableStateFlow(facts)
    val calls = mutableListOf<String>()
    val sent = mutableListOf<Pair<String, SendHow>>()
    var failNextSend = false
    var historyAnswer: FieldValue? = null

    override fun draftChanged(text: String) {
        calls += "draft:$text"
    }

    override suspend fun flushDraft(): Outcome<Unit> {
        calls += "flush"
        return Unit.ok()
    }

    override fun history(older: Boolean, current: FieldValue, suggestionsOpen: Boolean): FieldValue? {
        calls += "history:${if (older) "older" else "newer"}:${current.text}"
        return historyAnswer
    }

    override suspend fun send(text: String, how: SendHow): Outcome<Unit> {
        sent += text to how
        if (failNextSend) {
            failNextSend = false
            return Outcome.Failed(Failure.Network())
        }
        return Unit.ok()
    }

    override suspend fun stop(): Outcome<Unit> {
        calls += "stop"
        return Unit.ok()
    }
}

class RecordingOpeners : ComposerOpeners {
    val opened = mutableListOf<String>()

    override fun openModelPicker() {
        opened += "model"
    }

    override fun openApprovals() {
        opened += "approvals"
    }

    override fun startVoice() {
        opened += "voice"
    }
}
