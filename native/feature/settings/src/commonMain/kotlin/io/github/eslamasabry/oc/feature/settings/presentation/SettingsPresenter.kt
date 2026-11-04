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
    private val appearanceHelp: AppearanceHelp? = null,
    private val serverPage: SettingsServerDoor? = null,
    private val localData: LocalDataSettings? = null,
    private val phrases: PhraseSettings? = null,
    private val arrivals: io.github.eslamasabry.oc.engine.settings.teaching.ShowMeTargets? = null,
) : SettingsActions {
    private data class Local(
        val stack: ImmutableList<SettingsPage> = persistentListOf(SettingsPage.Hub),
        val query: String = "",
        val entries: List<SettingsSearchEntry> = emptyList(),
        val allPacks: Boolean = false,
        val note: SettingsNote? = null,
        val consentSaving: Boolean = false,
        val report: ReportUi? = null,
        val newsStep: Int = 0,
        val discardBusy: Boolean = false,
        val discardConfirm: LocalDataKind? = null,
        val discardResult: LocalDeleteResult? = null,
        val discardFailed: Boolean = false,
    )

    private data class Engines(
        val collect: WhatWeCollect?,
        val voice: VoiceFacts?,
        val notifications: NotificationFacts?,
        val updates: UpdateFacts?,
        val guide: AppearanceHelpFacts?,
        val data: LocalDataFacts?,
        val phrases: PhraseSettingsFacts?,
    )

    private val local = MutableStateFlow(Local())
    private val currentFacts = facts
    private val prefs = choices.value

    private data class Extra(
        val guide: AppearanceHelpFacts?,
        val data: LocalDataFacts?,
        val phrases: PhraseSettingsFacts?,
    )

    private val extra =
        combine(
            appearanceHelp?.facts ?: flowOf(null),
            localData?.facts ?: flowOf(null),
            phrases?.facts ?: flowOf(null),
        ) { g, d, p ->
            Extra(g, d, p)
        }
    private val engines =
        combine(
            analytics?.whatWeCollect ?: flowOf(null),
            voice?.facts ?: flowOf(null),
            notifications?.facts ?: flowOf(null),
            updates?.facts ?: flowOf(null),
            extra,
        ) { c, v, n, u, x ->
            Engines(c, v, n, u, x.guide, x.data, x.phrases)
        }

    private fun engineNow() =
        Engines(
            analytics?.whatWeCollect?.value,
            voice?.facts?.value,
            notifications?.facts?.value,
            updates?.facts?.value,
            appearanceHelp?.facts?.value,
            localData?.facts?.value,
            phrases?.facts?.value,
        )

    public val state: StateFlow<SettingsUi> =
        combine(local, facts, prefs, engines) { l, f, p, e -> build(l, f, p, e) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                build(local.value, facts.value, prefs.value, engineNow()),
            )

    override fun onShowHiddenPhrases() {
        val port = phrases ?: return
        val id = port.facts.value?.profile ?: return
        run(SettingsNote.ChoiceNotSaved) { port.showHidden(id) }
    }

    override fun onAskDiscard(kind: LocalDataKind) {
        if (!local.value.discardBusy && localData != null)
            local.update {
                it.copy(discardConfirm = kind, discardFailed = false, discardResult = null)
            }
    }

    override fun onCancelDiscard() {
        local.update { it.copy(discardConfirm = null) }
    }

    override fun onConfirmDiscard() {
        val kind = local.value.discardConfirm ?: return
        val port = localData ?: return
        if (local.value.discardBusy) return
        local.update {
            it.copy(
                discardBusy = true,
                discardConfirm = null,
                discardResult = null,
                discardFailed = false,
            )
        }
        scope.launch {
            val result = port.discard(kind)
            local.update {
                it.copy(
                    discardBusy = false,
                    discardResult = (result as? Outcome.Ok)?.value,
                    discardFailed = result is Outcome.Failed,
                )
            }
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
                GroupUi(
                    g.id,
                    SettingsStructure.rows(
                        g.id,
                        f,
                        p.reasoning,
                        p.timestamps,
                        e.phrases?.enabled ?: true,
                    ),
                )
            }
        return SettingsUi(
            stack = l.stack,
            hub =
                HubUi(
                    SettingsStructure.groups(f),
                    f.server,
                    l.query,
                    results,
                    searchable,
                    f.savedServers,
                ),
            group = group,
            appearance = appearance(p, f, l.allPacks).copy(guide = e.guide),
            voice = e.voice,
            notifications = e.notifications,
            updates = e.updates,
            about = about,
            privacy = e.collect?.let { PrivacyUi(it, l.consentSaving) },
            localData =
                localData?.let {
                    LocalDataUi(
                        e.data,
                        l.discardBusy,
                        l.discardConfirm,
                        l.discardResult,
                        l.discardFailed,
                    )
                },
            phrases = e.phrases,
            language = p.language,
            report = l.report,
            note = l.note,
            newsStep = l.newsStep,
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
            accent = p.accent,
            frost = p.frost,
            corners = p.corners,
            density = p.density,
            activityGlow = p.activityGlow,
            glowStyle = p.glowStyle,
        )
    }

    override fun onOpen(page: SettingsPage) {
        local.update {
            if (it.stack.last() == page) it
            else
                it.copy(
                    stack = (it.stack + page).toImmutableList(),
                    newsStep = if (page == SettingsPage.WhatsNew) 0 else it.newsStep,
                )
        }
        if (page == SettingsPage.ReportProblem) loadReport()
        if (page == SettingsPage.Notifications) notifications?.refresh()
        if (page == SettingsPage.Privacy) localData?.refresh()
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
        val serverSection =
            when (row) {
                SettingsRowId.DefaultShell -> SettingsOutside.DefaultShell
                SettingsRowId.Permissions -> SettingsOutside.Permissions
                SettingsRowId.ModelAndMode -> SettingsOutside.ModelAndMode
                SettingsRowId.Providers -> SettingsOutside.Providers
                SettingsRowId.Tools -> SettingsOutside.Tools
                else -> null
            }
        if (
            serverSection != null && currentFacts.value.savedServers.size > 1 && serverPage != null
        ) {
            onOpen(SettingsPage.ByServer(serverSection))
            return
        }
        val p = prefs.value
        val door =
            SettingsStructure.rows(
                    row.group,
                    currentFacts.value,
                    p.reasoning,
                    p.timestamps,
                    phrases?.facts?.value?.enabled ?: true,
                )
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
                    SettingsRowId.PhraseSuggestions ->
                        phrases?.let { port ->
                            run(SettingsNote.ChoiceNotSaved) { port.setEnabled(!door.on) }
                        }
                    else -> Unit
                }
        }
    }

    override fun onQuery(text: String) {
        local.update { it.copy(query = text) }
    }

    override fun onOutside(place: SettingsOutside) {
        if (place == SettingsOutside.WhatsNew) {
            onOpen(SettingsPage.WhatsNew)
            return
        }
        if (place == SettingsOutside.Tips) {
            onOpen(SettingsPage.Tips)
            return
        }
        openers.open(place)
    }

    override fun onSavedServer(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        section: SettingsOutside?,
    ) {
        if (currentFacts.value.savedServers.any { it.id == profile })
            serverPage?.open(profile, section)
    }

    override fun onAppearanceGuide(step: Int, done: Boolean) {
        val port = appearanceHelp ?: return
        run(SettingsNote.ChoiceNotSaved) { port.save(step.coerceIn(0, 3), done) }
    }

    override fun onShowTipsAgain() {
        val port = appearanceHelp ?: return
        run(SettingsNote.ChoiceNotSaved) { port.resetTips() }
    }

    override fun onNewsStep(index: Int) {
        if (index in 0..4) local.update { it.copy(newsStep = index) }
    }

    override fun onNewsShow() {
        val step = local.value.newsStep
        val target =
            when (step) {
                0 -> io.github.eslamasabry.oc.engine.settings.teaching.ShowMeTarget.Updates
                1 -> io.github.eslamasabry.oc.engine.settings.teaching.ShowMeTarget.Agents
                2 -> io.github.eslamasabry.oc.engine.settings.teaching.ShowMeTarget.AppearanceText
                else -> null
            }
        if (target != null) arrivals?.request(target)
        onBack()
        when (step) {
            0 -> onOutside(SettingsOutside.Updates)
            1 -> onOutside(SettingsOutside.Agents)
            2 -> {
                if (state.value.appearance.guide?.done == false) onAppearanceGuide(2, false)
                onOpen(SettingsPage.Appearance)
            }
        }
    }

    override fun onAccent(id: String?) =
        write(PersonalChange.Accent(id), SettingsNote.AppearanceNotSaved)

    override fun onFrost(strength: io.github.eslamasabry.oc.ui.design.effects.FrostStrength) =
        write(PersonalChange.Frost(strength), SettingsNote.AppearanceNotSaved)

    override fun onShape(
        corners: io.github.eslamasabry.oc.engine.settings.prefs.Corners?,
        density: io.github.eslamasabry.oc.engine.settings.prefs.Density?,
    ) {
        if (!prefs.value.saver)
            write(PersonalChange.Shape(corners, density), SettingsNote.AppearanceNotSaved)
    }

    override fun onGlow(style: io.github.eslamasabry.oc.engine.settings.prefs.GlowStyle?) =
        write(PersonalChange.Glow(style), SettingsNote.AppearanceNotSaved)

    override fun onResetAppearance() =
        write(PersonalChange.ResetAppearance, SettingsNote.AppearanceNotSaved)

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
        if (
            port.facts.value.blocked &&
                (change is NotificationChange.FinishedWork ||
                    change is NotificationChange.Questions)
        )
            return
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
        run(failed) { choices.set(change).map {} }
    }

    private fun run(failed: SettingsNote, call: suspend () -> Outcome<Unit>) {
        scope.launch {
            if (call() is Outcome.Failed) local.update { it.copy(note = failed) }
        }
    }
}
