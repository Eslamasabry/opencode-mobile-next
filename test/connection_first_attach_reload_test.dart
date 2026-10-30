// The transcript reload must follow the stream going live, so events
// between the first reload and the attach are not lost (2026-10-27).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'support/connection_v2_fixtures.dart';
import 'support/fake_event_channel.dart';

/// A stream that says it is connecting and goes live only when told, like
/// the real one.
class _SlowStream extends FakeEventChannel {
  _SlowStream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  @override
  void start() => onStatus(StreamStatus.connecting);
}

void main() {
  testWidgets('the open chat reloads again when the first stream goes live', (
    tester,
  ) async {
    final streams = <_SlowStream>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (_) => V2Api(),
      repositoryFactory: (_) => QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) {
            final stream = _SlowStream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            );
            streams.add(stream);
            return stream;
          },
    );
    addTearDown(controller.dispose);
    await controller.connect(
      ServerProfile(
        id: 'server',
        name: 'Server',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    await tester.pump();
    expect(controller.status, StreamStatus.connecting);
    final beforeLive = controller.dataRefreshRevision;

    streams.single.onStatus(StreamStatus.connected);
    await tester.pump();
    expect(controller.dataRefreshRevision, beforeLive + 1);

    // Only the first attach: a status repeat asks for nothing more.
    streams.single.onStatus(StreamStatus.connected);
    await tester.pump();
    expect(controller.dataRefreshRevision, beforeLive + 1);
    controller.dispose();
  });

  testWidgets('a stream live at once asks for one reload, not two', (
    tester,
  ) async {
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (_) => V2Api(),
      repositoryFactory: (_) => QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);
    final before = controller.dataRefreshRevision;
    await controller.connect(
      ServerProfile(
        id: 'server',
        name: 'Server',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    await tester.pump();
    expect(controller.status, StreamStatus.connected);
    expect(controller.dataRefreshRevision, before + 1);
    controller.dispose();
  });
}
