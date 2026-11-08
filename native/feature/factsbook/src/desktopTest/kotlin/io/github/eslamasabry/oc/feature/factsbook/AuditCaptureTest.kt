package io.github.eslamasabry.oc.feature.factsbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.engine.factsbook.Area
import io.github.eslamasabry.oc.engine.factsbook.AreaShare
import io.github.eslamasabry.oc.engine.factsbook.BriefSettings
import io.github.eslamasabry.oc.engine.factsbook.CaptureKind
import io.github.eslamasabry.oc.engine.factsbook.ControlVerification
import io.github.eslamasabry.oc.engine.factsbook.ConversationOffer
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveEntry
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveReason
import io.github.eslamasabry.oc.engine.factsbook.EventClaims
import io.github.eslamasabry.oc.engine.factsbook.EventTrust
import io.github.eslamasabry.oc.engine.factsbook.Fact
import io.github.eslamasabry.oc.engine.factsbook.FactProvenance
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLink
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.FactsBookSettings
import io.github.eslamasabry.oc.engine.factsbook.ModuleState
import io.github.eslamasabry.oc.engine.factsbook.ModuleStatus
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouItem
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouKind
import io.github.eslamasabry.oc.engine.factsbook.NodeHostFacts
import io.github.eslamasabry.oc.engine.factsbook.NodeStatus
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeState
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeStatus
import io.github.eslamasabry.oc.engine.factsbook.ProposalDecision
import io.github.eslamasabry.oc.engine.factsbook.Reminder
import io.github.eslamasabry.oc.engine.factsbook.ReminderProposal
import io.github.eslamasabry.oc.engine.factsbook.ReminderSchedule
import io.github.eslamasabry.oc.engine.factsbook.ReviewItem
import io.github.eslamasabry.oc.engine.factsbook.ReviewState
import io.github.eslamasabry.oc.engine.factsbook.Rule
import io.github.eslamasabry.oc.engine.factsbook.RuleActivity
import io.github.eslamasabry.oc.engine.factsbook.RuleDefinition
import io.github.eslamasabry.oc.engine.factsbook.ScheduleWhen
import io.github.eslamasabry.oc.engine.factsbook.Thing
import io.github.eslamasabry.oc.engine.factsbook.ThingKind
import io.github.eslamasabry.oc.engine.factsbook.TriggerEvent
import io.github.eslamasabry.oc.engine.factsbook.TriggerOutcome
import io.github.eslamasabry.oc.engine.factsbook.TriggerOutcomeKind
import io.github.eslamasabry.oc.engine.factsbook.TriggerReason
import io.github.eslamasabry.oc.engine.factsbook.TriggerSource
import io.github.eslamasabry.oc.engine.factsbook.TriggerSourceKind
import io.github.eslamasabry.oc.engine.factsbook.TriggerTemplate
import io.github.eslamasabry.oc.engine.factsbook.TriggerTemplateId
import io.github.eslamasabry.oc.engine.factsbook.TriggersLoad
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.CareNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.EventActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.EventUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.PhoneNodeActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.PhoneNodeUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.RemindersActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.RemindersUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleEditorActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleEditorUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleForm
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleGroup
import io.github.eslamasabry.oc.feature.factsbook.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.StatusActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.StatusUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingFilter
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersUi
import io.github.eslamasabry.oc.feature.factsbook.ui.AboutYouScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.AreasScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.CaptureScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.EventScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.MemorySectionContent
import io.github.eslamasabry.oc.feature.factsbook.ui.NeedsYouScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.NotSavedScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.PhoneNodeScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.RemindersScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.RuleEditorScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.SettingsScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.StatusScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.TriggersScreen
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

/** Audit capture only: FactsBook pages in many states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val t0 = Instant.parse("2026-11-08T10:00:00Z")

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) { content() }
    }

    private fun shoot(screen: String, state: String, content: @Composable () -> Unit) =
        Audit.guard("factsbook__${screen}__${state}__build") {
            Audit.shoot("factsbook", screen, state) { Frame { content() } }
        }

    private class Recorder : MemorySectionActions, AboutYouActions, AreasActions, CaptureActions, NotSavedActions {

        override fun setUp() {}
        override fun openAboutYou() {}
        override fun addSomething() {}
        override fun openNeedsYou(kind: NeedsYouKind) {}
        override fun openThing(id: String) {}
        override fun retry() {}
        override fun back() {}
        override fun search(query: String) {}
        override fun filter(filter: ThingFilter) {}
        override fun reload() {}
        override fun openNeedsYou() {}
        override fun openAreas() {}
        override fun openReminders() {}
        override fun setUpApproval() {}
        override fun kind(kind: CaptureKind) {}
        override fun area(slug: String?) {}
        override fun thing(id: String?) {}
        override fun save(text: String) {}
        override fun seeNotSaved() {}
        override fun dismiss() {}
        override fun retry(entry: CouldNotSaveEntry) {}
        override fun askDiscard(entry: CouldNotSaveEntry) {}
        override fun discard() {}
        override fun cancelDiscard() {}
        override fun openTriggers() {}
        override fun openStatus() {}
        override fun openSettings() {}
        override fun share(area: Area, on: Boolean) {}
        override fun confirmShare() {}
        override fun cancelShare() {}
        override fun rules(on: Boolean) {}
        override fun seeRules() {}
        override fun remind(on: Boolean) {}
        override fun startNode() {}
        override fun export(entry: CouldNotSaveEntry) {}
        override fun closeExport() {}
    }
    private class Others : RemindersActions, NeedsYouActions {
        override fun back() {}
        override fun reload() {}
        override fun add() {}
        override fun edit(reminder: Reminder) {}
        override fun togglePause(reminder: Reminder) {}
        override fun dismiss() {}
        override fun openNotSaved() {}
        override fun setUpApproval() {}
        override fun refresh() {}
        override fun approve(item: ReviewItem) {}
        override fun reject(item: ReviewItem) {}
        override fun skip(item: ReviewItem) {}
        override fun approveEdited(item: ReviewItem, text: String) {}
        override fun decideProposal(proposal: io.github.eslamasabry.oc.engine.factsbook.ReminderProposal, decision: io.github.eslamasabry.oc.engine.factsbook.ProposalDecision) {
        }
        override fun whyFired(event: io.github.eslamasabry.oc.engine.factsbook.TriggerEvent) {}
        override fun notNow(offer: io.github.eslamasabry.oc.engine.factsbook.ConversationOffer) {}
        override fun talk(offer: io.github.eslamasabry.oc.engine.factsbook.ConversationOffer, leadIn: String) {}
        override fun changeProposal(proposal: io.github.eslamasabry.oc.engine.factsbook.ReminderProposal) {}
    }
    private class Pages : StatusActions, SettingsActions {
        override fun back() {}
        override fun reload() {}
        override fun setUpApproval() {}
        override fun askDisconnect() {}
        override fun disconnect() {}
        override fun cancelDisconnect() {}
        override fun setBriefCap(cap: Long) {}
        override fun usePhoneZone() {}
        override fun openAreas() {}
        override fun openTriggers() {}
        override fun dismiss() {}
        override fun openPhoneNode() {}
    }
    private class Rules : TriggersActions, RuleEditorActions, EventActions {
        override fun back() {}
        override fun refresh() {}
        override fun newRule() {}
        override fun openRule(rule: io.github.eslamasabry.oc.engine.factsbook.Rule) {}
        override fun openEvent(event: TriggerEvent) {}
        override fun togglePause(rule: io.github.eslamasabry.oc.engine.factsbook.Rule) {}
        override fun setUpApproval() {}
        override fun dismiss() {}
        override fun edit(change: (RuleForm) -> RuleForm) {}
        override fun sample(text: String) {}
        override fun useExample() {}
        override fun test() {}
        override fun save() {}
        override fun askDelete() {}
        override fun delete() {}
        override fun cancelDelete() {}
        override fun reload() {}
    }
    private class Node : PhoneNodeActions {
        override fun back() {}
        override fun runHere() {}
        override fun start() {}
        override fun stop() {}
        override fun restart() {}
        override fun askRemove() {}
        override fun remove() {}
        override fun cancelRemove() {}
        override fun showLog() {}
        override fun openCapture() {}
        override fun setUpPhone() {}
        override fun ownServer() {}
        override fun dismiss() {}
    }

    private val sara =
        Thing(
            id = "per_01",
            title = "Sara",
            kind = ThingKind.Person,
            updatedAt = t0,
            facts = listOf(Fact("f_1", "per_01", "Likes tea", FactProvenance.Told, t0, "note")),
        )
    private val card =
        Thing(
            id = "crd_01",
            title = "A card with an extremely long name that has to wrap onto a second line of the list row",
            kind = ThingKind.Card,
            updatedAt = t0,
            facts = listOf(Fact("f_2", "crd_01", "Expires next spring", FactProvenance.Guessed, t0, "photo")),
        )
    private val things = persistentListOf(sara, card)

    private val health = Area("health", sensitiveStorage = true, reviewFirst = true, share = AreaShare(), brief = false, export = false, notify = "generic")
    private val money = Area("money", sensitiveStorage = false, reviewFirst = true, share = AreaShare(), brief = false, export = false, notify = "generic")

    private val notSaved = persistentListOf(
        CouldNotSaveEntry("out_1", CouldNotSaveReason.NodeUnreachable, t0, text = "Old note", onPhone = true),
        CouldNotSaveEntry("out_2", CouldNotSaveReason.StorageFull, t0, onPhone = false),
    )

    @Test
    fun memoryAndLists() {
        shoot("memory-section", "unpaired") { MemorySectionContent(MemorySectionUi(shown = true, loaded = true, paired = false), Recorder()) }
        shoot("memory-section", "paired-needs-you") {
            MemorySectionContent(
                MemorySectionUi(
                    shown = true, loaded = true, paired = true, load = FactsBookLoad.Ready,
                    needsYou = persistentListOf(NeedsYouItem(NeedsYouKind.Review, 2)), needsYouCount = 2,
                    recent = persistentListOf(sara, card), nowMs = t0.toEpochMilliseconds(),
                ),
                Recorder(),
            )
        }
        shoot("about-you", "things") { AboutYouScreen(AboutYouUi(load = FactsBookLoad.Ready, needsYouCount = 1, things = things), Recorder()) }
        shoot("about-you", "loading") { AboutYouScreen(AboutYouUi(load = FactsBookLoad.Loading), Recorder()) }
        shoot("about-you", "empty") { AboutYouScreen(AboutYouUi(load = FactsBookLoad.Ready, things = persistentListOf()), Recorder()) }
        shoot("about-you", "failed") { AboutYouScreen(AboutYouUi(load = FactsBookLoad.Failed, listError = FactsBookError("node_unreachable")), Recorder()) }
        shoot("areas", "list") { AreasScreen(AreasUi(areas = persistentListOf(money, health), loading = false, canApprove = true), Recorder()) }
        shoot("areas", "confirm-share") { AreasScreen(AreasUi(areas = persistentListOf(money, health), loading = false, canApprove = true, confirmShare = health), Recorder()) }
        shoot("not-saved", "entries") { NotSavedScreen(NotSavedUi(entries = notSaved, nowMs = t0.toEpochMilliseconds()), Recorder()) }
        shoot("not-saved", "empty") { NotSavedScreen(NotSavedUi(nowMs = t0.toEpochMilliseconds()), Recorder()) }
    }

    @Test
    fun captureRemindersCare() {
        val daily = ScheduleWhen(JsonObject(mapOf("every" to JsonPrimitive("day"), "at" to JsonPrimitive("07:00"))))
        val gym = Reminder("rem_1", "Gym", ReminderSchedule("sch_0", daily, "vault", JsonObject(emptyMap())), "en")
        val item = ReviewItem("rvw_1", "fact_add", ReviewState.Proposed, "d", "Likes tea", area = "health", risk = listOf("health"))
        val offer =
            ConversationOffer("off_1", 1, "evt_1", "trg_1", "health", "A clinic visit next week", "od", "offered", Instant.parse("2026-11-09T10:00:00Z"))
        shoot("capture", "default") { CaptureScreen(CaptureUi(), Recorder()) }
        shoot("capture", "kept-on-phone") { CaptureScreen(CaptureUi(notice = CaptureNotice.KeptOnPhone), Recorder()) }
        shoot("reminders", "list") { RemindersScreen(RemindersUi(reminders = persistentListOf(gym), loading = false), Others()) }
        shoot("needs-you", "review") { NeedsYouScreen(NeedsYouUi(review = persistentListOf(item), canApprove = true, notice = CareNotice.Stale), Others()) }
        shoot("needs-you", "offer") { NeedsYouScreen(NeedsYouUi(offers = persistentListOf(offer)), Others()) }
    }

    @Test
    fun statusSettingsRules() {
        val status =
            NodeStatus(
                modules = mapOf("storage" to ModuleStatus(ModuleState.Ok), "replication" to ModuleStatus(ModuleState.Ok)),
                generatedAt = t0,
            )
        val link = FactsBookLink("http://127.0.0.1:8787", "cli_01", "v", "fp", "n", listOf("read"))
        shoot("status", "ok") {
            StatusScreen(
                StatusUi(status = status, control = ControlVerification(true, true, true, true, forked = false), loading = false, canApprove = true, link = link, nowMs = t0.toEpochMilliseconds()),
                Pages(),
            )
        }
        shoot("status", "needs-a-look") {
            StatusScreen(StatusUi(status = status, control = ControlVerification(true, false, true, true, false), loading = false, nowMs = t0.toEpochMilliseconds()), Pages())
        }
        val settings = FactsBookSettings(3, "Africa/Cairo", 15, BriefSettings(5, "08:00", "22:00-07:00"), JsonObject(emptyMap()))
        shoot("settings", "loaded") { SettingsScreen(SettingsUi(settings = settings, loading = false, canApprove = true, phoneZone = "Europe/London"), Pages()) }

        val rule =
            Rule(
                "rul_1", 4,
                RuleDefinition(
                    name = "Clinic", enabled = true,
                    source = TriggerSource("cli_01", TriggerSourceKind.Share, "manual-share"),
                    template = TriggerTemplate(TriggerTemplateId.Appointment),
                    area = "health", containsAll = listOf("clinic"),
                    outcomes = listOf(TriggerOutcome(TriggerOutcomeKind.Reminder)), limitPerDay = 5,
                ),
                t0,
            )
        val event =
            TriggerEvent(
                "evt_1", TriggerSource("cli_01", TriggerSourceKind.Share, "manual-share"), EventClaims(sender = "Clinic"),
                EventTrust.ThirdPartyUntrusted, "health", sensitive = false, capturedAt = t0, reason = TriggerReason.NoMatch, firings = emptyList(),
            )
        shoot("triggers", "rules") {
            TriggersScreen(
                TriggersUi(
                    load = TriggersLoad.Ready,
                    groups = persistentListOf(RuleGroup(TriggerSourceKind.Share, persistentListOf(rule))),
                    activity = persistentMapOf("rul_1" to RuleActivity(proposed = 2, ignored = 1)),
                    events = persistentListOf(event),
                    canApprove = true,
                ),
                Rules(),
            )
        }
        shoot("triggers", "unavailable") { TriggersScreen(TriggersUi(load = TriggersLoad.Unavailable), Rules()) }
        shoot("rule-editor", "new") { RuleEditorScreen(RuleEditorUi(canApprove = true), Rules()) }
        shoot("event", "no-match") { EventScreen(EventUi(event = event, loading = false), Rules()) }
    }

    @Test
    fun phoneNode() {
        shoot("phone-node", "not-installed") { PhoneNodeScreen(PhoneNodeUi(PhoneNodeStatus(PhoneNodeState.NotInstalled)), Node()) }
        shoot("phone-node", "running") {
            PhoneNodeScreen(PhoneNodeUi(PhoneNodeStatus(PhoneNodeState.Running, facts = NodeHostFacts(linuxReady = true, dataExists = true))), Node())
        }
        shoot("phone-node", "failed") { PhoneNodeScreen(PhoneNodeUi(PhoneNodeStatus(PhoneNodeState.Failed)), Node()) }
    }
}
