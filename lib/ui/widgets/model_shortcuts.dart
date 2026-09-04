import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';

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

/// One compact, touch-sized menu beside the model chip. Both actions stay
/// visible in the menu so hardware shortcuts are discoverable on a phone.
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
  Widget build(BuildContext context) => PopupMenuButton<String>(
    tooltip: lookupAppLocalizations(
      Localizations.localeOf(context),
    ).modelSwitchSession,
    icon: const Icon(Icons.swap_horiz_rounded),
    onSelected: (value) => unawaited(
      onCycle(reverse: value == 'previous', favoritesOnly: value == 'favorite'),
    ),
    itemBuilder: (_) => [
      PopupMenuItem(
        value: 'next',
        enabled: hasRecent,
        child: Text(
          lookupAppLocalizations(
            Localizations.localeOf(context),
          ).modelNextRecent,
        ),
      ),
      PopupMenuItem(
        value: 'previous',
        enabled: hasRecent,
        child: Text(
          lookupAppLocalizations(
            Localizations.localeOf(context),
          ).modelPreviousRecent,
        ),
      ),
      PopupMenuItem(
        value: 'favorite',
        enabled: hasFavorites,
        child: Text(
          lookupAppLocalizations(
            Localizations.localeOf(context),
          ).modelNextFavorite,
        ),
      ),
    ],
  );
}
