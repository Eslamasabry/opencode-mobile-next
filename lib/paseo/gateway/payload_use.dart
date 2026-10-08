part of '../gateway.dart';

extension _PaseoPayloadUse on PaseoGateway {
  /// A trusted phone host leases its installed payloads across daemon work.
  /// Remote gateways have no callbacks. A denied acquisition never releases;
  /// a successful one always releases, including stale scope and RPC errors.
  Future<T> _withPayloadUse<T>(Future<T> Function() operation) async {
    final scope = _directory;
    final epoch = _locationEpoch;
    var acquired = false;
    _pendingPayloadWork++;
    _localWork.changed();
    try {
      try {
        final acquire = _beforePayloadUse;
        if (acquire != null) await acquire();
      } catch (_) {
        // Host exceptions may carry private native detail. Keep fixed copy.
        throw PaseoFailure(PaseoFailureKind.unavailable);
      }
      acquired = true;
      if (_beforePayloadUse != null &&
          (_closed || scope != _directory || epoch != _locationEpoch)) {
        throw PaseoFailure(PaseoFailureKind.scopeMismatch);
      }
      return await operation();
    } finally {
      _pendingPayloadWork--;
      _localWork.changed();
      if (acquired) {
        try {
          _afterPayloadUse?.call();
        } catch (_) {
          // A release callback must not replace RPC delivery truth or expose
          // private host errors. The host owns its counter's bookkeeping.
        }
      }
    }
  }
}
