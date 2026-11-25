package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * The install-to-first-reply funnel as one ordered list (docs/native/analytics/journeys-2026-11-09.md section 4b, made
 * measurable in one place). Each milestone is sent at most once per install, with how long after the person first
 * agreed to usage it happened, so "where do people stop" and "how long did it take" are one report instead of seven
 * joined by hand. The order of the entries is the funnel order; [wire] is the word that leaves the phone.
 */
public enum class Milestone(public val wire: String) {
    /** The app was in front. */
    Opened("opened"),

    /** The setup page was shown (a person who never taps Start stops here). */
    SetupOpened("setup_opened"),

    /** The first setup step started. */
    SetupStarted("setup_started"),

    /** The first-run setup finished. */
    SetupDone("setup_done"),

    /** A connection to a server (phone or computer) worked. */
    ServerReady("server_ready"),

    /** A chat page was shown. */
    ChatOpened("chat_opened"),

    /** A message was sent, whatever came of it. */
    FirstPrompt("first_prompt"),

    /** How the first message ended: a reply, a stop, an error, or a send the server refused. Kind words only. */
    FirstPromptEnded("first_prompt_ended"),

    /** The first reply this install saw. */
    FirstReply("first_reply"),
    ;

    public companion object {
        public fun ofWire(word: String?): Milestone? = entries.firstOrNull { it.wire == word }
    }
}

/** Where the funnel's memory is kept between runs: one short string of words and numbers. */
public interface JourneyMemory {
    public fun load(): String?

    public fun save(encoded: String)
}

public class MemoryJourneyMemory(private var text: String? = null) : JourneyMemory {
    override fun load(): String? = text

    override fun save(encoded: String) {
        text = encoded
    }
}

/** What the funnel remembers: when the clock started and which milestones were reached. */
public data class JourneySaved(val startedAtMs: Long, val reached: Set<Milestone>)

/** [JourneySaved] as `started;word,word` (words and one number). Anything unreadable decodes to nothing. */
public object JourneyCodec {
    public fun encode(saved: JourneySaved): String =
        "${saved.startedAtMs};" + saved.reached.sortedBy { it.ordinal }.joinToString(",") { it.wire }

    public fun decode(text: String?): JourneySaved? {
        val parts = text?.split(';') ?: return null
        if (parts.size != 2) return null
        val started = parts[0].toLongOrNull() ?: return null
        val reached = parts[1].split(',').filter { it.isNotEmpty() }.map { Milestone.ofWire(it) ?: return null }
        return JourneySaved(started, reached.toSet())
    }
}

/**
 * Turns the events the app already sends into milestones, so no call site needs to know about the funnel. It is fed
 * only what [TelemetryService] has already cleaned and sent (so only while `usage` is on), and sends each milestone
 * once: [emit] gets the milestone, its `after_range` word and the kind words a step adds (`first_prompt_ended`: how
 * the first message ended, never its text), [stage] the word of the furthest milestone reached (the `journey_stage`
 * user property, which splits every other report into "still new" and "has had a reply").
 */
public class JourneyMilestones(
    private val now: () -> Long,
    private val memory: JourneyMemory,
    private val emit: (Milestone, String, Map<String, String>) -> Unit,
    private val stage: (String) -> Unit,
) {
    private val state = MutableStateFlow(JourneyCodec.decode(memory.load()) ?: JourneySaved(-1, emptySet()))

    /**
     * The first message was sent in this run and its turn has not ended yet. Kept in memory only and never saved, so
     * an app killed in between sends no ending.
     */
    private var awaitingEnding = false

    /** The furthest milestone reached so far, or null before the first. */
    public fun furthest(): Milestone? = state.value.reached.maxByOrNull { it.ordinal }

    /** Sets the stage property again (after a restart the vendor may have lost it). */
    public fun publishStage() {
        furthest()?.let { stage(it.wire) }
    }

    /** One event the app just sent, with its cleaned parameters. */
    public fun observe(event: ProductEvent, props: Map<String, String>) {
        val milestone =
            when (event) {
                ProductEvent.AppOpen -> Milestone.Opened
                ProductEvent.ScreenView ->
                    when (props["screen"]) {
                        "setup" -> Milestone.SetupOpened
                        "chat" -> Milestone.ChatOpened
                        else -> null
                    }
                ProductEvent.SetupStepReached -> Milestone.SetupStarted
                ProductEvent.SetupDone -> Milestone.SetupDone
                ProductEvent.ConnAttempt -> Milestone.ServerReady.takeIf { props["result"] == "ok" }
                ProductEvent.SendResult -> Milestone.FirstPrompt
                ProductEvent.FirstReply -> Milestone.FirstReply
                else -> null
            }
        // A send whose first_prompt was already reached sends nothing more; a turn goes on to its ending.
        if (milestone != null && !reach(milestone)) return
        firstMessageEnding(event, props)
    }

    /**
     * How the person's first message ended, in kind words only, once per install. A send the server refused ends at
     * once (`send_failed` with its `fail_kind`); a send it took waits for the turn's `chat_turn` (`outcome` and
     * `fail_kind`). Only the send that made `first_prompt` new gets here, so a second message, a repeat or an upgraded
     * install sends nothing; the wait lasts this run only, so an app killed in between sends no ending.
     */
    private fun firstMessageEnding(event: ProductEvent, props: Map<String, String>) {
        when (event) {
            ProductEvent.SendResult ->
                // The first send: refused ends here, taken waits for its turn.
                if (props["result"] == "fail") {
                    reach(
                        Milestone.FirstPromptEnded,
                        buildMap {
                            put("outcome", "send_failed")
                            props["fail_kind"]?.let { put("fail_kind", it) }
                        },
                    )
                } else {
                    awaitingEnding = true
                }
            ProductEvent.ChatTurn ->
                if (awaitingEnding) {
                    awaitingEnding = false
                    reach(
                        Milestone.FirstPromptEnded,
                        buildMap {
                            props["outcome"]?.let { put("outcome", it) }
                            props["fail_kind"]?.let { put("fail_kind", it) }
                        },
                    )
                }
            else -> Unit
        }
    }

    /** Reaches [milestone] for this install, sending it with [extra] when it is new; true only when it was new. */
    public fun reach(milestone: Milestone, extra: Map<String, String> = emptyMap()): Boolean {
        val at = now()
        var fresh = false
        var after = 0L
        val next =
            state.updateAndGet { saved ->
                if (milestone in saved.reached) {
                    fresh = false
                    saved
                } else {
                    fresh = true
                    val started = if (saved.startedAtMs < 0) at else saved.startedAtMs
                    after = at - started
                    JourneySaved(started, saved.reached + milestone)
                }
            }
        if (!fresh) return false
        memory.save(JourneyCodec.encode(next))
        emit(milestone, HealthRanges.sinceStartOf(after), extra)
        // The stage is the furthest point, not the newest: reaching an earlier one late changes nothing.
        stage(next.reached.maxBy { it.ordinal }.wire)
        return true
    }
}
