package io.github.eslamasabry.oc.feature.computer

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.persistentListOf

val COMPUTER = ProfileId("paseo-1")

val NEWER = ImportItem("claude/h2", "Fix the login page", "/work/acme", "2 hours ago")
val OLDER = ImportItem("claude/h1", null, "/work/acme", "Yesterday")

/** A scripted ComputerTools: every call is recorded; answers are set per test. */
class FakeComputerTools : ComputerTools {
    val calls = mutableListOf<String>()
    var versionAnswer: Outcome<String?> = Outcome.Ok("1.4.0")
    var steps: List<UpdateStep> = listOf(UpdateStep.Starting, UpdateStep.Downloading, UpdateStep.Installing, UpdateStep.Finishing)
    var updateAnswer: Outcome<UpdateAnswer> = Outcome.Ok(UpdateAnswer.Done("1.5.0"))
    var importableAnswer: Outcome<ImportList> = Outcome.Ok(ImportList("/work/acme", persistentListOf(NEWER, OLDER), alreadyHere = 2))
    var importAnswer: Outcome<ConversationRef> = Outcome.Ok(ConversationRef(COMPUTER, SessionId("agent-9")))
    val seenSteps = mutableListOf<UpdateStep>()

    override fun name(profile: ProfileId): String? = "Studio Mac"

    override suspend fun version(profile: ProfileId): Outcome<String?> = versionAnswer.also { calls += "version" }

    override suspend fun update(profile: ProfileId, onStep: (UpdateStep) -> Unit): Outcome<UpdateAnswer> {
        calls += "update"
        steps.forEach { seenSteps += it; onStep(it) }
        return updateAnswer
    }

    override suspend fun importable(profile: ProfileId): Outcome<ImportList> = importableAnswer.also { calls += "importable" }

    override suspend fun import(profile: ProfileId, item: ImportItem): Outcome<ConversationRef> =
        importAnswer.also { calls += "import:${item.key}" }
}
