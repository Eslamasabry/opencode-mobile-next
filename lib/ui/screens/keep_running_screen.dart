import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../builtin/app_exit_recovery.dart' show appLifecycleBridgeProvider;
import '../../builtin/thermal_guard.dart';
import '../../builtin/thermal_guard_teams.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/app_exit.dart';
import '../../platform/keep_alive_advice.dart';
import '../../state/connection.dart' show connProvider;
import '../app_theme.dart';
import '../kit/kit.dart';
import 'settings_screen.dart'
    show NotificationsPage, NotificationsSettingsScreen;

/// Opens the keep-running controls, which live on the one "Notifications and
/// background" page, scrolled to them.
Future<void> openKeepRunningScreen(BuildContext context) {
  final controller = ProviderScope.containerOf(
    context,
    listen: false,
  ).read(connProvider);
  return pushKitPage<void>(
    context,
    (_) => NotificationsSettingsScreen(
      controller: controller,
      initialSection: 'keep-running',
      page: NotificationsPage.background,
    ),
  );
}

/// The step's words for [maker]'s phones.
({String title, String detail}) keepAliveStepText(
  AppLocalizations l10n,
  KeepAliveStep step,
  PhoneMaker maker, {
  bool batteryAllowed = false,
}) => switch (step.kind) {
  KeepAliveStepKind.battery => (
    title: l10n.keepRunningBatteryTitle,
    detail: batteryAllowed
        ? l10n.keepRunningBatteryDone
        : l10n.keepRunningBatteryDetail,
  ),
  KeepAliveStepKind.lockInRecents => (
    title: l10n.keepRunningLockTitle,
    detail: switch (maker) {
      PhoneMaker.nubia => l10n.keepRunningLockNubia,
      PhoneMaker.samsung => l10n.keepRunningLockSamsung,
      _ => l10n.keepRunningLockOther,
    },
  ),
  KeepAliveStepKind.autostart => (
    title: l10n.keepRunningAutostartTitle,
    detail: maker == PhoneMaker.huawei
        ? l10n.keepRunningAutostartHuawei
        : l10n.keepRunningAutostartDetail,
  ),
  KeepAliveStepKind.background => (
    title: l10n.keepRunningBackgroundTitle,
    detail: switch (maker) {
      PhoneMaker.xiaomi => l10n.keepRunningBackgroundXiaomi,
      PhoneMaker.oppo => l10n.keepRunningBackgroundOppo,
      PhoneMaker.vivo => l10n.keepRunningBackgroundVivo,
      PhoneMaker.samsung => l10n.keepRunningBackgroundSamsung,
      _ => l10n.keepRunningBackgroundOther,
    },
  ),
};

/// Keep running in the background: what to allow on this phone so Android
/// leaves the app (and the OpenCode and AI Team inside it) running. A
/// section of the "Notifications and background" page, reached from the
/// notice after Android closed the app; it never opens by itself.
///
/// Built from kit parts only (screen-system-1): one list of steps ordered
/// by what is left to do (a step already allowed moves to the end with its
/// word), "You're set" once nothing checkable is left, the heat pause, and
/// the limits Android keeps even then (the daily background budget on
/// Android 15 and newer). A settings screen the phone lacks is said in
/// place, not in a snackbar.
class KeepRunningSection extends ConsumerStatefulWidget {
  const KeepRunningSection({super.key});

  @override
  ConsumerState<KeepRunningSection> createState() => _KeepRunningSectionState();
}

class _KeepRunningSectionState extends ConsumerState<KeepRunningSection>
    with WidgetsBindingObserver {
  KeepAliveInfo? _info;
  bool _openFailed = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_load());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  // Back from a settings screen: the battery switch may have changed.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(_load());
  }

  Future<void> _load() async {
    final info = await ref.read(appLifecycleBridgeProvider).keepAliveInfo();
    if (mounted) setState(() => _info = info);
  }

  Future<void> _open(KeepAliveSetting setting) async {
    final opened = await ref
        .read(appLifecycleBridgeProvider)
        .openKeepAliveSetting(setting);
    if (mounted) setState(() => _openFailed = !opened);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final info = _info;
    if (info == null) return const SizedBox.shrink();
    final maker = PhoneMaker.of(info.manufacturer, info.brand);
    final batteryAllowed = info.batteryOptimizationIgnored;
    // On stock Android, App info › Battery › Unrestricted is the same switch
    // as the battery exemption, so once it is allowed nothing is left that
    // the app cannot check. Other makers add their own screens, which the
    // app cannot read, so they never get "You're set".
    final allSet = maker == PhoneMaker.other && batteryAllowed;
    final steps = [
      for (final step in keepAliveSteps(maker))
        if (!(allSet && step.kind == KeepAliveStepKind.background)) step,
    ];
    bool done(KeepAliveStep step) =>
        step.kind == KeepAliveStepKind.battery && batteryAllowed;
    // One list, what is left first; a done step keeps its place in the
    // maker's order among the done ones (owner rule: no state sections).
    final ordered = [
      for (final step in steps)
        if (!done(step)) step,
      for (final step in steps)
        if (done(step)) step,
    ];
    Widget rails(Widget child) => Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
      child: child,
    );
    final intro = rails(
      KitText(
        l10n.keepRunningIntro,
        key: const ValueKey('keep-running-intro'),
        role: KitTextRole.secondary,
      ),
    );
    // Not a lazy list: a search result that means one row (the battery step,
    // the heat pause) must find it laid out.
    final content = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (allSet) ...[
          SizedBox(height: tokens.space3),
          rails(
            KitNotice(
              key: const ValueKey('keep-running-done'),
              tone: AppStatusTone.ok,
              icon: AppIconography.checkCircle,
              title: l10n.keepRunningAllSetTitle,
              message: l10n.keepRunningAllSetBody,
              liveRegion: false,
            ),
          ),
        ],
        if (maker.closesOnSwipe) ...[
          SizedBox(height: tokens.space3),
          rails(
            KitNotice(
              key: const ValueKey('keep-running-swipe'),
              icon: AppIconography.warning,
              message: l10n.keepRunningSwipeWarning,
              liveRegion: false,
            ),
          ),
        ],
        SizedBox(height: tokens.space3),
        intro,
        KitRowGroup(
          label: l10n.keepRunningTitle,
          children: [
            for (final step in ordered)
              _stepRow(context, l10n, step, maker, done(step)),
          ],
        ),
        if (_openFailed) ...[
          SizedBox(height: tokens.space3),
          rails(
            KitNotice(
              key: const ValueKey('keep-running-open-failed'),
              icon: AppIconography.info,
              message: l10n.keepRunningOpenFailed,
            ),
          ),
        ],
        _ThermalGuardGroup(l10n: l10n),
        SizedBox(height: tokens.sectionGap),
        rails(
          KitText(
            l10n.keepRunningDailyLimit,
            key: const ValueKey('keep-running-daily-limit'),
            role: KitTextRole.secondary,
          ),
        ),
        SizedBox(height: tokens.space2),
        rails(KitText(l10n.keepRunningFootnote, role: KitTextRole.secondary)),
      ],
    );
    return content;
  }

  Widget _stepRow(
    BuildContext context,
    AppLocalizations l10n,
    KeepAliveStep step,
    PhoneMaker maker,
    bool allowed,
  ) {
    final roles = KitTokens.of(context).roles;
    final text = keepAliveStepText(l10n, step, maker, batteryAllowed: allowed);
    final icon = switch (step.kind) {
      KeepAliveStepKind.battery =>
        allowed
            ? AppIconography.batteryCharging
            : AppIconography.batteryWarning,
      KeepAliveStepKind.lockInRecents => AppIconography.locked,
      KeepAliveStepKind.autostart => AppIconography.launch,
      KeepAliveStepKind.background => AppIconography.sync,
    };
    final setting = step.setting;
    final opens = setting != null && !allowed;
    // A search result for this step arrives here (KitArrival).
    return KitArrival(
      id: 'keep-running-${step.kind.name}',
      child: KitRow(
        key: ValueKey('keep-running-${step.kind.name}'),
        leading: KitRow.icon(
          context,
          allowed ? AppIconography.check : icon,
          color: allowed ? roles.success : null,
        ),
        title: text.title,
        titleMaxLines: 2,
        supporting: TextSpan(
          text: text.detail,
          style: allowed
              ? KitTokens.of(
                  context,
                ).rowSupporting.copyWith(color: roles.success)
              : null,
        ),
        supportingMaxLines: 4,
        // The row opens Android's own screen for this step; the value says
        // so, since the chevron alone would promise a page in this app.
        trailing: opens ? KitRowValue(l10n.keepRunningOpen) : null,
        onTap: opens ? () => unawaited(_open(setting)) : null,
      ),
    );
  }
}

/// "Pause the AI Team when the phone is hot" (on by default): Android keeps
/// a hot phone running but slow, so the guard pauses the team instead and
/// resumes it once the phone has cooled. Only where the guard runs.
class _ThermalGuardGroup extends ConsumerWidget {
  const _ThermalGuardGroup({required this.l10n});

  final AppLocalizations l10n;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tokens = KitTokens.of(context);
    return ValueListenableBuilder<ThermalGuard?>(
      valueListenable: ref.watch(thermalGuardSlotProvider),
      builder: (context, guard, _) {
        if (guard == null) return const SizedBox.shrink();
        return Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.sectionGap),
          child: ListenableBuilder(
            listenable: guard,
            builder: (context, _) => KitRowGroup(
              children: [
                KitArrival(
                  id: 'keep-running-thermal',
                  child: KitSwitchRow(
                    key: const ValueKey('keep-running-thermal'),
                    switchKey: const ValueKey('keep-running-thermal-switch'),
                    leading: KitRow.icon(context, AppIconography.pause),
                    title: l10n.thermalGuardSetting,
                    supporting: l10n.thermalGuardSettingDetail,
                    value: guard.enabled,
                    onChanged: (value) => unawaited(guard.setEnabled(value)),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
