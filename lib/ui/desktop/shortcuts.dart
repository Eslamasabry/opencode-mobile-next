import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'desktop_interaction.dart';
import '../../domain/settings_search.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/kit_row.dart';
import '../kit/kit_search_field.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_text.dart';

AppLocalizations _shortcutL10n(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));

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

/// Ctrl/Cmd+` — open the terminal for the active workspace.
class OpenTerminalIntent extends Intent {
  const OpenTerminalIntent();
}

/// Ctrl/Cmd+1..3 — switch primary destination (Chats, Files, Settings).
class SelectDestinationIntent extends Intent {
  const SelectDestinationIntent(this.index);

  final int index;
}

/// Opens the Chats destination, optionally on its "Needs you" or "Running"
/// filter. Not bound to a key: notifications, the Quick Settings tile,
/// pinned shortcuts and search use it for everything that used to open the
/// Inbox. Dispatched through [dispatchAtShellRoot] from any route.
class OpenChatsIntent extends Intent {
  const OpenChatsIntent({this.needsYou = false, this.running = false});

  /// Open with the "Needs you" chip on.
  final bool needsYou;

  /// Open with the "Running" chip on.
  final bool running;
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
  ..._accelerator(LogicalKeyboardKey.backquote, const OpenTerminalIntent()),
  ..._accelerator(LogicalKeyboardKey.digit1, const SelectDestinationIntent(0)),
  ..._accelerator(LogicalKeyboardKey.digit2, const SelectDestinationIntent(1)),
  ..._accelerator(LogicalKeyboardKey.digit3, const SelectDestinationIntent(2)),
};

/// Where a shortcut works: the help groups its rows by it.
enum ShortcutHelpGroup {
  /// Anywhere in the app.
  anywhere,

  /// Inside a conversation (the chat screen and its composer).
  conversation,
}

/// One row of the shortcuts help sheet.
class ShortcutHelpEntry {
  const ShortcutHelpEntry(
    this.keys,
    this.description, {
    this.group = ShortcutHelpGroup.anywhere,
  });

  final String keys;
  final String description;

  /// Where it works; the help shows one section per group.
  final ShortcutHelpGroup group;
}

/// The discoverable table behind Ctrl+/ and the More hub entry.
List<ShortcutHelpEntry> shortcutHelp(AppLocalizations l10n) {
  final mod = shortcutModifierLabel;
  const conversation = ShortcutHelpGroup.conversation;
  return [
    ShortcutHelpEntry('$mod + K', l10n.e7LocaleUiCommandLauncher),
    ShortcutHelpEntry('$mod + N', l10n.e7LocaleUiNewSession),
    ShortcutHelpEntry('$mod + F', l10n.e7LocaleUiFindSurface),
    ShortcutHelpEntry('$mod + 1 … 3', l10n.e7LocaleUiDestinations),
    ShortcutHelpEntry('$mod + ,', l10n.e7LocaleUiSettings),
    ShortcutHelpEntry('$mod + `', l10n.e7LocaleUiTerminal),
    ShortcutHelpEntry('$mod + W', l10n.e7LocaleUiCloseScreen),
    ShortcutHelpEntry('$mod + /', l10n.e7LocaleUiThisList),
    ShortcutHelpEntry('Esc', l10n.e7LocaleUiCloseOverlay),
    ShortcutHelpEntry(
      l10n.e7LocaleUiContextKeys,
      l10n.e7LocaleUiContextActions,
    ),
    // Handled outside this file (chat screen, ModelShortcuts, the composer's
    // focus node). Listed here so no shortcut is known only to the people
    // who guessed it; each also has a visible control, see
    // docs/design/ui-ledger/gesture-audit.md.
    ShortcutHelpEntry(
      '$mod + Enter',
      l10n.e7LocaleUiSendPrompt,
      group: conversation,
    ),
    ShortcutHelpEntry(
      '$mod + C',
      l10n.e7LocaleUiCopyTranscript,
      group: conversation,
    ),
    ShortcutHelpEntry(
      'F2 / Shift + F2',
      l10n.e7LocaleUiRecentModel,
      group: conversation,
    ),
    ShortcutHelpEntry(
      'Ctrl + B',
      l10n.backgroundWorkTitle,
      group: conversation,
    ),
    ShortcutHelpEntry(
      'F3 / Shift + F3',
      l10n.gestureEquivShortcutFindMatch,
      group: conversation,
    ),
    ShortcutHelpEntry(
      '↑ / ↓',
      l10n.gestureEquivShortcutPromptHistory,
      group: conversation,
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

/// Pops every pushed route, then offers [intent] to the shell root.
///
/// Ctrl+1..3 and Ctrl+` are shell-level: pressed while chat, the terminal,
/// or review is open they must not silently do nothing. The pop is
/// synchronous, so the root surface is current again by the time the
/// signal is dispatched; a surface still animating in gets one more try
/// after the frame.
void dispatchAtShellRoot(
  NavigatorState navigator,
  AppShortcutSignals signals,
  Intent intent,
) {
  navigator.popUntil((route) => route.isFirst);
  if (signals.dispatch(intent)) return;
  WidgetsBinding.instance.addPostFrameCallback((_) {
    signals.dispatch(intent);
  });
}

/// Exposes [AppShortcutSignals] to every route below the shell.
class AppShortcutScope extends InheritedWidget {
  const AppShortcutScope({
    super.key,
    required this.signals,
    this.perform,
    required super.child,
  });

  final AppShortcutSignals signals;

  /// Does what the intent's key does, from a tap: the shell's search button
  /// opens the same command launcher as Ctrl/Cmd+K. Null where no
  /// [AppShortcuts] layer is installed (a bare screen in a test).
  final void Function(Intent intent)? perform;

  static AppShortcutSignals? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppShortcutScope>()?.signals;

  /// For a callback, which may not subscribe: a tap handler only needs the
  /// bus as it is now.
  static AppShortcutSignals? read(BuildContext context) =>
      context.getInheritedWidgetOfExactType<AppShortcutScope>()?.signals;

  /// [perform] of the nearest scope, or null (hide the control that would
  /// use it).
  static void Function(Intent intent)? performOf(BuildContext context) =>
      context.getInheritedWidgetOfExactType<AppShortcutScope>()?.perform;

  @override
  bool updateShouldNotify(AppShortcutScope oldWidget) =>
      signals != oldWidget.signals || perform != oldWidget.perform;
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
/// Off desktop no key binding is installed, so Android keeps exactly the key
/// handling it had — Ctrl+Enter in the composer, and nothing else.
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

  void _returnToShell(Intent intent) {
    final navigator = _navigator;
    if (navigator == null) return;
    dispatchAtShellRoot(navigator, _signals, intent);
  }

  /// Runs [fallback] only when no visible surface claimed the intent.
  Object? _dispatch(Intent intent, void Function(BuildContext) fallback) {
    if (_signals.dispatch(intent)) return null;
    final navigatorContext = _navigator?.context;
    if (navigatorContext != null) fallback(navigatorContext);
    return null;
  }

  /// One intent, whether a key or a tap asked for it.
  Object? _perform(Intent intent) => switch (intent) {
    OpenCommandPaletteIntent() => _dispatch(
      intent,
      (context) => unawaited(
        showCommandPalette(context, widget.handlers.paletteCommands(context)),
      ),
    ),
    NewSessionIntent() => _dispatch(
      intent,
      (_) => widget.handlers.onNewSession(),
    ),
    OpenSettingsIntent() => _dispatch(
      intent,
      (_) => widget.handlers.onOpenSettings(),
    ),
    // maybePop, never pop: a screen guarding unsaved work with PopScope
    // keeps its veto.
    CloseRouteIntent() => _dispatch(
      intent,
      (_) => unawaited(_navigator!.maybePop()),
    ),
    ShowShortcutsHelpIntent() => _dispatch(
      intent,
      (context) => unawaited(showShortcutsHelp(context)),
    ),
    // No shell fallback: only a surface with primary destinations or a find
    // field can service it. Registering it still stops the keystroke from
    // leaking through as a literal character.
    FindInSurfaceIntent() => _dispatch(intent, (_) {}),
    // Shell destinations and the terminal belong to the shell root. From a
    // pushed route (chat, terminal, review) nothing visible claims them, so
    // the fallback returns to the root and asks the shell again.
    SelectDestinationIntent() ||
    OpenChatsIntent() ||
    OpenTerminalIntent() => _dispatch(intent, (_) => _returnToShell(intent)),
    _ => _dispatch(intent, (_) {}),
  };

  void _performFromTap(Intent intent) => _perform(intent);

  @override
  Widget build(BuildContext context) {
    // Off desktop only the bus is installed, with no key bindings: search
    // results use it to reach the shell's tabs from any route, the same way
    // Ctrl+1..3 does here, and the shell's search button opens the launcher.
    if (!desktopInteractions) {
      return AppShortcutScope(
        signals: _signals,
        perform: _performFromTap,
        child: widget.child,
      );
    }
    CallbackAction<T> action<T extends Intent>() =>
        CallbackAction<T>(onInvoke: _perform);
    return AppShortcutScope(
      signals: _signals,
      perform: _performFromTap,
      child: Shortcuts(
        shortcuts: appShortcutBindings,
        child: Actions(
          actions: <Type, Action<Intent>>{
            OpenCommandPaletteIntent: action<OpenCommandPaletteIntent>(),
            NewSessionIntent: action<NewSessionIntent>(),
            OpenSettingsIntent: action<OpenSettingsIntent>(),
            CloseRouteIntent: action<CloseRouteIntent>(),
            ShowShortcutsHelpIntent: action<ShowShortcutsHelpIntent>(),
            FindInSurfaceIntent: action<FindInSurfaceIntent>(),
            SelectDestinationIntent: action<SelectDestinationIntent>(),
            OpenTerminalIntent: action<OpenTerminalIntent>(),
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
    this.keywords,
  });

  final String label;
  final IconData icon;
  final VoidCallback onInvoke;

  /// Secondary line: what the command actually does.
  final String? hint;

  /// The accelerator that reaches the same command, when one exists.
  final String? keys;

  /// Words that find the command without being shown (search-index aliases).
  final String? keywords;
}

/// Opens the searchable command launcher: the kit sheet (a centred panel on
/// a wide window), a search field, and one row per command. Enter runs the
/// first match, which is drawn filled so the choice never rests on colour
/// alone; Arrow Down moves into the rows, which Enter or a tap runs.
Future<void> showCommandPalette(
  BuildContext context,
  List<DesktopCommand> commands,
) {
  if (commands.isEmpty) return Future<void>.value();
  return showKitSheet<void>(
    context,
    sheetKey: const ValueKey('desktop-command-palette'),
    title: _shortcutL10n(context).e7LocaleUiCommandLauncher,
    icon: AppIconography.lightning,
    body: (_) => _CommandPalette(commands: commands),
  );
}

/// Which [commands] a query finds, by the same matcher as Settings' search
/// (lib/domain/settings_search.dart): label, hint and keywords, every word
/// by word, prefix or one typo, in any case; a whole label first. An empty
/// query lists every command in order.
@visibleForTesting
List<DesktopCommand> matchCommands(
  List<DesktopCommand> commands,
  String query,
) {
  if (query.trim().isEmpty) return commands;
  final index = SettingsSearchIndex([
    for (final (i, command) in commands.indexed)
      SettingsSearchDocument(
        id: '$i',
        title: command.label,
        parent: command.hint ?? '',
        aliases: command.keywords ?? '',
        target: const SettingsSearchTarget(pageId: 'command'),
      ),
  ]);
  return [
    for (final document in index.search(query))
      commands[int.parse(document.id)],
  ];
}

class _CommandPalette extends StatefulWidget {
  const _CommandPalette({required this.commands});

  final List<DesktopCommand> commands;

  @override
  State<_CommandPalette> createState() => _CommandPaletteState();
}

class _CommandPaletteState extends State<_CommandPalette> {
  final _query = TextEditingController();

  @override
  void initState() {
    super.initState();
    // Filtering is local and instant; the field's settled callback only
    // drives its own count announcement.
    _query.addListener(_changed);
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  List<DesktopCommand> get _matches =>
      matchCommands(widget.commands, _query.text);

  void _run(DesktopCommand command) {
    KitSheet.close(context);
    command.onInvoke();
  }

  void _runFirst() {
    final matches = _matches;
    if (matches.isNotEmpty) _run(matches.first);
  }

  @override
  void dispose() {
    _query
      ..removeListener(_changed)
      ..dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _shortcutL10n(context);
    final matches = _matches;
    final query = _query.text.trim();
    final showKeys = desktopInteractions;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitSearchField(
          fieldKey: const ValueKey('command-palette-query'),
          controller: _query,
          label: l10n.shortcutsPaletteSearch,
          autofocus: true,
          resultCount: query.isEmpty ? null : matches.length,
          onChanged: (_) {},
          onSubmitted: (_) => _runFirst(),
        ),
        if (matches.isEmpty)
          KitSearchNoMatch(query: query, onClear: _query.clear)
        else
          KitRowGroup(
            children: [
              for (final (index, command) in matches.indexed)
                KitRow(
                  key: ValueKey('command-${command.label}'),
                  leading: KitRow.icon(context, command.icon),
                  title: command.label,
                  supporting: command.hint == null
                      ? null
                      : TextSpan(text: command.hint),
                  // What Enter runs, filled rather than tinted.
                  selected: index == 0 && query.isNotEmpty,
                  trailing: !showKeys || command.keys == null
                      ? null
                      : KitText(
                          command.keys!,
                          role: KitTextRole.mono,
                          tone: KitTextTone.secondary,
                        ),
                  onTap: () => _run(command),
                ),
            ],
          ),
      ],
    );
  }
}

// =====================================================================
// Shortcut help
// =====================================================================

/// The discoverable list of every shortcut, reachable from Ctrl+/ and from
/// the More hub so it is not itself hidden behind a shortcut. One section
/// per place a shortcut works; the keys sit in a mono column at the end of
/// each row and the description wraps rather than being cut.
Future<void> showShortcutsHelp(BuildContext context) {
  final l10n = _shortcutL10n(context);
  final entries = shortcutHelp(l10n);
  return showKitSheet<void>(
    context,
    sheetKey: const ValueKey('keyboard-shortcuts-sheet'),
    title: l10n.e7LocaleUiKeyboardShortcuts,
    icon: AppIconography.keyboard,
    body: (context) => Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final group in ShortcutHelpGroup.values)
          KitRowGroup(
            key: ValueKey('shortcuts-help-${group.name}'),
            label: switch (group) {
              ShortcutHelpGroup.anywhere => l10n.shortcutsHelpAnywhere,
              ShortcutHelpGroup.conversation => l10n.shortcutsHelpConversation,
            },
            leadingIcons: false,
            children: [
              for (final entry in entries)
                if (entry.group == group)
                  KitRow(
                    title: entry.description,
                    titleMaxLines: 4,
                    trailing: KitText(
                      entry.keys,
                      role: KitTextRole.mono,
                      tone: KitTextTone.secondary,
                    ),
                  ),
            ],
          ),
      ],
    ),
  );
}
