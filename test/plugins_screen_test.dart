import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/plugin_inventory.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show StreamStatus, CommandInfo;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/plugins_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api(this.supported) : super(baseUrl: 'https://plugins.example');
  final bool supported;
  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(pluginInventory: supported);
}

class _Repository implements ProductRepository, PluginGateway {
  int calls = 0;
  Completer<List<PluginInfo>>? gate;
  bool fail = false;
  int commandCalls = 0;
  Completer<List<CommandInfo>>? commandGate;
  List<CommandInfo> commands = const [
    CommandInfo(name: 'review', subtask: false),
  ];
  @override
  Future<List<CommandInfo>> listCommands() async {
    commandCalls++;
    return commandGate?.future ?? commands;
  }

  List<PluginInfo> plugins = [];
  @override
  Future<List<PluginInfo>> listPlugins() {
    calls++;
    if (fail) return Future.error(StateError('synthetic-secret'));
    return gate?.future ?? Future.value(plugins);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

const _plugin = PluginInfo(
  id: 'reviewer',
  status: PluginStatus.active,
  source: PluginSourceKind.package,
  packageName: '@example/reviewer',
  terminalUi: true,
);

Future<ConnectionController> _controller(
  _Repository repository, {
  bool supported = true,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'plugins', 'name': 'Test', 'baseUrl': 'https://plugins.example'},
    ]),
    'oc.activeProfile': 'plugins',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final controller = ConnectionController(store)
    ..api = _Api(supported)
    ..repository = repository
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  return controller;
}

Widget _app(ConnectionController controller, {double textScale = 1}) =>
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
      home: PluginsScreen(controller: controller),
    );
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(storage, (_) async => null),
  );
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(storage, null),
  );

  testWidgets(
    'personal command links persist and open review without running',
    (tester) async {
      final repository = _Repository()..plugins = [_plugin];
      final controller = await _controller(repository);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Link commands'));
      await tester.pumpAndSettle();
      expect(find.textContaining('personal links apply only'), findsOneWidget);
      await tester.tap(find.text('/review'));
      await tester.tap(find.text('Save links'));
      await tester.pumpAndSettle();
      expect(
        find.text('Your command links · not verified plugin ownership'),
        findsOneWidget,
      );
      expect(
        controller.store.prefs.getString('oc.pluginCommandMappings.plugins'),
        contains('review'),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      expect(find.text('Review /review'), findsOneWidget);
      await tester.tap(find.text('Review /review'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('command-arguments')), findsOneWidget);
      expect(find.byKey(const ValueKey('command-submit')), findsOneWidget);
      expect(controller.sortedSessions(), isEmpty);
    },
  );

  testWidgets('command removed after review is checked again before Run', (
    tester,
  ) async {
    final repository = _Repository()..plugins = [_plugin];
    final controller = await _controller(repository);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Link commands'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('/review'));
    await tester.tap(find.text('Save links'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Review /review'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('command-arguments')),
      'keep this draft',
    );
    repository.commands = [];
    await tester.tap(find.byKey(const ValueKey('command-submit')));
    await tester.pumpAndSettle();
    expect(find.textContaining('no longer available here'), findsOneWidget);
    expect(find.byKey(const ValueKey('command-arguments')), findsOneWidget);
    expect(controller.sortedSessions(), isEmpty);
  });

  testWidgets('clearing personal links requires confirmation', (tester) async {
    final repository = _Repository()..plugins = [_plugin];
    final controller = await _controller(repository);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Link commands'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('/review'));
    await tester.tap(find.text('Save links'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Clear personal links'));
    await tester.pumpAndSettle();
    expect(find.text('Clear all personal command links?'), findsOneWidget);
    await tester.tap(find.text('Cancel').hitTestable());
    await tester.pumpAndSettle();
    expect(find.text('Review /review'), findsOneWidget);
    await tester.tap(find.text('Clear personal links'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Clear links').hitTestable());
    await tester.pumpAndSettle();
    expect(find.text('Review /review'), findsNothing);
    expect(find.text('reviewer'), findsOneWidget);
    expect(
      controller.store.prefs.containsKey('oc.pluginCommandMappings.plugins'),
      isFalse,
    );
  });

  testWidgets('removed command never opens a stale mapped action', (
    tester,
  ) async {
    final repository = _Repository()..plugins = [_plugin];
    final controller = await _controller(repository);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Link commands'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('/review'));
    await tester.tap(find.text('Save links'));
    await tester.pumpAndSettle();
    repository.commands = [];
    await tester.tap(find.text('Review /review'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('command-submit')), findsNothing);
    expect(find.textContaining('no longer available here'), findsOneWidget);
  });

  testWidgets('unsupported servers make no plugin request', (tester) async {
    final repository = _Repository();
    await tester.pumpWidget(
      _app(await _controller(repository, supported: false)),
    );
    await tester.pumpAndSettle();
    expect(
      find.text('This server does not support plugin inspection.'),
      findsOneWidget,
    );
    expect(repository.calls, 0);
  });

  testWidgets(
    'empty, failure and retry states are distinct and hide raw errors',
    (tester) async {
      final repository = _Repository()..fail = true;
      await tester.pumpWidget(_app(await _controller(repository)));
      await tester.pumpAndSettle();
      expect(find.text('Could not load plugins. Try again.'), findsOneWidget);
      expect(find.textContaining('synthetic-secret'), findsNothing);
      expect(find.text('No plugins reported for this location.'), findsNothing);
      repository.fail = false;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(
        find.text('No plugins reported for this location.'),
        findsOneWidget,
      );
      expect(repository.calls, 2);
    },
  );

  testWidgets('late previous-location inventory is discarded', (tester) async {
    final repository = _Repository()..gate = Completer<List<PluginInfo>>();
    final controller = await _controller(repository);
    await tester.pumpWidget(_app(controller));
    await tester.pump();
    final oldResponse = repository.gate!;
    repository.gate = null;
    controller.directory = '/new-location';
    controller.locationRevision++;
    controller.notifyListeners();
    await tester.pumpAndSettle();
    oldResponse.complete([_plugin]);
    await tester.pumpAndSettle();
    expect(find.text('reviewer'), findsNothing);
    expect(find.text('No plugins reported for this location.'), findsOneWidget);
    expect(repository.calls, 2);
  });

  testWidgets('late inventory after disposal is ignored', (tester) async {
    final repository = _Repository()..gate = Completer<List<PluginInfo>>();
    final controller = await _controller(repository);
    await tester.pumpWidget(_app(controller));
    await tester.pump();
    final lateResponse = repository.gate!;
    await tester.pumpWidget(const SizedBox.shrink());
    lateResponse.complete([_plugin]);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('late review validation cannot open a prior-location dialog', (
    tester,
  ) async {
    final repository = _Repository()..plugins = [_plugin];
    final controller = await _controller(repository);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Link commands'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('/review'));
    await tester.tap(find.text('Save links'));
    await tester.pumpAndSettle();

    repository.commandGate = Completer<List<CommandInfo>>();
    await tester.tap(find.text('Review /review'));
    await tester.pump();
    controller.directory = '/new-location';
    controller.locationRevision++;
    controller.notifyListeners();
    repository.commandGate!.complete(repository.commands);
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('command-arguments')), findsNothing);
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.textContaining('no longer available here'), findsNothing);
  });

  testWidgets('plugin events and reconnect refetch current inventory', (
    tester,
  ) async {
    final repository = _Repository();
    final controller = await _controller(repository);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    repository.plugins = [_plugin];
    controller.handleEventForTesting(
      EventEnvelope(type: 'plugin.updated', properties: {}),
    );
    await tester.pumpAndSettle();
    expect(find.text('reviewer'), findsOneWidget);
    expect(find.text('Package · @example/reviewer'), findsOneWidget);
    expect(find.text('Terminal UI declared'), findsOneWidget);
    controller.status = StreamStatus.disconnected;
    controller.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.text('reviewer'), findsNothing);
    repository.plugins = [];
    controller.status = StreamStatus.connected;
    controller.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.text('No plugins reported for this location.'), findsOneWidget);
    expect(repository.calls, 3);
  });

  testWidgets('flat plugin rows fit a narrow phone with large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _Repository()
      ..plugins = [
        _plugin,
        const PluginInfo(
          id: null,
          status: PluginStatus.failed,
          source: PluginSourceKind.local,
          terminalUi: false,
        ),
      ];
    await tester.pumpWidget(_app(await _controller(repository), textScale: 2));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Plugin without an ID'), 150);
    expect(tester.takeException(), isNull);
    expect(find.byType(Card), findsNothing);
  });
}
