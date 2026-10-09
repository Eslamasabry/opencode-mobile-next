// This phone after slice-builtin-speed: the free-model notice, the Reply
// speed row and the Performance values under Details, at phone and wide
// sizes. Synthetic data only; no server or device.
//
//   flutter test tool/capture/builtin_speed_capture_test.dart
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/reply_watch.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

import '../../test/revamp/screen_phone_1_fixtures.dart';
import 'fixtures.dart' show capturePng, loadCaptureFonts, writePng;

const _out = 'docs/qa/slice-builtin-speed-2026-09-28';

final _l10n = lookupAppLocalizations(const Locale('en'));

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Linux extends PhoneLinux {
  _Linux() : super(running: true);

  @override
  Future<BuiltinPerformance> performance() async => const BuiltinPerformance(
    serverRunning: true,
    prootMode: BuiltinProotMode.seccomp,
    prootFilters: 1,
    serverFilters: 2,
    workHeld: true,
  );
}

class _Source extends ChangeNotifier implements ReplySource {
  final _events = StreamController<EventEnvelope>.broadcast(sync: true);

  @override
  Stream<EventEnvelope> get events => _events.stream;

  @override
  bool get onInAppServer => true;

  @override
  Set<String> get busySessions => const {};

  void emit(String type, Map<String, dynamic> properties) =>
      _events.add(EventEnvelope(type: type, properties: properties));
}

CatalogModel _model(String provider, String id, ModelCost cost) => CatalogModel(
  id: id,
  providerID: provider,
  name: id,
  enabled: true,
  status: 'active',
  contextLimit: 200000,
  outputLimit: 32000,
  reasoning: false,
  attachments: false,
  tools: true,
  variants: const [],
  cost: cost,
);

/// The in-app server, connected, with [signedIn] providers and OpenCode's
/// free model selected.
void Function(ConnectionController) _connected({
  List<String> signedIn = const [],
}) => (controller) {
  controller
    ..api = OpenCodeApi(baseUrl: BuiltinLinux.serverUrl)
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..providers = ProvidersResponse(
      providers: [
        for (final id in signedIn)
          ProviderInfo(
            id: id,
            name: id,
            modelIDs: const [],
            modelData: const {},
          ),
      ],
    )
    ..catalog = CatalogSnapshot(
      providers: const [],
      models: [
        _model(
          'opencode',
          'big-pickle',
          const ModelCost(inputPerMillion: 0, outputPerMillion: 0),
        ),
      ],
      agents: const [],
    )
    ..selectedModel = ModelRef(providerID: 'opencode', modelID: 'big-pickle');
};

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  setUpAll(loadCaptureFonts);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  for (final (size, label) in [
    (phoneSize, '412x915'),
    (wideSize, '1280x800'),
  ]) {
    testWidgets('capture $label', (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      var micros = 0;
      final source = _Source();
      final replies = ReplyWatch(
        setChatLease: (_, _, _) async => const BuiltinWorkLeaseStatus(),
        nowMicros: () => micros,
      )..attach(source);
      addTearDown(replies.dispose);
      source.emit('message.updated', {
        'info': {
          'id': 'u1',
          'sessionID': 's1',
          'role': 'user',
          'time': {'created': 10000},
        },
      });
      source.emit('message.updated', {
        'info': {
          'id': 'a1',
          'sessionID': 's1',
          'role': 'assistant',
          'providerID': 'opencode',
          'modelID': 'big-pickle',
        },
      });
      micros = 4100000;
      source.emit('message.part.updated', {
        'part': {
          'sessionID': 's1',
          'messageID': 'a1',
          'type': 'text',
          'time': {'start': 13800},
        },
      });
      micros = 22000000;
      source.emit('session.status', {
        'sessionID': 's1',
        'status': {'type': 'idle'},
      });
      final boundary = GlobalKey();
      try {
        await pumpPhone(
          tester,
          home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
          linux: _Linux(),
          profiles: [inAppProfile],
          size: size,
          boundary: boundary,
          configure: _connected(),
          overrides: [replyWatchProvider.overrideWithValue(replies)],
        );
        await _settle(tester);
        await writePng(
          '$_out/after_this_phone_free_model_$label.png',
          await capturePng(tester, boundary, pixelRatio: 1),
        );
        final toggle = find
            .descendant(
              of: find.byKey(const ValueKey('this-phone-details')),
              matching: find.text(_l10n.kitDetails),
            )
            .first;
        await tester.ensureVisible(toggle);
        await _settle(tester);
        await tester.tap(toggle);
        await _settle(tester);
        await tester.ensureVisible(
          find.byKey(const ValueKey('this-phone-reply-speed')),
        );
        await _settle(tester);
        await writePng(
          '$_out/after_this_phone_reply_speed_$label.png',
          await capturePng(tester, boundary, pixelRatio: 1),
        );
        await tester.ensureVisible(find.text(_l10n.perfDetailModel));
        await _settle(tester);
        await writePng(
          '$_out/after_this_phone_performance_details_$label.png',
          await capturePng(tester, boundary, pixelRatio: 1),
        );
        expect(tester.takeException(), isNull);
      } finally {
        debugDefaultTargetPlatformOverride = null;
        await unmountPhone(tester);
      }
    });
  }
}
