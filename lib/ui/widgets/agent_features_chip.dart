import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/agent_features.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

AppLocalizations _copyOf(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));

/// One conversation's agent switches (Claude Code's fast mode and the like),
/// read from and saved to the server. The composer's chip and the settings
/// sheet share one, so the chip shows what the sheet just saved.
class AgentFeaturesModel extends ChangeNotifier {
  AgentFeaturesModel(this.gateway, this.sessionID);

  final AgentFeatureGateway gateway;
  final String sessionID;

  /// Null until the first read; empty when the agent offers none.
  List<AgentFeature>? features;
  bool loadFailed = false;
  String? saving;
  final failed = <String>{};
  bool _disposed = false;

  String? get agentName => gateway.agentFeaturesOwner(sessionID);

  Future<void> load() async {
    try {
      final read = await gateway.agentFeatures(sessionID);
      if (_disposed) return;
      features = read;
      loadFailed = false;
    } catch (_) {
      if (_disposed) return;
      loadFailed = true;
    }
    notifyListeners();
  }

  Future<void> set(AgentFeature feature, Object value) async {
    if (saving != null) return;
    saving = feature.id;
    failed.remove(feature.id);
    notifyListeners();
    try {
      final read = await gateway.setAgentFeature(sessionID, feature.id, value);
      if (_disposed) return;
      features = read;
    } catch (_) {
      if (_disposed) return;
      failed.add(feature.id);
    }
    saving = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

/// The agent's own words for a switch the app knows, else the agent's label.
(String, String?) agentFeatureWords(AppLocalizations copy, AgentFeature f) =>
    f.id == 'fast_mode'
    ? (copy.agentFeatureFastTitle, copy.agentFeatureFastDetail)
    : (f.label, f.description);

/// The chip in the composer's chip row: "Fast" lit when on and plain when
/// off (a screen reader says "Fast mode, on"); with several switches it is
/// the agent's name with a settings icon.
/// It opens the agent's settings sheet. Nothing is drawn when the agent
/// offers no switches (or they could not be read).
class AgentFeaturesChip extends StatefulWidget {
  const AgentFeaturesChip({
    super.key,
    required this.gateway,
    required this.sessionID,
    this.refreshKey,
  });

  final AgentFeatureGateway gateway;
  final String sessionID;

  /// Changes when what the agent offers may have (its model changed).
  final Object? refreshKey;

  @override
  State<AgentFeaturesChip> createState() => _AgentFeaturesChipState();
}

class _AgentFeaturesChipState extends State<AgentFeaturesChip> {
  late AgentFeaturesModel _model;

  @override
  void initState() {
    super.initState();
    _model = AgentFeaturesModel(widget.gateway, widget.sessionID);
    unawaited(_model.load());
  }

  @override
  void didUpdateWidget(AgentFeaturesChip old) {
    super.didUpdateWidget(old);
    if (old.sessionID != widget.sessionID || old.gateway != widget.gateway) {
      _model.dispose();
      _model = AgentFeaturesModel(widget.gateway, widget.sessionID);
      unawaited(_model.load());
    } else if (old.refreshKey != widget.refreshKey) {
      unawaited(_model.load());
    }
  }

  @override
  void dispose() {
    _model.dispose();
    super.dispose();
  }

  String _sheetTitle(AppLocalizations copy) {
    final name = _model.agentName;
    return name == null
        ? copy.agentFeaturesLabel
        : copy.agentFeaturesSheetTitle(name);
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _model,
    builder: (context, _) {
      final features = _model.features;
      if (features == null || features.isEmpty) return const SizedBox.shrink();
      final copy = _copyOf(context);
      final toggles = features
          .where((f) => f.kind == AgentFeatureKind.toggle)
          .toList();
      final single = features.length == 1 && toggles.length == 1
          ? toggles.single
          : null;
      final on = toggles.any((f) => f.on);
      // Short on the line above the field, so it shares it with the
      // approval and model chips; the screen reader says the whole thing.
      final name = _model.agentName;
      final String label;
      final String? spoken;
      if (single != null) {
        final title = agentFeatureWords(copy, single).$1;
        label = single.id == 'fast_mode' ? copy.agentFeatureFastChip : title;
        spoken = single.on
            ? copy.agentFeatureChipOn(title)
            : copy.agentFeatureChipOff(title);
      } else {
        label = name == null ? copy.agentFeaturesLabel : KitBidi.auto(name);
        spoken = _sheetTitle(copy);
      }
      return KitChip.action(
        key: const Key('agent-features-chip'),
        label: label,
        spoken: spoken,
        icon: single == null ? AppIconography.settings : AppIconography.speed,
        tone: on ? KitChipTone.active : KitChipTone.neutral,
        onPressed: () => unawaited(
          showAgentFeaturesSheet(context, _model, title: _sheetTitle(copy)),
        ),
      );
    },
  );
}

/// "Agent settings" (named for the agent): one switch row per switch, each saving at once and
/// saying what it does. A refusal keeps the old value and says so in words.
Future<void> showAgentFeaturesSheet(
  BuildContext context,
  AgentFeaturesModel model, {
  required String title,
}) {
  unawaited(model.load());
  return showKitSheet<void>(
    context,
    title: title,
    icon: AppIconography.speed,
    sheetKey: const Key('agent-features-sheet'),
    body: (sheetContext) => ListenableBuilder(
      listenable: model,
      builder: (context, _) => _SheetBody(model: model),
    ),
  );
}

class _SheetBody extends StatelessWidget {
  const _SheetBody({required this.model});

  final AgentFeaturesModel model;

  @override
  Widget build(BuildContext context) {
    final copy = _copyOf(context);
    final tokens = KitTokens.of(context);
    final features = model.features ?? const <AgentFeature>[];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (model.loadFailed) ...[
          KitNotice(
            key: const Key('agent-features-failed'),
            icon: AppIconography.info,
            message: copy.agentFeaturesLoadFailed,
          ),
          SizedBox(height: tokens.space3),
        ],
        KitRowGroup(
          key: const Key('agent-features'),
          margin: EdgeInsets.zero,
          children: [for (final f in features) _row(context, copy, f)],
        ),
      ],
    );
  }

  Widget _row(BuildContext context, AppLocalizations copy, AgentFeature f) {
    final (title, detail) = agentFeatureWords(copy, f);
    final saving = model.saving == f.id;
    final below = model.failed.contains(f.id)
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
        onChanged: model.saving == null
            ? (value) => unawaited(model.set(f, value))
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
      enabled: model.saving == null,
      onTap: () => unawaited(
        showKitMenu(
          context,
          items: [
            for (final option in f.options)
              KitMenuItem(
                key: Key('agent-feature-${f.id}-${option.id}'),
                label: option.label,
                checked: option.id == f.selected,
                onSelected: () => unawaited(model.set(f, option.id)),
              ),
          ],
        ),
      ),
    );
  }
}
