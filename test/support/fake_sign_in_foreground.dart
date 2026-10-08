import 'dart:async';

import 'package:opencode_mobile/domain/agent_sign_in_foreground.dart';

/// Explicit admission for fake-only UI fixtures. Production requires an
/// owner-bound platform service; a fake agent source does not install one.
class FakeSignInForeground implements AgentSignInForegroundPort {
  @override
  AgentSignInForegroundLease reserveAgentSignInForeground() =>
      _AdmittedForegroundLease();
}

class _AdmittedForegroundLease implements AgentSignInForegroundLease {
  _AdmittedForegroundLease() {
    // Allocate completion before cancellation, which otherwise can also use
    // Dart's shared null future rather than the widget fixture's zone.
    _closed = _lost.done;
  }

  bool _active = true;
  // One terminal listener per fake lease. An explicit asynchronous onCancel
  // creates a fixture-local future rather than Dart's shared null future.
  final _lost = StreamController<void>(
    sync: true,
    onCancel: () => Future<void>.value(),
  );
  late final Future<void> _closed;

  @override
  Future<void> get ready => Future<void>.value();

  @override
  bool get active => _active;

  @override
  Stream<void> get lost => _lost.stream;

  @override
  Future<void> release() async {
    _active = false;
    unawaited(_lost.close());
    await _closed;
  }
}
