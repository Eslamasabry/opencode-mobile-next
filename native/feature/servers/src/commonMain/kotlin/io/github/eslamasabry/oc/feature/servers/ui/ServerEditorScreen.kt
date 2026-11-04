package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.feature.servers.pairing.PairingText
import io.github.eslamasabry.oc.feature.servers.pairing.pairingProblemText
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

/** Secrets are held in memory and shown as Saved · Replace; none is a saved-state field. */
@Composable
public fun ServerEditorScreen(state: ServerEditorUi, actions: ServerEditorActions) {
    val restored = remember { actions.takeDraft() }
    val name = rememberTextFieldState(restored?.name.orEmpty())
    val address = rememberTextFieldState(restored?.address.orEmpty())
    val username = rememberTextFieldState(restored?.username ?: "opencode")
    val directory = rememberTextFieldState(restored?.directory.orEmpty())
    val password = remember { TextFieldState() }
    val pairing = remember { TextFieldState() }
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
    var trustQuestion by remember { mutableStateOf(false) }
    var commandsOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
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
        fill.username?.let { username.fill(it) }
        fill.takePassword()?.let {
            heldSecret = it
            password.fill("")
            keepSecret = false
        }
        backend = BackendKind.OpenCode2
        tailscale = false
    }
    fun input(): ServerEditorInput =
        ServerEditorInput(
            state.record?.id,
            name.text.toString(),
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
    LaunchedEffect(address) {
        snapshotFlow { address.text.toString() }
            .drop(1)
            .collect { text ->
                if (PairingText.looksLikePayload(text)) {
                    address.fill("")
                    actions.paste(text)
                }
                if (text != state.record?.address) {
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
    LaunchedEffect(pairing) {
        snapshotFlow { pairing.text.toString() }
            .collect { text ->
                if (text.isNotBlank()) {
                    pairing.fill("")
                    actions.paste(text)
                }
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
                // The adapter refuses unconfirmed HTTP before any network call.
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.step == ServerEditorStep.Tailscale) actions.refreshTailscale()
    }
    KitBackGuard(enabled = true, onBack = { leave() })
    val enabled =
        if (state.saving || state.loading)
            KitEnabled.Disabled(stringResource(Res.string.serversWorking))
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
    KitConfirm(discard, actions::discard, actions::keepEditing)
    val trustSpec =
        if (trustQuestion)
            KitConfirmSpec(
                stringResource(Res.string.editor40HttpTitle),
                stringResource(Res.string.editor40UseAddress),
                body = stringResource(Res.string.editor40HttpBody),
                consequences = listOf(address.text.toString()),
            )
        else null
    KitConfirm(
        trustSpec,
        {
            confirmedOrigin = address.text.toString()
            trustQuestion = false
            actions.changed()
        },
        { trustQuestion = false },
    )
    val certificate = state.certificate
    val certSpec = certificate?.let {
        KitConfirmSpec(
            stringResource(Res.string.editor40CertificateTitle),
            stringResource(Res.string.editor40TrustCertificate),
            body = stringResource(Res.string.editor40CertificateBody),
            consequences =
                listOf(
                    it.origin,
                    it.fingerprint,
                    stringResource(
                        Res.string.editor40CertificateDates,
                        it.validFrom,
                        it.validUntil,
                    ),
                ),
        )
    }
    KitConfirm(
        certSpec,
        {
            if (certificate != null) {
                pin = certificate.fingerprint
                pinOrigin = certificate.origin
                actions.changed()
            }
            actions.dismissCertificate()
        },
        actions::dismissCertificate,
    )
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    stringResource(
                        if (state.newServer) Res.string.serversAdd else Res.string.editor40Edit
                    ),
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = { leave() },
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.loading) KitNotice(stringResource(Res.string.editor40Loading))
            when (state.step) {
                ServerEditorStep.Kind -> {
                    KitText(stringResource(Res.string.editor40Kind))
                    for (kind in BackendKind.entries) KitRow(
                        title = backendName(kind),
                        trailing = KitRowTrailing.Chevron,
                        onClick = {
                            backend = kind
                            tailscale = false
                            actions.step(ServerEditorStep.Connect)
                        },
                    )
                    if (state.canTailscale)
                        KitRow(
                            title = stringResource(Res.string.tailscaleTitle),
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
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::phone,
                        )
                    KitRow(
                        title = stringResource(Res.string.agentsSectionTitle),
                        trailing = KitRowTrailing.Chevron,
                        onClick = actions::agents,
                    )
                }
                ServerEditorStep.Tailscale -> {
                    KitText(stringResource(Res.string.editor40TailscaleBody))
                    KitNotice(
                        stringResource(
                            when (state.tailscaleApp) {
                                EditorTailscaleState.Installed ->
                                    Res.string.editor40TailscaleInstalled
                                EditorTailscaleState.Missing -> Res.string.editor40TailscaleMissing
                                else -> Res.string.editor40TailscaleUnknown
                            }
                        )
                    )
                    if (state.tailscaleApp == EditorTailscaleState.Installed)
                        KitButton(
                            stringResource(Res.string.editor40TailscaleOpen),
                            {
                                actions.keepDraft(input())
                                actions.openTailscale()
                            },
                            role = KitButtonRole.Secondary,
                        )
                    if (state.tailscaleApp == EditorTailscaleState.Missing)
                        KitButton(
                            stringResource(Res.string.editor40TailscaleInstall),
                            actions::installTailscale,
                            role = KitButtonRole.Secondary,
                        )
                    KitButton(
                        stringResource(Res.string.editor40TailscaleRefresh),
                        actions::refreshTailscale,
                        role = KitButtonRole.Tertiary,
                    )
                    state.failure?.let { KitErrorNotice(editorFailureWords(it), failure = it) }
                    KitButton(
                        stringResource(Res.string.editor40EnterAddress),
                        { actions.step(ServerEditorStep.Connect) },
                    )
                }
                ServerEditorStep.Ready -> {
                    KitNotice(stringResource(Res.string.editor40Ready))
                    state.failure?.let { KitErrorNotice(editorFailureWords(it), failure = it) }
                    KitButton(
                        stringResource(Res.string.editor40Open),
                        actions::openReady,
                        enabled = enabled,
                        working = state.saving,
                    )
                }
                ServerEditorStep.Connect -> {
                    KitText(backendName(backend))
                    if (state.newServer) {
                        KitCodeBlock(SetupCommands.startFor(backend), KitCodeKind.Command)
                        KitExpandRow(
                            stringResource(Res.string.editor40OtherCommands),
                            commandsOpen,
                            onExpandedChange = { commandsOpen = it },
                        ) {
                            KitCodeBlock(
                                when (backend) {
                                    BackendKind.OpenCode2 -> SetupCommands.SERVE
                                    BackendKind.Paseo -> SetupCommands.PASEO_START_PRIVATE_NETWORK
                                    BackendKind.Codex -> SetupCommands.CODEX_USB
                                },
                                KitCodeKind.Command,
                            )
                            if (backend == BackendKind.Codex)
                                KitCodeBlock(SetupCommands.CODEX_TOKEN, KitCodeKind.Command)
                        }
                    }
                    if (backend == BackendKind.OpenCode2 && !tailscale) {
                        if (state.canScan)
                            KitButton(
                                stringResource(Res.string.addServerScan),
                                {
                                    actions.keepDraft(input())
                                    actions.scan()
                                },
                                role = KitButtonRole.Secondary,
                                enabled = enabled,
                            )
                        KitField(
                            pairing,
                            stringResource(Res.string.previewQrPairingCodeLabel),
                            kind = KitFieldKind.Secret,
                            enabled = enabled,
                        )
                    }
                    state.pairingProblem?.let { KitErrorNotice(pairingProblemText(it)) }
                    KitField(
                        address,
                        stringResource(Res.string.serversAddress),
                        kind = KitFieldKind.Url,
                        enabled = enabled,
                    )
                    if (backend != BackendKind.OpenCode2)
                        KitField(
                            directory,
                            stringResource(Res.string.editor40Folder),
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
                    if (state.record?.needsSecret == true)
                        KitNotice(stringResource(Res.string.rows40Secret))
                    if (backend == BackendKind.OpenCode2) {
                        KitSwitchRow(
                            stringResource(Res.string.serversCleartext),
                            checked = confirmedOrigin != null,
                            supporting = stringResource(Res.string.serversCleartextHelp),
                            enabled = enabled,
                            onCheckedChange = {
                                if (it) trustQuestion = true
                                else {
                                    confirmedOrigin = null
                                    actions.changed()
                                }
                            },
                        )
                        if (address.text.toString().trim().startsWith("https://")) {
                            if (pin != null) KitNotice(stringResource(Res.string.editor40Pinned))
                            KitButton(
                                stringResource(Res.string.editor40Inspect),
                                { actions.inspect(address.text.toString()) },
                                role = KitButtonRole.Tertiary,
                                enabled = enabled,
                                working = state.inspecting,
                            )
                        }
                    }
                    KitField(
                        password,
                        stringResource(
                            if (backend == BackendKind.OpenCode2) Res.string.serversPassword
                            else Res.string.editor40Token
                        ),
                        kind = KitFieldKind.Secret,
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
                    KitExpandRow(
                        stringResource(Res.string.editor40More),
                        moreOpen,
                        onExpandedChange = { moreOpen = it },
                    ) {
                        KitField(name, stringResource(Res.string.serversName), enabled = enabled)
                        if (backend == BackendKind.OpenCode2)
                            KitField(
                                username,
                                stringResource(Res.string.serversUsername),
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
                            KitCodeBlock(
                                "npm uninstall -g opencode-ai && npm install -g @opencode/cli@2.2.5",
                                KitCodeKind.Command,
                            )
                    }
                    if (state.saved && state.failure != null)
                        KitNotice(stringResource(Res.string.editor40SavedFailed))
                    state.checked?.let {
                        KitNotice(
                            stringResource(Res.string.editor40Checked, it.version.orEmpty()),
                            notes = it.runtimes,
                        )
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
                        enabled = enabled,
                        working = state.checking,
                    )
                    if (state.failedCheck)
                        KitButton(
                            stringResource(Res.string.editor40SaveAnyway),
                            { actions.save(input(), true) },
                            role = KitButtonRole.Secondary,
                            enabled = enabled,
                        )
                    KitButton(
                        stringResource(
                            if (state.connectOnSave) Res.string.editor40SaveConnect
                            else Res.string.editor40SaveChanges
                        ),
                        { actions.save(input()) },
                        enabled = enabled,
                        working = state.saving,
                    )
                }
            }
        }
    }
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
