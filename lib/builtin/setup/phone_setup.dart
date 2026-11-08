import 'package:flutter/foundation.dart';

import '../../l10n/app_localizations.dart';
import 'setup_contract.dart';
import 'setup_engine.dart';

/// Where the app gets its [SetupEngine]: [engine] installs into the app's
/// own Linux. It is the channel-backed engine (setup_engine.dart) unless a
/// test sets a fake.
class PhoneSetup {
  PhoneSetup._();

  static SetupEngine? _engine;

  static SetupEngine get engine => _engine ??= ChannelSetupEngine();

  static set engine(SetupEngine value) => _engine = value;

  /// The in-app engine only if something already made it: a screen that just
  /// watches setup must not start one (it polls the phone).
  static SetupEngine? get engineIfCreated => _engine;

  /// Jobs whose "name your first project" screen has been shown in this run
  /// of the app, so a later tap on the "ready" notification does not offer
  /// it a second time. Kept in memory only: after the app was killed, a
  /// first setup's ready notification leads to that screen again, which is
  /// still a sensible place to land.
  static final _readyShown = <String>{};

  static bool readyShownFor(String? jobId) =>
      jobId != null && _readyShown.contains(jobId);

  static void markReadyShown(String? jobId) {
    if (jobId != null) _readyShown.add(jobId);
  }

  @visibleForTesting
  static void resetReadyShown() => _readyShown.clear();

  /// Called by the app shell once it can start and connect to the phone's
  /// server: the engine's last step goes through [finisher], and its words
  /// follow the app's own language choice ([strings]).
  static void attach({
    required SetupFinisher finisher,
    AppLocalizations Function()? strings,
  }) {
    final current = engine;
    if (current is! ChannelSetupEngine) return;
    current.finisher = finisher;
    if (strings != null) current.strings = strings;
  }
}
