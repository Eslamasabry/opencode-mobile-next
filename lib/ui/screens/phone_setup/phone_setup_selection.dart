import 'dart:math' as math;

import '../../../builtin/setup/preflight.dart';
import '../../../builtin/setup/setup_contract.dart';
import '../../../l10n/app_localizations.dart';

/// Screen-side arithmetic over the component registry: what a selection
/// really installs and how long and how big that is. Every number screen A
/// and the Customize sheet show comes from here, so a component added to the
/// registry changes the promise without touching a screen.

/// What "Set up" installs when the person changes nothing: every required
/// component plus the optional ones that are on by default.
Set<String> defaultSetupSelection(List<SetupComponent> registry) => {
  for (final component in registry)
    if (component.required || component.defaultOn) component.id,
};

/// [chosen] plus every required component and everything they depend on, in
/// registry (dependency) order. This mirrors what the engine will run, so the
/// totals never promise less than what actually gets installed.
List<SetupComponent> expandSetupSelection(
  List<SetupComponent> registry,
  Set<String> chosen, {
  bool includeRequired = true,
}) {
  final byId = {for (final component in registry) component.id: component};
  final wanted = <String>{};
  void add(String id) {
    if (!wanted.add(id)) return;
    for (final dependency in byId[id]?.dependsOn ?? const <String>[]) {
      add(dependency);
    }
  }

  for (final component in registry) {
    if ((includeRequired && component.required) ||
        chosen.contains(component.id)) {
      add(component.id);
    }
  }
  return [
    for (final component in registry)
      if (wanted.contains(component.id)) component,
  ];
}

/// Seconds and bytes of [components]. Unknown sizes count as zero rather
/// than being guessed.
({int seconds, int bytes}) setupTotals(Iterable<SetupComponent> components) {
  var seconds = 0;
  var bytes = 0;
  for (final component in components) {
    seconds += component.estimatedSeconds;
    bytes += component.downloadBytes ?? 0;
  }
  return (seconds: seconds, bytes: bytes);
}

/// "About 4 minutes": rounded, never below one minute, because a promise of
/// "about 0 minutes" is not a promise.
String setupDurationText(AppLocalizations l10n, int seconds) =>
    l10n.phoneSetupStartAboutMinutes(math.max(1, (seconds / 60).round()));

/// FB2: the whole first-setup journey in one line, "Install, name a
/// project, chat · about 4 min". The minutes are the same registry estimate
/// as [setupDurationText] (rounded, never below one), so the line and any
/// other promise on the screen never disagree.
String setupStepsText(AppLocalizations l10n, int seconds) =>
    l10n.phoneSetupStartSteps(
      l10n.phoneSetupStartStepsTime(math.max(1, (seconds / 60).round())),
    );

/// "165 MB" or "1.2 GB", in decimal units like the store listing and the
/// download managers people compare against.
String setupSizeText(AppLocalizations l10n, int bytes) {
  final megabytes = bytes / 1000000;
  if (megabytes >= 1000) {
    final gigabytes = megabytes / 1000;
    return l10n.phoneSetupStartGigabytes(gigabytes.toStringAsFixed(1));
  }
  return l10n.phoneSetupStartMegabytes(
    math.max(1, megabytes.round()).toString(),
  );
}

/// "Git, Python and Node.js" with the locale's own separator and "and".
String joinSetupNames(AppLocalizations l10n, List<String> names) {
  if (names.isEmpty) return '';
  if (names.length == 1) return names.single;
  final head = names
      .sublist(0, names.length - 1)
      .join(l10n.phoneSetupStartListSeparator);
  return l10n.phoneSetupStartListPair(head, names.last);
}

/// The tools a selection brings along, for "Includes …". The Linux base is
/// plumbing the person never meets, and OpenCode is the agent the whole
/// screen is about, so neither is listed as something "included".
List<String> includedToolNames(Iterable<SetupComponent> components) => [
  for (final component in components)
    if (!component.native && component.id != 'opencode') component.shortTitle,
];

/// The headline for a pre-flight problem (P0.8): shown instead of the
/// promise on screen A's fresh state, and instead of the totals in the
/// Customize/Add tools sheet, before anything downloads.
String setupPreflightHeadline(
  AppLocalizations l10n,
  SetupPreflightIssue issue,
) => switch (issue) {
  SetupPreflightIssue.unsupportedAbi =>
    l10n.phoneSetupPreflightUnsupportedHeadline,
  SetupPreflightIssue.lowMemory => l10n.phoneSetupPreflightLowMemoryHeadline,
  SetupPreflightIssue.lowSpace => l10n.phoneSetupPreflightLowSpaceHeadline,
};

/// The one honest sentence for [result] (never called when it is
/// [SetupPreflightResult.supported]): low space names how much to free.
String setupPreflightBody(AppLocalizations l10n, SetupPreflightResult result) {
  final issue = result.issue;
  if (issue == null) return '';
  return switch (issue) {
    SetupPreflightIssue.unsupportedAbi =>
      l10n.phoneSetupPreflightUnsupportedBody(result.reportedAbi ?? ''),
    SetupPreflightIssue.lowMemory => l10n.phoneSetupPreflightLowMemoryBody(
      minimumSetupMemoryMb,
      result.totalMemoryMb ?? 0,
    ),
    SetupPreflightIssue.lowSpace => l10n.phoneSetupPreflightLowSpaceBody(
      setupSizeText(l10n, result.bytesToFree),
    ),
  };
}

/// The components a person installs: the registry without the steps the
/// engine runs by itself at the end of every job ("Start OpenCode"), which
/// have no size, no switch and nothing to choose.
List<SetupComponent> installableComponents(List<SetupComponent> registry) => [
  for (final component in registry)
    if (!component.jobStep) component,
];
