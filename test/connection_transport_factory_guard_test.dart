import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Every transport must be built through `_buildTransportPair`, which picks
/// the v1 or v2 stack from the profile's detected flavor. Calling the v1
/// factory directly anywhere else silently downgrades an OpenCode 2
/// connection to a v1 client whose health check can never succeed, which
/// surfaces to the user as a rotated password.
///
/// This is a source guard rather than a behavioral test on purpose. The
/// behavioral version drove the whole asynchronous rescope against a fake
/// gateway and was unreliable, so it was deleted — and the fix it protected
/// was reverted by a merge within a day. The failure mode here is
/// reintroduction, not misbehavior at runtime, so the source is what to
/// assert on.
void main() {
  test('only _buildTransportPair constructs the v1 transport', () {
    final source = File('lib/state/connection.dart').readAsStringSync();
    final lines = source.split('\n');

    final callSites = <int>[];
    for (var i = 0; i < lines.length; i++) {
      if (lines[i].contains('_apiFactory(profile)')) callSites.add(i + 1);
    }

    expect(
      callSites,
      isNotEmpty,
      reason: 'the v1 factory should still exist; check for a rename',
    );

    // The one legitimate call is inside _buildTransportPair itself.
    final builderStart = lines.indexWhere(
      (line) => line.contains('_buildTransportPair(ServerProfile profile)'),
    );
    expect(builderStart, isNot(-1), reason: '_buildTransportPair is missing');

    var builderEnd = builderStart;
    while (builderEnd < lines.length && lines[builderEnd] != '  }') {
      builderEnd++;
    }

    final strays = callSites
        .where((line) => line < builderStart + 1 || line > builderEnd + 1)
        .toList();

    expect(
      strays,
      isEmpty,
      reason:
          'lib/state/connection.dart calls _apiFactory(profile) outside '
          '_buildTransportPair at line(s) ${strays.join(', ')}. A v2 profile '
          'rebuilt on the v1 transport cannot authenticate, and the failure '
          'looks like a wrong password. Route it through _buildTransportPair.',
    );
  });

  test('the location rescope path uses the flavor-aware builder', () {
    final source = File('lib/state/connection.dart').readAsStringSync();
    final start = source.indexOf('Future<void> _selectLocation(');
    expect(start, isNot(-1), reason: '_selectLocation is missing');

    // The rescope rebuilds the transport after the early-return branch; look
    // at the body that follows, not the whole file.
    final body = source.substring(start, start + 2500);
    expect(
      body.contains('_buildTransportPair(profile)'),
      isTrue,
      reason:
          '_selectLocation must rebuild through _buildTransportPair so a v2 '
          'profile keeps its v2 transport when the directory changes.',
    );
  });
}
