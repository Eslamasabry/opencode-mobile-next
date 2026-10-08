import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

enum Bd9SmokePhase {
  initializing,
  fixture,
  preferences,
  bootstrap,
  conversation,
  screenshot,
  cleanup,
  complete,
}

/// The SDK's reporter calls details.toString(), which can omit diagnostics in
/// release mode. Supply a useful fixed category without inspecting raw text.
class Bd9SmokeFailureDetails extends FlutterErrorDetails {
  Bd9SmokeFailureDetails.from(FlutterErrorDetails details, this.phase)
    : kind = _kind(details.exception),
      super(exception: StateError('bd9_smoke_failed'));

  final Bd9SmokePhase phase;
  final String kind;

  static String _kind(Object exception) {
    if (exception is MissingPluginException) return 'missing_plugin';
    if (exception is PlatformException) return 'platform';
    if (exception is FlutterError) return 'framework';
    if (exception is StateError) return 'state';
    if (exception is TypeError) return 'type';
    return 'assertion';
  }

  @override
  String toString({DiagnosticLevel minLevel = DiagnosticLevel.info}) =>
      'bd9_phase_${phase.name}:kind_$kind';
}
