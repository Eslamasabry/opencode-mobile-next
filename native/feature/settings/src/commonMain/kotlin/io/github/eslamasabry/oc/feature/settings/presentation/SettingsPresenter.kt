package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollect
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
        combine(local, facts, prefs, engines) { l, f, p, e -> build(l, f, p, e) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                build(local.value, facts.value, prefs.value, engineNow()),
            )

    init {
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

    private fun build(l: Local, f: SettingsFacts, p: PersonalPrefs, e: Engines): SettingsUi {
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
            appearance = appearance(p, f, l.allPacks),
            voice = e.voice,
            notifications = e.notifications,
            updates = e.updates,
            about = about,
            privacy = e.collect?.let { PrivacyUi(it, l.consentSaving) },
            language = p.language,
            report = l.report,
            reportDescription = l.reportDescription,
            crash = l.crash,
            note = l.note,
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
        )
    }

    override fun onOpen(page: SettingsPage) {
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
            l.stack.size > 1 -> {
                if (l.stack.last() == SettingsPage.ReportProblem) closeReport()
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
        if (place == SettingsOutside.PrivacyPolicy) {
            onOpen(SettingsPage.PrivacyPolicy)
            return
        }
        openers.open(place)
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

    override fun onNoteSeen() {
        local.update { it.copy(note = null) }
    }

    private fun write(change: PersonalChange, failed: SettingsNote) {
        run(failed) { choices.set(change).map {} }
    }

    private fun run(failed: SettingsNote, call: suspend () -> Outcome<Unit>) {
        scope.launch {
            if (call() is Outcome.Failed) local.update { it.copy(note = failed) }
        }
    }
}
