import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/demo/demo_copy.dart';
import 'package:opencode_mobile/demo/demo_gateway.dart';
import 'package:opencode_mobile/demo/demo_store.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/demo_screen.dart';
import 'package:opencode_mobile/ui/widgets/diff_view.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _channels = [
  'oc/background',
  'oc/termux',
  'oc/voice',
  'oc/read-aloud',
  'oc/camera',
  'oc/share',
  'plugins.it_nomads.com/flutter_secure_storage',
  'dev.shorebird/code_push',
  'flutter/platform',
];

Future<void> _pump(WidgetTester tester) async {
  // Production chat deliberately keeps a running-turn indicator animated.
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

ConnectionController _demoController(WidgetTester tester) =>
    ProviderScope.containerOf(
      tester.element(find.byType(ChatScreen)),
    ).read(connProvider);

Future<void> _isolatedJourney(
  WidgetTester tester,
  Future<void> Function() body, {
  bool reducedMotion = true,
  double textScale = 1,
  double keyboard = 0,
}) async {
  final saved = <String, Object>{
    'oc.profiles':
        '[{"id":"real","name":"Existing server","baseUrl":"https://real.invalid"}]',
    'oc.activeProfile': 'real',
    'oc.sessionDrafts': '{"real/draft":"Existing private draft"}',
    'oc.offlineQueue': '[{"text":"Existing queued prompt"}]',
    'oc.location.real': '{"directory":"/real-project"}',
    'oc.keepLiveInBackground': false,
  };
  SharedPreferences.setMockInitialValues(saved);
  final preferences = await SharedPreferences.getInstance();
  final nativeCalls = <String>[];
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  for (final channel in _channels) {
    messenger.setMockMethodCallHandler(
      channel == 'flutter/platform'
          ? SystemChannels.platform
          : MethodChannel(channel),
      (call) async {
        // EditableText checks availability while building. That is not a
        // clipboard read/write; retain the platform channel's JSON codec.
        if (channel != 'flutter/platform' ||
            call.method == 'Clipboard.getData' ||
            call.method == 'Clipboard.setData') {
          nativeCalls.add('$channel/${call.method}');
        }
        if (call.method == 'Clipboard.hasStrings') return {'value': false};
        if (call.method == 'LiveText.isLiveTextInputAvailable') return false;
        return null;
      },
    );
  }
  final realConnection = ConnectionController(ProfileStore(prefs: preferences))
    ..directory = '/real-project'
    ..lastError = 'Existing connection state';
  addTearDown(() {
    realConnection.dispose();
    for (final channel in _channels) {
      messenger.setMockMethodCallHandler(MethodChannel(channel), null);
    }
  });
  var networkClients = 0;
  await HttpOverrides.runZoned(
    () async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(realConnection)],
          child: MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                disableAnimations: reducedMotion,
                textScaler: TextScaler.linear(textScale),
                viewInsets: EdgeInsets.only(bottom: keyboard),
              ),
              child: child!,
            ),
            home: Builder(
              builder: (context) => Scaffold(
                body: TextButton(
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(builder: (_) => const DemoScreen()),
                  ),
                  child: const Text('Open demo'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open demo'));
      await _pump(tester);
      expect(find.byType(ChatScreen), findsOneWidget);
      expect(_demoController(tester), isNot(same(realConnection)));
      expect(_demoController(tester).isIsolated, isTrue);
      if (keyboard == 0) expect(find.text(DemoCopy.disclosure), findsOneWidget);
      await body();
      await tester.pumpWidget(const SizedBox.shrink());
      await _pump(tester);
      expect(networkClients, 0);
      expect(nativeCalls, isEmpty);
      await preferences.reload();
      expect({
        for (final key in preferences.getKeys()) key: preferences.get(key),
      }, saved);
      expect(realConnection.directory, '/real-project');
      expect(realConnection.lastError, 'Existing connection state');
      expect(realConnection.api, isNull);
    },
    createHttpClient: (_) {
      networkClients++;
      throw StateError('The offline demo must not create a network client.');
    },
  );
}

Future<void> _review(WidgetTester tester) async {
  if (find.byKey(const Key('permission-sheet')).evaluate().isEmpty) {
    await tester.tap(find.byKey(const Key('permission-card-review')));
    await _pump(tester);
  }
  expect(find.byKey(const Key('permission-sheet')), findsOneWidget);
  expect(find.textContaining('welcome.txt'), findsWidgets);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'compact demo keeps send and exit reachable with large text and keyboard',
    (tester) async {
      tester.view.physicalSize = const Size(360, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await _isolatedJourney(
        tester,
        () async {
          expect(
            tester.takeException(),
            isNull,
            reason: 'Initial compact layout',
          );
          expect(find.text('Try a small change'), findsOneWidget);
          expect(find.byType(AppBar), findsNothing);
          final send = find.byKey(const Key('chat-send-button'));
          expect(tester.getRect(send).bottom, lessThanOrEqualTo(420));
          await tester.tap(send);
          await _pump(tester);
          expect(
            tester.takeException(),
            isNull,
            reason: 'Permission compact layout',
          );
          expect(
            _demoController(
              tester,
            ).permissionsForSession(DemoGateway.sessionID),
            hasLength(1),
          );
          expect(find.text('Set up your own server'), findsNothing);
          expect(tester.getRect(send).bottom, lessThanOrEqualTo(420));
          final review = find.byKey(const Key('permission-card-review'));
          await Scrollable.ensureVisible(tester.element(review), alignment: .5);
          await tester.tap(review);
          await _pump(tester);
          final allow = find.byKey(const Key('permission-allow-once'));
          await Scrollable.ensureVisible(tester.element(allow), alignment: .5);
          await _pump(tester);
          expect(allow.hitTestable(), findsOneWidget);
          await tester.tap(allow);
          await _pump(tester);
          expect(
            _demoController(
              tester,
            ).permissionsForSession(DemoGateway.sessionID),
            isEmpty,
          );
          expect(
            (_demoController(tester).api! as DemoGateway).hasFinished,
            isTrue,
          );
          expect(
            tester.takeException(),
            isNull,
            reason: 'Completed compact layout',
          );
          expect(find.text('Set up your own server'), findsNothing);
          expect(tester.getRect(send).bottom, lessThanOrEqualTo(420));
          await tester.tap(find.byTooltip(DemoCopy.exit));
          await _pump(tester);
          expect(find.text('Open demo'), findsOneWidget);
          expect(tester.takeException(), isNull);
        },
        textScale: 2.5,
        keyboard: 320,
      );
    },
  );

  for (final allow in [true, false]) {
    testWidgets(
      'production demo ${allow ? 'allows' : 'rejects'} its simulated edit without touching real state',
      (tester) async {
        await _isolatedJourney(tester, () async {
          final controller = _demoController(tester);
          final gateway = controller.api! as DemoGateway;
          expect(find.byTooltip('Review changes'), findsNothing);
          await tester.tap(find.byKey(const Key('chat-send-button')));
          await _pump(tester);
          expect(
            controller.permissionsForSession(DemoGateway.sessionID),
            hasLength(1),
          );
          expect(gateway.hasPendingTimer, isFalse);
          await tester.tap(find.byTooltip('Review changes'));
          await _pump(tester);
          expect(find.byType(DiffView), findsOneWidget);
          expect(
            tester.widget<DiffView>(find.byType(DiffView)).allowCopy,
            isFalse,
          );
          expect(
            find.textContaining('Welcome aboard!', findRichText: true),
            findsWidgets,
          );
          await tester.tap(find.byType(CloseButton));
          await _pump(tester);
          await _review(tester);
          await tester.tap(find.byKey(const Key('permission-see-full-diff')));
          await _pump(tester);
          expect(find.byType(DiffView), findsOneWidget);
          expect(
            tester.widget<DiffView>(find.byType(DiffView)).allowCopy,
            isFalse,
          );
          await tester.tap(find.byType(CloseButton));
          await _pump(tester);
          await tester.tap(
            find.byKey(
              Key(allow ? 'permission-allow-once' : 'permission-reject'),
            ),
          );
          await _pump(tester);
          expect(
            controller.permissionsForSession(DemoGateway.sessionID),
            isEmpty,
          );
          expect(controller.busySessions, isEmpty);
          final history = await gateway.messages(DemoGateway.sessionID);
          expect(
            history.last.parts.single.text,
            allow ? DemoCopy.allowed : DemoCopy.denied,
          );
          expect(
            find.textContaining(
              allow
                  ? 'You allowed the sample change.'
                  : 'You denied the sample change.',
              findRichText: true,
            ),
            findsOneWidget,
          );
          expect(find.text('Set up your own server'), findsOneWidget);
          await tester.tap(
            allow
                ? find.text('Set up your own server')
                : find.byTooltip(DemoCopy.exit),
          );
          await _pump(tester);
          expect(find.text('Open demo'), findsOneWidget);
          expect(gateway.isClosed, isTrue);
        });
      },
    );
  }

  testWidgets(
    'reset and exit dispose streaming gateways and keep a fresh prompt',
    (tester) async {
      await _isolatedJourney(tester, () async {
        final first = _demoController(tester).api! as DemoGateway;
        await tester.tap(find.byKey(const Key('chat-send-button')));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 200));
        expect(first.hasPendingTimer, isTrue);
        await tester.tap(find.byTooltip(DemoCopy.reset));
        await _pump(tester);
        expect(first.isClosed, isTrue);
        expect(first.hasPendingTimer, isFalse);
        final second = _demoController(tester).api! as DemoGateway;
        expect(second, isNot(same(first)));
        expect(await second.messages(DemoGateway.sessionID), isEmpty);
        expect(
          tester
              .widget<TextField>(find.byKey(const Key('chat-composer-field')))
              .controller!
              .text,
          DemoCopy.prompt,
        );
        await tester.tap(find.byKey(const Key('chat-send-button')));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 200));
        expect(second.hasPendingTimer, isTrue);
        await tester.tap(find.byTooltip(DemoCopy.exit));
        await _pump(tester);
        expect(second.isClosed, isTrue);
        expect(second.hasPendingTimer, isFalse);
        await tester.pump(const Duration(seconds: 5));
        expect(find.text('Open demo'), findsOneWidget);
        expect(tester.takeException(), isNull);
      }, reducedMotion: false);
    },
  );

  testWidgets(
    'demo treats mobile commands as text and disables external tool surfaces',
    (tester) async {
      await _isolatedJourney(tester, () async {
        expect(find.byKey(const Key('composer-tools-button')), findsNothing);
        expect(find.byTooltip('Session menu'), findsNothing);
        const input = '/new ![remote](https://example.invalid/private.png)';
        await tester.enterText(
          find.byKey(const Key('chat-composer-field')),
          input,
        );
        await tester.tap(find.byKey(const Key('chat-send-button')));
        await _pump(tester);
        final gateway = _demoController(tester).api! as DemoGateway;
        final history = await gateway.messages(DemoGateway.sessionID);
        expect(history.first.parts.single.text, input);
        expect(find.byType(ChatScreen), findsOneWidget);
        expect(find.byType(Image), findsNothing);
      });
    },
  );

  test(
    'demo preferences are independent maps with defensive list copies',
    () async {
      final first = DemoProfileStore();
      final second = DemoProfileStore();
      final values = ['saved'];
      await first.prefs.setStringList('example', values);
      values.add('external edit');
      first.prefs.getStringList('example')!.add('read edit');
      expect(first.prefs.getStringList('example'), ['saved']);
      expect(second.prefs.getKeys(), isEmpty);
      expect(first.prefs.containsKey('oc.profiles'), isFalse);
      expect(first.prefs.containsKey('oc.activeProfile'), isFalse);
      expect((await first.load()).single.id, 'offline-demo');
      await first.prefs.clear();
      expect(first.prefs.getKeys(), isEmpty);
    },
  );
}
