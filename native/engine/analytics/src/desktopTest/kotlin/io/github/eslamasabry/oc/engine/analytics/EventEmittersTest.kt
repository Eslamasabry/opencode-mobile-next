package io.github.eslamasabry.oc.engine.analytics

import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Analytics truth (intake 2026-11-16, stage 5): every declared event has a production caller. For each event, the
 * calls that carry it from the place that knows the fact to the vendor must all exist in production sources (test
 * source sets are not read). An event with no caller is listed in [notEmitted] with its reason; that list may only
 * shrink, so an event that loses its caller fails here instead of going quiet on a dashboard.
 */
class EventEmittersTest {
    /** The production call chain of each event: every one of these must appear in a production source file. */
    private val chains: Map<ProductEvent, List<String>> =
        mapOf(
            ProductEvent.AppOpen to listOf("ProductEvent.AppOpen", "lifecycle.coldStart(", "lifecycle.onForeground()"),
            ProductEvent.SetupStepReached to listOf("flow.setupStepReached(", "AppJourneys.setupOrNull"),
            ProductEvent.SetupDone to listOf("flow.setupDone(", "AppJourneys.setupOrNull"),
            ProductEvent.SetupAbandoned to listOf("flow.setupAbandoned(", "setupOrNull?.onBackground()"),
            ProductEvent.SetupResumed to listOf("flow.setupResumed(", "setupOrNull?.onForeground()"),
            ProductEvent.FirstReply to listOf("flow.firstReply(", "journey?.text(", "journey?.tool("),
            ProductEvent.ConnectComputerChosen to listOf("ProductEvent.ConnectComputerChosen", ".connectComputerChosen()"),
            ProductEvent.UpdateOffered to listOf("ProductEvent.UpdateOffered", "flow().updateOffered()", "meter.observe("),
            ProductEvent.UpdateInstalled to listOf("ProductEvent.UpdateInstalled", "lifecycle.coldStart("),
            ProductEvent.ScreenView to listOf("ProductEvent.ScreenView"),
            ProductEvent.FeatureUsed to listOf("ProductEvent.FeatureUsed", "AppTelemetry.current.feature("),
            ProductEvent.SessionLength to listOf("ProductEvent.SessionLength", "lifecycle.onBackground("),
            ProductEvent.ConnAttempt to listOf("HealthFlow().connAttempt("),
            ProductEvent.ConnDrop to listOf("flow.connDrop(", "journey?.stream(connection", "streamDropReason(status)", "chat.checkStalls()"),
            ProductEvent.SendResult to listOf("flow.sendResult(", "AppJourneys.sendSettled(", "HealthFlow().sendResult(\"queued\")"),
            ProductEvent.TurnStall to listOf("flow.turnStall(", "chat.checkStalls()"),
            ProductEvent.ChatTurn to listOf("flow.chatTurn(", "journey?.ended("),
            ProductEvent.Approval to listOf("flow.approval(", "feed.update(pendingFacts("),
            ProductEvent.QueueFlush to listOf("HealthFlow().queueFlush("),
            ProductEvent.LinuxSetupStep to listOf("flow.linuxSetupStep(", "AppJourneys.setupOrNull"),
            ProductEvent.AgentStart to listOf("HealthFlow().agentStart("),
            ProductEvent.BgServiceEnd to listOf("flow().bgServiceEnd(", "BackgroundServiceEnd.app.timedOut(", "BackgroundServiceEnd.app.destroyed("),
            ProductEvent.UpdateResult to listOf("flow().updateResult(\"in_app\", \"failed\"", "flow().updateResult(\"in_app\", \"declined\")", "updateResult(kind = \"in_app\", result = \"installed\")"),
            ProductEvent.AppStart to listOf("ProductEvent.AppStart", "health?.firstFrame()"),
            ProductEvent.Jank to listOf("ProductEvent.Jank", "jank?.onFrames(", "lifecycle.onBackground(health?.jank)"),
            ProductEvent.MemoryPressure to listOf("ProductEvent.MemoryPressure", "health?.memoryPressure("),
            ProductEvent.StarPromptShown to listOf("ProductEvent.StarPromptShown"),
            ProductEvent.StarPromptTapped to listOf("ProductEvent.StarPromptTapped"),
            ProductEvent.StarPromptDismissed to listOf("ProductEvent.StarPromptDismissed"),
            ProductEvent.JourneyMilestone to listOf("ProductEvent.JourneyMilestone", "telemetry.attachJourney("),
            ProductEvent.ShareIn to listOf("ProductEvent.ShareIn"),
            ProductEvent.AppExit to listOf("ProductEvent.AppExit", "reportAnalyticsExit("),
            ProductEvent.StarterUsed to listOf("ProductEvent.StarterUsed", "AppTelemetry.current.starterUsed("),
        )

    /** Declared, with no production caller today, and why. May only shrink. */
    private val notEmitted: Map<ProductEvent, String> =
        mapOf(
            ProductEvent.UseFor to
                "the one 'What do you want help with?' question is not built in the native app; nothing calls useFor",
        )

    private fun nativeRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "no settings.gradle.kts above the test's directory" }
    }

    /** Every production Kotlin source: `src/<set>Main/` (and androidApp's `src/main/`), never a test set or build output. */
    private fun productionText(): String {
        val root = nativeRoot()
        val sb = StringBuilder()
        root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && !it.name.startsWith(".") || it == root }
            .filter { f ->
                // The event list and the funnel that watches events name every event without sending one.
                f.isFile && f.extension == "kt" && f.name != "ProductEvents.kt" && f.name != "JourneyMilestones.kt" &&
                    f.invariantSeparatorsPath.let { p -> Regex("/src/[A-Za-z]*(Main|main)/").containsMatchIn(p) }
            }
            .forEach { sb.append(it.readText()).append('\n') }
        return sb.toString()
    }

    @Test
    fun `B-JOURNEY-080 every declared event has a production caller, or a reason in a list that may only shrink`() {
        val text = productionText()
        assertTrue(text.contains("class ChatJourney") && text.contains("class AppTelemetryHealth"), "the production sources were read")
        assertEquals(ProductEvent.entries.toSet(), chains.keys + notEmitted.keys, "every event is in exactly one list")
        assertTrue((chains.keys intersect notEmitted.keys).isEmpty())
        val missing = chains.flatMap { (event, calls) -> calls.filter { it !in text }.map { "${event.wire}: $it" } }
        assertEquals(emptyList(), missing, "a call that carries the event is gone")
        // The allow-list may only shrink: grow it and this fails, shrink it and lower the number.
        assertTrue(notEmitted.size <= 1, "an event lost its caller: give it one instead of listing it")
        for (why in notEmitted.values) assertTrue(why.isNotBlank())
    }
}
