import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/product_repository.dart' show ProductException;
import '../../api/server_probe.dart';
import '../../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../../feedback/bug_report.dart' show openBugReport;
import '../../domain/profile_monitor.dart' show ProfileAttentionSnapshot;
import '../../l10n/app_localizations.dart';
import '../widgets/setup_ui_messages.dart';
import '../../platform/connection_advice.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/codex_connection_probe.dart';
import '../../state/paseo_connection_probe.dart';
import '../../state/pairing.dart';
import '../../state/phone_host.dart' show PhoneHostKind;
import '../../state/profiles.dart';
import '../../state/queued_prompt_removal.dart';
import '../../state/external_agents.dart';
import '../../state/first_run.dart';
import '../../termux/bridge.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../kit/scenes/servers_link_scene.dart';
import '../kit/scenes/servers_welcome_scene.dart';
import '../setup_commands.dart';
import '../widgets/product_states.dart'
    show productErrorDetails, productErrorText;
import '../widgets/team_host_form.dart';
import '../widgets/local_agent_server_entry.dart';
import '../widgets/phone_server_card.dart';
import '../widgets/queued_prompt_move_sheet.dart';
import '../widgets/termux_migration_entry.dart';
import '../../state/termux_running_server.dart';
import '../widgets/termux_running_server_entry.dart';
import '../widgets/safety_confirms.dart';
import '../../state/local_server_controls.dart';
import 'phone_setup/phone_setup_routes.dart';
import 'phone_setup/phone_setup_welcome_entry.dart';
import 'demo_screen.dart';
import 'guide_screen.dart' show GuideScreen;
import 'agent_account_screen.dart';
import 'pairing_scanner_screen.dart';
import 'tailscale_setup_screen.dart';
import 'this_phone_screen.dart' show openThisPhone;
import '../../state/tailscale_address.dart';
import 'external_agents_screen.dart';

/// What the servers list learns back from the editor's save: whether the
/// profile reached the store, and the product-facing failure to show inline
/// when connecting (or saving) did not work out.
/// A save's outcome: [failure] in plain words when it did not finish, and
/// [details], the redacted technical text for the Details fold under it.
typedef _SubmitOutcome = ({bool saved, String? failure, String? details});

/// Checks a Paseo daemon or a Codex app-server, the two socket backends.
typedef SocketAgentProbe =
    Future<CodexConnectionProbeResult> Function({
      required ServerBackend backend,
      required String baseUrl,
      required String secret,
      required String directory,
    });

Future<CodexConnectionProbeResult> _probeSocketAgent({
  required ServerBackend backend,
  required String baseUrl,
  required String secret,
  required String directory,
}) => backend == ServerBackend.paseo
    ? probePaseoConnection(
        baseUrl: baseUrl,
        password: secret,
        directory: directory,
      )
    : probeCodexConnection(
        baseUrl: baseUrl,
        token: secret,
        directory: directory,
      );

/// The socket-backend counterpart of [serverProbe]: replaceable so a widget
/// test can answer the connect screen's test without opening a socket.
@visibleForTesting
SocketAgentProbe socketAgentProbe = _probeSocketAgent;

/// How long the person must stop typing before the first-run connect screen
/// tests by itself. Long enough that an address is not probed half-typed,
/// short enough that the verdict is there when they look up.
@visibleForTesting
const autoTestPause = Duration(milliseconds: 800);

AppLocalizations _connectionL10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// What another surface asks the Servers screen to do as it opens, passed as
/// the `/servers` route argument.
///
/// The server switcher in the shell lists the same servers, but connecting,
/// credentials, adding and forgetting each have one implementation, here:
/// the runtime-choice detour, the editor that stays open until a connect
/// succeeds, and the inline failure card. The switcher hands the choice over
/// instead of keeping a second, thinner copy of that flow.
class ServersRouteRequest {
  /// Connect [profileID] through the list's connect flow. [detectedRunning]
  /// is the phone card's promise that this runtime is the live one.
  const ServersRouteRequest.connect(
    String this.profileID, {
    this.detectedRunning = false,
  }) : kind = ServersRouteRequestKind.connect,
       backend = null,
       initialUrl = null,
       openCode2 = false;

  /// Open a new server editor, optionally starting with the requested kind
  /// or, for a conversation link that carries one, the validated address
  /// ([initialUrl], never credentials). This is an editable choice, never
  /// permission to connect or save.
  const ServersRouteRequest.add({this.backend, this.initialUrl})
    : kind = ServersRouteRequestKind.add,
      profileID = null,
      detectedRunning = false,
      openCode2 = false;

  /// Ask for the phone server's sign-in, saved as [profileID] when it exists.
  const ServersRouteRequest.enterPhoneCredentials({
    this.profileID,
    required this.openCode2,
  }) : kind = ServersRouteRequestKind.enterPhoneCredentials,
       backend = null,
       initialUrl = null,
       detectedRunning = true;

  /// Confirm and forget the saved server [profileID].
  const ServersRouteRequest.forget(String this.profileID)
    : kind = ServersRouteRequestKind.forget,
      backend = null,
      initialUrl = null,
      detectedRunning = false,
      openCode2 = false;

  final ServersRouteRequestKind kind;
  final ServerBackend? backend;
  final String? initialUrl;
  final String? profileID;
  final bool detectedRunning;
  final bool openCode2;
}

enum ServersRouteRequestKind { connect, add, enterPhoneCredentials, forget }

/// Manage opencode server profiles and connect.
class ServersScreen extends ConsumerStatefulWidget {
  const ServersScreen({super.key});

  @override
  ConsumerState<ServersScreen> createState() => _ServersScreenState();
}

class _ServersScreenState extends ConsumerState<ServersScreen> {
  bool _busy = false;
  bool _handledRouteArgument = false;

  /// A connect attempt from the list that failed, rendered inline above the
  /// rows in the same verdict style the editor uses — never a red snackbar
  /// carrying a raw exception.
  String? _listFailure;
  String? _listFailureDetails;

  /// Bumped after Termux setup returns so the running-server entry re-reads
  /// the phone instead of trusting what it saw before the user left.
  int _termuxRevision = 0;

  /// The first screen's last look found OpenCode or Termux on this phone:
  /// that leads the page, and the welcome steps back.
  bool _termuxFound = false;

  void _observedTermux(TermuxRunningServer server) {
    final found =
        server.state != TermuxRunningServerState.unsupported &&
        server.state != TermuxRunningServerState.absent;
    if (found != _termuxFound && mounted) {
      setState(() => _termuxFound = found);
    }
  }

  @override
  void initState() {
    super.initState();
    // The welcome as the first thing a device shows is what makes it new
    // (see [FirstRun]); the shell reads this after the first connect.
    final store = ref.read(bootstrapProvider).store;
    unawaited(
      FirstRun(
        store.prefs,
      ).observeServers(hasServers: store.profiles.isNotEmpty),
    );
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_handledRouteArgument) return;
    _handledRouteArgument = true;
    // The connection banner's "Update password" action routes here with this
    // argument: open the active profile's editor with the password focused so
    // a rotated serve password is one paste away (never a modal).
    final argument = ModalRoute.of(context)?.settings.arguments;
    if (argument is ServersRouteRequest) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) unawaited(_handleRouteRequest(argument));
      });
      return;
    }
    if (argument == 'edit-active') {
      final store = ref.read(bootstrapProvider).store;
      ServerProfile? active;
      for (final p in store.profiles) {
        if (p.id == store.activeId) active = p;
      }
      final target = active;
      if (target != null) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) unawaited(_edit(existing: target, focusPassword: true));
        });
      }
    }
  }

  Future<void> _handleRouteRequest(ServersRouteRequest request) async {
    ServerProfile? target;
    for (final p in ref.read(bootstrapProvider).store.profiles) {
      if (p.id == request.profileID) target = p;
    }
    switch (request.kind) {
      case ServersRouteRequestKind.add:
        await _edit(
          initialBackend: request.backend,
          initialUrl: request.initialUrl,
        );
      case ServersRouteRequestKind.connect:
        if (target != null) {
          await _connect(target, detectedRunning: request.detectedRunning);
        }
      case ServersRouteRequestKind.enterPhoneCredentials:
        await _enterPhoneCredentials(
          existing: target,
          openCode2: request.openCode2,
        );
      case ServersRouteRequestKind.forget:
        if (target != null) await _delete(target);
    }
  }

  /// A removal that did not finish, said where the list is: the same inline
  /// notice a failed connect uses, never a snackbar (KIT-34, STATE-3).
  void _showFailure(String message, {String? details}) {
    if (!mounted) return;
    setState(() {
      _listFailure = message;
      _listFailureDetails = details;
    });
  }

  /// This phone for the Termux server: its status, versions, tools and log.
  Future<void> _openTermuxSetup() async {
    await openThisPhone(context, kind: PhoneHostKind.termux);
    if (mounted) setState(() => _termuxRevision++);
  }

  /// The one door to running an agent on this phone (phone setup v2,
  /// screen A). Termux and the in-app setup both live behind it, so the
  /// welcome and the list never offer two competing phone paths.
  Future<void> _openPhoneSetup() async {
    await openPhoneSetupStart(context);
    // Termux may have been set up from its "Other ways" row meanwhile.
    if (mounted) setState(() => _termuxRevision++);
  }

  /// The detected running-server entry for [profiles]: it decides on its own
  /// whether anything is shown, so both the welcome and the list embed it
  /// unconditionally and stay platform-gated through it.
  Widget _runningServerEntry(
    List<ServerProfile> profiles,
    ConnectionController connection, {
    bool dividerAbove = false,
    bool lead = false,
  }) {
    // Both servers this app can run on the phone lead the list and are
    // controlled in place: OpenCode first, then the Claude Code daemon. Each
    // entry decides on its own whether it has anything to show.
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        _openCodeServerEntry(
          profiles,
          connection,
          dividerAbove: dividerAbove,
          lead: lead,
        ),
        LocalAgentServerEntry(
          // In a list, a hairline above it whenever a row may precede it.
          dividerAbove:
              dividerAbove || savedManagedPhoneProfile(profiles) != null,
          profiles: profiles,
          busy: _busy,
          revision: _termuxRevision,
          connectedProfileID: connection.api == null
              ? null
              : connection.profile?.id,
          busyConversations: connection.busySessions.length,
          onDisconnect: () async {
            if (!await confirmDisconnectServer(context, connection)) return;
            await connection.disconnect(keepActive: true);
          },
          onForget: _delete,
          onManage: _openTermuxSetup,
          onConnect: (profile) => _connect(profile, detectedRunning: true),
          onOpenSaved: _connect,
        ),
      ],
    );
  }

  Widget _openCodeServerEntry(
    List<ServerProfile> profiles,
    ConnectionController connection, {
    bool dividerAbove = false,
    bool lead = false,
  }) {
    final entry = TermuxRunningServerEntry(
      dividerAbove: dividerAbove,
      lead: lead,
      onObserved: lead ? _observedTermux : null,
      profiles: profiles,
      busy: _busy,
      revision: _termuxRevision,
      connectedProfileID: connection.api == null
          ? null
          : connection.profile?.id,
      busyConversations: connection.busySessions.length,
      actions: () {
        final controls = LocalServerControls(
          store: ref.read(bootstrapProvider).store,
          connection: connection,
        );
        return LocalServerCardActions(
          restart: () async => controls.restart(),
          stop: controls.stop,
        );
      }(),
      onDisconnect: () async {
        if (!await confirmDisconnectServer(context, connection)) return;
        await connection.disconnect(keepActive: true);
      },
      onForget: _delete,
      onManage: _openTermuxSetup,
      onConnect: (profile) => _connect(profile, detectedRunning: true),
      onOpenSaved: _connect,
      onEnterCredentials: (server, existing) => _enterPhoneCredentials(
        existing: existing,
        openCode2: server.flavor == ServerFlavor.v2,
      ),
    );
    // Under the Termux server's row, once: the move to the in-app server.
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        entry,
        TermuxMigrationOffer(profiles: profiles, store: connection.store),
      ],
    );
  }

  /// The phone's own server is running but the app has no usable password for
  /// it. The app wrote that password, so it first restores it from the phone;
  /// only when that is impossible (or the restored one was already refused)
  /// does it ask the person to type one.
  Future<void> _enterPhoneCredentials({
    required ServerProfile? existing,
    required bool openCode2,
  }) async {
    if (_busy) return;
    final recovered = await TermuxBridge.managedServerPassword();
    if (!mounted) return;
    final alreadyRefused =
        existing != null &&
        !existing.requiresPasswordReentry &&
        existing.password == recovered;
    if (recovered != null && !alreadyRefused) {
      final store = ref.read(bootstrapProvider).store;
      final profile =
          existing ??
          ServerProfile(
            id: DateTime.now().microsecondsSinceEpoch.toString(),
            name: lookupAppLocalizations(
              Localizations.localeOf(context),
            ).e7SetupThisDevice,
            baseUrl: TermuxBridge.managedServerUrl,
            flavor: openCode2 ? ServerFlavor.v2 : ServerFlavor.v1,
          );
      profile
        ..username = 'opencode'
        ..password = recovered
        ..requiresPasswordReentry = false;
      await store.upsert(profile);
      if (!mounted) return;
      setState(() {});
      await _connect(profile, detectedRunning: true);
      return;
    }
    await _edit(
      existing: existing,
      connectOnSave: true,
      initialUrl: TermuxBridge.managedServerUrl,
      focusPassword: true,
      openCode2Intent: openCode2,
    );
  }

  /// [detectedRunning] means the caller already knows which managed runtime
  /// is live and chose its profile, so the runtime-choice detour is moot.
  Future<void> _connect(ServerProfile p, {bool detectedRunning = false}) async {
    if (_busy) return;
    if (!detectedRunning &&
        _needsManagedRuntimeChoice(
          p,
          ref.read(bootstrapProvider).store.profiles,
        )) {
      await _openTermuxSetup();
      return;
    }
    if (p.requiresPasswordReentry || p.requiresCodexTokenReentry) {
      await _edit(
        existing: p,
        focusPassword: p.backend == ServerBackend.openCode,
        connectOnSave: detectedRunning,
      );
      return;
    }
    setState(() {
      _busy = true;
      _listFailure = null;
      _listFailureDetails = null;
    });
    final conn = ref.read(connProvider);
    Object? failure;
    try {
      await conn.connect(p);
    } catch (error) {
      failure = error;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    if (!mounted) return;
    if (conn.api != null && failure == null) {
      Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false);
    } else {
      final detail = productErrorText(
        conn.lastError ??
            failure ??
            lookupAppLocalizations(
              Localizations.localeOf(context),
            ).e7SetupConnectionFailed,
      );
      setState(() {
        _listFailure = lookupAppLocalizations(
          Localizations.localeOf(context),
        ).e7SetupConnectFailedDetail(p.name, detail);
      });
    }
  }

  /// Completes with whether the editor saved a server.
  ///
  /// A new server with nothing preset opens at the flow's first step (what
  /// runs there), whether it came from Add server, the welcome's "On my
  /// computer", the server switcher's Add or phone setup: one path.
  Future<bool> _edit({
    ServerProfile? existing,
    ServerBackend? initialBackend,
    bool focusPassword = false,
    String? initialUrl,
    bool openCode2Intent = false,
    bool connectOnSave = false,
  }) async {
    final isNew = existing == null;
    final useTailscale =
        existing != null &&
        ref
                .read(bootstrapProvider)
                .store
                .prefs
                .getBool('oc.tailscale.${existing.id}') ==
            true;
    // The editor stays open until the save (and, for new or active profiles,
    // the connect) has succeeded, so any failure is shown where the fields
    // that fix it are — not as a snackbar over a list the user just left.
    final result = await Navigator.of(context).push<ServerProfile>(
      KitPageRoute<ServerProfile>(
        builder: (_) => _ProfileEditorScreen(
          existing: existing,
          initialBackend: initialBackend,
          reconnectOnSave:
              connectOnSave ||
              existing?.id == ref.read(bootstrapProvider).store.activeId,
          focusPassword: focusPassword,
          tailscale: useTailscale,
          initialUrl: initialUrl,
          openCode2Intent: openCode2Intent,
          // A new server's first step also offers the other ways in, so
          // the list holds no second panel of them (R3).
          onPhoneSetup: isNew && platformCapabilities.supportsTermux
              ? _openPhoneSetup
              : null,
          onExternalAgents: isNew ? _externalAgents : null,
          onSubmit: (profile, {required tailscale}) => _saveAndConnect(
            profile,
            isNew: isNew,
            tailscale: tailscale,
            forceConnect: connectOnSave,
          ),
          secureStorageProbe: () =>
              ref.read(bootstrapProvider).store.secureStorageProblem(),
        ),
      ),
    );
    if (result == null) return false;
    if (!mounted) return true;
    if (isNew || connectOnSave || result.backend == ServerBackend.codex) {
      Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false);
    }
    return true;
  }

  /// Saves [result] and connects profiles whose submit action promises it.
  /// A brand-new profile and every Codex profile promise "Save & connect";
  /// edits of existing non-active OpenCode profiles keep saving only.
  Future<_SubmitOutcome> _saveAndConnect(
    ServerProfile result, {
    required bool isNew,
    bool tailscale = false,
    bool forceConnect = false,
  }) async {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final store = ref.read(bootstrapProvider).store;
    final wasActive = store.activeId == result.id;
    var saved = false;
    setState(() {
      _busy = true;
      _listFailure = null;
      _listFailureDetails = null;
    });
    try {
      await store.upsert(result);
      saved = true;
      if (tailscale &&
          !await store.prefs.setBool('oc.tailscale.${result.id}', true)) {
        throw StateError(copy.e7SetupGuidanceSaveFailed);
      }
      if (wasActive ||
          isNew ||
          forceConnect ||
          result.backend == ServerBackend.codex) {
        final savedProfile = store.profiles.firstWhere(
          (profile) => profile.id == result.id,
        );
        final conn = ref.read(connProvider);
        await conn.connect(savedProfile);
        if (conn.api == null) {
          // The connection's raw failure is the cause (for details); the
          // words say what it means.
          throw ProductException(
            productErrorText(conn.lastError ?? copy.e7SetupDidNotConnect),
            cause: conn.lastError,
          );
        }
      }
      return (saved: true, failure: null, details: null);
    } catch (error) {
      final detail = productErrorText(error);
      return (
        saved: saved,
        failure: saved
            ? copy.e7SetupSavedConnectFailed(result.name, detail)
            : copy.e7SetupSaveFailed(result.name, detail),
        details: productErrorDetails(error),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Names what removal actually deletes, as counted facts (DATA-11: a
  /// removal nobody can restore is confirmed first). Queued prompts and
  /// drafts are the only unsent work at stake, so they are counted; queued
  /// prompts move to Saved prompts unless the person deletes them too
  /// (P7.2); the server itself keeps everything; and removing the server in
  /// use says what the person sees next.
  List<KitConsequence> _removalConsequences(
    AppLocalizations copy,
    ConnectionController connection,
    String id, {
    required QueuedPromptRemovalPlan? queued,
    required bool active,
  }) {
    final drafts = connection.draftCountForProfile(id);
    return [
      if (queued != null && queued.count > 0)
        KitConsequence(
          copy.serversRemoveQueuedKept(queued.count),
          key: const ValueKey('remove-server-queued-kept'),
          mark: KitConsequenceMark.kept,
        ),
      if (queued != null && queued.uncertainCount > 0)
        KitConsequence(
          copy.serversRemoveQueuedUncertain(queued.uncertainCount),
        ),
      if (drafts > 0)
        KitConsequence(
          copy.serversRemoveDrafts(drafts),
          mark: KitConsequenceMark.lost,
        ),
      if (active)
        KitConsequence(
          copy.serversRemoveActiveNext,
          key: const ValueKey('remove-server-active-next'),
        ),
      KitConsequence(
        copy.serversRemoveServerKeeps,
        mark: KitConsequenceMark.kept,
      ),
    ];
  }

  Future<void> _delete(ServerProfile p) async {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final connection = ref.read(connProvider);
    final active =
        ref.read(bootstrapProvider).store.activeId == p.id &&
        connection.api != null;
    // Counted now and acted on exactly: a changed queue stops the removal.
    final QueuedPromptRemovalPlan queued;
    try {
      queued = connection.inspectQueuedPromptsForRemoval(p.id);
    } catch (error) {
      _showFailure(
        copy.serversRemoveQueuedUnreadable(p.name),
        details: productErrorDetails(error),
      );
      return;
    }
    var deleteQueued = false;
    final ok = await showKitConfirm(
      context,
      kind: KitConfirmKind.destructive,
      title: copy.e7SetupRemoveServer(p.name),
      body: copy.serversRemoveBody,
      confirmLabel: copy.capsuleRemove,
      icon: AppIconography.delete,
      consequenceItems: _removalConsequences(
        copy,
        connection,
        p.id,
        queued: queued,
        active: active,
      ),
      alternative: queued.count > 0
          ? KitAction(
              key: ValueKey('remove-server-delete-queued-${p.id}'),
              label: copy.serversRemoveDeleteQueued(queued.count),
              destructive: true,
              onPressed: () => deleteQueued = true,
            )
          : null,
      sheetKey: ValueKey('remove-server-sheet-${p.id}'),
      confirmKey: ValueKey('confirm-remove-server-${p.id}'),
    );
    if (!(ok || deleteQueued) || !mounted) return;
    final store = ref.read(bootstrapProvider).store;
    final wasActive = store.activeId == p.id;
    var removed = false;
    setState(() {
      _busy = true;
      _listFailure = null;
      _listFailureDetails = null;
    });
    try {
      // The cascade verifies every store it writes and reports what refused.
      // A partial deletion is stated, never rounded up to the silent success
      // the list rebuild would otherwise imply.
      final result = await connection.deleteProfileAndLocalData(
        p.id,
        queuedPrompts: queued,
        keepQueuedPrompts: !deleteQueued,
      );
      final partial = result.partialDeletionMessage;
      if (partial != null) {
        _showFailure(partial);
        if (!result.removedProfile) return;
      }
      removed = true;
      if (wasActive) {
        await connection.disconnect(keepActive: true);
      }
    } on QueuedPromptRemovalException catch (error) {
      _showFailure(
        error.unreadable
            ? copy.serversRemoveQueuedUnreadable(p.name)
            : error.changed
            ? copy.serversRemoveQueuedChanged(p.name)
            : copy.serversRemoveQueuedNotKept(p.name),
        details: error.unreadable ? productErrorDetails(error) : null,
      );
    } catch (error) {
      if (removed) {
        _showFailure(
          copy.e7SetupRemovedDisconnectFailed(p.name, productErrorText(error)),
        );
      } else {
        _showFailure(copy.e7SetupRemoveFailed(p.name, productErrorText(error)));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Prompts queued for [p] while it cannot send them: every server but
  /// the connected one. An isolated view never reads other servers.
  int _waitingFor(ServerProfile p, ConnectionController connection) {
    if (connection.isIsolated) return 0;
    if (connection.api != null && connection.profile?.id == p.id) return 0;
    return connection.queuedPromptCountForProfile(p.id);
  }

  /// The connected server [p]'s waiting prompts can move to, by name, or
  /// null when there is none (slice-queue-move).
  String? _moveDestinationName(
    ServerProfile p,
    ConnectionController connection,
    AppLocalizations copy,
  ) {
    final destination = connection.queuedPromptMoveDestination;
    if (destination == null || destination.id == p.id) return null;
    return serverDisplayName(
      destination,
      copy,
      among: connection.store.profiles,
    );
  }

  /// Moves prompts waiting for [p] into a conversation on the connected
  /// server; the sheet asks which, and says what happened.
  Future<void> _moveQueued(ServerProfile p) async {
    setState(() {
      _listFailure = null;
      _listFailureDetails = null;
    });
    await showQueuedPromptMoveSheet(
      context,
      connection: ref.read(connProvider),
      source: p,
      onProblem: (message, {details}) =>
          _showFailure(message, details: details),
    );
  }

  Future<void> _externalAgents() async {
    final bootstrap = ref.read(bootstrapProvider);
    final store = ExternalAgentStore(
      bootstrap.store.prefs,
      bootstrap.store.secure,
    );
    try {
      await pushKitPage<void>(
        context,
        (_) => ExternalAgentsScreen(store: store),
      );
    } finally {
      store.dispose();
    }
  }

  void _demo() =>
      unawaited(pushKitPage<void>(context, (_) => const DemoScreen()));

  @override
  Widget build(BuildContext context) {
    final bootstrap = ref.watch(bootstrapProvider);
    final accountConnection = ref.watch(connProvider);
    final store = bootstrap.store;
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final hasServers = store.profiles.isNotEmpty;
    // A root page (§1.18): the product mark and name, and About. Which
    // server needs the person is said on its own row (R1).
    //
    // With no server connected this is the whole app (no shell, so no
    // Settings): Report a bug and the Setup guide, which otherwise live in
    // Settings, sit in its menu. Opened from Settings, Settings has them.
    final isRoot = !(ModalRoute.of(context)?.canPop ?? false);
    final topBar = KitTopBar(
      title: copy.openCodeConnectionLabel,
      brand: true,
      actions: [
        KitAction(
          key: const ValueKey('servers-about'),
          label: copy.e7SetupAboutNotices,
          icon: AppIconography.info,
          onPressed: () => Navigator.pushNamed(context, '/about'),
        ),
      ],
      menuKey: const ValueKey('servers-menu'),
      menu: [
        if (isRoot) ...[
          KitMenuItem(
            key: const ValueKey('servers-report-bug'),
            label: copy.e7LibraryReportABug,
            icon: AppIconography.bug,
            onSelected: () => unawaited(openBugReport(context)),
          ),
          KitMenuItem(
            key: const ValueKey('servers-setup-guide'),
            label: copy.onboardingSetupGuide,
            icon: AppIconography.guide,
            onSelected: () => unawaited(
              pushKitPage<void>(
                context,
                (_) => const GuideScreen(embedded: false),
              ),
            ),
          ),
        ],
      ],
    );
    if (!hasServers) {
      return KitScreen(
        topBar: topBar,
        width: KitScreenWidth.reading,
        body: _WelcomeView(
          busy: _busy,
          found: _termuxFound,
          runningServer: _runningServerEntry(
            store.profiles,
            accountConnection,
            lead: true,
          ),
          phoneSetup: PhoneSetupWelcomeEntry(revision: _termuxRevision),
          onComputer: () => unawaited(_edit()),
          onPhone: _openPhoneSetup,
          onDemo: _demo,
        ),
      );
    }
    final activeId = store.activeId;
    final phoneServer = phoneServerProfile(store.profiles, activeId);
    final saved = [
      for (final p in store.profiles)
        if (!looksLikeInAppServer(p) && !shownAsPhoneRow(p)) p,
    ];
    final tokens = KitTokens.of(context);
    // The other servers' words come from the one attention source, which an
    // isolated profile never reads.
    final monitor = accountConnection.isIsolated
        ? null
        : accountConnection.profileMonitor;
    return KitScreen(
      topBar: topBar,
      width: KitScreenWidth.list,
      // One bar for a connect, save or removal in flight (standard §4).
      loading: _busy,
      loadingLabel: copy.e7SetupServerOperation,
      // Adding a server is what this screen offers beyond its rows:
      // the one primary, pinned below the list (§1, §2).
      // The demo is the welcome's "Just show me"; with servers saved it
      // would be a second, lesser way in (R3).
      bottom: KitActionBlock(
        primary: KitAction(
          key: const ValueKey('servers-add'),
          label: copy.e7SetupAddServer,
          icon: AppIconography.add,
          onPressed: _busy ? null : () => _edit(),
        ),
      ),
      body: ListView(
        padding: EdgeInsetsDirectional.only(
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          // A saved password or token this phone can no longer read is
          // said once, by the connection status line above (with Enter the
          // password) and by the server's row: no notice here repeats it.
          // A connect or a removal that failed unfolds over the rows and
          // folds away when dismissed or retried (design standard §10).
          KitReveal(
            key: const ValueKey('server-connect-failure-slot'),
            child: switch (_listFailure) {
              final failure? => _Rails(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    KitNotice(
                      key: const ValueKey('server-connect-failure'),
                      tone: AppStatusTone.failure,
                      message: failure,
                      onDismiss: () => setState(() {
                        _listFailure = null;
                        _listFailureDetails = null;
                      }),
                    ),
                    if (_listFailureDetails != null)
                      KitDetailsFold(text: _listFailureDetails),
                  ],
                ),
              ),
              null => null,
            },
          ),
          SizedBox(height: tokens.space2),
          // Every server in one list (R1): the rows ordered by urgency,
          // each saying first what it needs ("Needs you"), what runs there
          // or that its password must be entered again, then the phone's own
          // servers, which decide on their own whether they show. OpenCode
          // inside this app ("This phone") is one of the rows, ranked like
          // the others and first among equals; its saved entries are how
          // the app reaches it, so they are not listed again. A server
          // added or forgotten while the list is open unfolds in or folds
          // away where it was (design standard §10).
          ListenableBuilder(
            listenable: Listenable.merge([accountConnection, ?monitor]),
            builder: (context, _) {
              final ordered = _byUrgency([
                ?phoneServer,
                ...saved,
              ], accountConnection);
              return KitRowGroup(
                key: const ValueKey('servers-list'),
                children: [
                  KitAnimatedRows(
                    key: const ValueKey('saved-server-rows'),
                    children: [
                      for (final (i, p) in ordered.indexed)
                        KeyedSubtree(
                          key: ValueKey('saved-server-${p.id}'),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              if (i > 0)
                                const KitDivider(inset: KitDividerInset.text),
                              if (p == phoneServer)
                                PhoneServerCard.row(
                                  key: ValueKey('phone-server-card-${p.id}'),
                                  connection: accountConnection,
                                  profile: p,
                                  connected:
                                      accountConnection.api != null &&
                                      accountConnection.profile?.id == p.id,
                                  onOpen: _busy ? null : () => _connect(p),
                                  onDisconnect: () async {
                                    if (!await confirmDisconnectServer(
                                      context,
                                      accountConnection,
                                    )) {
                                      return;
                                    }
                                    await accountConnection.disconnect(
                                      keepActive: true,
                                    );
                                  },
                                  onRemoved: () {
                                    if (mounted) setState(() {});
                                  },
                                )
                              else
                                _ServerRow(
                                  profile: p,
                                  connected:
                                      accountConnection.api != null &&
                                      accountConnection.profile?.id == p.id,
                                  snapshot: _snapshotFor(p, accountConnection),
                                  working:
                                      accountConnection.api != null &&
                                          accountConnection.profile?.id == p.id
                                      ? accountConnection.busySessions.length
                                      : null,
                                  busy: _busy,
                                  showAccount:
                                      p.id == activeId &&
                                      accountConnection.isConnected &&
                                      accountConnection
                                          .capabilities
                                          .agentAccount,
                                  queued: _waitingFor(p, accountConnection),
                                  moveDestination: _moveDestinationName(
                                    p,
                                    accountConnection,
                                    copy,
                                  ),
                                  onMoveQueued: () => _moveQueued(p),
                                  onConnect: () => _connect(p),
                                  onEdit: () => _edit(existing: p),
                                  onRemove: () => _delete(p),
                                  onAccount: () => pushKitPage<void>(
                                    context,
                                    (_) => AgentAccountScreen(
                                      connection: accountConnection,
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                    ],
                  ),
                  // The phone's own servers close the list, a row each.
                  _runningServerEntry(
                    store.profiles,
                    accountConnection,
                    dividerAbove: ordered.isNotEmpty,
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  /// The monitor's current words about [profile], or null when it has none
  /// (isolated, unreadable or stale): a row then says nothing rather than
  /// something old.
  ProfileAttentionSnapshot? _snapshotFor(
    ServerProfile profile,
    ConnectionController connection,
  ) {
    if (connection.isIsolated || !connection.isProfileReadable(profile.id)) {
      return null;
    }
    final snapshot = connection.profileMonitor.snapshotFor(profile.id);
    return snapshot.isCurrent ? snapshot : null;
  }

  /// [profiles] most urgent first (R1): what waits on the person, then
  /// what is working, then the rest in the order they were saved.
  List<ServerProfile> _byUrgency(
    List<ServerProfile> profiles,
    ConnectionController connection,
  ) {
    int rank(ServerProfile profile) {
      final snapshot = _snapshotFor(profile, connection);
      final connected =
          connection.api != null && connection.profile?.id == profile.id;
      if ((snapshot?.requests.length ?? 0) > 0 ||
          (connected &&
              (connection.awaitingPermissions.isNotEmpty ||
                  connection.questions.isNotEmpty))) {
        return 0;
      }
      if ((snapshot?.runningCount ?? 0) > 0 ||
          (connected && connection.busySessions.isNotEmpty)) {
        return 1;
      }
      return 2;
    }

    final indexed = [for (final (i, p) in profiles.indexed) (i, p, rank(p))];
    indexed.sort((a, b) {
      final byRank = a.$3.compareTo(b.$3);
      return byRank != 0 ? byRank : a.$1.compareTo(b.$1);
    });
    return [for (final entry in indexed) entry.$2];
  }
}

/// The page's side rails for a part that does not pad itself (the kit's
/// rows and groups do), with a little air above and below.
class _Rails extends StatelessWidget {
  const _Rails({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.symmetric(
        horizontal: tokens.gutter,
        vertical: tokens.space1,
      ),
      child: child,
    );
  }
}

/// One saved server as a kit row (design standard §6): its kind as the
/// icon, the name, then what it runs and its address, with a credential that
/// must be re-entered said first. The server the app is connected to
/// carries the current mark and "Connected" leading its line. Tapping
/// connects; the rest is on long-press or right-click (KIT-28), destructive
/// last.
class _ServerRow extends StatelessWidget {
  const _ServerRow({
    required this.profile,
    required this.connected,
    required this.snapshot,
    required this.working,
    required this.busy,
    required this.showAccount,
    required this.queued,
    required this.moveDestination,
    required this.onMoveQueued,
    required this.onConnect,
    required this.onEdit,
    required this.onRemove,
    required this.onAccount,
  });

  final ServerProfile profile;
  final bool connected;

  /// The monitor's current words about this server; null says nothing.
  final ProfileAttentionSnapshot? snapshot;

  /// Conversations running on the connected server; null for the others,
  /// whose count comes from [snapshot].
  final int? working;
  final bool busy;
  final bool showAccount;

  /// Prompts queued for this server, waiting until it can be reached.
  final int queued;

  /// The connected server they can move to, by name; null: none.
  final String? moveDestination;
  final VoidCallback onMoveQueued;
  final VoidCallback onConnect;
  final VoidCallback onEdit;
  final VoidCallback onRemove;
  final VoidCallback onAccount;

  /// Where the server is, which the row itself no longer says: its full
  /// address and, for Codex and Paseo, the project folder.
  Future<void> _showDetails(BuildContext context, AppLocalizations copy) =>
      showKitTechnicalDetails(
        context,
        title: copy.serverRowDetailsTitle(profile.name),
        text: '',
        sheetKey: ValueKey('server-details-sheet-${profile.id}'),
        values: [
          KitTechnicalValue(copy.connectionServerAddress, profile.baseUrl),
          if (profile.usesAgentSocket && profile.codexDirectory.isNotEmpty)
            KitTechnicalValue(copy.codexProjectFolder, profile.codexDirectory),
        ],
      );

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final p = profile;
    // What it is, never where: the address and the folder are in the
    // menu's Details.
    final kind = switch (p.backend) {
      ServerBackend.paseo => copy.addServerTypePaseo,
      ServerBackend.codex => copy.addServerTypeCodex,
      ServerBackend.openCode => _knownOpenCodeGeneration(p),
    };
    final reentry = p.requiresPasswordReentry
        ? copy.e7SetupPasswordRequired
        : p.requiresCodexTokenReentry
        ? copy.e7SetupTokenRequired
        : null;
    final waiting = connected ? 0 : (snapshot?.requests.length ?? 0);
    final running = working ?? snapshot?.runningCount ?? 0;
    final wordStyle = KitText.styleOf(
      context,
      KitTextRole.label,
      tone: KitTextTone.primary,
    );
    // The state first, in words (R1, the switcher's words): "Needs you",
    // "2 working", a credential to enter again, then what it is.
    return KitRow(
      key: ValueKey('server-row-${p.id}'),
      leading: waiting > 0
          ? KitNeedsYou.mark()
          : KitRowIcon(
              isPhoneOwnServer(p)
                  ? AppIconography.phone
                  : AppIconography.server,
              current: connected,
            ),
      title: p.name,
      supporting: TextSpan(
        children: [
          if (connected) kitCurrentSpan(context, copy.serverRowConnected),
          if (waiting > 0) KitNeedsYou.span(context, count: waiting),
          if (running > 0)
            TextSpan(
              text: '${copy.otherServerWorking(running)} · ',
              style: wordStyle,
            ),
          // LOOK-5 interim: a credential to re-enter is said in words, in
          // the strong label weight, never in the danger colour.
          if (reentry != null) TextSpan(text: '$reentry · ', style: wordStyle),
          if (queued > 0)
            TextSpan(
              text: '${copy.serverRowQueuedWaiting(queued)} · ',
              style: wordStyle,
            ),
          TextSpan(text: kind),
        ],
      ),
      supportingMaxLines: 2,
      selected: connected,
      enabled: !busy,
      disabledReason: busy ? copy.e7SetupServerOperation : null,
      onTap: onConnect,
      menuLabel: p.name,
      menu: [
        if (showAccount)
          KitMenuItem(label: copy.agentAccountTitle, onSelected: onAccount),
        KitMenuItem(label: copy.e7SetupConnect, onSelected: onConnect),
        if (queued > 0 && moveDestination != null)
          KitMenuItem(
            key: ValueKey('server-move-queued-${p.id}'),
            label: copy.serverRowMoveQueued(queued, moveDestination!),
            onSelected: onMoveQueued,
          ),
        KitMenuItem(label: copy.e7SetupEdit, onSelected: onEdit),
        KitMenuItem(
          key: ValueKey('server-details-${p.id}'),
          label: copy.kitDetails,
          onSelected: () => unawaited(_showDetails(context, copy)),
        ),
        // Destructive: last, confirmed by the sheet it opens.
        KitMenuItem(
          label: copy.capsuleRemove,
          destructive: true,
          onSelected: onRemove,
        ),
      ],
    );
  }
}

/// First run asks the only real fork, one question with plain answers (UX
/// plan 5.6 step 1). Product names, private networks and the guide are met
/// later, at the step where each one matters.
class _WelcomeView extends StatelessWidget {
  final bool busy;

  /// The detected on-device server, above the question. It renders nothing
  /// unless a running server was actually observed: a live thing the app
  /// found outranks every generic choice.
  final Widget runningServer;

  /// A phone setup that was started and not finished (or finished with
  /// nothing saved). Like [runningServer] it renders nothing when there is
  /// no such job, and it outranks the generic question when there is.
  final Widget phoneSetup;
  final VoidCallback onComputer;
  final VoidCallback onPhone;
  final VoidCallback onDemo;

  /// OpenCode or Termux was found on this phone: [runningServer] leads the
  /// page, the in-app server is the fresh start after it, and the other
  /// ways follow in a compact list. No welcome hero.
  final bool found;

  const _WelcomeView({
    required this.busy,
    this.found = false,
    required this.runningServer,
    required this.phoneSetup,
    required this.onComputer,
    required this.onPhone,
    required this.onDemo,
  });

  @override
  Widget build(BuildContext context) {
    final copy = _connectionL10n(context);
    final tokens = KitTokens.of(context);
    final busyReason = busy ? copy.e7SetupServerOperation : null;
    final largeText = MediaQuery.textScalerOf(context).scale(1) >= 1.3;
    Widget choice({
      required String key,
      required IconData icon,
      required String title,
      required String detail,
      required VoidCallback onTap,
    }) => KitRow(
      key: ValueKey(key),
      leading: KitRow.icon(context, icon),
      title: title,
      titleMaxLines: 2,
      supporting: TextSpan(text: detail),
      supportingMaxLines: 3,
      trailing: const KitChevron(),
      enabled: !busy,
      disabledReason: busyReason,
      onTap: onTap,
    );
    // Keyed, so the found server keeps its state while the page around it
    // changes shape.
    final lead = KeyedSubtree(
      key: const ValueKey('welcome-termux'),
      child: runningServer,
    );
    final computer = choice(
      key: 'welcome-choice-computer',
      icon: AppIconography.server,
      title: copy.firstRunOnComputer,
      detail: copy.firstRunOnComputerDetail,
      onTap: onComputer,
    );
    final demo = choice(
      key: 'welcome-choice-demo',
      icon: AppIconography.playCircle,
      title: copy.firstRunJustShowMe,
      detail: copy.onboardingDemoNote,
      onTap: onDemo,
    );
    if (found) {
      return ListView(
        key: const ValueKey('first-run-welcome'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space6,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          lead,
          SizedBox(height: tokens.sectionGap),
          if (platformCapabilities.supportsTermux) phoneSetup,
          KitRowGroup(
            key: const ValueKey('welcome-in-app-instead'),
            children: [
              choice(
                key: 'welcome-choice-in-app',
                icon: AppIconography.phone,
                title: copy.termuxInAppInstead,
                detail: copy.termuxInAppInsteadDetail,
                onTap: onPhone,
              ),
            ],
          ),
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            key: const ValueKey('welcome-other-ways'),
            label: copy.phoneSetupStartOtherWays,
            children: [computer, demo],
          ),
        ],
      );
    }
    return ListView(
      key: const ValueKey('first-run-welcome'),
      padding: EdgeInsetsDirectional.only(
        top: tokens.space6,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        // The hero (design standard §10): this phone and the computer the
        // agent runs on. Drawn in once; the welcome is a resting screen, so
        // it never loops. From 1.3x text it steps aside: the picture says
        // nothing the words don't, and the choices are what the person came
        // for (emulator QA B3: at 2.0 they started below the fold).
        if (!largeText) ...[
          const _Rails(
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              child: KitIllustration(
                key: ValueKey('servers-welcome-hero'),
                scene: ServersWelcomeScene(),
              ),
            ),
          ),
          SizedBox(height: tokens.space5),
        ],
        _Rails(
          child: Semantics(
            header: true,
            child: KitText(
              copy.onboardingValueTitle,
              role: KitTextRole.largeTitle,
            ),
          ),
        ),
        SizedBox(height: tokens.space2),
        _Rails(
          child: KitText(copy.onboardingValueBody, tone: KitTextTone.secondary),
        ),
        SizedBox(height: tokens.space6),
        if (platformCapabilities.supportsTermux) phoneSetup,
        lead,
        _Rails(
          child: Semantics(
            header: true,
            child: KitText(
              copy.firstRunWhereQuestion,
              key: const ValueKey('welcome-question'),
              role: KitTextRole.headline,
            ),
          ),
        ),
        SizedBox(height: tokens.space2),
        KitRowGroup(
          children: [
            computer,
            if (platformCapabilities.supportsTermux)
              choice(
                key: 'welcome-choice-phone',
                icon: AppIconography.phone,
                title: copy.onboardingTermuxSetup,
                detail: copy.firstRunOnPhoneDetail,
                onTap: onPhone,
              ),
            demo,
          ],
        ),
      ],
    );
  }
}

/// The phone has one managed listener. Choosing between retained generation
/// profiles is a runtime decision, not permission to redetect/rewrite either.
bool _needsManagedRuntimeChoice(
  ServerProfile profile,
  List<ServerProfile> profiles,
) {
  if (!platformCapabilities.supportsTermux ||
      profile.backend != ServerBackend.openCode ||
      !TermuxBridge.managesServerUrl(profile.baseUrl) ||
      _knownOpenCodeFlavor(profile) == null) {
    return false;
  }
  return profiles.any(
    (other) =>
        other.id != profile.id &&
        other.backend == ServerBackend.openCode &&
        TermuxBridge.managesServerUrl(other.baseUrl) &&
        _knownOpenCodeFlavor(other) != null &&
        _knownOpenCodeFlavor(other) != _knownOpenCodeFlavor(profile),
  );
}

ServerFlavor? _knownOpenCodeFlavor(ServerProfile profile) {
  if (profile.flavor == ServerFlavor.v2) return ServerFlavor.v2;
  if (profile.flavor == ServerFlavor.v1 &&
      profile.serverVersion?.trim().isNotEmpty == true) {
    return ServerFlavor.v1;
  }
  return null;
}

/// Legacy profiles default to v1 without a probe. Only the cached version
/// proves that this default was confirmed. v2 never comes from that default.
String _knownOpenCodeGeneration(ServerProfile profile) =>
    switch (_knownOpenCodeFlavor(profile)) {
      ServerFlavor.v2 => 'OpenCode 2',
      ServerFlavor.v1 => 'OpenCode 1',
      _ => 'OpenCode',
    };

/// A phone feature must stay discoverable when the current server is remote.
/// One entry for every way of running an agent on this phone: it opens phone
/// setup (screen A), where the in-app setup leads and Termux is one of the
/// "Other ways".
class _PhoneSetupEntry extends StatelessWidget {
  const _PhoneSetupEntry({super.key, required this.onTap});
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => KitRow(
    leading: KitRow.icon(context, AppIconography.phone),
    title: _connectionL10n(context).onboardingTermuxSetup,
    supporting: TextSpan(
      text: _connectionL10n(context).phoneSetupStartEntryDetail,
    ),
    supportingMaxLines: 2,
    trailing: const KitChevron(),
    enabled: onTap != null,
    disabledReason: onTap == null
        ? _connectionL10n(context).e7SetupServerOperation
        : null,
    onTap: onTap,
  );
}

class _ProfileEditorScreen extends StatefulWidget {
  final ServerProfile? existing;
  final ServerBackend? initialBackend;
  final bool tailscale;
  final bool reconnectOnSave;
  final bool openCode2Intent;
  final String? initialUrl;

  /// The other ways in, offered on a new server's first step (null hides
  /// each): the editor closes, then the way opens. Tailscale is not one of
  /// them: it is a step of this flow.
  final VoidCallback? onPhoneSetup;
  final VoidCallback? onExternalAgents;

  /// Focus the password field on open — the path taken from the connection
  /// banner after a mid-session 401 (the serve password rotated).
  final bool focusPassword;

  /// Saves (and where promised, connects) the profile; [tailscale] says it
  /// was reached through the Tailscale step. The editor finishes only when
  /// this reports no failure; otherwise the failure is rendered inline and
  /// the fields stay editable.
  final Future<_SubmitOutcome> Function(
    ServerProfile profile, {
    required bool tailscale,
  })
  onSubmit;

  /// Resolves to a sentence when the device cannot keep a password (a Linux
  /// desktop without a keyring), shown above the form before the user types
  /// one that would be lost on save. Null skips the probe.
  final Future<String?> Function()? secureStorageProbe;
  const _ProfileEditorScreen({
    this.existing,
    this.initialBackend,
    this.tailscale = false,
    this.reconnectOnSave = false,
    this.openCode2Intent = false,
    this.initialUrl,
    this.onPhoneSetup,
    this.onExternalAgents,
    this.focusPassword = false,
    required this.onSubmit,
    this.secureStorageProbe,
  });

  @override
  State<_ProfileEditorScreen> createState() => _ProfileEditorScreenState();
}

class _ProfileEditorScreenState extends State<_ProfileEditorScreen> {
  /// "More options" starts open only when something in it was already set:
  /// a username other than the default, or an AI Team host.
  late final bool _moreOptionsOpen =
      (widget.existing?.username.isNotEmpty == true &&
          widget.existing?.username != 'opencode') ||
      widget.existing?.orchestration != null;

  late ServerBackend _backend =
      widget.existing?.backend ??
      widget.initialBackend ??
      ServerBackend.openCode;

  /// A new server with nothing preset is added in steps (P3.9): what runs
  /// there, then Tailscale when that is the way, then the address or the
  /// pairing code, the check, and a ready moment. Everything else (editing,
  /// a password to re-enter, the phone's own server) is one form.
  late final bool _stepped =
      widget.existing == null &&
      !widget.tailscale &&
      widget.initialUrl == null &&
      !widget.focusPassword;

  // An explicit entry point already answered the first question. Keep the
  // stepped flow so Back can change that answer without saving anything.
  late _AddStep _step = _stepped && widget.initialBackend == null
      ? _AddStep.kind
      : _AddStep.connect;

  /// The server is reached through Tailscale: the address must be a
  /// tailnet one, and the save remembers the way for the next edit.
  late bool _tailscale = widget.tailscale;

  /// A check or a pairing has run for [slowCheckAfter]: Cancel is offered.
  bool _slowCheck = false;
  Timer? _slowTimer;

  /// What the ready step opens: the profile as saved and connected.
  ServerProfile? _readyProfile;
  late final TextEditingController _name = TextEditingController(
    text: widget.existing?.name ?? '',
  );
  // New profiles start empty: the normalizer on Test/Save adds the scheme,
  // and a pre-seeded 'https://' fought typed bare hosts.
  late final TextEditingController _url = TextEditingController(
    text: widget.existing?.baseUrl ?? widget.initialUrl ?? '',
  );
  late final TextEditingController _user = TextEditingController(
    text: widget.existing?.username ?? '',
  );

  /// Always empty when the field mounts (SEC-3): a stored password stays
  /// in [_heldPassword] and the field says "Saved · Replace".
  final TextEditingController _pass = TextEditingController();
  late final TextEditingController _codexDirectory = TextEditingController(
    text: widget.existing?.codexDirectory ?? '',
  );

  /// Always empty when the field mounts, like [_pass]; see [_heldToken].
  final TextEditingController _codexToken = TextEditingController();

  /// The password this editor holds without showing it: the saved one, or
  /// the one a pairing code brought. A stored secret is never put back into
  /// a field (SEC-3, KIT-40): while this is set the field says "Saved ·
  /// Replace" and a save keeps it; Replace (or a check that found it
  /// refused) drops it for whatever is typed.
  late String? _heldPassword = _held(
    widget.existing?.password,
    reentry: widget.existing?.requiresPasswordReentry ?? false,
  );

  /// The Codex token or Paseo password held the same way.
  late String? _heldToken = _held(
    widget.existing?.codexToken,
    reentry: widget.existing?.requiresCodexTokenReentry ?? false,
  );

  /// A stored secret is held unless it must be re-entered or the person
  /// came to paste a new one (the connection banner's "Update password").
  String? _held(String? stored, {required bool reentry}) =>
      stored == null || stored.isEmpty || reentry || widget.focusPassword
      ? null
      : stored;

  /// The password a check or a save uses.
  String get _password => _heldPassword ?? _pass.text;

  /// The token a check or a save uses.
  String get _token => _heldToken ?? _codexToken.text;
  final _urlFocus = FocusNode();
  final _nameFocus = FocusNode();
  final _userFocus = FocusNode();
  final _passFocus = FocusNode();
  final _codexDirectoryFocus = FocusNode();
  final _codexTokenFocus = FocusNode();
  String? _error;
  bool _closing = false;
  bool _testing = false;

  /// True while [_ProfileEditorScreen.onSubmit] runs.
  bool _submitting = false;

  /// Why the last save or connect did not finish, in product copy.
  String? _submitFailure;

  /// The failed save's technical text, folded under its verdict.
  String? _submitDetails;

  /// The keyring problem [_ProfileEditorScreen.secureStorageProbe] found.
  String? _secureStorageNotice;

  /// The profile as last written to the store from this editor, so a save
  /// that stored but could not connect no longer counts as unsaved edits.
  ServerProfile? _savedProfile;
  ServerProbeResult? _testResult;
  CodexConnectionProbeResult? _codexTestResult;
  int _urlLength = 0;
  int _probeGeneration = 0;

  /// The pause before the first-run test runs by itself; see [_autoTests].
  Timer? _autoTestTimer;

  /// True while a pairing payload's addresses are being probed.
  bool _pairing = false;

  /// The AI Team host chosen in this editor (TEAM-106); the existing
  /// profile's config until "Add manually" replaces it.
  late OrchestrationConfig? _orchestration = widget.existing?.orchestration;

  /// Which address pairing settled on, as a sentence. Never contains the
  /// password.
  String? _pairingNotice;

  /// Why pairing could not finish — including the per-address verdicts, which
  /// are the only thing that tells the user whether to bridge a port or to
  /// put the server behind TLS.
  String? _pairingFailure;

  /// The head of the form: the link drawing and a slow check's offer to
  /// stop. A check or a save scrolls back to it as it starts.
  final _statusKey = GlobalKey();

  /// The verdicts (the check's, a failed save's), under the address field
  /// they are about; an answer scrolls them into view with that field.
  final _verdictKey = GlobalKey();

  /// The address field, with its label: the verdict is revealed under it.
  final _addressKey = GlobalKey();

  /// Add server's step line, at the head of the form: a check scrolls back
  /// to it, so the step it is on stays in view.
  final _stepLineKey = GlobalKey();

  /// "Enter the address instead" was opened by the editor itself (a check
  /// that needs a field, an empty address on save). Bumping [_manualFold]
  /// rebuilds the fold open.
  bool _manualForcedOpen = false;
  int _manualFold = 0;

  /// The last failed check came from Save & connect, so its verdict offers
  /// "Save anyway".
  bool _verdictFromSave = false;

  /// The plain-HTTP origin the person confirmed ("Use it anyway"). It only
  /// stands for that exact address: another one asks again. A saved server
  /// starts with the confirmation it was saved with.
  late String? _cleartextConfirmed = widget.existing?.cleartextConfirmedOrigin;

  /// True when [url] is plain HTTP to a private network address the person
  /// has not confirmed yet. Nothing is sent to it, and nothing is saved,
  /// until they do.
  bool _cleartextPending(String url) =>
      !_isCodex &&
      serverUrlNeedsCleartextConfirmation(url) &&
      cleartextOriginOf(url) != _cleartextConfirmed;

  /// Save & connect is checking the connection before it stores anything.
  bool _checkingForSave = false;

  /// The link drawing has left its first, idle picture: coming back to idle
  /// keeps the devices drawn instead of drawing them in again.
  bool _linkMoved = false;

  /// A new OpenCode server is paired first; its address and password wait
  /// under "Enter the address instead". Everywhere else (editing a saved
  /// server, a password to re-enter, the phone's own server, a Tailscale
  /// address) the fields are what the person came for and show at once.
  bool get _foldsManualAddress =>
      !_isCodex &&
      widget.existing == null &&
      !_tailscale &&
      widget.initialUrl == null &&
      !widget.focusPassword;

  /// Save & connect (a new server, any Codex or Paseo server, the server in
  /// use): the save checks the connection first, so a server that does not
  /// answer is explained before anything is stored.
  bool get _connectsOnSave =>
      _isCodex || widget.existing == null || widget.reconnectOnSave;

  /// A check since the fields last changed found the server answering.
  bool get _checkedOk =>
      _isCodex ? _codexTestResult?.ok == true : _testResult?.ok == true;

  /// What the link drawing shows: linking while pairing, checking or
  /// connecting; linked once the server answered; broken when it did not.
  ServersLinkState get _linkState {
    if (_readyProfile != null) return ServersLinkState.linked;
    if (_pairing || _testing || _submitting) return ServersLinkState.linking;
    final ok = _isCodex ? _codexTestResult?.ok : _testResult?.ok;
    if (ok == true) return ServersLinkState.linked;
    if (ok == false || _pairingFailure != null || _submitFailure != null) {
      return ServersLinkState.failed;
    }
    return ServersLinkState.idle;
  }

  /// Opens "Enter the address instead" (when folded and closed) and then
  /// focuses [node], once the field it belongs to is built.
  void _focusField(FocusNode node) {
    // Not built yet: under the closed fold, or a held secret's field that
    // is opening for a new value this frame.
    if (node.context == null) {
      if (_foldsManualAddress) {
        setState(() {
          _manualForcedOpen = true;
          _manualFold++;
        });
      }
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) node.requestFocus();
      });
      return;
    }
    node.requestFocus();
  }

  /// Brings the address field and the verdict under it into view once a
  /// check or a save answered: the field at the top, so what went wrong and
  /// the field that fixes it are read together above the pinned button.
  /// "Enter the address instead" opens first when the verdict is under it.
  void _revealVerdict() {
    if (_foldsManualAddress && _addressKey.currentContext == null) {
      setState(() {
        _manualForcedOpen = true;
        _manualFold++;
      });
    }
    // After the verdict has unfolded (KitReveal, KitMotion.standard): until
    // then the form is not yet tall enough to bring the field to the top.
    _verdictRevealTimer?.cancel();
    _verdictRevealTimer = Timer(KitMotion.standard, () {
      _verdictRevealTimer = null;
      final context = _addressKey.currentContext ?? _verdictKey.currentContext;
      if (!mounted || context == null) return;
      unawaited(
        Scrollable.ensureVisible(
          context,
          duration: KitMotion.standard,
          curve: KitMotion.enter,
          alignment: 0,
        ),
      );
    });
  }

  Timer? _verdictRevealTimer;

  /// Brings the drawing (and the step it is on) into view as a check or a
  /// connect starts from a button further down.
  void _revealStatus() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final context = _stepLineKey.currentContext ?? _statusKey.currentContext;
      if (!mounted || context == null) return;
      unawaited(
        Scrollable.ensureVisible(
          context,
          duration: KitMotion.standard,
          curve: KitMotion.enter,
          // The head of the form, where the drawing and the verdict are.
          alignment: 0,
        ),
      );
    });
  }

  /// True for both socket-style backends (Codex app-server and the Paseo
  /// daemon): they share the address, project folder and secret fields.
  bool get _isCodex => _backend != ServerBackend.openCode;
  bool get _isPaseo => _backend == ServerBackend.paseo;

  bool get _needsPassword =>
      !_isCodex && (widget.existing?.requiresPasswordReentry ?? false);

  bool get _needsCodexToken =>
      _isCodex && (widget.existing?.requiresCodexTokenReentry ?? false);

  Future<void> _probeSecureStorage() async {
    final probe = widget.secureStorageProbe;
    if (probe == null) return;
    String? notice;
    try {
      notice = await probe();
    } catch (_) {
      notice = null;
    }
    if (!mounted || notice == null || _isCodex) return;
    setState(() => _secureStorageNotice = notice);
  }

  @override
  void initState() {
    super.initState();
    if (!_isCodex) _probeSecureStorage();
    _urlLength = _url.text.length;
    _urlFocus.addListener(_checkSocketAddressOnLeave);
  }

  /// A Codex or Paseo address is checked when the person moves on from its
  /// field (the rule a standing helper used to recite): a wrong one says
  /// why under the field at once, a right one clears it.
  void _checkSocketAddressOnLeave() {
    if (!mounted || !_isCodex || _urlFocus.hasFocus || _submitting) return;
    final typed = _url.text.trim();
    if (typed.isEmpty) return;
    final url = _isPaseo
        ? normalizePaseoServerUrl(typed)
        : normalizeCodexServerUrl(typed);
    final error = _isPaseo
        ? validatePaseoServerUrl(url)
        : validateCodexServerUrl(url);
    if (error != _error) setState(() => _error = error);
  }

  /// A paste is a jump of several characters at once. When it lands without a
  /// scheme, expand it in place so `192.0.2.7:4096` just works.
  ///
  /// A pasted *pairing* payload is intercepted before anything else. It is
  /// JSON carrying the serve password, and it must not be left sitting in a
  /// text field: the field renders it, a screenshot captures it, and the
  /// platform may offer it to autofill. So the field is emptied first and the
  /// payload is routed to [_applyPairing].
  void _urlChanged(String value) {
    _applyUrlChange(value);
    _scheduleAutoTest();
  }

  void _applyUrlChange(String value) {
    setState(_invalidateProbe);
    if (_isCodex) {
      final pasted = value.length - _urlLength >= 4;
      _urlLength = value.length;
      if (pasted && !value.contains('://')) {
        final normalized = _isPaseo
            ? normalizePaseoServerUrl(value)
            : normalizeCodexServerUrl(value);
        if (normalized != value.trim()) {
          _urlLength = normalized.length;
          _url.value = TextEditingValue(
            text: normalized,
            selection: TextSelection.collapsed(offset: normalized.length),
          );
        }
      }
      return;
    }
    if (looksLikePairingPayload(value)) {
      final parsed = parsePairingPayload(value);
      _url.value = TextEditingValue.empty;
      _urlLength = 0;
      if (!parsed.ok) {
        setState(() {
          _pairingNotice = null;
          _pairingFailure = parsed.error;
        });
        return;
      }
      unawaited(_applyPairing(parsed.payload!));
      return;
    }
    final pasted = value.length - _urlLength >= 4;
    _urlLength = value.length;
    if (pasted && !value.contains('://')) {
      final normalized = normalizeServerProfileUrl(value);
      if (normalized != value.trim()) {
        _urlLength = normalized.length;
        _url.value = TextEditingValue(
          text: normalized,
          selection: TextSelection.collapsed(offset: normalized.length),
        );
      }
    }
  }

  /// Every field's change handler: what was tested is no longer what is
  /// typed, so the verdict goes and, on the first-run path, a new test is
  /// queued behind a pause.
  void _fieldChanged() {
    setState(_invalidateProbe);
    _scheduleAutoTest();
  }

  /// True on the connect step of Add server only (UX plan 5.6 step 3).
  /// Editing a saved server never tests by itself: that person came to
  /// change one value, and a probe of the half-edited profile is noise.
  bool get _autoTests => _stepped && _step == _AddStep.connect;

  /// The required fields as [_testConnection] would accept them, checked
  /// without its side effects (no error text, no focus move).
  bool get _readyForAutoTest {
    if (_url.text.trim().isEmpty) return false;
    if (_isCodex) {
      final url = _isPaseo
          ? normalizePaseoServerUrl(_url.text)
          : normalizeCodexServerUrl(_url.text);
      return _validateSocketFields(url) == null;
    }
    final url = normalizeServerProfileUrl(_url.text);
    return validateServerProfileUrl(
              url,
              username: _user.text,
              password: _password,
            ) ==
            null &&
        !_cleartextPending(url);
  }

  /// One test after the person pauses, never one per keystroke: each change
  /// restarts the wait, and [_invalidateProbe] has already retired whatever
  /// probe was in flight.
  void _scheduleAutoTest() {
    _autoTestTimer?.cancel();
    _autoTestTimer = null;
    if (!_autoTests || !_readyForAutoTest) return;
    _autoTestTimer = Timer(autoTestPause, () {
      _autoTestTimer = null;
      if (!mounted || _submitting || _pairing || _testing || _closing) return;
      if (!_readyForAutoTest) return;
      unawaited(_testConnection(auto: true));
    });
  }

  void _invalidateProbe() {
    _autoTestTimer?.cancel();
    _autoTestTimer = null;
    _stopSlowWatch();
    _probeGeneration += 1;
    _testing = false;
    _verdictFromSave = false;
    _submitFailure = null;
    _submitDetails = null;
    _pairing = false;
    _error = null;
    _testResult = null;
    _codexTestResult = null;
    _pairingNotice = null;
    _pairingFailure = null;
  }

  /// Checks the connection and shows the verdict; completes with whether the
  /// server answered.
  ///
  /// [auto] is the first-run test that runs by itself. It reports the same
  /// verdict but never moves focus: the person is still typing somewhere.
  /// [forSave] is Save & connect checking before it stores anything: the
  /// verdict then offers "Save anyway".
  Future<bool> _testConnection({
    bool auto = false,
    bool forSave = false,
  }) async {
    if (_testing) return false;
    _autoTestTimer?.cancel();
    _autoTestTimer = null;
    if (_isCodex) {
      return _testCodexConnection(auto: auto, forSave: forSave);
    }
    final url = normalizeServerProfileUrl(_url.text);
    if (_tailscale && !isValidTailscaleAddress(url)) {
      setState(() {
        _error = _connectionL10n(context).tailscaleAddressError;
        _testResult = null;
      });
      return false;
    }
    if (url != _url.text.trim()) {
      _urlLength = url.length;
      _url.value = TextEditingValue(
        text: url,
        selection: TextSelection.collapsed(offset: url.length),
      );
    }
    final error = validateServerProfileUrl(
      url,
      username: _user.text,
      password: _password,
    );
    if (error != null) {
      setState(() {
        _error = error;
        _testResult = null;
      });
      _focusField(_urlFocus);
      return false;
    }
    if (_cleartextPending(url)) {
      // The warning under the address field asks first; no request is made.
      setState(() {
        _error = null;
        _testResult = null;
      });
      if (!auto) _revealVerdict();
      return false;
    }
    final generation = ++_probeGeneration;
    setState(() {
      _testing = true;
      _testResult = null;
      _error = null;
      _watchSlowCheck();
    });
    if (!auto) {
      // The keyboard goes, and the form scrolls back to the drawing that
      // shows the check (a focused field would pull the scroll back to
      // itself).
      FocusScope.of(context).unfocus();
      _revealStatus();
    }
    final result = await serverProbe(
      baseUrl: url,
      username: _user.text.trim(),
      password: _password,
    );
    if (!mounted || generation != _probeGeneration) return false;
    setState(() {
      _testing = false;
      _stopSlowWatch();
      _submitFailure = null;
      _submitDetails = null;
      _testResult = result;
      _verdictFromSave = forSave && !result.ok;
    });
    // Per the v2 auth taxonomy: a 401 without a password sends the user to
    // the password field; a rejected password selects it for a clean repaste.
    if (!auto && result.flavor == ServerFlavor.v2 && result.needsPassword) {
      // A held password was refused: the field opens for a new one.
      if (_heldPassword != null) {
        setState(() => _heldPassword = null);
      } else if (_pass.text.isNotEmpty) {
        _pass.selection = TextSelection(
          baseOffset: 0,
          extentOffset: _pass.text.length,
        );
      }
      _focusField(_passFocus);
    }
    if (!auto) _revealVerdict();
    return result.ok;
  }

  String? _validateSocketFields(String url) => _isPaseo
      ? validatePaseoServerUrl(url) ??
            validateCodexProjectDirectory(_codexDirectory.text.trim()) ??
            validatePaseoPassword(_token)
      : validateCodexServerUrl(url) ??
            validateCodexProjectDirectory(_codexDirectory.text.trim()) ??
            validateCodexConnectionToken(_token);

  Future<bool> _testCodexConnection({
    bool auto = false,
    bool forSave = false,
  }) async {
    var url = _isPaseo
        ? normalizePaseoServerUrl(_url.text)
        : normalizeCodexServerUrl(_url.text);
    if (url != _url.text.trim()) {
      _urlLength = url.length;
      _url.value = TextEditingValue(
        text: url,
        selection: TextSelection.collapsed(offset: url.length),
      );
    }
    final error = _validateSocketFields(url);
    if (error != null) {
      setState(() {
        _error = error;
        _codexTestResult = null;
      });
      if ((_isPaseo
              ? validatePaseoServerUrl(url)
              : validateCodexServerUrl(url)) !=
          null) {
        _urlFocus.requestFocus();
      } else if (validateCodexProjectDirectory(_codexDirectory.text.trim()) !=
          null) {
        _codexDirectoryFocus.requestFocus();
      } else {
        _codexTokenFocus.requestFocus();
      }
      return false;
    }
    final generation = ++_probeGeneration;
    setState(() {
      _testing = true;
      _codexTestResult = null;
      _error = null;
      _watchSlowCheck();
    });
    if (!auto) {
      FocusScope.of(context).unfocus();
      _revealStatus();
    }
    try {
      final result = await socketAgentProbe(
        backend: _backend,
        baseUrl: url,
        secret: _token,
        directory: _codexDirectory.text.trim(),
      );
      if (!mounted || generation != _probeGeneration) return false;
      setState(() {
        _testing = false;
        _stopSlowWatch();
        _submitFailure = null;
        _submitDetails = null;
        _codexTestResult = result;
        _verdictFromSave = forSave && !result.ok;
      });
      if (!result.ok && !auto) _codexTokenFocus.requestFocus();
      if (!auto) _revealVerdict();
      return result.ok;
    } catch (error) {
      if (!mounted || generation != _probeGeneration) return false;
      setState(() {
        _testing = false;
        _stopSlowWatch();
        _error = productErrorText(error);
      });
      return false;
    }
  }

  /// Reads a pairing payload from the clipboard and applies it.
  ///
  /// The whole point of `opencode2 pair` is that the address, the username,
  /// and a 32-byte random password arrive together, so this fills all three
  /// rather than making the user shuttle between fields.
  Future<void> _pastePairing() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final raw = data?.text ?? '';
    if (!mounted) return;
    if (raw.trim().isEmpty) {
      setState(() {
        _pairingNotice = null;
        _pairingFailure = lookupAppLocalizations(
          Localizations.localeOf(context),
        ).e7SetupEmptyPairClipboard;
      });
      return;
    }
    final parsed = parsePairingPayload(raw);
    if (!parsed.ok) {
      setState(() {
        _pairingNotice = null;
        _pairingFailure = parsed.error;
      });
      return;
    }
    await _applyPairing(parsed.payload!);
  }

  /// Opens the camera scanner and applies whatever pairing code it decodes.
  ///
  /// The scanner owns every camera failure — permission, hardware, a QR that
  /// is not a pairing code — and returns null for all of them, so there is
  /// nothing to explain here beyond a payload that arrived.
  Future<void> _scanPairing() async {
    if (!platformCapabilities.supportsQrPairing) return;
    final payload = await pushKitPage<PairingPayload>(
      context,
      (_) => const PairingScannerScreen(),
    );
    if (payload == null || !mounted) {
      payload?.consume();
      return;
    }
    await _applyPairing(payload);
  }

  /// Probes a pairing payload's addresses and fills the editor from the one
  /// that answers.
  ///
  /// The payload is consumed on every exit path: it carries the serve
  /// password, and nothing beyond this method should still be holding it.
  /// The password reaches the password field and Keystore from there — it is
  /// never logged, never put in [_pairingNotice] or [_pairingFailure], and
  /// never in a URL.
  Future<void> _applyPairing(PairingPayload payload) async {
    if (_tailscale) {
      payload.consume();
      setState(
        () => _pairingFailure = _connectionL10n(context).tailscaleReviewDetail,
      );
      return;
    }
    if (_pairing) {
      payload.consume();
      return;
    }
    final username = payload.username;
    final password = payload.password;
    final firstUrl = payload.urls.first;
    final generation = ++_probeGeneration;
    setState(() {
      _testing = false;
      _pairing = true;
      _error = null;
      _testResult = null;
      _pairingNotice = null;
      _pairingFailure = null;
      _watchSlowCheck();
    });

    final PairingSelection selection;
    try {
      selection = await selectPairingUrl(
        payload,
        confirmedCleartextOrigins: {?_cleartextConfirmed},
      );
    } finally {
      payload.consume();
    }
    if (!mounted || generation != _probeGeneration) return;

    // Fill the fields either way. Even when nothing answered, the user now
    // has the address and credentials in front of them and can fix the tunnel
    // rather than re-copying everything by hand.
    //
    // A private-network http:// address is held for confirmation before
    // anything is sent: it fills the address, the warning appears under it,
    // and "Use it anyway" runs the check with the pairing password.
    final held = selection.outcomes
        .where((o) => o.needsCleartextConfirm)
        .map((o) => o.url)
        .firstOrNull;
    final chosen =
        selection.chosenUrl ?? held ?? normalizeServerProfileUrl(firstUrl);
    _url.value = TextEditingValue(text: chosen);
    _urlLength = chosen.length;
    _user.text = username;
    _heldPassword = password;
    _pass.clear();

    final host = Uri.tryParse(chosen)?.host ?? chosen;
    final tried = selection.outcomes.length;
    final result = selection.chosenResult;
    setState(() {
      _pairing = false;
      _stopSlowWatch();
      // The existing probe-verdict row already says the flavor, the version,
      // and "Connected — save to finish", and it is what `_save` reads to
      // cache the detected flavor. So pairing hands it the result and says
      // only the thing it cannot: *which* address was chosen, out of how
      // many. Repeating the verdict here would be two widgets telling the
      // user the same thing.
      _testResult = selection.ok ? result : null;
      if (!selection.ok && held != null) {
        _pairingNotice = null;
        _pairingFailure = null;
        _manualForcedOpen = true;
        _manualFold++;
      } else if (selection.ok) {
        _pairingNotice = tried > 1
            ? lookupAppLocalizations(
                Localizations.localeOf(context),
              ).e7SetupPairedChoice(host, tried)
            : lookupAppLocalizations(
                Localizations.localeOf(context),
              ).e7SetupPaired(host);
        _pairingFailure = null;
      } else {
        _pairingNotice = null;
        _pairingFailure =
            lookupAppLocalizations(
              Localizations.localeOf(context),
            ).e7SetupPairingFailed(
              setupUiMessage(
                lookupAppLocalizations(Localizations.localeOf(context)),
                selection.failureDetail,
              ),
              _pairingHint,
            );
      }
    });
    if (!selection.connected && (result?.needsPassword ?? false)) {
      _focusField(_passFocus);
    }
  }

  /// What to do about a pairing code whose addresses all failed. A phone and
  /// a desktop have genuinely different answers, so they get different ones.
  String get _pairingHint => platformCapabilities.supportsUsbHostBridge
      ? lookupAppLocalizations(
          Localizations.localeOf(context),
        ).e7SetupPairingPhoneHint
      : lookupAppLocalizations(
          Localizations.localeOf(context),
        ).e7SetupPairingDesktopHint;

  /// The password field changed: typed, or pasted with the field's own
  /// Paste (paste-first entry for the per-run serve password; nobody types
  /// a random 32-byte base64url string). A copied `server password ` line
  /// prefix is dropped.
  ///
  /// A whole pairing payload landing here is routed to [_applyPairing]
  /// instead, and the field is emptied at once — leaving that JSON in the
  /// password field would be both wrong and a way to get the credential
  /// rendered on screen.
  void _passwordChanged(String value) {
    if (looksLikePairingPayload(value)) {
      _pass.clear();
      final parsed = parsePairingPayload(value);
      if (!parsed.ok) {
        setState(() {
          _invalidateProbe();
          _pairingNotice = null;
          _pairingFailure = parsed.error;
        });
        return;
      }
      setState(_invalidateProbe);
      unawaited(_applyPairing(parsed.payload!));
      return;
    }
    const prefix = 'server password';
    final trimmed = value.trim();
    if (trimmed.toLowerCase().startsWith(prefix)) {
      final bare = trimmed.substring(prefix.length).trim();
      _pass.value = TextEditingValue(
        text: bare,
        selection: TextSelection.collapsed(offset: bare.length),
      );
    }
    _fieldChanged();
  }

  /// The token field changed; a pasted token loses the whitespace a copy
  /// from a terminal carries.
  void _tokenChanged(String value) {
    final trimmed = value.trim();
    if (trimmed != value && trimmed.isNotEmpty) {
      _codexToken.value = TextEditingValue(
        text: trimmed,
        selection: TextSelection.collapsed(offset: trimmed.length),
      );
    }
    _fieldChanged();
  }

  bool get _dirty {
    final baseline = _savedProfile ?? widget.existing;
    // A new server's kind is a step, not an edit: choosing one and leaving
    // loses nothing typed.
    return (baseline != null && _backend != baseline.backend) ||
        _name.text != (baseline?.name ?? '') ||
        _url.text != (baseline?.baseUrl ?? '') ||
        _user.text != (baseline?.username ?? '') ||
        _password != (baseline?.password ?? '') ||
        _codexDirectory.text != (baseline?.codexDirectory ?? '') ||
        _token != (baseline?.codexToken ?? '');
  }

  @override
  void dispose() {
    _autoTestTimer?.cancel();
    _slowTimer?.cancel();
    _verdictRevealTimer?.cancel();
    _name.dispose();
    _url.dispose();
    _user.dispose();
    _pass.dispose();
    _codexDirectory.dispose();
    _codexToken.dispose();
    _urlFocus
      ..removeListener(_checkSocketAddressOnLeave)
      ..dispose();
    _nameFocus.dispose();
    _userFocus.dispose();
    _passFocus.dispose();
    _codexDirectoryFocus.dispose();
    _codexTokenFocus.dispose();
    super.dispose();
  }

  Future<void> _close() async {
    if (_closing || _submitting) return;
    // The first step (what runs there) has nothing to type: closing it
    // never asks, even when Back brought the person here from an address
    // they had started (emulator QA B4). What they left behind is on a step
    // they already walked away from.
    if (!_dirty || _step == _AddStep.kind) {
      Navigator.pop(context);
      return;
    }
    _closing = true;
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final discard = await showKitConfirm(
      context,
      kind: KitConfirmKind.discard,
      title: copy.e7SetupDiscardChanges,
      body: copy.e7SetupUnsavedProfile,
      confirmLabel: copy.e7SetupDiscard,
      cancelLabel: copy.draftKeepEditing,
      icon: AppIconography.editOff,
      sheetKey: const ValueKey('server-discard-sheet'),
      confirmKey: const ValueKey('server-discard-confirm'),
    );
    _closing = false;
    if (discard && mounted) Navigator.pop(context);
  }

  Future<void> _tailscaleHelp() async {
    if (_submitting) return;
    final before = _url.text;
    final generation = ++_probeGeneration;
    setState(() => _testing = false);
    final reviewed = await pushKitPage<String>(
      context,
      (_) => TailscaleSetupScreen(initialAddress: before),
    );
    if (!mounted ||
        reviewed == null ||
        _url.text != before ||
        generation != _probeGeneration) {
      return;
    }
    setState(() {
      _invalidateProbe();
      _url.text = reviewed;
      _urlLength = reviewed.length;
    });
    _scheduleAutoTest();
  }

  /// "Not on the same network?" on the connect step. For an OpenCode server
  /// it is the flow's Tailscale step, and the address is then typed on the
  /// connect step that follows. Paseo and Codex listen on `ws://`, which a
  /// tailnet HTTPS name does not give, so there the Tailscale page opens for
  /// its guidance and the field is left to the person.
  Future<void> _notSameNetwork() async {
    if (_submitting) return;
    if (!_isCodex) return _chooseTailscale();
    await pushKitPage<String>(context, (_) => const TailscaleSetupScreen());
  }

  /// Moves the flow to [step]; whatever a check was doing is retired.
  void _goTo(_AddStep step) {
    FocusScope.of(context).unfocus();
    setState(() {
      _invalidateProbe();
      if (_step == _AddStep.connect && step != _AddStep.connect) {
        // A secret field never mounts filled (SEC-3): what was typed is
        // held, like a saved one, and the field says so when it returns.
        if (_pass.text.isNotEmpty) {
          _heldPassword = _pass.text;
          _pass.clear();
        }
        if (_codexToken.text.isNotEmpty) {
          _heldToken = _codexToken.text;
          _codexToken.clear();
        }
      }
      _step = step;
    });
    if (step == _AddStep.connect) _scheduleAutoTest();
  }

  /// The first step's answer: the connect step for that kind of server.
  void _chooseKind(ServerBackend backend) {
    if (_submitting) return;
    _backend = backend;
    _tailscale = false;
    _goTo(_AddStep.connect);
  }

  /// Tailscale, as a step of this flow (not a page it leaves for): an
  /// OpenCode server reached over the tailnet.
  void _chooseTailscale() {
    if (_submitting) return;
    _backend = ServerBackend.openCode;
    _tailscale = true;
    _goTo(_AddStep.tailscale);
  }

  /// Back within the flow: the connect step returns to Tailscale or to the
  /// first step, Tailscale to the first step. Null where Back leaves.
  _AddStep? get _previousStep => switch (_step) {
    _ when !_stepped => null,
    _AddStep.connect => _tailscale ? _AddStep.tailscale : _AddStep.kind,
    _AddStep.tailscale => _AddStep.kind,
    _AddStep.kind || _AddStep.ready => null,
  };

  /// Close or Back from the top bar and the system: a step back, the
  /// finished flow's way in, or the discard check.
  void _exit() {
    if (_submitting) return;
    if (_readyProfile case final profile?) {
      Navigator.pop(context, profile);
      return;
    }
    if (_previousStep case final previous?) {
      if (previous == _AddStep.kind) _tailscale = false;
      _goTo(previous);
      return;
    }
    unawaited(_close());
  }

  /// A check or a pairing started: after [slowCheckAfter] it offers Cancel,
  /// so a server that never answers never holds the person.
  void _watchSlowCheck() {
    _slowTimer?.cancel();
    _slowCheck = false;
    _slowTimer = Timer(slowCheckAfter, () {
      _slowTimer = null;
      if (!mounted || !(_testing || _pairing)) return;
      setState(() => _slowCheck = true);
    });
  }

  void _stopSlowWatch() {
    _slowTimer?.cancel();
    _slowTimer = null;
    _slowCheck = false;
  }

  /// Cancel on a slow check: the answer, when it comes, is ignored, and the
  /// fields are the person's again.
  void _cancelCheck() => setState(_invalidateProbe);

  /// Null where the link must not exist: off the connect step of Add
  /// server, once Tailscale is the way, and on a platform with no Tailscale
  /// handoff (hide, don't disable).
  Widget? _notSameNetworkLink() {
    if (!_stepped ||
        _step != _AddStep.connect ||
        _tailscale ||
        !platformCapabilities.supportsTailscaleHandoff) {
      return null;
    }
    return KitInset(
      child: KitButton.tertiary(
        key: const ValueKey('connect-not-same-network'),
        onPressed: _submitting ? null : () => unawaited(_notSameNetwork()),
        label: _connectionL10n(context).firstRunNotSameNetwork,
      ),
    );
  }

  /// "AI Team (optional)" › Add manually: the shared form; a found host is
  /// kept on the profile the next save writes.
  Future<void> _addTeamHost() async {
    final config = await showTeamHostSheet(
      context,
      initialUrl:
          _orchestration?.url ??
          teamDiscoveryUrlFor(normalizeServerProfileUrl(_url.text)) ??
          '',
      initialCity: _orchestration?.city ?? '',
      initialHostKind: _orchestration?.hostKind,
    );
    if (config == null || !mounted) return;
    setState(() => _orchestration = config);
  }

  /// Save & connect checks the connection first (unless a check since the
  /// last edit already found it answering), so a server that does not
  /// answer is explained — refused, timed out, wrong password — before it is
  /// stored. [anyway] is the verdict's "Save anyway": the person keeps a
  /// server that is not running right now.
  Future<void> _save({bool anyway = false}) async {
    if (_submitting || _testing) return;
    if (!anyway && _connectsOnSave && !_checkedOk) {
      FocusScope.of(context).unfocus();
      _checkingForSave = true;
      final bool answered;
      try {
        answered = await _testConnection(forSave: true);
      } finally {
        _checkingForSave = false;
      }
      if (!answered || !mounted) return;
    }
    if (_isCodex) {
      await _saveCodex();
      return;
    }
    var url = normalizeServerProfileUrl(_url.text);
    if (_tailscale && !isValidTailscaleAddress(url)) {
      setState(() => _error = _connectionL10n(context).tailscaleAddressError);
      return;
    }
    final error = validateServerProfileUrl(
      url,
      username: _user.text,
      password: _password,
    );
    if (error != null) {
      setState(() => _error = error);
      _focusField(_urlFocus);
      return;
    }
    if (_cleartextPending(url)) {
      _revealVerdict();
      return;
    }
    final uri = Uri.parse(url);
    url = uri.replace(scheme: uri.scheme.toLowerCase()).toString();
    // Cache what Test connection detected; the connection layer re-verifies
    // on every cold connect and a failed connect re-probes, so a save without
    // a test (default v1) still self-corrects.
    final probed = _testResult;
    final normalizedUrl = url.endsWith('/')
        ? url.substring(0, url.length - 1)
        : url;
    final previousUrl = widget.existing == null
        ? null
        : normalizeServerProfileUrl(
            widget.existing!.baseUrl,
          ).replaceFirst(RegExp(r'/$'), '');
    final endpointChanged = previousUrl != null && previousUrl != normalizedUrl;
    // Cached identity belongs to an endpoint, not merely this profile name.
    // A new untested endpoint uses the same safe default as a new profile;
    // connect/probe will detect it rather than inherit the old server's v2 proof.
    final detected = probed != null && probed.flavor != ServerFlavor.unknown
        ? probed.flavor
        : endpointChanged
        ? ServerFlavor.v1
        : widget.existing?.flavor ?? ServerFlavor.v1;
    final profile = ServerProfile(
      id:
          _savedProfile?.id ??
          widget.existing?.id ??
          DateTime.now().microsecondsSinceEpoch.toString(),
      name: _name.text.trim().isEmpty
          ? plainServerName(uri.host)
          : _name.text.trim(),
      baseUrl: normalizedUrl,
      username: _user.text.trim(),
      password: _password,
      flavor: detected,
      serverVersion:
          probed?.version ??
          (endpointChanged ? null : widget.existing?.serverVersion),
      orchestration: _orchestration,
    );
    if (serverUrlNeedsCleartextConfirmation(normalizedUrl)) {
      profile.cleartextConfirmedOrigin = _cleartextConfirmed;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _invalidateProbe();
      _submitting = true;
      _submitFailure = null;
      _submitDetails = null;
    });
    await _submit(profile);
  }

  Future<void> _saveCodex() async {
    var url = _isPaseo
        ? normalizePaseoServerUrl(_url.text)
        : normalizeCodexServerUrl(_url.text);
    final error = _validateSocketFields(url);
    if (error != null) {
      setState(() => _error = error);
      return;
    }
    final uri = Uri.parse(url);
    url = uri.toString().replaceAll(RegExp(r'/$'), '');
    final probed = _codexTestResult;
    final profile = ServerProfile(
      id:
          _savedProfile?.id ??
          widget.existing?.id ??
          DateTime.now().microsecondsSinceEpoch.toString(),
      name: _name.text.trim().isEmpty
          ? plainServerName(uri.host)
          : _name.text.trim(),
      baseUrl: url,
      backend: _backend,
      codexDirectory: _codexDirectory.text.trim(),
      codexToken: _token,
      serverVersion: probed?.version ?? widget.existing?.serverVersion,
    );
    FocusScope.of(context).unfocus();
    setState(() {
      _invalidateProbe();
      _submitting = true;
      _submitFailure = null;
      _submitDetails = null;
    });
    await _submit(profile);
  }

  /// Hands [profile] to the save (and connect), then finishes: Add server
  /// ends on its ready step, every other edit closes. A failure stays on
  /// the form, said where the fields that fix it are.
  Future<void> _submit(ServerProfile profile) async {
    _revealStatus();
    final outcome = await widget.onSubmit(profile, tailscale: _tailscale);
    if (!mounted) return;
    if (outcome.saved) _savedProfile = profile;
    if (outcome.failure == null) {
      if (!_stepped) {
        Navigator.pop(context, profile);
        return;
      }
      setState(() {
        _submitting = false;
        _readyProfile = profile;
        _step = _AddStep.ready;
      });
      return;
    }
    setState(() {
      _submitting = false;
      _submitFailure = outcome.failure;
      _submitDetails = outcome.details;
    });
    _revealVerdict();
  }

  /// The Codex and Paseo fields: address, project folder, token, then the
  /// name most people never change (KIT-20: a labelled kit field each; the
  /// token is the secret kind, never prefilled).
  List<Widget> _buildCodexFields(AppLocalizations copy, KitTokens tokens) => [
    SizedBox(height: tokens.space3),
    KeyedSubtree(
      key: _addressKey,
      child: KitField(
        label: copy.connectionServerAddress,
        kind: KitFieldKind.url,
        controller: _url,
        focusNode: _urlFocus,
        fieldKey: const ValueKey('codex-server-address-field'),
        hint: _isPaseo ? copy.paseoAddressHint : copy.codexAddressHint,
        // No standing ws:// / wss:// rule: the field checks the address
        // when the person moves on and says what is wrong right here.
        error: _error == null ? null : setupUiMessage(copy, _error!),
        enabled: !_submitting,
        disabledReason: _submitting ? copy.e7SetupSaving : null,
        textInputAction: TextInputAction.next,
        onSubmitted: (_) => _codexDirectoryFocus.requestFocus(),
        onChanged: _urlChanged,
      ),
    ),
    ?_notSameNetworkLink(),
    _verdicts(copy, tokens),
    SizedBox(height: tokens.space4),
    KitField(
      label: copy.codexProjectFolder,
      kind: KitFieldKind.path,
      controller: _codexDirectory,
      focusNode: _codexDirectoryFocus,
      fieldKey: const ValueKey('codex-project-directory-field'),
      hint: '/work/my-project',
      enabled: !_submitting,
      disabledReason: _submitting ? copy.e7SetupSaving : null,
      textInputAction: TextInputAction.next,
      onSubmitted: (_) => _codexTokenFocus.requestFocus(),
      onChanged: (_) => _fieldChanged(),
    ),
    SizedBox(height: tokens.space4),
    KitField.secret(
      label: _needsCodexToken
          ? copy.codexTokenReentry
          : _isPaseo
          ? copy.paseoPasswordLabel
          : copy.codexTokenLabel,
      controller: _codexToken,
      focusNode: _codexTokenFocus,
      fieldKey: const ValueKey('codex-connection-token-field'),
      revealKey: const ValueKey('codex-token-visibility'),
      pasteKey: const ValueKey('codex-token-paste'),
      replaceKey: const ValueKey('codex-token-replace'),
      helper: _isPaseo ? copy.paseoPasswordHelp : copy.codexTokenStorageHelp,
      saved: _heldToken != null,
      onReplace: () => setState(() {
        _heldToken = null;
        _invalidateProbe();
      }),
      autofocus: _needsCodexToken || widget.focusPassword,
      enabled: !_submitting,
      disabledReason: _submitting ? copy.e7SetupSaving : null,
      textInputAction: TextInputAction.done,
      onSubmitted: (_) => _save(),
      onChanged: _tokenChanged,
    ),
    // What most people never change, after what they must fill in.
    SizedBox(height: tokens.space4),
    KitField(
      label: copy.connectionDisplayName,
      controller: _name,
      focusNode: _nameFocus,
      fieldKey: const ValueKey('codex-server-name-field'),
      hint: copy.connectionDisplayNameHint,
      enabled: !_submitting,
      disabledReason: _submitting ? copy.e7SetupSaving : null,
      textInputAction: TextInputAction.done,
      onSubmitted: (_) => _save(),
      onChanged: (_) => _fieldChanged(),
    ),
    SizedBox(height: tokens.space4),
  ];

  Widget _buildCodexProbeVerdict(AppLocalizations copy) {
    final result = _codexTestResult!;
    // A verdict is a notice on the form's rails, not a filled block (§3).
    return KeyedSubtree(
      key: const ValueKey('codex-probe-verdict'),
      child: KitNotice(
        key: ValueKey(result.ok ? 'codex-test-success' : 'codex-test-failure'),
        tone: result.ok ? AppStatusTone.ok : AppStatusTone.failure,
        message: result.ok
            ? copy.codexConnectionVerified
            : setupUiMessage(copy, result.message),
        actions: [if (!result.ok && _verdictFromSave) _saveAnywayAction(copy)],
      ),
    );
  }

  KitAction _saveAnywayAction(AppLocalizations copy) => KitAction(
    key: const ValueKey('server-save-anyway'),
    label: copy.addServerSaveAnyway,
    onPressed: _submitting ? null : () => unawaited(_save(anyway: true)),
  );

  /// The host the person is connecting to, for the drawing's caption.
  String get _host {
    final url = _isCodex
        ? (_isPaseo
              ? normalizePaseoServerUrl(_url.text)
              : normalizeCodexServerUrl(_url.text))
        : normalizeServerProfileUrl(_url.text);
    final host = Uri.tryParse(url)?.host ?? '';
    return host.isEmpty ? _url.text.trim() : host;
  }

  /// The phone and the computer linking up (design standard §10): at the
  /// head of a new server's form, moving only while it pairs, checks or
  /// connects, with one line saying what it is doing then.
  Widget _linkMoment(AppLocalizations copy, KitTokens tokens) {
    final state = _linkState;
    if (state != ServersLinkState.idle) _linkMoved = true;
    final caption = switch (state) {
      ServersLinkState.linking when _pairing => copy.e7SetupPairing,
      ServersLinkState.linking when _submitting => copy.addServerConnectingHost(
        _host,
      ),
      ServersLinkState.linking => copy.addServerCheckingHost(_host),
      ServersLinkState.linked when _readyProfile != null =>
        copy.addServerConnectedHost(_host),
      _ => null,
    };
    return Column(
      key: const ValueKey('server-link-moment'),
      children: [
        Center(
          child: KitIllustration(
            // Each state plays its own entrance: the link drawing across,
            // the spark landing, the link breaking.
            key: ValueKey('server-link-${state.name}'),
            scene: ServersLinkScene(state, intro: !_linkMoved),
            ambient: state == ServersLinkState.linking,
            // Paired: a finished moment, so the spark takes the longer
            // celebration entrance (design standard §10).
            entranceDuration: state == ServersLinkState.linked
                ? KitMotion.celebration
                : KitMotion.entrance,
          ),
        ),
        // One line, held open so the form does not jump when it speaks.
        SizedBox(
          height: tokens.space6,
          child: Semantics(
            liveRegion: true,
            child: KitText(
              caption ?? '',
              key: const ValueKey('server-link-caption'),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ],
    );
  }

  /// What the old connection help explained, said where it matters: an
  /// http:// address on the network (not this device) is refused, and a
  /// private HTTPS name through Tailscale is the way (never a public relay).
  /// Shown only with the field's error; on Add server it offers the
  /// Tailscale step.
  Widget? _remoteHttpAdvice(AppLocalizations copy, KitTokens tokens) {
    if (_error == null ||
        _isCodex ||
        _tailscale ||
        explainConnectionAddress(_url.text) != ConnectionAdvice.remoteHttp) {
      return null;
    }
    final offersTailscale =
        _stepped && platformCapabilities.supportsTailscaleHandoff;
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space2),
      child: KitNotice(
        key: const ValueKey('server-remote-http-advice'),
        tone: AppStatusTone.neutral,
        message: copy.addServerRemoteHttpAdvice,
        actions: [
          if (offersTailscale)
            KitAction(
              key: const ValueKey('server-remote-http-tailscale'),
              label: copy.addServerUseTailscale,
              onPressed: _submitting ? null : _chooseTailscale,
            ),
        ],
      ),
    );
  }

  /// The warning for plain HTTP to a private network address, under the
  /// field it is about, with the explicit confirm. Once confirmed it stays
  /// as one quiet line, so the person can see what they chose.
  Widget? _cleartextWarning(AppLocalizations copy, KitTokens tokens) {
    if (_isCodex || _tailscale) return null;
    final url = normalizeServerProfileUrl(_url.text);
    if (!serverUrlNeedsCleartextConfirmation(url) ||
        validateServerProfileUrl(url) != null) {
      return null;
    }
    final pending = _cleartextPending(url);
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space2),
      child: KitNotice(
        key: const ValueKey('server-cleartext-warning'),
        tone: AppStatusTone.neutral,
        icon: pending ? AppIconography.warning : null,
        message: pending
            ? copy.addServerCleartextWarning
            : copy.addServerCleartextConfirmed,
        actions: [
          if (pending &&
              _stepped &&
              platformCapabilities.supportsTailscaleHandoff)
            KitAction(
              key: const ValueKey('server-cleartext-tailscale'),
              label: copy.addServerUseTailscale,
              onPressed: _submitting ? null : _chooseTailscale,
            ),
          if (pending)
            KitAction(
              key: const ValueKey('server-cleartext-confirm'),
              label: copy.addServerCleartextConfirm,
              onPressed: _submitting
                  ? null
                  : () {
                      setState(() {
                        _cleartextConfirmed = cleartextOriginOf(url);
                        _invalidateProbe();
                      });
                      // With a password in hand (a pairing code's, or one
                      // typed) the check runs now; otherwise the first-run
                      // pause applies as for any other address.
                      if (_password.isNotEmpty) {
                        unawaited(_testConnection());
                      } else {
                        _scheduleAutoTest();
                      }
                    },
            ),
        ],
      ),
    );
  }

  /// The address and password of an OpenCode server, and the check. Folded
  /// under "Enter the address instead" for a new server (pairing is the
  /// main path); shown at once where the fields are what the person came
  /// to change.
  Widget _manualAddress(AppLocalizations copy, KitTokens tokens) {
    final fields = _Rails(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(height: _foldsManualAddress ? tokens.space1 : tokens.space3),
          KeyedSubtree(
            key: _addressKey,
            child: KitField(
              label: copy.e7SetupServerUrl,
              kind: KitFieldKind.url,
              controller: _url,
              focusNode: _urlFocus,
              fieldKey: const ValueKey('server-url-field'),
              hint: 'https://server.example',
              helper: _tailscale
                  ? copy.tailscaleAddressDetail
                  : copy.e7SetupHttpsHint,
              error: _error,
              enabled: !_submitting,
              disabledReason: _submitting ? copy.e7SetupSaving : null,
              textInputAction: TextInputAction.next,
              // Straight to the password: the name and username are under
              // More options and rarely needed.
              onSubmitted: (_) => _passFocus.requestFocus(),
              onChanged: _urlChanged,
            ),
          ),
          ?_notSameNetworkLink(),
          ?_remoteHttpAdvice(copy, tokens),
          ?_cleartextWarning(copy, tokens),
          // What the check (or the save) found, under the field it is
          // about.
          _verdicts(copy, tokens),
          SizedBox(height: tokens.space4),
          // Paste is the main way in for the per-run random serve password;
          // the kit field carries it beside the reveal toggle.
          KitField.secret(
            label: _needsPassword
                ? copy.e7SetupReenterPassword
                : copy.e7SetupServerPassword,
            controller: _pass,
            focusNode: _passFocus,
            fieldKey: const ValueKey('server-password-field'),
            revealKey: const ValueKey('server-password-visibility'),
            pasteKey: const ValueKey('server-password-paste'),
            replaceKey: const ValueKey('server-password-replace'),
            helper: _needsPassword
                ? copy.e7SetupEmptyPasswordHint
                : copy.e7SetupPasswordStartupHint,
            saved: _heldPassword != null,
            onReplace: () => setState(() {
              _heldPassword = null;
              _invalidateProbe();
            }),
            autofocus: _needsPassword || widget.focusPassword,
            enabled: !_submitting,
            disabledReason: _submitting ? copy.e7SetupSaving : null,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _save(),
            onChanged: _passwordChanged,
          ),
          SizedBox(height: tokens.space1),
          _testAction(copy),
        ],
      ),
    );
    if (!_foldsManualAddress) return fields;
    return KitExpandRow(
      key: ValueKey('server-manual-address-$_manualFold'),
      headerKey: const ValueKey('server-manual-address'),
      title: copy.addServerManual,
      initiallyExpanded: _manualForcedOpen,
      children: [
        fields,
        SizedBox(height: tokens.space2),
      ],
    );
  }

  /// Test connection, a tertiary at most (§2): Save & connect checks by
  /// itself, this is for checking before deciding.
  Widget _testAction(AppLocalizations copy) => KitInset(
    child: KitButton.tertiary(
      key: const ValueKey('test-server-connection'),
      onPressed: _testing || _submitting ? null : _testConnection,
      icon: AppIconography.networkCheck,
      label: _testing ? copy.e7SetupTesting : copy.e7SetupTestConnection,
    ),
  );

  /// Each verdict unfolds in when it arrives and folds away when it goes
  /// (design standard §10); the slots stay in place so a new check that
  /// clears the old verdict and brings the next one moves smoothly.
  Widget _slot(KitTokens tokens, Widget? child) => KitReveal(
    child: child == null
        ? null
        : Padding(
            padding: EdgeInsetsDirectional.only(top: tokens.space2),
            child: child,
          ),
  );

  /// A check that has not answered for a while offers to stop, so a server
  /// that never answers never holds the person. At the head of the form,
  /// under the drawing that shows the check.
  Widget _progress(AppLocalizations copy, KitTokens tokens) => _Rails(
    child: _slot(
      tokens,
      !_slowCheck
          ? null
          : KitNotice(
              key: const ValueKey('server-check-slow'),
              tone: AppStatusTone.neutral,
              message: copy.addServerCheckSlow(_host),
              actions: [
                KitAction(
                  key: const ValueKey('server-check-cancel'),
                  label: copy.addServerCheckCancel,
                  onPressed: _cancelCheck,
                ),
              ],
            ),
    ),
  );

  /// The verdicts, under the address field they are about: a save that
  /// failed, and what the check found. Plain words; the technical text is
  /// folded under Details.
  Widget _verdicts(AppLocalizations copy, KitTokens tokens) {
    final failure = _submitFailure;
    final result = _isCodex ? null : _testResult;
    final codex = _isCodex ? _codexTestResult : null;
    return Column(
      key: _verdictKey,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _slot(
          tokens,
          failure == null
              ? null
              : _WithDetails(
                  details: _submitDetails,
                  detailsKey: const ValueKey('server-save-failure-details'),
                  child: KitNotice(
                    key: const ValueKey('server-save-failure'),
                    tone: AppStatusTone.failure,
                    message: failure,
                  ),
                ),
        ),
        _slot(tokens, codex == null ? null : _buildCodexProbeVerdict(copy)),
        _slot(
          tokens,
          result == null
              ? null
              : KeyedSubtree(
                  key: const ValueKey('server-probe-verdict'),
                  child: _ProbeVerdict(
                    result: result,
                    saveAnyway: !result.ok && _verdictFromSave
                        ? _saveAnywayAction(copy)
                        : null,
                  ),
                ),
        ),
      ],
    );
  }

  /// Name, username and the AI Team host: what most people never change,
  /// out of the way. The name comes from the address and the username is
  /// "opencode" unless the server was started with another.
  Widget _moreOptions(AppLocalizations copy, KitTokens tokens) => KitExpandRow(
    key: const ValueKey('server-editor-more-options'),
    headerKey: const ValueKey('server-editor-more-options-header'),
    title: copy.serverEditorMoreOptions,
    initiallyExpanded: _moreOptionsOpen,
    children: [
      Padding(
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.space1,
          tokens.gutter,
          tokens.space2,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitField(
              label: copy.connectionDisplayName,
              controller: _name,
              focusNode: _nameFocus,
              fieldKey: const ValueKey('server-name-field'),
              hint: copy.connectionDisplayNameHint,
              enabled: !_submitting,
              disabledReason: _submitting ? copy.e7SetupSaving : null,
              textInputAction: TextInputAction.next,
              onSubmitted: (_) => _userFocus.requestFocus(),
              onChanged: (_) => _fieldChanged(),
            ),
            SizedBox(height: tokens.space4),
            KitField(
              label: copy.e7SetupUsername,
              kind: KitFieldKind.mono,
              controller: _user,
              focusNode: _userFocus,
              fieldKey: const ValueKey('server-username-field'),
              hint: 'opencode',
              enabled: !_submitting,
              disabledReason: _submitting ? copy.e7SetupSaving : null,
              textInputAction: TextInputAction.next,
              onSubmitted: (_) => _passFocus.requestFocus(),
              onChanged: (_) => _fieldChanged(),
            ),
            SizedBox(height: tokens.space5),
            Semantics(
              header: true,
              child: KitText(
                copy.teamUiEditorTitle,
                key: const ValueKey('server-editor-team-section'),
                role: KitTextRole.label,
              ),
            ),
            SizedBox(height: tokens.space1),
            KitText(
              _orchestration == null
                  ? copy.teamUiEditorBody
                  : copy.teamUiEditorConfigured(_orchestration!.url),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
            KitInset(
              child: Wrap(
                spacing: tokens.space1,
                children: [
                  KitButton.tertiary(
                    key: const ValueKey('server-editor-team-learn'),
                    onPressed: _submitting
                        ? null
                        : () => showTeamHostGuideSheet(
                            context,
                            enterAddress: _addTeamHost,
                          ),
                    label: copy.teamUiLearnHow,
                  ),
                  KitButton.tertiary(
                    key: const ValueKey('server-editor-team-add'),
                    onPressed: _submitting ? null : _addTeamHost,
                    label: _orchestration == null
                        ? copy.teamUiAddManually
                        : copy.teamUiChange,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ],
  );

  /// Closes the editor (nothing is saved yet), then takes the other way.
  VoidCallback? _leaveFor(VoidCallback? way) {
    if (way == null) return null;
    return () {
      if (_submitting) return;
      Navigator.of(context).pop();
      way();
    };
  }

  /// The kind's own name, the connect step's title: the person sees what
  /// they chose on the step that asks for its address.
  String _kindTitle(AppLocalizations copy) => switch (_backend) {
    ServerBackend.openCode => copy.firstRunAgentOpenCode,
    ServerBackend.paseo => copy.firstRunPaseoTitle,
    ServerBackend.codex => copy.firstRunAgentCodex,
  };

  /// Where Add server is, as the kit's staged progress: "Step 2 of 4 ·
  /// Pair or enter the address". The check is a step of its own while it
  /// runs, and the step before it again when it did not answer.
  Widget _stepLine(AppLocalizations copy, KitTokens tokens) {
    final of = _tailscale ? 5 : 4;
    final connect = _tailscale ? 3 : 2;
    final checking = _testing || _pairing || _submitting;
    final (step, label) = switch (_step) {
      _AddStep.kind => (1, copy.addServerStepKind),
      _AddStep.tailscale => (2, copy.addServerStepTailscale),
      _AddStep.connect when checking => (connect + 1, copy.addServerStepCheck),
      _AddStep.connect => (
        connect,
        _isCodex ? copy.addServerStepAddress : copy.addServerStepPair,
      ),
      _AddStep.ready => (of, copy.addServerStepReady),
    };
    return _Rails(
      key: _stepLineKey,
      child: KitProgressView(
        key: ValueKey('server-add-steps-$of'),
        progress: KitProgress.staged(
          key: const ValueKey('server-add-step-bar'),
          step: step,
          of: of,
          label: label,
          // Ready is the last step done, not begun: the bar is full.
          stepValue: _step == _AddStep.ready ? 1 : null,
          semanticsLabel: copy.addServerStepsLabel,
        ),
      ),
    );
  }

  /// Step 1: what runs on the computer, one choice that acts on tap, and
  /// the other ways in under it (this phone, Tailscale, outside agents).
  List<Widget> _kindStep(AppLocalizations copy, KitTokens tokens) => [
    SizedBox(height: tokens.space2),
    // The agents are not alternatives: one computer runs all of them at
    // once. This step only picks where to begin.
    _Rails(
      child: KitText(
        copy.firstRunAgentsSideBySide,
        key: const ValueKey('agent-choice-side-by-side'),
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
    ),
    _BackendChoice(
      key: const ValueKey('server-kind-step'),
      selected: null,
      onSelected: _chooseKind,
    ),
    _OtherWays(
      onPhoneSetup: _leaveFor(widget.onPhoneSetup),
      onTailscale: platformCapabilities.supportsTailscaleHandoff
          ? _chooseTailscale
          : null,
      onExternalAgents: _leaveFor(widget.onExternalAgents),
    ),
  ];

  /// Tailscale as a step: the phone's side (the app, the VPN), then the
  /// address and the server's own sign-in on the connect step after it.
  List<Widget> _tailscaleStep(AppLocalizations copy, KitTokens tokens) => [
    SizedBox(height: tokens.space2),
    _Rails(
      child: Column(
        key: const ValueKey('server-tailscale-step'),
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitText(copy.tailscaleIntro, tone: KitTextTone.secondary),
          SizedBox(height: tokens.sectionGap),
          const TailscalePhoneSteps(),
          SizedBox(height: tokens.sectionGap),
          const TailscaleHelpFold(),
        ],
      ),
    ),
  ];

  /// The finished flow: the drawing linked, what it reached, and the one
  /// way on.
  List<Widget> _readyStep(AppLocalizations copy, KitTokens tokens) {
    final profile = _readyProfile!;
    return [
      _linkMoment(copy, tokens),
      _Rails(
        child: Column(
          key: const ValueKey('server-ready-step'),
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              header: true,
              child: KitText(
                copy.addServerReadyTitle(profile.name),
                role: KitTextRole.headline,
              ),
            ),
            SizedBox(height: tokens.space2),
            KitText(copy.addServerReadyBody, tone: KitTextTone.secondary),
          ],
        ),
      ),
    ];
  }

  /// The address or the pairing code, the check and its verdicts: the
  /// connect step of Add server, and the whole form everywhere else.
  List<Widget> _connectStep(
    AppLocalizations copy,
    KitTokens tokens, {
    required bool isNew,
    required bool showsCommand,
  }) => [
    if (_tailscale)
      _Rails(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(copy.tailscaleEditorDetail),
            KitInset(
              child: KitButton.tertiary(
                // On Add server the Tailscale step is one
                // step back; a saved server opens the page.
                onPressed: _stepped
                    ? () => _goTo(_AddStep.tailscale)
                    : _tailscaleHelp,
                icon: AppIconography.secureNetwork,
                label: copy.tailscaleHelp,
              ),
            ),
            if (_testResult?.ok == false || _submitFailure != null)
              KitText(copy.tailscaleRecovery),
          ],
        ),
      ),
    // The connection's moment at the head of the form: the
    // drawing, its line, and a slow check's offer to stop.
    // Every check and save starts by scrolling back to it; the
    // verdict itself is under the address field.
    Column(
      key: _statusKey,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [if (isNew) _linkMoment(copy, tokens), _progress(copy, tokens)],
    ),
    if (showsCommand) ...[
      SizedBox(height: tokens.space1),
      _ComputerCommand(
        key: ValueKey('connect-computer-command-${_backend.name}'),
        backend: _backend,
        // Pairing, the main path, right under the command
        // that prints the code.
        below: _isCodex
            ? null
            : _PairingActions(
                // The command is on screen above, with its
                // copy button; only the next move is left
                // to say.
                instructions: platformCapabilities.supportsQrPairing
                    ? copy.firstRunPairingNextScan
                    : copy.firstRunPairingNextPaste,
                busy: _pairing,
                notice: _pairingNotice,
                failure: _pairingFailure,
                onPaste: _pairing || _submitting
                    ? null
                    : () => unawaited(_pastePairing()),
                // Rendered only where a camera path exists.
                // Desktop gets no affordance at all rather
                // than one that opens and fails.
                onScan: platformCapabilities.supportsQrPairing
                    ? () => unawaited(_scanPairing())
                    : null,
              ),
      ),
    ],
    if (_secureStorageNotice case final notice?)
      _Rails(
        child: KitNotice(
          key: const ValueKey('server-secure-storage-notice'),
          tone: AppStatusTone.neutral,
          message: notice,
        ),
      ),
    // Unfolds when a check finds the password missing,
    // folds away once it is typed (design standard §10).
    KitReveal(
      child: !_needsPassword
          ? null
          : _Rails(
              child: Semantics(
                container: true,
                liveRegion: true,
                excludeSemantics: true,
                label: copy.e7SetupMissingPasswordLong,
                child: KitNotice(
                  tone: AppStatusTone.neutral,
                  icon: AppIconography.locked,
                  liveRegion: false,
                  message: copy.e7SetupMissingPasswordShort,
                ),
              ),
            ),
    ),
    if (_isCodex) ...[
      _Rails(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: _buildCodexFields(copy, tokens),
        ),
      ),
      _Rails(
        child: KitText(
          _isPaseo ? copy.paseoSetupNotice : copy.codexApprovalRecoveryNotice,
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
      ),
      _Rails(child: _testAction(copy)),
    ] else ...[
      if (_foldsManualAddress) SizedBox(height: tokens.space2),
      _manualAddress(copy, tokens),
      if (!_tailscale && !showsCommand)
        // Editing a saved server: pairing again (a rotated
        // password) stays one tap away, under the fields
        // the person came to change.
        _Rails(
          child: _PairingActions(
            compact: true,
            busy: _pairing,
            notice: _pairingNotice,
            failure: _pairingFailure,
            onPaste: _pairing || _submitting
                ? null
                : () => unawaited(_pastePairing()),
            onScan: platformCapabilities.supportsQrPairing
                ? () => unawaited(_scanPairing())
                : null,
          ),
        ),
      SizedBox(height: tokens.space1),
      _moreOptions(copy, tokens),
    ],
  ];

  @override
  Widget build(BuildContext context) {
    final copy = _connectionL10n(context);
    final tokens = KitTokens.of(context);
    final isNew = widget.existing == null;
    final title = switch (_step) {
      _AddStep.kind => copy.e7SetupAddServer,
      _AddStep.tailscale => copy.tailscaleTitle,
      _ when _stepped => _tailscale ? copy.tailscaleTitle : _kindTitle(copy),
      _ when isNew =>
        widget.openCode2Intent && !_isCodex
            ? copy.oc2DiscoveryEditorTitle
            : copy.e7SetupAddServer,
      _ when _needsCodexToken => copy.codexTokenReentry,
      _ when _needsPassword => copy.e7SetupReenterPassword,
      _ => copy.e7SetupEditServer,
    };
    // A new server starts from the command to run on the computer, for the
    // kind chosen (the phone's own server and Tailscale have their own).
    final showsCommand = isNew && !_tailscale && widget.initialUrl == null;
    final back = _previousStep != null;
    final Widget? bottom = switch (_step) {
      _AddStep.kind => null,
      _AddStep.tailscale => KitButton.primary(
        key: const ValueKey('server-tailscale-continue'),
        onPressed: () => _goTo(_AddStep.connect),
        icon: AppIconography.forward,
        label: copy.addServerTailscaleNext,
      ),
      _AddStep.ready => KitButton.primary(
        key: const ValueKey('server-ready-open'),
        onPressed: _exit,
        label: copy.addServerReadyOpen(_readyProfile!.name),
      ),
      // The one primary (§2), pinned below the form and lifted above the
      // keyboard. Its tap checks the connection, saves and connects; the
      // spinner is that tap in flight, the drawing and its line say which
      // step.
      _AddStep.connect => KitButton.primary(
        key: const ValueKey('save-server-profile'),
        onPressed: _submitting || _testing ? null : _save,
        working: _submitting || (_testing && _checkingForSave),
        label: _testing && _checkingForSave
            ? copy.addServerChecking
            : _submitting
            ? copy.e7SetupSaving
            : _connectsOnSave
            ? copy.onboardingSaveConnect
            : copy.onboardingSaveChanges,
      ),
    };
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _exit();
      },
      child: KitScreen(
        key: const ValueKey('server-profile-editor'),
        width: KitScreenWidth.reading,
        topBar: KitTopBar(
          title: title,
          // Back steps within Add server; Close leaves (asking first when
          // something is unsaved). Nothing closes while a save is in flight.
          exit: back ? KitTopBarExit.back : KitTopBarExit.close,
          exitKey: ValueKey(
            back ? 'server-editor-back' : 'server-editor-close',
          ),
          onExit: _exit,
        ),
        bottom: bottom,
        body: AbsorbPointer(
          absorbing: _submitting,
          // One box, not a lazy list: every field exists while the form is
          // open, so focus can move to one that is scrolled away.
          child: CustomScrollView(
            key: const ValueKey('server-profile-fields'),
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            slivers: [
              SliverPadding(
                padding: EdgeInsetsDirectional.only(
                  top: tokens.space1,
                  bottom: KitScreen.endPadding(context),
                ),
                sliver: SliverToBoxAdapter(
                  // Each step replaces the last in place.
                  child: Column(
                    key: ValueKey('server-add-step-${_step.name}'),
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (_stepped) _stepLine(copy, tokens),
                      ...switch (_step) {
                        _AddStep.kind => _kindStep(copy, tokens),
                        _AddStep.tailscale => _tailscaleStep(copy, tokens),
                        _AddStep.ready => _readyStep(copy, tokens),
                        _AddStep.connect => _connectStep(
                          copy,
                          tokens,
                          isNew: isNew,
                          showsCommand: showsCommand,
                        ),
                      },
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Where Add server is (P3.9): what runs there, Tailscale when that is the
/// way, the address or pairing code (whose check is a step while it runs),
/// and the ready moment.
enum _AddStep { kind, tailscale, connect, ready }

/// How long a connection check or a pairing runs before it offers Cancel.
@visibleForTesting
const slowCheckAfter = Duration(seconds: 8);

/// The one command that starts the chosen agent, with a copy button, and the
/// rest of the setup guide's commands behind "Show the commands" (UX plan
/// 5.9). First run is the moment the person is at their computer with a
/// terminal open; the guide screen stays in Settings → Help for later.
class _ComputerCommand extends StatelessWidget {
  const _ComputerCommand({super.key, required this.backend, this.below});

  final ServerBackend backend;

  /// What to do with what the command prints (OpenCode's pairing buttons),
  /// between the command and the rarer commands.
  final Widget? below;

  @override
  Widget build(BuildContext context) {
    final copy = _connectionL10n(context);
    final tokens = KitTokens.of(context);
    Widget caption(String text) => Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space2),
      child: KitText(
        text,
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
    );
    Widget command(String text, {Key? key, String? caption}) => Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space2),
      child: KitCodeBlock(
        key: key,
        text: text,
        kind: KitCodeKind.command,
        caption: caption,
        copyLabel: copy.handoffCopyCommand,
      ),
    );
    return Column(
      key: const ValueKey('connect-computer-command'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _Rails(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // The block's header says what to do with it, beside its copy
              // button.
              command(
                SetupCommands.startFor(backend),
                key: const ValueKey('connect-command'),
                caption: copy.firstRunRunOnComputer,
              ),
              if (below case final below?) ...[
                SizedBox(height: tokens.space3),
                below,
              ],
            ],
          ),
        ),
        KitExpandRow(
          key: const ValueKey('connect-show-commands'),
          title: copy.firstRunShowCommands,
          children: [
            _Rails(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: switch (backend) {
                  ServerBackend.openCode => [
                    caption(copy.e7SharedServersStartedWithOpencodeServeDoNot),
                    command(SetupCommands.legacyServe),
                    caption(copy.e7SharedThenAddTheServerManuallyWithUsername),
                  ],
                  ServerBackend.paseo => [
                    caption(copy.firstRunCommandsPaseoNetwork),
                    command(SetupCommands.paseoStartPrivateNetwork),
                  ],
                  ServerBackend.codex => [
                    caption(copy.firstRunCommandsCodexToken),
                    command(SetupCommands.codexToken),
                    caption(copy.firstRunCommandsCodexUsb),
                    command(SetupCommands.codexUsb),
                  ],
                },
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// Pairing, the main way to add an OpenCode server: the command on the
/// computer prints a code (a QR and a line to copy) carrying the address,
/// the username and the per-run password together, so scanning or pasting
/// it is strictly less work than typing them.
///
/// On a new server it is two equal buttons, Scan code and Paste code (just
/// Paste pairing code where there is no camera), under the line that says
/// the next move. Editing a saved server, it is two text buttons under the
/// fields ([compact]): pairing again after the password rotated.
class _PairingActions extends StatelessWidget {
  const _PairingActions({
    this.instructions,
    this.compact = false,
    required this.busy,
    required this.notice,
    required this.failure,
    required this.onPaste,
    required this.onScan,
  });

  /// The next move, under the command shown above with its copy button.
  final String? instructions;
  final bool compact;
  final bool busy;
  final String? notice;
  final String? failure;
  final VoidCallback? onPaste;

  /// Null wherever there is no camera path — desktop, and anywhere else
  /// `supportsQrPairing` says no. The button is then not built at all, so no
  /// camera code is reachable and nothing offers what it cannot do.
  final VoidCallback? onScan;

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final notice = this.notice;
    final failure = this.failure;
    final scan = onScan;
    final Widget buttons;
    if (compact) {
      buttons = KitInset(
        child: Wrap(
          spacing: tokens.space1,
          children: [
            KitButton.tertiary(
              key: const ValueKey('server-pairing-paste'),
              onPressed: busy ? null : onPaste,
              icon: AppIconography.paste,
              label: busy ? copy.e7SetupPairing : copy.e7SetupPastePairing,
            ),
            if (scan != null)
              KitButton.tertiary(
                key: const ValueKey('server-pairing-scan'),
                onPressed: busy ? null : scan,
                icon: AppIconography.qrCode,
                label: copy.addServerScan,
              ),
          ],
        ),
      );
    } else {
      // The spinner is only the code being checked; the drawing above and
      // the notice below say what was found.
      final paste = KitButton.secondary(
        key: const ValueKey('server-pairing-paste'),
        onPressed: onPaste,
        working: busy,
        icon: AppIconography.paste,
        maxLines: 1,
        label: scan == null ? copy.e7SetupPastePairing : copy.addServerPaste,
      );
      buttons = scan == null
          ? paste
          : Row(
              children: [
                Expanded(
                  child: KitButton.secondary(
                    key: const ValueKey('server-pairing-scan'),
                    onPressed: busy ? null : scan,
                    icon: AppIconography.qrCode,
                    maxLines: 1,
                    label: copy.addServerScan,
                  ),
                ),
                SizedBox(width: tokens.space3),
                Expanded(child: paste),
              ],
            );
    }
    return Column(
      key: const ValueKey('server-pairing-actions'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (instructions case final line?) ...[
          KitText(
            line,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
          SizedBox(height: tokens.space3),
        ],
        buttons,
        // What the code said unfolds under the buttons and folds away when
        // the next try starts (design standard §10).
        KitReveal(
          child: notice == null
              ? null
              : Padding(
                  padding: EdgeInsetsDirectional.only(top: tokens.space2),
                  child: KitNotice(
                    key: const ValueKey('server-pairing-notice'),
                    tone: AppStatusTone.ok,
                    message: notice,
                  ),
                ),
        ),
        KitReveal(
          child: failure == null
              ? null
              : Padding(
                  padding: EdgeInsetsDirectional.only(top: tokens.space2),
                  child: KitNotice(
                    key: const ValueKey('server-pairing-failure'),
                    tone: AppStatusTone.failure,
                    message: setupUiMessage(copy, failure),
                  ),
                ),
        ),
      ],
    );
  }
}

/// What a connection check found (design standard §3, a notice on the form's
/// rails, never a filled block): which OpenCode answered and what to do next,
/// or why it did not, with the setup guide when nothing seems to be there
/// and, after Save & connect, "Save anyway".
class _ProbeVerdict extends StatelessWidget {
  const _ProbeVerdict({required this.result, this.saveAnyway});

  final ServerProbeResult result;
  final KitAction? saveAnyway;

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    if (result.ok) {
      final version = result.version ?? copy.e7SetupUnknownVersion;
      return KitNotice(
        key: const ValueKey('server-test-success'),
        tone: AppStatusTone.ok,
        title: result.flavor == ServerFlavor.v2
            ? copy.e7SetupProbeV2(version)
            : copy.e7SetupProbeV1(version),
        message: copy.e7SetupSaveToFinish,
        notes: [if (result.flavor == ServerFlavor.v1) copy.e7SetupV1Limited],
      );
    }
    // A check that failed before the server could answer carries the raw
    // error (a socket or TLS message): plain words lead, the error waits
    // under Details.
    final raw = _rawCheckError(result.message!);
    return _WithDetails(
      details: raw,
      detailsKey: const ValueKey('server-test-failure-details'),
      child: KitNotice(
        key: const ValueKey('server-test-failure'),
        tone: AppStatusTone.failure,
        // A missing password answers 401 on OpenCode 1 and 2 alike; which
        // one it is shows once the password is in.
        title: result.flavor == ServerFlavor.v2 && !result.needsPassword
            ? copy.e7SetupIsV2
            : null,
        message: raw != null
            ? copy.addServerCheckFailedPlain
            : setupUiMessage(copy, result.message!),
        notes: [if (result.suggestsMissingServer) copy.e7SetupNoServerGuide],
        actions: [
          if (result.suggestsMissingServer)
            KitAction(
              key: const ValueKey('server-test-guide'),
              label: copy.e7SetupOpenSetupGuide,
              onPressed: () => Navigator.pushNamed(context, '/guide'),
            ),
          ?saveAnyway,
        ],
      ),
    );
  }
}

/// The raw error inside a probe's "Connection test failed: …" message, or
/// null for the probe's own plain verdicts.
String? _rawCheckError(String message) {
  const prefix = 'Connection test failed: ';
  if (!message.startsWith(prefix)) return null;
  final raw = message.substring(prefix.length).trim();
  return raw.isEmpty ? null : raw;
}

/// A verdict with its technical text folded under Details right below it
/// (no raw errors as copy: the words lead, the text waits, redacted).
class _WithDetails extends StatelessWidget {
  const _WithDetails({
    required this.child,
    required this.details,
    required this.detailsKey,
  });

  final Widget child;
  final String? details;
  final Key detailsKey;

  @override
  Widget build(BuildContext context) {
    final text = details;
    if (text == null || text.isEmpty) return child;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        child,
        KitDetailsFold(key: detailsKey, text: text),
      ],
    );
  }
}

/// Add server's first step (ledger row 15, P3.9): what runs on the
/// computer, as the kit's single choice list (KIT-25), a row each with a
/// line saying what it is. It acts on tap: the answer is the next step.
class _BackendChoice extends StatelessWidget {
  const _BackendChoice({
    super.key,
    required this.selected,
    required this.onSelected,
  });

  final ServerBackend? selected;
  final ValueChanged<ServerBackend> onSelected;

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    return Padding(
      key: const ValueKey('server-backend-selector'),
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        top: tokens.space3,
        end: tokens.gutter,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitChoiceList<ServerBackend>.single(
            semanticsLabel: copy.addServerConnectTo,
            selected: selected,
            onSelected: onSelected,
            choices: [
              KitChoice(
                key: const ValueKey('server-backend-opencode'),
                value: ServerBackend.openCode,
                leading: KitRow.icon(context, AppIconography.computer),
                title: copy.addServerTypeOpenCode,
                supporting: copy.addServerTypeOpenCodeDetail,
              ),
              KitChoice(
                key: const ValueKey('server-backend-codex'),
                value: ServerBackend.codex,
                leading: KitRow.icon(context, AppIconography.code),
                title: copy.addServerTypeCodex,
                supporting: copy.addServerTypeCodexDetail,
              ),
              KitChoice(
                key: const ValueKey('server-backend-paseo'),
                value: ServerBackend.paseo,
                leading: KitRow.icon(context, AppIconography.agent),
                title: copy.addServerTypePaseo,
                supporting: copy.addServerTypePaseoDetail,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Add server's other ways in, one row each (R3: they live here, not as a
/// second panel on the servers list): this phone, Tailscale and external
/// agents. This phone and external agents close the form and open their own
/// setup; Tailscale is the flow's next step. Null hides a row.
class _OtherWays extends StatelessWidget {
  const _OtherWays({
    required this.onPhoneSetup,
    required this.onTailscale,
    required this.onExternalAgents,
  });

  final VoidCallback? onPhoneSetup;
  final VoidCallback? onTailscale;
  final VoidCallback? onExternalAgents;

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final phone = onPhoneSetup;
    final tailscale = onTailscale;
    final external = onExternalAgents;
    if (phone == null && tailscale == null && external == null) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.sectionGap),
      child: KitRowGroup(
        key: const ValueKey('server-editor-other-ways'),
        label: copy.serversAddOtherWays,
        children: [
          if (phone != null)
            _PhoneSetupEntry(
              key: const ValueKey('quick-add-phone-card'),
              onTap: phone,
            ),
          if (tailscale != null)
            KitRow(
              key: const ValueKey('welcome-tailscale-card'),
              leading: KitRow.icon(context, AppIconography.secureNetwork),
              title: copy.tailscaleTitle,
              supporting: TextSpan(text: copy.onboardingPrivateNetwork),
              supportingMaxLines: 2,
              trailing: const KitChevron(),
              onTap: tailscale,
            ),
          if (external != null)
            KitRow(
              key: const ValueKey('server-editor-external-agents'),
              leading: KitRow.icon(context, AppIconography.network),
              title: copy.a2aTitle,
              trailing: const KitChevron(),
              onTap: external,
            ),
        ],
      ),
    );
  }
}
