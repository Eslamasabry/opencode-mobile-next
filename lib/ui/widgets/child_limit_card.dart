/// The card on Running on this phone that leads to Android 14+ Developer
/// options › "Disable child process restrictions". Shown by
/// [childLimitCard]'s rules only; one notice with its steps, one button
/// (none when this APK cannot open Settings) and a quiet Hide.
///
/// Kit parts only: [KitReveal], [KitNotice].
library;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/child_process_limit.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

class ChildLimitCard extends StatelessWidget {
  const ChildLimitCard({
    super.key,
    required this.kind,
    required this.onOpen,
    required this.onHide,
    this.padding = EdgeInsets.zero,
  });

  /// Around the card only while it shows, so a folded card leaves no gap.
  final EdgeInsetsGeometry padding;

  /// Null folds the card away (150-250 ms, instant under reduced motion).
  final ChildLimitCardKind? kind;
  final VoidCallback onOpen;
  final VoidCallback onHide;

  @override
  Widget build(BuildContext context) {
    final kind = this.kind;
    return KitReveal(
      child: kind == null
          ? null
          : Padding(padding: padding, child: _card(context, kind)),
    );
  }

  Widget _card(BuildContext context, ChildLimitCardKind kind) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final steps = switch (kind) {
      ChildLimitCardKind.openSettings => l10n.childLimitStepsOpen,
      ChildLimitCardKind.enableDeveloperOptions => l10n.childLimitStepsEnable,
      ChildLimitCardKind.instructionsOnly => l10n.childLimitStepsText,
    };
    final openLabel = switch (kind) {
      ChildLimitCardKind.openSettings => l10n.childLimitOpenDeveloper,
      ChildLimitCardKind.enableDeveloperOptions => l10n.childLimitOpenAbout,
      ChildLimitCardKind.instructionsOnly => null,
    };
    return KitNotice(
      key: const ValueKey('child-limit-card'),
      tone: AppStatusTone.neutral,
      icon: AppIconography.info,
      title: l10n.childLimitTitle,
      message: l10n.childLimitBody,
      notes: [steps],
      liveRegion: false,
      actions: [
        if (openLabel != null)
          KitAction(
            key: const ValueKey('child-limit-open'),
            label: openLabel,
            onPressed: onOpen,
          ),
        KitAction(
          key: const ValueKey('child-limit-hide'),
          label: l10n.childLimitHide,
          onPressed: onHide,
          calm: true,
        ),
      ],
    );
  }
}
