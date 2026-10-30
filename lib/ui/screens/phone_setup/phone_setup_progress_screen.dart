import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../builtin/setup/phone_setup.dart';
import '../../../builtin/setup/setup_contract.dart';
import '../../../builtin/setup/setup_engine.dart' show setupAddingTitle;
import '../../../l10n/app_localizations.dart';
import '../../../platform/network.dart';
import '../../../termux/bridge.dart' show TermuxBridge;
import '../../../voice/automatic_setup_platform.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../kit/scenes/setup_steps_scene.dart' show SetupSceneStage;
import '../../widgets/setup_progress_view.dart';
import 'phone_setup_routes.dart';

/// Screen B: setting up OpenCode on this phone (vision pick "Phone setup
/// progress" B, 2026-10-20; docs/qa/vision-phone-setup-2026-10-20/).
///
/// One big ring with a real figure (the current step's measured share, or
/// steps done of all when the step reports no amount), one sentence of
/// what is happening now, a slim trail of the job's steps, one cost line
/// with real numbers only (time left once the engine can say, the network
/// in use), "Safe to leave", and Pause setup / Continue setup. Every step,
/// the log and Report this failure are one tap down, under Details.
///
/// The job runs natively under a foreground service, so leaving this screen
/// (Back, home, the app killed) never stops it; coming back re-reads it.
/// Pause is the engine's stop: finished steps stay and downloads resume, so
/// Continue setup picks up where it stopped.
class PhoneSetupProgressScreen extends StatefulWidget {
  const PhoneSetupProgressScreen({
    super.key,
    this.engine,
    this.openReady = openPhoneSetupReady,
    this.firstSetup = false,
    this.askNotifications = _askNotifications,
    this.readNetwork,
  });

  /// Only a first setup ends on screen C ("name your first project"). An
  /// update or added tools end here: the finished ring shows for a moment,
  /// then the screen closes back to where it was opened.
  final bool firstSetup;

  /// Tests pass a fake; the app uses [PhoneSetup.engine].
  final SetupEngine? engine;

  /// Asks Android for the notification permission (the system prompt) and
  /// answers whether the person can now be notified. A parameter so tests
  /// can script both answers.
  final Future<bool> Function() askNotifications;

  /// Screen C. A parameter only so tests can see the hand-over.
  final Future<void> Function(BuildContext context) openReady;

  /// Reads Android's default network, for the cost line ("Wi-Fi"): on open
  /// and whenever the job moves to another step. Null reads the platform's
  /// own; where there is none it is unknown and the line says nothing.
  final Future<NetworkReading> Function()? readNetwork;

  /// Forgets the answer kept for this app run.
  @visibleForTesting
  static void resetNotificationAnswer() =>
      _PhoneSetupProgressScreenState._answer = null;

  @override
  State<PhoneSetupProgressScreen> createState() =>
      _PhoneSetupProgressScreenState();
}

Future<bool> _askNotifications() =>
    const AndroidVoiceSetupPlatform().askNotificationsIfWired();

class _PhoneSetupProgressScreenState extends State<PhoneSetupProgressScreen> {
  late final SetupEngine _engine = widget.engine ?? PhoneSetup.engine;

  /// Set once the persisted job has been read. Until then the in-memory
  /// value may be a previous job's "done", which must not skip to screen C.
  bool _restored = false;
  bool _handedOff = false;

  /// One system prompt per app run: the moment setup starts and says
  /// it will notify. Android shows nothing when already granted and stops
  /// showing it after repeated refusals, so this never nags. Until the
  /// answer is yes the screen promises no notification.
  static bool? _answer;
  // Promises until a refusal is known, so the first frame is unchanged.
  bool _canNotify = true;

  /// The pending job read; leaving the screen cancels its timeout.
  final _reads = SetupReadScope();

  NetworkTransport _transport = NetworkTransport.unknown;

  /// The job and step the network was last read for.
  String? _networkStep;

  /// Which job [_eta] and [_measured] belong to.
  String? _job;

  /// The time left as shown, in seconds: it never goes up within a job, and
  /// the steps not started yet count at least their own estimates, so a
  /// fast download never reads "Less than a minute" with the install still
  /// to come. Null until the engine gives an estimate.
  int? _eta;

  /// The current stage's furthest measured share, keyed by step and stage:
  /// within a stage the ring never goes back; a new stage that measures
  /// from zero is a new arc.
  String _measuredKey = '';
  double _measured = 0;

  @override
  void initState() {
    super.initState();
    _engine.progress.addListener(_onProgress);
    unawaited(_restore());
    unawaited(_askToNotify());
  }

  Future<void> _readNetwork() async {
    // No deadline: the line only says what Android answers, and a timer
    // left behind would outlive the screen.
    final read = widget.readNetwork ?? const NetworkBridge().currentUntimed;
    NetworkTransport transport;
    try {
      transport = (await read()).transport;
    } catch (_) {
      transport = NetworkTransport.unknown;
    }
    if (mounted && transport != _transport) {
      setState(() => _transport = transport);
    }
  }

  Future<void> _askToNotify() async {
    // Asked already this run: reuse the answer, never prompt again.
    var granted = _answer;
    if (granted == null) {
      try {
        granted = await widget.askNotifications();
      } catch (_) {
        granted = false;
      }
      _answer = granted;
    }
    if (mounted && granted != _canNotify) setState(() => _canNotify = granted!);
  }

  @override
  void dispose() {
    _engine.progress.removeListener(_onProgress);
    _reads.cancel();
    super.dispose();
  }

  Future<void> _restore() async {
    try {
      await _engine.restore(scope: _reads);
    } catch (_) {
      // Nothing persisted, or the channel is not up yet: the live progress
      // still arrives through the listener.
    }
    if (!mounted) return;
    _restored = true;
    _onProgress();
  }

  void _onProgress() {
    if (!_restored || _handedOff || !mounted) return;
    if (_engine.progress.value.state != SetupState.done) return;
    _handedOff = true;
    if (!widget.firstSetup) {
      Future<void>.delayed(const Duration(milliseconds: 1200), () {
        if (mounted) Navigator.of(context).maybePop();
      });
      return;
    }
    final navigator = Navigator.of(context);
    final route = ModalRoute.of(context);
    // The "ready" notification must not offer screen C again for this job.
    PhoneSetup.markReadyShown(_engine.progress.value.jobId);
    unawaited(widget.openReady(context));
    // Screen C replaces this one, so Back from C does not land on a
    // finished progress screen. Removed after C is pushed (a frame later)
    // rather than popped first, so C's own transition plays normally; if C
    // pushed nothing, this screen stays and shows the finished job.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (route != null && route.isActive && !route.isCurrent) {
        navigator.removeRoute(route);
      }
    });
  }

  /// Pause: the engine's stop. Nothing finished is lost and downloads
  /// resume, so it needs no confirmation (DATA-11).
  Future<void> _pause() => _engine.cancel();

  Future<void> _continue() async {
    // The same components as the job that stopped: the check scripts skip
    // what finished, so this picks up where it left off.
    final ids = _engine.progress.value.components.map((c) => c.id).toSet();
    await _engine.run(ids.isNotEmpty ? ids : _defaultIds());
  }

  Set<String> _defaultIds() => {
    for (final c in _engine.registry)
      if (c.required || c.defaultOn) c.id,
  };

  /// The rows the screen draws: the job's own, or before its first poll
  /// the default selection as waiting rows, the best honest guess.
  List<ComponentProgress> _rowsOf(SetupProgress progress) {
    if (progress.components.isNotEmpty) return progress.components;
    final ids = _defaultIds();
    return [
      for (final c in _engine.registry)
        if (ids.contains(c.id))
          ComponentProgress(id: c.id, state: ComponentState.pending),
    ];
  }

  String _titleOf(String id) {
    for (final component in _engine.registry) {
      if (component.id == id) return component.shortTitle;
    }
    return id;
  }

  void _track(SetupProgress progress) {
    final job = progress.jobId ?? progress.components.map((c) => c.id).join();
    if (job != _job) {
      _job = job;
      _eta = null;
      _measuredKey = '';
      _measured = 0;
    }
    // Read the network again when the job moves on, without a timer.
    final step = '$job|${progress.current}';
    if (step != _networkStep) {
      _networkStep = step;
      unawaited(_readNetwork());
    }
    final raw = progress.etaSeconds;
    if (progress.state != SetupState.running || raw == null) return;
    var later = 0;
    for (final row in progress.components) {
      if (row.state != ComponentState.pending) continue;
      for (final component in _engine.registry) {
        if (component.id == row.id) later += component.estimatedSeconds;
      }
    }
    final estimate = math.max(raw, later);
    final shown = _eta;
    _eta = shown == null ? estimate : math.min(shown, estimate);
  }

  /// The current step's measured share, never less than this stage
  /// already showed; null when the step reports no amount.
  double? _measuredOf(ComponentProgress row) {
    if (row.state != ComponentState.running) return null;
    final total = row.bytesTotal;
    final double? fraction = total != null && total > 0 && row.bytesDone != null
        ? (row.bytesDone! / total).clamp(0, 1).toDouble()
        : row.percent == null
        ? null
        : (row.percent! / 100).clamp(0, 1).toDouble();
    if (fraction == null) return null;
    final key = '${row.id}|${row.stage}';
    if (key != _measuredKey) {
      _measuredKey = key;
      _measured = fraction;
    } else {
      _measured = math.max(_measured, fraction);
    }
    return _measured;
  }

  /// [downloading]: the current step is measuring a download, so the
  /// network it uses is part of the cost even before a time is known.
  String? _costLine(
    AppLocalizations l10n,
    SetupProgress progress, {
    required bool downloading,
  }) {
    final parts = <String>[];
    final eta = _eta;
    if (eta != null) {
      final lastStep = !progress.components.any(
        (row) => row.state == ComponentState.pending,
      );
      parts.add(
        eta < 60 && lastStep
            ? l10n.phoneSetupRingUnderMinute
            : l10n.phoneSetupRingMinutesLeft(math.max(1, (eta / 60).round())),
      );
    }
    // The network alone says nothing while nothing downloads.
    if (parts.isEmpty && !downloading) return null;
    switch (_transport) {
      case NetworkTransport.wifi:
        parts.add(l10n.phoneSetupRingWifi);
      case NetworkTransport.mobile:
        parts.add(l10n.phoneSetupRingMobileData);
      case NetworkTransport.other || NetworkTransport.unknown:
        break;
    }
    return parts.isEmpty ? null : parts.join(' · ');
  }

  Future<void> _openDetails() => showKitSheet<void>(
    context,
    title: lookupAppLocalizations(
      Localizations.localeOf(context),
    ).phoneSetupRingDetailsTitle,
    height: KitSheetHeight.content,
    sheetKey: const Key('phone-setup-progress-details-sheet'),
    body: (context) => ValueListenableBuilder<SetupProgress>(
      valueListenable: _engine.progress,
      builder: (context, progress, _) => SetupProgressView(
        progress: progress,
        checklistOnly: true,
        components: _engine.registry,
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // Back just leaves; the job carries on in the background and the
    // notification brings the person back here.
    return ValueListenableBuilder<SetupProgress>(
      valueListenable: _engine.progress,
      builder: (context, progress, _) {
        _track(progress);
        final ownership = SetupProgressView.needsOwnershipRecovery(progress);
        final Widget? bottom = switch (progress.state) {
          SetupState.running => KitButton.secondary(
            key: const Key('setup-progress-cancel'),
            label: l10n.phoneSetupRingPause,
            icon: AppIconography.pause,
            onPressed: () => unawaited(_pause()),
          ),
          _ when ownership => KitButton.primary(
            key: const Key('setup-progress-recover-ownership'),
            label: l10n.e7SetupOpenAppSettings,
            onPressed: () => unawaited(TermuxBridge.openAppSettings()),
          ),
          _ when progress.canContinue => KitButton.primary(
            key: const Key('setup-progress-continue'),
            label: l10n.setupProgressViewContinue,
            icon: AppIconography.play,
            onPressed: () => unawaited(_continue()),
          ),
          _ => null,
        };
        return KitScreen(
          // B6: a saved server's connection problem is not about this
          // phone's own setup: one line, its ways out behind More.
          bodyQuiets: const {KitStatusKind.connection},
          topBar: KitTopBar(
            title: progress.adding.isEmpty
                ? l10n.phoneSetupStartScreenTitle
                : setupAddingTitle(l10n, _engine.registry, progress.adding),
          ),
          width: KitScreenWidth.reading,
          bottom: bottom,
          body: LayoutBuilder(
            builder: (context, constraints) {
              final end = KitScreen.endPadding(context);
              return ListView(
                padding: EdgeInsets.only(bottom: end),
                children: [
                  ConstrainedBox(
                    constraints: BoxConstraints(
                      minHeight: math.max(0, constraints.maxHeight - end),
                    ),
                    // A little above the middle, where the eye rests.
                    child: Align(
                      alignment: const Alignment(0, -0.3),
                      child: _body(context, l10n, progress),
                    ),
                  ),
                ],
              );
            },
          ),
        );
      },
    );
  }

  Widget _body(
    BuildContext context,
    AppLocalizations l10n,
    SetupProgress progress,
  ) {
    final tokens = KitTokens.of(context);
    final rows = _rowsOf(progress);
    bool finished(ComponentProgress r) =>
        r.state == ComponentState.done || r.state == ComponentState.skipped;
    final done = rows.where(finished).length;
    final state = progress.state;
    final halted =
        state == SetupState.failed ||
        state == SetupState.interrupted ||
        state == SetupState.cancelled;

    // The step being worked on: the one running (or checking, or failed),
    // else the engine's current, else the first not finished.
    var current = rows.indexWhere(
      (r) =>
          r.state == ComponentState.running ||
          r.state == ComponentState.failed ||
          r.state == ComponentState.checking,
    );
    if (current < 0) {
      current = rows.indexWhere(
        (r) => r.id == progress.current && !finished(r),
      );
    }
    if (current < 0 && halted) current = rows.indexWhere((r) => !finished(r));
    final row = current < 0 ? null : rows[current];
    final checking =
        rows.isNotEmpty &&
        rows.every((r) => r.state == ComponentState.checking || finished(r)) &&
        rows.any((r) => r.state == ComponentState.checking);

    final String headline;
    String? note;
    Key? noteKey;
    if (state == SetupState.done) {
      headline = l10n.setupProgressViewDone;
    } else if (state == SetupState.failed) {
      headline = l10n.setupProgressViewFailedTitle;
      note = SetupProgressView.failureSentence(l10n, progress, _titleOf);
      noteKey = const Key('setup-progress-job-error');
    } else if (state == SetupState.interrupted) {
      headline = l10n.phoneSetupRingInterruptedTitle;
      note = l10n.phoneSetupRingStoppedNote;
    } else if (state == SetupState.cancelled) {
      headline = l10n.phoneSetupRingPausedTitle;
      note = l10n.phoneSetupRingStoppedNote;
    } else if (checking) {
      headline = l10n.phoneSetupRingChecking;
    } else if (row == null || row.state == ComponentState.pending) {
      headline = l10n.setupProgressViewGettingStarted;
    } else {
      final title = _titleOf(row.id);
      headline = switch (SetupProgressView.sceneFor(
        l10n,
        progress,
        rows,
      ).stage) {
        SetupSceneStage.start => l10n.phoneSetupStageStarting,
        SetupSceneStage.download => l10n.phoneSetupRingDownloading(title),
        SetupSceneStage.unpack => l10n.phoneSetupRingUnpacking(title),
        SetupSceneStage.install => l10n.phoneSetupRingInstalling(title),
      };
    }

    final trail = [
      for (var i = 0; i < rows.length; i++)
        finished(rows[i])
            ? KitTrailMark.done
            : i == current && !checking
            ? KitTrailMark.current
            : KitTrailMark.waiting,
    ];
    final measured = !halted && row != null ? _measuredOf(row) : null;
    final String? caption;
    if (measured != null &&
        row != null &&
        (row.bytesTotal ?? 0) > 0 &&
        row.bytesDone != null) {
      final (d, t) = SetupProgressView.formatBytePair(
        row.bytesDone!,
        row.bytesTotal!,
      );
      // Isolated, so the figures keep their order in a right-to-left line.
      caption = l10n.setupProgressViewBytes(KitBidi.ltr(d), KitBidi.ltr(t));
    } else {
      caption = null;
    }
    final label = l10n.setupProgressViewOverallLabel;
    const ringKey = Key('setup-progress-overall');
    const headlineKey = Key('setup-progress-title');
    final ring = state == SetupState.done
        ? KitProgressRing.finished(
            headline: headline,
            semanticsLabel: label,
            trail: trail,
            ringKey: ringKey,
            headlineKey: headlineKey,
          )
        : measured != null
        ? KitProgressRing.measured(
            value: measured,
            caption: caption,
            headline: headline,
            semanticsLabel: label,
            trail: trail,
            ringKey: ringKey,
            headlineKey: headlineKey,
          )
        : KitProgressRing.steps(
            done: done,
            of: rows.length,
            headline: headline,
            semanticsLabel: label,
            trail: trail,
            halted: halted,
            ringKey: ringKey,
            headlineKey: headlineKey,
          );

    final running = state == SetupState.running || state == SetupState.idle;
    final cost = running
        ? _costLine(l10n, progress, downloading: caption != null)
        : null;
    final quiet = running
        ? (_canNotify
              ? l10n.phoneSetupRingSafeToLeave
              : l10n.phoneSetupRingSafeToLeaveQuiet)
        : note;
    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: tokens.space5,
        vertical: tokens.space6,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ring,
          if (cost != null) ...[
            SizedBox(height: tokens.space6),
            KitText(
              cost,
              key: const Key('phone-setup-progress-cost'),
              role: KitTextRole.rowTitle,
              tone: KitTextTone.secondary,
              textAlign: TextAlign.center,
            ),
          ],
          if (quiet != null) ...[
            SizedBox(height: cost == null ? tokens.space6 : tokens.space2),
            // A short measure, so a two-line note breaks evenly.
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 320),
              child: KitText(
                quiet,
                key: noteKey,
                role: KitTextRole.secondary,
                tone: KitTextTone.tertiary,
                textAlign: TextAlign.center,
              ),
            ),
          ],
          SizedBox(height: tokens.space3),
          KitButton.tertiary(
            key: const Key('setup-progress-details'),
            label: l10n.kitDetails,
            onPressed: () => unawaited(_openDetails()),
          ),
        ],
      ),
    );
  }
}
