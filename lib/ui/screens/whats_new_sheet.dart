import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/share_intent.dart';
import '../../state/connection.dart';
import '../../state/whats_new.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import 'about_screen.dart' show projectWebsiteUrl;
import 'whats_new_places.dart';

/// "What's new in X": a short walkthrough, one new thing per step. Each step
/// is one plain sentence with Next, Back and Skip; a step that has a real
/// control also has "Show me", which closes the walkthrough, goes to that
/// control and highlights it with the app's tour. The last step offers
/// Share. Opens once after an update, from About › What's new and from
/// Help › Tips.
///
/// [controller] lets "Show me" open the pages that need a connection; a
/// step whose page cannot open without it has no "Show me".
Future<void> showWhatsNewSheet(
  BuildContext context, {
  required String version,
  ConnectionController? controller,
}) async {
  final shown = whatsNewVersionFor(version);
  if (shown == null) return;
  final l10n = AppLocalizations.of(context);
  final steps = whatsNewByVersion[shown]!;
  final index = ValueNotifier<int>(0);
  final primary = ValueNotifier<KitAction?>(null);
  final secondary = ValueNotifier<KitAction?>(null);
  WhatsNewTarget? showMe;

  late BuildContext sheetContext;
  void close() => Navigator.of(sheetContext).pop();
  void sync() {
    final i = index.value;
    final last = i == steps.length - 1;
    primary.value = last
        ? (ShareOut.supported
              ? KitAction(
                  key: const ValueKey('whats-new-share'),
                  label: l10n.whatsNewShare,
                  icon: AppIconography.shareNetwork,
                  onPressed: () => unawaited(
                    ShareOut.text(l10n.shareAppMessage(projectWebsiteUrl)),
                  ),
                )
              : KitAction(
                  key: const ValueKey('whats-new-close'),
                  label: l10n.whatsNewClose,
                  onPressed: close,
                ))
        : KitAction(
            key: const ValueKey('whats-new-next'),
            label: l10n.kitTourNext,
            onPressed: () => index.value = i + 1,
          );
    secondary.value = i == 0
        ? null
        : KitAction(
            key: const ValueKey('whats-new-back'),
            label: l10n.whatsNewBack,
            onPressed: () => index.value = i - 1,
          );
  }

  bool canShow(WhatsNewTarget t) => WhatsNewPlaces.available(t, controller);

  index.addListener(sync);
  sync();
  try {
    await showKitSheet<void>(
      context,
      title: l10n.whatsNewTitle(shown),
      icon: AppIconography.sparkle,
      sheetKey: const ValueKey('whats-new-sheet'),
      primaryListenable: primary,
      secondaryListenable: secondary,
      body: (ctx) {
        sheetContext = ctx;
        return ValueListenableBuilder<int>(
          valueListenable: index,
          builder: (context, i, _) {
            final step = steps[i];
            final tokens = KitTokens.of(context);
            final target = step.target;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KitText(
                  l10n.kitTourStep(i + 1, steps.length),
                  key: const ValueKey('whats-new-counter'),
                  role: KitTextRole.secondary,
                ),
                SizedBox(height: tokens.space2),
                KitRow(
                  key: ValueKey('whats-new-step-$i'),
                  leading: KitRow.icon(context, step.icon),
                  title: step.text(l10n),
                  titleMaxLines: 5,
                ),
                SizedBox(height: tokens.space2),
                Wrap(
                  spacing: tokens.space2,
                  children: [
                    if (target != null && canShow(target))
                      KitButton.tertiary(
                        key: const ValueKey('whats-new-show-me'),
                        label: l10n.whatsNewShowMe,
                        onPressed: () {
                          showMe = target;
                          close();
                        },
                      ),
                    if (i < steps.length - 1)
                      KitButton.tertiary(
                        key: const ValueKey('whats-new-skip'),
                        label: l10n.kitTourSkip,
                        onPressed: close,
                      ),
                  ],
                ),
              ],
            );
          },
        );
      },
    );
  } finally {
    index.removeListener(sync);
  }
  final target = showMe;
  if (target != null && context.mounted) {
    await _showMe(context, target, controller);
  }
}

/// Goes to the real control and highlights it with a one-step tour.
Future<void> _showMe(
  BuildContext context,
  WhatsNewTarget target,
  ConnectionController? controller,
) async {
  final navigator = Navigator.of(context);
  final l10n = AppLocalizations.of(context);
  final page = WhatsNewPlaces.page(target, controller);
  BuildContext? scope;
  if (page != null) {
    unawaited(pushKitPage<void>(context, (_) => page));
    scope = navigator.context;
  } else {
    scope = await WhatsNewPlaces.openChat?.call(context, controller!);
  }
  // The page's first frames, then the highlight over its control.
  await Future<void>.delayed(const Duration(milliseconds: 400));
  if (scope == null || !scope.mounted || !navigator.mounted) return;
  await KitTour.show(
    scope,
    steps: [
      KitTourStep(
        anchors: [WhatsNewPlaces.anchor(target)],
        message: WhatsNewPlaces.message(target, l10n),
      ),
    ],
  );
}
