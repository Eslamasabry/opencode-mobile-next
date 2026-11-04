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
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density
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
    private val crashes: CrashSettings? = null,
    private val phrases: PhraseSettings? = null,
    private val appearanceGuide: AppearanceGuideMemory? = null,
    private val exitWhatsNew: () -> Unit = {},
    private val localModels: LocalModelStorage? = null,
    private val licenses: VoiceLicenseSource? = null,
) : SettingsActions {
    private data class Local(
        val stack: ImmutableList<SettingsPage> = persistentListOf(SettingsPage.Hub),
        val query: String = "",
        val entries: List<SettingsSearchEntry> = emptyList(),
        val allPacks: Boolean = false,
        val note: SettingsNote? = null,
        val consentSaving: Boolean = false,
        val report: ReportUi? = null,
        val reportDescription: String = "",
        val crash: CrashUi? = null,
        val focusedRow: SettingsRowId? = null,
        val focusedAnchor: String? = null,
        val phraseChips: Boolean = true,
        val appearanceQuestion: AppearanceQuestion? = null,
        val whatsNewIndex: Int = 0,
        val standaloneWhatsNew: Boolean = false,
        val confirmLocalModels: Boolean = false,
        val license: LicenseUi? = null,
    )

    private data class Engines(
        val collect: WhatWeCollect?,
        val voice: VoiceFacts?,
        val notifications: NotificationFacts?,
        val updates: UpdateFacts?,
        val localModels: LocalModelStorageFacts?,
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
            localModels?.facts ?: flowOf(null),
        ) { c, v, n, u, m ->
            Engines(c, v, n, u, m)
        }

    private fun engineNow() =
        Engines(
            analytics?.whatWeCollect?.value,
            voice?.facts?.value,
            notifications?.facts?.value,
            updates?.facts?.value,
            localModels?.facts?.value,
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
        phrases?.let { port ->
            scope.launch {
                port.enabled.collect { enabled -> local.update { it.copy(phraseChips = enabled) } }
            }
        }
        crashes?.let { port ->
            scope.launch {
                port.state.collect { evidence ->
                    local.update { current ->
                        val valid:
                            io.github.eslamasabry.oc.engine.diagnostics.crash.CrashReportPreview? =
                            null
                        current.copy(
                            crash =
                                CrashUi(
                                    current.crash?.history,
                                    evidence?.available == true,
                                    evidence?.enabled == true,
                                    evidence?.records?.size ?: 0,
                                    valid,
                                    if (evidence?.storageFailed == true)
                                        io.github.eslamasabry.oc.engine.diagnostics.crash
                                            .CrashReportError
                                            .StorageFailed
                                    else null,
                                )
                        )
                    }
                }
            }
        }
    }

    override fun onRefreshExitHistory() {
        val port = crashes ?: return
        scope.launch {
            val history = port.history()
            local.update { it.copy(crash = (it.crash ?: CrashUi()).copy(history = history)) }
        }
    }

    override fun onCrashCapture(on: Boolean) {
        val port = crashes ?: return
        scope.launch {
            if (!port.setEnabled(on)) local.update { it.copy(note = SettingsNote.ConsentNotSaved) }
        }
    }

    override fun onClearCrashes() {
        val port = crashes ?: return
        scope.launch {
            if (!port.clear()) local.update { it.copy(note = SettingsNote.ChoiceNotSaved) }
        }
    }

    override fun onPreviewCrashes() {
        val port = crashes ?: return
        scope.launch {
            val result = port.preview()
            local.update {
                it.copy(crash = it.crash?.copy(preview = result.preview, error = result.error))
            }
        }
    }

    override fun onShareCrashes() {
        val port = crashes ?: return
        val preview = local.value.crash?.preview ?: return
        scope.launch {
            val error = port.share(preview)
            local.update { it.copy(crash = it.crash?.copy(error = error)) }
        }
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
            capabilities =
                f.server?.let {
                    ServerFeatures.project(
                        it.name,
                        f.serverCapabilities
                            ?: io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
                                .Unknown,
                        f.platform,
                        f.usageStatistics,
                    )
                },
            report = l.report,
            reportDescription = l.reportDescription,
            phraseChips = l.phraseChips,
            focusedRow = l.focusedRow,
            focusedAnchor = l.focusedAnchor,
            crash = l.crash,
            localModels = e.localModels?.copy(confirmDelete = l.confirmLocalModels),
            note = l.note,
            voiceLicenses = licenses != null,
            license = l.license,
        )
    }

    private fun appearance(p: PersonalPrefs, f: SettingsFacts, all: Boolean): AppearanceUi {
        val available = PackId.entries.filter { it != PackId.Dynamic || f.dynamicColour }
        val first = (Packs.mostUsed + p.pack).distinct().filter { it in available }
        val listed = if (all) available else first
        val ownKnobs =
            StyleResolver.spec(Style.entries.first { it.name == p.style.name }).tokens.defaults
        return AppearanceUi(
            mode = p.mode,
            pack = p.pack,
            packs = listed.toImmutableList(),
            seeAllCount = if (listed.size < available.size) available.size else 0,
            style = p.style,
            corners = p.corners ?: ownKnobs.corners,
            spacing = p.spacing ?: ownKnobs.density,
            fineTuned =
                (p.corners != null && p.corners != ownKnobs.corners) ||
                    (p.spacing != null && p.spacing != ownKnobs.density),
            accent = p.accent,
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
                // A standalone launch stays standalone: Back from the shown page leaves the
                // route instead of revealing a Settings hub the person never opened (Flutter
                // closes the sheet back to the launch screen).
                local.update { it.copy(stack = (it.stack.dropLast(1) + page).toImmutableList()) }
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
        updates?.setPageOpen(page == SettingsPage.Updates)
        if (page == SettingsPage.WhatsNew) {
            showWhatsNew()
            return
        }
        local.update {
            if (it.stack.last() == page) it
            else it.copy(stack = (it.stack + page).toImmutableList())
        }
        if (page == SettingsPage.ReportProblem) loadReport()
        if (page == SettingsPage.Storage) onRefreshLocalModels()
        if (page == SettingsPage.Notifications) notifications?.refresh()
    }

    override fun onRefreshLocalModels() {
        scope.launch { localModels?.refresh() }
    }

    override fun onAskDeleteLocalModel() {
        val facts = localModels?.facts?.value ?: return
        if (!facts.loading && !facts.deleting && !facts.failed && facts.present) {
            local.update { it.copy(confirmLocalModels = true) }
        }
    }

    override fun onCancelDeleteLocalModel() {
        local.update { it.copy(confirmLocalModels = false) }
    }

    override fun onDeleteLocalModel() {
        if (!local.value.confirmLocalModels) return
        local.update { it.copy(confirmLocalModels = false) }
        scope.launch { localModels?.delete() }
    }

    override fun onResume() {
        if (local.value.stack.last() == SettingsPage.Storage) onRefreshLocalModels()
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
            l.standaloneWhatsNew && l.stack.size <= 2 -> {
                exitWhatsNew()
                true
            }
            l.stack.size > 1 -> {
                if (l.stack.last() == SettingsPage.ReportProblem) closeReport()
                local.update { it.copy(stack = it.stack.dropLast(1).toImmutableList()) }
                updates?.setPageOpen(local.value.stack.last() == SettingsPage.Updates)
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

    override fun onAnchor(tag: String) {
        if (tag == "notifications-servers" && currentFacts.value.platform.notifications) {
            onOpen(SettingsPage.Notifications)
            local.update { it.copy(query = "", focusedAnchor = tag) }
        } else SettingsRowId.entries.firstOrNull { it.tag == tag }?.let(::onTarget)
    }

    override fun onTarget(row: SettingsRowId) {
        if (row !in SettingsStructure.searchable(currentFacts.value)) return
        onOpen(SettingsPage.Group(row.group))
        local.update { it.copy(query = "", focusedRow = row) }
        // Like Flutter's search results, a page or outside row opens its destination; a switch
        // stays highlighted in its group so the person can flip it there.
        val p = prefs.value
        val door =
            SettingsStructure.rows(row.group, currentFacts.value, p.reasoning, p.timestamps)
                .firstOrNull { it.id == row }
                ?.door
        if (door != null && door !is SettingsDoor.Switch) onRow(row)
    }

    override fun onPhraseChips(on: Boolean) {
        val port = phrases ?: return
        run(SettingsNote.ChoiceNotSaved) { port.setEnabled(on) }
    }

    override fun onQuery(text: String) {
        local.update { it.copy(query = text) }
    }

    override fun onOutside(place: SettingsOutside) {
        if (place == SettingsOutside.PrivacyPolicy) {
            onOpen(SettingsPage.PrivacyPolicy)
            return
        }
        if (place == SettingsOutside.WhatsNew) {
            showWhatsNew()
            return
        }
        // Voice notices open the in-app licence list when this build can read the bundled licences.
        if (place == SettingsOutside.VoiceNotices && licenses != null) onOpen(SettingsPage.About)
        else openers.open(place)
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

    override fun onCorners(corners: Corners) {
        val p = prefs.value
        if (p.saver || (p.corners ?: ownKnobs(p).corners) == corners) return
        write(PersonalChange.Corner(corners), SettingsNote.AppearanceNotSaved)
    }

    override fun onSpacing(spacing: Density) {
        val p = prefs.value
        if (p.saver || (p.spacing ?: ownKnobs(p).density) == spacing) return
        write(PersonalChange.Spacing(spacing), SettingsNote.AppearanceNotSaved)
    }

    override fun onResetFineTune() {
        run(SettingsNote.AppearanceNotSaved) {
            val corners = choices.set(PersonalChange.Corner(null))
            if (corners is Outcome.Failed) corners.map {}
            else choices.set(PersonalChange.Spacing(null)).map {}
        }
    }

    override fun onAccent(id: String?) {
        if (prefs.value.accent != id)
            write(PersonalChange.Accent(id), SettingsNote.AppearanceNotSaved)
    }

    private fun ownKnobs(p: PersonalPrefs) =
        StyleResolver.spec(Style.entries.first { it.name == p.style.name }).tokens.defaults

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

    override fun onReportDescription(text: String) {
        if (text == local.value.reportDescription) return
        local.update { it.copy(reportDescription = text) }
        loadReport()
    }

    override fun onSendReport() {
        val port = diagnostics ?: return
        if (local.value.report !is ReportUi.Ready) return
        val description = local.value.reportDescription
        run(SettingsNote.ShareFailed) { port.send(description) }
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
        val description = local.value.reportDescription
        local.update { it.copy(report = ReportUi.Loading) }
        scope.launch {
            val built =
                when (val result = port.report(description)) {
                    is Outcome.Ok -> ReportUi.Ready(result.value)
                    is Outcome.Failed -> ReportUi.Failed
                }
            if (generation == reportGeneration) local.update { it.copy(report = built) }
        }
    }

    private var reportGeneration = 0L

    public fun closeReport() {
        reportGeneration++
        diagnostics?.endReport()
        local.update { it.copy(report = null, reportDescription = "") }
    }

    override fun onVoiceLicense(id: String) {
        if (licenses == null || VoiceLicenseComponent.entries.none { it.id == id }) return
        onOpen(SettingsPage.VoiceLicense(id))
        loadLicense(id)
    }

    override fun onRetryLicense() {
        val page = local.value.stack.last() as? SettingsPage.VoiceLicense ?: return
        loadLicense(page.id)
    }

    override fun onCopyLicense() {
        val source = licenses ?: return
        val text = (local.value.license as? LicenseUi.Ready)?.text ?: return
        scope.launch {
            val note =
                if (source.copy(text) is Outcome.Ok) SettingsNote.LicenseCopied
                else SettingsNote.LicenseCopyFailed
            local.update { it.copy(note = note) }
        }
    }

    override fun onLicenseWebsite() {
        val page = local.value.stack.last() as? SettingsPage.VoiceLicense ?: return
        val component = VoiceLicenseComponent.entries.firstOrNull { it.id == page.id } ?: return
        licenses?.openWebsite(component.website)
    }

    private var licenseGeneration = 0L

    private fun loadLicense(id: String) {
        val source = licenses ?: return
        val component = VoiceLicenseComponent.entries.firstOrNull { it.id == id } ?: return
        val generation = ++licenseGeneration
        local.update { it.copy(license = LicenseUi.Loading) }
        scope.launch {
            val result =
                when (val read = source.read(component.asset)) {
                    is Outcome.Ok -> LicenseUi.Ready(reflowLicenseText(read.value))
                    is Outcome.Failed -> LicenseUi.Failed
                }
            if (
                generation == licenseGeneration &&
                    local.value.stack.last() == SettingsPage.VoiceLicense(id)
            ) {
                local.update { it.copy(license = result) }
            }
        }
    }

    public fun close() {
        updates?.setPageOpen(false)
        licenseGeneration++
    }

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
