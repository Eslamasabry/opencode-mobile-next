import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../builtin/setup/components.dart' show SetupComponentIds;
import '../../builtin/setup/phone_setup.dart';
import '../../builtin/setup/setup_contract.dart';
import '../../domain/termux_migration_service.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/phone_host.dart' show PhoneHostKind;
import '../../state/profiles.dart';
import '../../state/termux_migration_owner.dart';
import '../../termux/bridge.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../widgets/phone_server_card.dart' show formatPhoneStorage;
import 'library_screen.dart' show IntegrationsScreen, IntegrationsMode;
import 'phone_setup/phone_setup_routes.dart';
import 'phone_setup/phone_setup_selection.dart' show joinSetupNames;
import 'this_phone_screen.dart' show openThisPhone;

/// Opens the move from Termux to the in-app server for [source], the saved
/// Termux server.
Future<void> openTermuxMigration(
  BuildContext context, {
  required ServerProfile source,
}) => pushKitPage<void>(
  context,
  (_) => TermuxMigrationScreen(source: source),
  settings: const RouteSettings(name: termuxMigrationRouteName),
);

const termuxMigrationRouteName = 'termux-migration';

/// Sets up the in-app server for a move (the backend's setup v2 hand-off):
/// the required parts through the normal setup engine, with OpenCode of the
/// Termux server's generation, then setup's own progress screen, which
/// closes itself when the job finishes. A job already running is shown, not
/// started twice. Without an engine, phone setup's first screen.
Future<void> setUpBuiltinForMigration(
  BuildContext context,
  TermuxRuntime runtime,
) async {
  final SetupEngine engine;
  try {
    engine = PhoneSetup.engine;
  } on StateError {
    await openPhoneSetupStart(context);
    return;
  }
  if (engine.progress.value.state != SetupState.running) {
    await engine.run(
      {
        for (final component in engine.registry)
          if (component.required) component.id,
      },
      params: {
        SetupComponentIds.openCode: {'runtime': runtime.wireName},
      },
    );
  }
  if (context.mounted) await openPhoneSetupProgress(context);
}

/// The plain words for a migration failure (the backend's code table):
/// what failed and the way forward. Never the backend's own text.
String migrationFailureWords(
  AppLocalizations l10n,
  TermuxMigrationFailure code,
) => switch (code) {
  TermuxMigrationFailure.unavailable => l10n.migrationTermuxUnavailable,
  TermuxMigrationFailure.sourceChanged => l10n.migrationSourceChanged,
  TermuxMigrationFailure.sourceBusy => l10n.migrationSourceBusy,
  TermuxMigrationFailure.unsupportedEntry => l10n.migrationUnsupportedFiles,
  TermuxMigrationFailure.linkedFileUnreadable =>
    l10n.migrationLinkedFileUnreadable,
  TermuxMigrationFailure.tooLarge => l10n.migrationTooLarge,
  TermuxMigrationFailure.invalidArchive ||
  TermuxMigrationFailure.checksumMismatch => l10n.migrationVerificationFailed,
  TermuxMigrationFailure.destinationConflict =>
    l10n.migrationDestinationChanged,
  TermuxMigrationFailure.storage => l10n.migrationStorageFailed,
  TermuxMigrationFailure.timedOut => l10n.migrationTimedOut,
  TermuxMigrationFailure.invalidSelection => l10n.migrationSelectionChanged,
  TermuxMigrationFailure.profileSwitch => l10n.migrationConnectionFailed,
  TermuxMigrationFailure.cancelled => l10n.migrationCancelled,
};

/// A second line for the failures whose fix is not in the first.
String? migrationFailureFix(
  AppLocalizations l10n,
  TermuxMigrationFailure code,
) => switch (code) {
  TermuxMigrationFailure.unsupportedEntry => l10n.migrationUnsupportedFilesFix,
  TermuxMigrationFailure.linkedFileUnreadable =>
    l10n.migrationLinkedFileUnreadableFix,
  TermuxMigrationFailure.tooLarge => l10n.migrationTooLargeFix,
  TermuxMigrationFailure.destinationConflict =>
    l10n.migrationDestinationChangedFix,
  _ => null,
};

String migrationItemLabel(AppLocalizations l10n, TermuxMigrationItem item) =>
    switch (item) {
      TermuxMigrationItem.projects => l10n.migrationItemProjects,
      TermuxMigrationItem.config => l10n.migrationItemConfig,
      TermuxMigrationItem.sessions => l10n.migrationItemSessions,
      TermuxMigrationItem.gitConfig => l10n.migrationItemGitConfig,
      TermuxMigrationItem.shellFiles => l10n.migrationItemShellFiles,
      TermuxMigrationItem.aiTeam => l10n.migrationItemAiTeam,
    };

String _itemWhat(AppLocalizations l10n, TermuxMigrationItem item) =>
    switch (item) {
      TermuxMigrationItem.projects => l10n.migrationItemProjectsWhat,
      TermuxMigrationItem.config => l10n.migrationItemConfigWhat,
      TermuxMigrationItem.sessions => l10n.migrationItemSessionsWhat,
      TermuxMigrationItem.gitConfig => l10n.migrationItemGitConfigWhat,
      TermuxMigrationItem.shellFiles => l10n.migrationItemShellFilesWhat,
      TermuxMigrationItem.aiTeam => l10n.migrationItemAiTeamWhat,
    };

IconData _itemIcon(TermuxMigrationItem item) => switch (item) {
  TermuxMigrationItem.projects => AppIconography.projects,
  TermuxMigrationItem.config => AppIconography.settings,
  TermuxMigrationItem.sessions => AppIconography.history,
  TermuxMigrationItem.gitConfig => AppIconography.branch,
  TermuxMigrationItem.shellFiles => AppIconography.terminal,
  TermuxMigrationItem.aiTeam => AppIconography.agent,
};

/// Why [size] cannot be copied, or null: Termux's own refusal, or the
/// backend's per-item limit (512 MB with its per-file overhead, 20,000
/// files), said before starting rather than after packing.
TermuxMigrationFailure? migrationItemProblem(TermuxMigrationSize size) =>
    size.problem ??
    (size.files > 20000 ||
            size.bytes + size.files * 1024 + 10240 >
                TermuxMigrationController.maxArchiveBytes
        ? TermuxMigrationFailure.tooLarge
        : null);

/// Moving from Termux to the in-app server (owner request 2026-09-28): one
/// flow from what moves to done.
///
/// The review says, item by item with measured sizes, what is copied and
/// ready to use (projects, on by default), what is kept only as a private
/// copy that nothing turns on (settings and MCP servers, conversation
/// history, Git and shell settings, AI Team; off by default), and what does
/// not move (sign-ins, keys, tools) — and that Termux stays as it is. The
/// copy then shows the real stage per item, can be stopped, and stops when
/// the app leaves the foreground (Android gives it no background time); it
/// resumes from here, also after the app was closed. Done says what to do
/// next: sign in to the AI providers again (named when known), where the
/// files went, and that removing the Termux server is the person's choice.
///
/// Every failure is the backend's fixed code in plain words with the way
/// forward; the code alone is under Details. The migration itself lives in
/// [TermuxMigrationOwner], above the routes.
class TermuxMigrationScreen extends ConsumerStatefulWidget {
  const TermuxMigrationScreen({
    super.key,
    required this.source,
    this.owner,
    this.setUpBuiltin,
    this.openProviders,
    this.openTermux,
  });

  /// The saved Termux server the files come from.
  final ServerProfile source;

  /// Null is [TermuxMigrationOwner.instance].
  final TermuxMigrationOwner? owner;

  /// Sets up the in-app server; null is [setUpBuiltinForMigration].
  final Future<void> Function(BuildContext context, TermuxRuntime runtime)?
  setUpBuiltin;

  /// Opens provider sign-in on the in-app server; null opens Providers.
  final Future<void> Function(BuildContext context)? openProviders;

  /// Opens the Termux app; null asks the Termux bridge.
  final Future<void> Function()? openTermux;

  @override
  ConsumerState<TermuxMigrationScreen> createState() =>
      _TermuxMigrationScreenState();
}

/// What the page shows before it follows the migration's own state.
enum _Entry {
  /// Finding out whether a copy was saved.
  loading,

  /// This phone cannot prepare the migration now.
  unavailable,

  /// The migration's state, as it happens.
  live,

  /// A copy was saved and did not finish (the app was closed meanwhile).
  unfinished,

  /// The move finished before.
  moved,
}

class _TermuxMigrationScreenState extends ConsumerState<TermuxMigrationScreen> {
  late final TermuxMigrationOwner _owner =
      widget.owner ?? TermuxMigrationOwner.instance;
  _Entry _entry = _Entry.loading;
  Set<TermuxMigrationItem> _selected = {};
  bool _chosen = false;
  Set<TermuxMigrationItem>? _saved;
  String? _movedJob;
  List<String> _names = const [];
  bool _namesLoaded = false;
  bool _discardFailed = false;
  bool _settingUp = false;
  bool _setupFailed = false;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  ConnectionController get _connection => ref.read(connProvider);

  String get _id => widget.source.id;

  @override
  void initState() {
    super.initState();
    _owner.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_open());
    });
  }

  @override
  void dispose() {
    _owner.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (!mounted) return;
    final snapshot = _owner.snapshot;
    final source = snapshot?.source;
    // The review starts with projects on and every private copy off.
    if (!_chosen && source != null && _owner.source == _id) {
      _selected = {
        for (final size in source.items)
          if (size.item == TermuxMigrationItem.projects &&
              migrationItemProblem(size) == null)
            size.item,
      };
    }
    setState(() {});
    if (_entry == _Entry.live &&
        !_namesLoaded &&
        _owner.source == _id &&
        snapshot?.phase == TermuxMigrationPhase.done) {
      unawaited(_loadNames());
    }
  }

  /// The providers named in the imported settings copy (offline, names only).
  Future<void> _loadNames() async {
    _namesLoaded = true;
    final names = await _owner.providerNames(_id);
    if (mounted) setState(() => _names = names);
  }

  Future<void> _open() async {
    final c = await _owner.obtain(_connection, _l10n.phoneSetupProfileName);
    if (!mounted) return;
    if (c == null) {
      setState(() => _entry = _Entry.unavailable);
      return;
    }
    // A copy for this server runs, or just ended: show it as it is.
    const ended = {
      TermuxMigrationPhase.cancelled,
      TermuxMigrationPhase.failed,
      TermuxMigrationPhase.done,
    };
    if (_owner.source == _id &&
        (_owner.busy || ended.contains(c.snapshot.phase))) {
      setState(() => _entry = _Entry.live);
      return;
    }
    // Whether the move finished is the backend's record; a record that
    // cannot be read is unknown, not "unfinished".
    TermuxMigrationCompletedJob? moved;
    try {
      moved = await _owner.completedJob(_id);
    } catch (_) {
      if (mounted) setState(() => _entry = _Entry.unavailable);
      return;
    }
    if (!mounted) return;
    final saved = await _owner.savedSelection(_id);
    if (!mounted) return;
    if (moved != null) {
      final names = await _owner.providerNames(_id);
      if (!mounted) return;
      setState(() {
        _saved = saved;
        _movedJob = moved!.jobId;
        _names = names;
        _namesLoaded = true;
        _entry = _Entry.moved;
      });
      return;
    }
    if (saved != null) {
      setState(() {
        _saved = saved;
        _entry = _Entry.unfinished;
      });
      return;
    }
    setState(() => _entry = _Entry.live);
    await _owner.check(_id, selected: _selected);
  }

  /// The AI providers the Termux server is signed in to, by name, while
  /// the app is connected to it (never their keys).
  List<String> _providerNames() {
    final connection = _connection;
    if (connection.profile?.id != _id) return const [];
    return [
      for (final provider in connection.catalog?.providers ?? const [])
        if (provider.enabled && provider.id != 'opencode') provider.name,
    ];
  }

  Future<void> _start() async {
    if (_selected.isEmpty || _owner.busy) return;
    await _owner.start(_connection, _id, _selected);
  }

  Future<void> _resume() async {
    if (_owner.busy) return;
    setState(() => _entry = _Entry.live);
    await _owner.resume(
      _connection,
      _id,
      fallback: _saved ?? _owner.items.toSet(),
    );
  }

  /// Repeats what was asked last: a look, or the copy (from where it
  /// stopped, or from the start when nothing was saved yet).
  Future<void> _retry() async {
    if (_owner.busy) return;
    if (_owner.request == null ||
        _owner.request == TermuxMigrationRequest.check) {
      await _owner.check(_id);
    } else {
      await _resume();
    }
  }

  Future<void> _check() async {
    if (_owner.busy) return;
    _chosen = true;
    await _owner.check(_id, selected: _selected);
  }

  Future<void> _discard() async {
    if (!await showKitConfirm(
          context,
          title: _l10n.migrationDiscardTitle,
          body: _l10n.migrationDiscardBody,
          confirmLabel: _l10n.migrationDiscard,
          kind: KitConfirmKind.destructive,
          icon: AppIconography.delete,
          consequenceItems: [
            KitConsequence(
              _l10n.migrationTermuxKept,
              mark: KitConsequenceMark.kept,
            ),
          ],
          sheetKey: const ValueKey('migration-discard-sheet'),
          confirmKey: const ValueKey('migration-discard-confirm'),
        ) ||
        !mounted) {
      return;
    }
    setState(() => _discardFailed = false);
    final result = await _owner.discard(_id);
    if (!mounted) return;
    if (result == null) {
      setState(() => _discardFailed = true);
      return;
    }
    // Back to the start: a fresh look, then a new choice of what to copy
    // (or, when it turned out the move had finished, where the files went).
    setState(() {
      _selected = {};
      _chosen = false;
      _saved = null;
      _movedJob = null;
      _names = const [];
      _namesLoaded = false;
      _entry = _Entry.loading;
    });
    await _open();
  }

  /// Throwing the saved copy away, offered wherever a copy is saved and not
  /// running; a failed attempt says so where the person is looking.
  KitAction _discardAction() => KitAction(
    key: const ValueKey('migration-discard'),
    label: _l10n.migrationDiscard,
    onPressed: _owner.busy ? null : () => unawaited(_discard()),
  );

  Widget? _discardNotice() => _discardFailed
      ? KitNotice(
          key: const ValueKey('migration-discard-failed'),
          tone: AppStatusTone.failure,
          message: _l10n.migrationDiscardFailed,
        )
      : null;

  Future<bool> _confirmStop() => showKitConfirm(
    context,
    title: _l10n.migrationStopTitle,
    body: _l10n.migrationStopBody,
    confirmLabel: _l10n.migrationStop,
    cancelLabel: _l10n.migrationKeepGoing,
    kind: KitConfirmKind.stop,
    icon: AppIconography.stop,
    consequenceItems: [
      KitConsequence(_l10n.migrationStopKept, mark: KitConsequenceMark.kept),
      KitConsequence(_l10n.migrationTermuxKept, mark: KitConsequenceMark.kept),
    ],
    sheetKey: const ValueKey('migration-stop-sheet'),
    confirmKey: const ValueKey('migration-stop-confirm'),
  );

  Future<void> _stop() async {
    if (!await _confirmStop() || !mounted) return;
    await _owner.cancel();
  }

  /// Back while copying: asks, stops, and leaves once the copy settled.
  Future<void> _leaveWhileCopying() async {
    if (!await _confirmStop() || !mounted) return;
    await _owner.cancel();
    await _owner.settled();
    if (mounted) Navigator.of(context).pop();
  }

  Future<void> _setUp() async {
    if (_settingUp || _owner.busy) return;
    setState(() {
      _settingUp = true;
      _setupFailed = false;
    });
    var failed = false;
    try {
      await (widget.setUpBuiltin ?? setUpBuiltinForMigration)(
        context,
        TermuxRuntime.openCode2,
      );
    } catch (_) {
      failed = true;
    }
    if (!mounted) return;
    setState(() {
      _settingUp = false;
      _setupFailed = failed;
    });
    // Setup finished or was left: a fresh look says which.
    if (!failed) await _owner.check(_id, selected: _selected);
  }

  Future<void> _openTermux() async {
    try {
      await (widget.openTermux ?? TermuxBridge.openTermux)();
    } catch (_) {
      // Opening Termux is a convenience; Try again stays.
    }
  }

  Future<void> _openProviders() async {
    final open = widget.openProviders;
    if (open != null) return open(context);
    await pushKitPage<void>(
      context,
      (_) => IntegrationsScreen(
        controller: _connection,
        mode: IntegrationsMode.providers,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final snapshot = _owner.snapshot;
    final mine = _owner.source == _id;
    final copying = mine && _owner.copying;
    final phase = _entry == _Entry.live && mine ? snapshot?.phase : null;
    final reviewing =
        phase == TermuxMigrationPhase.ready &&
        !copying &&
        snapshot?.source != null;
    return PopScope(
      canPop: !copying,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_leaveWhileCopying());
      },
      child: KitScreen(
        topBar: KitTopBar(title: l10n.migrationTitle),
        width: KitScreenWidth.reading,
        bottom: reviewing ? _startBlock(l10n) : null,
        body: KeyedSubtree(
          key: const ValueKey('termux-migration'),
          child: switch (_entry) {
            _Entry.loading => _checking(l10n),
            _Entry.unavailable => _unavailable(l10n),
            _Entry.unfinished => _unfinished(l10n),
            _Entry.moved => _done(
              l10n,
              job: _movedJob,
              items: TermuxMigrationOwner.ordered(_saved ?? const {}),
            ),
            _Entry.live => _live(l10n, snapshot, copying: copying),
          },
        ),
      ),
    );
  }

  Widget _live(
    AppLocalizations l10n,
    TermuxMigrationSnapshot? snapshot, {
    required bool copying,
  }) {
    if (snapshot == null || _owner.source != _id) return _checking(l10n);
    return switch (snapshot.phase) {
      TermuxMigrationPhase.done => _done(
        l10n,
        job: snapshot.jobId,
        items: _owner.items,
      ),
      TermuxMigrationPhase.failed => _failed(l10n, snapshot),
      TermuxMigrationPhase.cancelled => _cancelled(l10n),
      TermuxMigrationPhase.needsSpace => _needsSpace(l10n, snapshot),
      TermuxMigrationPhase.needsBuiltin => _needsBuiltin(l10n),
      TermuxMigrationPhase.termuxUnreachable => _unreachable(l10n),
      _ when copying => _progress(l10n, snapshot),
      TermuxMigrationPhase.ready when snapshot.source != null => _review(
        l10n,
        snapshot.source!,
      ),
      _ => _checking(l10n),
    };
  }

  // --- States ----------------------------------------------------------------

  Widget _checking(AppLocalizations l10n) => KitStateView(
    key: const ValueKey('migration-checking'),
    icon: AppIconography.sync,
    tone: AppStatusTone.progress,
    title: l10n.migrationChecking,
    progress: const KitProgress.waiting(),
  );

  Widget _unavailable(AppLocalizations l10n) => KitStateView(
    key: const ValueKey('migration-unavailable'),
    icon: AppIconography.warning,
    title: l10n.migrationUnavailableTitle,
    body: l10n.migrationUnavailableBody,
    primary: KitAction(
      key: const ValueKey('migration-try-again'),
      label: l10n.commonRetry,
      onPressed: () {
        setState(() => _entry = _Entry.loading);
        unawaited(_open());
      },
    ),
  );

  Widget _itemRows(List<TermuxMigrationItem> items) => KitRowGroup(
    margin: EdgeInsets.zero,
    children: [
      for (final item in items)
        KitRow(
          key: ValueKey('migration-saved-${item.name}'),
          leading: KitRow.icon(context, _itemIcon(item)),
          title: migrationItemLabel(_l10n, item),
        ),
    ],
  );

  Widget _unfinished(AppLocalizations l10n) => KitStateView(
    key: const ValueKey('migration-unfinished'),
    icon: AppIconography.restore,
    title: l10n.migrationUnfinishedTitle,
    body: l10n.migrationUnfinishedBody,
    content: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _itemRows(TermuxMigrationOwner.ordered(_saved ?? const {})),
        if (_discardNotice() case final notice?) ...[
          SizedBox(height: KitTokens.of(context).space3),
          notice,
        ],
      ],
    ),
    primary: KitAction(
      key: const ValueKey('migration-resume'),
      label: l10n.migrationResume,
      onPressed: () => unawaited(_resume()),
    ),
    tertiary: [_discardAction()],
    footer: KitText(l10n.migrationKeepOpen, role: KitTextRole.secondary),
  );

  Widget _cancelled(AppLocalizations l10n) {
    final settling = _owner.busy;
    final resumable = _owner.request != TermuxMigrationRequest.check;
    return KitStateView(
      key: const ValueKey('migration-cancelled'),
      icon: AppIconography.pause,
      tone: settling ? AppStatusTone.progress : AppStatusTone.neutral,
      title: settling ? l10n.migrationStopping : l10n.migrationCancelled,
      body: _owner.stoppedByLeaving
          ? l10n.migrationStoppedLeaving
          : l10n.migrationCancelledBody,
      bodyKey: const ValueKey('migration-cancelled-body'),
      content: _discardNotice(),
      tertiary: resumable ? [_discardAction()] : const [],
      primary: KitAction(
        key: const ValueKey('migration-resume'),
        label: resumable ? l10n.migrationResume : l10n.commonRetry,
        // Resume waits for the stopped copy to settle (the contract).
        working: settling,
        onPressed: settling ? () {} : () => unawaited(_retry()),
      ),
    );
  }

  Widget _needsSpace(AppLocalizations l10n, TermuxMigrationSnapshot snapshot) {
    final needed = KitBidi.ltr(formatPhoneStorage(snapshot.requiredBytes ?? 0));
    final free = snapshot.availableBytes;
    return KitStateView(
      key: const ValueKey('migration-needs-space'),
      icon: AppIconography.storage,
      title: l10n.migrationNeedsSpace,
      body: free == null
          ? l10n.migrationNeedsSpaceBodyUnknown(needed)
          : l10n.migrationNeedsSpaceBody(
              needed,
              KitBidi.ltr(formatPhoneStorage(free)),
            ),
      primary: KitAction(
        key: const ValueKey('migration-try-again'),
        label: l10n.commonRetry,
        onPressed: _owner.busy ? null : () => unawaited(_retry()),
      ),
      secondary: KitAction(
        key: const ValueKey('migration-choose-fewer'),
        label: l10n.migrationChooseFewer,
        onPressed: _owner.busy ? null : () => unawaited(_check()),
      ),
    );
  }

  Widget _needsBuiltin(AppLocalizations l10n) {
    return KitStateView(
      key: const ValueKey('migration-needs-builtin'),
      icon: AppIconography.phone,
      title: l10n.migrationNeedsBuiltin,
      body: l10n.migrationNeedsBuiltinBody(l10n.setupRuntimeTwo),
      primary: KitAction(
        key: const ValueKey('migration-set-up'),
        label: l10n.migrationSetUpBuiltin,
        working: _settingUp,
        onPressed: () => unawaited(_setUp()),
      ),
      content: _setupFailed
          ? KitNotice(
              key: const ValueKey('migration-setup-failed'),
              tone: AppStatusTone.failure,
              message: l10n.migrationSetupFailed,
            )
          : null,
    );
  }

  Widget _unreachable(AppLocalizations l10n) => KitStateView(
    key: const ValueKey('migration-termux-unreachable'),
    icon: AppIconography.deviceOff,
    title: l10n.migrationTermuxNotAnswering,
    body: l10n.migrationTermuxUnavailable,
    primary: KitAction(
      key: const ValueKey('migration-open-termux'),
      label: l10n.migrationOpenTermux,
      icon: AppIconography.launch,
      onPressed: () => unawaited(_openTermux()),
    ),
    secondary: KitAction(
      key: const ValueKey('migration-try-again'),
      label: l10n.commonRetry,
      onPressed: _owner.busy ? null : () => unawaited(_retry()),
    ),
  );

  Widget _failed(AppLocalizations l10n, TermuxMigrationSnapshot snapshot) {
    final code = snapshot.failure ?? TermuxMigrationFailure.storage;
    final fix = migrationFailureFix(l10n, code);
    final item = snapshot.item;
    final busy = _owner.busy;
    final KitAction? primary;
    KitAction? secondary;
    switch (code) {
      case TermuxMigrationFailure.destinationConflict:
        // Nothing to repeat: the moved files stay as the person left them.
        primary = KitAction(
          key: const ValueKey('migration-open-builtin'),
          label: l10n.migrationOpenBuiltin,
          onPressed: _openBuiltin,
        );
      case TermuxMigrationFailure.invalidSelection:
        primary = KitAction(
          key: const ValueKey('migration-resume'),
          label: l10n.migrationResume,
          onPressed: busy ? null : () => unawaited(_resume()),
        );
      case TermuxMigrationFailure.profileSwitch:
        primary = KitAction(
          key: const ValueKey('migration-try-again'),
          label: l10n.commonRetry,
          onPressed: busy ? null : () => unawaited(_retry()),
        );
        secondary = KitAction(
          key: const ValueKey('migration-open-this-phone'),
          label: l10n.migrationOpenThisPhone,
          onPressed: () =>
              unawaited(openThisPhone(context, kind: PhoneHostKind.inApp)),
        );
      default:
        primary = KitAction(
          key: const ValueKey('migration-try-again'),
          label: l10n.commonRetry,
          onPressed: busy ? null : () => unawaited(_retry()),
        );
    }
    return KitStateView(
      key: const ValueKey('migration-failed'),
      icon: AppIconography.warning,
      title: l10n.migrationFailedTitle,
      body: [migrationFailureWords(l10n, code), ?fix].join('\n\n'),
      bodyKey: const ValueKey('migration-failed-body'),
      primary: primary,
      secondary: secondary,
      content: _discardNotice(),
      tertiary: snapshot.jobId != null ? [_discardAction()] : const [],
      detailValues: [
        KitTechnicalValue(
          l10n.migrationFailureCode,
          code.name,
          key: const ValueKey('migration-failure-code'),
        ),
        if (item != null)
          KitTechnicalValue(l10n.migrationFailureItem, item.name),
      ],
    );
  }

  void _openBuiltin() => unawaited(
    Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false),
  );

  Widget _done(
    AppLocalizations l10n, {
    required String? job,
    required List<TermuxMigrationItem> items,
  }) {
    final names = _names;
    final exports = [
      for (final item in items)
        if (item != TermuxMigrationItem.projects) item,
    ];
    final folder = job == null ? null : 'termux-$job';
    final projects = items.contains(TermuxMigrationItem.projects);
    Widget icon(IconData data) => KitRow.icon(context, data);
    return KitStateView(
      key: const ValueKey('migration-done'),
      icon: AppIconography.checkCircle,
      tone: AppStatusTone.ok,
      title: l10n.migrationDoneTitle,
      body: l10n.migrationDone,
      content: KitRowGroup(
        margin: EdgeInsets.zero,
        children: [
          KitRow(
            key: const ValueKey('migration-sign-in'),
            leading: icon(AppIconography.login),
            title: l10n.migrationSignInAgain,
            titleMaxLines: 2,
            supporting: TextSpan(
              text: names.isEmpty
                  ? l10n.migrationSignInAgainAny
                  : l10n.migrationSignInAgainNamed(joinSetupNames(l10n, names)),
            ),
            supportingKey: const ValueKey('migration-sign-in-detail'),
            supportingMaxLines: 4,
            trailing: const KitChevron(),
            onTap: () => unawaited(_openProviders()),
          ),
          if (projects && folder != null)
            KitRow(
              key: const ValueKey('migration-projects-where'),
              leading: icon(AppIconography.projects),
              title: l10n.migrationProjectsWhere,
              supporting: TextSpan(
                text: l10n.migrationProjectsWhereBody(KitBidi.ltr(folder)),
              ),
              supportingMaxLines: 3,
            ),
          if (exports.isNotEmpty)
            KitRow(
              key: const ValueKey('migration-exports-where'),
              leading: icon(AppIconography.archive),
              title: l10n.migrationExportsWhere,
              supporting: TextSpan(
                text: l10n.migrationExportsWhereBody(
                  joinSetupNames(l10n, [
                    for (final item in exports) migrationItemLabel(l10n, item),
                  ]),
                ),
              ),
              supportingMaxLines: 4,
            ),
          KitRow(
            key: const ValueKey('migration-remove-termux'),
            leading: icon(AppIconography.server),
            title: l10n.migrationRemoveTermux,
            titleMaxLines: 2,
            supporting: TextSpan(text: l10n.migrationRemoveTermuxBody),
            supportingMaxLines: 4,
            trailing: const KitChevron(),
            onTap: () =>
                unawaited(Navigator.of(context).pushNamed<void>('/servers')),
          ),
        ],
      ),
      primary: KitAction(
        key: const ValueKey('migration-open-builtin'),
        label: l10n.migrationOpenBuiltin,
        onPressed: _openBuiltin,
      ),
      detailValues: [
        if (projects && folder != null)
          KitTechnicalValue(
            l10n.migrationDetailProjects,
            '/root/projects/$folder',
          ),
        if (exports.isNotEmpty && job != null)
          KitTechnicalValue(
            l10n.migrationDetailExports,
            '/root/.oc-migration-exports/$job',
          ),
      ],
    );
  }

  // --- The copy --------------------------------------------------------------

  String _stage(AppLocalizations l10n, TermuxMigrationPhase phase) =>
      switch (phase) {
        TermuxMigrationPhase.packing => l10n.migrationPacking,
        TermuxMigrationPhase.copying => l10n.migrationCopying,
        TermuxMigrationPhase.unpacking => l10n.migrationUnpacking,
        TermuxMigrationPhase.verifying => l10n.migrationVerifying,
        TermuxMigrationPhase.switching => l10n.migrationSwitching,
        _ => l10n.migrationChecking,
      };

  Widget _progress(AppLocalizations l10n, TermuxMigrationSnapshot snapshot) {
    final tokens = KitTokens.of(context);
    final items = _owner.items;
    final switching = snapshot.phase == TermuxMigrationPhase.switching;
    final current = snapshot.item;
    final at = switching
        ? items.length
        : current == null
        ? 0
        : items.indexOf(current).clamp(0, items.length);
    // Before the first item (Termux and space are checked again) nothing
    // works yet: every step waits under the stage's words.
    final started = switching || current != null;
    final stage = _stage(l10n, snapshot.phase);
    return ListView(
      key: const ValueKey('migration-progress'),
      padding: EdgeInsetsDirectional.only(
        top: tokens.space2,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: KitNotice(
            key: const ValueKey('migration-keep-open'),
            icon: AppIconography.phone,
            message: l10n.migrationKeepOpen,
            liveRegion: false,
          ),
        ),
        SizedBox(height: tokens.sectionGap),
        Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: KitChecklist(
            checklistKey: const ValueKey('migration-steps'),
            progress: KitProgress.staged(
              step: at + 1,
              of: items.length + 1,
              label: stage,
              semanticsLabel: l10n.migrationRowRunning,
            ),
            steps: [
              for (final (i, item) in items.indexed)
                KitStep(
                  key: ValueKey('migration-step-${item.name}'),
                  title: migrationItemLabel(l10n, item),
                  state: !started || i > at
                      ? KitMarkState.waiting
                      : i < at
                      ? KitMarkState.done
                      : KitMarkState.working,
                  supporting: started && i == at ? stage : null,
                ),
              KitStep(
                key: const ValueKey('migration-step-connect'),
                title: l10n.migrationStepConnect,
                state: switching ? KitMarkState.working : KitMarkState.waiting,
                supporting: switching ? stage : null,
              ),
            ],
            stop: KitAction(
              key: const ValueKey('migration-stop'),
              label: l10n.migrationStop,
              onPressed: () => unawaited(_stop()),
            ),
          ),
        ),
      ],
    );
  }

  // --- The review ------------------------------------------------------------

  Widget _startBlock(AppLocalizations l10n) {
    final short =
        _selected.isNotEmpty &&
        _owner.reviewSpace(_selected)?.sufficient == false;
    return KitActionBlock(
      primary: KitAction(
        key: const ValueKey('migration-start'),
        label: l10n.migrationStart,
        onPressed: _selected.isEmpty || short || _owner.busy
            ? null
            : () => unawaited(_start()),
        disabledReason: _selected.isEmpty
            ? l10n.migrationChooseOne
            : short
            ? l10n.migrationReviewSpaceShort
            : null,
      ),
    );
  }

  String _sizeWords(AppLocalizations l10n, TermuxMigrationSize size) =>
      l10n.migrationItemSize(
        KitBidi.ltr(formatPhoneStorage(size.bytes)),
        size.files,
      );

  Widget _itemRow(AppLocalizations l10n, TermuxMigrationSize size) {
    final item = size.item;
    final problem = migrationItemProblem(size);
    final on = _selected.contains(item);
    final fix = problem == null ? null : migrationFailureFix(l10n, problem);
    return KitSwitchRow(
      key: ValueKey('migration-item-${item.name}'),
      switchKey: ValueKey('migration-switch-${item.name}'),
      leading: KitRow.icon(context, _itemIcon(item)),
      title: migrationItemLabel(l10n, item),
      value: on && problem == null,
      supporting: problem == null ? _sizeWords(l10n, size) : null,
      // A refused item's size is unknown, never its placeholder zero; one
      // over the limit says its measured size.
      disabledReason: problem == null
          ? null
          : '${migrationFailureWords(l10n, problem)} '
                '${size.problem == null ? _sizeWords(l10n, size) : l10n.migrationSizeUnknown}.',
      below: KitText(
        fix ?? _itemWhat(l10n, item),
        role: KitTextRole.secondary,
        maxLines: 4,
      ),
      onChanged: problem == null
          ? (value) => setState(() {
              _chosen = true;
              if (value) {
                _selected.add(item);
              } else {
                _selected.remove(item);
              }
            })
          : null,
    );
  }

  Widget _review(AppLocalizations l10n, TermuxMigrationSource source) {
    final tokens = KitTokens.of(context);
    final projects = [
      for (final size in source.items)
        if (size.disposition == TermuxMigrationDisposition.migrate) size,
    ];
    final exports = [
      for (final item in TermuxMigrationItem.values)
        for (final size in source.items)
          if (size.item == item &&
              size.disposition == TermuxMigrationDisposition.privateExport)
            size,
    ];
    final chosen = [
      for (final size in source.items)
        if (_selected.contains(size.item) && migrationItemProblem(size) == null)
          size,
    ];
    final names = _providerNames();
    final space = chosen.isEmpty ? null : _owner.reviewSpace(_selected);
    return ListView(
      key: const ValueKey('migration-review'),
      padding: EdgeInsetsDirectional.only(
        top: tokens.space2,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: KitText(l10n.migrationReviewIntro, role: KitTextRole.body),
        ),
        if (projects.isNotEmpty)
          KitRowGroup(
            key: const ValueKey('migration-moves'),
            label: l10n.migrationGroupMoves,
            children: [for (final size in projects) _itemRow(l10n, size)],
          ),
        if (exports.isNotEmpty) ...[
          KitRowGroup(
            key: const ValueKey('migration-exports'),
            label: l10n.migrationGroupExports,
            children: [for (final size in exports) _itemRow(l10n, size)],
          ),
          KitGroupNote(message: l10n.migrationExportsNote),
        ],
        Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              KitSectionLabel(
                l10n.migrationGroupNotMoved,
                margin: EdgeInsets.zero,
              ),
              KitConsequences(
                key: const ValueKey('migration-not-moved'),
                items: [
                  KitConsequence(
                    names.isEmpty
                        ? l10n.migrationNotMovedSignIn
                        : l10n.migrationNotMovedSignInNamed(
                            joinSetupNames(l10n, names),
                          ),
                    mark: KitConsequenceMark.lost,
                    key: const ValueKey('migration-not-moved-sign-in'),
                  ),
                  KitConsequence(
                    l10n.migrationNotMovedKeys,
                    mark: KitConsequenceMark.lost,
                  ),
                  KitConsequence(
                    l10n.migrationNotMovedTools,
                    mark: KitConsequenceMark.info,
                  ),
                  KitConsequence(
                    l10n.migrationTermuxKept,
                    mark: KitConsequenceMark.kept,
                  ),
                ],
              ),
              SizedBox(height: tokens.sectionGap),
              if (space != null)
                KitNotice(
                  key: const ValueKey('migration-space'),
                  icon: AppIconography.storage,
                  tone: space.sufficient == false
                      ? AppStatusTone.failure
                      : AppStatusTone.neutral,
                  message: space.availableBytes == null
                      ? l10n.migrationReviewSpaceUnknown(
                          KitBidi.ltr(
                            formatPhoneStorage(space.requiredBytes ?? 0),
                          ),
                        )
                      : l10n.migrationReviewSpace(
                          KitBidi.ltr(
                            formatPhoneStorage(space.requiredBytes ?? 0),
                          ),
                          KitBidi.ltr(
                            formatPhoneStorage(space.availableBytes!),
                          ),
                        ),
                  notes: [l10n.migrationKeepOpen],
                  liveRegion: false,
                ),
            ],
          ),
        ),
      ],
    );
  }
}
