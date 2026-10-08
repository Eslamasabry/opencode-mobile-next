import '../../builtin/builtin_server.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/profiles.dart';

/// The in-app server this phone is switching to while the app leaves another
/// in-app server (OpenCode 2 to OpenCode 1), or null when no such switch
/// runs. The server being left stops, rejects its old password and stops
/// answering on purpose: the header then names where it is going in one calm
/// progress state instead of failure words about where it was.
///
/// Read from what [starter] is bringing up ([BuiltinServerStarter.bringingUp])
/// against the server the connection's status is about. A restart of the
/// same server is not a switch.
ServerProfile? phoneRuntimeSwitchTarget(
  ConnectionController controller,
  BuiltinServerStarter starter,
) {
  final target = starter.bringingUp;
  if (target == null || !looksLikeInAppServer(target)) return null;
  final leavingId = controller.connectionStatus.profileId ?? '';
  if (leavingId.isEmpty || leavingId == target.id) return null;
  ServerProfile? leaving;
  for (final profile in controller.store.profiles) {
    if (profile.id == leavingId) leaving = profile;
  }
  return looksLikeInAppServer(leaving) ? target : null;
}

/// The OpenCode version [profile] runs, as the switch confirmation names it.
String phoneRuntimeName(AppLocalizations l10n, ServerProfile profile) =>
    profile.flavor == ServerFlavor.v2
    ? l10n.setupRuntimeTwo
    : l10n.setupRuntimeOne;
