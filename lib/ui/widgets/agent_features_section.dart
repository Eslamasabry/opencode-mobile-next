import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/agent_features.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// The agent's own switches for one conversation (Claude Code's fast mode and
/// whatever else the agent lists), in the model sheet: each is a row that
/// asks the server, shows "Saving…" while it waits, and then shows what the
/// server now has. A refusal leaves the old value and says so in plain words.
/// Nothing is drawn when the agent offers no switches.
class AgentFeaturesSection extends StatefulWidget {
  const AgentFeaturesSection({
    super.key,
    required this.gateway,
    required this.sessionID,
  });

  final AgentFeatureGateway gateway;
  final String sessionID;

  @override
  State<AgentFeaturesSection> createState() => _AgentFeaturesSectionState();
}

class _AgentFeaturesSectionState extends State<AgentFeaturesSection> {
  List<AgentFeature>? _features;
  bool _loadFailed = false;
  String? _saving;
  final _failed = <String>{};

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  @override
  void didUpdateWidget(AgentFeaturesSection old) {
    super.didUpdateWidget(old);
    if (old.sessionID != widget.sessionID || old.gateway != widget.gateway) {
      _features = null;
      _loadFailed = false;
      _failed.clear();
      unawaited(_load());
    }
  }

  Future<void> _load() async {
    final sessionID = widget.sessionID;
    try {
      final features = await widget.gateway.agentFeatures(sessionID);
      if (!mounted || sessionID != widget.sessionID) return;
      setState(() {
        _features = features;
        _loadFailed = false;
      });
    } catch (_) {
      if (!mounted || sessionID != widget.sessionID) return;
      setState(() => _loadFailed = _features == null);
    }
  }

  Future<void> _set(AgentFeature feature, Object value) async {
    if (_saving != null) return;
    final sessionID = widget.sessionID;
    setState(() {
      _saving = feature.id;
      _failed.remove(feature.id);
    });
    try {
      final features = await widget.gateway.setAgentFeature(
        sessionID,
        feature.id,
        value,
      );
      if (!mounted || sessionID != widget.sessionID) return;
      setState(() => _features = features);
    } catch (_) {
      if (!mounted || sessionID != widget.sessionID) return;
      setState(() => _failed.add(feature.id));
    } finally {
      if (mounted) setState(() => _saving = null);
    }
  }

  /// The agent's own wording for a switch it names, in the app's words for
  /// the ones the app knows.
  (String, String?) _words(AppLocalizations copy, AgentFeature feature) =>
      feature.id == 'fast_mode'
      ? (copy.agentFeatureFastTitle, copy.agentFeatureFastDetail)
      : (feature.label, feature.description);

  @override
  Widget build(BuildContext context) {
    final copy =
        Localizations.of<AppLocalizations>(context, AppLocalizations) ??
        lookupAppLocalizations(const Locale('en'));
    final features = _features;
    if (_loadFailed) {
      return KitNotice(
        key: const Key('agent-features-failed'),
        icon: AppIconography.info,
        message: copy.agentFeaturesLoadFailed,
      );
    }
    if (features == null) {
      return KitRowGroup(
        key: const Key('agent-features-loading'),
        margin: EdgeInsets.zero,
        label: copy.agentFeaturesLabel,
        children: [
          KitRow(
            leading: KitRow.icon(context, AppIconography.settings),
            title: copy.agentFeaturesLoading,
            enabled: false,
          ),
        ],
      );
    }
    if (features.isEmpty) return const SizedBox.shrink();
    return KitRowGroup(
      key: const Key('agent-features'),
      margin: EdgeInsets.zero,
      label: copy.agentFeaturesLabel,
      children: [for (final feature in features) _row(context, copy, feature)],
    );
  }

  Widget _row(BuildContext context, AppLocalizations copy, AgentFeature f) {
    final (title, detail) = _words(copy, f);
    final saving = _saving == f.id;
    final failed = _failed.contains(f.id);
    final below = failed
        ? KitNotice(
            key: Key('agent-feature-failed-${f.id}'),
            tone: AppStatusTone.failure,
            message: copy.agentFeatureChangeFailed(title),
          )
        : null;
    if (f.kind == AgentFeatureKind.toggle) {
      return KitSwitchRow(
        key: Key('agent-feature-${f.id}'),
        title: title,
        value: f.on,
        supporting: saving ? copy.agentFeatureSaving : detail,
        below: below,
        onChanged: _saving == null
            ? (value) => unawaited(_set(f, value))
            : null,
      );
    }
    final current = f.options.where((o) => o.id == f.selected).firstOrNull;
    return KitRow(
      key: Key('agent-feature-${f.id}'),
      title: title,
      supporting: TextSpan(
        text: saving ? copy.agentFeatureSaving : current?.label ?? detail ?? '',
      ),
      trailing: const KitChevron(),
      enabled: _saving == null,
      onTap: () => unawaited(_choose(context, f)),
    );
  }

  Future<void> _choose(BuildContext context, AgentFeature f) async {
    await showKitMenu(
      context,
      items: [
        for (final option in f.options)
          KitMenuItem(
            key: Key('agent-feature-${f.id}-${option.id}'),
            label: option.label,
            checked: option.id == f.selected,
            onSelected: () => unawaited(_set(f, option.id)),
          ),
      ],
    );
  }
}
