// Adding a provider OpenCode's catalog does not list: the declaration shape,
// the write protocol, the controller path, and the form screen.
//
// The capability is false everywhere except the phone's own engine, because
// OpenCode 2 has no endpoint that adds a provider: the declaration is a write
// into the server's `opencode.json`, a file only the in-app engine lets the
// app own (docs/opencode2-protocol-notes.md).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/custom_provider_script.dart';
import 'package:opencode_mobile/builtin/custom_provider_write.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/custom_provider_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RecordingWriter implements CustomProviderWriter {
  final calls = <({String id, Map<String, Object?> block})>[];
  CustomProviderWriteOutcome outcome = CustomProviderWriteOutcome.saved;

  @override
  Future<CustomProviderWriteOutcome> write({
    required String providerID,
    required Map<String, Object?> block,
  }) async {
    calls.add((id: providerID, block: block));
    return outcome;
  }
}

class _ProviderController extends ConnectionController {
  _ProviderController(super.store, {super.customProviderWriter});

  ServerCapabilities serverCapabilities = const ServerCapabilities(
    customProviders: true,
  );

  @override
  ServerCapabilities get capabilities => serverCapabilities;
}

const _draft = CustomProviderDraft(
  id: 'my-endpoint',
  name: 'My endpoint',
  baseURL: 'https://api.example.com/v1',
  models: ['llama-3.1-70b'],
);

Future<ConnectionController> _controller(
  CustomProviderWriter? writer, {
  bool supported = true,
}) async {
  // ProfileStore.load/upsert answer the Keystore channel; unmocked it hangs.
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(
        secure,
        (call) async => call.method == 'readAll' ? <String, String>{} : null,
      );
  addTearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null),
  );
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  await store.upsert(
    ServerProfile(
      id: 'phone',
      name: 'This phone',
      baseUrl: 'http://127.0.0.1:4097',
    ),
  );
  await store.setActiveId('phone');
  return _ProviderController(store, customProviderWriter: writer)
    ..serverCapabilities = ServerCapabilities(customProviders: supported);
}

Future<void> _open(WidgetTester tester, ConnectionController controller) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: FilledButton(
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => CustomProviderScreen(controller: controller),
                ),
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Open'));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('the declaration', () {
    test('writes name, package, address and models, and nothing secret', () {
      final block = _draft.toConfigJson();
      expect(block['name'], 'My endpoint');
      expect(block['package'], customProviderPackage);
      expect(block['settings'], {'baseURL': 'https://api.example.com/v1'});
      expect(block['models'], hasLength(1));
      expect((block['models'] as Map).keys, ['llama-3.1-70b']);
      expect(block.keys, isNot(contains(anyOf('apiKey', 'key', 'options'))));
    });

    test('trims the id and the name into the written block', () {
      const draft = CustomProviderDraft(
        id: '  My-Endpoint  ',
        name: '  My endpoint ',
        baseURL: ' https://api.example.com/v1 ',
        models: [' m '],
      );
      expect(draft.normalizedID, 'my-endpoint');
      expect(draft.toConfigJson()['name'], 'My endpoint');
    });

    test('drops a model listed twice', () {
      const draft = CustomProviderDraft(
        id: 'p',
        name: 'P',
        baseURL: 'https://api.example.com/v1',
        models: ['m', 'm'],
      );
      expect((draft.toConfigJson()['models'] as Map).keys, ['m']);
    });

    test('refuses what the config file cannot hold', () {
      void refuses(CustomProviderDraft draft, Matcher message) => expect(
        draft.toConfigJson,
        throwsA(
          isA<ProductException>().having((e) => e.message, 'message', message),
        ),
      );
      refuses(
        const CustomProviderDraft(
          id: 'My Endpoint',
          name: 'P',
          baseURL: 'https://x.example',
          models: ['m'],
        ),
        contains('provider ID'),
      );
      refuses(
        const CustomProviderDraft(
          id: 'p',
          name: '  ',
          baseURL: 'https://x.example',
          models: ['m'],
        ),
        contains('provider name'),
      );
      refuses(
        const CustomProviderDraft(
          id: 'p',
          name: 'P',
          baseURL: 'api.example.com',
          models: ['m'],
        ),
        contains('HTTP or HTTPS'),
      );
      refuses(
        const CustomProviderDraft(
          id: 'p',
          name: 'P',
          baseURL: 'https://key:secret@x.example',
          models: ['m'],
        ),
        contains('without credentials'),
      );
      refuses(
        const CustomProviderDraft(
          id: 'p',
          name: 'P',
          baseURL: 'https://x.example',
        ),
        contains('at least one model'),
      );
      refuses(
        const CustomProviderDraft(
          id: 'p',
          name: 'P',
          baseURL: 'https://x.example',
          models: ['m', '  '],
        ),
        contains('empty one'),
      );
    });
  });

  group('the write', () {
    test('the exit status is the whole result', () {
      expect(customProviderWriteOutcome(0), CustomProviderWriteOutcome.saved);
      expect(
        customProviderWriteOutcome(20),
        CustomProviderWriteOutcome.notSetUp,
      );
      expect(
        customProviderWriteOutcome(21),
        CustomProviderWriteOutcome.unusableConfig,
      );
      expect(
        customProviderWriteOutcome(22),
        CustomProviderWriteOutcome.unsafePath,
      );
      expect(customProviderWriteOutcome(23), CustomProviderWriteOutcome.busy);
      expect(
        customProviderWriteOutcome(24),
        CustomProviderWriteOutcome.alreadyExists,
      );
      expect(
        customProviderWriteOutcome(99),
        CustomProviderWriteOutcome.unusableConfig,
      );
    });

    test(
      'a saved write is never a failure; every other one explains itself',
      () {
        expect(
          () => customProviderWriteFailure(CustomProviderWriteOutcome.saved),
          throwsArgumentError,
        );
        for (final outcome in CustomProviderWriteOutcome.values) {
          if (outcome == CustomProviderWriteOutcome.saved) continue;
          final failure = customProviderWriteFailure(outcome);
          expect(failure.message, isNotEmpty);
          expect(failure.message, isNot(contains('null')));
        }
      },
    );

    test('the script carries the block as data, never as code', () {
      final script = customProviderWriteScript(
        providerID: 'my-endpoint',
        block: _draft.toConfigJson(),
      );
      expect(script, contains(openCode2ConfigPath));
      expect(script, contains('/usr/bin/python3'));
      // The person's words never reach the shell or the Python source.
      expect(script, isNot(contains('My endpoint')));
      expect(script, isNot(contains('api.example.com')));
      expect(script, contains("python3 - '$openCode2ConfigPath' "));
    });

    test('the script re-checks the identity it was handed', () {
      expect(
        () => customProviderWriteScript(
          providerID: 'Bad Id',
          block: _draft.toConfigJson(),
        ),
        throwsArgumentError,
      );
    });
  });

  group('the controller', () {
    test('writes the validated block and reports the restart', () async {
      final writer = _RecordingWriter();
      final controller = await _controller(writer);
      addTearDown(controller.dispose);
      final result = await controller.addCustomProvider(_draft);
      expect(result.restartRequired, isTrue);
      expect(writer.calls, hasLength(1));
      expect(writer.calls.single.id, 'my-endpoint');
      expect(writer.calls.single.block['package'], customProviderPackage);
    });

    test('a rejected field never reaches the file', () async {
      final writer = _RecordingWriter();
      final controller = await _controller(writer);
      addTearDown(controller.dispose);
      await expectLater(
        controller.addCustomProvider(
          const CustomProviderDraft(
            id: 'p',
            name: 'P',
            baseURL: 'https://x.example',
          ),
        ),
        throwsA(isA<ProductException>()),
      );
      expect(writer.calls, isEmpty);
    });

    test('a refused write says why and writes nothing else', () async {
      final writer = _RecordingWriter()
        ..outcome = CustomProviderWriteOutcome.alreadyExists;
      final controller = await _controller(writer);
      addTearDown(controller.dispose);
      await expectLater(
        controller.addCustomProvider(_draft),
        throwsA(
          isA<ProductException>().having(
            (e) => e.message,
            'message',
            contains('already declared'),
          ),
        ),
      );
    });

    test('a server that keeps its own config cannot take one', () async {
      final writer = _RecordingWriter();
      final controller = await _controller(writer, supported: false);
      addTearDown(controller.dispose);
      await expectLater(
        controller.addCustomProvider(_draft),
        throwsA(isA<ProductException>()),
      );
      expect(writer.calls, isEmpty);
    });
  });

  group('the screen', () {
    testWidgets('explains why a server that keeps its own config cannot add '
        'one, instead of a form that could not save', (tester) async {
      final controller = await _controller(
        _RecordingWriter(),
        supported: false,
      );
      addTearDown(controller.dispose);
      await _open(tester, controller);
      expect(
        find.byKey(const ValueKey('provider-setup-unavailable')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('provider-setup-form')), findsNothing);
      expect(find.byKey(const ValueKey('provider-setup-save')), findsNothing);
    });

    testWidgets('an untouched form is not red; Save marks what is missing', (
      tester,
    ) async {
      final controller = await _controller(_RecordingWriter());
      addTearDown(controller.dispose);
      await _open(tester, controller);
      expect(find.byKey(const ValueKey('provider-setup-form')), findsOneWidget);
      expect(find.text('Enter a provider ID'), findsNothing);

      await tester.tap(find.byKey(const ValueKey('provider-setup-save')));
      await tester.pumpAndSettle();
      expect(find.text('Enter a provider ID'), findsOneWidget);
      expect(find.text('Enter a name'), findsOneWidget);
    });

    testWidgets('rejects an address with credentials in it', (tester) async {
      final controller = await _controller(_RecordingWriter());
      addTearDown(controller.dispose);
      await _open(tester, controller);
      await tester.enterText(
        find.byKey(const ValueKey('provider-url')),
        'https://key:secret@api.example.com/v1',
      );
      await tester.tap(find.byKey(const ValueKey('provider-setup-save')));
      await tester.pumpAndSettle();
      expect(find.textContaining('without credentials'), findsOneWidget);
    });

    testWidgets('saves, then says the server must restart to load it', (
      tester,
    ) async {
      final writer = _RecordingWriter();
      final controller = await _controller(writer);
      addTearDown(controller.dispose);
      await _open(tester, controller);
      await tester.enterText(
        find.byKey(const ValueKey('provider-id')),
        'my-endpoint',
      );
      await tester.enterText(
        find.byKey(const ValueKey('provider-name')),
        'My endpoint',
      );
      await tester.enterText(
        find.byKey(const ValueKey('provider-url')),
        'https://api.example.com/v1',
      );
      await tester.enterText(
        find.byKey(const ValueKey('provider-models')),
        'llama-3.1-70b',
      );
      await tester.tap(find.byKey(const ValueKey('provider-setup-save')));
      await tester.pumpAndSettle();

      expect(writer.calls, hasLength(1));
      expect(
        find.byKey(const ValueKey('provider-setup-saved')),
        findsOneWidget,
      );
      expect(find.textContaining('Saved My endpoint'), findsOneWidget);
      // No server owner in a bare test: the page says what is left to do
      // rather than claiming the provider is loaded.
      expect(find.textContaining('Restart OpenCode'), findsOneWidget);
      expect(find.byKey(const ValueKey('provider-setup-close')), findsWidgets);
    });

    testWidgets('leaving with unsaved input asks first', (tester) async {
      final controller = await _controller(_RecordingWriter());
      addTearDown(controller.dispose);
      await _open(tester, controller);
      await tester.enterText(
        find.byKey(const ValueKey('provider-name')),
        'Half typed',
      );
      // The guard is a PopScope, whose canPop is decided at build time: the
      // frame after the edit is what arms it.
      await tester.pump();
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('Discard this provider?'), findsOneWidget);
      expect(find.byKey(const ValueKey('provider-setup-form')), findsOneWidget);
    });
  });
}
