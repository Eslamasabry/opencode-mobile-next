import 'package:flutter/foundation.dart';

import '../domain/phone_agents.dart';
import '../platform/network.dart';

/// The reasons a phone check was required that an app update causes by
/// itself: the pinned agent or the shared helper moved to another version.
/// `firstCheck` and `installationChanged` are not here: a person has to see
/// those.
const phoneAgentAutoUpdateReasons = {'agentVersion', 'helperVersion'};

/// Whether [row] is an installed agent behind the app's pin (an update is
/// owed), as opposed to one that was never installed or needs a person.
bool phoneAgentOwesUpdate(AgentRow row) =>
    row.behindPin ||
    (row.status == PhoneAgentStatus.needsQualification &&
        phoneAgentAutoUpdateReasons.contains(row.checkRequiredReason));

/// Downloads above this are not started over mobile data (or any metered
/// network): the update waits for Wi-Fi.
const autoUpdateMobileLimitBytes = 50 * 1000 * 1000;

/// Whether an automatic download of [downloadBytes] may start on [network].
/// Small ones go anywhere; a big one needs a network that is neither mobile
/// nor metered. An unreadable network does not block (the person's own
/// download rules are Android's to enforce).
bool autoUpdateNetworkAllowed({
  required int downloadBytes,
  required NetworkReading network,
}) =>
    downloadBytes <= autoUpdateMobileLimitBytes ||
    !(network.transport == NetworkTransport.mobile || network.metered == true);

/// How the network is read; tests replace it.
Future<NetworkReading> Function() autoUpdateNetworkReader = () =>
    NetworkBridge().current();

/// What stands between a person and Send while an automatic update runs
/// (the update has reached a step that cannot yield, or the agent has no
/// older version to run on). The composer reads it, shows [fraction] and
/// enables Send again when it clears; the draft stays.
@immutable
class AgentUpdateBlock {
  const AgentUpdateBlock({
    required this.agentName,
    this.fraction,
    this.everyAgent = false,
  });

  /// The agent being updated, as the person sees it ("Claude Code").
  final String agentName;

  /// 0 to 1; null when not known yet.
  final double? fraction;

  /// Every agent's Send waits (the update is switching the shared helper),
  /// not just [agentName]'s.
  final bool everyAgent;

  bool blocks(String? name) =>
      name != null && (everyAgent || name == agentName);

  @override
  bool operator ==(Object other) =>
      other is AgentUpdateBlock &&
      other.agentName == agentName &&
      other.fraction == fraction &&
      other.everyAgent == everyAgent;

  @override
  int get hashCode => Object.hash(agentName, fraction, everyAgent);
}

final ValueNotifier<AgentUpdateBlock?> agentUpdateBlock = ValueNotifier(null);

/// Whether the app should move one installed agent to its pinned version
/// now, by itself. Mirrors `shouldAutoUpdateInApp` (OpenCode): never under
/// running work, never over another setup job or check, one try per pin per
/// app run (a failure is left to the row's Check).
bool shouldAutoUpdateAgent({
  required bool owesUpdate,
  required bool busy,
  required bool checkRunning,
  required bool setupJobRunning,
  required bool attemptedThisRun,
  bool networkAllowed = true,
}) {
  if (!owesUpdate) return false;
  if (!networkAllowed) return false;
  if (attemptedThisRun) return false;
  if (busy || checkRunning || setupJobRunning) return false;
  return true;
}
