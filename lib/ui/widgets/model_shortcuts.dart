import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/kit_icon_button.dart';
import '../kit/kit_menu.dart';

/// Chat-local shortcuts also work with a hardware keyboard on Android.
/// A sheet or dialog above the chat must keep ownership of the keyboard.
class ModelShortcuts extends StatelessWidget {
  const ModelShortcuts({
    super.key,
    required this.onCycle,
    this.onBackground,
    required this.child,
  });

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
