package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollect
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
 * The logic of every Settings page, testable under a test scope. It reads the ports' acknowledged values and its own
 * navigation state, and builds [SettingsUi]; actions write through the ports and never publish an optimistic value
 * (B-STATE-005): a refused write leaves the port's value as it was and sets one [SettingsNote].
 *
 * A port the host does not bind (null) makes its page's state null; its row is already absent, because the capability
 * flags in [SettingsFacts] gate the rows.
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
    private val phraseChips: PhraseChipsSetting? = null,
) : SettingsActions {
    private data class Local(
        val stack: ImmutableList<SettingsPage> = persistentListOf(SettingsPage.Hub),
        val query: String = "",
        val entries: List<SettingsSearchEntry> = emptyList(),
        val allPacks: Boolean = false,
        val note: SettingsNote? = null,
        val consentSaving: Boolean = false,
        val report: ReportUi? = null,
        val placeEntries: List<SettingsPlaceEntry> = emptyList(),
        val focus: String? = null,
    )

    private data class Engines(
        val collect: WhatWeCollect?,
        val voice: VoiceFacts?,
        val notifications: NotificationFacts?,
        val updates: UpdateFacts?,
    )

    private val local = MutableStateFlow(Local())
    private val hostFacts = facts

    /** The host's facts with the phrase switch's acknowledged value laid over them (null: no phrase store). */
    private val liveFacts =
        combine(facts, phraseChips?.enabled ?: flowOf(null)) { f, on -> f.copy(phraseChips = on) }

    private val currentFacts: SettingsFacts get() = hostFacts.value.copy(phraseChips = phraseChips?.enabled?.value)
    private val prefs = choices.value

    private val engines =
        combine(
            analytics?.whatWeCollect ?: flowOf(null),
            voice?.facts ?: flowOf(null),
            notifications?.facts ?: flowOf(null),
            updates?.facts ?: flowOf(null),
        ) { c, v, n, u -> Engines(c, v, n, u) }

    private fun engineNow() =
        Engines(analytics?.whatWeCollect?.value, voice?.facts?.value, notifications?.facts?.value, updates?.facts?.value)

    public val state: StateFlow<SettingsUi> =
        combine(local, liveFacts, prefs, engines) { l, f, p, e -> build(l, f, p, e) }
            .stateIn(scope, SharingStarted.Eagerly, build(local.value, currentFacts, prefs.value, engineNow()))

    override fun onSearchEntries(entries: List<SettingsSearchEntry>) {
        local.update { it.copy(entries = entries) }
    }

    private fun build(l: Local, f: SettingsFacts, p: PersonalPrefs, e: Engines): SettingsUi {
        val searchable = SettingsStructure.searchable(f)
        val results =
            if (l.query.isBlank()) persistentListOf()
            else SettingsSearch.find(l.query, l.entries.filter { it.row in searchable })
        val placesSearchable = SettingsPlaces.searchable(f)
        val places =
            if (l.query.isBlank()) persistentListOf()
            else SettingsSearch.findPlaces(l.query, l.placeEntries.filter { it.place in placesSearchable })
        val top = l.stack.last()
        val group =
            (l.stack.last() as? SettingsPage.Group)?.let { g ->
                GroupUi(g.id, SettingsStructure.rows(g.id, f, p.reasoning, p.timestamps))
            }
        return SettingsUi(
            stack = l.stack,
            hub = HubUi(SettingsStructure.groups(f), f.server, l.query, results, searchable, places, placesSearchable),
            group = group,
            appearance = appearance(p, f, l.allPacks),
            voice = e.voice,
            notifications = e.notifications,
            updates = e.updates,
            about = about,
            privacy = e.collect?.let { PrivacyUi(it, l.consentSaving) },
            language = p.language,
            report = l.report,
            note = l.note,
            focus = l.focus,
            capabilities = (top as? SettingsPage.ServerCapabilities)?.let { CapabilitiesUi.of(f, it.focus) },
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
        )
    }

    override fun onOpen(page: SettingsPage) {
        open(page, focus = null)
    }

    private fun open(page: SettingsPage, focus: String?) {
        local.update {
            val stack = if (it.stack.last() == page) it.stack else (it.stack + page).toImmutableList()
            it.copy(stack = stack, focus = focus)
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
        run(SettingsNote.ChoiceNotSaved) { port.set(NotificationChange.Quiet(if (on) QuietHours.Default else null)) }
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
                local.update { it.copy(stack = it.stack.dropLast(1).toImmutableList(), focus = null) }
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
            SettingsStructure.rows(row.group, currentFacts, p.reasoning, p.timestamps).firstOrNull { it.id == row }?.door
                ?: return
        when (door) {
            is SettingsDoor.Page -> onOpen(door.page)
            is SettingsDoor.Outside -> openers.open(door.place)
            is SettingsDoor.Switch ->
                when (row) {
                    SettingsRowId.ShowReasoning -> write(PersonalChange.Reasoning(!door.on), SettingsNote.ChoiceNotSaved)
                    SettingsRowId.ShowTimestamps -> write(PersonalChange.Timestamps(!door.on), SettingsNote.ChoiceNotSaved)
                    SettingsRowId.PhraseChips -> phraseChips?.let { port -> run(SettingsNote.ChoiceNotSaved) { port.setEnabled(!door.on) } }
                    else -> Unit
                }
        }
    }

    override fun onPlaceEntries(entries: List<SettingsPlaceEntry>) {
        local.update { it.copy(placeEntries = entries) }
    }

    /** A row result opens like the row (its page, or its owner's place); a switch opens its group at it, unflipped. */
    override fun onSearchResult(row: SettingsRowId) {
        val p = prefs.value
        val door = SettingsStructure.rows(row.group, currentFacts, p.reasoning, p.timestamps).firstOrNull { it.id == row }?.door
        if (door is SettingsDoor.Switch) land(SettingsTarget.Row(row)) else onRow(row)
    }

    override fun onPlace(place: SettingsPlaceId) {
        if (SettingsPlaces.available(place, currentFacts)) land(SettingsPlaces.target(place))
    }

    override fun onDeepLink(tag: String) {
        SettingsPlaces.resolve(tag, currentFacts)?.let(::land)
    }

    /** Opens a target from the hub: the row's group arrived at the row, a page at its section, or the owner's place. */
    private fun land(target: SettingsTarget) {
        when (target) {
            is SettingsTarget.Row -> {
                local.update { it.copy(stack = persistentListOf(SettingsPage.Hub)) }
                open(SettingsPage.Group(target.row.group), focus = target.row.tag)
            }
            is SettingsTarget.Page -> {
                local.update { it.copy(stack = persistentListOf(SettingsPage.Hub)) }
                open(target.page, focus = target.section?.tag)
            }
            is SettingsTarget.Outside -> openers.open(target.place)
        }
    }

    override fun onQuery(text: String) {
        local.update { it.copy(query = text) }
    }

    override fun onOutside(place: SettingsOutside) {
        openers.open(place)
    }

    override fun onMode(mode: AppearanceMode) {
        if (prefs.value.mode != mode) write(PersonalChange.Mode(mode), SettingsNote.AppearanceNotSaved)
    }

    override fun onPack(pack: PackId) {
        if (prefs.value.pack != pack) write(PersonalChange.Pack(pack), SettingsNote.AppearanceNotSaved)
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
                it.copy(consentSaving = false, note = if (outcome is Outcome.Failed) SettingsNote.ConsentNotSaved else it.note)
            }
        }
    }

    override fun onTextSize(size: AppTextSize) {
        if (prefs.value.textSize != size) write(PersonalChange.TextSize(size), SettingsNote.AppearanceNotSaved)
    }

    override fun onLanguage(language: AppLanguage) {
        if (prefs.value.language != language) write(PersonalChange.Language(language), SettingsNote.ChoiceNotSaved)
    }

    override fun onRetryReport() {
        loadReport()
    }

    override fun onCopyReport() {
        val port = diagnostics ?: return
        val text = (local.value.report as? ReportUi.Ready)?.text ?: return
        scope.launch {
            val note = if (port.copy(text) is Outcome.Ok) SettingsNote.ReportCopied else SettingsNote.ShareFailed
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
        run(failed) { choices.set(change).map { } }
    }

    private fun run(failed: SettingsNote, call: suspend () -> Outcome<Unit>) {
        scope.launch {
            if (call() is Outcome.Failed) local.update { it.copy(note = failed) }
        }
    }
}
