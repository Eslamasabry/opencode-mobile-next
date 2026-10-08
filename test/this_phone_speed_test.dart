// This phone and reply speed (slice-builtin-speed): a server answering with
// OpenCode's free model says so, with the way to a provider sign-in; the
// last reply's speed is one plain row; the technical side (how proot runs
// the server, the wake lock, first words split between app and server) is
// folded under Details.
import 'dart:async';

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
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart'
    show IntegrationsScreen;
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

import 'revamp/screen_phone_1_fixtures.dart';

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
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('no provider signed in: the free, slower model is named, with '
      'the way to sign in', (tester) async {
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
      configure: _connected(),
    );
    await _settle(tester);
    expect(find.text(_l10n.freeModelNotice), findsOneWidget);
    await tester.tap(find.text(_l10n.freeModelSignIn));
    await _settle(tester);
    // The server's provider sign-ins, the same page Settings opens.
    expect(find.byType(IntegrationsScreen), findsOneWidget);
    await unmountPhone(tester);
  });

  testWidgets('a provider signed in: no free-model notice', (tester) async {
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
      configure: _connected(signedIn: ['anthropic']),
    );
    await _settle(tester);
    expect(find.text(_l10n.freeModelNotice), findsNothing);
    await unmountPhone(tester);
  });

  testWidgets('not connected to it: no free-model notice', (tester) async {
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
    );
    await _settle(tester);
    expect(find.text(_l10n.freeModelNotice), findsNothing);
    await unmountPhone(tester);
  });

  testWidgets('the last reply\'s speed in plain words; the technical side '
      'under Details', (tester) async {
    var micros = 0;
    final source = _Source();
    final replies = ReplyWatch(
      setChatLease: (_, _, _) async => const BuiltinWorkLeaseStatus(),
      nowMicros: () => micros,
    )..attach(source);
    addTearDown(replies.dispose);

    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
      linux: _Linux(),
      profiles: [inAppProfile],
      overrides: [replyWatchProvider.overrideWithValue(replies)],
    );
    await _settle(tester);
    // Nothing timed yet: no row.
    expect(find.byKey(const ValueKey('this-phone-reply-speed')), findsNothing);

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
    await _settle(tester);

    final row = find.byKey(const ValueKey('this-phone-reply-speed'));
    await tester.scrollUntilVisible(
      row,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(
      find.textContaining(
        _l10n.replySpeedLast(
          KitBidi.ltr(_l10n.replySpeedSeconds('4.1')),
          KitBidi.ltr(_l10n.replySpeedSeconds('22.0')),
        ),
        findRichText: true,
      ),
      findsOneWidget,
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
    await tester.ensureVisible(find.text(_l10n.perfLinuxModeFast));
    expect(find.text(_l10n.perfDetailLinuxMode), findsOneWidget);
    expect(find.text(_l10n.perfAwakeNow), findsOneWidget);
    expect(find.text('opencode/big-pickle'), findsOneWidget);
    expect(
      find.textContaining(_l10n.replySpeedSeconds('3.8'), findRichText: true),
      findsWidgets,
    );
    await unmountPhone(tester);
  });
}
