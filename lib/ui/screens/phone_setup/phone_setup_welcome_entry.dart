import 'dart:async';

import 'package:flutter/material.dart';

import '../../../builtin/setup/phone_setup.dart';
import '../../../builtin/setup/preflight.dart';
import '../../../builtin/setup/setup_contract.dart';
import '../../../l10n/app_localizations.dart';
import '../../../voice/device.dart';
import '../../widgets/product_states.dart' show productErrorText;
import 'phone_setup_routes.dart';
import 'phone_setup_selection.dart';

/// What the first-run welcome's "On this phone" card says under its title,
/// and what the page's main button does while that card is chosen.
@immutable
class PhoneWelcomeOffer {
  const PhoneWelcomeOffer({
    required this.line,
    required this.action,
    required this.onPressed,
    this.state = SetupState.idle,
    this.working = false,
    this.failed = false,
  });

  /// One calm line: the setup's cost, how far a started setup got, or why
  /// starting did not work.
  final String line;

  /// The main button's words, naming what it does ("Set up on this phone").
  final String action;
  final VoidCallback onPressed;

  /// The job on the phone, [SetupState.idle] when there is none.
  final SetupState state;

  /// Starting or resuming is under way.
  final bool working;

  /// [line] says why the last start did not work.
  final bool failed;
}

/// The phone half of the first-run welcome: what setting up on this phone
/// costs, whether a setup already exists, and the one action for it.
///
/// Every number in the cost line is the setup engine's own (the component
/// registry's time and download estimates, the same arithmetic as phone
/// setup's own page), so a component added to the registry changes the
/// promise without touching the welcome.
///
/// Someone who started setting up and then left (the app killed, a reboot,
/// a cancel) comes back to the welcome, because no server is saved until
/// setup ends. So the card says where it stands instead of the cost:
///
/// - running: "Setting up · 42%", and the button shows its progress;
/// - stopped part way: "Setup is 42% done" with Continue setup, which
///   resumes the same job (the checks skip what finished);
/// - done but nothing saved or connected: "Installed and ready" with Start on
///   this phone, which runs the job again so only its last step (start and
///   connect) happens.
///
/// With no job, Set up on this phone reads the phone first (CPU, memory,
/// free space) and, when nothing stands in the way, starts the default
/// setup at once and opens its progress. A phone that cannot run it, or
/// may be slow, goes to phone setup's own page, which says why before
/// anything downloads.
///
/// The job is read after the first frame, so the welcome never waits on it;
/// the engine is watched only once a job is known to exist.
class PhoneSetupWelcomeEntry extends StatefulWidget {
  const PhoneSetupWelcomeEntry({
    super.key,
    required this.builder,
    this.engine,
    this.openProgress = _openFirstSetupProgress,
    this.openStart = openPhoneSetupStart,
    this.deviceProbe,
    this.restoreTimeout = const Duration(seconds: 3),
    this.revision = 0,
  });

  /// Builds the welcome around the phone card's [PhoneWelcomeOffer].
  final Widget Function(BuildContext context, PhoneWelcomeOffer offer) builder;

  /// Bumped by the host when the person comes back from phone setup, where
  /// a job may have started: the job is read again.
  final int revision;

  /// Tests pass a fake; the app uses [PhoneSetup.engine].
  final SetupEngine? engine;

  /// Screen B as a first setup: the welcome only shows while nothing is
  /// saved, so whatever it starts is this phone's first setup.
  final Future<void> Function(BuildContext context) openProgress;

  /// Phone setup's own page (screen A), for a phone the pre-flight check
  /// stops and for "More ways to set up".
  final Future<void> Function(BuildContext context) openStart;

  /// The CPU, free space and memory reading; defaults to the one voice
  /// already collects.
  final Future<VoiceDeviceInfo> Function()? deviceProbe;

  /// How long the job read may take before the card gives up; a hung
  /// channel must never leave a spinner on the welcome.
  final Duration restoreTimeout;

  @override
  State<PhoneSetupWelcomeEntry> createState() => _PhoneSetupWelcomeEntryState();
}

class _PhoneSetupWelcomeEntryState extends State<PhoneSetupWelcomeEntry> {
  late final SetupEngine _engine = widget.engine ?? PhoneSetup.engine;

  /// Until the persisted job is read, an in-memory "done" may be stale, so
  /// the card offers a fresh setup (which resumes a job if one exists).
  bool _restored = false;
  bool _busy = false;
  String? _failure;

  /// Gives up waiting on a read that hangs. Owned (not a `Future.timeout`)
  /// so leaving the welcome cancels it.
  Timer? _giveUp;

  /// The pending job reads; leaving the welcome cancels their timeouts.
  final _reads = SetupReadScope();

  @override
  void initState() {
    super.initState();
    _giveUp = Timer(widget.restoreTimeout, _showWhatIsKnown);
    unawaited(_restore());
  }

  @override
  void didUpdateWidget(PhoneSetupWelcomeEntry oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.revision != widget.revision) unawaited(_restore());
  }

  @override
  void dispose() {
    _giveUp?.cancel();
    _reads.cancel();
    super.dispose();
  }

  Future<void> _restore() async {
    try {
      await _engine.restore(scope: _reads);
    } catch (_) {
      // No job, or no channel (desktop, tests): the welcome stays as it is.
    }
    _showWhatIsKnown();
  }

  void _showWhatIsKnown() {
    _giveUp?.cancel();
    _giveUp = null;
    // Always rebuilt: a read after coming back may have found a new job.
    if (mounted) setState(() => _restored = true);
  }

  /// What a fresh setup installs: the registry's required components and
  /// the ones on by default, exactly what phone setup's own page starts.
  List<SetupComponent> get _defaultInstall {
    final registry = installableComponents(_engine.registry);
    return expandSetupSelection(registry, defaultSetupSelection(registry));
  }

  /// The job's own components, so Continue resumes that job rather than a
  /// guess; the registry defaults only when the job listed none.
  Set<String> _selection(SetupProgress progress) {
    if (progress.components.isNotEmpty) {
      return {for (final component in progress.components) component.id};
    }
    return {for (final component in _defaultInstall) component.id};
  }

  /// Set up on this phone: the phone is read before anything downloads.
  Future<void> _start() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _failure = null;
    });
    VoiceDeviceInfo device;
    try {
      device =
          await (widget.deviceProbe ?? voiceDevicePlatform.getDeviceInfo)();
    } catch (_) {
      device = const VoiceDeviceInfo.unknown();
    }
    if (!mounted) return;
    final check = checkSetupPreflight(
      device,
      downloadBytes: setupTotals(_defaultInstall).bytes,
    );
    if (!check.supported || check.mayBeSlow) {
      // Phone setup's own page says why (or that it may be slow), with
      // its way out, before anything downloads.
      setState(() => _busy = false);
      await widget.openStart(context);
      if (mounted) unawaited(_restore());
      return;
    }
    setState(() => _busy = false);
    await _run(_engine.progress.value);
  }

  Future<void> _run(SetupProgress progress) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _failure = null;
    });
    try {
      // The job keeps "first setup", so a notification tap after the app
      // was killed still ends on "name your first project".
      await _engine.run(
        _selection(progress),
        params: SetupJobParams.firstSetup,
      );
    } catch (error) {
      if (!mounted) return;
      final l10n = lookupAppLocalizations(Localizations.localeOf(context));
      setState(() {
        _busy = false;
        _failure = l10n.phoneSetupStartFailed(productErrorText(error));
      });
      return;
    }
    if (!mounted) return;
    setState(() => _busy = false);
    await widget.openProgress(context);
    if (mounted) unawaited(_restore());
  }

  @override
  Widget build(BuildContext context) {
    // Watching the engine's progress makes it poll the native job, so the
    // welcome watches only once a job is known to exist: an untouched first
    // run costs one read, not a poll every half second.
    if (!_restored || _engine.progress.value.state == SetupState.idle) {
      return widget.builder(context, _offer(context, SetupProgress.idle));
    }
    return ValueListenableBuilder<SetupProgress>(
      valueListenable: _engine.progress,
      builder: (context, progress, _) =>
          widget.builder(context, _offer(context, progress)),
    );
  }

  PhoneWelcomeOffer _offer(BuildContext context, SetupProgress progress) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // Never "100%" while it is still going: the last step (starting
    // OpenCode) has no bar of its own. Same rule as phone setup's page.
    final percent = (progress.overall * 100).floor().clamp(0, 99);
    final String line;
    final String action;
    final VoidCallback onPressed;
    switch (progress.state) {
      case SetupState.idle:
        final totals = setupTotals(_defaultInstall);
        final time = setupDurationText(l10n, totals.seconds);
        line = totals.bytes > 0
            ? l10n.welcomePhoneCost(time, setupSizeText(l10n, totals.bytes))
            : time;
        action = l10n.welcomePhoneSetUp;
        onPressed = () => unawaited(_start());
      case SetupState.running:
        line = l10n.welcomePhoneSettingUp(percent);
        action = l10n.phoneSetupOpenWelcomeShowProgress;
        onPressed = () => unawaited(widget.openProgress(context));
      case SetupState.interrupted:
      case SetupState.failed:
      case SetupState.cancelled:
        line = l10n.welcomePhoneStopped(percent);
        action = l10n.welcomePhoneContinue;
        onPressed = () => unawaited(_run(progress));
      case SetupState.done:
        // Done, yet the welcome shows, so no server is saved: the job's
        // last step (save, start and connect) is what is missing, and
        // running the job again redoes only that.
        line = l10n.welcomePhoneReady;
        action = l10n.welcomePhoneOpen;
        onPressed = () => unawaited(_run(progress));
    }
    final failure = _failure;
    return PhoneWelcomeOffer(
      line: failure ?? line,
      action: action,
      onPressed: onPressed,
      state: progress.state,
      working: _busy,
      failed: failure != null,
    );
  }
}

Future<void> _openFirstSetupProgress(BuildContext context) =>
    openPhoneSetupProgress(context, firstSetup: true);
