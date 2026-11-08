import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/provider_usage.dart' show cleanUsageText;
import '../../../domain/usage_subscriptions.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/usage_subscriptions_controller.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import 'usage_actions.dart';
import 'usage_text.dart';

/// Alert levels offered for one plan, besides "same as the others".
const _levels = [50, 60, 70, 80, 90, 100];

/// One subscription: every window with its exact reset time, this plan's own
/// alert level, the reset switch, where it is signed in (with several
/// connections) and the technical details folded away.
class UsageSubscriptionScreen extends StatefulWidget {
  const UsageSubscriptionScreen({
    super.key,
    required this.controller,
    required this.usage,
    required this.subscriptionKey,
    this.launcher,
    this.clock,
    this.phoneHasAgent,
  });

  final ConnectionController controller;
  final UsageSubscriptionsController usage;
  final String subscriptionKey;
  final UsageSignInLauncher? launcher;
  final DateTime Function()? clock;

  /// Test seam: whether this phone has [providerId]'s agent installed.
  /// Defaults to what the phone's agent list says.
  final bool Function(String providerId)? phoneHasAgent;

  @override
  State<UsageSubscriptionScreen> createState() =>
      _UsageSubscriptionScreenState();
}

class _UsageSubscriptionScreenState extends State<UsageSubscriptionScreen> {
  String? _notice;
  bool _saveFailed = false;

  UsageSignInLauncher get _launcher =>
      widget.launcher ?? ConnectionSignInLauncher(widget.controller);

  Future<void> _save(Future<bool> Function() write) async {
    final saved = await write();
    if (mounted) setState(() => _saveFailed = !saved);
  }

  Future<void> _signIn(UsageSubscription s, UsageConnection where) async {
    setState(() => _notice = null);
    final outcome = await startUsageSignIn(
      context,
      controller: widget.controller,
      launcher: _launcher,
      providerId: s.providerId,
      connection: where,
      action: s.fix == UsageFix.addKey ? 'add_key' : 'sign_in',
    );
    if (!mounted) return;
    setState(() {
      _notice = outcome == UsageSignInOutcome.opened
          ? null
          : usageOutcomeWords(usageStrings(context), outcome);
    });
  }

  Future<void> _computerOff(UsageConnection c) async {
    await widget.controller.computerUsage.turnOff(c.id);
    await widget.usage.refresh();
    if (mounted) setState(() {});
  }

  /// This phone is listed for a plan only when that plan's agent is installed
  /// on it (Claude Code for Claude, the Codex agent for Codex). Stopped is
  /// "Not running", not "Offline"; a missing agent is not listed at all.
  bool _phoneHasAgent(UsageConnection c, String providerId) {
    if (c.kind != UsageConnectionKind.phoneAgents) return true;
    final seam = widget.phoneHasAgent;
    if (seam != null) return seam(providerId);
    return widget.controller.agentRows.any(
      (row) => row.id == providerId && row.installedOnPhone,
    );
  }

  String _connectionState(
    AppLocalizations l10n,
    UsageSubscription s,
    SignedInOn on,
  ) {
    final parts = <String>[
      switch (on.state) {
        SignInState.signedIn => l10n.usageStateSignedIn,
        SignInState.expired => l10n.usageStateExpired,
        SignInState.notSignedIn => l10n.usageStateNotSignedIn,
      },
      if (!on.online)
        on.connection.kind == UsageConnectionKind.phoneAgents
            ? l10n.usagePhoneNotRunning
            : l10n.usageConnOffline,
      if (on.readsFromHere)
        l10n.usageConnReadsHere
      else if (on.state == SignInState.signedIn && s.readFrom != null)
        l10n.usageSameAccount,
    ];
    return parts.join(' · ');
  }

  @override
  Widget build(BuildContext context) {
    final l10n = usageStrings(context);
    final tokens = KitTokens.of(context);
    return ListenableBuilder(
      listenable: widget.usage,
      builder: (context, _) {
        final usage = widget.usage;
        final s = usage.subscription(widget.subscriptionKey);
        if (s == null) {
          return KitScreen(
            topBar: KitTopBar(title: l10n.settingsHubGroupUsage),
            body: KitStateView(
              key: const ValueKey('usage-sub-gone'),
              icon: AppIconography.info,
              title: l10n.usageSubGoneTitle,
              body: l10n.usageSubGoneBody,
            ),
          );
        }
        final snapshot = usage.snapshot;
        final now = (widget.clock ?? DateTime.now)();
        final name = UsageText.subscriptionName(l10n, s);
        final plan = UsageText.plan(s.planLabel);
        final several = snapshot.hasSeveralConnections;
        final fetched = s.fetchedAt;
        final readLine = fetched == null
            ? null
            : l10n.usageReadAgo(
                UsageText.duration(l10n, now.difference(fetched)),
              );
        final header = [?plan, ?readLine].join(' · ');
        final message = UsageText.stateMessage(l10n, snapshot, s, now);
        final fixLabel = s.fix == UsageFix.none
            ? null
            : usageFixLabel(l10n, s, (c) => UsageText.connection(l10n, c));
        // Computers read through OpenCode for this plan, each with its switch.
        final computers = <UsageConnection>[
          for (final c in {
            ...s.signedInOn.map((on) => on.connection),
            ?s.readFrom,
          })
            if (c.kind == UsageConnectionKind.openCodeServer &&
                widget.controller.computerUsage.isEnabled(c.id))
              c,
        ];
        final levels = {..._levels, s.alertThreshold.round()}.toList()..sort();
        final accountText = _isDisplayableAccount(s.accountRef)
            ? cleanUsageText(s.accountRef, 64)
            : null;
        final others = <UsageConnection>[
          if (several)
            for (final c in usageAddChoices(
              widget.controller,
              snapshot,
              s.providerId,
            ))
              if (!s.signedInOn.any((on) => on.connection == c) &&
                  _phoneHasAgent(c, s.providerId))
                c,
        ];
        final windows = s.windows;
        final calm = s.state == SubscriptionState.offlineStale;
        return KitScreen(
          topBar: KitTopBar(
            title: name,
            actions: [
              KitAction(
                key: const ValueKey('usage-sub-refresh'),
                label: l10n.usagePlansRefresh,
                icon: AppIconography.retry,
                onPressed: () => unawaited(usage.refresh(force: true)),
                working: usage.refreshing,
              ),
            ],
          ),
          width: KitScreenWidth.reading,
          body: ListView(
            key: const ValueKey('usage-subscription'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              KitRowGroup(
                children: [
                  KitRow(
                    key: const ValueKey('usage-sub-header'),
                    leading: UsageText.logo(s.providerId),
                    title: name,
                    titleMaxLines: 2,
                    supporting: header.isEmpty ? null : TextSpan(text: header),
                    supportingMaxLines: 2,
                  ),
                ],
              ),
              if (_notice case final notice?)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    top: tokens.space3,
                  ),
                  child: KitNotice(
                    key: const ValueKey('usage-sub-notice'),
                    icon: AppIconography.info,
                    message: notice,
                  ),
                ),
              if (message != null)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    top: tokens.space3,
                  ),
                  child: KitText(
                    message,
                    key: const ValueKey('usage-sub-message'),
                    role: KitTextRole.body,
                  ),
                ),
              if (fixLabel != null)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    top: tokens.space3,
                  ),
                  child: Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: KitButton.secondary(
                      key: const ValueKey('usage-sub-fix'),
                      label: fixLabel,
                      expand: false,
                      maxLines: 3,
                      onPressed: () => unawaited(
                        runUsageFix(
                          context,
                          controller: widget.controller,
                          usage: usage,
                          launcher: _launcher,
                          subscription: s,
                        ).then((outcome) {
                          if (!mounted) return;
                          setState(
                            () => _notice =
                                outcome == null ||
                                    outcome == UsageSignInOutcome.opened
                                ? null
                                : usageOutcomeWords(
                                    usageStrings(context),
                                    outcome,
                                  ),
                          );
                        }),
                      ),
                    ),
                  ),
                ),
              if (windows.isNotEmpty) ...[
                SizedBox(height: tokens.space3),
                Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.gutter,
                  ),
                  child: KitPanel(
                    key: const ValueKey('usage-sub-windows'),
                    padding: EdgeInsetsDirectional.symmetric(
                      vertical: tokens.space2,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        for (final window in windows)
                          KitWindowRow(
                            key: ValueKey('usage-sub-window-${window.id}'),
                            title: UsageText.windowTitle(l10n, window),
                            left: window.leftPercent / 100,
                            valueLabel: UsageText.left(
                              l10n,
                              window.leftPercent,
                            ),
                            tone: calm
                                ? KitWindowTone.calm
                                : window.leftPercent <= 0
                                ? KitWindowTone.out
                                : window.usedPercent >= s.alertThreshold
                                ? KitWindowTone.low
                                : KitWindowTone.normal,
                            note:
                                UsageText.resetsDetail(
                                  context,
                                  window,
                                  now,
                                ).isEmpty
                                ? null
                                : UsageText.resetsDetail(context, window, now),
                          ),
                      ],
                    ),
                  ),
                ),
              ],
              if (windows.isNotEmpty) ...[
                SizedBox(height: tokens.sectionGap),
                KitRowGroup(
                  label: l10n.usageAlertsTitle,
                  children: [
                    KitPickerRow<int>(
                      rowKey: const ValueKey('usage-sub-alert-at'),
                      leading: KitRow.icon(
                        context,
                        AppIconography.notificationImportant,
                      ),
                      title: l10n.usageAlertAt,
                      supporting: s.hasAlertOverride
                          ? l10n.usageAlertAtPlan(
                              name,
                              usage.defaultThreshold.round(),
                            )
                          : l10n.usageAlertAtSame,
                      valueLabel: l10n.usageUsedPercent(
                        s.alertThreshold.round(),
                      ),
                      selected: s.hasAlertOverride
                          ? s.alertThreshold.round()
                          : -1,
                      choices: [
                        KitChoice(
                          value: -1,
                          title: l10n.usageAlertSameAsOthers(
                            usage.defaultThreshold.round(),
                          ),
                        ),
                        for (final level in levels)
                          KitChoice(
                            value: level,
                            title: l10n.usageUsedPercent(level),
                          ),
                      ],
                      onSelected: (level) => _save(
                        () => usage.setThresholdFor(
                          s.key,
                          level < 0 ? null : level.toDouble(),
                        ),
                      ),
                    ),
                    KitSwitchRow(
                      key: const ValueKey('usage-sub-notify-reset'),
                      leading: KitRow.icon(context, AppIconography.timer),
                      title: l10n.usageNotifyReset,
                      supporting: l10n.usageNotifyResetHelp,
                      value: s.notifyOnReset,
                      onChanged: (value) =>
                          _save(() => usage.setNotifyOnReset(s.key, value)),
                    ),
                  ],
                ),
              ],
              if (_saveFailed)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    top: tokens.space3,
                  ),
                  child: KitNotice.error(
                    key: const ValueKey('usage-sub-save-failed'),
                    message: l10n.usageSaveFailed,
                  ),
                ),
              if (computers.isNotEmpty) ...[
                SizedBox(height: tokens.sectionGap),
                KitRowGroup(
                  key: const ValueKey('usage-sub-computer-reader'),
                  children: [
                    for (final c in computers)
                      KitRow(
                        key: ValueKey('usage-sub-computer-${c.id}'),
                        leading: KitRow.icon(context, AppIconography.computer),
                        title: l10n.usageComputerRowTitle(
                          UsageText.connection(l10n, c),
                        ),
                        titleMaxLines: 2,
                        supporting: usage.readerProblemFor(c.id) == null
                            ? null
                            : TextSpan(
                                text: UsageText.computerProblem(
                                  l10n,
                                  usage.readerProblemFor(c.id)!,
                                  UsageText.connection(l10n, c),
                                ),
                              ),
                        supportingMaxLines: 3,
                        trailing: KitButton.tertiary(
                          key: ValueKey('usage-sub-computer-off-${c.id}'),
                          label: l10n.usageComputerTurnOff,
                          onPressed: () => unawaited(_computerOff(c)),
                        ),
                      ),
                  ],
                ),
              ],
              if (several) ...[
                SizedBox(height: tokens.sectionGap),
                KitRowGroup(
                  key: const ValueKey('usage-sub-signed-in'),
                  label: l10n.usageConnectionsHeader,
                  children: [
                    for (final on in s.signedInOn)
                      KitRow(
                        key: ValueKey('usage-sub-on-${on.connection.id}'),
                        leading: KitRow.icon(
                          context,
                          on.connection.kind == UsageConnectionKind.phoneAgents
                              ? AppIconography.phone
                              : AppIconography.computer,
                        ),
                        title: UsageText.connection(l10n, on.connection),
                        titleMaxLines: 2,
                        supporting: TextSpan(
                          text: _connectionState(l10n, s, on),
                        ),
                        supportingMaxLines: 3,
                        trailing: on.state == SignInState.signedIn
                            ? null
                            : KitButton.tertiary(
                                key: ValueKey(
                                  'usage-sub-sign-in-${on.connection.id}',
                                ),
                                label: l10n.usageSignIn,
                                onPressed: () =>
                                    unawaited(_signIn(s, on.connection)),
                              ),
                      ),
                    for (final c in others)
                      KitRow(
                        key: ValueKey('usage-sub-on-${c.id}'),
                        leading: KitRow.icon(
                          context,
                          c.kind == UsageConnectionKind.phoneAgents
                              ? AppIconography.phone
                              : AppIconography.computer,
                        ),
                        title: UsageText.connection(l10n, c),
                        titleMaxLines: 2,
                        supporting: TextSpan(
                          text: snapshot.isOffline(c)
                              ? (c.kind == UsageConnectionKind.phoneAgents
                                    ? l10n.usagePhoneNotRunning
                                    : l10n.usageConnOffline)
                              : snapshot.hasAnswered(c)
                              ? l10n.usageStateNotSignedIn
                              : l10n.usageStateCouldntCheck,
                        ),
                        supportingMaxLines: 3,
                        trailing: snapshot.isOffline(c)
                            ? null
                            : KitButton.tertiary(
                                key: ValueKey('usage-sub-sign-in-${c.id}'),
                                label: l10n.usageSignIn,
                                onPressed: () => unawaited(_signIn(s, c)),
                              ),
                      ),
                  ],
                ),
              ],
              if (several) ...[
                SizedBox(height: tokens.sectionGap),
                Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.gutter,
                  ),
                  child: KitDetailsFold(
                    key: const ValueKey('usage-sub-details'),
                    label: l10n.usageDetails,
                    values: [
                      if (fetched == null)
                        KitTechnicalValue(
                          l10n.usageDetailRead,
                          l10n.usageNotReadYet,
                          copyable: false,
                        ),
                      if (fetched != null && s.readFrom != null)
                        KitTechnicalValue(
                          l10n.usageDetailReader,
                          UsageText.connection(l10n, s.readFrom!),
                          copyable: false,
                        ),
                      if (fetched != null)
                        KitTechnicalValue(
                          l10n.usageDetailRead,
                          KitTime.moment(context, fetched, now: now),
                          copyable: false,
                        ),
                      if (s.checkedAt != null)
                        KitTechnicalValue(
                          l10n.usageDetailChecked,
                          KitTime.moment(context, s.checkedAt!, now: now),
                          copyable: false,
                        ),
                      if (fetched != null ||
                          accountText != null ||
                          s.accountRef != null)
                        KitTechnicalValue(
                          l10n.usageDetailAccount,
                          accountText ??
                              (s.accountRef != null
                                  ? l10n.usageYes
                                  : l10n.usageNo),
                          copyable: accountText != null,
                        ),
                      for (final c in computers)
                        if (usage.readerProblemFor(c.id) case final problem?)
                          KitTechnicalValue(
                            l10n.usageDetailLastCheck,
                            UsageText.computerProblem(
                              l10n,
                              problem,
                              UsageText.connection(l10n, c),
                            ),
                            copyable: false,
                          ),
                      if (windows.isNotEmpty)
                        KitTechnicalValue(
                          l10n.usageDetailWindows,
                          windows.map((w) => w.id).join(', '),
                          copyable: false,
                        ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        );
      },
    );
  }
}

final _opaqueHashPattern = RegExp(r'^[a-f0-9]{64}$');
bool _isDisplayableAccount(String? ref) {
  if (ref == null || ref.isEmpty) return false;
  if (ref.length <= 2 || _opaqueHashPattern.hasMatch(ref)) return false;
  return ref.contains('@') || !RegExp(r'^[a-f0-9]+$').hasMatch(ref);
}
