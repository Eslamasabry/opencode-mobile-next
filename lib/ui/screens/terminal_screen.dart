import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:xterm/xterm.dart' as xterm;

import '../../domain/dev_preview.dart';
import '../../domain/product_repository.dart';
import '../../builtin/builtin_linux.dart';
import '../../builtin/builtin_server.dart' show builtinLinuxProvider;
import '../../builtin/local_terminal.dart';
import '../../feedback/bug_report.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/project_dev_server.dart';
import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';
import '../kit/kit.dart';
import '../kit/scenes/states_scenes.dart';
import '../kit/terminal_key_bar.dart';
import '../widgets/product_states.dart' show productErrorText;
import '../widgets/setup_ui_messages.dart';
import 'dev_preview_screen.dart' show openDevPreview;
import 'local_terminal_screen.dart';

part 'terminal/terminal_input.dart';
part 'terminal/terminal_list.dart';
part 'terminal/terminal_surface.dart';
part 'terminal/terminal_accessible.dart';

/// [TerminalScreen] as its own pushed route. Every entry point that leaves
/// the shell for the terminal — the More hub, the Workspace header, the
/// desktop shortcut — pushes this one page so they all land identically.
///
/// One live shell at once, wherever it runs: on Android "Built-in Linux", a
/// shell in the app's built-in Ubuntu that needs no OpenCode server, or the
/// connected server's own terminals (docs/design/local-terminal-2026-09-24.md
/// §4). The line under the title says where it runs and switches it; when
/// the app really knows a dev server runs for this project the same line
/// says so and opens the preview (docs/qa/vision-terminal-2026-10-20).
class TerminalPage extends ConsumerStatefulWidget {
  final ConnectionController controller;

  /// Where to start; null picks this phone when its Linux is installed.
  final TerminalSource? initialSource;

  /// Tests: whether the local terminal exists here (Android only), and the
  /// fakes behind it.
  final bool? localSupported;
  final BuiltinLinux? linux;
  final LocalTerminalSessions? sessions;

  /// Tests: the dev-server signal; the app reads the project's own.
  final ProjectDevServer? devServer;

  const TerminalPage({
    super.key,
    required this.controller,
    this.initialSource,
    this.localSupported,
    this.linux,
    this.sessions,
    this.devServer,
  });

  @override
  ConsumerState<TerminalPage> createState() => _TerminalPageState();
}

/// Where a terminal's shell runs.
enum TerminalSource { phone, server }

class _TerminalPageState extends ConsumerState<TerminalPage>
    with WidgetsBindingObserver {
  TerminalSource? _source;
  late final ProjectDevServer _dev =
      widget.devServer ?? ProjectDevServer.of(widget.controller);

  bool get _local => widget.localSupported ?? LocalTerminalSessions.supported;

  @override
  void initState() {
    super.initState();
    _source = _local ? widget.initialSource : TerminalSource.server;
    if (_source == null) unawaited(_pickDefault());
    WidgetsBinding.instance.addObserver(this);
    _dev.addListener(_devChanged);
    _dev.resume();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _dev.resume();
    } else if (state != AppLifecycleState.inactive) {
      _dev.pause();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _dev.removeListener(_devChanged);
    if (widget.devServer == null) {
      _dev.dispose();
    } else {
      _dev.pause();
    }
    super.dispose();
  }

  void _devChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _pickDefault() async {
    var installed = false;
    try {
      final BuiltinLinux linux = widget.linux ?? ref.read(builtinLinuxProvider);
      installed = (await linux.status()).installed;
    } catch (_) {}
    if (!mounted || _source != null) return;
    setState(
      () => _source = installed ? TerminalSource.phone : TerminalSource.server,
    );
  }

  void _choose(TerminalSource source) => setState(() => _source = source);

  Future<void> _openPreview(DevPreviewLink link) {
    final profile = widget.controller.profile;
    return openDevPreview(
      context,
      link,
      serverBaseUrl: profile?.baseUrl,
      leased: profile?.transientTransport ?? false,
    );
  }

  /// The line under the title: where the shell runs (a switch where there
  /// are two places), then the dev server when one is known.
  KitTopBarScope _scope(TerminalSource source) {
    final l10n = _l10nOf(context);
    final labels = terminalSourceLabels(l10n, widget.controller.profile?.name);
    final place = switch (source) {
      TerminalSource.phone => labels.phone,
      TerminalSource.server => labels.server,
    };
    final running = _dev.running;
    if (running != null) {
      // One line: the dev server says itself; the place stays in the menu.
      return KitTopBarScope(
        label: '',
        semanticsLabel: l10n.terminalPlaceLabel(place),
        link: KitTopBarLink(
          linkKey: const ValueKey('terminal-dev-server'),
          label: l10n.terminalDevServerRunning,
          action: l10n.terminalOpenPreview,
          onTap: () => unawaited(_openPreview(running)),
        ),
      );
    }
    return KitTopBarScope(
      label: KitBidi.auto(place),
      semanticsLabel: _local
          ? l10n.terminalPlaceSwitchLabel(place)
          : l10n.terminalPlaceLabel(place),
      chipKey: const ValueKey('terminal-source'),
      menuLabel: l10n.terminalScreenSourceLabel,
      items: _sourceItems(source),
    );
  }

  /// Where the shell can run, the one in use checked. Empty where there is
  /// only the server.
  List<KitMenuItem> _sourceItems(TerminalSource source) {
    if (!_local) return const [];
    final labels = terminalSourceLabels(
      _l10nOf(context),
      widget.controller.profile?.name,
    );
    return [
      for (final (value, label) in [
        (TerminalSource.phone, labels.phone),
        (TerminalSource.server, labels.server),
      ])
        KitMenuItem(
          key: ValueKey('terminal-source-${value.name}'),
          label: KitBidi.auto(label),
          icon: value == TerminalSource.phone
              ? AppIconography.terminal
              : AppIconography.server,
          checked: value == source,
          group: 'place',
          onSelected: () => _choose(value),
        ),
    ];
  }

  /// The place in the bar's menu while the line under the title is the dev
  /// server's: switching where the shell runs is never out of reach.
  List<KitMenuItem> _placeMenu(TerminalSource source) =>
      _dev.running == null ? const [] : _sourceItems(source);

  @override
  Widget build(BuildContext context) {
    final l10n = _l10nOf(context);
    final source = _source;
    return KeyedSubtree(
      key: const ValueKey('terminal-page'),
      child: switch (source) {
        TerminalSource.phone => LocalTerminalView(
          scope: _scope(source!),
          placeMenu: _placeMenu(source),
          linux: widget.linux,
          sessions: widget.sessions,
        ),
        TerminalSource.server => TerminalScreen(
          controller: widget.controller,
          page: true,
          scope: _scope(source!),
          placeMenu: _placeMenu(source),
        ),
        null => KitScreen(
          topBar: KitTopBar(title: l10n.libraryTerminalTitle),
          loading: true,
          loadingLabel: l10n.localTerminalStarting,
          body: const SizedBox.shrink(),
        ),
      },
    );
  }
}

/// The two labels of the host switch: the app's built-in Linux, and the
/// connected server by its own name ("OpenCode server" when it has none).
/// They never read the same: a server profile called "This phone" sits next
/// to "Built-in Linux", and a name that still collides gets the runtime
/// added.
({String phone, String server}) terminalSourceLabels(
  AppLocalizations l10n,
  String? serverName,
) {
  final phone = l10n.localTerminalSourcePhone;
  final name = serverName?.trim();
  var server = name == null || name.isEmpty
      ? l10n.localTerminalSourceServer
      : name;
  if (server.toLowerCase() == phone.toLowerCase()) {
    server = '$server · ${l10n.localTerminalSourceServer}';
  }
  return (phone: phone, server: server);
}

/// Asks for a terminal's new name and renames it on the server. A failure
/// stays in the dialog under the field, with the typed name kept. Returns
/// the new name, or null when nothing changed.
Future<String?> _renameTerminal(
  BuildContext context,
  TerminalProcess process,
  Future<ServerOperationsGateway?> Function() repository,
) {
  final l10n = _l10nOf(context);
  return showKitInputDialog(
    context,
    title: l10n.e7SetupRenameTerminal,
    label: l10n.terminalScreenNameLabel,
    confirmLabel: l10n.terminalScreenRenameConfirm,
    initial: process.title,
    validate: (value) =>
        value.trim().isEmpty ? l10n.terminalScreenNameEmpty : null,
    fieldKey: const ValueKey('terminal-rename-field'),
    confirmKey: const ValueKey('terminal-rename-confirm'),
    onSubmit: (value) async {
      try {
        final gateway = await repository();
        if (gateway == null) return l10n.e7SetupServerDisconnected;
        await gateway.renameTerminal(process.id, value.trim());
        return null;
      } catch (error) {
        return setupUiMessage(l10n, productErrorText(error));
      }
    },
  );
}

/// Asks before stopping (a running terminal) or removing (an ended one) and
/// does it inside the question, so a failure keeps it open with Try again.
/// True once the terminal is gone.
Future<bool> _removeTerminal(
  BuildContext context,
  TerminalProcess process,
  Future<ServerOperationsGateway?> Function() repository,
) {
  final l10n = _l10nOf(context);
  final running = process.running;
  return showKitConfirm(
    context,
    title: running
        ? l10n.terminalScreenStopTitle(process.title)
        : l10n.terminalScreenRemoveTitle(process.title),
    body: running ? l10n.terminalScreenStopBody : l10n.terminalScreenRemoveBody,
    confirmLabel: running
        ? l10n.terminalScreenStopConfirm
        : l10n.terminalScreenRemoveConfirm,
    kind: running ? KitConfirmKind.stop : KitConfirmKind.destructive,
    icon: running ? AppIconography.stop : AppIconography.delete,
    confirmKey: const ValueKey('terminal-remove-confirm'),
    action: () async {
      final gateway = await repository();
      if (gateway == null) {
        throw ProductException(l10n.e7SetupServerDisconnected);
      }
      await gateway.removeTerminal(process.id);
    },
  );
}
