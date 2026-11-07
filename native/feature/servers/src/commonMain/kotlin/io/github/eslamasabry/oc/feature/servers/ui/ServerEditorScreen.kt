package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.feature.servers.discovery.LanDiscovery
import io.github.eslamasabry.oc.feature.servers.discovery.NoLanDiscovery
import io.github.eslamasabry.oc.feature.servers.pairing.PairingText
import io.github.eslamasabry.oc.feature.servers.pairing.pairingProblemText
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.action.KitPasteButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.setup.KitServersLink
import io.github.eslamasabry.oc.ui.kit.setup.KitServersLinkState
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

/** Secrets are held in memory and shown as Saved · Replace; none is a saved-state field. */
@Composable
public fun ServerEditorScreen(
    state: ServerEditorUi,
    actions: ServerEditorActions,
    lan: LanDiscovery = NoLanDiscovery(),
) {
    val restored = remember { actions.takeDraft() }
    val name = rememberTextFieldState(restored?.name.orEmpty())
    val address = rememberTextFieldState(restored?.address.orEmpty())
    val username = rememberTextFieldState(restored?.username ?: "opencode")
    val directory = rememberTextFieldState(restored?.directory.orEmpty())
    val password = remember { TextFieldState() }
    var backend by remember { mutableStateOf(restored?.backend ?: BackendKind.OpenCode2) }
    var tailscale by remember { mutableStateOf(restored?.tailscale ?: false) }
    var keepSecret by remember { mutableStateOf(restored?.keepSecret ?: false) }
    var heldSecret by remember {
        mutableStateOf(
            restored?.secret?.reveal()?.takeIf { restored?.keepSecret != true && it.isNotEmpty() }
        )
    }
    var confirmedOrigin by remember { mutableStateOf(restored?.cleartextOrigin) }
    var pin by remember { mutableStateOf(restored?.certificatePin) }
    var pinOrigin by remember { mutableStateOf(restored?.certificateOrigin) }
    var loaded by remember { mutableStateOf(restored != null) }
    var trustOrigin by remember { mutableStateOf<ServerOrigin?>(null) }
    var commandsOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var tailscaleOpened by remember { mutableStateOf(false) }
    var tailscaleReturned by remember { mutableStateOf(false) }
    var pairCommandOpen by remember { mutableStateOf(false) }
    var manualOpen by remember {
        mutableStateOf(!state.newServer || restored?.address?.isNotBlank() == true)
    }
    val scroll = rememberScrollState()
    LaunchedEffect(manualOpen, state.step) {
        if (manualOpen) scroll.scrollTo(0)
    }
    LaunchedEffect(state.failure, state.record?.needsSecret, state.fillGeneration) {
        if (state.failure != null || state.record?.needsSecret == true || state.fillGeneration > 0)
            manualOpen = true
    }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(state.passwordFocusGeneration) {
        if (state.passwordFocusGeneration > 0) {
            delay(250) // after the fields have opened and the pick has filled the address
            try {
                passwordFocus.requestFocus()
            } catch (_: IllegalStateException) {}
        }
    }
    // Ports the person already used are searched too; the request asks the system only when tapped.
    val findRequest = lan.rememberAccess { access ->
        actions.findAccess(
            access,
            listOfNotNull(editorOrigin(address.text.toString())?.port).toSet(),
        )
    }
    var clip by remember { mutableStateOf<String?>(null) }
    var clipId by remember { mutableLongStateOf(0L) }
    KitClipboardEffect(if (clip == null) null else clipId, clip, onDone = { clip = null })
    val copyLabel = stringResource(Res.string.handoffCopyCommand)
    val copiedLabel = stringResource(Res.string.kitCopied)
    // App-authored commands with no credential; the block hands over its displayed source.
    val copy: (String) -> Unit = {
        clipId++
        clip = it
    }
    fun TextFieldState.fill(value: String) = edit { replace(0, length, value) }
    LaunchedEffect(state.record) {
        val record = state.record ?: return@LaunchedEffect
        if (loaded && !state.saved) return@LaunchedEffect
        name.fill(record.name)
        address.fill(record.address)
        username.fill(record.username)
        directory.fill(record.directory)
        backend = record.backend
        tailscale = record.tailscale
        keepSecret = record.hasSecret && !record.needsSecret
        if (state.saved) {
            heldSecret = null
            password.fill("")
        }
        confirmedOrigin = record.cleartextOrigin
        pin = record.certificatePin
        pinOrigin = record.certificateOrigin
        loaded = true
    }
    LaunchedEffect(state.fillGeneration) {
        val fill = actions.takePairing() ?: return@LaunchedEffect
        address.fill(fill.address)
        if (name.text.isBlank()) fill.hostname?.let { name.fill(it) }
        fill.username?.let { username.fill(it) }
        fill.takePassword()?.let {
            heldSecret = it
            password.fill("")
            keepSecret = false
        }
        backend = BackendKind.OpenCode2
        tailscale = false
    }
    // A server is named after its host, never its IP; the person can rename it inline.
    val suggestedName =
        suggestServerName(
            address.text.toString(),
            stringResource(Res.string.upgradeConnectNameFallback),
        )
    fun input(): ServerEditorInput =
        ServerEditorInput(
            state.record?.id,
            name.text.toString().ifBlank { if (state.newServer) suggestedName else "" },
            address.text.toString(),
            username.text.toString(),
            backend,
            directory.text.toString(),
            (heldSecret ?: password.text.toString()).let(::SecretText),
            keepSecret,
            tailscale,
            confirmedOrigin,
            pin,
            pinOrigin,
            state.record?.editToken,
        )
    val dirty =
        state.record?.let {
            name.text.toString() != it.name ||
                address.text.toString() != it.address ||
                username.text.toString() != it.username ||
                directory.text.toString() != it.directory ||
                backend != it.backend ||
                !keepSecret && it.hasSecret ||
                password.text.isNotEmpty() ||
                heldSecret != null ||
                confirmedOrigin != it.cleartextOrigin ||
                pin != it.certificatePin
        }
            ?: (name.text.isNotEmpty() ||
                address.text.isNotEmpty() ||
                password.text.isNotEmpty() ||
                heldSecret != null)
    // The effect outlives this composition's `state`; read the loaded address fresh, or the fill
    // from a loaded record would look like an edit and drop its saved HTTP consent and pin.
    val savedAddress by rememberUpdatedState(state.record?.address)
    LaunchedEffect(address) {
        snapshotFlow { address.text.toString() }
            .drop(1)
            .collect { text ->
                if (PairingText.looksLikePayload(text)) {
                    address.fill("")
                    actions.paste(text)
                }
                if (text != savedAddress) {
                    confirmedOrigin = null
                    pin = null
                    pinOrigin = null
                }
            }
    }
    LaunchedEffect(password) {
        snapshotFlow { password.text.toString() }
            .collect { text ->
                if (PairingText.looksLikePayload(text)) {
                    password.fill("")
                    actions.paste(text)
                } else if (text.lowercase().startsWith("server password"))
                    password.fill(text.substring("server password".length).trim())
            }
    }
    LaunchedEffect(state.step, loaded) {
        snapshotFlow {
                listOf(
                    name.text.toString(),
                    address.text.toString(),
                    username.text.toString(),
                    directory.text.toString(),
                    password.text.toString(),
                    heldSecret,
                    backend,
                    tailscale,
                    confirmedOrigin,
                    pin,
                    keepSecret,
                )
            }
            .drop(1)
            .collectLatest {
                actions.keepDraft(input())
                actions.changed()
                if (
                    !state.newServer ||
                        state.step != ServerEditorStep.Connect ||
                        address.text.isBlank()
                )
                    return@collectLatest
                delay(900)
                if (backend != BackendKind.OpenCode2 && directory.text.isBlank())
                    return@collectLatest
                // Explain the transport choice before checking; the adapter still enforces it.
                if (requiresHttpReview(backend, address.text.toString(), confirmedOrigin))
                    return@collectLatest
                actions.check(input())
            }
    }
    fun leave() {
        if (state.step == ServerEditorStep.Connect && tailscale && state.newServer)
            actions.step(ServerEditorStep.Tailscale)
        else if (state.step == ServerEditorStep.Tailscale && !state.newServer)
            actions.step(ServerEditorStep.Connect)
        else actions.back(dirty)
    }
    LaunchedEffect(state.step) {
        if (state.step == ServerEditorStep.Tailscale) actions.refreshTailscale()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.step == ServerEditorStep.Tailscale) {
            actions.refreshTailscale()
            if (tailscaleOpened) tailscaleReturned = true
        }
    }
    KitBackGuard(enabled = true, onBack = { leave() })
    val enabled =
        if (state.saving || state.loading)
            KitEnabled.Disabled(stringResource(Res.string.serversWorking))
        else KitEnabled.Enabled
    val connectionEnabled =
        if (!enabled.isEnabled) enabled
        else if (requiresHttpReview(backend, address.text.toString(), confirmedOrigin))
            KitEnabled.Disabled(stringResource(Res.string.serversCleartextRequired))
        else KitEnabled.Enabled
    val discard =
        if (state.discard)
            KitConfirmSpec(
                stringResource(Res.string.editor40DiscardTitle),
                stringResource(Res.string.editor40Discard),
                kind = KitConfirmKind.Discard,
                body = stringResource(Res.string.editor40DiscardBody),
            )
        else null
    val certificate = state.certificate
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    stringResource(
                        if (!state.newServer) Res.string.editor40Edit
                        else if (state.step == ServerEditorStep.Connect)
                            when (backend) {
                                BackendKind.OpenCode2 -> Res.string.firstRunAgentOpenCode
                                BackendKind.Codex -> Res.string.firstRunAgentCodex
                                BackendKind.Paseo -> Res.string.firstRunPaseoTitle
                            }
                        else Res.string.serversAdd
                    ),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = { leave() },
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading || state.checking || state.saving,
        bottom =
            if (state.step == ServerEditorStep.Tailscale)
                ({
                    KitButton(
                        stringResource(Res.string.editor40EnterAddress),
                        { actions.step(ServerEditorStep.Connect) },
                        fullWidth = true,
                    )
                })
            else if (state.step != ServerEditorStep.Connect && state.step != ServerEditorStep.Ready)
                null
            else
                ({
                    KitButton(
                        stringResource(
                            if (state.step == ServerEditorStep.Ready)
                                (if (state.startsConversation) Res.string.upgradeReadyStart
                                else Res.string.editor40Open)
                            else if (state.connectOnSave) Res.string.editor40SaveConnect
                            else Res.string.editor40SaveChanges
                        ),
                        {
                            if (state.step == ServerEditorStep.Ready) actions.openReady()
                            else actions.save(input())
                        },
                        enabled =
                            if (state.step == ServerEditorStep.Ready) enabled
                            else connectionEnabled,
                        working = state.saving,
                        fullWidth = true,
                    )
                }),
    ) {
        KitConfirm(discard, actions::discard, actions::keepEditing)
        KitSheet(
            visible = trustOrigin != null,
            onDismissRequest = { trustOrigin = null },
            title = stringResource(Res.string.serverTrustHttpTitle),
            icon = KitIcons.warning,
            primary =
                KitAction(
                    stringResource(Res.string.serverTrustHttpAccept),
                    {
                        val reviewed = trustOrigin
                        // F: lib/ui/screens/servers/profile_editor_trust.dart:20-24
                        if (reviewed != null && reviewed == editorOrigin(address.text.toString())) {
                            confirmedOrigin = reviewed.toString()
                            actions.changed()
                        }
                        trustOrigin = null
                    },
                    enabled = enabled,
                ),
        ) {
            trustOrigin?.let {
                KitServerTrustBody(it.toString(), httpWarning(it), inset = false)
            }
        }
        KitSheet(
            visible = certificate != null,
            onDismissRequest = actions::dismissCertificate,
            title = stringResource(Res.string.serverTrustCertificateTitle),
            icon = KitIcons.warning,
            primary =
                KitAction(
                    stringResource(Res.string.serverTrustCertificateAccept),
                    {
                        if (certificate != null) {
                            if (
                                editorOrigin(certificate.origin) ==
                                    editorOrigin(address.text.toString()) &&
                                    editorOrigin(certificate.origin) != null
                            ) {
                                pin = certificate.fingerprint
                                pinOrigin = certificate.origin
                                actions.changed()
                            }
                        }
                        actions.dismissCertificate()
                    },
                    enabled = enabled,
                ),
        ) {
            certificate?.let {
                KitServerTrustBody(
                    it.origin,
                    stringResource(Res.string.serverTrustCertificateBody),
                    fingerprint = it.fingerprint,
                    fingerprintLabel = stringResource(Res.string.serverTrustFingerprint),
                    validity =
                        stringResource(
                            Res.string.serverTrustCertificateValidity,
                            it.validFrom,
                            it.validUntil,
                        ),
                    inset = false,
                )
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.newServer) {
                val total =
                    if (tailscale) 5
                    else 4 // F: lib/ui/screens/servers/profile_editor_steps.dart:26
                val connect =
                    if (tailscale) 3
                    else 2 // F: lib/ui/screens/servers/profile_editor_steps.dart:27
                val step =
                    when (state.step) {
                        ServerEditorStep.Kind -> 1
                        ServerEditorStep.Tailscale ->
                            2 // F: lib/ui/screens/servers/profile_editor_steps.dart:31
                        ServerEditorStep.Connect ->
                            if (state.checking || state.saving) connect + 1 else connect
                        ServerEditorStep.Ready -> total
                    }
                val label =
                    stringResource(
                        when {
                            state.step == ServerEditorStep.Kind -> Res.string.addServerStepKind
                            state.step == ServerEditorStep.Tailscale ->
                                Res.string.addServerStepTailscale
                            state.step == ServerEditorStep.Ready -> Res.string.addServerStepReady
                            state.checking || state.saving -> Res.string.addServerStepCheck
                            backend != BackendKind.OpenCode2 -> Res.string.addServerStepAddress
                            else -> Res.string.addServerStepPair
                        }
                    )
                KitProgressView(
                    KitProgress.Staged(
                        step,
                        total,
                        label,
                        stepValue = if (state.step == ServerEditorStep.Ready) 1f else null,
                    ),
                    semanticsLabel = stringResource(Res.string.addServerStepsLabel),
                )
            }
            when (state.step) {
                ServerEditorStep.Kind -> {
                    KitText(
                        stringResource(Res.string.firstRunAgentsSideBySide),
                        role = TypeRole.Secondary,
                    )
                    for (kind in
                        listOf(BackendKind.OpenCode2, BackendKind.Codex, BackendKind.Paseo)) KitRow(
                        title = backendName(kind),
                        leading =
                            KitRowLeading.Glyph(
                                when (kind) {
                                    BackendKind.OpenCode2 -> KitIcons.computer
                                    BackendKind.Codex -> KitIcons.code
                                    BackendKind.Paseo -> KitIcons.agent
                                }
                            ),
                        supporting =
                            stringResource(
                                when (kind) {
                                    BackendKind.OpenCode2 -> Res.string.addServerTypeOpenCodeDetail
                                    BackendKind.Codex -> Res.string.addServerTypeCodexDetail
                                    BackendKind.Paseo -> Res.string.addServerTypePaseoDetail
                                }
                            ),
                        supportingMaxLines =
                            2, // F: lib/ui/screens/servers/profile_editor_steps.dart:372
                        trailing = KitRowTrailing.Chevron,
                        onClick = {
                            backend = kind
                            tailscale = false
                            actions.step(ServerEditorStep.Connect)
                        },
                    )
                    Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                    KitRowGroup(label = stringResource(Res.string.serversAddOtherWays)) {
                        if (state.canTailscale)
                            KitRow(
                                title = stringResource(Res.string.tailscaleTitle),
                                leading = KitRowLeading.Glyph(KitIcons.secureNetwork),
                                supporting = stringResource(Res.string.onboardingPrivateNetwork),
                                supportingMaxLines =
                                    2, // F: lib/ui/screens/servers/profile_editor_steps.dart:372
                                trailing = KitRowTrailing.Chevron,
                                onClick = {
                                    backend = BackendKind.OpenCode2
                                    tailscale = true
                                    actions.step(ServerEditorStep.Tailscale)
                                },
                            )
                        if (state.canPhone)
                            KitRow(
                                title = stringResource(Res.string.settingsV3ThisPhone),
                                leading = KitRowLeading.Glyph(KitIcons.phone),
                                trailing = KitRowTrailing.Chevron,
                                onClick = actions::phone,
                            )
                        KitRow(
                            title = stringResource(Res.string.a2aTitle),
                            leading = KitRowLeading.Glyph(KitIcons.network),
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::agents,
                        )
                    }
                }
                ServerEditorStep.Tailscale -> {
                    KitText(
                        stringResource(Res.string.tailscaleIntro),
                        tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary,
                    )
                    TailscalePhoneContent(
                        appState =
                            when (state.tailscaleApp) {
                                null -> KitStepState.Working
                                EditorTailscaleState.Installed -> KitStepState.Done
                                EditorTailscaleState.Missing -> KitStepState.Waiting
                                EditorTailscaleState.Unavailable -> KitStepState.Failed
                            },
                        appSupporting =
                            stringResource(
                                when (state.tailscaleApp) {
                                    null -> Res.string.tailscaleChecking
                                    EditorTailscaleState.Installed -> Res.string.tailscaleInstalled
                                    EditorTailscaleState.Missing -> Res.string.tailscaleMissing
                                    EditorTailscaleState.Unavailable -> Res.string.tailscaleUnknown
                                }
                            ),
                        canCheck = true,
                        onCheck = actions::refreshTailscale,
                        canOpen = state.tailscaleApp == EditorTailscaleState.Installed,
                        onOpen = {
                            actions.keepDraft(input())
                            actions.changed()
                            tailscaleOpened = true
                            actions.openTailscale()
                        },
                        openFailed = tailscaleOpened && state.failure != null,
                        getApp =
                            if (state.tailscaleApp == EditorTailscaleState.Missing)
                                actions::installTailscale
                            else null,
                    )
                    if (tailscaleReturned)
                        KitNotice(
                            stringResource(Res.string.tailscaleReturned),
                            icon = KitIcons.info,
                        )
                    state.failure?.let { KitErrorNotice(editorFailureWords(it), failure = it) }
                    TailscaleHelpContent()
                }
                ServerEditorStep.Ready -> {
                    EditorLinkMoment(KitServersLinkState.Linked, state, address.text.toString())
                    val readyName =
                        state.record?.name?.ifBlank { null }
                            ?: name.text.toString().ifBlank { suggestedName }
                    KitText(
                        stringResource(Res.string.upgradeReadyTitle, readyName),
                        role = TypeRole.Headline,
                        pageTitle = true,
                    )
                    // Only facts the state carries: the host, and the version and tools of the
                    // check.
                    val facts =
                        listOfNotNull(
                                editorOrigin(address.text.toString())?.host,
                                state.checked
                                    ?.version
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { stringResource(Res.string.upgradeReadyVersion, it) },
                                state.checked
                                    ?.runtimes
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.joinToString(", "),
                            )
                            .joinToString(" · ")
                    if (facts.isNotEmpty())
                        KitText(
                            facts,
                            role = TypeRole.Secondary,
                            tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary,
                        )
                    KitText(
                        stringResource(Res.string.addServerReadyBody),
                        tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary,
                    )
                    state.failure?.let { KitErrorNotice(editorFailureWords(it), failure = it) }
                }
                ServerEditorStep.Connect -> {
                    // OpenCode on a computer leads with the fastest way: scan (or paste) the code
                    // the
                    // computer shows. The run-this command sits below the buttons, not above them.
                    val pairingFirst =
                        state.newServer &&
                            !manualOpen &&
                            backend == BackendKind.OpenCode2 &&
                            !tailscale
                    if (state.newServer && !manualOpen) {
                        EditorLinkMoment(
                            when {
                                state.checking || state.saving -> KitServersLinkState.Linking
                                state.failure != null -> KitServersLinkState.Failed
                                state.checked != null -> KitServersLinkState.Linked
                                else -> KitServersLinkState.Idle
                            },
                            state,
                            address.text.toString(),
                        )
                    }
                    if (backend == BackendKind.OpenCode2 && !tailscale && !manualOpen) {
                        if (pairingFirst) {
                            KitText(
                                stringResource(Res.string.upgradeConnectScanTitle),
                                role = TypeRole.Headline,
                            )
                            KitText(
                                stringResource(
                                    if (state.canScan) Res.string.upgradeConnectScanBody
                                    else Res.string.upgradeConnectPasteBody
                                ),
                                role = TypeRole.Secondary,
                                tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary,
                            )
                        } else
                            KitText(
                                stringResource(
                                    if (state.canScan) Res.string.firstRunPairingNextScan
                                    else Res.string.firstRunPairingNextPaste
                                ),
                                role = TypeRole.Secondary,
                                tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary,
                            )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                        ) {
                            if (state.canScan)
                                KitButton(
                                    stringResource(Res.string.addServerScan),
                                    {
                                        actions.keepDraft(input())
                                        actions.scan()
                                    },
                                    modifier = Modifier.weight(1f),
                                    role = KitButtonRole.Primary,
                                    icon = KitIcons.qrCode,
                                    enabled = enabled,
                                    fullWidth = true,
                                )
                            KitPasteButton(
                                stringResource(
                                    if (state.canScan) Res.string.addServerPaste
                                    else Res.string.e7SetupPastePairing
                                ),
                                actions::paste,
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                            )
                        }
                        state.pairingProblem?.let { KitErrorNotice(pairingProblemText(it)) }
                        // The second way in: look for the computer on this Wi-Fi.
                        if (pairingFirst && state.canFind)
                            FindOnNetworkSection(
                                find = state.find,
                                enabled = enabled.isEnabled,
                                onStart = { findRequest.ask() },
                                onStop = actions::stopFind,
                                onPick = actions::pickFound,
                                onEnterAddress = { manualOpen = true },
                                onOpenSettings = actions::openAccessSettings,
                            )
                    }
                    if (state.newServer && !manualOpen) {
                        val commands: @Composable () -> Unit = {
                            EditorCommand(
                                SetupCommands.startFor(backend),
                                copyLabel,
                                copiedLabel,
                                copy,
                            )
                            KitExpandRow(
                                stringResource(Res.string.editor40OtherCommands),
                                commandsOpen,
                                onExpandedChange = { commandsOpen = it },
                                inset = false,
                            ) {
                                EditorCommand(
                                    when (backend) {
                                        BackendKind.OpenCode2 -> SetupCommands.SERVE
                                        BackendKind.Paseo ->
                                            SetupCommands.PASEO_START_PRIVATE_NETWORK
                                        BackendKind.Codex -> SetupCommands.CODEX_USB
                                    },
                                    copyLabel,
                                    copiedLabel,
                                    copy,
                                )
                                if (backend == BackendKind.Codex)
                                    EditorCommand(
                                        SetupCommands.CODEX_TOKEN,
                                        copyLabel,
                                        copiedLabel,
                                        copy,
                                    )
                            }
                        }
                        if (pairingFirst)
                            KitExpandRow(
                                stringResource(Res.string.upgradeConnectCommandTitle),
                                pairCommandOpen,
                                onExpandedChange = { pairCommandOpen = it },
                                inset = false,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                                    commands()
                                }
                            }
                        else commands()
                    }
                    if (pairingFirst && state.canTailscale)
                        KitRowGroup(label = stringResource(Res.string.upgradeConnectOtherWays)) {
                            KitRow(
                                title = stringResource(Res.string.tailscaleTitle),
                                leading = KitRowLeading.Glyph(KitIcons.secureNetwork),
                                supporting = stringResource(Res.string.upgradeConnectTailscaleBody),
                                supportingMaxLines = 2,
                                trailing = KitRowTrailing.Chevron,
                                onClick = {
                                    actions.keepDraft(input())
                                    tailscale = true
                                    actions.step(ServerEditorStep.Tailscale)
                                },
                            )
                        }
                    if (backend != BackendKind.OpenCode2 || tailscale)
                        state.pairingProblem?.let { KitErrorNotice(pairingProblemText(it)) }
                    val manualFields: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
                            KitField(
                                address,
                                stringResource(
                                    if (backend == BackendKind.OpenCode2)
                                        Res.string.e7SetupServerUrl
                                    else Res.string.connectionServerAddress
                                ),
                                kind = KitFieldKind.Url,
                                placeholder =
                                    if (backend == BackendKind.Paseo)
                                        stringResource(Res.string.paseoAddressHint)
                                    else if (backend == BackendKind.Codex)
                                        stringResource(Res.string.codexAddressHint)
                                    else "https://server.example", // F:
                                // lib/ui/screens/servers/profile_editor_fields.dart:248
                                helper =
                                    if (backend == BackendKind.OpenCode2)
                                        stringResource(
                                            if (tailscale) Res.string.tailscaleAddressDetail
                                            else Res.string.e7SetupHttpsHint
                                        )
                                    else null,
                                enabled = enabled,
                            )
                            KitField(
                                password,
                                stringResource(
                                    if (state.record?.needsSecret == true) {
                                        if (backend == BackendKind.OpenCode2)
                                            Res.string.e7SetupReenterPassword
                                        else Res.string.codexTokenReentry
                                    } else if (backend == BackendKind.OpenCode2)
                                        Res.string.e7SetupServerPassword
                                    else if (backend == BackendKind.Paseo)
                                        Res.string.paseoPasswordLabel
                                    else Res.string.codexTokenLabel
                                ),
                                kind = KitFieldKind.Secret,
                                focusRequester = passwordFocus,
                                helper =
                                    stringResource(
                                        if (backend == BackendKind.Paseo)
                                            Res.string.paseoPasswordHelp
                                        else if (backend == BackendKind.Codex)
                                            Res.string.codexTokenStorageHelp
                                        else if (state.record?.needsSecret == true)
                                            Res.string.e7SetupEmptyPasswordHint
                                        else Res.string.e7SetupPasswordStartupHint
                                    ),
                                enabled = enabled,
                                secretPresence =
                                    if (keepSecret || heldSecret != null) SecretFieldPresence.Saved
                                    else SecretFieldPresence.Editing,
                                onReplaceSecret = {
                                    keepSecret = false
                                    heldSecret = null
                                    password.fill("")
                                    actions.changed()
                                },
                            )
                            val origin = editorOrigin(address.text.toString())
                            if (
                                backend == BackendKind.OpenCode2 &&
                                    origin != null &&
                                    AddressRules.needsCleartextConsent(origin)
                            ) {
                                val pending = editorOrigin(confirmedOrigin.orEmpty()) != origin
                                if (pending)
                                    KitNotice(
                                        message = httpWarning(origin),
                                        icon = KitIcons.warning,
                                        actions =
                                            buildList {
                                                if (
                                                    pending && state.newServer && state.canTailscale
                                                )
                                                    add(
                                                        KitLineAction(
                                                            stringResource(
                                                                Res.string.addServerUseTailscale
                                                            ),
                                                            {
                                                                actions.keepDraft(input())
                                                                tailscale = true
                                                                actions.step(
                                                                    ServerEditorStep.Tailscale
                                                                )
                                                            },
                                                            enabled = enabled.isEnabled,
                                                            disabledReason = enabled.reason,
                                                        )
                                                    )
                                                if (pending)
                                                    add(
                                                        KitLineAction(
                                                            stringResource(
                                                                Res.string.addServerCleartextConfirm
                                                            ),
                                                            { trustOrigin = origin },
                                                            enabled = enabled.isEnabled,
                                                            disabledReason = enabled.reason,
                                                        )
                                                    )
                                            },
                                    )
                                else
                                    KitNotice(
                                        stringResource(Res.string.addServerCleartextConfirmed)
                                    )
                            }
                            if (backend != BackendKind.OpenCode2)
                                KitField(
                                    directory,
                                    stringResource(Res.string.codexProjectFolder),
                                    kind = KitFieldKind.Path,
                                    enabled = enabled,
                                )
                            if (tailscale) {
                                KitText(stringResource(Res.string.editor40TailscaleBody))
                                if (state.canTailscale)
                                    KitButton(
                                        stringResource(Res.string.tailscaleTitle),
                                        {
                                            actions.keepDraft(input())
                                            actions.step(ServerEditorStep.Tailscale)
                                        },
                                        role = KitButtonRole.Tertiary,
                                        enabled = enabled,
                                    )
                            }
                            state.failure?.let { failure ->
                                KitErrorNotice(
                                    editorFailureWords(failure),
                                    failure = failure,
                                    detailsLabel = stringResource(Res.string.kitDetails),
                                    copyLabel = stringResource(Res.string.kitCopyDetails),
                                )
                                if (
                                    (failure as? Failure.Incompatible)?.kind ==
                                        io.github.eslamasabry.oc.core.common.IncompatibleKind
                                            .LegacyOpenCode1
                                )
                                    EditorCommand(
                                        SetupCommands.OPENCODE2_UPGRADE,
                                        copyLabel,
                                        copiedLabel,
                                        copy,
                                    )
                            }
                            if (state.saved && state.failure != null)
                                KitNotice(stringResource(Res.string.editor40SavedFailed))
                            state.checked?.let {
                                KitNotice(
                                    stringResource(
                                        Res.string.editor40Checked,
                                        it.version.orEmpty(),
                                    ),
                                    notes = it.runtimes,
                                )
                            }
                            if (state.record?.needsSecret == true)
                                KitNotice(stringResource(Res.string.rows40Secret))
                            if (backend == BackendKind.OpenCode2) {
                                if (address.text.toString().trim().startsWith("https://")) {
                                    if (pin != null)
                                        KitNotice(stringResource(Res.string.editor40Pinned))
                                    KitButton(
                                        stringResource(Res.string.editor40Inspect),
                                        { actions.inspect(address.text.toString()) },
                                        role = KitButtonRole.Tertiary,
                                        enabled = enabled,
                                        working = state.inspecting,
                                    )
                                }
                            }
                            if (state.slow) KitNotice(stringResource(Res.string.editor40Slow))
                            if (state.checking)
                                KitButton(
                                    stringResource(Res.string.editor40CancelCheck),
                                    actions::cancelCheck,
                                    role = KitButtonRole.Tertiary,
                                )
                            KitButton(
                                stringResource(Res.string.editor40Check),
                                { actions.check(input()) },
                                role = KitButtonRole.Tertiary,
                                enabled = connectionEnabled,
                                working = state.checking,
                            )
                            if (state.failedCheck)
                                KitButton(
                                    stringResource(Res.string.editor40SaveAnyway),
                                    { actions.save(input(), true) },
                                    role = KitButtonRole.Secondary,
                                    enabled = connectionEnabled,
                                )
                        }
                    }
                    if (state.newServer && backend == BackendKind.OpenCode2 && !tailscale) {
                        KitExpandRow(
                            stringResource(Res.string.addServerManual),
                            manualOpen,
                            onExpandedChange = { manualOpen = it },
                            inset = false,
                        ) {
                            manualFields()
                        }
                    } else manualFields()
                    if (backend == BackendKind.OpenCode2) {
                        KitField(
                            name,
                            stringResource(Res.string.connectionDisplayName),
                            placeholder =
                                if (state.newServer) suggestedName
                                else stringResource(Res.string.connectionDisplayNameHint),
                            enabled = enabled,
                        )
                        if (state.newServer && name.text.isBlank() && address.text.isNotBlank())
                            KitText(
                                stringResource(Res.string.upgradeConnectNameLine, suggestedName),
                                role = TypeRole.Secondary,
                                tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary,
                            )
                        KitExpandRow(
                            stringResource(Res.string.editor40More),
                            moreOpen,
                            onExpandedChange = { moreOpen = it },
                            inset = false,
                        ) {
                            if (backend == BackendKind.OpenCode2)
                                KitField(
                                    username,
                                    stringResource(Res.string.serversUsername),
                                    enabled = enabled,
                                )
                        }
                    } else {
                        KitField(
                            name,
                            stringResource(Res.string.connectionDisplayName),
                            placeholder =
                                if (state.newServer) suggestedName
                                else stringResource(Res.string.connectionDisplayNameHint),
                            enabled = enabled,
                        )
                        KitText(
                            stringResource(
                                if (backend == BackendKind.Paseo) Res.string.paseoSetupNotice
                                else Res.string.codexApprovalRecoveryNotice
                            ),
                            role = TypeRole.Secondary,
                            tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The name a new server gets when the person typed none: the first label of its host name
 * (`laptop.tailnet.ts.net` becomes `laptop`). An IP address is never a title, so it falls back to
 * [fallback].
 */
internal fun suggestServerName(address: String, fallback: String): String {
    val host =
        editorOrigin(address.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://"))
            ?.host ?: return fallback
    val bare = host.trim('[', ']')
    val isIp = ':' in bare || bare.all { it.isDigit() || it == '.' }
    return if (isIp || bare.isBlank()) fallback else bare.substringBefore('.')
}

internal fun editorOrigin(address: String): ServerOrigin? =
    (AddressRules.normalize(address) as? Outcome.Ok)?.value

private fun requiresHttpReview(
    backend: BackendKind,
    address: String,
    confirmedOrigin: String?,
): Boolean {
    if (backend != BackendKind.OpenCode2) return false
    val origin = editorOrigin(address) ?: return false
    return AddressRules.needsCleartextConsent(origin) &&
        editorOrigin(confirmedOrigin.orEmpty()) != origin
}

@Composable
private fun httpWarning(origin: ServerOrigin): String =
    stringResource(
        if (AddressRules.isPrivateNetwork(origin.host) || AddressRules.isTailnet(origin.host))
            Res.string.addServerCleartextWarning
        else Res.string.serverTrustHttpPublicBody
    )

/** One copyable setup command (Flutter `KitCodeBlock(copyLabel: handoffCopyCommand)`). */
@Composable
private fun EditorCommand(
    source: String,
    copyLabel: String,
    copiedLabel: String,
    onCopy: (String) -> Unit,
) {
    KitCodeBlock(
        source = source,
        kind = KitCodeKind.Command,
        wrap = true,
        copyLabel = copyLabel,
        copiedLabel = copiedLabel,
        onCopy = onCopy,
    )
}

@Composable
private fun backendName(backend: BackendKind): String =
    stringResource(
        when (backend) {
            BackendKind.OpenCode2 -> Res.string.addServerTypeOpenCode
            BackendKind.Paseo -> Res.string.addServerTypePaseo
            BackendKind.Codex -> Res.string.addServerTypeCodex
        }
    )

@Composable
private fun editorFailureWords(failure: Failure): String =
    stringResource(
        when (failure) {
            is Failure.SignIn -> Res.string.serversSignInFailed
            is Failure.Certificate -> Res.string.serverFailureCertificate
            is Failure.TimedOut -> Res.string.serverFailureTimeout
            is Failure.Network -> Res.string.serverFailureNetwork
            is Failure.Refused ->
                when (failure.reason) {
                    RefusalReason.CleartextNotConfirmed -> Res.string.serversCleartextRequired
                    RefusalReason.StaleScope -> Res.string.editor40Stale
                    RefusalReason.ProfileDeleted -> Res.string.editor40Deleted
                    else -> Res.string.serversInvalidAddress
                }
            is Failure.Incompatible -> Res.string.serverFailureIncompatible
            is Failure.SecretUnavailable -> Res.string.serverFailureSecret
            else -> Res.string.serverFailureOther
        }
    )

/**
 * The scene's caption never falls back to the raw address: an invalid URL might contain a
 * credential.
 */
@Composable
private fun EditorLinkMoment(link: KitServersLinkState, state: ServerEditorUi, address: String) {
    val normalized = address.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")
    val host = (AddressRules.normalize(normalized) as? Outcome.Ok)?.value?.host
    val caption =
        if (host == null) null
        else
            when (link) {
                KitServersLinkState.Linking ->
                    stringResource(
                        if (state.saving) Res.string.addServerConnectingHost
                        else Res.string.addServerCheckingHost,
                        host,
                    )
                KitServersLinkState.Linked ->
                    stringResource(Res.string.addServerConnectedHost, host)
                else -> null
            }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KitServersLink(link)
        Box(Modifier.fillMaxWidth().height(Space.S6.dp), contentAlignment = Alignment.Center) {
            KitText(
                caption.orEmpty(),
                role = TypeRole.Secondary,
                tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
