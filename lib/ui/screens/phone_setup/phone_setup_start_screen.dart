import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/app_exit_recovery.dart'
    show appLifecycleBridgeProvider;
import '../../../builtin/builtin_server.dart';
import '../../../builtin/setup/phone_setup.dart';
import '../../../builtin/setup/preflight.dart';
import '../../../builtin/setup/setup_contract.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/profiles.dart';
import '../../../state/termux_running_server.dart';
import '../../../termux/bridge.dart' show TermuxBridge;
import '../../../voice/device.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../kit/scenes/setup_phone_scene.dart';
import '../../kit/scenes/setup_unplugged_scene.dart';
import '../../widgets/phone_server_consents.dart';
import '../../widgets/product_states.dart' show productErrorText;
import '../../widgets/setup_progress_view.dart';
import '../servers_screen.dart' show ServersRouteRequest;
import 'phone_setup_hero.dart';
import 'phone_setup_routes.dart';
import 'phone_setup_selection.dart';
import 'phone_setup_termux_job_screen.dart' show openPhoneSetupTermuxJob;
import 'phone_setup_termux_screen.dart';

/// Screen A of phone setup v2 (docs/design/phone-setup-v2-2026-09-24.md):
/// "On this phone". One promise, one filled button, and the less common ways
/// folded away underneath.
///
/// The hero follows what is true on the phone right now, in this order:
/// a setup that is running or stopped part way ("Setup is 42% done"), then
/// one that runs or stopped in Termux ("Setup in Termux is 42% done"), an
/// OpenCode that is ready in the app, one that Termux already runs, and only
/// then the first-time promise. Every number in the promise comes from the
/// component registry, so adding a component changes it with no screen code.
class PhoneSetupStartScreen extends ConsumerStatefulWidget {
  const PhoneSetupStartScreen({
    super.key,
    this.termuxProbe,
    this.inAppProbe,
    this.deviceProbe,
    this.openProgress = _openFirstSetupProgress,
  });

  /// Looks for an OpenCode that the Termux path already set up. Defaults to
  /// the read-only [detectTermuxRunningServer]; tests stand in for Termux.
  final Future<TermuxRunningServer> Function()? termuxProbe;

  /// Whether the in-app OpenCode is installed even though no setup job says
  /// so (it was set up before setup v2 kept a job). Defaults to a saved
  /// in-app profile plus the Linux base being there.
  final Future<bool> Function()? inAppProbe;

  /// The device info the pre-flight check reads (P0.8): CPU ABI, free space
  /// and total RAM. Defaults to [voiceDevicePlatform.getDeviceInfo], already
  /// collected for voice; tests stand in for the `oc/voice` channel.
  final Future<VoiceDeviceInfo> Function()? deviceProbe;

  /// Screen B. A parameter so tests can see the hand-over without building
  /// the progress screen.
  final Future<void> Function(BuildContext context) openProgress;

  @override
  ConsumerState<PhoneSetupStartScreen> createState() =>
      _PhoneSetupStartScreenState();
}

enum _Hero { loading, fresh, progress, termuxProgress, ready, termux }

class _PhoneSetupStartScreenState extends ConsumerState<PhoneSetupStartScreen> {
  late final SetupEngine _engine;

  /// The Termux host's engine (P1.2), read so a job Termux runs or stopped
  /// shows here too; null off Android.
  SetupEngine? _termuxEngine;
  late Set<String> _selection;

  /// Until the persisted job is read, the screen cannot know whether to
  /// promise a first setup or offer to continue one, so it promises nothing.
  bool _restored = false;
  bool _inAppInstalled = false;
  TermuxRunningServer? _termux;
  VoiceDeviceInfo? _device;
  bool _busy = false;

  /// Open is starting OpenCode and connecting, which can take a while: the
  /// state says so with progress instead of a button that spins (standard
  /// §2).
  bool _opening = false;
  bool _otherWaysOpen = false;
  String? _failure;

  /// Leaving the screen stops a Termux look that is still waiting, so its
  /// deadlines do not outlive the page.
  final _discovery = TermuxDiscoveryCancellation();

  @override
  void initState() {
    super.initState();
    _engine = PhoneSetup.engine;
    if (TermuxBridge.supported) _termuxEngine = PhoneSetup.termux;
    _selection = defaultSetupSelection(installableComponents(_engine.registry));
    unawaited(_load());
  }

  Future<void> _load() async {
    try {
      // An app restart leaves an interrupted job on disk; reading it is what
      // turns this screen into "Continue". A read that hangs must not hold
      // the screen hostage, so it gets a few seconds and then the screen
      // shows what it knows.
      await _engine.restore().timeout(const Duration(seconds: 3));
    } catch (_) {
      // Nothing restored: the progress stays idle and the promise shows.
    }
    if (!mounted) return;
    setState(() => _restored = true);
    // Termux's job is read beside the rest, never before the screen shows:
    // its progress listener redraws the hero when it arrives.
    unawaited(_restoreTermux());
    unawaited(_probeInApp());
    unawaited(_probeTermux());
    unawaited(_probeDevice());
  }

  /// The Termux job on disk, if any; unreadable counts as none.
  Future<void> _restoreTermux() async {
    try {
      await _termuxEngine?.restore().timeout(const Duration(seconds: 3));
    } catch (_) {
      // No Termux job to show.
    }
  }

  /// A Termux job that runs or stopped part way, which the hero leads with
  /// when the app's own setup has none.
  SetupProgress? get _termuxJob {
    final progress = _termuxEngine?.progress.value;
    if (progress == null) return null;
    return switch (progress.state) {
      SetupState.running ||
      SetupState.interrupted ||
      SetupState.failed ||
      SetupState.cancelled => progress,
      SetupState.idle || SetupState.done => null,
    };
  }

  /// P0.8: the CPU ABI, free space and total RAM the pre-flight check reads,
  /// before the primary button downloads anything.
  Future<void> _probeDevice() async {
    VoiceDeviceInfo device;
    try {
      device =
          await (widget.deviceProbe ?? voiceDevicePlatform.getDeviceInfo)();
    } catch (_) {
      device = const VoiceDeviceInfo.unknown();
    }
    if (mounted) setState(() => _device = device);
  }

  /// Null while the device has not answered yet. A supported result may
  /// still carry a "may be slow" memory note
  /// ([SetupPreflightResult.mayBeSlow]).
  SetupPreflightResult? _preflightFor(List<SetupComponent> install) {
    final device = _device;
    if (device == null) return null;
    return checkSetupPreflight(
      device,
      downloadBytes: setupTotals(install).bytes,
    );
  }

  Future<void> _probeInApp() async {
    bool installed;
    try {
      installed = await (widget.inAppProbe ?? _defaultInAppProbe)();
    } catch (_) {
      installed = false;
    }
    if (mounted && installed != _inAppInstalled) {
      setState(() => _inAppInstalled = installed);
    }
  }

  Future<bool> _defaultInAppProbe() async {
    final profile = _inAppProfile();
    if (profile == null) return false;
    return isInAppServer(profile, ref.read(builtinLinuxProvider));
  }

  Future<void> _probeTermux() async {
    TermuxRunningServer found;
    try {
      found =
          await (widget.termuxProbe ??
              () => detectTermuxRunningServer(
                profiles: ref.read(bootstrapProvider).store.profiles,
                cancellation: _discovery,
              ))();
    } catch (_) {
      found = const TermuxRunningServer.unavailable();
    }
    if (mounted) setState(() => _termux = found);
  }

  @override
  void dispose() {
    _discovery.cancel();
    super.dispose();
  }

  bool get _termuxPresent =>
      _termux != null && (_termux!.isRunning || _termux!.isStopped);

  _Hero _heroFor(SetupProgress progress) {
    if (!_restored) return _Hero.loading;
    switch (progress.state) {
      case SetupState.running:
      case SetupState.interrupted:
      case SetupState.failed:
      case SetupState.cancelled:
        return _Hero.progress;
      case SetupState.done:
      case SetupState.idle:
        break;
    }
    if (_termuxJob != null) return _Hero.termuxProgress;
    if (progress.state == SetupState.done) return _Hero.ready;
    if (_inAppInstalled) return _Hero.ready;
    if (_termuxPresent) return _Hero.termux;
    return _Hero.fresh;
  }

  /// The in-app profile to open: the active one when it is in-app, else the
  /// first saved one. The app keeps one per OpenCode generation.
  ServerProfile? _inAppProfile() {
    final store = ref.read(bootstrapProvider).store;
    ServerProfile? first;
    for (final profile in store.profiles) {
      if (!looksLikeInAppServer(profile)) continue;
      if (profile.id == store.activeId) return profile;
      first ??= profile;
    }
    return first;
  }

  /// The selection of the job on disk, so "Continue" resumes that job rather
  /// than whatever the switches say now.
  Set<String> _jobSelection(SetupProgress progress) =>
      progress.components.isEmpty
      ? _selection
      : {for (final component in progress.components) component.id};

  Future<void> _run(Set<String> ids) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _failure = null;
    });
    try {
      // Everything started here is the phone's first setup; the job keeps
      // that fact so a notification tap after the app was killed still ends
      // on "name your first project".
      await _engine.run(ids, params: SetupJobParams.firstSetup);
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _failure = _l10n.phoneSetupStartFailed(productErrorText(error));
        });
      }
      return;
    }
    if (!mounted) return;
    setState(() => _busy = false);
    await _showProgress();
  }

  Future<void> _showProgress() async {
    await widget.openProgress(context);
    // The person may come back after it finished; re-read what is installed
    // so the hero is not left on a stale promise.
    if (mounted) unawaited(_probeInApp());
  }

  Future<void> _continue(SetupProgress progress) async {
    if (progress.canContinue) {
      // The check scripts skip what is already installed, so running the
      // same selection again is exactly a resume.
      await _run(_jobSelection(progress));
    } else {
      await _showProgress();
    }
  }

  Future<void> _open(SetupProgress progress) async {
    final profile = _inAppProfile();
    if (profile == null) {
      // A finished job ends by starting OpenCode and connecting to it. With
      // no saved connection that last step never happened (or was forgotten),
      // and running the job again redoes just that: every check passes.
      await _run(_jobSelection(progress));
      return;
    }
    if (_busy) return;
    setState(() {
      _busy = true;
      _opening = true;
      _failure = null;
    });
    final l10n = _l10n;
    final navigator = Navigator.of(context);
    String? failure;
    try {
      final connection = ref.read(connProvider);
      final alreadyThere =
          connection.hasConnectedServer && connection.profile?.id == profile.id;
      if (!alreadyThere) {
        var running = false;
        try {
          running =
              (await ref.read(builtinLinuxProvider).status()).serverRunning;
        } catch (_) {
          // Unknown counts as stopped; starting a running one only restarts.
        }
        if (!running) {
          final startFailure = await ref
              .read(builtinServerStarterProvider)
              .start(profile);
          if (startFailure != null) failure = startFailure.reason(l10n);
        }
        if (failure == null) {
          // The first start is the moment keeping it alive matters (P6.7):
          // asked once per server, before connecting; later starts ask
          // nothing.
          if (!mounted) return;
          await askPhoneServerConsents(
            context,
            connection: connection,
            bridge: ref.read(appLifecycleBridgeProvider),
            profileId: profile.id,
          );
          if (!mounted) return;
          await connection.connect(profile);
          if (!connection.hasConnectedServer) {
            final error = connection.lastError;
            failure = error == null
                ? l10n.builtinServerStopped
                : productErrorText(error, l10n: l10n);
          }
        }
      }
    } catch (error) {
      failure = productErrorText(error, l10n: l10n);
    }
    if (!mounted) return;
    setState(() {
      _busy = false;
      _opening = false;
      _failure = failure == null ? null : l10n.phoneSetupStartFailed(failure);
    });
    if (failure == null) {
      unawaited(navigator.pushNamedAndRemoveUntil('/home', (_) => false));
    }
  }

  /// Termux already runs OpenCode: hand over to the Servers screen, which
  /// owns connecting, restoring the phone's password and the start card.
  void _connectTermux() {
    final server = _termux;
    if (server == null) return;
    final profile = savedProfileForTermuxServer(
      ref.read(bootstrapProvider).store.profiles,
      server,
    );
    final ServersRouteRequest? request;
    if (!server.isRunning) {
      // Stopped: the Servers list leads with that phone server and its Start.
      request = null;
    } else if (profile != null) {
      request = ServersRouteRequest.connect(profile.id, detectedRunning: true);
    } else {
      request = ServersRouteRequest.enterPhoneCredentials(
        openCode2: server.flavor == ServerFlavor.v2,
      );
    }
    unawaited(
      Navigator.of(
        context,
      ).pushNamedAndRemoveUntil('/servers', (_) => false, arguments: request),
    );
  }

  /// The Termux host of the same v2 job (P1.2): Termux and its permission
  /// are rows only the person can do, then the same components as here,
  /// with the same Customize choice, install in Termux.
  Future<void> _useTermux() async {
    await openPhoneSetupTermux(
      context,
      firstSetup: true,
      selection: _selection,
    );
    // Setting Termux up there changes what this screen should lead with.
    if (mounted) unawaited(_probeTermux());
  }

  /// The Termux job that runs or stopped: its own progress screen, which
  /// follows a running job and continues a stopped one straight away.
  Future<void> _continueTermux(SetupProgress progress) async {
    await openPhoneSetupTermuxJob(
      context,
      selection: progress.adding.isEmpty ? _jobSelection(progress) : null,
      adding: progress.adding.toSet(),
      firstSetup: progress.firstSetup,
      resume: true,
    );
    if (mounted) unawaited(_probeTermux());
  }

  /// P0.8, low space: Android's Storage settings, so freeing space is one
  /// tap away instead of a dead end. Freeing space and coming back re-reads
  /// it on the next build rather than polling.
  Future<void> _openStorageSettings() async {
    try {
      await ref.read(builtinLinuxProvider).openStorageSettings();
    } catch (_) {
      // No native answer (an old build, a test): nothing else to try.
    }
    if (mounted) unawaited(_probeDevice());
  }

  void _connectByAddress() {
    unawaited(
      Navigator.of(
        context,
      ).pushNamed('/servers', arguments: const ServersRouteRequest.add()),
    );
  }

  Future<void> _customize() async {
    final chosen = await showPhoneSetupCustomize(context, selected: _selection);
    if (chosen != null && mounted) setState(() => _selection = chosen);
  }

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    return ListenableBuilder(
      listenable: Listenable.merge([_engine.progress, _termuxEngine?.progress]),
      builder: (context, _) {
        final progress = _engine.progress.value;
        final hero = _heroFor(progress);
        // Until the job on disk is read the screen promises nothing: the
        // one loading bar, and no state that might be the wrong one.
        return KitScreen(
          // B6: a saved server's connection problem is not about this
          // phone's own setup: one line, its ways out behind More.
          bodyQuiets: const {KitStatusKind.connection},
          topBar: KitTopBar(title: l10n.phoneSetupStartScreenTitle),
          loading: hero == _Hero.loading,
          loadingLabel: l10n.workLoadingLabel,
          // Each state cross-fades in and fills the body from the top: a
          // centred swap would float it mid-screen (design regressions
          // ledger row 1). Reduced motion swaps at once (KitSwap).
          body: KitSwap(
            pace: KitPace.standard,
            alignment: AlignmentDirectional.topCenter,
            child: KeyedSubtree(
              key: ValueKey(hero),
              child: hero == _Hero.loading
                  ? const SizedBox.shrink()
                  : _state(
                      context,
                      l10n,
                      hero,
                      hero == _Hero.termuxProgress
                          ? _termuxJob ?? progress
                          : progress,
                    ),
            ),
          ),
        );
      },
    );
  }

  /// The screen as one [KitStateView]: what is true on the phone now, the
  /// one thing to do about it, and the less common ways folded underneath.
  Widget _state(
    BuildContext context,
    AppLocalizations l10n,
    _Hero hero,
    SetupProgress progress,
  ) {
    final install = expandSetupSelection(
      installableComponents(_engine.registry),
      _selection,
    );
    final included = includedToolNames(install);
    final includesText = included.isEmpty
        ? null
        : l10n.phoneSetupStartIncludes(joinSetupNames(l10n, included));
    // P0.8: told why before anything downloads, never after a failed one.
    final check = hero == _Hero.fresh ? _preflightFor(install) : null;
    final preflight = check == null || check.supported ? null : check;
    // B2: over the memory floor but under a comfortable amount, setup goes
    // ahead and the page says once, plainly, that it may be slow.
    final slowNote = check != null && check.mayBeSlow
        ? l10n.phoneSetupPreflightMayBeSlow(check.totalMemoryMb!)
        : null;
    final String headline;
    final String body;
    final String action;
    final VoidCallback onPressed;
    // The drawing says where the phone stands; it moves only while setup
    // runs or OpenCode starts (design standard §10).
    // FB2: the shape of the whole journey, only where a first setup can
    // start (a phone that cannot run it is told why instead).
    String? steps;
    KitScene scene = const SetupPhoneScene();
    var ambient = false;
    KitProgress? meter;
    switch (hero) {
      case _Hero.loading:
      case _Hero.fresh:
        final totals = setupTotals(install);
        if (preflight != null) {
          headline = setupPreflightHeadline(l10n, preflight.issue!);
          body = setupPreflightBody(l10n, preflight);
        } else {
          headline = l10n.phoneSetupStartHeadline;
          // The time is on the step line under it (FB2), said once.
          body = totals.bytes > 0
              ? l10n.phoneSetupStartPromise(setupSizeText(l10n, totals.bytes))
              : l10n.phoneSetupStartPromiseNoSize;
          steps = setupStepsText(l10n, totals.seconds);
        }
        action = l10n.phoneSetupStartSetUp;
        onPressed = () => unawaited(_run(_selection));
      case _Hero.progress:
      case _Hero.termuxProgress:
        // Never "100% done" while it is still going: the last step
        // (starting OpenCode) has no bar of its own.
        final percent = (progress.overall * 100).floor().clamp(0, 99);
        final running = progress.state == SetupState.running;
        final inTermux = hero == _Hero.termuxProgress;
        headline = inTermux
            ? l10n.phoneSetupStartTermuxProgressHeadline(percent)
            : l10n.phoneSetupStartProgressHeadline(percent);
        body = running
            ? l10n.phoneSetupStartRunningBody
            : l10n.phoneSetupStartStoppedBody;
        action = l10n.phoneSetupStartContinue;
        onPressed = inTermux
            ? () => unawaited(_continueTermux(progress))
            : () => unawaited(_continue(progress));
        scene = SetupProgressView.sceneFor(l10n, progress);
        ambient = running;
        meter = KitProgress.known(
          progress.overall.clamp(0, 1).toDouble(),
          key: const ValueKey('phone-setup-start-meter'),
          tone: running ? null : AppStatusTone.neutral,
          semanticsLabel: l10n.setupProgressViewOverallLabel,
        );
      case _Hero.ready:
        headline = l10n.phoneSetupStartReadyHeadline;
        body = l10n.phoneSetupStartReadyBody;
        action = l10n.phoneSetupStartOpen;
        onPressed = () => unawaited(_open(progress));
        scene = const SetupPhoneScene(mood: SetupPhoneMood.ready);
      case _Hero.termux:
        headline = l10n.phoneSetupStartTermuxHeadline;
        body = l10n.phoneSetupStartTermuxBody;
        action = l10n.phoneSetupStartConnect;
        onPressed = _connectTermux;
        scene = const SetupPhoneScene(mood: SetupPhoneMood.termux);
    }
    final failure = _failure;
    final fresh = hero == _Hero.fresh;
    if (_opening) {
      scene = const SetupPhoneScene(mood: SetupPhoneMood.starting);
      ambient = true;
    } else if (failure != null && hero == _Hero.ready) {
      // Open could not start OpenCode or reach it.
      scene = const SetupUnpluggedScene();
    }
    return PhoneSetupHero(
      key: ValueKey('phone-setup-start-hero-${hero.name}'),
      scene: scene,
      ambient: ambient,
      // While Open starts OpenCode the title says so, never the promise.
      title: _opening ? l10n.connectStartingPhone : headline,
      titleKey: const ValueKey('phone-setup-start-headline'),
      body: _opening ? null : failure ?? body,
      bodyKey: failure != null && !_opening
          ? const ValueKey('phone-setup-start-failure')
          : preflight != null
          ? const ValueKey('phone-setup-start-preflight')
          : const ValueKey('phone-setup-start-body'),
      bodyTone: failure != null && !_opening ? AppStatusTone.failure : null,
      progress: _opening ? const KitProgress.waiting() : meter,
      // What Set up puts on the phone, next to the promise it counts, and
      // (B2) that it may be slow on a phone with little memory.
      content:
          fresh && (steps != null || includesText != null || slowNote != null)
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              spacing: KitTokens.of(context).space2,
              children: [
                if (steps != null)
                  KitText(
                    steps,
                    key: const ValueKey('phone-setup-start-steps'),
                    role: KitTextRole.label,
                  ),
                if (slowNote != null)
                  KitText(
                    slowNote,
                    key: const ValueKey('phone-setup-start-may-be-slow'),
                    role: KitTextRole.secondary,
                  ),
                if (includesText != null)
                  KitText(
                    includesText,
                    key: const ValueKey('phone-setup-start-includes'),
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                  ),
              ],
            )
          : null,
      primary: _opening
          ? null
          : KitAction(
              key: const ValueKey('phone-setup-start-primary'),
              label: action,
              onPressed: _busy || preflight != null ? null : onPressed,
              working: _busy,
            ),
      tertiary: [
        if (preflight?.issue == SetupPreflightIssue.lowSpace)
          KitAction(
            key: const ValueKey('phone-setup-start-open-storage'),
            label: l10n.phoneSetupPreflightOpenStorage,
            onPressed: _busy ? null : _openStorageSettings,
          ),
        // Trimming the selection can clear low space; it changes nothing
        // for a CPU or memory the phone simply does not have.
        if (fresh &&
            includesText != null &&
            preflight?.issue != SetupPreflightIssue.unsupportedAbi &&
            preflight?.issue != SetupPreflightIssue.lowMemory)
          KitAction(
            key: const ValueKey('phone-setup-start-customize'),
            label: l10n.phoneSetupStartCustomize,
            onPressed: _busy ? null : _customize,
          ),
      ],
      footer: _otherWays(context, l10n, hero, includesText),
    );
  }

  /// The less common ways in: one row that unfolds in place (a rare
  /// choice, [KitExpandRow]), then plain rows.
  Widget _otherWays(
    BuildContext context,
    AppLocalizations l10n,
    _Hero hero,
    String? includesText,
  ) {
    final large = AppTheme.stackedActions(context);
    final termux = _termux;
    Widget row({
      required Key key,
      required IconData icon,
      required String title,
      String? detail,
      required VoidCallback onTap,
    }) => KitRow(
      key: key,
      leading: KitRow.icon(context, icon),
      title: title,
      titleMaxLines: large ? 3 : 1,
      supporting: detail == null ? null : TextSpan(text: detail),
      supportingMaxLines: large ? 3 : 2,
      trailing: const KitChevron(),
      onTap: _busy ? null : onTap,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        const KitDivider(),
        KitExpandRow(
          headerKey: const ValueKey('phone-setup-start-other-ways'),
          title: l10n.phoneSetupStartOtherWays,
          expanded: _otherWaysOpen,
          onExpansionChanged: (open) => setState(() => _otherWaysOpen = open),
          children: [
            // Termux already runs OpenCode, so Connect leads; the in-app
            // setup stays one tap away for someone who wants to move off
            // Termux.
            if (hero == _Hero.termux)
              row(
                key: const ValueKey('phone-setup-start-set-up-here'),
                icon: AppIconography.phone,
                title: l10n.phoneSetupStartSetUpHere,
                detail: includesText,
                onTap: () => unawaited(_run(_selection)),
              ),
            // Both are on the phone: the app leads with the one inside it,
            // and the one in Termux stays one tap away (map statesMissing
            // "both in-app and Termux present").
            if (hero == _Hero.ready && _termuxPresent)
              row(
                key: const ValueKey('phone-setup-start-connect-termux'),
                icon: AppIconography.terminal,
                title: l10n.phoneSetupStartUseTermuxOne,
                detail: l10n.phoneSetupStartUseTermuxOneDetail,
                onTap: _connectTermux,
              ),
            if (hero != _Hero.termux &&
                !(hero == _Hero.ready && _termuxPresent))
              row(
                key: const ValueKey('phone-setup-start-use-termux'),
                icon: AppIconography.terminal,
                title: l10n.phoneSetupStartUseTermux,
                // Termux is there but has not let the app in yet (map
                // statesMissing "Termux installed but not yet allowed"):
                // the Termux setup is where that permission is given.
                // What it costs, said before anything installs.
                detail: termux?.state == TermuxRunningServerState.denied
                    ? l10n.phoneSetupStartTermuxNotAllowed
                    : l10n.phoneSetupTermuxCost,
                onTap: () => unawaited(_useTermux()),
              ),
            row(
              key: const ValueKey('phone-setup-start-by-address'),
              icon: AppIconography.link,
              title: l10n.phoneSetupStartByAddress,
              onTap: _connectByAddress,
            ),
          ],
        ),
      ],
    );
  }
}

/// Everything started from this screen is a first setup, so it ends on
/// "name your first project".
Future<void> _openFirstSetupProgress(BuildContext context) =>
    openPhoneSetupProgress(context, firstSetup: true);
