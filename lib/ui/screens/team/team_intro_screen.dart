/// The AI Team page while the team is off (programme P3.4: team-intro
/// merged into team-home). [TeamPage] shows it in the team page's place
/// until the team is on, then shows the team itself on the same page: the
/// person is never sent back to where they came from.
///
/// What the team does, in four plain steps under a drawing of a task going
/// from agent to agent, what it needs on this kind of server, and one
/// primary action that hands over to that kind's own set-up. Nothing here
/// sets anything up itself:
///
/// - **This phone** (OpenCode inside the app): "Turn on AI Team on this
///   phone" opens the engine's one-tap setup page ([openPhoneTeamSetup]).
///   The phone's pre-flight ([checkSetupPreflight]: CPU, memory,
///   free space for the team's download) is read first; a phone that
///   cannot run a team is told why here, with the computer route, and is
///   never hidden.
/// - **A computer**: the app looks for Gas City on the server's host
///   ([TeamDiscovery]). Found: Turn on. Not found: why, in plain words
///   ([TeamDiscovery.miss]), then Set it up (the host guide, which looks
///   again when it closes). Enter its address is the manual form
///   ([editTeamAddress]) either way.
///
/// Built from kit parts only (screen-team-1): the top bar, the drawing,
/// two row panels (how it works, what it needs), the cost before the
/// set-up primary ([KitNotice.cost]: the time the first setup takes and
/// the memory each worker uses on a phone, KIT-37), and every action named
/// after what it sets up ("Set up AI Team on this phone").
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../../builtin/setup/aiteam_scripts.dart' show AiTeamPins;
import '../../../builtin/setup/components.dart' show SetupComponentIds;
import '../../../builtin/setup/phone_setup.dart';
import '../../../builtin/setup/preflight.dart';
import '../../../builtin/setup/setup_contract.dart' show setupToolsChanged;
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/team_project_demo.dart';
import '../../../state/profiles.dart';
import '../../../voice/device.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import 'project_demo_screen.dart';
import 'team_phone_setup_screen.dart';
import '../../kit/scenes/team_discover_scenes.dart';
import '../../widgets/team_discover.dart';
import '../../widgets/team_discovery_card.dart' show TeamDiscovery;
import '../../widgets/team_host_form.dart';
import '../../widgets/team_switch.dart' show editTeamAddress;
import '../phone_setup/phone_setup_selection.dart'
    show setupPreflightBody, setupPreflightHeadline, setupSizeText;

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

// revamp: merge-into:team-home (slice-P3.4): the team page's off state;
// [TeamPage] (team_page.dart) is its one route.
class TeamIntroScreen extends StatefulWidget {
  const TeamIntroScreen({
    super.key,
    required this.controller,
    this.probe,
    this.deviceProbe,
  });

  final ConnectionController controller;

  /// The Gas City probe (a computer); tests pass a fake.
  final TeamHostProbe? probe;

  /// The device facts the phone's pre-flight reads (CPU, memory, free
  /// space); [voiceDevicePlatform] by default, tests stand in.
  final Future<VoiceDeviceInfo> Function()? deviceProbe;

  @override
  State<TeamIntroScreen> createState() => _TeamIntroScreenState();
}

class _TeamIntroScreenState extends State<TeamIntroScreen> {
  ServerProfile? _profile;
  TeamServerKind? _kind;

  /// This phone: what its pre-flight found (null while the device is
  /// asked); [SetupPreflightResult.supported] when the team can run here.
  SetupPreflightResult? _preflight;

  /// This phone: AI Team's programs are on the phone already (the same
  /// check This phone and Add tools read), so the page offers Turn on, not
  /// a download. Read again when a setup job ends.
  bool? _teamInstalled;
  int _inventoryRead = 0;
  bool _inventoryFailed = false;

  /// A computer: the search for Gas City on the server's host.
  TeamDiscovery? _discovery;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    final profile = _profile = widget.controller.profile;
    if (profile == null) return;
    final kind = _kind = teamServerKindOf(profile);
    switch (kind) {
      case TeamServerKind.inApp:
        setupToolsChanged.addListener(_readInstalled);
        unawaited(_readInstalled());
        unawaited(_checkPhone());
      case TeamServerKind.computer:
        _look();
    }
  }

  Future<void> _readInstalled() async {
    final profile = _profile;
    if (profile == null) return;
    final read = ++_inventoryRead;
    if (_inventoryFailed) setState(() => _inventoryFailed = false);
    bool installed;
    try {
      installed = (await PhoneSetup.engine.installedOptional()).contains(
        SetupComponentIds.aiTeam,
      );
    } catch (_) {
      // A failed probe says nothing about what is installed. Keep the last
      // known inventory, or offer a retry for the initial unknown state.
      if (mounted && read == _inventoryRead) {
        setState(() => _inventoryFailed = true);
      }
      return;
    }
    if (mounted && read == _inventoryRead && installed != _teamInstalled) {
      setState(() => _teamInstalled = installed);
    }
  }

  @override
  void dispose() {
    setupToolsChanged.removeListener(_readInstalled);
    _discovery
      ?..removeListener(_changed)
      ..dispose();
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  /// The phone's pre-flight for the team's download: the same check phone
  /// setup runs before it downloads anything (P0.8). An unknown reading
  /// never blocks.
  Future<void> _checkPhone() async {
    VoiceDeviceInfo device;
    try {
      device =
          await (widget.deviceProbe ?? voiceDevicePlatform.getDeviceInfo)();
    } catch (_) {
      device = const VoiceDeviceInfo.unknown();
    }
    if (!mounted) return;
    setState(
      () => _preflight = checkSetupPreflight(
        device,
        downloadBytes: AiTeamPins.deviceDownloadBytes,
      ),
    );
  }

  /// Looks for Gas City on the server's host, afresh.
  void _look() {
    _discovery
      ?..removeListener(_changed)
      ..dispose();
    final discovery = _discovery = TeamDiscovery(
      widget.controller,
      probe: widget.probe,
    )..addListener(_changed);
    // After this frame: the discovery notifies as soon as it starts.
    scheduleMicrotask(() {
      if (mounted && identical(discovery, _discovery)) {
        unawaited(discovery.ensureProbed());
      }
    });
  }

  bool get _looking {
    final discovery = _discovery;
    return discovery != null && (discovery.running || !discovery.probed);
  }

  Future<void> _run(Future<void> Function() work) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await work();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// "Turn on AI Team on this phone": the engine's one-tap setup page.
  Future<void> _turnOnPhoneEngine() =>
      _run(() => openPhoneTeamSetup(context, widget.controller));

  Future<void> _turnOn() => _run(() async {
    await _discovery?.turnOn();
  });

  Future<void> _showGuide() async {
    // The guide's next step is the address: its "Enter the address" opens
    // the same form as this page's own action.
    await showTeamHostGuideSheet(context, enterAddress: _enterAddress);
    // Installed it in the meantime? Look again.
    if (mounted) setState(_look);
  }

  /// The manual form; a saved address turns the team on, and [TeamPage]
  /// then shows it here.
  Future<void> _enterAddress() => _run(() async {
    await editTeamAddress(
      context,
      widget.controller,
      probe: widget.probe,
      suggestedUrl: _discovery?.result?.url,
    );
  });

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final profile = _profile;
    final kind = _kind;
    final name = profile?.name ?? '';

    KitAction? primary;
    KitAction? secondary;
    switch (kind) {
      case null:
        break;
      case TeamServerKind.inApp:
        // OpenCode inside this app: the team is the phone engine, turned
        // on by the one-tap setup page (Finishing your reply, the safety
        // check, OpenCode back protected).
        if (_preflight?.supported ?? false) {
          primary = KitAction(
            key: const ValueKey('team-intro-set-up'),
            label: l10n.teamIntroTurnOnPhone,
            working: _busy,
            onPressed: _busy ? null : _turnOnPhoneEngine,
          );
        }
      case TeamServerKind.computer:
        if (_discovery?.result != null) {
          primary = KitAction(
            key: const ValueKey('team-intro-turn-on'),
            label: l10n.teamIntroTurnOn(name),
            working: _busy,
            onPressed: _busy ? null : _turnOn,
          );
        } else {
          primary = KitAction(
            key: const ValueKey('team-intro-set-up'),
            label: l10n.teamIntroSetUpOn(name),
            onPressed: _busy ? null : _showGuide,
          );
        }
        // Another address than the one found, or the one discovery could
        // not reach: the manual form, in both states.
        secondary = KitAction(
          key: const ValueKey('team-intro-address'),
          label: l10n.teamDiscoverEnterAddress,
          onPressed: _busy ? null : _enterAddress,
        );
    }
    final actions = KitActionBlock(
      primary: primary,
      secondary: secondary,
      tertiary: [
        KitAction(
          key: const ValueKey('team-try-project-demo'),
          label: l10n.teamProjectTryDemo,
          onPressed: _busy
              ? null
              : () => _run(() async {
                  final enabled = await enableTeamProjectDemo(
                    widget.controller,
                  );
                  if (!enabled && context.mounted) {
                    await pushKitPage<void>(
                      context,
                      (_) => TeamProjectDemoScreen(
                        preferences: widget.controller.store.prefs,
                      ),
                    );
                  }
                }),
        ),
      ],
    );
    final inset = EdgeInsetsDirectional.symmetric(
      horizontal: tokens.gutter,
      vertical: tokens.space2,
    );

    return KitScreen(
      // The one state word under the page's name; what turning it on
      // does is the page itself.
      topBar: KitTopBar(
        title: l10n.teamUiHomeTitle,
        subtitle: l10n.teamUiRowOff,
      ),
      width: KitScreenWidth.reading,
      loading: _looking,
      loadingLabel: l10n.teamDiscoverLooking(name),
      body: ListView(
        key: const ValueKey('team-intro'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: actions.isEmpty
              ? KitScreen.endPadding(context)
              : tokens.space4,
        ),
        children: [
          const Center(
            child: KitIllustration(
              key: ValueKey('team-intro-drawing'),
              scene: TeamDiscoverRelayScene(),
            ),
          ),
          Padding(
            padding: inset,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KitText(l10n.teamDiscoverEntryTitle, role: KitTextRole.title),
                SizedBox(height: tokens.space2),
                KitText(
                  l10n.teamDiscoverIntroBody,
                  tone: KitTextTone.secondary,
                ),
              ],
            ),
          ),
          KitRowGroup(
            margin: _groupMargin(context),
            label: l10n.teamDiscoverHowHeading,
            children: [
              for (final (icon, title, body) in [
                (
                  AppIconography.checklist,
                  l10n.teamDiscoverStepPlanTitle,
                  l10n.teamDiscoverStepPlanBody,
                ),
                (
                  AppIconography.agent,
                  l10n.teamDiscoverStepWorkTitle,
                  l10n.teamDiscoverStepWorkBody,
                ),
                (
                  AppIconography.review,
                  l10n.teamDiscoverStepCheckTitle,
                  l10n.teamDiscoverStepCheckBody,
                ),
                (
                  AppIconography.branch,
                  l10n.teamDiscoverStepMergeTitle,
                  l10n.teamDiscoverStepMergeBody,
                ),
              ])
                _fact(context, icon, title, body),
            ],
          ),
          if (kind != null) ..._needs(context, l10n, kind, name, inset),
        ],
      ),
      bottom: actions.isEmpty ? null : actions,
    );
  }

  /// What it needs on this kind of server, then its cost before the
  /// primary (KIT-37).
  List<Widget> _needs(
    BuildContext context,
    AppLocalizations l10n,
    TeamServerKind kind,
    String name,
    EdgeInsetsGeometry inset,
  ) {
    final label = kind == TeamServerKind.computer
        ? l10n.teamDiscoverNeedsServer(name)
        : l10n.teamDiscoverNeedsPhone;
    Widget group(List<Widget> rows) => KitRowGroup(
      margin: _groupMargin(context),
      key: const ValueKey('team-intro-needs'),
      label: label,
      children: rows,
    );
    // The phone's own cost: the time the first setup takes (measured
    // 8–10 min) and the memory each worker holds (about 550 MB).
    Widget phoneCost(String download) => Padding(
      padding: inset,
      child: KitNotice.cost(
        [download, l10n.teamIntroCostTime, l10n.teamIntroCostMemory],
        key: const ValueKey('team-intro-cost'),
        title: l10n.teamIntroCostTitle,
      ),
    );
    switch (kind) {
      case TeamServerKind.inApp:
        final preflight = _preflight;
        if (preflight == null) {
          return [
            group(const [KitSkeletonRows(count: 2)]),
          ];
        }
        if (!preflight.supported) {
          // Told why before anything downloads, never hidden (P1.7): the
          // phone's own reason, and the computer route instead.
          return [
            Padding(
              padding: inset,
              child: KitNotice(
                key: const ValueKey('team-intro-unsupported'),
                icon: AppIconography.phone,
                title: setupPreflightHeadline(l10n, preflight.issue!),
                message: setupPreflightBody(l10n, preflight),
                actions: [
                  KitAction(
                    key: const ValueKey('team-intro-on-computer'),
                    label: l10n.teamDiscoverOnComputer,
                    onPressed: () => unawaited(
                      showTeamHostGuideSheet(
                        context,
                        enterAddress: _enterAddress,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ];
        }
        if (_teamInstalled == null) {
          if (_inventoryFailed) {
            return [
              Padding(
                padding: inset,
                child: KitNotice(
                  message: l10n.e7SetupCheckInstallFailed,
                  icon: AppIconography.warning,
                  actions: [
                    KitAction(
                      label: l10n.commonRetry,
                      onPressed: _readInstalled,
                    ),
                  ],
                ),
              ),
            ];
          }
          return [
            group(const [KitSkeletonRows(count: 2)]),
          ];
        }
        if (_teamInstalled == true) {
          // Nothing to download: said once, instead of the download line
          // and its cost.
          return [
            group([
              _fact(
                context,
                AppIconography.checkCircle,
                l10n.teamIntroInstalledTitle,
                l10n.teamIntroInstalledBody,
              ),
              _fact(
                context,
                AppIconography.batteryWarning,
                l10n.teamDiscoverBatteryTitle,
                l10n.teamDiscoverInAppBatteryBody,
              ),
              _fact(
                context,
                AppIconography.projects,
                l10n.teamDiscoverProjectTitle,
                l10n.teamDiscoverProjectBody,
              ),
            ]),
          ];
        }
        final download = l10n.teamDiscoverDownloadTitle(
          setupSizeText(l10n, AiTeamPins.deviceDownloadBytes),
        );
        return [
          group([
            _fact(
              context,
              AppIconography.download,
              download,
              l10n.teamDiscoverInAppDownloadBody,
            ),
            _fact(
              context,
              AppIconography.batteryWarning,
              l10n.teamDiscoverBatteryTitle,
              l10n.teamDiscoverInAppBatteryBody,
            ),
            _fact(
              context,
              AppIconography.projects,
              l10n.teamDiscoverProjectTitle,
              l10n.teamDiscoverProjectBody,
            ),
          ]),
          phoneCost(download),
        ];
      case TeamServerKind.computer:
        return [
          group([
            _fact(
              context,
              AppIconography.computer,
              l10n.teamDiscoverComputerTitle(name),
              l10n.teamDiscoverComputerBody,
            ),
            _fact(
              context,
              AppIconography.speed,
              l10n.teamDiscoverSpeedTitle,
              l10n.teamDiscoverSpeedBody,
            ),
          ]),
          if (_discovery?.result != null)
            Padding(
              padding: inset,
              child: KitNotice(
                key: const ValueKey('team-intro-found'),
                tone: AppStatusTone.ok,
                icon: AppIconography.checkCircle,
                title: l10n.pluginsTeamRowFound(name),
                message: l10n.teamDiscoverFoundBody,
              ),
            )
          else if (_discovery?.miss case final miss?)
            // Why nothing was found, in plain words (the host's own
            // answer stays out of the copy).
            Padding(
              padding: inset,
              child: KitNotice(
                key: const ValueKey('team-intro-miss'),
                icon: AppIconography.info,
                title: l10n.teamIntroNotFound(name),
                message: teamVerdictCopy(l10n, miss) ?? '',
              ),
            ),
        ];
    }
  }

  /// A panel's place in the list: the gutter at the sides, a section step
  /// above its label.
  static EdgeInsetsDirectional _groupMargin(BuildContext context) {
    final tokens = KitTokens.of(context);
    return EdgeInsetsDirectional.fromSTEB(
      tokens.gutter,
      tokens.space3,
      tokens.gutter,
      tokens.space1,
    );
  }

  /// One line of the intro: an icon, a short title and what it means. Not
  /// a door (nothing to open), so no chevron.
  Widget _fact(
    BuildContext context,
    IconData icon,
    String title,
    String body,
  ) => MergeSemantics(
    child: KitRow(
      leading: KitRow.icon(context, icon),
      title: title,
      titleMaxLines: 2,
      supporting: TextSpan(text: body),
      supportingMaxLines: 3,
    ),
  );
}
