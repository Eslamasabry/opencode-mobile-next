package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.termux.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.engine.termuxhost.TermuxAnswer
import io.github.eslamasabry.oc.engine.termuxhost.TermuxBridge
import io.github.eslamasabry.oc.engine.termuxhost.TermuxProblem
import io.github.eslamasabry.oc.engine.termuxhost.TermuxRetryWait
import io.github.eslamasabry.oc.feature.linux.presentation.*
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneTermuxMigration
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneTermuxMigrationOffer
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneTermuxProblem
import io.github.eslamasabry.oc.ui.routes.*
import io.github.eslamasabry.oc.ui.words.res.*
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.getString

@ContributesTo(AppScope::class)
interface TermuxMigrationGraph {
    val termuxMigration: AndroidTermuxMigration
}

val AndroidAppGraph.termuxMigration: AndroidTermuxMigration
    get() = (this as TermuxMigrationGraph).termuxMigration

@BindingContainer
@ContributesTo(AppScope::class)
object TermuxMigrationBindings {
    @Provides fun offers(migration: AndroidTermuxMigration): LinuxMigrationOffers = migration

    @Provides fun sessions(migration: AndroidTermuxMigration): LinuxMigrationSessions = migration

    @Provides fun phone(migration: AndroidTermuxMigration): PhoneTermuxMigration = migration

    @Provides @IntoSet fun closer(migration: AndroidTermuxMigration): ProfileCloser = migration

    @Provides
    @IntoSet
    fun cleaner(migration: AndroidTermuxMigration): ProfileResourceCleaner = migration
}

/** One foreground owner, above routes. Only the migrate-out commands can reach Termux. */
@Inject
@SingleIn(AppScope::class)
class AndroidTermuxMigration(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    scope: AppCoroutineScope,
    redactor: Redactor,
    private val clock: Clock,
    private val serverSource: Lazy<PhoneServerSource>,
    private val navigator: AppNavigator,
    profileScope: ProfileScope,
) :
    LinuxMigrationOffers,
    LinuxMigrationSessions,
    PhoneTermuxMigration,
    ProfileCloser,
    ProfileResourceCleaner {
    private val scope = CoroutineScope(scope.coroutineContext + dispatchers.main)
    private val mutableOffers = MutableStateFlow<List<LinuxMigrationOffer>>(emptyList())
    override val offers: StateFlow<List<LinuxMigrationOffer>> = mutableOffers.asStateFlow()
    private val phoneOffers = MutableStateFlow<List<PhoneTermuxMigrationOffer>>(emptyList())
    override val migration: StateFlow<List<PhoneTermuxMigrationOffer>> = phoneOffers.asStateFlow()
    private val closing = mutableSetOf<String>()
    private var active: ComponentActivity? = null
    private val launchers =
        java.util.WeakHashMap<ComponentActivity, ActivityResultLauncher<String>>()
    private var permissionResult: CompletableDeferred<Boolean>? = null
    private var blocked = false
    private var commandProblem: TermuxProblem? = null
    private val sessions = mutableSetOf<Session>()
    private val channel = AndroidTermuxChannel(app, dispatchers, ::requestPermission)
    private val bridge = TermuxBridge(true, channel, redactor, TermuxRetryWait { delay(it) })
    private val migrationOwner = TermuxMigrationOwner(this.scope) { createController() }

    init {
        profileScope.register(
            KeyShape(
                "oc.termuxMigration.",
                KeyScope.Profile,
                PrefType.Str,
                ":android:termux",
                "move journal",
            )
        )
        profileScope.register(
            KeyShape(
                "oc.termuxMigrationOffer.",
                KeyScope.Profile,
                PrefType.Bool,
                ":android:termux",
                "move offer",
            )
        )
        profileScope.register(
            KeyShape(
                ACCESS_BLOCKED,
                KeyScope.App,
                PrefType.Bool,
                ":android:termux",
                "Android run-command denial",
            )
        )
        this.scope.launch {
            if (stores.warm() is Outcome.Ok) {
                blocked = stores.prefs.bool(ACCESS_BLOCKED) == true
                stores.profiles.profiles.collect { refresh() }
            }
        }
    }

    fun attach(activity: ComponentActivity) {
        if (!launchers.containsKey(activity))
            launchers[activity] =
                activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                    granted ->
                    blocked = !granted && !activity.shouldShowRequestPermissionRationale(PERMISSION)
                    val result = permissionResult
                    scope.launch {
                        stores.prefs.commit(stores.lanes.app) { putBool(ACCESS_BLOCKED, blocked) }
                        result?.complete(granted)
                        if (permissionResult === result) permissionResult = null
                    }
                }
        active = activity
    }

    fun activate(activity: ComponentActivity) {
        active = activity
        scope.launch {
            if (stores.warm() is Outcome.Ok) refresh()
            sessions.toList().forEach { if (!migrationOwner.busy) it.open() }
        }
    }

    fun detach(activity: ComponentActivity) {
        launchers.remove(activity)?.unregister()
        if (active === activity) {
            active = null
            permissionResult?.complete(false)
            permissionResult = null
        }
    }

    fun onAppLeftForeground() {
        migrationOwner.onAppLeftForeground()
    }

    private suspend fun requestPermission(): String =
        withContext(dispatchers.main) {
            if (app.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                blocked = false
                stores.prefs.commit(stores.lanes.app) { putBool(ACCESS_BLOCKED, false) }
                return@withContext "granted"
            }
            if (blocked) return@withContext "permanentlyDenied"
            val activity = active ?: return@withContext "denied"
            val launcher = launchers[activity] ?: return@withContext "denied"
            if (permissionResult != null) return@withContext "denied"
            val result = CompletableDeferred<Boolean>()
            permissionResult = result
            try {
                launcher.launch(PERMISSION)
                if (result.await()) "granted" else if (blocked) "permanentlyDenied" else "denied"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "denied"
            } finally {
                if (permissionResult === result) permissionResult = null
            }
        }

    override suspend fun refresh() {
        if (stores.warm() !is Outcome.Ok) return
        mutableOffers.value =
            stores.profiles.profiles.value.mapNotNull { loaded ->
                val profile = migrationProfile(loaded.profile)
                if (termuxMigrationEligible(profile) && profile.id !in closing)
                    LinuxMigrationOffer(profile.id, loaded.profile.name)
                else null
            }
        val problem =
            when (channel.state()) {
                GitRepairTermuxState.Missing -> PhoneTermuxProblem.Missing
                GitRepairTermuxState.Permission ->
                    if (blocked) PhoneTermuxProblem.Blocked else PhoneTermuxProblem.Permission
                GitRepairTermuxState.Unavailable -> PhoneTermuxProblem.Unavailable
                GitRepairTermuxState.Ready ->
                    when (commandProblem) {
                        TermuxProblem.OtherAppsOff -> PhoneTermuxProblem.OtherAppsOff
                        TermuxProblem.Asleep -> PhoneTermuxProblem.Asleep
                        else -> null
                    }
            }
        phoneOffers.value =
            mutableOffers.value.map {
                PhoneTermuxMigrationOffer(it.profileId, it.displayName, problem)
            }
    }

    override suspend fun fix(profileId: String) {
        if (mutableOffers.value.none { it.profileId == profileId }) return
        when (channel.state()) {
            GitRepairTermuxState.Permission ->
                if (blocked) channel.openSettings() else requestPermission()
            else -> {
                copyExternalAppsFix()
                channel.openTermux()
            }
        }
        refresh()
    }

    override fun open(profileId: String) {
        if (mutableOffers.value.any { it.profileId == profileId })
            navigator.open(LinuxRoute.Migration(ProfileId(profileId)))
    }

    private fun copyExternalAppsFix() {
        if (commandProblem != TermuxProblem.OtherAppsOff) return
        val clipboard =
            app.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                "Termux",
                "mkdir -p ~/.termux; printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties; termux-reload-settings",
            )
        )
    }

    private fun exists(id: String): Boolean =
        id !in closing && stores.profiles.profile(ProfileId(id)) != null

    private fun migrationProfile(p: StoredProfile): MigrationProfile =
        MigrationProfile(p.id.raw, p.backend == BackendKind.OpenCode2, p.baseUrl, "v2")

    private fun createController(): TermuxMigrationController {
        val journal =
            object : TermuxMigrationJournal {
                override suspend fun read(sourceProfileId: String): JsonObject? =
                    stores.prefs
                        .string(PreferencesTermuxMigrationJournal.key(sourceProfileId))
                        ?.let { Json.parseToJsonElement(it) as JsonObject }

                override suspend fun remove(sourceProfileId: String) {
                    check(
                        stores.prefs.commit(stores.lanes.forProfile(ProfileId(sourceProfileId))) {
                            remove(PreferencesTermuxMigrationJournal.key(sourceProfileId))
                        } is Outcome.Ok
                    )
                }

                override suspend fun write(sourceProfileId: String, value: JsonObject) {
                    check(exists(sourceProfileId))
                    check(
                        stores.prefs.commit(stores.lanes.forProfile(ProfileId(sourceProfileId))) {
                            putString(
                                PreferencesTermuxMigrationJournal.key(sourceProfileId),
                                value.toString(),
                            )
                        } is Outcome.Ok
                    )
                    check(exists(sourceProfileId))
                }
            }
        val profiles =
            object : MigrationProfiles {
                override fun profile(id: String): MigrationProfile? =
                    stores.profiles.profile(ProfileId(id))?.profile?.let(::migrationProfile)

                override fun selectedProfileId(): String? = stores.profiles.activeId.value?.raw

                override fun locationDirectory(profileId: String): String? =
                    (stores.prefs.string("oc.location.$profileId")?.let(DartJson::decodeOrNull)
                            as? Map<*, *>)
                        ?.get("directory") as? String

                override suspend fun setLocationDirectory(profileId: String, directory: String) {
                    check(
                        stores.prefs.commit(stores.lanes.forProfile(ProfileId(profileId))) {
                            putString(
                                "oc.location.$profileId",
                                DartJson.encode(
                                    mapOf("directory" to directory, "workspace" to null)
                                ),
                            )
                        } is Outcome.Ok
                    )
                }

                override suspend fun ensureBuiltinProfile(flavor: String): MigrationProfile {
                    check(flavor == "v2")
                    stores.profiles.profiles.value
                        .firstOrNull {
                            it.profile.backend == BackendKind.OpenCode2 &&
                                builtinManagesServerUrl(it.profile.baseUrl)
                        }
                        ?.let {
                            return migrationProfile(it.profile)
                        }
                    val profile =
                        StoredProfile(
                            ProfileId(UUID.randomUUID().toString()),
                            getString(Res.string.phoneSetupProfileName),
                            "http://127.0.0.1:$BUILTIN_SERVER_PORT",
                            username = "opencode",
                        )
                    val password = PhoneServerScripts.newPassword(java.security.SecureRandom())
                    check(stores.profiles.upsert(profile, SecretEdit.Set(password)) is Outcome.Ok)
                    return migrationProfile(profile)
                }

                override suspend fun startBuiltin(
                    profile: MigrationProfile,
                    stillWanted: () -> Boolean,
                ): Boolean {
                    if (!stillWanted() || !exists(profile.id)) return false
                    val server = serverSource.value
                    server.refresh()
                    val ownerKey = "oc.builtinServerOwner"
                    val previous = stores.prefs.get(ownerKey)
                    val previousOwner = (previous as? PrefValue.Str)?.value
                    // A migration cannot take over another saved owner's process or credentials.
                    if (previousOwner != null && previousOwner != profile.id) return false
                    if (
                        server.facts.value.starting ||
                            (server.facts.value.status?.serverRunning == true &&
                                previousOwner == null)
                    )
                        return false
                    val claimed =
                        stores.prefs.compareAndCommit(
                            stores.lanes.forProfile(ProfileId(profile.id)),
                            mapOf(ownerKey to previous),
                        ) {
                            putString(ownerKey, profile.id)
                        }
                    if ((claimed as? Outcome.Ok)?.value != true) return false
                    return stillWanted() && server.start() is Outcome.Ok && stillWanted()
                }

                override suspend fun connect(profile: MigrationProfile) {
                    check(app.appGraph.previewRuntime.connect(ProfileId(profile.id)) is Outcome.Ok)
                    val stored =
                        stores.prefs
                            .string("oc.location.${profile.id}")
                            ?.let(DartJson::decodeOrNull) as? Map<*, *>
                    val location =
                        ServerLocation(
                            stored?.get("directory") as? String,
                            stored?.get("workspace") as? String,
                        )
                    app.appGraph.connections.live.value[ProfileId(profile.id)]
                        ?.session
                        ?.server
                        ?.setLocation(location)
                }

                override fun isConnectedTo(profile: MigrationProfile): Boolean =
                    selectedProfileId() == profile.id &&
                        app.appGraph.connections.live.value[ProfileId(profile.id)] != null
            }
        val transport =
            BridgeTermuxMigrationTransport(
                MigrationTermuxRun { script, timeout ->
                    when (
                        val result = bridge.run(script, timeoutMillis = timeout.inWholeMilliseconds)
                    ) {
                        is TermuxAnswer.Done -> {
                            commandProblem = null
                            TermuxCommandResult(
                                result.value.stdout,
                                result.value.stderr,
                                result.value.exitCode,
                                result.value.errorCode,
                                result.value.errorMessage,
                            )
                        }
                        is TermuxAnswer.Failed -> {
                            commandProblem = TermuxProblem.ofError(result.error)
                            TermuxCommandResult("", exitCode = -1, errorCode = 1)
                        }
                    }
                },
                dispatchers,
            )
        return TermuxMigrationController(
            transport,
            TermuxMigrationArchiveStore(app.filesDir.toPath(), dispatchers),
            journal,
            availableBytes = {
                withContext(dispatchers.io) { app.filesDir.usableSpace.takeIf { it >= 0 } }
            },
            switchProfile = TermuxMigrationProfileSwitcher(profiles),
            sourceProfileExists = ::exists,
            clock = clock,
            scope = scope,
        )
    }

    override fun session(profileId: String): LinuxMigrationSession =
        Session(profileId).also { sessions += it }

    override val owner: String
        get() = ":android:termux"

    override val order: Int
        get() = DeletionStep.STASH + 10

    override val guardsSweep: Boolean
        get() = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        attempt({ Failure.StorageRefused() }) {
            val raw = stores.prefs.string(PreferencesTermuxMigrationJournal.key(profileId.raw))
            if (raw == null) 0
            else {
                val record = Json.parseToJsonElement(raw) as JsonObject
                val job = (record["job"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                check(job != null && Regex("^[a-f0-9]{32}$").matches(job))
                TermuxMigrationArchiveStore(app.filesDir.toPath(), dispatchers).cleanupPartial(job)
                1
            }
        }

    override fun stopAdmission(profile: ProfileId) {
        closing += profile.raw
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        if (migrationOwner.source == profile.raw) {
            migrationOwner.cancel()
            migrationOwner.settled()
        }
        return Outcome.Ok(Unit)
    }

    override fun reopen(profile: ProfileId) {
        closing -= profile.raw
        scope.launch { refresh() }
    }

    private inner class Session(private val profileId: String) : LinuxMigrationSession {
        private val mutable = MutableStateFlow(LinuxMigrationUi(busy = true))
        override val state: StateFlow<LinuxMigrationUi> = mutable.asStateFlow()
        private val page = TermuxMigrationPageModel(migrationOwner, profileId)
        private var accessIssue: LinuxMigrationAccess? = null
        private val listener: () -> Unit = { publish() }

        init {
            page.addListener(listener)
        }

        override suspend fun open() {
            if (stores.warm() !is Outcome.Ok || !exists(profileId)) {
                mutable.value =
                    LinuxMigrationUi(
                        title = LinuxMigrationWord.migrationUnavailableTitle,
                        paragraphs = listOf(text(MigrationWord.migrationUnavailableBody)),
                    )
                return
            }
            migrationOwner.obtain()
            val finished =
                (migrationOwner.completedJob(profileId) as? MigrationAnswer.Ok)?.value != null
            val saved = migrationOwner.savedSelection(profileId) != null
            accessIssue = if (finished || saved) null else access()
            if (accessIssue != null) {
                mutable.value = LinuxMigrationUi(access = accessIssue)
                return
            }
            page.open()
            publish()
        }

        private fun access(): LinuxMigrationAccess? =
            when (channel.state()) {
                GitRepairTermuxState.Ready -> null
                GitRepairTermuxState.Missing -> LinuxMigrationAccess.Missing
                GitRepairTermuxState.Permission ->
                    if (blocked) LinuxMigrationAccess.Blocked else LinuxMigrationAccess.Permission
                GitRepairTermuxState.Unavailable -> LinuxMigrationAccess.Unavailable
            }

        override fun toggle(id: String, selected: Boolean) {
            TermuxMigrationItem.entries
                .firstOrNull { it.name == id }
                ?.let { page.toggle(it, selected) }
            publish()
        }

        override suspend fun command(command: LinuxMigrationCommand) {
            when (command) {
                LinuxMigrationCommand.Retry -> {
                    accessIssue = access()
                    if (accessIssue == null) {
                        if (page.entry == MigrationEntry.Unavailable) page.reopen()
                        else page.retry()
                    }
                }
                LinuxMigrationCommand.Start -> page.start()
                LinuxMigrationCommand.Resume -> page.resume()
                LinuxMigrationCommand.Stop -> page.stop()
                LinuxMigrationCommand.Discard -> page.discard()
                LinuxMigrationCommand.ChooseFewer -> page.chooseFewer()
                LinuxMigrationCommand.Setup -> navigator.open(PhoneSetupRoute.Setup)
                LinuxMigrationCommand.OpenPhone,
                LinuxMigrationCommand.OpenBuiltin -> navigator.open(PhoneRoute.ThisPhone)
                LinuxMigrationCommand.SignIn -> {
                    val completed = migrationOwner.completedJob(profileId)
                    val destination =
                        (completed as? MigrationAnswer.Ok)?.value?.destinationProfileId
                    if (destination != null && exists(destination))
                        navigator.open(ProvidersRoute(ProfileId(destination)))
                }
                LinuxMigrationCommand.FixTermux -> {
                    when (access()) {
                        LinuxMigrationAccess.Permission -> requestPermission()
                        LinuxMigrationAccess.Blocked -> channel.openSettings()
                        else -> {
                            copyExternalAppsFix()
                            channel.openTermux()
                        }
                    }
                    open()
                }
            }
            publish()
        }

        override fun close() {
            sessions -= this
            page.close()
        }

        private fun publish() {
            val current = page.page()
            val issue =
                if (
                    current is MigrationPage.TermuxUnreachable ||
                        (current is MigrationPage.Failed &&
                            current.code == TermuxMigrationFailure.unavailable)
                )
                    when (commandProblem) {
                        TermuxProblem.OtherAppsOff -> LinuxMigrationAccess.OtherAppsOff
                        TermuxProblem.Asleep -> LinuxMigrationAccess.Asleep
                        TermuxProblem.AccessNeeded ->
                            if (blocked) LinuxMigrationAccess.Blocked
                            else LinuxMigrationAccess.Permission
                        TermuxProblem.NotInstalled -> LinuxMigrationAccess.Missing
                        TermuxProblem.Outdated -> LinuxMigrationAccess.Unavailable
                        null -> access()
                        else -> LinuxMigrationAccess.Unknown
                    }
                else null
            mutable.value = migrationUi(current).copy(access = accessIssue ?: issue)
            if (page.needsNames) scope.launch { page.loadNames() }
        }
    }

    private companion object {
        const val PERMISSION = "com.termux.permission.RUN_COMMAND"
        const val ACCESS_BLOCKED = "oc.termuxRunAccessBlocked"
    }
}

private fun text(word: MigrationWord, vararg arguments: String): LinuxMigrationText =
    LinuxMigrationText(LinuxMigrationWord.valueOf(word.name), arguments.toList())

private fun button(
    word: MigrationWord,
    command: LinuxMigrationCommand,
    enabled: Boolean = true,
): LinuxMigrationButton =
    LinuxMigrationButton(LinuxMigrationWord.valueOf(word.name), command, enabled)

private fun migrationUi(page: MigrationPage): LinuxMigrationUi {
    val retry = button(MigrationWord.commonRetry, LinuxMigrationCommand.Retry)
    val discard = button(MigrationWord.migrationDiscard, LinuxMigrationCommand.Discard)
    fun state(
        title: MigrationWord,
        paragraphs: List<LinuxMigrationText>,
        buttons: List<LinuxMigrationButton> = listOf(retry),
        busy: Boolean = false,
    ) =
        LinuxMigrationUi(
            LinuxMigrationWord.valueOf(title.name),
            paragraphs = paragraphs,
            buttons = buttons,
            busy = busy,
        )
    return when (page) {
        MigrationPage.Checking ->
            state(
                MigrationWord.migrationTitle,
                listOf(text(MigrationWord.migrationChecking)),
                emptyList(),
                true,
            )
        MigrationPage.Unavailable ->
            state(
                MigrationWord.migrationUnavailableTitle,
                listOf(text(MigrationWord.migrationUnavailableBody)),
            )
        is MigrationPage.Unfinished ->
            state(
                MigrationWord.migrationUnfinishedTitle,
                listOf(text(MigrationWord.migrationUnfinishedBody)) +
                    if (page.discardFailed) listOf(text(MigrationWord.migrationDiscardFailed))
                    else emptyList(),
                listOf(
                    button(MigrationWord.migrationResume, LinuxMigrationCommand.Resume),
                    discard.copy(enabled = page.discardEnabled),
                ),
            )
        is MigrationPage.Review ->
            state(
                    MigrationWord.migrationTitle,
                    listOf(
                        text(MigrationWord.migrationReviewIntro),
                        text(MigrationWord.migrationExportsNote),
                        text(MigrationWord.migrationNotMovedSignIn),
                        text(MigrationWord.migrationNotMovedKeys),
                        text(MigrationWord.migrationNotMovedTools),
                        text(MigrationWord.migrationTermuxKept),
                    ) + listOfNotNull(page.startDisabledReason?.let { text(it) }),
                    listOf(
                        button(
                            MigrationWord.migrationStart,
                            LinuxMigrationCommand.Start,
                            page.startEnabled,
                        )
                    ),
                )
                .copy(
                    choices =
                        (page.moves + page.exports).map { row ->
                            LinuxMigrationChoice(
                                row.item.name,
                                LinuxMigrationWord.valueOf(row.label.name),
                                LinuxMigrationWord.valueOf(row.below.name),
                                row.on,
                                row.problem == null,
                                row.bytes,
                                row.files,
                            )
                        },
                    requiredBytes = page.space?.requiredBytes,
                    availableBytes = page.space?.freeBytes,
                    showSpace = page.space != null,
                )
        is MigrationPage.Progress ->
            state(
                MigrationWord.migrationTitle,
                listOf(text(page.stage), text(MigrationWord.migrationKeepOpen)),
                listOf(button(MigrationWord.migrationStop, LinuxMigrationCommand.Stop)),
                true,
            )
        is MigrationPage.Cancelled ->
            state(
                page.title,
                listOf(text(page.body)) +
                    if (page.discardFailed) listOf(text(MigrationWord.migrationDiscardFailed))
                    else emptyList(),
                if (page.settling) emptyList()
                else
                    listOf(button(page.primary, LinuxMigrationCommand.Resume)) +
                        if (page.discardOffered) listOf(discard) else emptyList(),
                page.settling,
            )
        is MigrationPage.NeedsSpace ->
            state(
                    MigrationWord.migrationNeedsSpace,
                    listOf(text(MigrationWord.migrationReviewSpaceShort)),
                    listOf(
                        button(
                            MigrationWord.migrationChooseFewer,
                            LinuxMigrationCommand.ChooseFewer,
                            page.actionsEnabled,
                        ),
                        retry.copy(enabled = page.actionsEnabled),
                    ),
                )
                .copy(
                    showSpace = true,
                    requiredBytes = page.requiredBytes,
                    availableBytes = page.freeBytes,
                )
        is MigrationPage.NeedsBuiltin ->
            state(
                MigrationWord.migrationNeedsBuiltin,
                listOf(text(MigrationWord.migrationNeedsBuiltinBody, "OpenCode")),
                listOf(
                    button(MigrationWord.migrationSetUpBuiltin, LinuxMigrationCommand.Setup),
                    retry,
                ),
            )
        is MigrationPage.TermuxUnreachable ->
            state(
                MigrationWord.migrationTermuxNotAnswering,
                listOf(text(MigrationWord.migrationTermuxUnavailable)),
                listOf(
                    button(MigrationWord.migrationOpenTermux, LinuxMigrationCommand.FixTermux),
                    retry.copy(enabled = page.retryEnabled),
                ),
            )
        is MigrationPage.Failed ->
            state(
                MigrationWord.migrationFailedTitle,
                page.words.map { text(it) } +
                    if (page.discardFailed) listOf(text(MigrationWord.migrationDiscardFailed))
                    else emptyList(),
                listOf(
                    when (page.primary) {
                        MigrationFailedAction.OpenBuiltin ->
                            button(
                                MigrationWord.migrationOpenBuiltin,
                                LinuxMigrationCommand.OpenBuiltin,
                            )
                        MigrationFailedAction.Resume ->
                            button(
                                MigrationWord.migrationResume,
                                LinuxMigrationCommand.Resume,
                                page.primaryEnabled,
                            )
                        MigrationFailedAction.TryAgain -> retry.copy(enabled = page.primaryEnabled)
                    }
                ) + if (page.discardOffered) listOf(discard) else emptyList(),
            )
        is MigrationPage.Done ->
            state(
                MigrationWord.migrationDoneTitle,
                listOf(
                    text(MigrationWord.migrationDone),
                    text(page.signInWords, page.providerNames.joinToString(", ")),
                ) +
                    listOfNotNull(
                        page.projectsPath?.let {
                            text(MigrationWord.migrationProjectsWhereBody, it)
                        },
                        page.exportsPath?.let { text(MigrationWord.migrationExportsWhereBody, it) },
                    ) +
                    listOf(text(MigrationWord.migrationRemoveTermuxBody)),
                listOf(
                    button(MigrationWord.migrationSignInAgain, LinuxMigrationCommand.SignIn),
                    button(MigrationWord.migrationOpenBuiltin, LinuxMigrationCommand.OpenBuiltin),
                ),
            )
    }
}
