package io.github.eslamasabry.oc.feature.chats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelDisplayName
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.selection.AgentSelection
import io.github.eslamasabry.oc.engine.git.GitLinkRepairReport
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.NewChatOutcome
import io.github.eslamasabry.oc.ui.routes.NewChatReports
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** A refused first read is read again this often, this many times (about 30 s), before the page says it could not read. */
internal const val SETUP_RETRY_EVERY_MS: Long = 3_000
internal const val SETUP_RETRIES: Int = 10

/** The one short re-read of This phone's server right after it came up, while it finishes opening. */
internal const val SETUP_AFTER_UP_REREAD_MS: Long = 2_000

/** How long the page waits for a server's first answer before it says so and offers Try again. */
internal val SETUP_WAIT = 90.seconds

private val ALWAYS_READ: StateFlow<Boolean> = MutableStateFlow(true)

/** What the page knows about the server it reads from, before it reads (first-read-ready). */
public enum class NewConversationServerState {
    /** Not This phone's own server (a computer, a helper): the page reads as it always did. */
    NotPhone,

    /** This phone's server is starting or about to: the page waits, and reads by itself once it is up. */
    Starting,

    /** The person stopped This phone's server: the page says so with its Start action, and reads nothing. */
    StoppedByPerson,

    /** This phone's server is stopped (not by the person) and nothing is starting it: the page asks for one start. */
    Stopped,

    /** This phone's server answers: a read that fails now is a real failure. */
    Up,

    /** This phone's server could not start: the page says so with Try again. */
    Failed,
}

private val NOT_PHONE: StateFlow<NewConversationServerState> = MutableStateFlow(NewConversationServerState.NotPhone)

public data class NewConversationServer(
    val profile: ProfileId,
    val name: String,
    val connected: Boolean,
    /** This phone's own OpenCode is off because the person stopped it. */
    val phoneOff: Boolean = false,
    /**
     * Connections that are part of this server and never listed on their own (This phone's agents): a page opened
     * for one of them (Start new from an agent's conversation) starts here, with that agent chosen.
     */
    val standsFor: Set<ProfileId> = emptySet(),
)

public data class NewConversationProject(
    val directory: String,
    val name: String,
    val separateCopy: Boolean,
)

/**
 * This phone's shared storage (Downloads, Documents and the rest) for the phone's own OpenCode: whether All files
 * access is on. A server that is not this phone's own offers none (null).
 */
public enum class PhoneStorageAccess { Off, On }

/** One folder of the phone's storage, for the folder list under the Phone storage row. */
public data class StorageFolder(val name: String, val path: String)

/** The phone's storage as agents on this phone see it: where the Phone storage folder list starts. */
public const val PHONE_STORAGE_ROOT: String = "/storage/emulated/0"

/**
 * Issue 147: a folder in this phone's storage, as the phone's Linux side that will work in it finds it (the phone's own
 * OpenCode, or the helper that runs the phone's agents), checked when the folder is chosen and before the first
 * message. Anything but [Ready] keeps Start back and says why, with the way forward: a message never goes to another
 * folder than the one shown.
 */
public enum class PhoneFolderReach {
    /** That side lists and writes the folder: the agent starts in it. */
    Ready,

    /** Android's All files access is off. */
    AppAccess,

    /** That side was started before the folder was chosen and does not see it until it starts again. */
    Restart,

    /** The folder is not on the phone any more. */
    Missing,

    /** The app sees the folder, but that side cannot open or change files in it. */
    Denied;

    public companion object {
        /** Whether [directory] is spelled as a folder in the phone's storage (the check itself decides the rest). */
        public fun looksLikePhoneStorage(directory: String?): Boolean {
            val path = directory?.trim() ?: return false
            return listOf("/storage/", "/sdcard/", "/mnt/sdcard/").any { path.startsWith(it) }
        }
    }
}

/** W3: the two Linux sides of this phone a folder in its storage must be bound into: its OpenCode and its agents. */
public enum class PhoneFolderSide {
    /** The phone's own OpenCode server. */
    Server,

    /** The helper that runs the phone's agents (Claude Code, Codex, Antigravity, Qwen and the rest). */
    Agents,
}

public data class NewConversationModel(
    val model: ModelRef,
    val name: String,
    val runtime: String? = null,
    /** N-51: the provider's name, the heading its models sit under (as in the chat's model sheet). */
    val providerName: String? = null,
    /**
     * The thinking efforts this model offers (Paseo `thinkingOptions`, OpenCode `variants`), as the server names
     * them ("low", "high", "xhigh"); empty when it offers none.
     */
    val variants: List<String> = emptyList(),
)

/**
 * One agent this phone can run, as New conversation offers it (Flutter `ChatAgentChoice` / a set-up row). [checking]:
 * its install, update or phone check runs now; its set-up row says "Checking…" instead of "Set up".
 */
public data class NewConversationPhoneAgent(
    val id: String,
    val name: String,
    val iconKey: String,
    val checking: Boolean = false,
    /** Its update runs now (installed and installing again): its row says "Updating <agent>" (issue 147). */
    val updating: Boolean = false,
    /** Installed, but the app cannot read its sign-in: the Agents page says "Set it up the first time you use it". */
    val setUpAtFirstUse: Boolean = false,
    /** Signed out: the Agents page says "Sign in needed", and so does this row (F-040). */
    val signInNeeded: Boolean = false,
    /** Needs its phone check: the Agents page says "Phone check needed", and so does this row. */
    val phoneCheck: Boolean = false,
    /** Its sign-in check could not run to an answer: the Agents page says "Sign-in not confirmed", and so does this row. */
    val signInNotConfirmed: Boolean = false,
    /** Its plan limit is reached: the Agents page says so, and so does this row. [limitResets] is when, when it said. */
    val limitReached: Boolean = false,
    val limitResets: String? = null,
)

/**
 * The agents on this phone beside the connected server's own: [choices] can start a conversation now,
 * [needsSetup] open their setup. [helper] is the profile their conversations live on, and [selected] the agent
 * remembered for the shown server (null = the server's own agent).
 */
public data class NewConversationPhone(
    val helper: ProfileId,
    val choices: List<NewConversationPhoneAgent>,
    val needsSetup: List<NewConversationPhoneAgent>,
    val selected: String? = null,
)

/** A phone agent's models, and the one remembered for it (else its first). */
public data class NewConversationPhoneModels(
    val models: List<NewConversationModel>,
    val model: ModelRef?,
)

/**
 * A model row's name: the catalog's, else the one the server or agent reports, and when that is blank or only the id
 * again, the id read as words (Flutter `_choiceName`): never a raw id such as `claude-opus-4-6`.
 */
public fun newConversationModelName(modelId: String, catalogName: String?, reportedName: String?): String =
    ModelDisplayName.of(modelId, catalogName?.takeIf { it.isNotBlank() } ?: reportedName)

/**
 * Whether the helper's model list for a phone agent must be read again from the agent itself: the Agents page says
 * the agent is ready, yet the helper's last list names no real model for it (it was read before the agent was
 * installed or signed in, and the helper keeps that list until it is asked to look again). Asked once per read, never
 * in a loop.
 */
public fun phoneModelsNeedRecheck(agentReady: Boolean, models: List<NewConversationModel>): Boolean =
    agentReady && models.none { it.model.modelId != "default" }

public data class NewConversationSetup(
    val directory: String?,
    val projects: List<NewConversationProject>,
    val models: List<NewConversationModel>,
    val agents: List<String>,
    val model: ModelRef? = null,
    /** The model this server resolves "Server default" to, when it says; null otherwise. */
    val serverDefault: ModelRef? = null,
    val agent: String? = null,
    val importDirectory: String? = null,
    val canChangeProject: Boolean = true,
    val defaultRuntime: String = "OpenCode",
    val modes: List<String> = emptyList(),
    /** N-51: this server's favourite models, as the chat's model sheet shows them. */
    val favorites: List<ModelRef> = emptyList(),
    /** The agents on this phone; null where the phone has no agent host. */
    val phone: NewConversationPhone? = null,
)

/** Keeps successful stages on an explicit retry: a copy or conversation is never created twice. */
public data class NewConversationAttempt(
    val directory: String,
    val ref: ConversationRef? = null,
    val failure: Failure? = null,
    val dispatched: Boolean = false,
    val creationAttempted: Boolean = false,
    val deliveryUnconfirmed: Boolean = false,
    val targetIdentity: String? = null,
)

public interface NewConversationHost {
    /**
     * The setup last read for [profile] in this run (its projects, models, agents and defaults), valid while the same live
     * connection stands; null when there is none. The page shows it at once and reads a fresh one behind it (Issue 147: every
     * visit waited for five to eight round trips before the page was usable).
     */
    public fun cachedSetup(profile: ProfileId): NewConversationSetup? = null

    public fun canRepair(profile: ProfileId, directory: String): Boolean = false

    public suspend fun repair(profile: ProfileId, directory: String): Outcome<GitLinkRepairReport> =
        Outcome.Failed(Failure.Unavailable(FeatureId("gitLinkRepair")))

    /** [profile]'s phone storage access when it is this phone's own server; null when the page offers none. */
    public suspend fun phoneStorage(profile: ProfileId): PhoneStorageAccess? = null

    /** Opens Android's page for All files access. A request to the person, never a grant; the answer is read on return. */
    public suspend fun requestPhoneStorage(profile: ProfileId): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("phoneStorage")))

    /** The folders inside [path] in the phone's storage (the Phone storage folder list). */
    public suspend fun storageFolders(path: String): Outcome<List<StorageFolder>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("phoneStorage")))

    /** Makes folder [name] inside [parent] in the phone's storage; the answer is the new folder's path. */
    public suspend fun createStorageFolder(parent: String, name: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("phoneStorage")))

    /**
     * Issue 147: what [directory] is to the side of this phone that will work in it: [phoneAgent]'s helper, or the
     * phone's own OpenCode when null. Null when the folder is not in this phone's storage, the server is not this
     * phone's own, or it can't be told (the start itself then answers).
     */
    public suspend fun phoneFolder(profile: ProfileId, phoneAgent: String?, directory: String): PhoneFolderReach? =
        null

    /**
     * The way forward for [reach]: Android's All files access page ([PhoneFolderReach.AppAccess]) or a new start of
     * the side that was started before the folder was chosen ([PhoneFolderReach.Restart]; refused while it works).
     */
    public suspend fun fixPhoneFolder(
        profile: ProfileId,
        phoneAgent: String?,
        reach: PhoneFolderReach,
    ): Outcome<Unit> = Outcome.Failed(Failure.Unavailable(FeatureId("phoneFolder")))

    /** [fixPhoneFolder] for [directory]: the other side of this phone restarts too when it also lacks it (W3). */
    public suspend fun fixPhoneFolder(
        profile: ProfileId,
        phoneAgent: String?,
        reach: PhoneFolderReach,
        directory: String,
    ): Outcome<Unit> = fixPhoneFolder(profile, phoneAgent, reach)

    /**
     * W3: whether the other side of this phone (its OpenCode when [phoneAgent] is set, else its agents) also runs
     * without [directory]. The one restart [fixPhoneFolder] makes then restarts both, so choosing another agent later
     * never asks again for the same folder.
     */
    public suspend fun phoneFolderBothSides(profile: ProfileId, phoneAgent: String?, directory: String): Boolean = false

    /**
     * W3: the sides whose restart for a chosen folder waits for the work running there to end (a reply on the phone's
     * OpenCode, an agent's turn); it is never interrupted, and the side restarts by itself once idle.
     */
    public suspend fun phoneFolderWaiting(profile: ProfileId): Set<PhoneFolderSide> = emptySet()

    /** [phoneFolderWaiting] as it changes (a waiting side restarts once its work ends). */
    public fun phoneFolderWaitingUpdates(profile: ProfileId): kotlinx.coroutines.flow.Flow<Set<PhoneFolderSide>> =
        kotlinx.coroutines.flow.flow { emit(phoneFolderWaiting(profile)) }

    /** Whether [profile] can make a new project folder here (the app's own phone server only, as in Projects). */
    public fun canCreateProject(profile: ProfileId): Boolean = false

    /** Makes a project folder named [name] with Git under the projects root; answers its path. */
    public suspend fun createProject(profile: ProfileId, name: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("projectFolderCreation")))

    /**
     * B-063: whether [directory] is really there on [profile]'s server, read where the app can look
     * (its own phone server); null when it can't tell. The server keeps listing a project whose
     * folder is gone, and a message sent there is refused.
     */
    public suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = null

    /** B-063: makes [directory] again as an empty Git project; answers its path. */
    public suspend fun remakeFolder(profile: ProfileId, directory: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("projectFolderCreation")))

    public val servers: StateFlow<List<NewConversationServer>>

    /**
     * Whether [servers] is the real saved list. False while the app is still reading its saved connections (a cold
     * start): an empty list then is "not read yet", never "no server" (issue 147, F-039).
     */
    public val serversRead: StateFlow<Boolean>
        get() = ALWAYS_READ

    /** Starts this phone's own OpenCode after the person stopped it. */
    public suspend fun startPhoneServer(profile: ProfileId): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("startPhoneServer")))

    /** "Check <agent> again": asks the phone's agents now, and publishes what they answer (signin-followups). */
    public suspend fun checkPhoneAgents() {}

    /** The state of [profile]'s own server as the app already knows it (no poll); [NewConversationServerState.NotPhone] for others. */
    public fun serverState(profile: ProfileId): StateFlow<NewConversationServerState> = NOT_PHONE

    public suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup>

    /**
     * Remembers the agent for [profile] (`oc.chatAgent.<profile>`); null is the server's own agent. A phone agent that
     * cannot start a conversation now is refused and nothing is written.
     */
    public suspend fun choosePhoneAgent(profile: ProfileId, agentId: String?): Outcome<Unit> = Outcome.Ok(Unit)

    /** [agentId]'s models and the remembered one; the phone's helper starts when it has to (the first read is slow). */
    public suspend fun phoneAgentModels(profile: ProfileId, agentId: String): Outcome<NewConversationPhoneModels> =
        Outcome.Failed(Failure.Unavailable(FeatureId("phoneAgentModels")))

    /**
     * The list [phoneAgentModels] last read for [agentId] in this app run, shown at once while it is read again (the
     * helper's first read takes about ten seconds); null when none was read yet.
     */
    public fun cachedPhoneAgentModels(profile: ProfileId, agentId: String): NewConversationPhoneModels? = null

    /**
     * Reads the models of the ready phone agents [agentIds] ahead of any pick and keeps them for
     * [cachedPhoneAgentModels]; nothing is shown from here.
     */
    public suspend fun prefetchPhoneAgentModels(profile: ProfileId, agentIds: List<String>) {}

    /** Remembers [model] for [agentId] (`oc.agentModel.<profile>`). */
    public suspend fun savePhoneAgentModel(profile: ProfileId, agentId: String, model: ModelRef?): Outcome<Unit> =
        Outcome.Ok(Unit)

    public suspend fun review(profile: ProfileId, directory: String?): Outcome<ConversationRef?> =
        Outcome.Ok(null)

    /**
     * N-48: the person's Discard on a first message whose arrival was never confirmed: the review record leaves and
     * so does its waiting copy in the device queue, so nothing stays locked behind it.
     */
    public suspend fun dismissReview(profile: ProfileId, directory: String?): Outcome<Unit> = Outcome.Ok(Unit)

    public suspend fun draft(profile: ProfileId, directory: String?): Outcome<String>

    public suspend fun saveDraft(
        profile: ProfileId,
        directory: String?,
        text: String,
    ): Outcome<Unit>

    /**
     * N-51: adds or removes [model] from [profile]'s favourite models (the store the chat's model sheet
     * uses) and answers the favourites as they now are.
     */
    public suspend fun toggleFavorite(profile: ProfileId, model: ModelRef): Outcome<List<ModelRef>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("modelFavorites")))

    /** Saved with the project draft; changing screens never discards the selected model. */
    public suspend fun saveChoices(
        profile: ProfileId,
        directory: String?,
        model: ModelRef?,
        agent: String?,
    ): Outcome<Unit> = Outcome.Ok(Unit)

    public suspend fun start(
        profile: ProfileId,
        directory: String,
        text: String,
        model: ModelRef?,
        agent: String?,
        separateCopy: Boolean,
        previous: NewConversationAttempt?,
        /** The session and local send exist; its composer can open while admission settles. */
        onStarted: (ConversationRef) -> Unit = {},
    ): NewConversationAttempt

    /** [start] with the thinking effort chosen for [model] ([variant], null = the model's own default). */
    public suspend fun startWithEffort(
        profile: ProfileId,
        directory: String,
        text: String,
        model: ModelRef?,
        variant: String?,
        agent: String?,
        separateCopy: Boolean,
        previous: NewConversationAttempt?,
        onStarted: (ConversationRef) -> Unit = {},
    ): NewConversationAttempt = start(profile, directory, text, model, agent, separateCopy, previous, onStarted)

    public suspend fun imports(
        profile: ProfileId,
        directory: String,
    ): Outcome<ImportableConversations>

    public suspend fun import(
        profile: ProfileId,
        directory: String,
        conversation: ImportableConversation,
    ): Outcome<ConversationRef>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoNewConversationHost : NewConversationHost {
    override val servers: StateFlow<List<NewConversationServer>> = MutableStateFlow(emptyList())

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Unavailable(FeatureId("newConversation")))

    override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> = unavailable()

    override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> =
        unavailable()

    override suspend fun saveDraft(
        profile: ProfileId,
        directory: String?,
        text: String,
    ): Outcome<Unit> = unavailable()

    override suspend fun start(
        profile: ProfileId,
        directory: String,
        text: String,
        model: ModelRef?,
        agent: String?,
        separateCopy: Boolean,
        previous: NewConversationAttempt?,
        onStarted: (ConversationRef) -> Unit,
    ): NewConversationAttempt =
        NewConversationAttempt(
            directory,
            failure = Failure.Unavailable(FeatureId("newConversation")),
        )

    override suspend fun imports(
        profile: ProfileId,
        directory: String,
    ): Outcome<ImportableConversations> = unavailable()

    override suspend fun import(
        profile: ProfileId,
        directory: String,
        conversation: ImportableConversation,
    ): Outcome<ConversationRef> = unavailable()
}

/**
 * [Agents] is who works and how; [Models] is the kit's one model sheet (N-51), the same sheet a
 * conversation's model chip opens.
 */
public enum class NewConversationSheet {
    Projects,
    Models,
    Agents,
    Import,
}

/** Ways to start that fill the prompt and never send; the words live in `:ui:words`. */
public enum class NewConversationStarter {
    ExplainProject,
    FindBug,
    AddTests,
    ListFolder,
}

/**
 * The starters for [directory]. Folder facts are not read on this screen (no listing before the
 * conversation exists), so only starters that hold for any project are offered.
 */
public fun newConversationStarters(directory: String?): List<NewConversationStarter> {
    val name = directory?.trim()?.replace('\\', '/')?.split('/')?.lastOrNull { it.isNotEmpty() }
    val projectsRoot = directory?.trim()?.trimEnd('/')?.endsWith("/root/projects") == true
    return if (name == null || projectsRoot)
        listOf(NewConversationStarter.ListFolder, NewConversationStarter.FindBug)
    else
        listOf(
            NewConversationStarter.ExplainProject,
            NewConversationStarter.FindBug,
            NewConversationStarter.AddTests,
        )
}

/** A label ending in an ellipsis starts a sentence: the field gets it with a trailing space. */
public fun starterInsertText(label: String): String =
    if (label.endsWith('…')) label.dropLast(1).trimEnd() + " " else label

/** Search over what each model row shows (its name and its host app), never hidden ids. */
public fun filterNewConversationModels(
    models: List<NewConversationModel>,
    query: String,
): List<NewConversationModel> {
    val q = query.trim().lowercase()
    val named = models.filter { it.model.modelId != "default" }
    if (q.isEmpty()) return named
    return named.filter { model ->
        model.name.lowercase().contains(q) || model.runtime?.lowercase()?.contains(q) == true
    }
}

/**
 * What the New conversation page is waiting on, for the Start button's words and spinner. A spinner belongs only to
 * [Sending] (the person pressed Start); getting the page ready and reading models say what they are, with Start off.
 */
/** How long getting the page ready may take before it says what it is reading. */
internal const val PREPARING_SLOW_MS: Long = 4_000

public enum class NewConversationWait {
    None,
    /** The chosen server's projects, models and agents are being read. */
    Preparing,
    /** The agent's models are being read. */
    Models,
    /** Start was pressed. */
    Sending,
}

/** The one way forward after a phone agent's conversation did not start. */
public enum class PhoneStartWay {
    /** The agent needs setting up (not ready, not signed in): open its set-up. */
    SetUp,

    /** The chosen model is not one the helper lists for the agent: choose another. */
    ChooseModel,

    /** Anything else: try again. */
    TryAgain,

    /**
     * signin-followups: the helper said the agent "is not ready": what is wrong is not known to be its set-up, so the way
     * forward is "Check <agent> again" (asks the agent now), then the start goes again.
     */
    CheckAgain,

    /**
     * Issue 147: the agent's own sign-in no longer works (the helper refused the start for it: "Failed to
     * authenticate: OAuth session expired"): "<Agent> needs you to sign in again", with Sign in.
     */
    SignIn,
}

/** A phone agent's refused start: [agentName] is named on screen, [way] is the one action offered. */
public data class PhoneStartProblem(val agentId: String, val agentName: String, val way: PhoneStartWay)

/** Reads a refused phone-agent start into the one way forward (the helper's sentence is matched, never shown). */
public object PhoneStartRules {
    /** "<Agent> is not ready": asked again before anyone is sent to set it up (signin-followups). */
    private val NOT_YET_READY = Regex("is not ready", RegexOption.IGNORE_CASE)

    private val NOT_READY =
        Regex(
            "is disabled|not installed|auth(?:entication)?[ _-]?required|authenticate|sign[ -]?in|" +
                "log ?in|No model selected|No API key",
            RegexOption.IGNORE_CASE,
        )

    /** The tag the phone's helper gives a start refused for the agent's sign-in (`PaseoAgentSignInTag`). */
    public const val SIGN_IN_TAG: String = "Paseo AgentSignIn"

    public fun way(failure: Failure, chosen: ModelRef?, listed: List<NewConversationModel>): PhoneStartWay =
        when {
            failure is Failure.Rejected && failure.tag?.raw == SIGN_IN_TAG -> PhoneStartWay.SignIn
            chosen != null && listed.isNotEmpty() && listed.none { it.model == chosen } -> PhoneStartWay.ChooseModel
            failure is Failure.ComputerDidNotAnswer && NOT_YET_READY.containsMatchIn(failure.details?.text.orEmpty()) ->
                PhoneStartWay.CheckAgain
            failure is Failure.ComputerDidNotAnswer && NOT_READY.containsMatchIn(failure.details?.text.orEmpty()) ->
                PhoneStartWay.SetUp
            else -> PhoneStartWay.TryAgain
        }
}

public data class NewConversationUi(
    val servers: List<NewConversationServer> = emptyList(),
    /** False while the saved connections are still being read: an empty [servers] is then not "no server". */
    val serversRead: Boolean = true,
    val profile: ProfileId? = null,
    val setup: NewConversationSetup? = null,
    val directory: String? = null,
    val text: String = "",
    val textRevision: Long = 0,
    /** Bumped when a starter fills the prompt: the field takes the cursor back. */
    val focusRevision: Long = 0,
    val model: ModelRef? = null,
    /** N-51: this server's favourite models; the model sheet's stars change them. */
    val favorites: List<ModelRef> = emptyList(),
    val agent: String? = null,
    val separateCopy: Boolean = false,
    val busy: Boolean = false,
    val openingChat: Boolean = false,
    val failure: Failure? = null,
    val sheet: NewConversationSheet? = null,
    val imports: ImportableConversations? = null,
    /** When [imports] was read, for each row's relative age. */
    val importsReadAtMs: Long = 0,
    val importBusy: Boolean = false,
    val importFailed: Boolean = false,
    val draftFailed: Boolean = false,
    val attempted: Boolean = false,
    val creationUnconfirmed: Boolean = false,
    val deliveryUnconfirmed: Boolean = false,
    val reviewConversation: ConversationRef? = null,
    val repairAvailable: Boolean = false,
    val repair: GitLinkRepairReport? = null,
    val repairing: Boolean = false,
    /** The project sheet offers New project (the app's own phone server can make a Git folder under its projects). */
    val canCreateProject: Boolean = false,
    /** This phone's storage access, read when the project sheet opens and when the page comes back; null: not offered. */
    val phoneStorage: PhoneStorageAccess? = null,
    /** The Phone storage folder list: the folder it shows (null: closed), its rows and what it waits on. */
    val storagePath: String? = null,
    val storageFolders: List<StorageFolder> = emptyList(),
    val storageLoading: Boolean = false,
    val storageFailure: Failure? = null,
    /** The New project name dialog is open. */
    val askingProjectName: Boolean = false,
    val creatingProject: Boolean = false,
    val projectFailure: Failure? = null,
    /** B-063: the chosen project's folder is not on its server any more; Start waits for a choice. */
    val folderMissing: Boolean = false,
    /** Issue 147: the chosen folder in this phone's storage as the side that will work in it finds it; null: not one. */
    val phoneFolder: PhoneFolderReach? = null,
    val fixingPhoneFolder: Boolean = false,
    val phoneFolderFailure: Failure? = null,
    /** The chosen folder in phone storage is being read; Start waits and the page says so. */
    val checkingPhoneFolder: Boolean = false,
    /** What the last action fixed (the access, a restart), once the folder reads Ready again; the page says it once. */
    val phoneFolderFixed: PhoneFolderReach? = null,
    /** W3: the other side of this phone also runs without the folder; one restart restarts both. */
    val phoneFolderBoth: Boolean = false,
    /** W3: the sides whose restart waits for their work to end (never interrupted). */
    val phoneFolderWaiting: Set<PhoneFolderSide> = emptySet(),
    val remakingFolder: Boolean = false,
    val remakeFailure: Failure? = null,
    /** The chosen server is this phone's own OpenCode and the person stopped it: no list can be read until it starts. */
    val phoneOff: Boolean = false,
    val startingPhone: Boolean = false,
    /** The agent on this phone chosen for this conversation; null = the server's own agent. */
    val phoneAgent: String? = null,
    /** [phoneAgent]'s models and the chosen one; empty while they are read. */
    val phoneModels: List<NewConversationModel> = emptyList(),
    val phoneModel: ModelRef? = null,
    val phoneModelsBusy: Boolean = false,
    /** The person chose Plan for [phoneAgent]'s conversation (only an agent that can be held to Plan offers it). */
    val phonePlan: Boolean = false,
    /** Getting the page ready has lasted more than a few seconds: the page names what it is reading. */
    val preparingSlow: Boolean = false,
    /** The thinking effort chosen for the shown model; null is the model's own default. */
    val variant: String? = null,
    /**
     * Issue 147: Start was pressed while the page was still getting ready: the tap is kept, and the conversation starts
     * the moment the page is ready (it is never dropped, and Start never sits grey with nothing said).
     */
    val startQueued: Boolean = false,
) {
    /** A separate copy is OpenCode only: a phone agent hides it (Flutter `_start`). */
    val canCopy: Boolean
        get() = phoneAgent == null && setup?.projects?.any { it.directory == directory && it.separateCopy } == true

    /** The chosen phone agent's name (its row while offered, else the catalog's). */
    /** Issue 147: the chosen folder in phone storage is not ready for the side that will work in it; Start waits. */
    val phoneFolderBlocked: Boolean
        get() = phoneFolder != null && phoneFolder != PhoneFolderReach.Ready

    /** W3: the side that will work in the folder restarts once its running work ends (it is never interrupted). */
    val chosenSideWaits: Boolean
        get() =
            phoneFolder == PhoneFolderReach.Restart &&
                (if (phoneAgent != null) PhoneFolderSide.Agents else PhoneFolderSide.Server) in phoneFolderWaiting

    /** W3: a restart of OpenCode is under way and is waiting for the reply it is writing (nothing is cut). */
    val restartWaitsForReply: Boolean
        get() = fixingPhoneFolder && PhoneFolderSide.Server in phoneFolderWaiting

    val phoneAgentName: String?
        get() =
            phoneAgent?.let { id ->
                phoneAgentChoice?.name ?: AgentCatalog.builtIn.agents.firstOrNull { it.id.raw == id }?.name
            }

    /** Why a phone agent's start was refused, once it was and nothing is running; null otherwise. */
    val phoneStartProblem: PhoneStartProblem?
        get() {
            val id = phoneAgent ?: return null
            val failed = failure ?: return null
            val name = phoneAgentName ?: return null
            if (busy || !openingChat) return null
            return PhoneStartProblem(id, name, PhoneStartRules.way(failed, phoneModel, phoneModels))
        }

    /** The chosen phone agent's row, while it is still offered. */
    /**
     * Whether [phoneAgent] can be held to Plan: its own read-only or plan mode exists (the Paseo gateway's plan table), and
     * the app declines its changes in Plan besides. An agent with neither is never offered Plan.
     */
    val phoneCanPlan: Boolean
        get() =
            phoneAgent != null &&
                AgentCatalog.builtIn.agents.firstOrNull { it.id.raw == phoneAgent }?.providerId in PLAN_HOLDING_PROVIDERS

    val phoneAgentChoice: NewConversationPhoneAgent?
        get() = setup?.phone?.choices?.firstOrNull { it.id == phoneAgent }

    /** The models the Model row and sheet list: the phone agent's own, else the server's. */
    val shownModels: List<NewConversationModel>
        get() = if (phoneAgent != null) phoneModels else setup?.models.orEmpty()

    /**
     * Whether the Model row is a control: a person can name at least two models, or the phone agent's list is
     * still being read. With one model or none there is nothing to choose, so the row only states the model.
     */
    val modelChoosable: Boolean
        get() = (phoneAgent != null && phoneModelsBusy) || filterNewConversationModels(shownModels, "").size >= 2

    /** The efforts the shown model offers (none when it offers none, or no model is named). */
    val shownVariants: List<String>
        get() =
            shownModel
                ?.let { ref -> shownModels.firstOrNull { it.model == ref } }
                // A name that says its effort ("Gemini 3.8 Flash (High)") leaves no second effort to choose.
                ?.takeIf { io.github.eslamasabry.oc.core.model.ModelEffort.offersChoice(it.name) }
                ?.variants
                .orEmpty()

    /**
     * The phone agent's model list is being read now (the list shown meanwhile is the last known one, or none). This is
     * the model sheet's `loading` once the kit's KitModelSheet takes it (branch native/kit-model-loading); until then
     * the sheet only holds back the "no models" sentence while it is true.
     */
    val modelsLoading: Boolean
        get() = phoneAgent != null && phoneModelsBusy

    /** What the page waits on now (see [NewConversationWait]). */
    val wait: NewConversationWait
        get() =
            when {
                openingChat -> NewConversationWait.Sending
                !serversRead -> NewConversationWait.Preparing
                busy && setup == null && !repairing -> NewConversationWait.Preparing
                busy && setup != null && !repairing && text.isNotBlank() -> NewConversationWait.Sending
                phoneModelsBusy && !busy -> NewConversationWait.Models
                else -> NewConversationWait.None
            }

    /** The model the Model row shows. */
    val shownModel: ModelRef?
        get() = if (phoneAgent != null) phoneModel else model

    /** Why Start cannot run for a phone agent: it works only in the projects on this phone (`/root/projects/`). */
    val phoneNeedsProjectsFolder: Boolean
        get() =
            phoneAgent != null && directory != null &&
                !AgentSelection.folderAllowed(AgentId(phoneAgent), directory)

    /** [phoneNeedsProjectsFolder] and the phone has no project the agent could use: the way out is a new one. */
    val phoneHasNoProject: Boolean
        get() =
            phoneNeedsProjectsFolder && phoneAgent != null &&
                setup?.projects.orEmpty().none { AgentSelection.folderAllowed(AgentId(phoneAgent), it.directory) }

    /**
     * E20-S08: the project sheet's rows, one per folder. A server can list the same folder twice (a worktree, a
     * trailing "/"); the first listing wins, and it says Separate copy when any listing of that folder does.
     */
    val projectChoices: List<NewConversationProject>
        get() =
            setup?.projects.orEmpty().groupBy { projectPathKey(it.directory) }.values.map { same ->
                same.first().copy(separateCopy = same.any { it.separateCopy })
            }

    /** Whether [project] is the chosen folder, however its path is spelled. */
    public fun isChosen(project: NewConversationProject): Boolean =
        directory != null && projectPathKey(project.directory) == projectPathKey(directory)
}

/** One spelling per folder: no trailing "/" (the root stays "/"). */
internal fun projectPathKey(path: String): String = path.trim().trimEnd('/').ifEmpty { if (path.isBlank()) "" else "/" }

public interface NewConversationActions {
    public fun openCreatedConversation() {}

    /** N-48: Discard the unconfirmed first message: the form opens again, empty. */
    public fun discardUnconfirmed() {}

    /** B-096/B-103: read the unconfirmed first message's record again (the server may be back); frees the form when it is settled. */
    public fun recheckUnconfirmed() {}

    public fun shown() {}

    public fun back()

    public fun agents() {}

    public fun addServer()

    public fun chooseServer(profile: ProfileId)

    public fun draftChanged(text: String)

    public fun chooseDirectory(directory: String)

    public fun chooseModel(model: ModelRef?)

    /** The thinking effort for the chosen model; null is its own default. */
    public fun chooseVariant(variant: String?) {}

    /** N-51: a model sheet star: adds or removes [model] from this server's favourites. */
    public fun toggleFavorite(model: ModelRef) {}

    public fun chooseAgent(agent: String?)

    /** An agent on this phone, or null for the server's own agent; remembered per server. */
    public fun choosePhoneAgent(agent: String?) {}

    /** A dimmed agent: opens its setup (the Agents page); coming back rereads the offer. */
    public fun setUpPhoneAgent(agent: String) {
        agents()
    }

    public fun chooseMode(mode: String) {
        chooseAgent(mode)
    }

    /** A starter fills an empty prompt; it never sends. */
    public fun chooseStarter(starter: NewConversationStarter, label: String) {}

    /** From a first message that was definitely not sent: back to its words and choices. */
    public fun editPrompt() {}

    public fun separateCopy(on: Boolean)

    public fun sheet(sheet: NewConversationSheet?)

    public fun start()

    public fun import(conversation: ImportableConversation)

    public fun repair() {}

    public fun retryLoad()

    /** Starts this phone's OpenCode when [NewConversationUi.phoneOff]. */
    public fun startPhoneServer() {}

    /**
     * "Check <agent> again" (a start refused as "not ready", a row whose sign-in check did not answer): asks the phone's
     * agents now. After a refused start the start then goes again; otherwise the page reads its offer again.
     */
    public fun checkPhoneAgents() {}

    /** New project in the project sheet: asks for its name. */
    public fun askNewProject() {}

    /** The project sheet's Phone storage row without access: opens Android's page for it. */
    public fun requestPhoneStorage() {}

    /** The Phone storage row with access: opens the folder list of phone storage. */
    public fun browsePhoneStorage() {}

    public fun enterStorageFolder(path: String) {}

    public fun closeStorageFolders() {}

    /** Uses the folder shown as the project (never the storage root itself). */
    public fun useStorageFolder() {}

    /** New folder inside the folder shown: asks for its name. */
    public fun askNewStorageFolder() {}

    /**
     * The sentence "this agent works only in the projects on this phone": with none to choose it asks for a new
     * project's name right there, otherwise it opens the project chooser.
     */
    public fun chooseProjectForAgent() {}

    /** The name dialog's answer: makes the folder with Git and picks it; null closes the dialog. */
    public fun createProject(name: String?) {}

    /** B-063: makes the missing project folder again (empty, with Git). */
    public fun remakeFolder() {}

    /** Issue 147: the phone-storage notice's action (allow All files access, or restart the side that can't see it). */
    public fun fixPhoneFolder() {}
}

@AssistedInject
public class NewConversationViewModel(
    @Assisted private val route: ChatsRoute.New,
    private val host: NewConversationHost,
    private val nav: AppNavigator,
    private val clock: Clock,
    private val reports: NewChatReports,
    kept: Set<ImportsLastKnown> = emptySet(),
) : ViewModel(), NewConversationActions {
    private val importShelf = kept.firstOrNull()
    private val mutable =
        MutableStateFlow(
            if (route.initialText.isEmpty()) NewConversationUi()
            else NewConversationUi(text = route.initialText, textRevision = 1)
        )
    /** Whether [ChatsRoute.New.reportKey] has had its one report. */
    private var reported = false
    public val state: StateFlow<NewConversationUi> = mutable.asStateFlow()
    private val edits = Mutex()
    private var generation = 0
    private var attempt: NewConversationAttempt? = null

    init {
        viewModelScope.launch {
            host.serversRead.collect { read ->
                mutable.update { it.copy(serversRead = read) }
                // A tap kept for the list that the page's own setup outran: the list is read now, so it goes on.
                val kept = mutable.value
                if (read && kept.startQueued && kept.setup != null && !kept.busy) {
                    mutable.update { it.copy(startQueued = false) }
                    start()
                }
            }
        }
        viewModelScope.launch {
            host.servers.collect { servers ->
                mutable.update { it.copy(servers = servers) }
                // The person's Start (here or on This phone) brought the server back: read it again.
                val shown = mutable.value
                if (shown.phoneOff && !shown.startingPhone && servers.none { it.profile == shown.profile && it.phoneOff })
                    shown.profile?.let(::chooseServer)
                if (mutable.value.profile == null) {
                    val target =
                        route.profile?.let { wanted ->
                            servers.firstOrNull { it.profile == wanted || wanted in it.standsFor }?.profile
                        }
                            ?: servers.firstOrNull { it.connected }?.profile
                            ?: servers.singleOrNull()?.profile
                    target?.let(::chooseServer)
                }
            }
        }
    }

    // This entry owns its draft in memory while pickers or other pages cover it. A new entry with an empty field
    // shows the words left unsent in this project (P25, F: new_chat_screen.dart:119-175); resuming reads nothing.
    /** Agent setup was opened from here: the agents may have changed when this screen is shown again. */
    private var refreshOnShown = false

    /** [ChatsRoute.New.phoneAgent] is applied once, on the first setup that offers it. */
    private var wantedApplied = false

    override fun shown() {
        // Back from Android's All files access page: the Phone storage row says what the person answered.
        if (mutable.value.phoneStorage != null) readPhoneStorage()
        // Back from Android's page, or Android took the access back meanwhile: the folder is checked again.
        mutable.value.let { now ->
            if (now.phoneFolder != null && now.profile != null && now.directory != null)
                checkPhoneFolder(now.profile, now.directory)
        }
        if (!refreshOnShown) return
        val profile = mutable.value.profile ?: return
        refreshOnShown = false
        viewModelScope.launch {
            // Only the offered agents are reread: the draft, project, model and agent stay as they are.
            val loaded = host.setup(profile) as? Outcome.Ok ?: return@launch
            var reread: String? = null
            mutable.update {
                if (it.profile == profile && it.setup != null) {
                    // A phone agent that is no longer offered (removed, signed out) goes back to OpenCode.
                    val kept =
                        it.phoneAgent?.takeIf { id -> loaded.value.phone?.choices?.any { c -> c.id == id } == true }
                    reread = kept
                    it.copy(
                        setup = loaded.value,
                        phoneAgent = kept,
                        phoneModels = if (kept == null) emptyList() else it.phoneModels,
                        phoneModel = if (kept == null) null else it.phoneModel,
                    )
                } else it
            }
            // Setup may have installed or signed in the agent, or changed its models: its list is read again
            // (before, the list read when the page opened stayed until the app restarted).
            reread?.let { agent -> loadPhoneModels(profile, agent) }
        }
    }

    override fun back() {
        val shown = mutable.value
        val profile = shown.profile ?: return nav.back()
        // Leaving while a conversation starts cancels nothing already sent: the words and the queued message are
        // saved, and the conversation appears in the list when it is made; no page opens by itself.
        if (shown.busy && shown.openingChat) return nav.back()
        if (shown.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            val saved = edits.withLock { host.saveDraft(profile, shown.directory, shown.text) }
            mutable.update { it.copy(busy = false, draftFailed = saved is Outcome.Failed) }
            if (saved is Outcome.Ok) {
                report(NewChatOutcome.Left)
                nav.back()
            }
        }
    }

    /** The one report a keyed new conversation makes (F: `showNewChat(onSent:)`, then cancelled when not sent). */
    private fun report(outcome: NewChatOutcome) {
        val key = route.reportKey ?: return
        if (reported) return
        reported = true
        reports.report(key, outcome)
    }

    override fun onCleared() {
        report(NewChatOutcome.Left)
    }

    override fun agents() {
        refreshOnShown = true
        nav.open(AgentsRoute.Home)
    }

    /** A dimmed agent's "Set up" opens that agent's own sheet (not the whole page) and comes back here when it is ready. */
    override fun setUpPhoneAgent(agent: String) {
        refreshOnShown = true
        nav.open(AgentsRoute.Agent(agent, forNewChat = true))
    }

    override fun addServer(): Unit = nav.open(ServersRoute.Home)

    override fun chooseServer(profile: ProfileId) {
        if (mutable.value.busy || mutable.value.attempted || mutable.value.deliveryUnconfirmed)
            return
        val ticket = ++generation
        // A server the person turned off cannot answer: say so now instead of waiting on it.
        if (mutable.value.servers.any { it.profile == profile && it.phoneOff }) {
            mutable.update {
                it.copy(profile = profile, busy = false, failure = null, setup = null, phoneOff = true)
            }
            return
        }
        mutable.update {
            it.copy(profile = profile, busy = true, failure = null, setup = null, phoneOff = false, preparingSlow = false)
        }
        viewModelScope.launch {
            delay(PREPARING_SLOW_MS)
            if (ticket == generation) mutable.update { if (it.busy && it.setup == null) it.copy(preparingSlow = true) else it }
        }
        viewModelScope.launch {
            // Issue 147: the setup last read in this run shows at once; the fresh read below revalidates it behind.
            val cached = host.cachedSetup(profile)
            if (cached != null && ticket == generation) applySetup(profile, ticket, cached)
            // This phone's own server: the page waits on the server's state, not on a failed read, and reads once it is up.
            val state = host.serverState(profile)
            var answered: Outcome<NewConversationSetup>
            if (state.value == NewConversationServerState.NotPhone) {
                // Never wait for ever: a server that does not answer in time is a failure with a Try again.
                answered =
                    withTimeoutOrNull(SETUP_WAIT) { host.setup(profile) }
                        ?: Outcome.Failed(Failure.Network(NetworkWhy.Unknown))
                // Another computer that is reconnecting refuses the read at once: read again for a bounded time.
                for (again in 1..SETUP_RETRIES) {
                    val refused = answered
                    if (refused !is Outcome.Failed || cached != null || refused.failure !is Failure.Network) break
                    delay(SETUP_RETRY_EVERY_MS)
                    if (ticket != generation) return@launch
                    answered =
                        withTimeoutOrNull(SETUP_WAIT) { host.setup(profile) }
                            ?: Outcome.Failed(Failure.Network(NetworkWhy.Unknown))
                }
            } else {
                var again = 0
                var startAsked = false
                while (true) {
                    if (state.value == NewConversationServerState.StoppedByPerson) {
                        // The same page the Start action belongs to; nothing is read from a server the person stopped.
                        if (ticket == generation)
                            mutable.update {
                                it.copy(busy = false, failure = null, setup = null, phoneOff = true, preparingSlow = false)
                            }
                        return@launch
                    }
                    // Stopped by the system with nothing starting it: ask for the one start (the Start action's own).
                    if (state.value == NewConversationServerState.Stopped && !startAsked) {
                        startAsked = true
                        if (host.startPhoneServer(profile) is Outcome.Failed && state.value == NewConversationServerState.Stopped) {
                            answered = Outcome.Failed(Failure.Network(NetworkWhy.Refused))
                            break
                        }
                    }
                    // Starting: follow the state as it changes; the read happens the moment it is up. Bounded by the
                    // phone server's own start budget (its ready timeout, 90 s): past it the page says so, never waits on.
                    if (state.value == NewConversationServerState.Starting || state.value == NewConversationServerState.Stopped) {
                        val settled =
                            withTimeoutOrNull(SETUP_WAIT) {
                                state.first {
                                    it != NewConversationServerState.Starting && it != NewConversationServerState.Stopped
                                }
                            }
                        if (settled == null) {
                            answered = Outcome.Failed(Failure.TimedOut())
                            break
                        }
                        if (settled == NewConversationServerState.StoppedByPerson) continue
                    }
                    if (ticket != generation) return@launch
                    if (state.value == NewConversationServerState.Failed && cached == null) {
                        answered = Outcome.Failed(Failure.Network(NetworkWhy.Refused))
                        break
                    }
                    answered =
                        withTimeoutOrNull(SETUP_WAIT) { host.setup(profile) }
                            ?: Outcome.Failed(Failure.Network(NetworkWhy.Unknown))
                    if (answered !is Outcome.Failed || cached != null) break
                    // The server went back to starting: wait for it again. Right after Up it can still be finishing
                    // its opening: one short re-read in that case only, then the failure words.
                    if (state.value == NewConversationServerState.Starting && again < 3) {
                        again++
                        continue
                    }
                    if (state.value == NewConversationServerState.Up && again < 1) {
                        again++
                        delay(SETUP_AFTER_UP_REREAD_MS)
                        if (ticket != generation) return@launch
                        continue
                    }
                    break
                }
            }
            when (val loaded = answered) {
                is Outcome.Failed ->
                    // A page already showing the remembered setup stays usable when the fresh read fails.
                    if (ticket == generation && cached == null)
                        mutable.update { it.copy(busy = false, failure = loaded.failure, startQueued = false) }
                is Outcome.Ok ->
                    if (ticket == generation) {
                        if (cached != null) {
                            // Behind a page already showing the remembered setup: the fresh lists replace the old in place;
                            // the person's words, folder, model and agent stay as they are.
                            mutable.update { it.copy(setup = loaded.value, favorites = loaded.value.favorites) }
                        } else applySetup(profile, ticket, loaded.value)
                    }
            }
        }
    }

    /** Shows [setup] on the page and settles what follows it (agent, models, draft, folder); the page is usable from here. */
    private suspend fun applySetup(profile: ProfileId, ticket: Int, setup: NewConversationSetup) {
        mutable.update {
            it.copy(
                setup = setup,
                directory = setup.directory,
                model = setup.model,
                favorites = setup.favorites,
                agent = setup.agent,
                phoneAgent = setup.phone?.selected,
                // The default agent's last known list shows at once; the read below replaces it.
                phoneModels =
                    setup.phone?.selected?.let { a -> host.cachedPhoneAgentModels(profile, a) }
                        ?.models.orEmpty(),
                phoneModel =
                    setup.phone?.selected?.let { a -> host.cachedPhoneAgentModels(profile, a) }?.model,
                variant = null,
                busy = false,
                repairAvailable =
                    setup.directory?.let { dir -> host.canRepair(profile, dir) } ==
                        true,
            )
        }
        val wanted = route.phoneAgent
        val offer = setup.phone
        if (
            wanted != null && !wantedApplied && offer != null &&
                offer.choices.none { c -> c.id == wanted } &&
                offer.needsSetup.none { c -> c.id == wanted }
        ) {
            // A ready agent the offer lists only once chosen (not yet certified for chat, as Flutter's
            // agentChoosable): the Agents card's choice is saved like a tap and the offer read again,
            // now listing it. Before, the page dropped it and showed the remembered agent instead.
            wantedApplied = true
            if (host.choosePhoneAgent(profile, wanted) is Outcome.Ok && ticket == generation) {
                chooseServer(profile)
                return
            }
        }
        if (
            wanted != null && !wantedApplied &&
                setup.phone?.choices?.any { c -> c.id == wanted } == true
        ) {
            // The Agents page chose this agent: it is the one chosen here, remembered like a tap.
            wantedApplied = true
            if (setup.phone?.selected != wanted) choosePhoneAgent(wanted)
            else loadPhoneModels(profile, wanted)
        } else {
            val chosen = setup.phone?.selected
            if (chosen != null) loadPhoneModels(profile, chosen)
            else setup.phone?.choices?.map { c -> c.id }?.takeIf { it.isNotEmpty() }?.let { ready ->
            // No agent chosen yet: the ready agents' lists are read now, so a pick shows one at once.
                viewModelScope.launch { host.prefetchPhoneAgentModels(profile, ready) }
            }
        }
        restore(profile, setup.directory)
        setup.directory?.let { checkFolder(profile, it) }
        startInWantedFolder(profile, setup.directory)
        // The tap kept while the page got ready: the words and the folder are settled now.
        if (ticket == generation && mutable.value.startQueued) {
            mutable.update { it.copy(startQueued = false) }
            start()
        }
    }

    /**
     * [ChatsRoute.New.directory] is applied once; a folder the server no longer has leaves the usual one. Where the app
     * cannot check (a helper on another computer, or the phone's Linux not running) the old folder is used, as Flutter's
     * start-new-chat uses the old conversation's folder unchecked.
     */
    private var wantedFolderApplied = false

    private suspend fun startInWantedFolder(profile: ProfileId, current: String?) {
        val wanted = route.directory?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (wantedFolderApplied) return
        wantedFolderApplied = true
        if (wanted == current) return
        if (host.folderExists(profile, wanted) != false) chooseDirectory(wanted)
    }

    private suspend fun restore(profile: ProfileId, directory: String?) {
        edits.withLock {
            val before = mutable.value
            if (before.profile != profile || before.directory != directory) return
            when (val review = host.review(profile, directory)) {
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(
                            deliveryUnconfirmed = true,
                            attempted = true,
                            reviewConversation = null,
                            failure = review.failure,
                        )
                    }
                is Outcome.Ok -> {
                    if (review.value != null) {
                        attempt =
                            NewConversationAttempt(
                                directory.orEmpty(),
                                ref = review.value,
                                deliveryUnconfirmed = true,
                                creationAttempted = true,
                            )
                        mutable.update {
                            it.copy(
                                deliveryUnconfirmed = true,
                                attempted = true,
                                reviewConversation = review.value,
                            )
                        }
                    } else
                        mutable.update {
                            // attempted too: a record that settled (or is gone) leaves no frame to be stuck in.
                            it.copy(
                                deliveryUnconfirmed = false,
                                attempted = if (before.deliveryUnconfirmed) false else it.attempted,
                                failure = if (before.deliveryUnconfirmed) null else it.failure,
                                reviewConversation = null,
                            )
                        }
                }
            }
            // P25 (F: new_chat_screen.dart:119-175, `KitDraft` per project): words typed and left unsent here (Close,
            // Back) come back when New conversation opens again on this project with an empty field. A sent first
            // message clears them once its delivery is confirmed; offered words (a starter text) are never replaced.
            // An unresolved first-send receipt restores the words of the conversation offered for review, as before.
            if (before.text.isNotEmpty()) {
                // Words typed while the page was getting ready are this project's draft from now on.
                if (before.text != route.initialText && !mutable.value.deliveryUnconfirmed)
                    host.saveDraft(profile, directory, before.text)
                return
            }
            if (route.initialText.isNotEmpty() && !mutable.value.deliveryUnconfirmed) return
            when (val saved = host.draft(profile, directory)) {
                is Outcome.Failed -> mutable.update { it.copy(draftFailed = true) }
                is Outcome.Ok ->
                    mutable.update {
                        // The reads above wait on storage and the server: words typed meanwhile (or a server or
                        // project changed meanwhile) win, and are never replaced by the stored draft.
                        if (it.text.isNotEmpty() || it.profile != profile || it.directory != directory) it
                        else
                            it.copy(
                                text = saved.value,
                                textRevision = it.textRevision + 1,
                                draftFailed = false,
                            )
                    }
            }
        }
    }

    /**
     * The profile a start runs on: a phone agent's conversation lives on the phone helper's, so its first-message
     * record is read, checked and discarded there (SEND-STALE: Check again and Discard read the server's own record,
     * found none, and the next Start was refused again by the helper's).
     */
    private fun startProfile(shown: NewConversationUi): ProfileId? =
        shown.setup?.phone?.takeIf { shown.phoneAgent != null }?.helper ?: shown.profile

    override fun openCreatedConversation() {
        val ref = mutable.value.reviewConversation ?: return
        viewModelScope.launch {
            // Re-read the persisted scope receipt immediately before navigation; profile edits
            // never redirect it.
            val shown = mutable.value
            val profile = startProfile(shown) ?: return@launch
            val current = host.review(profile, shown.directory)
            if (current is Outcome.Ok && current.value == ref) {
                if (nav.top.value == route) nav.back()
                nav.openChat(ref)
            } else mutable.update { it.copy(reviewConversation = null) }
        }
    }

    override fun recheckUnconfirmed() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        if (!shown.deliveryUnconfirmed) return
        val target = startProfile(shown) ?: return
        if (target == profile) {
            viewModelScope.launch { restore(profile, shown.directory) }
            return
        }
        viewModelScope.launch { recheckPhoneStart(target, shown.directory) }
    }

    /**
     * Check again for a phone agent's start: the helper's own record decides. Confirmed (the helper made this start's
     * conversation) opens it; a record of an earlier start that is settled now leaves the page ready to start; one
     * that is still open keeps the notice.
     */
    private suspend fun recheckPhoneStart(target: ProfileId, directory: String?) {
        edits.withLock {
            val before = attempt
            when (val review = host.review(target, directory)) {
                is Outcome.Failed -> mutable.update { it.copy(failure = review.failure) }
                is Outcome.Ok ->
                    if (review.value == null) {
                        attempt = null
                        val made = before?.takeIf { it.creationAttempted }?.ref
                        mutable.update {
                            it.copy(
                                deliveryUnconfirmed = false,
                                attempted = false,
                                failure = null,
                                reviewConversation = null,
                                text = if (made != null) "" else it.text,
                                textRevision = if (made != null) it.textRevision + 1 else it.textRevision,
                            )
                        }
                        if (made != null && nav.top.value == route) {
                            nav.back()
                            nav.openChat(made)
                        }
                    }
            }
        }
    }

    override fun discardUnconfirmed() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        if (!shown.deliveryUnconfirmed && !shown.creationUnconfirmed) return
        val target = startProfile(shown) ?: return
        viewModelScope.launch {
            edits.withLock {
                when (val done = host.dismissReview(target, shown.directory)) {
                    is Outcome.Failed -> mutable.update { it.copy(failure = done.failure) }
                    is Outcome.Ok -> {
                        attempt = null
                        host.saveDraft(profile, shown.directory, "")
                        mutable.update {
                            it.copy(
                                deliveryUnconfirmed = false,
                                creationUnconfirmed = false,
                                attempted = false,
                                reviewConversation = null,
                                failure = null,
                                text = "",
                                textRevision = it.textRevision + 1,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun startPhoneServer() {
        val profile = mutable.value.profile ?: return
        if (!mutable.value.phoneOff || mutable.value.startingPhone) return
        mutable.update { it.copy(startingPhone = true, failure = null) }
        viewModelScope.launch {
            val started = host.startPhoneServer(profile)
            mutable.update { it.copy(startingPhone = false) }
            when (started) {
                is Outcome.Failed -> mutable.update { it.copy(failure = started.failure) }
                is Outcome.Ok -> chooseServer(profile)
            }
        }
    }

    override fun retryLoad() {
        mutable.value.profile?.let(::chooseServer)
    }

    override fun checkPhoneAgents() {
        val refused = mutable.value.phoneStartProblem?.way == PhoneStartWay.CheckAgain
        viewModelScope.launch {
            host.checkPhoneAgents()
            if (refused) start() else retryLoad()
        }
    }

    override fun draftChanged(text: String) {
        val before = mutable.value
        // Getting the page ready never locks the field (RC1 device pass: 90 s of "Still reading…" then an empty box):
        // words typed meanwhile are kept here and saved as the project's draft once the project is known (restore).
        val preparing = before.busy && before.setup == null
        if ((before.busy && !preparing) || before.deliveryUnconfirmed) return
        mutable.update { it.copy(text = text) }
        val current = mutable.value
        val profile = current.profile ?: return
        if (current.setup == null) return
        viewModelScope.launch {
            edits.withLock {
                val saved = host.saveDraft(profile, current.directory, text)
                if (
                    mutable.value.profile == profile &&
                        mutable.value.directory == current.directory &&
                        mutable.value.text == text
                )
                    mutable.update { it.copy(draftFailed = saved is Outcome.Failed) }
            }
        }
    }

    override fun chooseDirectory(directory: String) {
        if (
            mutable.value.busy ||
                mutable.value.attempted ||
                mutable.value.deliveryUnconfirmed ||
                mutable.value.setup?.canChangeProject != true ||
                !directory.trim().startsWith('/') ||
                directory.trim().split('/').any { it == ".." || it == "." } ||
                directory.any { it.code <= 0x1f || it.code == 0x7f }
        )
            return
        val before = mutable.value
        val profile = before.profile ?: return
        mutable.update {
            it.copy(
                directory = directory.trim(),
                sheet = null,
                failure = null,
                separateCopy = false,
                repairAvailable = host.canRepair(profile, directory.trim()),
                repair = null,
                folderMissing = false,
                remakeFailure = null,
                phoneFolder = null,
                phoneFolderFailure = null,
                phoneFolderFixed = null,
            )
        }
        checkFolder(profile, directory.trim())
        viewModelScope.launch {
            edits.withLock {
                if (before.text.isNotEmpty()) {
                    val saved = host.saveDraft(profile, directory.trim(), before.text)
                    if (saved is Outcome.Ok) host.saveDraft(profile, before.directory, "")
                    mutable.update { it.copy(draftFailed = saved is Outcome.Failed) }
                }
            }
            if (before.text.isEmpty()) restore(profile, directory.trim())
            host.saveChoices(profile, directory.trim(), before.model, before.agent)
        }
    }

    override fun chooseModel(model: ModelRef?) {
        if (mutable.value.busy || mutable.value.deliveryUnconfirmed) return
        // The model is the last choice of the sheet: picking one closes it.
        val shown = mutable.value
        val phone = shown.phoneAgent
        val profile = shown.profile
        if (phone != null && profile != null) {
            mutable.update { it.copy(phoneModel = model, variant = null, sheet = null) }
            viewModelScope.launch { host.savePhoneAgentModel(profile, phone, model) }
            return
        }
        mutable.update { it.copy(model = model, variant = null, sheet = null) }
        saveChoices()
    }

    override fun chooseVariant(variant: String?) {
        val shown = mutable.value
        if (shown.busy || shown.deliveryUnconfirmed) return
        if (variant != null && variant !in shown.shownVariants) return
        mutable.update { it.copy(variant = variant) }
    }

    override fun toggleFavorite(model: ModelRef) {
        val profile = mutable.value.profile ?: return
        viewModelScope.launch {
            val now = host.toggleFavorite(profile, model)
            // A star that could not be saved stays as it was; the list never claims what the store refused.
            if (now is Outcome.Ok && mutable.value.profile == profile)
                mutable.update { it.copy(favorites = now.value) }
        }
    }

    override fun chooseStarter(starter: NewConversationStarter, label: String) {
        val shown = mutable.value
        if (
            shown.busy ||
                shown.openingChat ||
                shown.deliveryUnconfirmed ||
                shown.setup == null ||
                shown.text.isNotBlank() ||
                label.isBlank() ||
                starter !in newConversationStarters(shown.directory)
        )
            return
        draftChanged(starterInsertText(label))
        mutable.update {
            it.copy(textRevision = it.textRevision + 1, focusRevision = it.focusRevision + 1)
        }
    }

    override fun editPrompt() {
        val shown = mutable.value
        if (
            !shown.openingChat ||
                shown.busy ||
                shown.creationUnconfirmed ||
                shown.deliveryUnconfirmed
        )
            return
        // Nothing was made yet: the person may also change server or project. A made conversation
        // keeps its project; its words, agent and model stay editable and Retry reuses it.
        val nothingMade = attempt?.ref == null && attempt?.creationAttempted != true
        if (nothingMade) attempt = null
        mutable.update {
            it.copy(
                openingChat = false,
                attempted = if (nothingMade) false else it.attempted,
                textRevision = it.textRevision + 1,
            )
        }
    }

    override fun chooseMode(mode: String) {
        val shown = mutable.value
        if (shown.phoneAgent == null) return super.chooseMode(mode)
        if (shown.busy || shown.deliveryUnconfirmed || shown.attempted || !shown.phoneCanPlan) return
        mutable.update { it.copy(phonePlan = mode.equals("plan", ignoreCase = true)) }
    }

    override fun chooseAgent(agent: String?) {
        val setup = mutable.value.setup ?: return
        if (
            agent != null &&
                agent !in setup.modes &&
                (agent !in setup.agents || !isStartableAgent(agent))
        )
            return
        // The sheet stays open: Build/Plan and the model are the next choices in the same sheet.
        if (!mutable.value.busy && !mutable.value.deliveryUnconfirmed) {
            // The server's own agent is the choice now: a phone agent is no longer the one remembered.
            if (mutable.value.phoneAgent != null) choosePhoneAgent(null)
            mutable.update { it.copy(agent = agent) }
        }
        saveChoices()
    }

    override fun choosePhoneAgent(agent: String?) {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val phone = shown.setup?.phone ?: return
        if (shown.busy || shown.deliveryUnconfirmed || shown.attempted || shown.phoneAgent == agent) return
        if (agent != null && phone.choices.none { it.id == agent }) return
        mutable.update {
            it.copy(
                phoneAgent = agent,
                // Build/Plan and a separate copy belong to the server's own agent.
                agent = if (agent != null) null else it.agent,
                phonePlan = false,
                separateCopy = if (agent != null) false else it.separateCopy,
                // The list last read for this agent shows at once; the read below replaces it.
                phoneFolderFixed = null,
                phoneModels = agent?.let { a -> host.cachedPhoneAgentModels(profile, a) }?.models.orEmpty(),
                phoneModel = agent?.let { a -> host.cachedPhoneAgentModels(profile, a) }?.model,
                variant = null,
            )
        }
        viewModelScope.launch {
            if (host.choosePhoneAgent(profile, agent) is Outcome.Failed)
                mutable.update { it.copy(draftFailed = true) }
        }
        // The phone's OpenCode and the agents' helper are two Linux sides: the folder is checked for the one chosen.
        shown.directory?.let { checkPhoneFolder(profile, it) }
        if (agent != null) loadPhoneModels(profile, agent)
        else saveChoices()
    }

    /** The phone agent's own models; read while the person types, so Start does not wait for them. */
    /** The newest model read: only it may end [NewConversationUi.phoneModelsBusy] (the sheet's `loading`). */
    private var phoneModelsRead = 0

    private fun loadPhoneModels(profile: ProfileId, agent: String) {
        val read = ++phoneModelsRead
        mutable.update { it.copy(phoneModelsBusy = true) }
        viewModelScope.launch {
            val answer = host.phoneAgentModels(profile, agent)
            mutable.update {
                // An older read never clears the flag while a newer one is still on its way.
                if (read != phoneModelsRead) it
                else if (it.phoneAgent != agent || it.profile != profile) it.copy(phoneModelsBusy = false)
                else
                    when (answer) {
                        is Outcome.Ok ->
                            it.copy(
                                phoneModels = answer.value.models,
                                phoneModel = answer.value.model,
                                phoneModelsBusy = false,
                            )
                        is Outcome.Failed -> it.copy(phoneModelsBusy = false)
                    }
            }
        }
    }

    private fun saveChoices() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        viewModelScope.launch {
            edits.withLock {
                val saved = host.saveChoices(profile, shown.directory, shown.model, shown.agent)
                if (saved is Outcome.Failed) mutable.update { it.copy(draftFailed = true) }
            }
        }
    }

    override fun separateCopy(on: Boolean) {
        if (!mutable.value.busy && !mutable.value.attempted)
            mutable.update { it.copy(separateCopy = on && it.canCopy) }
    }

    override fun sheet(sheet: NewConversationSheet?) {
        // The model sheet never opens with nothing to choose.
        if (sheet == NewConversationSheet.Models && !mutable.value.modelChoosable) return
        val profile = mutable.value.profile
        mutable.update {
            it.copy(
                sheet = sheet,
                canCreateProject =
                    if (sheet == NewConversationSheet.Projects && profile != null) host.canCreateProject(profile)
                    else it.canCreateProject,
                projectFailure = if (sheet == NewConversationSheet.Projects) null else it.projectFailure,
            )
        }
        if (sheet == NewConversationSheet.Import) loadImports()
        if (sheet == NewConversationSheet.Projects) readPhoneStorage()
    }

    override fun askNewProject() {
        val shown = mutable.value
        if (!shown.canCreateProject || shown.creatingProject || shown.setup?.canChangeProject != true) return
        mutable.update { it.copy(askingProjectName = true, projectFailure = null) }
    }

    private fun readPhoneStorage() {
        val profile = mutable.value.profile ?: return
        viewModelScope.launch {
            val access = host.phoneStorage(profile)
            mutable.update { if (it.profile == profile) it.copy(phoneStorage = access) else it }
        }
    }

    override fun requestPhoneStorage() {
        val profile = mutable.value.profile ?: return
        if (mutable.value.phoneStorage != PhoneStorageAccess.Off) return
        viewModelScope.launch {
            // Android's page opens over the app; the answer is read when the person comes back (shown()).
            host.requestPhoneStorage(profile)
            readPhoneStorage()
        }
    }

    private var storageRun = 0

    override fun browsePhoneStorage() {
        if (mutable.value.phoneStorage != PhoneStorageAccess.On) return
        enterStorageFolder(PHONE_STORAGE_ROOT)
    }

    override fun enterStorageFolder(path: String) {
        if (path != PHONE_STORAGE_ROOT && !path.startsWith("$PHONE_STORAGE_ROOT/")) return
        if (path.split('/').any { it == ".." }) return
        val run = ++storageRun
        mutable.update {
            it.copy(storagePath = path, storageFolders = emptyList(), storageLoading = true, storageFailure = null)
        }
        viewModelScope.launch {
            val listed = host.storageFolders(path)
            if (run != storageRun) return@launch
            mutable.update {
                when (listed) {
                    is Outcome.Ok -> it.copy(storageFolders = listed.value, storageLoading = false)
                    is Outcome.Failed -> it.copy(storageFailure = listed.failure, storageLoading = false)
                }
            }
        }
    }

    override fun closeStorageFolders() {
        storageRun++
        mutable.update {
            it.copy(storagePath = null, storageFolders = emptyList(), storageLoading = false, storageFailure = null, askingProjectName = false)
        }
    }

    override fun useStorageFolder() {
        val shown = mutable.value
        val path = shown.storagePath ?: return
        if (path == PHONE_STORAGE_ROOT || shown.storageLoading || shown.storageFailure != null) return
        closeStorageFolders()
        chooseDirectory(path)
    }

    override fun askNewStorageFolder() {
        val shown = mutable.value
        if (shown.storagePath == null || shown.storageLoading || shown.creatingProject) return
        mutable.update { it.copy(askingProjectName = true, projectFailure = null) }
    }

    private fun createStorageFolder(parent: String, name: String) {
        val folder = io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName.validated(name) ?: return
        mutable.update { it.copy(askingProjectName = false, creatingProject = true, projectFailure = null) }
        viewModelScope.launch {
            val made = host.createStorageFolder(parent, folder)
            mutable.update { it.copy(creatingProject = false) }
            when (made) {
                is Outcome.Ok -> {
                    // A new folder is meant as the project: one flow from the row to a usable folder.
                    closeStorageFolders()
                    chooseDirectory(made.value)
                }
                is Outcome.Failed -> mutable.update { it.copy(projectFailure = made.failure) }
            }
        }
    }

    override fun chooseProjectForAgent() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        if (shown.phoneHasNoProject && host.canCreateProject(profile)) {
            mutable.update { it.copy(canCreateProject = true) }
            askNewProject()
        } else sheet(NewConversationSheet.Projects)
    }

    override fun createProject(name: String?) {
        val shown = mutable.value
        val profile = shown.profile
        if (name == null || profile == null) {
            if (!shown.creatingProject) mutable.update { it.copy(askingProjectName = false) }
            return
        }
        if (shown.creatingProject) return
        shown.storagePath?.let { return createStorageFolder(it, name) }
        val folder = io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName.validated(name) ?: return
        mutable.update { it.copy(askingProjectName = false, creatingProject = true, projectFailure = null) }
        viewModelScope.launch {
            val made = host.createProject(profile, folder)
            if (mutable.value.profile != profile) return@launch
            mutable.update { it.copy(creatingProject = false) }
            when (made) {
                is Outcome.Ok -> chooseDirectory(made.value)
                is Outcome.Failed -> mutable.update { it.copy(projectFailure = made.failure) }
            }
        }
    }

    /** B-063: a project whose folder is gone says so before Start, never after a refused send. */
    private fun checkFolder(profile: ProfileId, directory: String) {
        checkPhoneFolder(profile, directory)
        viewModelScope.launch {
            val there = host.folderExists(profile, directory)
            mutable.update {
                if (it.profile == profile && it.directory == directory)
                    it.copy(folderMissing = there == false)
                else it
            }
        }
    }

    /** The newest phone-folder check: only its answer is shown. */
    private var phoneFolderRead = 0

    /**
     * Issue 147: what the chosen folder in phone storage is to the side that will work in it (for the agent shown).
     * While it is read, a folder that looks like the phone's storage holds Start and says so: the answer (a restart, the
     * access) must be on the page before the person types and taps Start.
     */
    private fun checkPhoneFolder(profile: ProfileId, directory: String) {
        val read = ++phoneFolderRead
        val agent = mutable.value.phoneAgent
        if (PhoneFolderReach.looksLikePhoneStorage(directory))
            mutable.update { if (it.directory == directory) it.copy(checkingPhoneFolder = true) else it }
        viewModelScope.launch {
            val reach = host.phoneFolder(profile, agent, directory)
            val both = reach == PhoneFolderReach.Restart && host.phoneFolderBothSides(profile, agent, directory)
            val waiting = if (reach == null) emptySet() else host.phoneFolderWaiting(profile)
            mutable.update {
                if (read == phoneFolderRead && it.profile == profile && it.directory == directory)
                    it.copy(
                        phoneFolder = reach,
                        checkingPhoneFolder = false,
                        phoneFolderBoth = both,
                        phoneFolderWaiting = waiting,
                    )
                else it
            }
            watchPhoneFolderWaiting(profile, directory)
        }
    }

    private var waitingWatch: kotlinx.coroutines.Job? = null

    /**
     * W3: while a side's restart waits for its work to end, the page follows it; when that side has restarted, the
     * folder is read again (the notice then goes by itself).
     */
    private fun watchPhoneFolderWaiting(profile: ProfileId, directory: String) {
        if (mutable.value.phoneFolderWaiting.isEmpty() || waitingWatch?.isActive == true) return
        waitingWatch =
            viewModelScope.launch {
                host.phoneFolderWaitingUpdates(profile)
                    .takeWhile { mutable.value.profile == profile }
                    .collect { now ->
                        if (now == mutable.value.phoneFolderWaiting) return@collect
                        mutable.update { if (it.profile == profile) it.copy(phoneFolderWaiting = now) else it }
                        val shown = mutable.value
                        if (shown.profile == profile && shown.directory == directory)
                            checkPhoneFolder(profile, directory)
                    }
            }
    }

    override fun fixPhoneFolder() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        val reach = shown.phoneFolder ?: return
        if (shown.fixingPhoneFolder || (reach != PhoneFolderReach.AppAccess && reach != PhoneFolderReach.Restart))
            return
        if (shown.chosenSideWaits) return
        // At once: the notice says what is happening and the action waits (a restart of the agents takes half a
        // minute); it ends with the folder read again, so the page says it is done or why not.
        val read = ++phoneFolderRead
        mutable.update { it.copy(fixingPhoneFolder = true, phoneFolderFailure = null, phoneFolderFixed = null) }
        viewModelScope.launch {
            // While the restart runs, a side that waits for its work (OpenCode writing a reply) shows at once.
            val following =
                launch {
                    host.phoneFolderWaitingUpdates(profile).collect { now ->
                        mutable.update {
                            if (it.profile == profile && it.fixingPhoneFolder) it.copy(phoneFolderWaiting = now) else it
                        }
                    }
                }
            val fixed = host.fixPhoneFolder(profile, shown.phoneAgent, reach, directory)
            following.cancel()
            val now = host.phoneFolder(profile, shown.phoneAgent, directory)
            val waiting = host.phoneFolderWaiting(profile)
            // W3 (F-042): a folder Linux could not find before it was bound is read again now it is.
            val there = if (fixed is Outcome.Ok && shown.folderMissing) host.folderExists(profile, directory) else null
            mutable.update {
                if (read != phoneFolderRead || it.profile != profile || it.directory != directory)
                    it.copy(fixingPhoneFolder = false)
                else
                    it.copy(
                        fixingPhoneFolder = false,
                        checkingPhoneFolder = false,
                        phoneFolder = now,
                        phoneFolderFailure = (fixed as? Outcome.Failed)?.failure,
                        phoneFolderFixed = reach.takeIf { fixed is Outcome.Ok && now == PhoneFolderReach.Ready },
                        phoneFolderWaiting = waiting,
                        folderMissing = if (there != null) !there else it.folderMissing,
                    )
            }
            watchPhoneFolderWaiting(profile, directory)
        }
    }

    override fun remakeFolder() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        if (!shown.folderMissing || shown.remakingFolder) return
        mutable.update { it.copy(remakingFolder = true, remakeFailure = null) }
        viewModelScope.launch {
            val made = host.remakeFolder(profile, directory)
            mutable.update {
                if (it.profile != profile || it.directory != directory) it.copy(remakingFolder = false)
                else
                    when (made) {
                        is Outcome.Ok -> it.copy(remakingFolder = false, folderMissing = false)
                        is Outcome.Failed -> it.copy(remakingFolder = false, remakeFailure = made.failure)
                    }
            }
        }
    }

    private fun loadImports() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        // Cache-first: the list last read for this folder shows at once; the live list replaces it in place.
        val first = importShelf?.peek(profile, directory)
        mutable.update {
            it.copy(
                importBusy = first == null,
                importFailed = false,
                imports = first?.toImports(),
                importsReadAtMs = first?.readAtMs ?: it.importsReadAtMs,
            )
        }
        viewModelScope.launch {
            if (first == null) {
                importShelf?.read(profile, directory)?.let { stored ->
                    mutable.update {
                        if (it.imports != null) it
                        else it.copy(importBusy = false, imports = stored.toImports(), importsReadAtMs = stored.readAtMs)
                    }
                }
            }
            when (val result = host.imports(profile, directory)) {
                is Outcome.Ok -> {
                    val at = clock.now().toEpochMilliseconds()
                    mutable.update { it.copy(importBusy = false, imports = result.value, importsReadAtMs = at) }
                    importShelf?.keep(profile, directory, KeptImports.of(result.value, at))
                }
                is Outcome.Failed ->
                    mutable.update { it.copy(importBusy = false, importFailed = true) }
            }
        }
    }

    override fun import(conversation: ImportableConversation) {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        if (shown.importBusy) return
        mutable.update { it.copy(importBusy = true, importFailed = false) }
        viewModelScope.launch {
            when (val result = host.import(profile, directory, conversation)) {
                is Outcome.Ok -> {
                    if (nav.top.value == route) nav.back()
                    nav.openChat(result.value)
                }
                is Outcome.Failed ->
                    mutable.update { it.copy(importBusy = false, importFailed = true) }
            }
        }
    }


    override fun repair() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        if (
            shown.busy ||
                !shown.repairAvailable ||
                shown.failure !is Failure.ProjectGit ||
                shown.creationUnconfirmed ||
                shown.deliveryUnconfirmed
        )
            return
        mutable.update { it.copy(busy = true, repairing = true, repair = null) }
        viewModelScope.launch {
            when (val result = host.repair(profile, directory)) {
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(busy = false, repairing = false, failure = result.failure)
                    }
                is Outcome.Ok -> {
                    mutable.update {
                        it.copy(busy = false, repairing = false, repair = result.value)
                    }
                    if (
                        result.value.result == GitLinkRepairResult.Repaired ||
                            result.value.result == GitLinkRepairResult.Healthy
                    ) {
                        // A definite Git rejection created no session. Repair continues the same
                        // explicit Start.
                        if (attempt?.ref == null && attempt?.failure is Failure.ProjectGit)
                            attempt = null
                        start()
                    }
                }
            }
        }
    }

    override fun start() {
        val shown = mutable.value
        // Issue 147: a tap on Start while the page is still getting ready is kept and acts when it is ready.
        if (
            ((shown.setup == null && shown.busy && !shown.repairing && shown.profile != null) || !shown.serversRead) &&
                !shown.openingChat && !shown.startQueued && shown.text.isNotBlank()
        ) {
            mutable.update { it.copy(startQueued = true) }
            return
        }
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        // The model list being read never holds Start back: it starts with the model last known for the agent, and the
        // read goes on behind (the chip that is loading says so).
        if (
            shown.busy ||
                shown.text.isBlank() ||
                shown.setup == null ||
                shown.creationUnconfirmed ||
                shown.deliveryUnconfirmed ||
                shown.folderMissing ||
                shown.phoneFolderBlocked ||
                shown.fixingPhoneFolder ||
                shown.checkingPhoneFolder ||
                shown.phoneNeedsProjectsFolder
        )
            return
        if (shown.separateCopy) {
            // The separate-copy readiness review remains explicit. Save the exact first
            // prompt and choices before handing it to that owner, just as for ordinary Start.
            mutable.update { it.copy(busy = true, failure = null) }
            viewModelScope.launch {
                val saved = edits.withLock {
                    when (val draft = host.saveDraft(profile, directory, shown.text)) {
                        is Outcome.Failed -> draft
                        is Outcome.Ok ->
                            host.saveChoices(profile, directory, shown.model, shown.agent)
                    }
                }
                mutable.update { it.copy(busy = false, draftFailed = saved is Outcome.Failed) }
                if (saved is Outcome.Ok)
                    nav.open(
                        ProjectsRoute.Project(
                            profile,
                            directory,
                            initialTask = shown.text.trim(),
                            autoStartIsolated = true,
                            taskProvider = shown.model?.providerId,
                            taskModel = shown.model?.modelId,
                            taskAgent = shown.agent,
                        )
                    )
            }
            return
        }
        mutable.update {
            it.copy(busy = true, openingChat = true, failure = null, attempted = true)
        }
        viewModelScope.launch {
            // Issue 147: checked again from the side that will work in the folder, right before the first message.
            val reach = host.phoneFolder(profile, shown.phoneAgent, directory)
            if (reach != null && reach != PhoneFolderReach.Ready) {
                mutable.update {
                    it.copy(busy = false, openingChat = false, attempted = shown.attempted, phoneFolder = reach)
                }
                return@launch
            }
            var openedConversation: ConversationRef? = null
            // A phone agent's conversation lives on the phone helper's profile, with the agent's own model.
            val phone = shown.setup?.phone?.takeIf { shown.phoneAgent != null }
            val target = phone?.helper ?: profile
            val model = if (phone != null) shown.phoneModel else shown.model
            val variant = shown.variant?.takeIf { it in shown.shownVariants }
            val agent = if (phone != null) null else shown.agent
            // Plan for a phone agent rides the first message only (never saved as the folder's remembered choice).
            val startMode = if (phone != null && shown.phonePlan && shown.phoneCanPlan) "plan" else agent
            val result = edits.withLock {
                val saved = host.saveDraft(target, directory, shown.text)
                val choices = host.saveChoices(target, directory, model, agent)
                when {
                    saved is Outcome.Failed ->
                        NewConversationAttempt(directory, failure = saved.failure)
                    choices is Outcome.Failed ->
                        NewConversationAttempt(directory, failure = choices.failure)
                    else ->
                        when (
                            val started =
                                io.github.eslamasabry.oc.core.common.attempt({
                                    Failure.StorageRefused()
                                }) {
                                    host.startWithEffort(
                                        target,
                                        directory,
                                        shown.text.trim(),
                                        model,
                                        variant,
                                        startMode,
                                        shown.separateCopy,
                                        attempt,
                                        onStarted = { ref ->
                                            if (nav.top.value == route) {
                                                // Keep this entry's ViewModel alive until the
                                                // receipt settles, while the real chat owns the
                                                // composer and the engine's pending bubble.
                                                openedConversation = ref
                                                nav.openChat(ref)
                                                leaveWhenChatCloses()
                                            }
                                        },
                                    )
                                }
                        ) {
                            is Outcome.Ok -> started.value
                            is Outcome.Failed ->
                                NewConversationAttempt(
                                    directory,
                                    failure = started.failure,
                                    deliveryUnconfirmed = true,
                                )
                        }
                }
            }
            attempt = result
            // The words are the phone agent's conversation now: the server's own draft for this folder is spent.
            if (target != profile && result.ref != null)
                edits.withLock { host.saveDraft(profile, directory, "") }
            // A put-back first message is the opened chat's failed bubble (with Try again): never a second copy here.
            if (openedConversation != null && result.failure != null && !result.deliveryUnconfirmed)
                edits.withLock { host.saveDraft(target, directory, "") }
            if (result.dispatched) report(NewChatOutcome.FirstMessageSent)
            else if (result.deliveryUnconfirmed) report(NewChatOutcome.Unknown)
            // A phone agent's conversation exists only once its helper made the agent: a start that was not confirmed
            // never opens a page that may not exist. It stays here, saying so, and the words are kept.
            val unopenedPhoneStart = phone != null && openedConversation == null && !result.dispatched
            if (!unopenedPhoneStart && (result.dispatched || result.deliveryUnconfirmed) && result.ref != null) {
                // Once the session exists, its chat owns the receipt and always has an editor.
                // An uncertain send must not strand the person on the chip-only starting frame.
                if (openedConversation == null && nav.top.value == route) {
                    nav.back()
                    nav.openChat(result.ref)
                }
                // A screen left lower in the stack starts the next conversation afresh.
                attempt = null
                // Open-transitions (S22 recording): a form the opened conversation covers keeps its starting frame. It
                // went back to its box, which takes focus whenever it comes into the page, while it was still drawn
                // under the arriving conversation: the keyboard came up over the conversation and dropped again as the
                // form left. It never becomes usable again (it steps off when the conversation closes), so its box is
                // never put back.
                val covered = openedConversation != null
                mutable.update {
                    it.copy(
                        // Preserve the saved first prompt until its receipt is confirmed. Never
                        // insert it into the chat editor, where Send could duplicate it.
                        text = if (result.dispatched) "" else it.text,
                        textRevision = it.textRevision + 1,
                        busy = covered,
                        attempted = !result.dispatched,
                        openingChat = covered,
                        deliveryUnconfirmed = result.deliveryUnconfirmed,
                        reviewConversation = result.ref.takeIf { result.deliveryUnconfirmed },
                    )
                }
            } else
                mutable.update {
                    it.copy(
                        busy = false,
                        // The chat this page opened shows a put-back first message as its own failed bubble.
                        failure = result.failure.takeIf { openedConversation == null },
                        creationUnconfirmed = result.creationAttempted && result.ref == null,
                        deliveryUnconfirmed = result.deliveryUnconfirmed,
                        reviewConversation = result.ref.takeIf { result.deliveryUnconfirmed && phone == null },
                    )
                }
        }
    }

    /**
     * N-43: this form stays alive under the new chat until the receipt settles, but Back from that chat goes to where
     * the person came from, never to the emptied form: once the form is on top again it steps off.
     */
    private fun leaveWhenChatCloses() {
        viewModelScope.launch {
            nav.top.dropWhile { it == route }.first { it == route }
            nav.back()
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatsRoute.New): NewConversationViewModel
    }
}

/** The phone agents' runtimes that list a plan or read-only mode (`PaseoPlanModes`); others are not offered Plan. */
private val PLAN_HOLDING_PROVIDERS = setOf("codex", "claude", "opencode", "copilot")

/** Server modes and background helpers are never peer agents in the chooser. */
public fun isStartableAgent(name: String): Boolean =
    name.isNotBlank() &&
        name.trim().lowercase() !in
            setOf(
                "build",
                "plan",
                "compaction",
                "compact",
                "title",
                "summary",
                "summarize",
                "explore",
                "general",
            )

/**
 * The line under a project's name in the project sheet: the folders it sits in, so same-named projects tell apart
 * ("work/app"). The phone's Linux home (`/root`) is where every project of the phone lives, so it is never part of the
 * line: a project straight in it has none. Longer than two folders keeps the last two behind an ellipsis.
 */
public fun projectPlace(directory: String): String? {
    val parts = directory.trim().replace('\\', '/').split('/').filter { it.isNotEmpty() }
    val parents = parts.dropLast(1).let { if (it.firstOrNull() == "root" && directory.trim().startsWith("/root")) it.drop(1) else it }
    return when {
        parents.isEmpty() -> null
        parents.size <= 2 -> parents.joinToString("/")
        else -> "…/" + parents.takeLast(2).joinToString("/")
    }
}
