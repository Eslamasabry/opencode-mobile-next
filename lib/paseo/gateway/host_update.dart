part of '../gateway.dart';

// ---- OD1 agent lane: update the computer's helper (daemon.update.*) -------

extension _PaseoHostUpdate on PaseoGateway {
  HostUpdatePhase? _phaseOf(Object? name) => switch (name) {
    'starting' => HostUpdatePhase.starting,
    'downloading' => HostUpdatePhase.downloading,
    'installing' => HostUpdatePhase.installing,
    'complete' => HostUpdatePhase.complete,
    _ => null,
  };

  String? _cleanVersion(Object? value) =>
      value is String && value.length <= 64 && value.trim().isNotEmpty
      ? value.trim()
      : null;

  Future<HostUpdateResult> _updateHostDaemon(
    void Function(HostUpdatePhase phase)? onProgress,
  ) async {
    final before = transport.serverVersion;
    final scope = _scope;
    final epoch = _locationEpoch;
    final result = await transport.request(
      'daemon.update.request',
      const {},
      mutation: true,
      // Installing downloads a package on the computer.
      timeout: const Duration(minutes: 10),
      answerErrors: true,
      beforeSend: () => _checkLocation(scope, epoch),
      onProgress: (type, payload) {
        final phase = _phaseOf(payload['phase']);
        if (phase != null) onProgress?.call(phase);
      },
    );
    final success = result['success'] == true;
    final error = result['error'];
    return HostUpdateResult(
      success: success,
      previousVersion: _cleanVersion(result['previousVersion']) ?? before,
      newVersion: _cleanVersion(result['newVersion']),
      reason: success || error is! String ? null : _clipReason(error),
    );
  }

  String _clipReason(String error) {
    final clean = error
        .replaceAll(RegExp(r'[\x00-\x08\x0b-\x1f\x7f]'), ' ')
        .trim();
    return clean.length > 600 ? clean.substring(0, 600) : clean;
  }
}
