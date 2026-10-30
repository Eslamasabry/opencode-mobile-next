import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show Clipboard;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:xterm/xterm.dart' as xterm;

import '../../builtin/builtin_linux.dart';
import '../../builtin/builtin_server.dart' show builtinLinuxProvider;
import '../../builtin/local_terminal.dart';
import '../../builtin/team/builtin_team.dart' show BuiltinTeam;
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../kit/scenes/states_scenes.dart';
import '../kit/terminal_key_bar.dart';
import '../widgets/product_states.dart' show productErrorDetails;
import 'phone_setup/phone_setup_routes.dart';

/// Test seam for leaving to phone setup.
@visibleForTesting
Future<void> Function(BuildContext context)? localTerminalOpenSetupOverride;

/// A shell in this phone's built-in Ubuntu, straight from the app: no
/// OpenCode server is involved, so it works while the server is stopped or
/// not answering (docs/design/local-terminal-2026-09-24.md).
///
/// The whole screen: the top bar ("Terminal", with [scope] under it: where
/// the shell runs, and the dev-server line when one is known), the shells as
/// tabs once there are two, the terminal, and the one key row. Shells
/// outlive the screen ([localTerminalProvider]).
class LocalTerminalView extends ConsumerStatefulWidget {
  const LocalTerminalView({
    super.key,
    this.scope,
    this.placeMenu = const [],
    this.sessions,
    this.linux,
  });

  /// The line under the title (the terminal page's place and dev server).
  final KitTopBarScope? scope;

  /// Where the shell runs, at the end of the bar's menu (while the line
  /// under the title is the dev server's).
  final List<KitMenuItem> placeMenu;

  /// Tests pass fakes; the app uses its providers.
  final LocalTerminalSessions? sessions;
  final BuiltinLinux? linux;

  @override
  ConsumerState<LocalTerminalView> createState() => _LocalTerminalViewState();
}

class _LocalTerminalViewState extends ConsumerState<LocalTerminalView> {
  late final LocalTerminalSessions _sessions =
      widget.sessions ?? ref.read(localTerminalProvider);
  late final BuiltinLinux _linux =
      widget.linux ?? ref.read(builtinLinuxProvider);
  final _keys = TerminalKeyBarController();
  final _selection = xterm.TerminalController();
  final _focus = FocusNode();
  BuiltinLinuxStatus? _status;
  String? _statusFailure;
  LocalShell? _shell;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    _sessions.addListener(_sessionsChanged);
    _selection.addListener(_selectionChanged);
    unawaited(_open());
  }

  @override
  void dispose() {
    _sessions.removeListener(_sessionsChanged);
    _selection.removeListener(_selectionChanged);
    _shell?.inputFilter = null;
    _selection.dispose();
    _keys.dispose();
    _focus.dispose();
    super.dispose();
  }

  Future<void> _open() async {
    setState(() {
      _status = null;
      _statusFailure = null;
    });
    BuiltinLinuxStatus status;
    try {
      status = await _linux.status();
    } catch (error) {
      if (mounted) {
        setState(() => _statusFailure = productErrorDetails(error) ?? '');
      }
      return;
    }
    if (!mounted) return;
    setState(() => _status = status);
    if (!status.installed) return;
    await _sessions.load();
    if (!mounted) return;
    final shells = _sessions.shells;
    final remembered = _sessions.current;
    _show(
      remembered != null && shells.contains(remembered)
          ? remembered
          : shells.isNotEmpty
          ? shells.last
          : _sessions.startShell(),
    );
    if (status.serviceRunning(BuiltinTeam.serviceName)) {
      unawaited(_sessions.refreshProcesses());
    }
  }

  void _show(LocalShell shell) {
    if (!identical(_shell, shell)) _shell?.inputFilter = null;
    shell.inputFilter = _keys.apply;
    _sessions.current = shell;
    _selection.clearSelection();
    setState(() => _shell = shell);
  }

  void _sessionsChanged() {
    if (!mounted) return;
    final shell = _shell;
    if (shell != null && !_sessions.shells.contains(shell)) {
      final shells = _sessions.shells;
      if (shells.isNotEmpty) {
        _show(shells.last);
        return;
      }
      _shell = null;
    }
    setState(() {});
  }

  void _selectionChanged() {
    if (mounted) setState(() {});
  }

  void _newShell() => _show(_sessions.startShell());

  void _restart(LocalShell shell) => _show(_sessions.restart(shell));

  Future<void> _paste(LocalShell shell) async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text;
    if (text == null || text.isEmpty) return;
    shell.terminal.paste(text);
    _focus.requestFocus();
  }

  /// The selected text, for the bar's Copy (KitCopy, KIT-23).
  String _selectedText(LocalShell shell) =>
      KitTerminalView.selectedText(shell.terminal, _selection);

  Future<void> _stop(LocalShell shell) async {
    final l10n = _l10n;
    final confirmed = await showKitConfirm(
      context,
      title: l10n.localTerminalStopNamedTitle(_shellName(shell)),
      body: l10n.localTerminalStopBody,
      confirmLabel: l10n.localTerminalStop,
      kind: KitConfirmKind.stop,
      icon: AppIconography.stop,
      confirmKey: const ValueKey('local-terminal-stop-confirm'),
    );
    if (!confirmed) return;
    await _sessions.stop(shell);
  }

  String _shellName(LocalShell shell) =>
      _l10n.localTerminalShellName(shell.number);

  /// The shells as tabs, once there are two: the one showing in mint, each
  /// with its own Stop or Close on long-press.
  Widget? _tabs(LocalShell? shell) {
    final shells = _sessions.shells;
    if (shells.length < 2) return null;
    final l10n = _l10n;
    return Padding(
      padding: EdgeInsetsDirectional.only(bottom: KitTokens.of(context).space1),
      child: KitSessionTabs(
        key: const ValueKey('local-terminal-tabs'),
        semanticsLabel: l10n.terminalSessionsLabel,
        endedLabel: l10n.terminalSessionEnded,
        tabs: [
          for (final each in shells)
            KitSessionTab(
              tabKey: ValueKey('local-terminal-shell-${each.number}'),
              label: _shellName(each),
              selected: identical(each, shell),
              ended: !_live(each),
              onTap: () => _show(each),
              menuLabel: _shellName(each),
              menu: [
                if (each.running)
                  KitMenuItem(
                    label: l10n.localTerminalStopNamed(_shellName(each)),
                    icon: AppIconography.stopCircle,
                    destructive: true,
                    onSelected: () => unawaited(_stop(each)),
                  )
                else
                  KitMenuItem(
                    label: l10n.localTerminalCloseNamed(_shellName(each)),
                    icon: AppIconography.close,
                    destructive: true,
                    onSelected: () => unawaited(_sessions.remove(each)),
                  ),
              ],
            ),
        ],
      ),
    );
  }

  static bool _live(LocalShell shell) =>
      shell.running || shell.state == LocalShellState.starting;

  /// The top bar's menu: New shell and Paste, then stopping or closing the
  /// one showing. The shells themselves are the tabs.
  List<KitMenuItem> _menu(LocalShell? shell) {
    final l10n = _l10n;
    return [
      KitMenuItem(
        key: const ValueKey('local-terminal-new'),
        label: l10n.localTerminalNewShell,
        icon: AppIconography.add,
        group: 'actions',
        onSelected: _newShell,
      ),
      if (shell != null && shell.running) ...[
        KitMenuItem(
          key: const ValueKey('local-terminal-paste'),
          label: l10n.localTerminalPasteNamed(_shellName(shell)),
          icon: AppIconography.paste,
          group: 'actions',
          onSelected: () => unawaited(_paste(shell)),
        ),
        KitMenuItem(
          key: const ValueKey('local-terminal-stop'),
          label: l10n.localTerminalStopShell,
          icon: AppIconography.stopCircle,
          destructive: true,
          onSelected: () => unawaited(_stop(shell)),
        ),
      ] else if (shell != null)
        KitMenuItem(
          key: const ValueKey('local-terminal-close'),
          label: l10n.localTerminalCloseShell,
          icon: AppIconography.close,
          destructive: true,
          onSelected: () => unawaited(_sessions.remove(shell)),
        ),
    ];
  }

  /// With AI Team on, how many programs the shells add against Android's
  /// process budget (battery-heat vertical): one condition on the line.
  KitStatus? _cost() {
    final status = _status;
    if (status == null || !status.serviceRunning(BuiltinTeam.serviceName)) {
      return null;
    }
    final l10n = _l10n;
    final processes = _sessions.processes;
    return KitStatus(
      kind: KitStatusKind.info,
      id: 'local-terminal-cost',
      icon: AppIconography.info,
      message: processes == null
          ? l10n.localTerminalCost(
              LocalTerminalSessions.processesPerShell,
              LocalTerminalSessions.androidProcessLimit,
            )
          : l10n.localTerminalCostNow(
              LocalTerminalSessions.processesPerShell,
              // The app itself is not one of the extra processes.
              (processes - 1).clamp(0, 9999),
              LocalTerminalSessions.androidProcessLimit,
            ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final shell = _shell;
    final installed = _status?.installed == true;
    final starting =
        (_status == null && _statusFailure == null) ||
        (installed &&
            (shell == null || shell.state == LocalShellState.starting));
    // Landscape with the keyboard up leaves a strip: the tabs go (and the
    // key bar folds into one sliding row), so the shell keeps some lines.
    final header = _compact(context) || !installed ? null : _tabs(shell);
    final selected = shell != null && _selection.selection != null;
    return KitScreen(
      topBar: KitTopBar(
        title: l10n.libraryTerminalTitle,
        // A short strip above the keyboard keeps its lines for the shell.
        scope: _compact(context) ? null : widget.scope,
        menuKey: const ValueKey('local-terminal-menu'),
        actions: [
          if (selected)
            KitAction.copy(
              key: const ValueKey('local-terminal-copy'),
              label: l10n.localTerminalCopySelection,
              text: () => _selectedText(shell),
            ),
        ],
        menu: [if (installed) ..._menu(shell), ...widget.placeMenu],
      ),
      header: [?header],
      status: installed && shell != null ? _cost() : null,
      loading: starting,
      loadingLabel: l10n.localTerminalStarting,
      body: _body(context),
    );
  }

  /// Less than this much height above the keyboard makes the screen compact.
  static const compactHeight = 420.0;

  static bool _compact(BuildContext context) {
    // A page frame removes the keyboard from its body's MediaQuery, so the
    // window's own inset counts too.
    final window = View.of(context);
    final keyboard = math.max(
      MediaQuery.viewInsetsOf(context).bottom,
      window.viewInsets.bottom / window.devicePixelRatio,
    );
    return MediaQuery.sizeOf(context).height - keyboard < compactHeight;
  }

  Widget _body(BuildContext context) {
    final l10n = _l10n;
    final status = _status;
    if (_statusFailure != null) {
      return KitStateView(
        key: const ValueKey('local-terminal-failed'),
        icon: AppIconography.error,
        tone: AppStatusTone.failure,
        title: l10n.localTerminalFailedTitle,
        body: l10n.localTerminalFailedBody,
        primary: KitAction(
          key: const ValueKey('local-terminal-try-again'),
          label: l10n.localTerminalTryAgain,
          onPressed: () => unawaited(_open()),
        ),
        details: _statusFailure!.isEmpty ? null : _statusFailure,
      );
    }
    if (status == null) return const SizedBox.shrink();
    if (!status.installed) {
      // The gate explains itself and offers the flow that opens it (P7.4).
      return KitStateView(
        key: const ValueKey('local-terminal-not-set-up'),
        icon: AppIconography.terminal,
        illustration: const StatesTerminalScene(),
        title: l10n.localTerminalNotSetUpTitle,
        body: l10n.localTerminalNotSetUpBody,
        primary: KitAction(
          key: const ValueKey('local-terminal-set-up'),
          label: l10n.localTerminalSetUpLinux,
          onPressed: () async {
            await (localTerminalOpenSetupOverride ?? openPhoneSetupStart)(
              context,
            );
            if (mounted) unawaited(_open());
          },
        ),
      );
    }
    final shell = _shell;
    if (shell == null) return const SizedBox.shrink();
    if (shell.state == LocalShellState.failed) {
      return KitStateView(
        key: const ValueKey('local-terminal-failed'),
        icon: AppIconography.error,
        tone: AppStatusTone.failure,
        title: l10n.localTerminalFailedTitle,
        body: l10n.localTerminalFailedBody,
        primary: KitAction(
          key: const ValueKey('local-terminal-try-again'),
          label: l10n.localTerminalTryAgain,
          onPressed: () => _restart(shell),
        ),
        details: shell.failure,
      );
    }
    return ListenableBuilder(
      listenable: shell,
      builder: (context, _) {
        final exited = shell.state == LocalShellState.exited;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: KitTerminalView.live(
                terminal: shell.terminal,
                semanticsLabel: l10n.localTerminalSemantics,
                viewKey: ValueKey('local-terminal-view-${shell.number}'),
                controller: _selection,
                focusNode: _focus,
                readOnly: !shell.running,
                calm: true,
                // An ended shell shows its last screen and Restart instead
                // of keys that would type into nothing.
                keys: exited ? null : _keys,
                keysKey: const ValueKey('local-terminal-keys'),
              ),
            ),
            if (exited)
              SafeArea(
                top: false,
                child: KitStateView(
                  key: const ValueKey('local-terminal-ended'),
                  size: KitStateSize.inline,
                  icon: AppIconography.terminal,
                  illustration: const StatesTerminalScene(ended: true),
                  title: l10n.localTerminalEndedTitle,
                  body: l10n.localTerminalEndedBody(shell.exitCode ?? -1),
                  primary: KitAction(
                    key: const ValueKey('local-terminal-restart'),
                    label: l10n.localTerminalRestart,
                    onPressed: () => _restart(shell),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}
