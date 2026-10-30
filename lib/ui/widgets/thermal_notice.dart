import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../builtin/thermal_guard.dart';
import '../../builtin/thermal_guard_teams.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// The thermal guard's one line in the shell: the AI Team was paused (or
/// stopped) because the phone is hot, then that it resumed. Once per
/// episode, until dismissed; nothing when no guard runs.
class ThermalNoticeLine extends ConsumerWidget {
  const ThermalNoticeLine({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final slot = ref.watch(thermalGuardSlotProvider);
    return ValueListenableBuilder<ThermalGuard?>(
      valueListenable: slot,
      builder: (context, guard, _) {
        if (guard == null) return const KitReveal(child: null);
        return ListenableBuilder(
          listenable: guard,
          builder: (context, _) {
            final notice = guard.notice;
            if (notice == null) return const KitReveal(child: null);
            final l10n = lookupAppLocalizations(
              Localizations.localeOf(context),
            );
            final resumed = notice.kind == ThermalNoticeKind.resumed;
            return KitReveal(
              child: Padding(
                key: ValueKey('thermal-notice-${notice.kind.name}'),
                padding: EdgeInsetsDirectional.fromSTEB(
                  KitTokens.of(context).gutter,
                  KitTokens.of(context).space2,
                  KitTokens.of(context).space1,
                  KitTokens.of(context).space1,
                ),
                child: KitNotice(
                  // Neutral, as its KitStatus twin: amber is needs-you only (LOOK-24).
                  tone: resumed ? AppStatusTone.ok : AppStatusTone.neutral,
                  icon: switch (notice.kind) {
                    ThermalNoticeKind.paused => AppIconography.pause,
                    ThermalNoticeKind.stopped => AppIconography.stopCircle,
                    ThermalNoticeKind.resumed => AppIconography.play,
                  },
                  message: thermalNoticeText(l10n, notice.kind),
                  onDismiss: guard.dismiss,
                ),
              ),
            );
          },
        );
      },
    );
  }
}

/// The thermal condition joins the app-wide status slot on every route.
KitStatus? thermalKitStatus(BuildContext context, ThermalGuard? guard) {
  final notice = guard?.notice;
  if (notice == null) return null;
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  return KitStatus(
    kind: KitStatusKind.heat,
    id: 'heat:${notice.kind.name}',
    key: ValueKey('thermal-notice-${notice.kind.name}'),
    icon: switch (notice.kind) {
      ThermalNoticeKind.paused => AppIconography.pause,
      ThermalNoticeKind.stopped => AppIconography.stopCircle,
      ThermalNoticeKind.resumed => AppIconography.play,
    },
    tone: notice.kind == ThermalNoticeKind.resumed
        ? AppStatusTone.ok
        : AppStatusTone.neutral,
    // Nothing to do: it resumes by itself. One quiet line, the full
    // sentence for a screen reader.
    message: switch (notice.kind) {
      ThermalNoticeKind.paused => l10n.thermalQuietPaused,
      ThermalNoticeKind.stopped => l10n.thermalQuietStopped,
      ThermalNoticeKind.resumed => l10n.thermalQuietResumed,
    },
    semanticsLabel: thermalNoticeText(l10n, notice.kind),
    onDismiss: guard!.dismiss,
    quiet: true,
  );
}
