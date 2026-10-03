part of '../gateway.dart';

extension _PaseoProviders on PaseoGateway {
  Future<void> _requireProviderAvailable(String id) async {
    if (!isPaseoProviderId(id)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final matches = (await _providers()).where(
      (entry) => entry['provider'] == id,
    );
    if (matches.length != 1 || !paseoProviderCanStart(matches.single)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
  }

  // ---- runtimes, models and modes ---------------------------------------

  Future<List<Map<String, dynamic>>> _providers() async {
    final cached = _providerEntries;
    if (cached != null) return cached;
    final scope = _scope;
    final epoch = _locationEpoch;
    final revision = _providerRevision;
    // Just after the daemon starts, every runtime reports `loading` while it
    // asks each CLI for its models. The app reads providers once per
    // connection, so answering with that empty moment left the composer with
    // no default model for the whole session. Wait briefly for a settled
    // snapshot; past the limit, answer with what is ready and cache nothing.
    var entries = const <Map<String, dynamic>>[];
    for (var attempt = 0; attempt < providerWarmupAttempts; attempt++) {
      if (attempt > 0) await Future<void>.delayed(providerWarmupInterval);
      final result = await transport.request('get_providers_snapshot_request', {
        'cwd': scope,
      }, timeout: const Duration(seconds: 45));
      _checkLocation(scope, epoch);
      entries = paseoList(
        result['entries'],
        max: 256,
      ).whereType<Map<String, dynamic>>().toList();
      if (entries.every((entry) => entry['status'] != 'loading')) {
        if (revision == _providerRevision) _providerEntries = entries;
        break;
      }
    }
    return entries;
  }

  List<String> _modesFor(String provider) {
    for (final entry in _providerEntries ?? const <Map<String, dynamic>>[]) {
      if (entry['provider'] != provider) continue;
      final modes = entry['modes'];
      if (modes is! List) return const [];
      return modes
          .whereType<Map>()
          .map((mode) => mode['id'])
          .whereType<String>()
          .toList();
    }
    return const [];
  }
}
