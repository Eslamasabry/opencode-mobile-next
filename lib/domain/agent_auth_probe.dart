/// Authentication reported by an agent's own status command. Installation and
/// model availability are separate checks; neither proves an account is signed in.
enum AgentAuthProbeState { signedIn, signedOut, error }

enum AgentAuthProbeError {
  probeUnsupported,
  invalidResponse,
  timedOut,
  hostUnavailable,
  notInstalled,
  invalidContext,
  signInExpired,
  signOutFailed,
}

final class AgentAuthProbeResult {
  const AgentAuthProbeResult({
    required this.state,
    this.accountDisplayName,
    this.error,
  });

  const AgentAuthProbeResult.failed(AgentAuthProbeError reason)
    : state = AgentAuthProbeState.error,
      accountDisplayName = null,
      error = reason;

  final AgentAuthProbeState state;
  final String? accountDisplayName;
  final AgentAuthProbeError? error;

  /// Only the helper's narrow allowlist crosses the bridge. Unknown, malformed,
  /// or contradictory replies are errors rather than guessed signed-out states.
  factory AgentAuthProbeResult.fromJson(Map<String, dynamic> json) {
    final state = AgentAuthProbeState.values
        .where((state) => state.name == json['state'])
        .firstOrNull;
    if (state == null) {
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.invalidResponse,
      );
    }
    if (state == AgentAuthProbeState.error) {
      final error = AgentAuthProbeError.values
          .where((error) => error.name == json['error'])
          .firstOrNull;
      return AgentAuthProbeResult.failed(
        error ?? AgentAuthProbeError.invalidResponse,
      );
    }
    if (json['error'] != null ||
        (state == AgentAuthProbeState.signedOut &&
            json['accountDisplayName'] != null)) {
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.invalidResponse,
      );
    }
    final account = json['accountDisplayName'];
    if (account != null &&
        (account is! String ||
            account.trim().isEmpty ||
            account.length > 160 ||
            RegExp(r'[\x00-\x1f\x7f]').hasMatch(account))) {
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.invalidResponse,
      );
    }
    return AgentAuthProbeResult(
      state: state,
      accountDisplayName: account as String?,
    );
  }
}
