package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollect
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGroup
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideMemory
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.engine.settings.walkthrough.WhatsNewCatalog
import io.github.eslamasabry.oc.engine.settings.walkthrough.WhatsNewTarget
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The logic of every Settings page, testable under a test scope. It reads the ports' acknowledged
 * values and its own navigation state, and builds [SettingsUi]; actions write through the ports and
 * never publish an optimistic value (B-STATE-005): a refused write leaves the port's value as it
 * was and sets one [SettingsNote].
 *
 * A port the host does not bind (null) makes its page's state null; its row is already absent,
 * because the capability flags in [SettingsFacts] gate the rows.
 */
public class SettingsPresenter(
    private val scope: CoroutineScope,
    facts: StateFlow<SettingsFacts>,
    private val choices: PersonalChoices,
    private val openers: SettingsOpeners,
    private val analytics: AnalyticsChoices? = null,
    private val voice: VoiceSettings? = null,
    private val notifications: NotificationSettings? = null,
    private val updates: UpdateSettings? = null,
    private val about: AboutFacts = AboutFacts(),
    private val diagnostics: DiagnosticsExport? = null,
    private val appearanceGuide: AppearanceGuideMemory? = null,
    private val exitWhatsNew: () -> Unit = {},
) : SettingsActions {
    private data class Local(
        val stack: ImmutableList<SettingsPage> = persistentListOf(SettingsPage.Hub),
        val query: String = "",
        val entries: List<SettingsSearchEntry> = emptyList(),
        val allPacks: Boolean = false,
        val note: SettingsNote? = null,
        val consentSaving: Boolean = false,
        val report: ReportUi? = null,
        val appearanceQuestion: AppearanceQuestion? = null,
        val whatsNewIndex: Int = 0,
        val standaloneWhatsNew: Boolean = false,
    )

    private data class Engines(
        val collect: WhatWeCollect?,
        val voice: VoiceFacts?,
        val notifications: NotificationFacts?,
        val updates: UpdateFacts?,
    )

    private val local = MutableStateFlow(Local())
    private val currentFacts = facts
    private val prefs = choices.value

    private val engines =
        combine(
            analytics?.whatWeCollect ?: flowOf(null),
            voice?.facts ?: flowOf(null),
            notifications?.facts ?: flowOf(null),
            updates?.facts ?: flowOf(null),
        ) { c, v, n, u ->
            Engines(c, v, n, u)
        }

    private fun engineNow() =
        Engines(
            analytics?.whatWeCollect?.value,
            voice?.facts?.value,
            notifications?.facts?.value,
            updates?.facts?.value,
        )

    public val state: StateFlow<SettingsUi> =
        combine(local, facts, prefs, engines, appearanceGuide?.record ?: flowOf(null)) {
                l,
                f,
                p,
                e,
                guide ->
                build(l, f, p, e, guide)
            }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                build(
                    local.value,
                    facts.value,
                    prefs.value,
                    engineNow(),
                    appearanceGuide?.record?.value,
                ),
            )

    init {
        scope.launch { appearanceGuide?.load() }
    }

    override fun onSearchEntries(entries: List<SettingsSearchEntry>) {
        local.update { it.copy(entries = entries) }
    }

    private fun build(
        l: Local,
        f: SettingsFacts,
        p: PersonalPrefs,
        e: Engines,
        guide: AppearanceGuideRecord?,
    ): SettingsUi {
        val searchable = SettingsStructure.searchable(f)
        val results =
            if (l.query.isBlank()) persistentListOf()
            else SettingsSearch.find(l.query, l.entries.filter { it.row in searchable })
        val group =
            (l.stack.last() as? SettingsPage.Group)?.let { g ->
                GroupUi(g.id, SettingsStructure.rows(g.id, f, p.reasoning, p.timestamps))
            }
        return SettingsUi(
            stack = l.stack,
            hub = HubUi(SettingsStructure.groups(f), f.server, l.query, results, searchable),
            group = group,
            appearance =
                appearance(p, f, l.allPacks)
                    .copy(
                        guide = guide,
                        question = l.appearanceQuestion?.takeIf { it in appearanceQuestions(f) },
                    ),
            voice = e.voice,
            notifications = e.notifications,
            updates = e.updates,
            about = about,
            whatsNew = whatsNew(l, f),
            privacy = e.collect?.let { PrivacyUi(it, l.consentSaving) },
            language = p.language,
            report = l.report,
            note = l.note,
        )
    }

    private fun appearance(p: PersonalPrefs, f: SettingsFacts, all: Boolean): AppearanceUi {
        val available = PackId.entries.filter { it != PackId.Dynamic || f.dynamicColour }
        val first = (Packs.mostUsed + p.pack).distinct().filter { it in available }
        val listed = if (all) available else first
        return AppearanceUi(
            mode = p.mode,
            pack = p.pack,
            packs = listed.toImmutableList(),
            seeAllCount = if (listed.size < available.size) available.size else 0,
            style = p.style,
            motion = p.motion,
            textSize = p.textSize,
            styles = f.surfaceStyles,
            heldBySaver = p.saver,
            systemMotionOff = f.systemMotionOff,
            questions = appearanceQuestions(f),
        )
    }

    private fun appearanceQuestions(f: SettingsFacts): List<AppearanceQuestion> = buildList {
        add(AppearanceQuestion.Mode)
        add(AppearanceQuestion.Theme)
        if (f.surfaceStyles) add(AppearanceQuestion.Style)
        add(AppearanceQuestion.Text)
        add(AppearanceQuestion.Motion)
    }

    override fun onAppearanceQuestion(question: AppearanceQuestion?) {
        if (question != null && question !in appearanceQuestions(currentFacts.value)) return
        local.update { it.copy(appearanceQuestion = question) }
        if (question != null)
            scope.launch {
                appearanceGuide?.update { old ->
                    old.copy(
                        seen = if (question in old.seen) old.seen else old.seen + question,
                        lastIn = old.lastIn + (question.group to question),
                    )
                }
            }
    }

    override fun onAppearanceGroup(group: AppearanceGroup) {
        val questions = appearanceQuestions(currentFacts.value).filter { it.group == group }
        if (questions.isEmpty()) return
        val old = appearanceGuide?.record?.value
        val last =
            old?.lastIn?.get(group)?.takeIf {
                it in questions && !(prefs.value.saver && it.heldBySaver)
            }
        val first =
            questions.firstOrNull { !(prefs.value.saver && it.heldBySaver) } ?: questions.first()
        onAppearanceQuestion(if (old?.done == true) last ?: first else first)
    }

    override fun onAppearanceNext() {
        val old = appearanceGuide?.record?.value ?: return
        if (old.done) return
        val from =
            local.value.appearanceQuestion
                ?: AppearanceQuestion.entries.lastOrNull { it in old.seen }
        val next =
            AppearanceQuestion.after(
                from,
                prefs.value.saver,
                appearanceQuestions(currentFacts.value),
            )
        if (next != null) onAppearanceQuestion(next)
        else {
            local.update { it.copy(appearanceQuestion = null) }
            scope.launch {
                appearanceGuide?.update { it.copy(done = true, seen = AppearanceQuestion.entries) }
            }
        }
    }

    override fun onAppearanceSkip() {
        local.update { it.copy(appearanceQuestion = null) }
        scope.launch { appearanceGuide?.update { it.copy(done = true) } }
    }

    override fun onAppearanceAgain() {
        local.update { it.copy(appearanceQuestion = null) }
        scope.launch {
            appearanceGuide?.update { AppearanceGuideRecord(lastIn = it.lastIn, last = it.last) }
        }
    }

    private fun changedAppearance(question: AppearanceQuestion) {
        scope.launch { appearanceGuide?.update { it.copy(last = question) } }
    }

    private fun whatsNew(l: Local, f: SettingsFacts): WhatsNewUi? {
        val version = WhatsNewCatalog.versionFor(about.version) ?: return null
        val changes = WhatsNewCatalog.byVersion.getValue(version)
        val index = l.whatsNewIndex.coerceIn(changes.indices)
        val canShow =
            when (changes[index].target) {
                WhatsNewTarget.Updates -> updates != null
                WhatsNewTarget.Agents -> f.canOpen(SettingsOutside.Agents)
                WhatsNewTarget.AppearanceText -> true
                WhatsNewTarget.PasteImage,
                WhatsNewTarget.HoldToTalk -> false
            }
        return WhatsNewUi(version, changes, index, canShow)
    }

    public fun showWhatsNew(standalone: Boolean = false) {
        local.update {
            it.copy(
                stack = (it.stack + SettingsPage.WhatsNew).distinct().toImmutableList(),
                whatsNewIndex = 0,
                standaloneWhatsNew = standalone,
            )
        }
    }

    override fun onWhatsNewNext() {
        val notes = state.value.whatsNew ?: return
        if (!notes.last) local.update { it.copy(whatsNewIndex = notes.index + 1) }
    }

    override fun onWhatsNewPrevious() {
        local.update { it.copy(whatsNewIndex = (it.whatsNewIndex - 1).coerceAtLeast(0)) }
    }

    override fun onWhatsNewClose() {
        onBack()
    }

    override fun onWhatsNewShowMe() {
        val notes = state.value.whatsNew?.takeIf { it.canShow } ?: return
        when (notes.change.target) {
            WhatsNewTarget.Updates,
            WhatsNewTarget.AppearanceText -> {
                val page =
                    if (notes.change.target == WhatsNewTarget.Updates) SettingsPage.Updates
                    else SettingsPage.Appearance
                local.update {
                    it.copy(
                        stack = (it.stack.dropLast(1) + page).toImmutableList(),
                        standaloneWhatsNew = false,
                    )
                }
                if (page == SettingsPage.Appearance) onAppearanceQuestion(AppearanceQuestion.Text)
            }
            WhatsNewTarget.Agents -> {
                onWhatsNewClose()
                openers.open(SettingsOutside.Agents)
            }
            WhatsNewTarget.PasteImage,
            WhatsNewTarget.HoldToTalk -> Unit
        }
    }

    override fun onOpen(page: SettingsPage) {
        if (page == SettingsPage.WhatsNew) {
            showWhatsNew()
            return
        }
        local.update {
            if (it.stack.last() == page) it
            else it.copy(stack = (it.stack + page).toImmutableList())
        }
        if (page == SettingsPage.ReportProblem) loadReport()
        if (page == SettingsPage.Notifications) notifications?.refresh()
    }

    override fun onResume() {
        if (local.value.stack.last() == SettingsPage.Notifications) notifications?.refresh()
    }

    override fun onQuietHours(on: Boolean) {
        val port = notifications ?: return
        if ((port.facts.value.quietHours != null) == on) return
        run(SettingsNote.ChoiceNotSaved) {
            port.set(NotificationChange.Quiet(if (on) QuietHours.Default else null))
        }
    }

    override fun onQuietTime(start: Boolean, minutes: Int) {
        val port = notifications ?: return
        val current = port.facts.value.quietHours ?: QuietHours.Default
        val next = if (start) current.copy(start = minutes) else current.copy(end = minutes)
        if (local.value.stack.last() is SettingsPage.QuietTime) onBack()
        if (next == port.facts.value.quietHours) return
        run(SettingsNote.ChoiceNotSaved) { port.set(NotificationChange.Quiet(next)) }
    }

    override fun onBack(): Boolean {
        val l = local.value
        return when {
            l.standaloneWhatsNew && l.stack.last() == SettingsPage.WhatsNew -> {
                exitWhatsNew()
                true
            }
            l.stack.size > 1 -> {
                local.update { it.copy(stack = it.stack.dropLast(1).toImmutableList()) }
                true
            }
            l.query.isNotEmpty() -> {
                local.update { it.copy(query = "") }
                true
            }
            else -> false
        }
    }

    override fun onRow(row: SettingsRowId) {
        val p = prefs.value
        val door =
            SettingsStructure.rows(row.group, currentFacts.value, p.reasoning, p.timestamps)
                .firstOrNull { it.id == row }
                ?.door ?: return
        when (door) {
            is SettingsDoor.Page -> onOpen(door.page)
            is SettingsDoor.Outside -> openers.open(door.place)
            is SettingsDoor.Switch ->
                when (row) {
                    SettingsRowId.ShowReasoning ->
                        write(PersonalChange.Reasoning(!door.on), SettingsNote.ChoiceNotSaved)
                    SettingsRowId.ShowTimestamps ->
                        write(PersonalChange.Timestamps(!door.on), SettingsNote.ChoiceNotSaved)
                    else -> Unit
                }
        }
    }

    override fun onQuery(text: String) {
        local.update { it.copy(query = text) }
    }

    override fun onOutside(place: SettingsOutside) {
        if (place == SettingsOutside.WhatsNew) showWhatsNew() else openers.open(place)
    }

    override fun onMode(mode: AppearanceMode) {
        if (prefs.value.mode != mode)
            write(PersonalChange.Mode(mode), SettingsNote.AppearanceNotSaved)
    }

    override fun onPack(pack: PackId) {
        if (prefs.value.pack != pack)
            write(PersonalChange.Pack(pack), SettingsNote.AppearanceNotSaved)
    }

    override fun onSeeAllPacks() {
        local.update { it.copy(allPacks = true) }
    }

    override fun onStyle(style: SurfaceStyle) {
        val p = prefs.value
        if (p.saver || p.style == style) return
        write(PersonalChange.Style(style), SettingsNote.AppearanceNotSaved)
    }

    override fun onMotion(motion: MotionLevel) {
        val p = prefs.value
        if (p.saver || p.motion == motion) return
        write(PersonalChange.Motion(motion), SettingsNote.AppearanceNotSaved)
    }

    override fun onReadAloud(on: Boolean) {
        val port = voice ?: return
        run(SettingsNote.ChoiceNotSaved) { port.setReadAloud(on) }
    }

    override fun onNotification(change: NotificationChange) {
        val port = notifications ?: return
        run(SettingsNote.ChoiceNotSaved) { port.set(change) }
    }

    override fun onSendTestNotification() {
        val port = notifications ?: return
        run(SettingsNote.TestNotSent) { port.sendTest() }
    }

    override fun onCheckForUpdates() {
        val port = updates ?: return
        run(SettingsNote.UpdateFailed) { port.checkNow() }
    }

    override fun onUpdateChecks(on: Boolean) {
        val port = updates ?: return
        run(SettingsNote.ChoiceNotSaved) { port.setChecks(on) }
    }

    override fun onUpdateAction() {
        val port = updates ?: return
        run(SettingsNote.UpdateFailed) { port.act() }
    }

    override fun onDetailedUsage(on: Boolean) {
        val port = analytics ?: return
        if (local.value.consentSaving) return
        local.update { it.copy(consentSaving = true) }
        scope.launch {
            val outcome = port.setDetailedConsent(on)
            local.update {
                it.copy(
                    consentSaving = false,
                    note = if (outcome is Outcome.Failed) SettingsNote.ConsentNotSaved else it.note,
                )
            }
        }
    }

    override fun onTextSize(size: AppTextSize) {
        if (prefs.value.textSize != size)
            write(PersonalChange.TextSize(size), SettingsNote.AppearanceNotSaved)
    }

    override fun onLanguage(language: AppLanguage) {
        if (prefs.value.language != language)
            write(PersonalChange.Language(language), SettingsNote.ChoiceNotSaved)
    }

    override fun onRetryReport() {
        loadReport()
    }

    override fun onCopyReport() {
        val port = diagnostics ?: return
        val text = (local.value.report as? ReportUi.Ready)?.text ?: return
        scope.launch {
            val note =
                if (port.copy(text) is Outcome.Ok) SettingsNote.ReportCopied
                else SettingsNote.ShareFailed
            local.update { it.copy(note = note) }
        }
    }

    override fun onShareReport() {
        val port = diagnostics ?: return
        val text = (local.value.report as? ReportUi.Ready)?.text ?: return
        run(SettingsNote.ShareFailed) { port.share(text) }
    }

    /** Builds the preview once per visit; a newer request wins over a slower older one. */
    private fun loadReport() {
        val port = diagnostics ?: return
        val generation = ++reportGeneration
        local.update { it.copy(report = ReportUi.Loading) }
        scope.launch {
            val built =
                when (val result = port.report()) {
                    is Outcome.Ok -> ReportUi.Ready(result.value)
                    is Outcome.Failed -> ReportUi.Failed
                }
            if (generation == reportGeneration) local.update { it.copy(report = built) }
        }
    }

    private var reportGeneration = 0L

    override fun onNoteSeen() {
        local.update { it.copy(note = null) }
    }

    private fun write(change: PersonalChange, failed: SettingsNote) {
        run(failed) {
            choices.set(change).map {
                when (change) {
                    is PersonalChange.Mode -> AppearanceQuestion.Mode
                    is PersonalChange.Pack -> AppearanceQuestion.Theme
                    is PersonalChange.Style -> AppearanceQuestion.Style
                    is PersonalChange.Motion -> AppearanceQuestion.Motion
                    is PersonalChange.TextSize -> AppearanceQuestion.Text
                    else -> null
                }?.let(::changedAppearance)
                Unit
            }
        }
    }

    private fun run(failed: SettingsNote, call: suspend () -> Outcome<Unit>) {
        scope.launch {
            if (call() is Outcome.Failed) local.update { it.copy(note = failed) }
        }
    }
}
