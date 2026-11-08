import 'dart:async';

import 'package:flutter/material.dart';

import '../../../builtin/setup/phone_setup.dart';
import '../../../builtin/setup/setup_contract.dart';
import '../../kit/kit.dart';
import 'phone_setup_customize_sheet.dart';
import 'phone_setup_progress_screen.dart';
import 'phone_setup_ready_screen.dart';
import 'phone_setup_start_screen.dart';

// Routes between the phone setup screens (docs/design/phone-setup-v2-2026-09-24.md).
// Each screen's owner replaces only its own function below; the others call
// these, so the screens can be built in parallel.

/// Screen A: "On this phone".
Future<void> openPhoneSetupStart(BuildContext context) =>
    Navigator.of(context).push(
      KitPageRoute<void>(
        settings: const RouteSettings(name: 'phone-setup-start'),
        builder: (_) => const PhoneSetupStartScreen(),
      ),
    );

/// Screen A's Customize sheet. [addMode] is "Add tools" from screen D: only
/// optional components, installed ones shown as installed.
///
/// Returns the ids to hand to `SetupEngine.run`, or null when dismissed:
/// the whole selection for first setup, only the new tools in [addMode].
/// [selected] reopens the sheet on an earlier choice instead of the
/// registry defaults.
Future<Set<String>?> showPhoneSetupCustomize(
  BuildContext context, {
  bool addMode = false,
  Set<String>? selected,
}) => showSetupCustomizeSheet(
  context,
  engine: PhoneSetup.engine,
  addMode: addMode,
  selected: selected,
);

/// Screen B: the setup in progress (and continue after an interruption).
///
/// [firstSetup] (screen A) ends on screen C; updates and added tools (the
/// "This phone" card) end back where they started.
Future<void> openPhoneSetupProgress(
  BuildContext context, {
  bool firstSetup = false,
}) => Navigator.of(context).push(_progressRoute(firstSetup));

/// Route names of screens B and C, so a notification tap can tell that the
/// person is already looking at the setup.
const phoneSetupProgressRouteName = 'phone-setup-progress';
const phoneSetupReadyRouteName = 'phone-setup-ready';

Route<void> _progressRoute(bool firstSetup) => KitPageRoute<void>(
  settings: const RouteSettings(name: phoneSetupProgressRouteName),
  builder: (_) => PhoneSetupProgressScreen(firstSetup: firstSetup),
);

Route<void> _readyRoute() => KitPageRoute<void>(
  settings: const RouteSettings(name: phoneSetupReadyRouteName),
  builder: (_) => const PhoneSetupReadyScreen(),
);

/// Screen C: ready, name the first project. It takes the progress screen's place, so Back never returns to a finished
/// setup. Screen B calls it only when a first setup is done; updates and
/// added tools end on B.
Future<void> openPhoneSetupReady(BuildContext context) =>
    Navigator.of(context).pushReplacement<void, void>(_readyRoute());

/// Where a tap on a phone setup notification lands (SetupService.kt), with
/// the app running, in the background or started by the tap.
///
/// The job is read first, since after a cold start only setup.json knows
/// it, and the job's state decides rather than which notification was
/// tapped (a progress notification can be tapped after the job finished):
/// - running or stopped: screen B, which shows it live or offers Continue.
///   A job begun as the first setup still ends on screen C, because the job
///   itself remembers that ([SetupProgress.firstSetup]);
/// - done: OpenCode is started and connected already, so bringing the app
///   forward is the whole answer, except for a first setup whose "name your
///   first project" has not been shown yet: that opens screen C;
/// - no job, or B or C already on top ([topRouteName]): nothing changes.
///
/// Returns the route pushed, or null when the app stays where it is.
Future<Route<void>?> openPhoneSetupFromNotification(
  NavigatorState navigator, {
  String? topRouteName,
  SetupEngine? engine,
  Duration restoreTimeout = const Duration(seconds: 3),
}) async {
  if (topRouteName == phoneSetupProgressRouteName ||
      topRouteName == phoneSetupReadyRouteName) {
    return null;
  }
  final setup = engine ?? PhoneSetup.engine;
  try {
    await setup.restore().timeout(restoreTimeout);
  } catch (_) {
    // Unreadable: what the engine already holds is the best answer.
  }
  if (!navigator.mounted) return null;
  final progress = setup.progress.value;
  final Route<void> route;
  switch (progress.state) {
    case SetupState.idle:
      return null;
    case SetupState.done:
      if (!progress.firstSetup || PhoneSetup.readyShownFor(progress.jobId)) {
        return null;
      }
      PhoneSetup.markReadyShown(progress.jobId);
      route = _readyRoute();
    case SetupState.running:
    case SetupState.interrupted:
    case SetupState.failed:
    case SetupState.cancelled:
      route = _progressRoute(progress.firstSetup);
  }
  unawaited(navigator.push(route));
  return route;
}
