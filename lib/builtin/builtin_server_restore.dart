import '../termux/bridge.dart' show TermuxRuntime;

/// Native restoration after OS process reclamation; force-stop stays stopped.
enum BuiltinServerRestorePhase {
  idle,
  waiting,
  restoring,
  unavailable;

  static BuiltinServerRestorePhase parse(Object? value) => switch (value) {
    null || 'idle' => idle,
    'waiting' => waiting,
    'restoring' => restoring,
    _ => unavailable,
  };
}

/// Fixed reasons only; native process identity and errors stay private.
enum BuiltinServerRestoreReason {
  stopped,
  policyDisabled,
  budgetExhausted,
  ownershipUnknown,
  storageUnavailable,
  componentRecoveryRequired,
  systemTimeout;

  static BuiltinServerRestoreReason? parse(Object? value) => switch (value) {
    null => null,
    'stopped' => stopped,
    'policyDisabled' => policyDisabled,
    'budgetExhausted' => budgetExhausted,
    'storageUnavailable' => storageUnavailable,
    'componentRecoveryRequired' => componentRecoveryRequired,
    'systemTimeout' => systemTimeout,
    _ => ownershipUnknown,
  };
}

/// Selects a canonical native command. Contains no script or account data.
class BuiltinServerRestoreRecipe {
  const BuiltinServerRestoreRecipe({
    required this.profileId,
    required this.runtime,
  });

  final String profileId;
  final TermuxRuntime runtime;

  Map<String, Object> toMap() => {
    'version': 1,
    'profileId': profileId,
    'runtime': runtime.name,
  };
}
