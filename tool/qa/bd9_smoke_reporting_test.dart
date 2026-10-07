import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../integration_test/bd9_smoke_reporting.dart';

void main() {
  test('release failure text retains only a fixed phase and category', () {
    const private = 'synthetic-provider-credential-do-not-export';
    var inspectedMetadata = false;
    final details = FlutterErrorDetails(
      exception: StateError(private),
      stack: StackTrace.fromString(private),
      library: private,
      context: ErrorDescription(private),
      informationCollector: () {
        inspectedMetadata = true;
        return [ErrorDescription(private)];
      },
      stackFilter: (_) {
        inspectedMetadata = true;
        return [private];
      },
    );
    final safe = Bd9SmokeFailureDetails.from(details, Bd9SmokePhase.bootstrap);
    expect(safe.toString(), 'bd9_phase_bootstrap:kind_state');
    expect(safe.toString(), isNot(contains(private)));
    expect(inspectedMetadata, isFalse);
    expect(safe.stack, isNull);
    expect(safe.context, isNull);
    expect(safe.informationCollector, isNull);
  });

  test('every phase has an authored release result token', () {
    for (final phase in Bd9SmokePhase.values) {
      final safe = Bd9SmokeFailureDetails.from(
        FlutterErrorDetails(exception: Object()),
        phase,
      );
      expect(safe.toString(), 'bd9_phase_${phase.name}:kind_assertion');
    }
  });

  test('native and framework failures use fixed kinds', () {
    for (final (error, kind) in [
      (MissingPluginException('synthetic-private'), 'missing_plugin'),
      (PlatformException(code: 'synthetic-private'), 'platform'),
      (FlutterError('synthetic-private'), 'framework'),
    ]) {
      final safe = Bd9SmokeFailureDetails.from(
        FlutterErrorDetails(exception: error),
        Bd9SmokePhase.screenshot,
      );
      expect(safe.toString(), 'bd9_phase_screenshot:kind_$kind');
    }
  });
}
