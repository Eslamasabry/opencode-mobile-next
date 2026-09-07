import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../domain/agent_account.dart';
import '../../l10n/app_localizations.dart';
import '../../state/agent_account.dart';
import '../../state/connection.dart';
import '../widgets/external_link.dart';

/// Pins the route to the profile/location the user opened. A reconnect may
/// refresh that account, but never recreates a pending login.
class AgentAccountScreen extends StatefulWidget {
  final ConnectionController connection;
  const AgentAccountScreen({super.key, required this.connection});
  @override
  State<AgentAccountScreen> createState() => _AgentAccountScreenState();
}

class _AgentAccountScreenState extends State<AgentAccountScreen>
    with WidgetsBindingObserver {
  late final String? _profileId;
  late final int _location;
  late final Object? _gateway;
  late final String _name;
  AgentAccountController? _controller;
  bool _scopeLost = false;

  @override
  void initState() {
    super.initState();
    final connection = widget.connection;
    _profileId = connection.profile?.id;
    _location = connection.locationRevision;
    _gateway = connection.api;
    _name = connection.profile?.name ?? '';
    connection.addListener(_sync);
    WidgetsBinding.instance.addObserver(this);
    _bind();
  }

  void _bind() {
    final gateway = widget.connection.api;
    if (!widget.connection.isConnected ||
        !widget.connection.capabilities.agentAccount ||
        gateway is! AgentAccountGateway)
      return;
    _controller?.dispose();
    _controller = AgentAccountController(gateway.openAccountSession());
    unawaited(_controller!.refresh());
  }

  void _sync() {
    if (!mounted || _scopeLost) return;
    final connection = widget.connection;
    if (connection.profile?.id != _profileId ||
        connection.locationRevision != _location ||
        !identical(connection.api, _gateway)) {
      _scopeLost = true;
      _controller?.dispose();
      _controller = null;
      setState(() {});
      return;
    }
    if (!connection.isConnected) {
      _controller?.invalidate();
    } else if (_controller == null || !_controller!.session.active) {
      _bind();
      setState(() {});
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_scopeLost) {
      _sync();
      final controller = _controller;
      if (controller != null && controller.session.active)
        unawaited(controller.refresh());
    }
  }

  @override
  void dispose() {
    widget.connection.removeListener(_sync);
    WidgetsBinding.instance.removeObserver(this);
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    if (controller == null) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.agentAccountTitle)),
        body: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(l10n.agentAccountScopeLost),
        ),
      );
    }
    return AgentAccountPanel(controller: controller, profileName: _name);
  }
}

/// Rendered product surface, also used by synthetic widget capture fixtures.
class AgentAccountPanel extends StatelessWidget {
  final AgentAccountController controller;
  final String profileName;
  const AgentAccountPanel({
    super.key,
    required this.controller,
    required this.profileName,
  });

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller,
    builder: (context, _) {
      final l = lookupAppLocalizations(Localizations.localeOf(context));
      final theme = Theme.of(context);
      final colors = theme.colorScheme;
      final account = controller.account;
      final ready = controller.status == AccountPanelStatus.ready;
      final headline = switch (controller.status) {
        AccountPanelStatus.loading => l.agentAccountLoading,
        AccountPanelStatus.unavailable => l.agentAccountUnavailable,
        AccountPanelStatus.error => l.agentAccountReadFailed,
        AccountPanelStatus.disconnected => l.agentAccountDisconnected,
        AccountPanelStatus.ready =>
          account!.signedIn
              ? l.agentAccountConnected
              : account.requiresSignIn
              ? l.agentAccountSignedOut
              : l.agentAccountNoAuth,
      };
      return Scaffold(
        appBar: AppBar(
          title: Text(l.agentAccountTitle),
          actions: [
            IconButton(
              tooltip: l.agentAccountRefresh,
              onPressed:
                  controller.status == AccountPanelStatus.loading ||
                      !controller.session.active
                  ? null
                  : controller.refresh,
              icon: const Icon(Icons.refresh_rounded),
            ),
          ],
        ),
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 620),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      profileName,
                      style: theme.textTheme.labelLarge?.copyWith(
                        color: colors.primary,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Container(
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: colors.primaryContainer.withValues(alpha: .42),
                        borderRadius: BorderRadius.circular(24),
                        border: Border.all(
                          color: colors.primary.withValues(alpha: .18),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(
                            ready && account!.signedIn
                                ? Icons.verified_user_outlined
                                : Icons.account_circle_outlined,
                            color: colors.primary,
                            size: 32,
                          ),
                          const SizedBox(height: 18),
                          Semantics(
                            liveRegion: true,
                            child: Text(
                              headline,
                              style: theme.textTheme.headlineSmall?.copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          if (account?.email case final email?) ...[
                            const SizedBox(height: 8),
                            Text(email, style: theme.textTheme.bodyLarge),
                          ],
                          if (ready && account!.signedIn) ...[
                            const SizedBox(height: 12),
                            Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              children: [
                                _badge(context, switch (account.type) {
                                  'chatgpt' => 'ChatGPT',
                                  'apiKey' => l.agentAccountApiKey,
                                  'amazonBedrock' => 'Amazon Bedrock',
                                  _ => l.agentAccountHostAuth,
                                }),
                                if (account.plan case final plan?)
                                  _badge(context, l.agentAccountPlan(plan)),
                              ],
                            ),
                          ],
                          const SizedBox(height: 16),
                          Text(
                            l.agentAccountHostNote,
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: colors.onSurfaceVariant,
                            ),
                          ),
                          if (controller.status ==
                              AccountPanelStatus.loading) ...[
                            const SizedBox(height: 20),
                            const LinearProgressIndicator(),
                          ],
                        ],
                      ),
                    ),
                    if (controller.status ==
                        AccountPanelStatus.unavailable) ...[
                      const SizedBox(height: 16),
                      Text(l.agentAccountUnsupportedDetail),
                    ],
                    if (controller.status ==
                        AccountPanelStatus.disconnected) ...[
                      const SizedBox(height: 16),
                      Text(l.agentAccountReconnectDetail),
                    ],
                    if (controller.canSignIn) ...[
                      const SizedBox(height: 20),
                      FilledButton.icon(
                        onPressed: controller.signIn,
                        icon: const Icon(Icons.login_rounded),
                        label: Text(l.agentAccountSignIn),
                      ),
                      const SizedBox(height: 10),
                      Text(
                        l.agentAccountSignInNote,
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                    if (controller.loginStatus != AccountLoginStatus.idle) ...[
                      const SizedBox(height: 20),
                      _login(context, l),
                    ],
                    if (ready && account!.signedIn) ...[
                      const SizedBox(height: 28),
                      Text(
                        l.agentAccountLimits,
                        style: theme.textTheme.titleLarge,
                      ),
                      const SizedBox(height: 12),
                      if (controller.metricsLoading &&
                          controller.limits == null)
                        const LinearProgressIndicator(),
                      if (controller.limits == null ||
                          controller.limits!.every(
                            (bucket) =>
                                bucket.primary == null &&
                                bucket.secondary == null,
                          ))
                        _detail(context, l.agentAccountLimitsUnavailable)
                      else
                        for (final bucket in controller.limits!)
                          _bucket(context, l, bucket),
                      const SizedBox(height: 24),
                      Text(
                        l.agentAccountUsage,
                        style: theme.textTheme.titleLarge,
                      ),
                      const SizedBox(height: 12),
                      if (controller.metricsLoading)
                        const LinearProgressIndicator(),
                      if (controller.usage?.lifetimeTokens == null &&
                          controller.usage?.peakDailyTokens == null)
                        _detail(context, l.agentAccountUsageUnavailable)
                      else
                        Wrap(
                          spacing: 12,
                          runSpacing: 12,
                          children: [
                            if (controller.usage?.lifetimeTokens
                                case final value?)
                              _metric(
                                context,
                                l.agentAccountLifetimeTokens,
                                value,
                              ),
                            if (controller.usage?.peakDailyTokens
                                case final value?)
                              _metric(context, l.agentAccountPeakTokens, value),
                          ],
                        ),
                      const SizedBox(height: 12),
                      Text(
                        l.agentAccountUsageNote,
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                    if (controller.updatedAt case final at?) ...[
                      const SizedBox(height: 24),
                      Text(
                        l.agentAccountUpdated(_time(context, at)),
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: colors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    },
  );

  Widget _login(BuildContext context, AppLocalizations l) {
    final status = controller.loginStatus;
    final code = controller.deviceCode;
    final text = switch (status) {
      AccountLoginStatus.starting => l.agentAccountStarting,
      AccountLoginStatus.waiting => l.agentAccountWaiting,
      AccountLoginStatus.cancelling => l.agentAccountCancelling,
      AccountLoginStatus.cancelled => l.agentAccountCancelled,
      AccountLoginStatus.failed => l.agentAccountLoginFailed,
      AccountLoginStatus.uncertain => l.agentAccountLoginUncertain,
      AccountLoginStatus.completed => l.agentAccountLoginCompleted,
      AccountLoginStatus.idle => '',
    };
    return Card.outlined(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              liveRegion: true,
              child: Text(text, style: Theme.of(context).textTheme.titleMedium),
            ),
            if (status == AccountLoginStatus.starting ||
                status == AccountLoginStatus.cancelling) ...[
              const SizedBox(height: 16),
              const LinearProgressIndicator(),
            ],
            if (code != null) ...[
              const SizedBox(height: 16),
              Text(l.agentAccountCodeHint),
              const SizedBox(height: 12),
              SelectableText(
                code.userCode,
                style: Theme.of(context).textTheme.headlineMedium
                    ?.copyWith(fontFamily: 'AppMono', letterSpacing: 2),
              ),
              const SizedBox(height: 16),
              FilledButton.tonalIcon(
                onPressed: () =>
                    openExternalLink(context, code.verificationUrl),
                icon: const Icon(Icons.open_in_new_rounded),
                label: Text(l.agentAccountOpenSignIn),
              ),
              const SizedBox(height: 8),
              const Text('auth.openai.com'),
            ],
            if (controller.loginPending) ...[
              const SizedBox(height: 12),
              OutlinedButton(
                onPressed: status == AccountLoginStatus.cancelling
                    ? null
                    : controller.cancel,
                child: Text(l.agentAccountCancel),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _bucket(
    BuildContext context,
    AppLocalizations l,
    AccountRateBucket bucket,
  ) => Card.filled(
    margin: const EdgeInsets.only(bottom: 12),
    child: Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            bucket.name ?? l.agentAccountAllowance,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          if (bucket.primary case final window?) _window(context, l, window),
          if (bucket.secondary case final window?) _window(context, l, window),
        ],
      ),
    ),
  );

  Widget _window(
    BuildContext context,
    AppLocalizations l,
    AccountRateWindow window,
  ) => Padding(
    padding: const EdgeInsets.only(top: 16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l.agentAccountPercentUsed(window.usedPercent),
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        LinearProgressIndicator(
          value: (window.usedPercent / 100).clamp(0, 1),
          minHeight: 7,
          borderRadius: BorderRadius.circular(8),
          semanticsLabel: l.agentAccountPercentUsed(window.usedPercent),
        ),
        const SizedBox(height: 8),
        Text(
          window.durationMinutes == null
              ? l.agentAccountWindowUnknown
              : l.agentAccountWindowMinutes(window.durationMinutes!),
        ),
        Text(
          window.resetsAt == null
              ? l.agentAccountResetUnknown
              : l.agentAccountReset(_time(context, window.resetsAt!)),
        ),
      ],
    ),
  );

  Widget _metric(BuildContext context, String label, int value) => Container(
    padding: const EdgeInsets.all(20),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surfaceContainer,
      borderRadius: BorderRadius.circular(16),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          NumberFormat.decimalPattern(
            Localizations.localeOf(context).toLanguageTag(),
          ).format(value),
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 6),
        Text(label),
      ],
    ),
  );
  Widget _detail(BuildContext context, String text) => Card.filled(
    margin: EdgeInsets.zero,
    child: Padding(padding: const EdgeInsets.all(20), child: Text(text)),
  );
  Widget _badge(BuildContext context, String text) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surface,
      borderRadius: BorderRadius.circular(10),
    ),
    child: Text(text, style: Theme.of(context).textTheme.labelLarge),
  );
  String _time(BuildContext context, DateTime at) =>
      DateFormat.yMMMd(Localizations.localeOf(context).toLanguageTag())
          .add_jm()
          .format(at.toLocal());
}
