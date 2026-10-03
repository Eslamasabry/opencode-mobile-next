import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:xterm/xterm.dart' as xterm;

import '../../api/product_repository.dart';
import '../../builtin/builtin_linux.dart';
import '../../builtin/builtin_server.dart' show builtinLinuxProvider;
import '../../builtin/local_terminal.dart';
import '../../feedback/bug_report.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';
import '../kit/kit.dart';
import '../kit/scenes/states_scenes.dart';
import '../kit/terminal_key_bar.dart';
import '../widgets/product_states.dart' show productErrorText;
import '../widgets/setup_ui_messages.dart';
import 'local_terminal_screen.dart';

part 'terminal/terminal_input.dart';
part 'terminal/terminal_list.dart';
part 'terminal/terminal_surface.dart';
part 'terminal/terminal_accessible.dart';

/// [TerminalScreen] as its own pushed route. Every entry point that leaves
/// the shell for the terminal — the More hub, the Workspace header, the
/// desktop shortcut — pushes this one page so they all land identically.
///
/// On Android it offers two sources (docs/design/local-terminal-2026-09-24.md
/// §4): "This phone", a shell in the app's built-in Ubuntu that needs no
/// OpenCode server, and "OpenCode server", the server's own terminals. This
/// phone is the default once its Linux is installed; without it the phone
/// choice stays offered and explains how to set Linux up (P7.4).
class TerminalPage extends ConsumerStatefulWidget {
  final ConnectionController controller;

  /// Where to start; null picks this phone when its Linux is installed.
  final TerminalSource? initialSource;

  /// Tests: whether the local terminal exists here (Android only), and the
  /// fakes behind it.
  final bool? localSupported;
  final BuiltinLinux? linux;
  final LocalTerminalSessions? sessions;

  const TerminalPage({
    super.key,
    required this.controller,
    this.initialSource,
    this.localSupported,
    this.linux,
    this.sessions,
  });

  @override
  ConsumerState<TerminalPage> createState() => _TerminalPageState();
}

/// Where a terminal's shell runs.
enum TerminalSource { phone, server }

class _TerminalPageState extends ConsumerState<TerminalPage> {
  TerminalSource? _source;

  bool get _local => widget.localSupported ?? LocalTerminalSessions.supported;

  @override
  void initState() {
    super.initState();
    _source = _local ? widget.initialSource : TerminalSource.server;
    if (_source == null) unawaited(_pickDefault());
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

  @override
  Widget build(BuildContext context) {
    final l10n = _l10nOf(context);
    if (!_local) {
      return KeyedSubtree(
        key: const ValueKey('terminal-page'),
        child: TerminalScreen(controller: widget.controller, page: true),
      );
    }
    final source = _source;
    final choice = source == null
        ? null
        : _SourceChoice(
            source: source,
            serverName: widget.controller.profile?.name,
            onChanged: _choose,
          );
    return KeyedSubtree(
      key: const ValueKey('terminal-page'),
      child: switch (source) {
        TerminalSource.phone => LocalTerminalView(
          header: choice,
          linux: widget.linux,
          sessions: widget.sessions,
        ),
        TerminalSource.server => TerminalScreen(
          controller: widget.controller,
          page: true,
          // The source choice above is the one way to this phone's
          // terminal: no second "Use this phone's terminal" button.
          header: [?choice],
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

/// Where the shell runs: this phone or the connected server, named
/// ("Laptop"); "OpenCode server" only when the server has no name.
class _SourceChoice extends StatelessWidget {
  const _SourceChoice({
    required this.source,
    required this.onChanged,
    this.serverName,
  });

  final TerminalSource source;
  final ValueChanged<TerminalSource> onChanged;
  final String? serverName;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10nOf(context);
    final tokens = KitTokens.of(context);
    final labels = terminalSourceLabels(l10n, serverName);
    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        tokens.space1,
        tokens.gutter,
        tokens.space2,
      ),
      child: KitSegmented<TerminalSource>(
        key: const ValueKey('terminal-source'),
        semanticsLabel: l10n.terminalScreenSourceLabel,
        segments: [
          KitSegment(
            key: const ValueKey('terminal-source-phone'),
            value: TerminalSource.phone,
            label: labels.phone,
          ),
          KitSegment(
            key: const ValueKey('terminal-source-server'),
            value: TerminalSource.server,
            label: labels.server,
          ),
        ],
        selected: source,
        onChanged: onChanged,
      ),
    );
  }
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
