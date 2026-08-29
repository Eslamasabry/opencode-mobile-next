import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'desktop_interaction.dart';

// =====================================================================
// Intents
// =====================================================================
//
// The shell binds keys to intents. Surfaces claim the intents they can
// service by registering with [AppShortcutSignals]; anything unclaimed falls
// through to the shell's own handler. Claiming is used rather than nesting
// [Actions] because a freshly pushed route often has no focused widget yet,
// and action lookup starts at the primary focus — a surface would silently
// stop responding until the user clicked something inside it.

/// Ctrl/Cmd+K — open the command launcher for whatever surface is current.
class OpenCommandPaletteIntent extends Intent {
  const OpenCommandPaletteIntent();
}

/// Ctrl/Cmd+N — start a new session.
class NewSessionIntent extends Intent {
  const NewSessionIntent();
}

/// Ctrl/Cmd+F — focus the find field of the surface that has one.
class FindInSurfaceIntent extends Intent {
  const FindInSurfaceIntent();
}

/// Ctrl/Cmd+, — open Settings.
class OpenSettingsIntent extends Intent {
  const OpenSettingsIntent();
}

/// Ctrl/Cmd+W — close the current route.
class CloseRouteIntent extends Intent {
  const CloseRouteIntent();
}

/// Ctrl/Cmd+/ — list every shortcut.
class ShowShortcutsHelpIntent extends Intent {
  const ShowShortcutsHelpIntent();
}

/// Ctrl/Cmd+1..4 — switch primary destination.
class SelectDestinationIntent extends Intent {
  const SelectDestinationIntent(this.index);

  final int index;
}

// =====================================================================
// Bindings
// =====================================================================

/// Both modifiers for every accelerator: Control is the Linux/Windows habit,
/// Command the macOS one, and either reaches the same intent.
Map<ShortcutActivator, Intent> _accelerator(
  LogicalKeyboardKey key,
  Intent intent,
) => {
  SingleActivator(key, control: true): intent,
  SingleActivator(key, meta: true): intent,
};

/// Every shortcut the shell owns.
///
/// Deliberately no plain-letter accelerators: every binding carries a
/// modifier, so typing in the composer, a rename dialog, or a search field can
/// never fire one. Ctrl+Enter (send) stays on the composer field where it
/// belongs, and Escape is left to Flutter's own modal dismiss action rather
/// than re-bound here.
Map<ShortcutActivator, Intent> get appShortcutBindings => {
  ..._accelerator(LogicalKeyboardKey.keyK, const OpenCommandPaletteIntent()),
  ..._accelerator(LogicalKeyboardKey.keyN, const NewSessionIntent()),
  ..._accelerator(LogicalKeyboardKey.keyF, const FindInSurfaceIntent()),
  ..._accelerator(LogicalKeyboardKey.comma, const OpenSettingsIntent()),
  ..._accelerator(LogicalKeyboardKey.keyW, const CloseRouteIntent()),
  ..._accelerator(LogicalKeyboardKey.slash, const ShowShortcutsHelpIntent()),
  ..._accelerator(LogicalKeyboardKey.digit1, const SelectDestinationIntent(0)),
  ..._accelerator(LogicalKeyboardKey.digit2, const SelectDestinationIntent(1)),
  ..._accelerator(LogicalKeyboardKey.digit3, const SelectDestinationIntent(2)),
  ..._accelerator(LogicalKeyboardKey.digit4, const SelectDestinationIntent(3)),
};

/// One row of the shortcuts help sheet.
class ShortcutHelpEntry {
  const ShortcutHelpEntry(this.keys, this.description);

  final String keys;
  final String description;
}

/// The discoverable table behind Ctrl+/ and the More hub entry.
List<ShortcutHelpEntry> shortcutHelp() {
  final mod = shortcutModifierLabel;
  return [
    ShortcutHelpEntry('$mod + K', 'Command launcher'),
    ShortcutHelpEntry('$mod + N', 'New session'),
    ShortcutHelpEntry('$mod + F', 'Find in this surface'),
    ShortcutHelpEntry('$mod + 1 … 4', 'Workspace, Files, Activity, More'),
    ShortcutHelpEntry('$mod + ,', 'Settings'),
    ShortcutHelpEntry('$mod + W', 'Close this screen'),
    ShortcutHelpEntry('$mod + Enter', 'Send the prompt'),
    ShortcutHelpEntry('$mod + /', 'This list'),
    const ShortcutHelpEntry('Esc', 'Close a sheet, dialog, or menu'),
    const ShortcutHelpEntry(
      'Right click',
      'Message, file, and session actions',
    ),
  ];
}

// =====================================================================
// Surface claiming
// =====================================================================

/// Returns true when the surface handled [intent] and the shell should not.
typedef AppShortcutClaim = bool Function(Intent intent);

/// The registry the shell dispatches through. Handlers are consulted newest
/// first, which matches route push order, and each one still verifies it is
/// the current route before claiming anything.
class AppShortcutSignals {
  final List<AppShortcutClaim> _claims = <AppShortcutClaim>[];

  void register(AppShortcutClaim claim) => _claims.add(claim);

  void unregister(AppShortcutClaim claim) => _claims.remove(claim);

  bool dispatch(Intent intent) {
    for (final claim in _claims.reversed.toList(growable: false)) {
      if (claim(intent)) return true;
    }
    return false;
  }
}

/// Exposes [AppShortcutSignals] to every route below the shell.
class AppShortcutScope extends InheritedWidget {
  const AppShortcutScope({
    super.key,
    required this.signals,
    required super.child,
  });

  final AppShortcutSignals signals;

  static AppShortcutSignals? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppShortcutScope>()?.signals;

  @override
  bool updateShouldNotify(AppShortcutScope oldWidget) =>
      signals != oldWidget.signals;
}

/// Mixed into a surface that wants to answer shell shortcuts.
///
/// Registration is a no-op when no [AppShortcutScope] is above (every widget
/// test that pumps a bare screen), so a surface using this mixin behaves
/// exactly as before outside the shell.
mixin AppShortcutSurface<T extends StatefulWidget> on State<T> {
  AppShortcutSignals? _signals;

  /// Return true only for intents this surface actually serviced.
  bool onAppShortcut(Intent intent);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final signals = AppShortcutScope.maybeOf(context);
    if (identical(signals, _signals)) return;
    _signals?.unregister(_claim);
    _signals = signals;
    _signals?.register(_claim);
  }

  bool _claim(Intent intent) {
    if (!mounted) return false;
    // A surface buried under a pushed route never steals a shortcut from the
    // screen the user is actually looking at.
    final route = ModalRoute.of(context);
    if (route != null && !route.isCurrent) return false;
    return onAppShortcut(intent);
  }

  @override
  void dispose() {
    _signals?.unregister(_claim);
    _signals = null;
    super.dispose();
  }
}

// =====================================================================
// The shell layer
// =====================================================================

/// Callbacks the shell services when no surface claims the intent.
class AppShortcutHandlers {
  const AppShortcutHandlers({
    required this.onNewSession,
    required this.onOpenSettings,
    required this.paletteCommands,
  });

  final VoidCallback onNewSession;
  final VoidCallback onOpenSettings;

  /// Built lazily so the launcher always reflects current connection state.
  final List<DesktopCommand> Function(BuildContext context) paletteCommands;
}

/// Installs the app-wide shortcut layer above the navigator.
///
/// Off desktop this returns [child] untouched, so Android keeps exactly the
/// key handling it had — Ctrl+Enter in the composer, and nothing else.
class AppShortcuts extends StatefulWidget {
  const AppShortcuts({
    super.key,
    required this.navigatorKey,
    required this.signals,
    required this.handlers,
    required this.child,
  });

  final GlobalKey<NavigatorState> navigatorKey;

  /// Owned by the caller so the command launcher can dispatch the same
  /// intents the keyboard does.
  final AppShortcutSignals signals;
  final AppShortcutHandlers handlers;
  final Widget child;

  @override
  State<AppShortcuts> createState() => _AppShortcutsState();
}

class _AppShortcutsState extends State<AppShortcuts> {
  AppShortcutSignals get _signals => widget.signals;

  NavigatorState? get _navigator => widget.navigatorKey.currentState;

  /// Runs [fallback] only when no visible surface claimed the intent.
  Object? _dispatch(Intent intent, void Function(BuildContext) fallback) {
    if (_signals.dispatch(intent)) return null;
    final navigatorContext = _navigator?.context;
    if (navigatorContext != null) fallback(navigatorContext);
    return null;
  }

  @override
  Widget build(BuildContext context) {
    if (!desktopInteractions) return widget.child;
    return AppShortcutScope(
      signals: _signals,
      child: Shortcuts(
        shortcuts: appShortcutBindings,
        child: Actions(
          actions: <Type, Action<Intent>>{
            OpenCommandPaletteIntent: CallbackAction<OpenCommandPaletteIntent>(
              onInvoke: (intent) => _dispatch(
                intent,
                (context) => unawaited(
                  showCommandPalette(
                    context,
                    widget.handlers.paletteCommands(context),
                  ),
                ),
              ),
            ),
            NewSessionIntent: CallbackAction<NewSessionIntent>(
              onInvoke: (intent) =>
                  _dispatch(intent, (_) => widget.handlers.onNewSession()),
            ),
            OpenSettingsIntent: CallbackAction<OpenSettingsIntent>(
              onInvoke: (intent) =>
                  _dispatch(intent, (_) => widget.handlers.onOpenSettings()),
            ),
            CloseRouteIntent: CallbackAction<CloseRouteIntent>(
              // maybePop, never pop: a screen guarding unsaved work with
              // PopScope keeps its veto.
              onInvoke: (intent) =>
                  _dispatch(intent, (_) => unawaited(_navigator!.maybePop())),
            ),
            ShowShortcutsHelpIntent: CallbackAction<ShowShortcutsHelpIntent>(
              onInvoke: (intent) => _dispatch(
                intent,
                (context) => unawaited(showShortcutsHelp(context)),
              ),
            ),
            // No shell fallback: only a surface with primary destinations or
            // a find field can service these. Registering them still stops
            // the keystroke from leaking through as a literal character.
            FindInSurfaceIntent: CallbackAction<FindInSurfaceIntent>(
              onInvoke: (intent) => _dispatch(intent, (_) {}),
            ),
            SelectDestinationIntent: CallbackAction<SelectDestinationIntent>(
              onInvoke: (intent) => _dispatch(intent, (_) {}),
            ),
          },
          child: widget.child,
        ),
      ),
    );
  }
}

// =====================================================================
// Command launcher
// =====================================================================

/// One entry in the Ctrl+K launcher.
class DesktopCommand {
  const DesktopCommand({
    required this.label,
    required this.icon,
    required this.onInvoke,
    this.hint,
    this.keys,
  });

  final String label;
  final IconData icon;
  final VoidCallback onInvoke;

  /// Secondary line: what the command actually does.
  final String? hint;

  /// The accelerator that reaches the same command, when one exists.
  final String? keys;
}

/// Opens the searchable command launcher.
Future<void> showCommandPalette(
  BuildContext context,
  List<DesktopCommand> commands,
) {
  if (commands.isEmpty) return Future<void>.value();
  return showDialog<void>(
    context: context,
    builder: (_) => _CommandPalette(commands: commands),
  );
}

class _CommandPalette extends StatefulWidget {
  const _CommandPalette({required this.commands});

  final List<DesktopCommand> commands;

  @override
  State<_CommandPalette> createState() => _CommandPaletteState();
}

class _CommandPaletteState extends State<_CommandPalette> {
  final _query = TextEditingController();
  final _listController = ScrollController();
  int _highlighted = 0;

  List<DesktopCommand> get _matches {
    final query = _query.text.trim().toLowerCase();
    if (query.isEmpty) return widget.commands;
    return widget.commands
        .where(
          (command) =>
              command.label.toLowerCase().contains(query) ||
              (command.hint?.toLowerCase().contains(query) ?? false),
        )
        .toList();
  }

  void _move(int delta) {
    final matches = _matches;
    if (matches.isEmpty) return;
    setState(
      () => _highlighted = (_highlighted + delta).clamp(0, matches.length - 1),
    );
  }

  void _run(DesktopCommand command) {
    Navigator.of(context).pop();
    command.onInvoke();
  }

  void _runHighlighted() {
    final matches = _matches;
    if (matches.isEmpty || _highlighted >= matches.length) return;
    _run(matches[_highlighted]);
  }

  @override
  void dispose() {
    _query.dispose();
    _listController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final matches = _matches;
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.arrowDown): () => _move(1),
        const SingleActivator(LogicalKeyboardKey.arrowUp): () => _move(-1),
      },
      child: Dialog(
        key: const ValueKey('desktop-command-palette'),
        alignment: Alignment.topCenter,
        insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 72),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560, maxHeight: 460),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
                child: TextField(
                  key: const ValueKey('command-palette-query'),
                  controller: _query,
                  autofocus: true,
                  onChanged: (_) => setState(() => _highlighted = 0),
                  onSubmitted: (_) => _runHighlighted(),
                  decoration: const InputDecoration(
                    isDense: true,
                    prefixIcon: Icon(Icons.bolt_rounded, size: 20),
                    hintText: 'Type a command…',
                    border: OutlineInputBorder(),
                  ),
                ),
              ),
              const Divider(height: 1),
              Flexible(
                child: matches.isEmpty
                    ? Padding(
                        padding: const EdgeInsets.all(28),
                        child: Text(
                          'No matching command',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      )
                    : Scrollbar(
                        controller: _listController,
                        thumbVisibility: true,
                        child: ListView.builder(
                          controller: _listController,
                          shrinkWrap: true,
                          itemCount: matches.length,
                          itemBuilder: (context, index) {
                            final command = matches[index];
                            return ListTile(
                              key: ValueKey('command-${command.label}'),
                              dense: true,
                              selected: index == _highlighted,
                              leading: Icon(command.icon, size: 20),
                              title: Text(command.label),
                              subtitle: command.hint == null
                                  ? null
                                  : Text(command.hint!),
                              trailing: command.keys == null
                                  ? null
                                  : Text(
                                      command.keys!,
                                      style: theme.textTheme.labelSmall
                                          ?.copyWith(
                                            color: theme
                                                .colorScheme
                                                .onSurfaceVariant,
                                          ),
                                    ),
                              onTap: () => _run(command),
                            );
                          },
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

// =====================================================================
// Shortcut help
// =====================================================================

/// The discoverable list of every shortcut, reachable from Ctrl+/ and from
/// the More hub so it is not itself hidden behind a shortcut.
Future<void> showShortcutsHelp(BuildContext context) {
  final entries = shortcutHelp();
  return showDialog<void>(
    context: context,
    builder: (context) {
      final theme = Theme.of(context);
      return AlertDialog(
        key: const ValueKey('keyboard-shortcuts-sheet'),
        title: const Text('Keyboard shortcuts'),
        content: SizedBox(
          width: 380,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final entry in entries)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 5),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: 132,
                          child: Text(
                            entry.keys,
                            style: theme.textTheme.labelLarge?.copyWith(
                              fontFamily: 'AppMono',
                            ),
                          ),
                        ),
                        Expanded(
                          child: Text(
                            entry.description,
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        ],
      );
    },
  );
}
