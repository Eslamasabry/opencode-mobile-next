package io.github.eslamasabry.oc.feature.factsbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.Area
import io.github.eslamasabry.oc.engine.factsbook.AreaShare
import io.github.eslamasabry.oc.engine.factsbook.CaptureKind
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveEntry
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveReason
import io.github.eslamasabry.oc.engine.factsbook.Fact
import io.github.eslamasabry.oc.engine.factsbook.FactProvenance
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouItem
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouKind
import io.github.eslamasabry.oc.engine.factsbook.Thing
import io.github.eslamasabry.oc.engine.factsbook.ThingKind
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingFilter
import io.github.eslamasabry.oc.feature.factsbook.ui.AboutYouScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.AreasScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.CaptureScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.MemorySectionContent
import io.github.eslamasabry.oc.feature.factsbook.ui.NotSavedScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.engine.factsbook.Reminder
import io.github.eslamasabry.oc.engine.factsbook.ReminderSchedule
import io.github.eslamasabry.oc.engine.factsbook.ReviewItem
import io.github.eslamasabry.oc.engine.factsbook.ReviewState
import io.github.eslamasabry.oc.engine.factsbook.ScheduleWhen
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.RemindersActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.RemindersUi
import io.github.eslamasabry.oc.feature.factsbook.ui.NeedsYouScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.RemindersScreen
import io.github.eslamasabry.oc.engine.factsbook.BriefSettings
import io.github.eslamasabry.oc.engine.factsbook.ControlVerification
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLink
import io.github.eslamasabry.oc.engine.factsbook.FactsBookSettings
import io.github.eslamasabry.oc.engine.factsbook.ModuleState
import io.github.eslamasabry.oc.engine.factsbook.ModuleStatus
import io.github.eslamasabry.oc.engine.factsbook.NodeStatus
import io.github.eslamasabry.oc.feature.factsbook.presentation.CareNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.StatusActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.StatusUi
import io.github.eslamasabry.oc.feature.factsbook.ui.SettingsScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.StatusScreen
import androidx.compose.ui.test.onNodeWithContentDescription
import io.github.eslamasabry.oc.engine.factsbook.EventClaims
import io.github.eslamasabry.oc.engine.factsbook.EventTrust
import io.github.eslamasabry.oc.engine.factsbook.RuleActivity
import io.github.eslamasabry.oc.engine.factsbook.TriggerEvent
import io.github.eslamasabry.oc.engine.factsbook.TriggerOutcome
import io.github.eslamasabry.oc.engine.factsbook.TriggerOutcomeKind
import io.github.eslamasabry.oc.engine.factsbook.TriggerReason
import io.github.eslamasabry.oc.engine.factsbook.TriggerSource
import io.github.eslamasabry.oc.engine.factsbook.TriggerSourceKind
import io.github.eslamasabry.oc.engine.factsbook.TriggerTemplate
import io.github.eslamasabry.oc.engine.factsbook.TriggerTemplateId
import io.github.eslamasabry.oc.engine.factsbook.TriggersLoad
import io.github.eslamasabry.oc.feature.factsbook.presentation.EventActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.EventUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleEditorActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleEditorUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleForm
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleGroup
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersUi
import io.github.eslamasabry.oc.feature.factsbook.ui.EventScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.RuleEditorScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.TriggersScreen
import kotlinx.collections.immutable.persistentMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.engine.factsbook.NodeHostFacts
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeState
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeStatus
import io.github.eslamasabry.oc.feature.factsbook.presentation.PhoneNodeActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.PhoneNodeUi
import io.github.eslamasabry.oc.feature.factsbook.ui.PhoneNodeScreen
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/** The real pages with fake state, found by visible text (Flutter test/factsbook/factsbook_pages_test.dart). */
@OptIn(ExperimentalTestApi::class)
class FactsBookScreensTest {
    private val t0 = Instant.parse("2026-11-08T10:00:00Z")

    @Composable
    private fun Themed(locale: String = "en", content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) { content() }
            }
        }
    }

    private class Recorder : MemorySectionActions, AboutYouActions, AreasActions, CaptureActions, NotSavedActions {
        val calls = mutableListOf<String>()

        override fun setUp() { calls += "setUp" }
        override fun openAboutYou() { calls += "aboutYou" }
        override fun addSomething() { calls += "add" }
        override fun openNeedsYou(kind: NeedsYouKind) { calls += "needs $kind" }
        override fun openThing(id: String) { calls += "thing $id" }
        override fun retry() { calls += "retry" }
        override fun back() { calls += "back" }
        override fun search(query: String) { calls += "search $query" }
        override fun filter(filter: ThingFilter) { calls += "filter $filter" }
        override fun reload() { calls += "reload" }
        override fun openNeedsYou() { calls += "needs" }
        override fun openAreas() { calls += "areas" }
        override fun openReminders() { calls += "reminders" }
        override fun setUpApproval() { calls += "approval" }
        override fun kind(kind: CaptureKind) { calls += "kind $kind" }
        override fun area(slug: String?) { calls += "area $slug" }
        override fun thing(id: String?) { calls += "pick $id" }
        override fun save(text: String) { calls += "save $text" }
        override fun seeNotSaved() { calls += "seeNotSaved" }
        override fun dismiss() { calls += "dismiss" }
        override fun retry(entry: CouldNotSaveEntry) { calls += "retry ${entry.id}" }
        override fun askDiscard(entry: CouldNotSaveEntry) { calls += "ask ${entry.id}" }
        override fun discard() { calls += "discard" }
        override fun cancelDiscard() { calls += "cancel" }
        override fun openTriggers() { calls += "triggers" }
        override fun openStatus() { calls += "status" }
        override fun openSettings() { calls += "settings" }
        override fun share(area: Area, on: Boolean) { calls += "share ${area.slug} $on" }
        override fun confirmShare() { calls += "confirmShare" }
        override fun cancelShare() { calls += "cancelShare" }
        override fun rules(on: Boolean) { calls += "rules $on" }
        override fun seeRules() { calls += "seeRules" }
        override fun remind(on: Boolean) { calls += "remind $on" }
        override fun startNode() { calls += "startNode" }
        override fun export(entry: CouldNotSaveEntry) { calls += "export ${entry.id}" }
        override fun closeExport() { calls += "closeExport" }
    }

    private val sara =
        Thing(
            id = "per_01",
            title = "Sara",
            kind = ThingKind.Person,
            updatedAt = t0,
            facts = listOf(Fact("f_1", "per_01", "Likes tea", FactProvenance.Told, t0, "note")),
        )

    @Test
    fun `B-FB-040 unpaired the Memory section is one row that sets FactsBook up`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { MemorySectionContent(MemorySectionUi(shown = true, loaded = true, paired = false), actions) } }
        onNodeWithText("Set up Memory").performClick()
        onNodeWithText("Open About you").assertDoesNotExist()
        assertEquals(listOf("setUp"), actions.calls)
    }

    @Test
    fun `B-FB-041 while the switch is off nothing about FactsBook shows`() = runComposeUiTest {
        setContent { Themed { MemorySectionContent(MemorySectionUi(shown = false, loaded = true, paired = true), Recorder()) } }
        onNodeWithText("Open About you").assertDoesNotExist()
        onNodeWithText("Set up Memory").assertDoesNotExist()
    }

    @Test
    fun `B-FB-042 paired the section shows what needs you, the newest thing and the two ways in`() = runComposeUiTest {
        val actions = Recorder()
        val state =
            MemorySectionUi(
                shown = true,
                loaded = true,
                paired = true,
                load = FactsBookLoad.Ready,
                needsYou = persistentListOf(NeedsYouItem(NeedsYouKind.Review, 2)),
                needsYouCount = 2,
                recent = persistentListOf(sara),
                nowMs = t0.toEpochMilliseconds(),
            )
        setContent { Themed { MemorySectionContent(state, actions) } }
        onNodeWithText("2 changes to review").performClick()
        onNodeWithText("Sara").performClick()
        onNodeWithText("Add something").performClick()
        assertEquals(listOf("needs Review", "thing per_01", "add"), actions.calls)
    }

    @Test
    fun `B-FB-043 About you lists every thing with its newest fact and opens it`() = runComposeUiTest {
        val actions = Recorder()
        setContent {
            Themed { AboutYouScreen(AboutYouUi(load = FactsBookLoad.Ready, needsYouCount = 1, things = persistentListOf(sara)), actions) }
        }
        onNodeWithText("Needs you").assertExists()
        onNodeWithText("People", useUnmergedTree = true).performClick()
        onNodeWithText("Sara").performClick()
        assertEquals(listOf("filter People", "thing per_01"), actions.calls)
    }

    @Test
    fun `B-FB-044 Areas says why sharing cannot be changed yet`() = runComposeUiTest {
        val area = Area("health", sensitiveStorage = true, reviewFirst = true, share = AreaShare(), brief = false, export = false, notify = "generic")
        setContent { Themed { AreasScreen(AreasUi(areas = persistentListOf(area), loading = false, canApprove = false), Recorder()) } }
        onNodeWithText("Other apps and agents can read it").assertExists()
        onNodeWithText("Set up approval first", substring = true).assertExists()
    }

    @Test
    fun `B-FB-045 a note kept on the phone says so and offers Not saved yet`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { CaptureScreen(CaptureUi(notice = CaptureNotice.KeptOnPhone), actions) } }
        onNodeWithText("It could not be saved yet. It is kept on this phone.").assertExists()
    }

    @Test
    fun `B-FB-046 Not saved yet discards only after asking`() = runComposeUiTest {
        val entry = CouldNotSaveEntry("out_1", CouldNotSaveReason.NodeUnreachable, t0, text = "Old note", onPhone = true)
        val actions = Recorder()
        setContent {
            Themed {
                NotSavedScreen(NotSavedUi(entries = persistentListOf(entry), confirmDiscard = entry, nowMs = t0.toEpochMilliseconds()), actions)
            }
        }
        onNodeWithText("Old note", useUnmergedTree = true).assertExists()
        onNodeWithText("Discard this note?").assertExists()
        onNodeWithText("Discard note").performClick()
        assertEquals(listOf("discard"), actions.calls)
    }

    @Test
    fun `B-FB-047 the Arabic Memory section reads in plain Arabic words`() = runComposeUiTest {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        try {
            setContent {
                Themed("ar") {
                    MemorySectionContent(MemorySectionUi(shown = true, loaded = true, paired = true, load = FactsBookLoad.Ready), Recorder())
                }
            }
            onNodeWithText("أضف شيئًا").assertExists()
            onNodeWithText("افتح «عنّي»").assertExists()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private class Others : RemindersActions, NeedsYouActions {
        val calls = mutableListOf<String>()
        override fun back() { calls += "back" }
        override fun reload() { calls += "reload" }
        override fun add() { calls += "add" }
        override fun edit(reminder: Reminder) { calls += "edit ${reminder.id}" }
        override fun togglePause(reminder: Reminder) { calls += "pause ${reminder.id}" }
        override fun dismiss() { calls += "dismiss" }
        override fun openNotSaved() { calls += "notSaved" }
        override fun setUpApproval() { calls += "approval" }
        override fun refresh() { calls += "refresh" }
        override fun approve(item: ReviewItem) { calls += "approve ${item.id}" }
        override fun reject(item: ReviewItem) { calls += "reject ${item.id}" }
        override fun skip(item: ReviewItem) { calls += "skip ${item.id}" }
        override fun approveEdited(item: ReviewItem, text: String) { calls += "edit ${item.id} $text" }
        override fun decideProposal(proposal: io.github.eslamasabry.oc.engine.factsbook.ReminderProposal, decision: io.github.eslamasabry.oc.engine.factsbook.ProposalDecision) {
            calls += "proposal ${proposal.id} $decision"
        }
        override fun whyFired(event: io.github.eslamasabry.oc.engine.factsbook.TriggerEvent) { calls += "why ${event.id}" }
        override fun notNow(offer: io.github.eslamasabry.oc.engine.factsbook.ConversationOffer) { calls += "notNow ${offer.id}" }
        override fun talk(offer: io.github.eslamasabry.oc.engine.factsbook.ConversationOffer, leadIn: String) { calls += "talk ${offer.id} $leadIn" }
        override fun changeProposal(proposal: io.github.eslamasabry.oc.engine.factsbook.ReminderProposal) { calls += "change ${proposal.id}" }
    }

    @Test
    fun `B-FB-048 Reminders groups active ones, says they do not ring here and pauses by name`() = runComposeUiTest {
        val daily = ScheduleWhen(JsonObject(mapOf("every" to JsonPrimitive("day"), "at" to JsonPrimitive("07:00"))))
        val gym = Reminder("rem_1", "Gym", ReminderSchedule("sch_0", daily, "vault", JsonObject(emptyMap())), "en")
        val actions = Others()
        setContent { Themed { RemindersScreen(RemindersUi(reminders = persistentListOf(gym), loading = false), actions) } }
        onNodeWithText("Active").assertExists()
        onNodeWithText("Every day at 07:00", substring = true).assertExists()
        onNodeWithText("Not sounded on this phone", substring = true).assertExists()
        onNodeWithText("Pause “Gym”", useUnmergedTree = true).performClick()
        onNodeWithText("Add a reminder").performClick()
        assertEquals(listOf("pause rem_1", "add"), actions.calls)
    }

    @Test
    fun `B-FB-049 Needs you shows the change and says why it cannot be approved yet`() = runComposeUiTest {
        val item = ReviewItem("rvw_1", "fact_add", ReviewState.Proposed, "d", "Likes tea")
        val actions = Others()
        setContent { Themed { NeedsYouScreen(NeedsYouUi(review = persistentListOf(item), notSaved = 1), actions) } }
        onNodeWithText("Likes tea").assertExists()
        onNodeWithText("Approve").assertExists()
        onNodeWithText("1 note not saved yet").performClick()
        assertEquals(listOf("notSaved"), actions.calls)
    }

    @Test
    fun `B-FB-050 with approval set up each change is approved or rejected by name and a tap offers Change and Skip`() =
        runComposeUiTest {
            val item = ReviewItem("rvw_1", "fact_add", ReviewState.Proposed, "d", "Likes tea", area = "health", risk = listOf("health"))
            val actions = Others()
            setContent {
                Themed { NeedsYouScreen(NeedsYouUi(review = persistentListOf(item), canApprove = true, notice = CareNotice.Stale), actions) }
            }
            onNodeWithText("That changed after it was proposed", substring = true).assertExists()
            onNodeWithText("Set up approval first", substring = true).assertDoesNotExist()
            onNodeWithText("Approve").performClick()
            onNodeWithText("Reject").performClick()
            onNodeWithText("Likes tea").performClick()
            onNodeWithText("Skip “Likes tea”").performClick()
            assertEquals(listOf("approve rvw_1", "reject rvw_1", "skip rvw_1"), actions.calls)
        }

    @Test
    fun `B-FB-081 an offer says what opens and offers Talk about this and Not now as equals`() = runComposeUiTest {
        val offer =
            io.github.eslamasabry.oc.engine.factsbook.ConversationOffer(
                "off_1", 1, "evt_1", "trg_1", "health", "A clinic visit next week", "od", "offered",
                kotlin.time.Instant.parse("2026-11-09T10:00:00Z"),
            )
        val actions = Others()
        setContent { Themed { NeedsYouScreen(NeedsYouUi(offers = persistentListOf(offer)), actions) } }
        onNodeWithText("A clinic visit next week").assertExists()
        onNodeWithText("It gets a short summary, not the whole message", substring = true).assertExists()
        onNodeWithText("Talk about this").performClick()
        onNodeWithText("Not now").performClick()
        assertEquals(
            listOf(
                "talk off_1 Memory noticed something you may want to talk about. This is a short summary, not the original message:",
                "notNow off_1",
            ),
            actions.calls,
        )
    }

    @Test
    fun `B-FB-051 Areas changes sharing with the switch and asks before opening the locked folder`() = runComposeUiTest {
        val money = Area("money", sensitiveStorage = false, reviewFirst = true, share = AreaShare(), brief = false, export = false, notify = "generic")
        val health = money.copy(slug = "health", sensitiveStorage = true)
        val actions = Recorder()
        setContent {
            Themed { AreasScreen(AreasUi(areas = persistentListOf(money, health), loading = false, canApprove = true, confirmShare = health), actions) }
        }
        onNodeWithText("Set up approval first", substring = true).assertDoesNotExist()
        onNodeWithText("Let others read Health?").assertExists()
        onNodeWithText("Let them read").performClick()
        assertEquals(listOf("confirmShare"), actions.calls)
    }

    private class Pages : StatusActions, SettingsActions {
        val calls = mutableListOf<String>()
        override fun back() { calls += "back" }
        override fun reload() { calls += "reload" }
        override fun setUpApproval() { calls += "approval" }
        override fun askDisconnect() { calls += "askDisconnect" }
        override fun disconnect() { calls += "disconnect" }
        override fun cancelDisconnect() { calls += "cancel" }
        override fun setBriefCap(cap: Long) { calls += "cap $cap" }
        override fun usePhoneZone() { calls += "zone" }
        override fun openAreas() { calls += "areas" }
        override fun openTriggers() { calls += "triggers" }
        override fun dismiss() { calls += "dismiss" }
        override fun openPhoneNode() { calls += "phoneNode" }
    }

    @Test
    fun `B-FB-052 Status says everything works only with a verified history and asks before Disconnect`() = runComposeUiTest {
        val status =
            NodeStatus(
                modules = mapOf("storage" to ModuleStatus(ModuleState.Ok), "replication" to ModuleStatus(ModuleState.Ok)),
                generatedAt = t0,
            )
        val link = FactsBookLink("http://127.0.0.1:8787", "cli_01", "v", "fp", "n", listOf("read"))
        val actions = Pages()
        setContent {
            Themed {
                StatusScreen(
                    StatusUi(
                        status = status,
                        control = ControlVerification(true, true, true, true, forked = false),
                        loading = false,
                        canApprove = true,
                        link = link,
                        nowMs = t0.toEpochMilliseconds(),
                        confirmDisconnect = true,
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("Everything is working", useUnmergedTree = true).assertExists()
        onNodeWithText("Disconnect this phone's memory?").assertExists()
        onNodeWithText("Disconnect this phone's memory").performClick()
        assertEquals(listOf("disconnect"), actions.calls)
    }

    @Test
    fun `B-FB-053 Status names a history that does not check out as something to look at`() = runComposeUiTest {
        val status = NodeStatus(modules = mapOf("storage" to ModuleStatus(ModuleState.Ok)), generatedAt = t0)
        setContent {
            Themed {
                StatusScreen(
                    StatusUi(status = status, control = ControlVerification(true, false, true, true, false), loading = false, nowMs = t0.toEpochMilliseconds()),
                    Pages(),
                )
            }
        }
        onNodeWithText("1 thing needs a look").assertExists()
        onNodeWithText("Signed history").assertExists()
        onNodeWithText("Approve changes from this phone").assertExists()
    }

    @Test
    fun `B-FB-054 Settings offers this phone's time zone when it differs and shows the brief`() = runComposeUiTest {
        val settings =
            FactsBookSettings(3, "Africa/Cairo", 15, BriefSettings(5, "08:00", "22:00-07:00"), JsonObject(emptyMap()))
        val actions = Pages()
        setContent {
            Themed { SettingsScreen(SettingsUi(settings = settings, loading = false, canApprove = true, phoneZone = "Europe/London"), actions) }
        }
        onNodeWithText("Items a day").assertExists()
        onNodeWithText("15 minutes").assertExists()
        onNodeWithText("Use this phone's time zone").performClick()
        onNodeWithText("Areas", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(listOf("zone"), actions.calls)
    }

    private val rule =
        io.github.eslamasabry.oc.engine.factsbook.Rule(
            "rul_1",
            4,
            io.github.eslamasabry.oc.engine.factsbook.RuleDefinition(
                name = "Clinic",
                enabled = true,
                source = TriggerSource("cli_01", TriggerSourceKind.Share, "manual-share"),
                template = TriggerTemplate(TriggerTemplateId.Appointment),
                area = "health",
                containsAll = listOf("clinic"),
                outcomes = listOf(TriggerOutcome(TriggerOutcomeKind.Reminder)),
                limitPerDay = 5,
            ),
            t0,
        )

    private val event =
        TriggerEvent(
            "evt_1",
            TriggerSource("cli_01", TriggerSourceKind.Share, "manual-share"),
            EventClaims(sender = "Clinic"),
            EventTrust.ThirdPartyUntrusted,
            "health",
            sensitive = false,
            capturedAt = t0,
            reason = TriggerReason.NoMatch,
            firings = emptyList(),
        )

    private class Rules : TriggersActions, RuleEditorActions, EventActions {
        val calls = mutableListOf<String>()
        override fun back() { calls += "back" }
        override fun refresh() { calls += "refresh" }
        override fun newRule() { calls += "new" }
        override fun openRule(rule: io.github.eslamasabry.oc.engine.factsbook.Rule) { calls += "rule ${rule.id}" }
        override fun openEvent(event: TriggerEvent) { calls += "event ${event.id}" }
        override fun togglePause(rule: io.github.eslamasabry.oc.engine.factsbook.Rule) { calls += "pause ${rule.id}" }
        override fun setUpApproval() { calls += "approval" }
        override fun dismiss() { calls += "dismiss" }
        override fun edit(change: (RuleForm) -> RuleForm) { calls += "edit" }
        override fun sample(text: String) { calls += "sample" }
        override fun useExample() { calls += "example" }
        override fun test() { calls += "test" }
        override fun save() { calls += "save" }
        override fun askDelete() { calls += "askDelete" }
        override fun delete() { calls += "delete" }
        override fun cancelDelete() { calls += "cancelDelete" }
        override fun reload() { calls += "reload" }
    }

    @Test
    fun `B-FB-069 Triggers lists rules by where messages come from, pauses one by name and opens a recent message`() = runComposeUiTest {
        val actions = Rules()
        setContent {
            Themed {
                TriggersScreen(
                    TriggersUi(
                        load = TriggersLoad.Ready,
                        groups = persistentListOf(RuleGroup(TriggerSourceKind.Share, persistentListOf(rule))),
                        activity = persistentMapOf("rul_1" to RuleActivity(proposed = 2, ignored = 1)),
                        events = persistentListOf(event),
                        canApprove = true,
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("Text you share · 1 rule").assertExists()
        onNodeWithText("Looks for an appointment → propose a reminder", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("This week: 2 proposed, 1 ignored", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Pause “Clinic”").performClick()
        onNodeWithText("No rule matched this message.", useUnmergedTree = true).performClick()
        onNodeWithText("New rule").performClick()
        assertEquals(listOf("pause rul_1", "event evt_1", "new"), actions.calls)
    }

    @Test
    fun `B-FB-070 a node without rules says so plainly`() = runComposeUiTest {
        setContent { Themed { TriggersScreen(TriggersUi(load = TriggersLoad.Unavailable), Rules()) } }
        onNodeWithText("Rules are off here").assertExists()
    }

    @Test
    fun `B-FB-071 the rule editor shows a saved rule and deletes it only after asking`() = runComposeUiTest {
        val actions = Rules()
        setContent {
            Themed {
                RuleEditorScreen(
                    RuleEditorUi(form = RuleForm.of(rule), saved = rule, dirty = false, canApprove = true, confirmDelete = true),
                    actions,
                )
            }
        }
        onNodeWithText("Edit rule", useUnmergedTree = true).assertExists()
        onNodeWithText("Delete this rule?").assertExists()
        onNodeWithText("Delete rule").performClick()
        assertEquals(listOf("delete"), actions.calls)
    }

    @Test
    fun `B-FB-072 what happened to a message says why first and labels the sender as a claim`() = runComposeUiTest {
        setContent { Themed { EventScreen(EventUi(event = event, loading = false), Rules()) } }
        onNodeWithText("No rule matched this message.").assertExists()
        onNodeWithText("Sender (a claim)").assertExists()
        onNodeWithText("Unverified sender", substring = true).assertExists()
    }

    private class Node : PhoneNodeActions {
        val calls = mutableListOf<String>()
        override fun back() { calls += "back" }
        override fun runHere() { calls += "run" }
        override fun start() { calls += "start" }
        override fun stop() { calls += "stop" }
        override fun restart() { calls += "restart" }
        override fun askRemove() { calls += "askRemove" }
        override fun remove() { calls += "remove" }
        override fun cancelRemove() { calls += "cancelRemove" }
        override fun showLog() { calls += "log" }
        override fun openCapture() { calls += "capture" }
        override fun setUpPhone() { calls += "setUpPhone" }
        override fun ownServer() { calls += "ownServer" }
        override fun dismiss() { calls += "dismiss" }
    }

    @Test
    fun `B-FB-075 memory on this phone offers one button to set it up and says plainly when Linux comes first`() = runComposeUiTest {
        val actions = Node()
        var ui by mutableStateOf(PhoneNodeUi(PhoneNodeStatus(PhoneNodeState.NotInstalled)))
        setContent { Themed { PhoneNodeScreen(ui, actions) } }
        onNodeWithText("Memory not installed yet").assertExists()
        onNodeWithText("Set up memory on this phone").performClick()
        ui = PhoneNodeUi(PhoneNodeStatus(PhoneNodeState.LinuxNotSetUp))
        onNodeWithText("Set up phone first").assertExists()
        onNodeWithText("Set up this phone").performClick()
        assertEquals(listOf("run", "setUpPhone"), actions.calls)
    }

    @Test
    fun `B-FB-076 a running memory offers Stop and Restart and removing it asks first with what is lost`() = runComposeUiTest {
        val actions = Node()
        setContent {
            Themed {
                PhoneNodeScreen(
                    PhoneNodeUi(PhoneNodeStatus(PhoneNodeState.Running, facts = NodeHostFacts(linuxReady = true, dataExists = true)), confirmRemove = true),
                    actions,
                )
            }
        }
        onNodeWithText("Memory is running here", useUnmergedTree = true).assertExists()
        onNodeWithText("Remove memory from phone?").assertExists()
        onNodeWithText("Every note, fact and reminder Memory holds here", substring = true).assertExists()
        onNodeWithText("Remove memory from this phone").performClick()
        assertEquals(listOf("remove"), actions.calls)
    }

    @Test
    fun `B-FB-079 a stopped memory on this phone says so on the home section with Start, not that it did not answer`() = runComposeUiTest {
        val actions = Recorder()
        val state =
            MemorySectionUi(
                shown = true,
                loaded = true,
                paired = true,
                load = FactsBookLoad.Failed,
                error = io.github.eslamasabry.oc.engine.factsbook.FactsBookError("node_unreachable"),
                nodeStopped = true,
            )
        setContent { Themed { MemorySectionContent(state, actions) } }
        onNodeWithText("Memory is stopped").assertExists()
        onNodeWithText("Memory did not answer").assertDoesNotExist()
        onNodeWithText("Start").performClick()
        assertEquals(listOf("startNode"), actions.calls)
    }

    @Test
    fun `B-FB-080 a note the node holds can be exported as the encrypted object, with what it does not unlock`() = runComposeUiTest {
        val entry = CouldNotSaveEntry("ivc_1", CouldNotSaveReason.KeyLocked, t0, onPhone = false)
        val export = io.github.eslamasabry.oc.engine.factsbook.CaptureExport("{\n  \"item\": \"itm_1\"\n}", "itm_1")
        setContent {
            Themed { NotSavedScreen(NotSavedUi(entries = persistentListOf(entry), canExport = true, export = export, nowMs = t0.toEpochMilliseconds()), Recorder()) }
        }
        onNodeWithText("Export this note").assertExists()
        onNodeWithText("It does not unlock the locked folder", substring = true).assertExists()
        onNodeWithText("Copy export").assertExists()
    }
}
