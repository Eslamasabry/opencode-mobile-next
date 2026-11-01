package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusNotificationModelTest {
    private fun content(title: String = "Running the tests") =
        StatusContent(StatusKind.WORKING, title)

    // ---- the shared notification ID: who owns the row ----

    @Test fun oneServiceStoppingWhileTheOtherRunsDoesNotRemoveTheRow() {
        val owners = StatusOwners()
        assertTrue(owners.start("live"))
        assertFalse(owners.start("server"))
        assertFalse(owners.stop("live"))
        assertTrue(owners.any())
        assertTrue(owners.has("server"))
    }

    @Test fun theLastServiceStoppingRemovesTheRow() {
        val owners = StatusOwners()
        owners.start("live")
        owners.start("server")
        assertFalse(owners.stop("server"))
        assertTrue(owners.stop("live"))
        assertFalse(owners.any())
    }

    @Test fun stoppingAServiceThatNeverStartedRemovesNothing() {
        val owners = StatusOwners()
        owners.start("live")
        assertFalse(owners.stop("server"))
        assertTrue(owners.has("live"))
    }

    @Test fun startingTheSameServiceTwiceIsStillOneOwner() {
        val owners = StatusOwners()
        assertTrue(owners.start("live"))
        assertFalse(owners.start("live"))
        assertTrue(owners.stop("live"))
    }

    // ---- throttle: one update per two seconds, only when it changed ----

    @Test fun firstContentPostsAtOnce() {
        val throttle = StatusThrottle(2_000)
        assertEquals(StatusThrottle.Step.Post(content()), throttle.offer(content(), 1_000))
    }

    @Test fun identicalContentIsSkipped() {
        val throttle = StatusThrottle(2_000)
        throttle.offer(content(), 1_000)
        assertEquals(StatusThrottle.Step.Skip, throttle.offer(content(), 9_000))
    }

    @Test fun aChangeInsideTheGapWaitsForTheRestOfTheGap() {
        val throttle = StatusThrottle(2_000)
        throttle.offer(content("a"), 1_000)
        assertEquals(StatusThrottle.Step.Wait(1_400), throttle.offer(content("b"), 1_600))
        assertNull(throttle.due(2_000))
        assertEquals(content("b"), throttle.due(3_000))
    }

    @Test fun aBurstInsideTheGapProducesOneTrailingPostWithTheNewest() {
        val throttle = StatusThrottle(2_000)
        throttle.offer(content("a"), 0)
        assertTrue(throttle.offer(content("b"), 100) is StatusThrottle.Step.Wait)
        assertEquals(StatusThrottle.Step.Skip, throttle.offer(content("c"), 200))
        assertEquals(StatusThrottle.Step.Skip, throttle.offer(content("d"), 300))
        assertEquals(content("d"), throttle.due(2_000))
        assertNull(throttle.due(2_001))
    }

    @Test fun updatesTwoSecondsApartBothPost() {
        val throttle = StatusThrottle(2_000)
        assertTrue(throttle.offer(content("a"), 0) is StatusThrottle.Step.Post)
        assertTrue(throttle.offer(content("b"), 2_000) is StatusThrottle.Step.Post)
    }

    @Test fun returningToWhatIsShownCancelsTheTrailingPost() {
        val throttle = StatusThrottle(2_000)
        throttle.offer(content("a"), 0)
        throttle.offer(content("b"), 500)
        assertEquals(StatusThrottle.Step.Skip, throttle.offer(content("a"), 700))
        assertNull(throttle.due(5_000))
    }

    @Test fun aServiceStartingForcesAPostAndRestartsTheGap() {
        val throttle = StatusThrottle(2_000)
        throttle.offer(content("a"), 0)
        throttle.offer(content("b"), 100)
        throttle.force(content("c"), 200)
        assertNull(throttle.due(5_000))
        assertEquals(StatusThrottle.Step.Skip, throttle.offer(content("c"), 300))
        assertTrue(throttle.offer(content("d"), 400) is StatusThrottle.Step.Wait)
    }

    @Test fun resetForgetsWhatWasShown() {
        val throttle = StatusThrottle(2_000)
        throttle.offer(content("a"), 0)
        throttle.reset()
        assertTrue(throttle.offer(content("a"), 10) is StatusThrottle.Step.Post)
    }

    // ---- what Dart may send ----

    @Test fun aWellFormedStatusIsRead() {
        val parsed = StatusContent.fromMap(mapOf(
            "kind" to "working",
            "title" to "Running the tests",
            "text" to "UI review · 5 of 6 tasks",
            "lines" to listOf("a", "b"),
            "subText" to "IPTV_King · 4 min",
            "progress" to 83,
            "actions" to listOf("open", "stop_reply"),
            "sessionId" to "s1",
            "publicTitle" to "OpenCode · 1 working",
        ))!!
        assertEquals(StatusKind.WORKING, parsed.kind)
        assertEquals(83, parsed.progress)
        assertEquals(listOf(StatusAction.OPEN, StatusAction.STOP_REPLY), parsed.actions)
        assertEquals("OpenCode · 1 working", parsed.publicTitle)
        assertEquals("s1", parsed.sessionId)
    }

    @Test fun anUnknownKindOrAMissingTitleIsRefused() {
        assertNull(StatusContent.fromMap(mapOf("kind" to "mystery", "title" to "x")))
        assertNull(StatusContent.fromMap(mapOf("kind" to "idle")))
        assertNull(StatusContent.fromMap(mapOf("kind" to "idle", "title" to "  ")))
        assertNull(StatusContent.fromMap(null))
    }

    @Test fun unknownActionsAreDroppedAndOpenIsTheDefault() {
        val parsed = StatusContent.fromMap(mapOf(
            "kind" to "idle", "title" to "Ready", "actions" to listOf("format_disk", "exec"),
        ))!!
        assertEquals(listOf(StatusAction.OPEN), parsed.actions)
    }

    @Test fun progressIsClampedAndLinesAreCapped() {
        val parsed = StatusContent.fromMap(mapOf(
            "kind" to "working", "title" to "t", "progress" to 400,
            "lines" to listOf("1", "2", "3", "4", "5", "6"),
        ))!!
        assertEquals(100, parsed.progress)
        assertEquals(StatusContent.MAX_LINES, parsed.lines.size)
    }

    @Test fun noProgressMeansNoBar() {
        assertNull(StatusContent.fromMap(mapOf("kind" to "idle", "title" to "t"))!!.progress)
    }

    @Test fun overlongTextIsCut() {
        val parsed = StatusContent.fromMap(mapOf(
            "kind" to "idle", "title" to "x".repeat(1_000),
        ))!!
        assertEquals(StatusContent.MAX_TEXT, parsed.title.length)
    }

    @Test fun everyKindAndActionNameRoundTrips() {
        for (kind in StatusKind.values()) assertEquals(kind, StatusKind.fromWire(kind.wire))
        for (action in StatusAction.values()) assertEquals(action, StatusAction.fromWire(action.wire))
    }
}

class AlertButtonsTest {
    @Test fun aReadablePermissionOffersAllowOnceAndRejectOnly() {
        val plan = AlertButtons.plan("permission", answerable = true)
        assertEquals(listOf("allow", "deny"), plan.map { it.decision })
        assertEquals(
            listOf(AlertButton.Label.ALLOW_ONCE, AlertButton.Label.REJECT),
            plan.map { it.label },
        )
        assertTrue(plan.none { it.decision.contains("always") })
    }

    @Test fun allowOnceNeedsTheUnlockedPhoneButRejectDoesNot() {
        val plan = AlertButtons.plan("permission", true)
        assertTrue(plan.first { it.decision == "allow" }.authRequired)
        assertFalse(plan.first { it.decision == "deny" }.authRequired)
    }

    @Test fun aPermissionTheShadeCannotShowOnlyOpens() {
        assertEquals(listOf(AlertButton.Label.OPEN), AlertButtons.plan("permission", false).map { it.label })
    }

    @Test fun aQuestionOnlyOpens() {
        assertEquals(listOf(AlertButton.Label.OPEN), AlertButtons.plan("question", true).map { it.label })
    }

    @Test fun neverMoreThanTwoActions() {
        for (kind in listOf("permission", "question", "complete", "error", "checkin", "quota", "team_decision")) {
            for (answerable in listOf(true, false)) {
                assertTrue(kind, AlertButtons.plan(kind, answerable).size <= 2)
            }
        }
    }

    @Test fun resultsCarryNoButtons() {
        assertTrue(AlertButtons.plan("complete", true).isEmpty())
        assertTrue(AlertButtons.plan("error", true).isEmpty())
    }
}

/** The final copy per notification type, before localization. */
class AlertCopyTest {
    private fun request(kind: String, detail: String = "", chatTitle: String = "") =
        CodingAlertRequest(kind = kind, sessionID = "s1", key = "k", detail = detail, chatTitle = chatTitle)

    private fun ask(detail: String, action: String, target: String) =
        request("permission", detail).copy(askAction = action, askTarget = target)

    @Test fun aCommandAsksToRunItAndShowsTheExactCommand() {
        val copy = AlertCopy.of(ask("npm install --save left-pad", "run", "npm install"))!!
        assertEquals(AlertCopy.Copy(AlertCopy.Title.RUN, "npm install", "npm install --save left-pad"), copy)
        assertTrue(AlertCopy.answerable("permission", true, "npm install --save left-pad"))
    }

    @Test fun aFileEditAsksToEditTheFile() {
        val copy = AlertCopy.of(ask("lib/settings.dart", "edit", "settings.dart"))!!
        assertEquals(AlertCopy.Title.EDIT, copy.title)
        assertEquals("settings.dart", copy.titleArg)
        assertEquals("lib/settings.dart", copy.body)
    }

    @Test fun aReadAndAFolderAskInTheirOwnWords() {
        assertEquals(AlertCopy.Title.READ, AlertCopy.of(ask("a.env", "read", "a.env"))!!.title)
        assertEquals(AlertCopy.Title.FOLDER, AlertCopy.of(ask("/srv/api", "folder", "api"))!!.title)
    }

    @Test fun aPermissionWithoutItsCommandAsksInGeneralAndCannotBeAllowed() {
        val copy = AlertCopy.of(request("permission"))!!
        assertEquals(AlertCopy.Copy(AlertCopy.Title.ASK), copy)
        assertFalse(AlertCopy.answerable("permission", true, ""))
    }

    @Test fun aWatchedServerPermissionCannotBeAllowedFromTheShade() {
        assertFalse(AlertCopy.answerable("permission", allowActions = false, detail = "rm -rf build"))
    }

    @Test fun aQuestionIsItsOwnTitleWithItsChoicesBelow() {
        val copy = AlertCopy.of(
            request("question", "Which branch should I use?").copy(options = listOf("main", "develop"))
        )!!
        assertEquals(AlertCopy.Copy(AlertCopy.Title.LITERAL, "Which branch should I use?", "main · develop"), copy)
    }

    @Test fun aQuestionWithManyOrLongChoicesShowsNoChoices() {
        assertEquals("", AlertCopy.choices(listOf("a", "b", "c", "d")))
        assertEquals("", AlertCopy.choices(listOf("Yes", "x".repeat(25))))
        assertEquals("", AlertCopy.choices(listOf("Yes", "Yes")))
    }

    @Test fun aFormWithoutWordsSaysItHasAQuestion() {
        assertEquals(AlertCopy.Copy(AlertCopy.Title.QUESTION), AlertCopy.of(request("question")))
    }

    @Test fun finishedWorkNamesTheConversationAndItsFirstLine() {
        val copy = AlertCopy.of(request("complete", "Added a combo counter and screen shake.",
            chatTitle = "Add combo counter"))!!
        assertEquals(
            AlertCopy.Copy(AlertCopy.Title.FINISHED, "Add combo counter", "Added a combo counter and screen shake."),
            copy,
        )
        assertEquals(AlertCopy.Title.FINISHED_ANY, AlertCopy.of(request("complete"))!!.title)
    }

    @Test fun aStoppedConversationIsNamedWithoutTheServersError() {
        assertEquals(AlertCopy.Copy(AlertCopy.Title.STOPPED, "Fix login"),
            AlertCopy.of(request("error", "raw error text", chatTitle = "Fix login")))
        assertEquals(AlertCopy.Copy(AlertCopy.Title.STOPPED_ANY), AlertCopy.of(request("error")))
    }

    @Test fun theFinishedSummaryListsEachConversationOnce() {
        val finished = request("complete", "Done.", chatTitle = "Add combo counter")
        assertEquals("Add combo counter", AlertCopy.summaryLine(finished, "Finished: Add combo counter"))
        assertEquals("Run npm install?", AlertCopy.summaryLine(request("permission"), "Run npm install?"))
    }

    @Test fun longWordsAreCappedOnOneLine() {
        val copy = AlertCopy.of(request("question", "Why\n" + "x".repeat(200)))!!
        assertEquals(AlertCopy.MAX_QUESTION, copy.titleArg.length)
        assertTrue(copy.titleArg.endsWith("…"))
        assertFalse(copy.titleArg.contains("\n"))
    }

    @Test fun otherKindsKeepTheirFixedCopy() {
        assertEquals(null, AlertCopy.of(request("checkin")))
        assertEquals(null, AlertCopy.of(request("team_decision")))
    }
}
