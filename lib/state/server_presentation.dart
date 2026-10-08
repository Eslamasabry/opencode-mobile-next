import '../api/provider_presentation.dart';
import '../api/server_probe.dart' show ServerFlavor;
import '../domain/server_gateway.dart' show CatalogProvider;
import '../domain/session_handoff.dart' show SessionResumeCli;
import 'connection.dart';
import 'profiles.dart' show ServerProfile;

/// What screens say about the connected server that depends on its protocol
/// or its provider catalog, resolved here so lib/ui never reads lib/api
/// (ARCH-1) or compares [ServerFlavor] (ARCH-2). Copy only: features still
/// gate on `controller.capabilities`.
extension ServerPresentation on ConnectionController {
  /// The command-line product that resumes this server's sessions on the
  /// computer. It follows the server's product generation; whether a resume
  /// command is offered at all is `capabilities.cliSessionResume`.
  SessionResumeCli get sessionResumeCli => serverFlavor == ServerFlavor.v2
      ? SessionResumeCli.openCode2
      : SessionResumeCli.openCode1;

  /// [providerID]'s user-facing name from the current catalog, with the
  /// shared regional-route naming ("Z.AI · China").
  String providerName(String providerID) => presentedProviderName(
    providerID,
    catalog?.providers ?? const <CatalogProvider>[],
  );
}

/// What screens say about a saved profile that depends on its protocol, for
/// the same reason as [ServerPresentation]. Copy only.
extension ProfilePresentation on ServerProfile {
  /// Whether this profile runs OpenCode 2, for naming its runtime.
  bool get runsOpenCode2 => flavor == ServerFlavor.v2;
}
