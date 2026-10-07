import 'dart:async';

import 'package:flutter/material.dart';

import '../../../builtin/setup/phone_setup.dart';
import '../../../builtin/setup/setup_contract.dart';
import '../../../builtin/setup/setup_engine.dart' show setupAddingTitle;
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/setup_progress_view.dart';
import 'phone_setup_routes.dart';

/// Screen B: "Setting up OpenCode on this phone"
/// (docs/design/phone-setup-v2-2026-09-24.md).
///
/// A thin host for [SetupProgressView]: it watches the engine, offers
/// Cancel and Continue, and hands over to screen C when the job is done.
/// The job runs natively under a foreground service, so leaving this screen
/// (Back, home, the app killed) never stops it; coming back re-reads it.
class PhoneSetupProgressScreen extends StatefulWidget {
  const PhoneSetupProgressScreen({
    super.key,
    this.engine,
    this.openReady = openPhoneSetupReady,
    this.firstSetup = false,
  });

  /// Only a first setup ends on screen C ("name your first project"). An
  /// update or added tools end here: the finished list shows for a moment,
  /// then the screen closes back to where it was opened.
  final bool firstSetup;

  /// Tests pass a fake; the app uses [PhoneSetup.engine].
  final SetupEngine? engine;

  /// Screen C. A parameter only so tests can see the hand-over.
  final Future<void> Function(BuildContext context) openReady;

  @override
  State<PhoneSetupProgressScreen> createState() =>
      _PhoneSetupProgressScreenState();
}

class _PhoneSetupProgressScreenState extends State<PhoneSetupProgressScreen> {
  late final SetupEngine _engine = widget.engine ?? PhoneSetup.engine;

  /// Set once the persisted job has been read. Until then the in-memory
  /// value may be a previous job's "done", which must not skip to screen C.
  bool _restored = false;
  bool _handedOff = false;

  @override
  void initState() {
    super.initState();
    _engine.progress.addListener(_onProgress);
    unawaited(_restore());
  }

  @override
  void dispose() {
    _engine.progress.removeListener(_onProgress);
    super.dispose();
  }

  Future<void> _restore() async {
    try {
      await _engine.restore();
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

  /// "Stop setup?" (phone-setup-progress-stop-sheet): a stop, since it
  /// ends running work; it says what is kept and where to pick it up.
  Future<void> _cancel() async {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final stop = await showKitConfirm(
      context,
      title: l10n.phoneSetupProgressStopTitle,
      body: l10n.phoneSetupProgressStopMessage,
      confirmLabel: l10n.phoneSetupProgressStopConfirm,
      cancelLabel: l10n.phoneSetupProgressKeepGoing,
      icon: AppIconography.stop,
      kind: KitConfirmKind.stop,
      consequenceItems: [
        KitConsequence(
          l10n.phoneSetupProgressStopContinueLater,
          key: const Key('phone-setup-progress-stop-later'),
        ),
      ],
      confirmKey: const Key('phone-setup-progress-stop-confirm'),
    );
    if (!stop || !mounted) return;
    await _engine.cancel();
  }

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

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // Back just leaves; the job carries on in the background and the
    // notification brings the person back here. The bar names the place
    // the job belongs to, as the start screen does; the job's own title is
    // the view's headline.
    return KitScreen(
      // B6: a saved server's connection problem is not about this
      // phone's own setup: one line, its ways out behind More.
      bodyQuiets: const {KitStatusKind.connection},
      topBar: KitTopBar(title: l10n.phoneSetupStartScreenTitle),
      width: KitScreenWidth.reading,
      body: ValueListenableBuilder<SetupProgress>(
        valueListenable: _engine.progress,
        builder: (context, progress, _) {
          final ids = _defaultIds();
          return SetupProgressView(
            progress: progress,
            title: progress.adding.isEmpty
                ? l10n.phoneSetupProgressTitle
                : setupAddingTitle(l10n, _engine.registry, progress.adding),
            // Before the first poll the job's own list is not known yet;
            // the default selection is the best honest guess.
            components: progress.components.isNotEmpty
                ? _engine.registry
                : [
                    for (final c in _engine.registry)
                      if (ids.contains(c.id)) c,
                  ],
            // FB2: a first setup says where it sits in the journey (install,
            // name a project, chat); an update or added tools end here.
            note: widget.firstSetup && progress.adding.isEmpty
                ? l10n.phoneSetupProgressFirstSetupNote
                : l10n.phoneSetupProgressLeaveHint,
            onCancel: progress.state == SetupState.running ? _cancel : null,
            onContinue: progress.canContinue ? _continue : null,
          );
        },
      ),
    );
  }
}
