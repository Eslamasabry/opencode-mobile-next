/// AI Team row helpers that outlived the Plugins page (retired: the AI Team
/// has its own Settings row, and the server's plugin inventory is a section
/// of Settings > This server).
library;

import '../../../l10n/app_localizations.dart';
import '../../../state/orchestration.dart';
import '../../../state/team_project_demo.dart' show teamProjectDemoConfig;
import '../../../state/profiles.dart';
import '../../../termux/bridge.dart';
import '../../../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../../widgets/team_discovery_card.dart' show TeamDiscovery;
import '../../widgets/team_technical_details.dart';

export '../../widgets/team_technical_details.dart' show teamReadOnly;

/// True when [profile] is the app-managed Termux server on this phone: the
/// only profile that gets the "On this phone" section and the re-offer.
bool teamPhoneProfile(ServerProfile? profile) =>
    profile != null &&
    TermuxBridge.supported &&
    TermuxBridge.managesServerUrl(profile.baseUrl);

/// The reason an [OrchestrationErrorKind] gives the row, lower-case so it
/// reads after "Not available on this server ·".
String teamErrorReason(AppLocalizations l10n, OrchestrationErrorKind kind) =>
    switch (kind) {
      OrchestrationErrorKind.notGasCity => l10n.teamUiReasonNotGasCity,
      OrchestrationErrorKind.cityNotRunning => l10n.teamUiReasonCityNotRunning,
      OrchestrationErrorKind.plainHttpRefused => l10n.teamUiReasonPlainHttp,
      OrchestrationErrorKind.unreachable => l10n.teamUiReasonUnreachable,
      OrchestrationErrorKind.readFailed => l10n.teamUiReasonReadFailed,
    };

/// How long a first unreachable answer still reads as "Checking…".
const teamFirstProbeGrace = Duration(seconds: 30);

/// The row subtitle for every state of 02-ux §1.1.
String teamRowSubtitle(
  AppLocalizations l10n, {
  required ServerProfile profile,
  required OrchestrationController? orchestration,
  required TeamDiscovery? discovery,
  required DateTime now,
}) {
  final config = profile.orchestration;
  // The server by the name a person knows it: "This phone" for the one on
  // this phone, not the name it was saved under.
  final server = teamPhoneProfile(profile) || looksLikeInAppServer(profile)
      ? l10n.phoneServerCardTitle
      : profile.name;
  if (config == null) {
    // "Off", or where a team was found. The engine's name, its version
    // and "Add manually" are on the AI Team page, not in the row.
    final found = discovery?.result;
    if (found != null) return l10n.pluginsTeamRowFound(server);
    return l10n.teamUiRowOff;
  }
  // The simulator Try the demo attaches: simulated projects, not a team
  // that is on.
  if (config.provider == teamProjectDemoConfig.provider &&
      config.url == teamProjectDemoConfig.url) {
    return l10n.teamProjectDemo;
  }
  final c = orchestration;
  if (c == null || c.profileId != profile.id) {
    // A phone team that never proved itself (a failed turn-on leaves its
    // settings behind) is off, not "On".
    if (config.provider == OrchestrationProvider.phoneEngine) {
      return l10n.teamUiRowOff;
    }
    return l10n.teamUiRowOn(server);
  }
  switch (c.phase) {
    case OrchestrationPhase.idle:
    case OrchestrationPhase.probing:
    case OrchestrationPhase.connecting:
      return l10n.teamUiRowConnecting;
    case OrchestrationPhase.failed:
      final error = c.lastError;
      // The host often answers a moment later (it was still starting): the
      // first miss reads as a check in progress, not as a failure.
      final failedAt = c.failedAt;
      if (error?.kind == OrchestrationErrorKind.unreachable &&
          !c.everReady &&
          failedAt != null &&
          now.difference(failedAt) < teamFirstProbeGrace) {
        return l10n.teamUiRowChecking;
      }
      return error == null
          ? l10n.teamUiRowNotAvailable
          : l10n.teamUiRowNotAvailableReason(teamErrorReason(l10n, error.kind));
    case OrchestrationPhase.stopped:
      return l10n.teamUiRowOn(server);
    case OrchestrationPhase.ready:
      switch (c.streamStatus) {
        case OrchestrationStreamStatus.connecting:
        case OrchestrationStreamStatus.reconnecting:
          final since = c.lastEventAt ?? c.lastRefreshedAt;
          if (since != null &&
              now.difference(since) >= const Duration(minutes: 1)) {
            return l10n.teamUiRowUnreachable(
              now.difference(since).inMinutes.toString(),
            );
          }
          return l10n.teamUiRowReconnecting;
        case OrchestrationStreamStatus.closed:
        case OrchestrationStreamStatus.live:
          return teamReadOnly(config, c)
              ? l10n.teamUiRowOnReadOnly(server)
              : l10n.teamUiRowOn(server);
      }
  }
}
