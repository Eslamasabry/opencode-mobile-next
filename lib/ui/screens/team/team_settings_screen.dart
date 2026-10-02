/// Team settings: everything about how the AI Team is set up, kept off the
/// work page (crit team-page 2026-09-29). Opened from the team page's top
/// bar and from Settings › AI Team while the team is on.
///
/// One list: the **agents** row ("Agents · 5 roles", opening [TeamAgentsScreen]), what the team
/// **spent today** when the host reports it, the host's upkeep in words, the
/// phone's own team controls and Change address where they apply, the
/// **Details** row (how fast it runs; the address, version and engine, where
/// Gas City is named, behind it) and **Turn off the AI Team** last.
library;

import 'dart:async';

import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart' show ProviderScope;

import 'package:shared_preferences/shared_preferences.dart';

import '../../../builtin/team/builtin_team.dart' show BuiltinTeam;
import '../../../builtin/thermal_guard.dart';
import '../../../builtin/thermal_guard_teams.dart'
    show thermalGuardSlotProvider;
import '../../../domain/orchestration_gateway.dart';
import '../../../domain/server_gateway.dart' show CatalogModel;
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/profiles.dart' show OrchestrationProvider;
import '../../../state/orchestration.dart';
import '../../../state/team_model.dart';
import '../../../state/team_roles.dart';
import '../../../termux/team_runtime.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/team_discovery_card.dart'
    show teamHostDisclaimer, teamHostKindFor;
import '../../widgets/team_host_form.dart'
    show TeamHostProbe, showTeamTurnOffSheet;
import '../../widgets/team_phone_section.dart' show TeamPhoneSection;
import '../../widgets/team_switch.dart';
import '../../widgets/team_technical_details.dart';
import '../../widgets/team_vocabulary.dart';
import '../settings/plugins_screen.dart' show teamPhoneProfile;
import 'team_agents_screen.dart';
import 'team_model_sheet.dart';
import 'team_phone_setup_screen.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Opens Team settings over the current page.
Future<void> openTeamSettings(
  BuildContext context, {
  required OrchestrationController controller,
  ConnectionController? connection,
  ValueListenable<ThermalGuard?>? thermalGuard,
  TeamHostProbe? probe,
  TermuxTeamRuntime? teamRuntime,
  DateTime Function()? now,
  ValueChanged<OrchestrationAgent>? onOpenAgent,
  VoidCallback? onTeamChanged,
}) => Navigator.of(context).push(
  KitPageRoute<void>(
    builder: (_) => TeamSettingsScreen(
      controller: controller,
      connection: connection,
      thermalGuard: thermalGuard,
      probe: probe,
      teamRuntime: teamRuntime,
      now: now,
      onOpenAgent: onOpenAgent,
      onTeamChanged: onTeamChanged,
    ),
  ),
);

class TeamSettingsScreen extends StatefulWidget {
  const TeamSettingsScreen({
    super.key,
    required this.controller,
    this.connection,
    this.thermalGuard,
    this.probe,
    this.teamRuntime,
    this.now,
    this.onOpenAgent,
    this.onTeamChanged,
  });

  final OrchestrationController controller;

  /// The connection whose server this team belongs to: Change address and
  /// Turn off write its profile. Neither is offered without one.
  final ConnectionController? connection;

  /// The heat guard's slot ([thermalGuardSlotProvider] when null).
  final ValueListenable<ThermalGuard?>? thermalGuard;

  /// The Gas City probe of the address form; tests pass a fake.
  final TeamHostProbe? probe;

  /// The Termux team runtime; tests pass a fake.
  final TermuxTeamRuntime? teamRuntime;
  final DateTime Function()? now;
  final ValueChanged<OrchestrationAgent>? onOpenAgent;

  /// After Change address or Turn off replaced this team; this page has
  /// closed itself first.
  final VoidCallback? onTeamChanged;

  @override
  State<TeamSettingsScreen> createState() => _TeamSettingsScreenState();
}

class _TeamSettingsScreenState extends State<TeamSettingsScreen> {
  bool _offFailed = false;
  bool _modelFailed = false;
  bool _switching = false;
  TeamModelStore? _models;
  TeamRolesController? _roles;
  String? _model;
  ValueListenable<ThermalGuard?>? _scopeHeat;

  ValueListenable<ThermalGuard?>? get _heat =>
      widget.thermalGuard ?? _scopeHeat;

  @override
  void initState() {
    super.initState();
    unawaited(_loadModel());
  }

  Future<void> _loadModel() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final store = TeamModelStore(prefs);
      final model = store.read(widget.controller.profileId);
      final roles = teamRolesFor(prefs, widget.controller.profileId);
      if (!mounted) return;
      setState(() {
        _models = store;
        _model = model;
        _roles = roles;
      });
    } catch (_) {
      // No stored choice reads as the phone's default.
    }
  }

  /// The chosen model as a person reads it: the catalog's name when the
  /// phone lists it, else the choice as it was saved.
  String _modelName(AppLocalizations l10n, ConnectionController? owner) {
    final spec = _model;
    if (spec == null) return l10n.teamModelDefault;
    for (final model in owner?.catalog?.models ?? const <CatalogModel>[]) {
      if (teamModelSpec(model.providerID, model.id) == spec) return model.name;
    }
    return spec;
  }

  Future<void> _pickModel(ConnectionController owner) async {
    unawaited(owner.ensureCatalog());
    final choice = await showTeamModelSheet(
      context,
      connection: owner,
      current: _model,
    );
    final store = _models;
    if (choice == null || store == null || !mounted || choice.spec == _model) {
      return;
    }
    setState(() => _modelFailed = false);
    try {
      await BuiltinTeam().applyModel(choice.spec);
      final kept = await store.write(widget.controller.profileId, choice.spec);
      if (!kept) throw StateError('model choice not kept');
      if (mounted) setState(() => _model = choice.spec);
    } catch (_) {
      if (mounted) setState(() => _modelFailed = true);
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (widget.thermalGuard != null) return;
    try {
      _scopeHeat = ProviderScope.containerOf(
        context,
        listen: false,
      ).read(thermalGuardSlotProvider);
    } on StateError {
      _scopeHeat = null;
    }
  }

  /// The connection's profile when it is this team's, else null.
  ConnectionController? get _owner {
    final connection = widget.connection;
    final profile = connection?.profile;
    if (connection == null ||
        profile == null ||
        profile.id != widget.controller.profileId ||
        profile.orchestration == null) {
      return null;
    }
    return connection;
  }

  bool _closed = false;

  /// Closes this page once, however the team went away.
  void _close() {
    if (_closed || !mounted) return;
    _closed = true;
    Navigator.of(context).pop();
  }

  void _changed() {
    _close();
    widget.onTeamChanged?.call();
  }

  /// The team was turned off or removed elsewhere (the phone's own Remove):
  /// there is nothing left to set up here.
  bool get _teamGone {
    final profile = widget.connection?.profile;
    return profile != null &&
        profile.id == widget.controller.profileId &&
        profile.orchestration == null;
  }

  Future<void> _changeAddress() async {
    final connection = _owner;
    if (connection == null || _switching) return;
    setState(() => _switching = true);
    var replaced = false;
    try {
      replaced = await editTeamAddress(
        context,
        connection,
        probe: widget.probe,
      );
      if (replaced) _changed();
    } finally {
      // A replaced team keeps this page blank until it has closed.
      if (!replaced && mounted) setState(() => _switching = false);
    }
  }

  Future<void> _turnOff() async {
    final connection = _owner;
    final profile = connection?.profile;
    if (connection == null || profile == null || _switching) return;
    final confirmed = await showTeamTurnOffSheet(context, profile.name);
    if (!confirmed || !mounted) return;
    setState(() {
      _switching = true;
      _offFailed = false;
    });
    var replaced = false;
    try {
      final outcome = await turnOffTeam(connection, profile);
      if (outcome == TeamOffOutcome.failed) {
        if (mounted) setState(() => _offFailed = true);
        return;
      }
      replaced = true;
      _changed();
    } finally {
      // A replaced team keeps this page blank until it has closed.
      if (!replaced && mounted) setState(() => _switching = false);
    }
  }

  Future<void> _openPhoneControls() {
    final connection = _owner!;
    final l10n = _copy(context);
    return showKitSheet<void>(
      context,
      title: l10n.teamUiPhoneSectionTitle,
      icon: AppIconography.phone,
      sheetKey: const ValueKey('team-home-phone-sheet'),
      body: (sheetContext) => TeamPhoneSection(
        connection: connection,
        profile: connection.profile!,
        runtime: widget.teamRuntime,
        onRemoved: () => Navigator.of(sheetContext).pop(),
      ),
    );
  }

  void _openAgents() {
    Navigator.of(context).push(
      KitPageRoute<void>(
        builder: (_) => TeamAgentsScreen(
          controller: widget.controller,
          roles: _roles,
          connection: widget.connection,
          onOpenAgent: widget.onOpenAgent,
          now: widget.now,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final controller = widget.controller;
    // Change address and Turn off replace the team before this page closes
    // itself: draw nothing for that moment, never listen to a stopped team.
    if (_switching && widget.connection?.orchestration != controller) {
      return const SizedBox.shrink();
    }
    return ListenableBuilder(
      listenable: Listenable.merge([
        controller,
        widget.connection,
        _heat,
        _roles,
      ]),
      builder: (context, _) {
        if (_teamGone) {
          WidgetsBinding.instance.addPostFrameCallback((_) => _close());
          return const SizedBox.shrink();
        }
        final owner = _owner;
        final builtin = BuiltinTeam.isBuiltinConfig(controller.config);
        final snapshot = controller.snapshot;
        final upkeep = teamUpkeepRuns(snapshot.runs);
        final spent = _spentRow(context, l10n, controller);
        return KitScreen(
          key: const ValueKey('team-settings'),
          topBar: KitTopBar(
            title: l10n.teamSettingsTitle,
            subtitle: teamHostPhrase(l10n, controller),
          ),
          width: KitScreenWidth.list,
          body: ListView(
            key: const ValueKey('team-settings-list'),
            padding: EdgeInsetsDirectional.only(
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              if (_offFailed)
                Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.gutter,
                    vertical: tokens.space2,
                  ),
                  child: KitNotice(
                    key: const ValueKey('team-home-turn-off-failed'),
                    tone: AppStatusTone.failure,
                    icon: AppIconography.error,
                    message: l10n.teamHomeTurnOffFailed,
                    onDismiss: () => setState(() => _offFailed = false),
                  ),
                )
              else
                SizedBox(height: tokens.space3),
              if (_modelFailed)
                Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.gutter,
                    vertical: tokens.space2,
                  ),
                  child: KitNotice(
                    key: const ValueKey('team-settings-model-failed'),
                    tone: AppStatusTone.failure,
                    icon: AppIconography.error,
                    message: l10n.teamModelFailed,
                    onDismiss: () => setState(() => _modelFailed = false),
                  ),
                ),
              KitRowGroup(
                key: const ValueKey('team-home-team'),
                children: [
                  KitRow(
                    key: const ValueKey('team-home-agents-row'),
                    leading: KitRow.icon(context, AppIconography.agent),
                    title: l10n.teamSettingsAgentsRow(
                      _roles?.roles.length ?? TeamRoleIds.builtIn.length,
                    ),
                    supporting: TextSpan(text: l10n.teamSettingsAgentsHint),
                    supportingMaxLines: 2,
                    trailing: const KitChevron(),
                    onTap: _openAgents,
                  ),
                  ?spent,
                  if (owner != null && builtin)
                    KitRow(
                      key: const ValueKey('team-settings-model-row'),
                      leading: KitRow.icon(context, AppIconography.model),
                      title: l10n.teamModelRowTitle(_modelName(l10n, owner)),
                      supporting: TextSpan(text: l10n.teamModelChange),
                      supportingMaxLines: 2,
                      trailing: const KitChevron(),
                      onTap: () => unawaited(_pickModel(owner)),
                    ),
                  if (upkeep.isNotEmpty)
                    KitRow(
                      key: const ValueKey('team-home-upkeep-row'),
                      leading: KitRow.icon(context, AppIconography.retry),
                      title: l10n.teamHomeUpkeepTitle,
                      supporting: TextSpan(text: teamUpkeepLine(l10n, upkeep)),
                      supportingKey: const ValueKey('team-home-upkeep-line'),
                      supportingMaxLines: 3,
                    ),
                ],
              ),
              SizedBox(height: tokens.sectionGap),
              KitRowGroup(
                key: const ValueKey('team-settings-how'),
                children: [
                  if (widget.connection != null &&
                      controller.config.provider ==
                          OrchestrationProvider.phoneEngine)
                    KitRow(
                      key: const ValueKey('team-settings-turn-on-phone'),
                      leading: KitRow.icon(context, AppIconography.phone),
                      title: l10n.teamIntroTurnOnPhone,
                      supporting: TextSpan(text: l10n.phoneTeamOffBody),
                      supportingMaxLines: 2,
                      trailing: const KitChevron(),
                      onTap: () => unawaited(
                        openPhoneTeamSetup(context, widget.connection!),
                      ),
                    ),
                  if (owner != null && teamPhoneProfile(owner.profile))
                    KitRow(
                      key: const ValueKey('team-home-phone-controls'),
                      leading: KitRow.icon(context, AppIconography.phone),
                      title: l10n.teamHomePhoneControls,
                      trailing: const KitChevron(),
                      onTap: () => unawaited(_openPhoneControls()),
                    ),
                  if (owner != null && !builtin)
                    KitRow(
                      key: const ValueKey('team-home-change-address'),
                      leading: KitRow.icon(context, AppIconography.edit),
                      title: l10n.teamHomeChangeAddress,
                      enabled: !_switching,
                      trailing: const KitChevron(),
                      onTap: () => unawaited(_changeAddress()),
                    ),
                  // How fast it runs where it runs; the address, version and
                  // engine (where Gas City is named) are behind it.
                  KitRow(
                    key: const ValueKey('team-home-host-row'),
                    leading: KitRow.icon(context, AppIconography.speed),
                    title: l10n.teamUiTechnicalDetails,
                    supporting: TextSpan(
                      text: teamHostDisclaimer(
                        l10n,
                        teamHostKindFor(
                          controller.config,
                          controller.host?.hostMode ??
                              controller.config.hostMode,
                        ),
                      ),
                    ),
                    supportingKey: const ValueKey('team-home-host-speed'),
                    supportingMaxLines: 2,
                    trailing: const KitChevron(),
                    onTap: () => showTeamHostDetailsSheet(context, controller),
                  ),
                  if (owner != null)
                    KitRow(
                      key: const ValueKey('team-home-turn-off'),
                      leading: KitRow.icon(context, AppIconography.unlink),
                      title: l10n.teamSettingsTurnOff,
                      destructive: true,
                      enabled: !_switching,
                      onTap: () => unawaited(_turnOff()),
                    ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }

  /// What the whole team spent today, as the host estimates it; null when
  /// the host reports nothing (unknown is never "\$0").
  Widget? _spentRow(
    BuildContext context,
    AppLocalizations l10n,
    OrchestrationController controller,
  ) {
    if (!controller.capabilities.usage) return null;
    final evidence = controller.snapshot.usage?.evidence;
    final today = evidence != null && evidence.available
        ? evidence.today
        : null;
    if (today == null) return null;
    final cost = today.costUsdEstimate;
    final input = today.inputTokens, output = today.outputTokens;
    final tokens = input == null && output == null
        ? null
        : (input ?? 0) + (output ?? 0);
    if ((cost ?? 0) == 0 && (tokens ?? 0) == 0) return null;
    final figures = [
      if (cost != null) l10n.teamUiUsageCostEstimated(teamCurrencyLabel(cost)),
      if (tokens != null) l10n.teamUiUsageTokens(teamCompactCount(tokens)),
    ];
    final lines = [
      l10n.teamHomeSpentHint,
      if ((today.unpriced ?? 0) > 0)
        l10n.teamHomeSpentPartial
      else if (evidence!.partial)
        l10n.teamHomeSpentHistoryMissing,
      if (!evidence!.recording) l10n.teamHomeSpentNotRecording,
    ];
    return KitRow(
      key: const ValueKey('team-home-spent'),
      leading: KitRow.icon(context, AppIconography.usage),
      title: l10n.teamHomeSpentToday(figures.join(teamUsageSeparator)),
      titleKey: const ValueKey('team-home-spent-figure'),
      supporting: TextSpan(text: lines.join(' ')),
      supportingKey: const ValueKey('team-home-spent-scope'),
      supportingMaxLines: 5,
    );
  }
}
