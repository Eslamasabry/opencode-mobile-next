package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.review.ConversationReview
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

// B-155: Guided review. The agent that made the change orders it for the reader: core sections first, each with one
// plain line of what and why; glue (imports, renames, generated files, tests) folds under "Also changed". The guide is
// asked of the same conversation's agent with a fixed, bounded prompt that names the changed paths only: no file
// contents go anywhere new (the agent already has them).

/** One section of a guide: a short [title], one line of what and why, and the changed files it covers. */
public data class GuideSection(val title: String, val why: String, val files: ImmutableList<String>)

/** A guide for one change set: [sections] core first, then the [alsoChanged] glue files. */
public data class ReviewGuide(val sections: ImmutableList<GuideSection>, val alsoChanged: ImmutableList<String>)

/** Asks this conversation's agent [prompt] and returns its reply text (the reply to the prompt that carries [marker]). */
public fun interface ReviewGuideAsk {
    public suspend fun ask(prompt: String, marker: String): Outcome<String>
}

/** The fixed guide prompt and the reader of its answer. */
public object ReviewGuidePrompt {
    public const val MAX_SECTIONS: Int = 6
    public const val MAX_TITLE: Int = 80
    public const val MAX_WHY: Int = 200
    public const val MAX_PATHS: Int = 50

    /** The marker the reply is found by; [key] names the change set. */
    public fun marker(key: String): String = "[review-guide ${key.take(12)}]"

    /**
     * The prompt (content for the agent, English like the other review prompts). Only paths are listed, at most
     * [MAX_PATHS]; the agent reads its own changes.
     */
    public fun prompt(marker: String, paths: List<String>): String =
        buildString {
            append(marker).append(' ')
            append("Do not change any files for this message. Group the file changes you made in this conversation ")
            append("for a reviewer, most important first. Reply with only this JSON and nothing else:\n")
            append("{\"sections\":[{\"title\":\"short title\",\"why\":\"one plain sentence: what changed and why\",")
            append("\"files\":[\"path\"],\"glue\":false}]}\n")
            append("Rules: at most $MAX_SECTIONS sections; each title under $MAX_TITLE characters; ")
            append("each why one sentence under $MAX_WHY characters; ")
            append("set \"glue\": true for sections that are only imports, renames, generated files or tests; ")
            append("use only these paths:\n")
            paths.take(MAX_PATHS).forEach { append("- ").append(it).append('\n') }
        }.trimEnd()

    /** A key for the change set: the guide is cached under it and made again when it changes. */
    public fun key(rows: List<ChangeRowUi>): String =
        rows.sortedBy { it.path }.joinToString("|") { "${it.path}:${it.added}:${it.removed}" }.hashCode().toUInt().toString(16)

    /**
     * Reads the agent's answer. Unknown paths are dropped, files no section names go to "Also changed", glue sections
     * fold there too, and text is cut to one bounded line. Null when the reply holds no usable JSON.
     */
    public fun parse(reply: String, paths: List<String>): ReviewGuide? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = parseJson(reply.substring(start, end + 1)) as? JsonObject ?: return null
        val raw = root["sections"] as? JsonArray ?: return null
        val known = paths.toSet()
        val used = HashSet<String>()
        val core = ArrayList<GuideSection>()
        val glue = LinkedHashSet<String>()
        for (item in raw) {
            val obj = item as? JsonObject ?: continue
            val files =
                (obj["files"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                    .filter { it in known && it !in used }
                    .distinct()
            if (files.isEmpty()) continue
            used += files
            if ((obj["glue"] as? JsonPrimitive)?.booleanOrNull == true || core.size >= MAX_SECTIONS) {
                glue += files
                continue
            }
            val title = line(obj["title"], MAX_TITLE)
            if (title.isEmpty()) {
                glue += files
                continue
            }
            core += GuideSection(title, line(obj["why"], MAX_WHY), files.toImmutableList())
        }
        if (core.isEmpty()) return null
        glue += paths.filter { it !in used }
        return ReviewGuide(core.toImmutableList(), glue.toImmutableList())
    }

    private fun line(element: JsonElement?, max: Int): String {
        val text = (element as? JsonPrimitive)?.contentOrNull.orEmpty().replace(Regex("\\s+"), " ").trim()
        return if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
    }

    private fun parseJson(text: String): JsonElement? =
        try {
            Json.parseToJsonElement(text)
        } catch (_: IllegalArgumentException) {
            null
        }
}

/**
 * Asks through the conversation itself: sends the guide prompt (only while the agent is idle, so it never joins a
 * running turn), then reads the agent's reply each time its turn ends. It gives up after [maxChecks] idle reads with
 * no reply; leaving the page stops it.
 */
public class AgentGuideAsk(
    private val review: ConversationReview,
    private val sender: ReviewPromptSender,
    private val maxChecks: Int = MAX_CHECKS,
) : ReviewGuideAsk {
    override suspend fun ask(prompt: String, marker: String): Outcome<String> {
        if (review.state.value.running) return Outcome.Failed(Failure.Busy(BusyKind.SessionBusy))
        var seen = review.edits.value
        when (val sent = sender.send(prompt)) {
            is Outcome.Failed -> return sent
            is Outcome.Ok -> Unit
        }
        var checks = 0
        while (checks < maxChecks) {
            seen = review.edits.first { it > seen }
            if (review.state.value.running) continue
            checks++
            val history = review.history() as? Outcome.Ok ?: continue
            replyTo(history.value.messages, marker)?.let { return Outcome.Ok(it) }
        }
        return Outcome.Failed(Failure.UnreadableReply())
    }

    public companion object {
        /** Idle reads without a reply before the page falls back to the file list. */
        public const val MAX_CHECKS: Int = 3

        /** The agent's words after the prompt that carries [marker]; null until it has said something. */
        public fun replyTo(messages: List<MessageWithParts>, marker: String): String? {
            val at =
                messages.indexOfLast { m ->
                    m.info.role == Role.User &&
                        m.parts.any { it.type == PartType.Text && marker in it.text }
                }
            if (at < 0) return null
            val text =
                messages.drop(at + 1)
                    .takeWhile { it.info.role != Role.User }
                    .flatMap { it.parts }
                    .filter { it.type == PartType.Text && !it.synthetic }
                    .joinToString("\n") { it.text }
            return text.takeIf { it.isNotBlank() }
        }
    }
}
