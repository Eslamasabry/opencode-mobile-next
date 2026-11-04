package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.CancellationException

// The move's page as plain state (Flutter lib/ui/screens/termux_migration_screen.dart, its _TermuxMigrationScreenState
// and lib/ui/widgets/termux_migration_entry.dart). No Compose, no copy: words are MigrationWord keys, values are raw
// numbers and names the screen formats (sizes with formatPhoneStorage, name lists with the list-pair words).

/** The plain words for a failure code: what failed and the way forward. Never the backend's own text. */
public fun migrationFailureWords(code: TermuxMigrationFailure): MigrationWord = when (code) {
    TermuxMigrationFailure.unavailable -> MigrationWord.migrationTermuxUnavailable
    TermuxMigrationFailure.sourceChanged -> MigrationWord.migrationSourceChanged
    TermuxMigrationFailure.sourceBusy -> MigrationWord.migrationSourceBusy
    TermuxMigrationFailure.unsupportedEntry -> MigrationWord.migrationUnsupportedFiles
    TermuxMigrationFailure.linkedFileUnreadable -> MigrationWord.migrationLinkedFileUnreadable
    TermuxMigrationFailure.tooLarge -> MigrationWord.migrationTooLarge
    TermuxMigrationFailure.invalidArchive, TermuxMigrationFailure.checksumMismatch -> MigrationWord.migrationVerificationFailed
    TermuxMigrationFailure.destinationConflict -> MigrationWord.migrationDestinationChanged
    TermuxMigrationFailure.storage -> MigrationWord.migrationStorageFailed
    TermuxMigrationFailure.timedOut -> MigrationWord.migrationTimedOut
    TermuxMigrationFailure.invalidSelection -> MigrationWord.migrationSelectionChanged
    TermuxMigrationFailure.profileSwitch -> MigrationWord.migrationConnectionFailed
    TermuxMigrationFailure.cancelled -> MigrationWord.migrationCancelled
}

/** A second line for the failures whose fix is not in the first. */
public fun migrationFailureFix(code: TermuxMigrationFailure): MigrationWord? = when (code) {
    TermuxMigrationFailure.unsupportedEntry -> MigrationWord.migrationUnsupportedFilesFix
    TermuxMigrationFailure.linkedFileUnreadable -> MigrationWord.migrationLinkedFileUnreadableFix
    TermuxMigrationFailure.tooLarge -> MigrationWord.migrationTooLargeFix
    TermuxMigrationFailure.destinationConflict -> MigrationWord.migrationDestinationChangedFix
    else -> null
}

public fun migrationItemLabel(item: TermuxMigrationItem): MigrationWord = when (item) {
    TermuxMigrationItem.projects -> MigrationWord.migrationItemProjects
    TermuxMigrationItem.config -> MigrationWord.migrationItemConfig
    TermuxMigrationItem.sessions -> MigrationWord.migrationItemSessions
    TermuxMigrationItem.gitConfig -> MigrationWord.migrationItemGitConfig
    TermuxMigrationItem.shellFiles -> MigrationWord.migrationItemShellFiles
    TermuxMigrationItem.aiTeam -> MigrationWord.migrationItemAiTeam
}

public fun migrationItemWhat(item: TermuxMigrationItem): MigrationWord = when (item) {
    TermuxMigrationItem.projects -> MigrationWord.migrationItemProjectsWhat
    TermuxMigrationItem.config -> MigrationWord.migrationItemConfigWhat
    TermuxMigrationItem.sessions -> MigrationWord.migrationItemSessionsWhat
    TermuxMigrationItem.gitConfig -> MigrationWord.migrationItemGitConfigWhat
    TermuxMigrationItem.shellFiles -> MigrationWord.migrationItemShellFilesWhat
    TermuxMigrationItem.aiTeam -> MigrationWord.migrationItemAiTeamWhat
}

/** Where the page is before it follows the migration's own state. */
public enum class MigrationEntry { Loading, Unavailable, Live, Unfinished, Moved }

public enum class MigrationStepState { Waiting, Working, Done }

/** What a primary action does on the failed page. */
public enum class MigrationFailedAction { OpenBuiltin, Resume, TryAgain }

/** One switch row of the review. */
public data class MigrationItemRow(
    val item: TermuxMigrationItem,
    val label: MigrationWord,
    val on: Boolean,
    /** Null when it can be chosen. */
    val problem: TermuxMigrationFailure?,
    /** The measured size, or null when a refusal made it unknown (never the placeholder zero). */
    val bytes: Long?,
    val files: Long?,
    val below: MigrationWord,
)

/** The review's space notice: [freeBytes] null is "Free space unknown". */
public data class MigrationSpaceNotice(val requiredBytes: Long, val freeBytes: Long?, val short: Boolean)

public data class MigrationStep(val item: TermuxMigrationItem?, val state: MigrationStepState, val stage: MigrationWord?)

public sealed interface MigrationPage {
    public data object Checking : MigrationPage

    public data object Unavailable : MigrationPage

    public data class Unfinished(val items: List<TermuxMigrationItem>, val discardFailed: Boolean, val discardEnabled: Boolean) :
        MigrationPage

    public data class Review(
        val moves: List<MigrationItemRow>,
        val exports: List<MigrationItemRow>,
        /** Providers the Termux server is signed in to while connected; empty uses the generic sentence. */
        val notMovedProviderNames: List<String>,
        val space: MigrationSpaceNotice?,
        val startEnabled: Boolean,
        val startDisabledReason: MigrationWord?,
    ) : MigrationPage

    public data class Progress(val steps: List<MigrationStep>, val step: Int, val of: Int, val stage: MigrationWord) : MigrationPage

    public data class Cancelled(
        val settling: Boolean,
        val title: MigrationWord,
        val body: MigrationWord,
        /** Resume when a copy was asked; Try again after a stopped look. */
        val primary: MigrationWord,
        val discardOffered: Boolean,
        val discardFailed: Boolean,
    ) : MigrationPage

    public data class NeedsSpace(val requiredBytes: Long, val freeBytes: Long?, val body: MigrationWord, val actionsEnabled: Boolean) :
        MigrationPage

    public data class NeedsBuiltin(val settingUp: Boolean, val setupFailed: Boolean) : MigrationPage

    public data class TermuxUnreachable(val retryEnabled: Boolean) : MigrationPage

    public data class Failed(
        val code: TermuxMigrationFailure,
        val words: List<MigrationWord>,
        val item: TermuxMigrationItem?,
        val primary: MigrationFailedAction,
        val primaryEnabled: Boolean,
        val openThisPhone: Boolean,
        val discardOffered: Boolean,
        val discardFailed: Boolean,
    ) : MigrationPage

    public data class Done(
        val job: String?,
        val items: List<TermuxMigrationItem>,
        /** Fixed provider labels from the imported settings copy; empty means unknown. */
        val providerNames: List<String>,
        val signInWords: MigrationWord,
        /** `termux-<job>` when projects moved. */
        val projectsFolder: String?,
        val exports: List<TermuxMigrationItem>,
        val projectsPath: String?,
        val exportsPath: String?,
        /** A finished move offers no discard. */
        val discardOffered: Boolean = false,
    ) : MigrationPage
}

/**
 * The move page's state holder (Flutter _TermuxMigrationScreenState minus widgets). [connectedProviderNames] gives the
 * providers the Termux server reports while the app is connected to it (names only). The screen calls [open] once
 * shown, renders [page] on every [addListener] callback, and calls the actions; confirmations (Stop, Discard) are the
 * screen's sheets, shown before [stop] and [discard].
 */
public class TermuxMigrationPageModel(
    private val owner: TermuxMigrationOwner,
    private val sourceId: String,
    private val connectedProviderNames: () -> List<String> = { emptyList() },
    private val setUpBuiltin: suspend () -> Unit = {},
) {
    public var entry: MigrationEntry = MigrationEntry.Loading
        private set
    public var selected: Set<TermuxMigrationItem> = emptySet()
        private set
    private var chosen = false
    private var saved: Set<TermuxMigrationItem>? = null
    private var movedJob: String? = null
    private var names: List<String> = emptyList()
    private var namesLoaded = false
    private var namesPending = false
    private var discardFailed = false
    private var settingUp = false
    private var setupFailed = false
    private val listeners = ArrayList<() -> Unit>()
    private val ownerListener: () -> Unit = { changed() }

    init {
        owner.addListener(ownerListener)
    }

    public fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    public fun close() {
        owner.removeListener(ownerListener)
        listeners.clear()
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) listener()
    }

    private fun changed() {
        val source = owner.snapshot?.source
        // The review starts with projects on and every private copy off.
        if (!chosen && source != null && owner.source == sourceId) {
            selected = source.items.filter { it.item == TermuxMigrationItem.projects && migrationItemProblem(it) == null }.map { it.item }.toSet()
        }
        if (entry == MigrationEntry.Live && !namesLoaded && owner.source == sourceId && owner.snapshot?.phase == TermuxMigrationPhase.done) {
            namesPending = true
        }
        notifyListeners()
    }

    /** Loads names a just-finished copy found; the screen calls it when [needsNames] turns true. */
    public val needsNames: Boolean get() = namesPending && !namesLoaded

    public suspend fun loadNames() {
        namesLoaded = true
        namesPending = false
        names = owner.providerNames(sourceId)
        notifyListeners()
    }

    private fun set(change: () -> Unit) {
        change()
        notifyListeners()
    }

    public suspend fun open() {
        val c = owner.obtain()
        if (c == null) return set { entry = MigrationEntry.Unavailable }
        // A copy for this server runs or just ended: show it as it is.
        val ended = setOf(TermuxMigrationPhase.cancelled, TermuxMigrationPhase.failed, TermuxMigrationPhase.done)
        if (owner.source == sourceId && (owner.busy || c.snapshot.phase in ended)) return set { entry = MigrationEntry.Live }
        // Whether the move finished is the backend's record; one that cannot be read is unknown, not "unfinished".
        val moved = when (val answer = owner.completedJob(sourceId)) {
            is MigrationAnswer.Ok -> answer.value
            is MigrationAnswer.Refused -> return set { entry = MigrationEntry.Unavailable }
        }
        val savedSelection = owner.savedSelection(sourceId)
        if (moved != null) {
            val found = owner.providerNames(sourceId)
            return set {
                saved = savedSelection
                movedJob = moved.jobId
                names = found
                namesLoaded = true
                entry = MigrationEntry.Moved
            }
        }
        if (savedSelection != null) {
            return set {
                saved = savedSelection
                entry = MigrationEntry.Unfinished
            }
        }
        set { entry = MigrationEntry.Live }
        owner.check(sourceId, selected)
        loadNamesIfDone()
    }

    private suspend fun loadNamesIfDone() {
        if (needsNames) loadNames()
    }

    /** Try again on the unavailable page. */
    public suspend fun reopen() {
        set { entry = MigrationEntry.Loading }
        open()
    }

    public fun toggle(item: TermuxMigrationItem, on: Boolean) {
        val row = owner.snapshot?.source?.items?.firstOrNull { it.item == item } ?: return
        if (migrationItemProblem(row) != null) return
        chosen = true
        set { selected = if (on) selected + item else selected - item }
    }

    public suspend fun start() {
        if (selected.isEmpty() || owner.busy) return
        if (owner.reviewSpace(selected)?.sufficient == false) return
        owner.start(sourceId, selected)
        loadNamesIfDone()
    }

    public suspend fun resume() {
        if (owner.busy) return
        set { entry = MigrationEntry.Live }
        owner.resume(sourceId, fallback = saved ?: owner.items.toSet())
        loadNamesIfDone()
    }

    /** Repeats what was asked last: a look, or the copy from where it stopped. */
    public suspend fun retry() {
        if (owner.busy) return
        if (owner.request == null || owner.request == TermuxMigrationRequest.Check) owner.check(sourceId) else resume()
    }

    /** Choose fewer items: a fresh look keeping the person's choice. */
    public suspend fun chooseFewer() {
        if (owner.busy) return
        chosen = true
        owner.check(sourceId, selected)
    }

    /** After the Stop sheet was confirmed. */
    public suspend fun stop() {
        owner.cancel()
    }

    /** After the Discard sheet was confirmed. */
    public suspend fun discard() {
        set { discardFailed = false }
        val result = owner.discard(sourceId)
        if (result == null) return set { discardFailed = true }
        // Back to the start: a fresh look, then a new choice of what to copy.
        set {
            selected = emptySet()
            chosen = false
            saved = null
            movedJob = null
            names = emptyList()
            namesLoaded = false
            namesPending = false
            entry = MigrationEntry.Loading
        }
        open()
    }

    public suspend fun setUp() {
        if (settingUp || owner.busy) return
        set {
            settingUp = true
            setupFailed = false
        }
        val failed = try {
            setUpBuiltin()
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
        set {
            settingUp = false
            setupFailed = failed
        }
        if (!failed) owner.check(sourceId, selected)
    }

    /** True while Back must ask first (a copy runs). */
    public val confirmBeforeLeaving: Boolean get() = owner.source == sourceId && owner.copying

    public fun page(): MigrationPage = when (entry) {
        MigrationEntry.Loading -> MigrationPage.Checking
        MigrationEntry.Unavailable -> MigrationPage.Unavailable
        MigrationEntry.Unfinished -> MigrationPage.Unfinished(TermuxMigrationOwner.ordered(saved ?: emptySet()), discardFailed, !owner.busy)
        MigrationEntry.Moved -> done(movedJob, TermuxMigrationOwner.ordered(saved ?: emptySet()))
        MigrationEntry.Live -> live()
    }

    private fun live(): MigrationPage {
        val snapshot = owner.snapshot
        if (snapshot == null || owner.source != sourceId) return MigrationPage.Checking
        val copying = owner.copying
        return when {
            snapshot.phase == TermuxMigrationPhase.done -> done(snapshot.jobId, owner.items)
            snapshot.phase == TermuxMigrationPhase.failed -> failed(snapshot)
            snapshot.phase == TermuxMigrationPhase.cancelled -> cancelled()
            snapshot.phase == TermuxMigrationPhase.needsSpace -> {
                val free = snapshot.availableBytes
                MigrationPage.NeedsSpace(
                    requiredBytes = snapshot.requiredBytes ?: 0,
                    freeBytes = free,
                    body = if (free == null) MigrationWord.migrationNeedsSpaceBodyUnknown else MigrationWord.migrationNeedsSpaceBody,
                    actionsEnabled = !owner.busy,
                )
            }
            snapshot.phase == TermuxMigrationPhase.needsBuiltin -> MigrationPage.NeedsBuiltin(settingUp, setupFailed)
            snapshot.phase == TermuxMigrationPhase.termuxUnreachable -> MigrationPage.TermuxUnreachable(!owner.busy)
            copying -> progress(snapshot)
            snapshot.phase == TermuxMigrationPhase.ready && snapshot.source != null -> review(snapshot.source)
            else -> MigrationPage.Checking
        }
    }

    private fun cancelled(): MigrationPage {
        val settling = owner.busy
        val resumable = owner.request != TermuxMigrationRequest.Check
        return MigrationPage.Cancelled(
            settling = settling,
            title = if (settling) MigrationWord.migrationStopping else MigrationWord.migrationCancelled,
            body = if (owner.stoppedByLeaving) MigrationWord.migrationStoppedLeaving else MigrationWord.migrationCancelledBody,
            primary = if (resumable) MigrationWord.migrationResume else MigrationWord.commonRetry,
            discardOffered = resumable,
            discardFailed = discardFailed,
        )
    }

    private fun failed(snapshot: TermuxMigrationSnapshot): MigrationPage {
        val code = snapshot.failure ?: TermuxMigrationFailure.storage
        val primary = when (code) {
            TermuxMigrationFailure.destinationConflict -> MigrationFailedAction.OpenBuiltin
            TermuxMigrationFailure.invalidSelection -> MigrationFailedAction.Resume
            else -> MigrationFailedAction.TryAgain
        }
        return MigrationPage.Failed(
            code = code,
            words = listOfNotNull(migrationFailureWords(code), migrationFailureFix(code)),
            item = snapshot.item,
            primary = primary,
            primaryEnabled = primary == MigrationFailedAction.OpenBuiltin || !owner.busy,
            openThisPhone = code == TermuxMigrationFailure.profileSwitch,
            discardOffered = snapshot.jobId != null,
            discardFailed = discardFailed,
        )
    }

    private fun done(job: String?, items: List<TermuxMigrationItem>): MigrationPage {
        val exports = items.filter { it != TermuxMigrationItem.projects }
        val folder = job?.let { "termux-$it" }
        val projects = TermuxMigrationItem.projects in items
        return MigrationPage.Done(
            job = job,
            items = items,
            providerNames = names,
            signInWords = if (names.isEmpty()) MigrationWord.migrationSignInAgainAny else MigrationWord.migrationSignInAgainNamed,
            projectsFolder = if (projects) folder else null,
            exports = exports,
            projectsPath = if (projects && folder != null) "/root/projects/$folder" else null,
            exportsPath = if (exports.isNotEmpty() && job != null) "/root/.oc-migration-exports/$job" else null,
        )
    }

    private val stageWords = mapOf(
        TermuxMigrationPhase.packing to MigrationWord.migrationPacking,
        TermuxMigrationPhase.copying to MigrationWord.migrationCopying,
        TermuxMigrationPhase.unpacking to MigrationWord.migrationUnpacking,
        TermuxMigrationPhase.verifying to MigrationWord.migrationVerifying,
        TermuxMigrationPhase.switching to MigrationWord.migrationSwitching,
    )

    private fun progress(snapshot: TermuxMigrationSnapshot): MigrationPage {
        val items = owner.items
        val switching = snapshot.phase == TermuxMigrationPhase.switching
        val current = snapshot.item
        val at = when {
            switching -> items.size
            current == null -> 0
            else -> items.indexOf(current).coerceIn(0, items.size)
        }
        // Before the first item (Termux and space are checked again) every step waits under the stage's words.
        val started = switching || current != null
        val stage = stageWords[snapshot.phase] ?: MigrationWord.migrationChecking
        val steps = items.mapIndexed { i, item ->
            MigrationStep(
                item = item,
                state = when {
                    !started || i > at -> MigrationStepState.Waiting
                    i < at -> MigrationStepState.Done
                    else -> MigrationStepState.Working
                },
                stage = if (started && i == at) stage else null,
            )
        } + MigrationStep(null, if (switching) MigrationStepState.Working else MigrationStepState.Waiting, if (switching) stage else null)
        return MigrationPage.Progress(steps = steps, step = at + 1, of = items.size + 1, stage = stage)
    }

    private fun row(size: TermuxMigrationSize): MigrationItemRow {
        val problem = migrationItemProblem(size)
        return MigrationItemRow(
            item = size.item,
            label = migrationItemLabel(size.item),
            on = size.item in selected && problem == null,
            problem = problem,
            bytes = if (size.problem == null) size.bytes else null,
            files = if (size.problem == null) size.files else null,
            below = problem?.let(::migrationFailureFix) ?: migrationItemWhat(size.item),
        )
    }

    private fun review(source: TermuxMigrationSource): MigrationPage {
        val moves = source.items.filter { it.disposition == TermuxMigrationDisposition.Migrate }.map(::row)
        val exports = TermuxMigrationItem.entries.flatMap { item ->
            source.items.filter { it.item == item && it.disposition == TermuxMigrationDisposition.PrivateExport }
        }.map(::row)
        val chosenRows = source.items.filter { it.item in selected && migrationItemProblem(it) == null }
        val space = if (chosenRows.isEmpty()) null else owner.reviewSpace(selected)
        val short = selected.isNotEmpty() && owner.reviewSpace(selected)?.sufficient == false
        return MigrationPage.Review(
            moves = moves,
            exports = exports,
            notMovedProviderNames = connectedProviderNames(),
            space = space?.let { MigrationSpaceNotice(it.requiredBytes ?: 0, it.availableBytes, it.sufficient == false) },
            startEnabled = selected.isNotEmpty() && !short && !owner.busy,
            startDisabledReason = when {
                selected.isEmpty() -> MigrationWord.migrationChooseOne
                short -> MigrationWord.migrationReviewSpaceShort
                else -> null
            },
        )
    }
}

/** What This phone's row says (Flutter TermuxMigrationRow): the move, Resume, Moving…, or Moved. */
public data class MigrationRowState(val title: MigrationWord, val detail: MigrationWord, val running: Boolean, val moved: Boolean)

/**
 * This phone's row for a Termux user. It reads what was saved when it appears ([read]); it never looks at Termux by
 * itself.
 */
public class TermuxMigrationRowModel(private val owner: TermuxMigrationOwner, private val profileId: String) {
    private var moved = false
    private var unfinished = false

    public suspend fun read() {
        owner.obtain()
        // Finished is the backend's record; one that cannot be read leaves the plain row, and the page says why.
        moved = owner.completedJob(profileId).valueOrNull() != null
        unfinished = !moved && owner.savedSelection(profileId) != null
    }

    public fun state(): MigrationRowState {
        val running = owner.copying && owner.source == profileId
        val (title, detail) = when {
            running -> MigrationWord.migrationRowRunning to MigrationWord.migrationKeepOpen
            moved -> MigrationWord.migrationDoneTitle to MigrationWord.migrationRowDoneBody
            unfinished -> MigrationWord.migrationRowResume to MigrationWord.migrationRowResumeBody
            else -> MigrationWord.migrationTitle to MigrationWord.migrationRowBody
        }
        return MigrationRowState(title, detail, running, moved)
    }
}

/**
 * The entry rules (Flutter termux_migration_entry.dart, TermuxMigrationService.shouldOffer/dismissOffer): which profile
 * offers the move, and the one-time Servers offer whose dismissal is `oc.termuxMigrationOffer.<id>`, swept with the
 * profile like every scoped key.
 */
public object TermuxMigrationOffers {
    public fun offerKey(profileId: String): String = "oc.termuxMigrationOffer.$profileId"

    /** A Termux server this phone can move to the in-app server (only where the in-app Linux runs). */
    public fun offersTermuxMigration(profile: MigrationProfile?, builtinLinuxSupported: Boolean): Boolean =
        profile != null && builtinLinuxSupported && termuxMigrationEligible(profile)

    /** The offer shows until dismissed for that server, and is gone once a copy was saved (its journal exists). */
    public fun shouldOffer(profiles: List<MigrationProfile>, preferences: MigrationPreferences, profileId: String): Boolean =
        profiles.any { it.id == profileId && termuxMigrationEligible(it) } &&
            preferences.getBoolean(offerKey(profileId)) != true &&
            !preferences.contains(PreferencesTermuxMigrationJournal.key(profileId))

    /** The first profile on Servers that gets the offer, or null. */
    public fun offerSource(
        profiles: List<MigrationProfile>,
        preferences: MigrationPreferences,
        termuxSupported: Boolean,
        builtinLinuxSupported: Boolean,
    ): MigrationProfile? {
        if (!termuxSupported) return null
        val source = profiles.firstOrNull { offersTermuxMigration(it, builtinLinuxSupported) } ?: return null
        return source.takeIf { shouldOffer(profiles, preferences, it.id) }
    }

    /** Remembers the dismissal; [mayWrite] false (the profile is being deleted) writes nothing and leaves nothing. */
    public fun dismiss(preferences: MigrationPreferences, profileId: String, mayWrite: () -> Boolean): MigrationAnswer<Unit> {
        if (!mayWrite()) return MigrationAnswer.Refused(TermuxMigrationFailure.cancelled)
        if (!preferences.setBoolean(offerKey(profileId), true)) return MigrationAnswer.Refused(TermuxMigrationFailure.storage)
        if (!mayWrite()) {
            preferences.remove(offerKey(profileId))
            return MigrationAnswer.Refused(TermuxMigrationFailure.cancelled)
        }
        return MigrationAnswer.Ok(Unit)
    }

    /** Every key the move keeps for [profileId], for the deletion sweep: the offer flag and the journal, nothing else. */
    public fun scopedKeyNames(profileId: String): Set<String> = setOf(offerKey(profileId), PreferencesTermuxMigrationJournal.key(profileId))

    /** The scoped keys present now (what the sweep removes). */
    public fun profileScopedKeys(preferences: MigrationPreferences, profileId: String): Set<String> =
        scopedKeyNames(profileId).filterTo(LinkedHashSet()) { preferences.contains(it) }
}
