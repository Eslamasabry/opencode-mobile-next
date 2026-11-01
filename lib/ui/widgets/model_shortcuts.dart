import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/kit_icon_button.dart';
import '../kit/kit_menu.dart';
import '../kit/kit_typing_guard.dart';

/// Where a transcript key moves the chat.
enum TranscriptKey { pageUp, pageDown, oldest, latest }

/// Chat-local shortcuts also work with a hardware keyboard on Android.
/// A sheet or dialog above the chat must keep ownership of the keyboard.
class ModelShortcuts extends StatelessWidget {
  const ModelShortcuts({
    super.key,
    required this.onCycle,
    this.onBackground,
    this.onCycleVariant,
    this.onTranscriptKey,
    required this.child,
  });

  /// F4 / Shift+F4: the next / previous thinking level of the chat's model.
  final Future<void> Function({bool reverse})? onCycleVariant;

  /// PageUp / PageDown / Home / End move the transcript, but only when no
  /// text field has focus (there they move the caret).
  final void Function(TranscriptKey key)? onTranscriptKey;

  final Future<void> Function({bool reverse, bool favoritesOnly}) onCycle;
  final Widget child;
  final Future<void> Function()? onBackground;

  @override
  Widget build(BuildContext context) => Focus(
    canRequestFocus: false,
    skipTraversal: true,
    // Keyboard handling must not merge the chat's independent TalkBack nodes.
    includeSemantics: false,
    onKeyEvent: (_, event) {
      if (!(ModalRoute.of(context)?.isCurrent ?? true)) {
        return KeyEventResult.ignored;
      }
      final background = onBackground;
      if (background != null &&
          const SingleActivator(
            LogicalKeyboardKey.keyB,
            control: true,
            includeRepeats: false,
          ).accepts(event, HardwareKeyboard.instance)) {
        unawaited(background());
        return KeyEventResult.handled;
      }
      final transcript = onTranscriptKey;
      if (transcript != null && event is! KeyUpEvent) {
        final keyboard = HardwareKeyboard.instance;
        final key = switch (event.logicalKey) {
          LogicalKeyboardKey.pageUp => TranscriptKey.pageUp,
          LogicalKeyboardKey.pageDown => TranscriptKey.pageDown,
          LogicalKeyboardKey.home => TranscriptKey.oldest,
          LogicalKeyboardKey.end => TranscriptKey.latest,
          _ => null,
        };
        if (key != null &&
            !keyboard.isControlPressed &&
            !keyboard.isMetaPressed &&
            !keyboard.isAltPressed &&
            !keyboard.isShiftPressed &&
            !kitTypingInField()) {
          transcript(key);
          return KeyEventResult.handled;
        }
      }
      final variant = onCycleVariant;
      if (variant != null) {
        const nextVariant = SingleActivator(
          LogicalKeyboardKey.f4,
          includeRepeats: false,
        );
        const previousVariant = SingleActivator(
          LogicalKeyboardKey.f4,
          shift: true,
          includeRepeats: false,
        );
        final back = previousVariant.accepts(event, HardwareKeyboard.instance);
        if (back || nextVariant.accepts(event, HardwareKeyboard.instance)) {
          unawaited(variant(reverse: back));
          return KeyEventResult.handled;
        }
      }
      const next = SingleActivator(
        LogicalKeyboardKey.f2,
        includeRepeats: false,
      );
      const previous = SingleActivator(
        LogicalKeyboardKey.f2,
        shift: true,
        includeRepeats: false,
      );
      final reverse = previous.accepts(event, HardwareKeyboard.instance);
      if (!reverse && !next.accepts(event, HardwareKeyboard.instance)) {
        return KeyEventResult.ignored;
      }
      unawaited(onCycle(reverse: reverse, favoritesOnly: false));
      return KeyEventResult.handled;
    },
    child: child,
  );
}

/// Esc stops the running reply, on a hardware keyboard (tablet, Chromebook,
/// DeX), and only when nothing else wants the key:
///  - the chat is not running ([enabled] false),
///  - a sheet, dialog or menu is above the chat (not the current route),
///  - a text field is mid-composition (Esc cancels the IME's candidate, not
///    the reply), or a modifier is held,
///  - key repeats (holding Esc stops once, never a second run).
/// An open find bar or selection handles Esc below this node first, since a
/// handled key never bubbles here.
class StopOnEscape extends StatelessWidget {
  const StopOnEscape({
    super.key,
    required this.enabled,
    required this.onStop,
    required this.child,
  });

  final bool enabled;
  final VoidCallback onStop;
  final Widget child;

  static bool _composing() {
    final focus = FocusManager.instance.primaryFocus?.context;
    final editable = focus?.findAncestorStateOfType<EditableTextState>();
    return editable?.widget.controller.value.composing.isValid ?? false;
  }

  @override
  Widget build(BuildContext context) => Focus(
    canRequestFocus: false,
    skipTraversal: true,
    includeSemantics: false,
    onKeyEvent: (_, event) {
      if (!enabled ||
          event is! KeyDownEvent ||
          event.logicalKey != LogicalKeyboardKey.escape ||
          !(ModalRoute.of(context)?.isCurrent ?? true)) {
        return KeyEventResult.ignored;
      }
      final keyboard = HardwareKeyboard.instance;
      if (keyboard.isControlPressed ||
          keyboard.isMetaPressed ||
          keyboard.isAltPressed ||
          keyboard.isShiftPressed ||
          _composing()) {
        return KeyEventResult.ignored;
      }
      onStop();
      return KeyEventResult.handled;
    },
    child: child,
  );
}

/// The model shortcuts as menu items, for the composer's model chip (its
/// long-press, right-click and custom actions) and [ModelCycleButton]. Each
/// item shows its keyboard shortcut on a fine pointer, so F2 and Shift+F2
/// are discoverable without a help page; an item that cannot run yet says
/// why instead of vanishing (STATE-8).
List<KitMenuItem> modelCycleMenuItems(
  AppLocalizations l10n, {
  required Future<void> Function({bool reverse, bool favoritesOnly}) onCycle,
  required bool hasRecent,
  required bool hasFavorites,
}) => [
  KitMenuItem(
    key: const Key('model-cycle-next'),
    icon: AppIconography.swap,
    label: l10n.modelShortcutsNextRecent,
    shortcut: 'F2',
    enabled: hasRecent,
    disabledReason: hasRecent ? null : l10n.modelShortcutsNoRecent,
    onSelected: () => unawaited(onCycle()),
  ),
  KitMenuItem(
    key: const Key('model-cycle-previous'),
    icon: AppIconography.history,
    label: l10n.modelShortcutsPreviousRecent,
    shortcut: 'Shift+F2',
    enabled: hasRecent,
    disabledReason: hasRecent ? null : l10n.modelShortcutsNoRecent,
    onSelected: () => unawaited(onCycle(reverse: true)),
  ),
  KitMenuItem(
    key: const Key('model-cycle-favorite'),
    icon: AppIconography.bookmarks,
    label: l10n.modelNextFavorite,
    enabled: hasFavorites,
    disabledReason: hasFavorites ? null : l10n.modelShortcutsNoFavorite,
    onSelected: () => unawaited(onCycle(favoritesOnly: true)),
  ),
];

/// One touch-sized button that opens the model shortcuts. The composer
/// folds the same items into its model chip's menu; this stays for hosts
/// without a chip.
class ModelCycleButton extends StatelessWidget {
  const ModelCycleButton({
    super.key,
    required this.onCycle,
    required this.hasRecent,
    required this.hasFavorites,
  });

  final Future<void> Function({bool reverse, bool favoritesOnly}) onCycle;
  final bool hasRecent;
  final bool hasFavorites;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Builder(
      builder: (anchor) => KitIconButton(
        icon: AppIconography.swap,
        tooltip: l10n.modelSwitchSession,
        onPressed: () => unawaited(
          showKitMenu(
            anchor,
            semanticsLabel: l10n.kitModelActions,
            items: modelCycleMenuItems(
              l10n,
              onCycle: onCycle,
              hasRecent: hasRecent,
              hasFavorites: hasFavorites,
            ),
          ),
        ),
      ),
    );
  }
}
