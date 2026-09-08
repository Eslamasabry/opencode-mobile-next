/// Optional account management at the connected official agent runtime.
/// Credentials remain at that runtime; these types never carry provider tokens.
abstract interface class AgentAccountGateway {
  AgentAccountSession openAccountSession();
}

enum AgentAccountFailure {
  unavailable,
  invalidResponse,
  disconnected,
  uncertain,
}

class AgentAccountException implements Exception {
  final AgentAccountFailure kind;
  const AgentAccountException(this.kind);
  @override
  String toString() => 'Agent account operation failed (${kind.name}).';
}

class AgentAccount {
  final String? type;
  final String? email;
  final String? plan;
  final bool requiresSignIn;
  const AgentAccount({
    this.type,
    this.email,
    this.plan,
    required this.requiresSignIn,
  });
  bool get signedIn => type != null;
}

class AccountRateWindow {
  final int usedPercent;
  final int? durationMinutes;
  final DateTime? resetsAt;
  const AccountRateWindow(
    this.usedPercent,
    this.durationMinutes,
    this.resetsAt,
  );
}

class AccountRateBucket {
  final String? name;
  final AccountRateWindow? primary;
  final AccountRateWindow? secondary;
  const AccountRateBucket(this.name, this.primary, this.secondary);
}

class AccountTokenUsage {
  final int? lifetimeTokens;
  final int? peakDailyTokens;
  const AccountTokenUsage({this.lifetimeTokens, this.peakDailyTokens});
}

/// One session owns this handle. Never persist or log it or its user code.
class AccountDeviceCode {
  final String verificationUrl;
  final String userCode;
  const AccountDeviceCode(this.verificationUrl, this.userCode);
  @override
  String toString() => 'AccountDeviceCode(redacted)';
}

enum AccountEventKind {
  changed,
  limitsChanged,
  loginCompleted,
  loginCancelled,
  disconnected,
}

class AccountEvent {
  final AccountEventKind kind;
  final bool success;
  const AccountEvent(this.kind, {this.success = false});
}

/// Bound to one location and transport epoch. Mutations never reconnect.
abstract class AgentAccountSession {
  bool get active;
  bool get supported;
  Stream<AccountEvent> get events;
  Future<AgentAccount> read();
  Future<List<AccountRateBucket>> rateLimits();
  Future<AccountTokenUsage> usage();
  Future<AccountDeviceCode> startDeviceLogin();

  /// Cancels only this session's outstanding attempt. False means the host
  /// could not confirm cancellation; it is never a successful sign-out.
  Future<bool> cancelLogin();
  Future<void> close();
}
