// Golden renders of slice-P2.4-5: the MCP add sheet (with and without a
// catalogue for the server), the MCP catalogue (consent, the list of
// switches on a phone and a wide window, the Node question on this phone,
// the registry unreachable), and the manual form opened from a listing and
// for a local command (environment variables as secret rows). Phone
// 412x915 and 1280x800, dark (and light for the list), with the app's real
// fonts at DPR 1. The registry is a fake with fictional listings.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_p2_4_5_golden_test.dart
// and look at every changed image before committing it.
import '../support/fake_server_capabilities.dart';
import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/setup_registry.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/mcp_catalog_screen.dart';
import 'package:opencode_mobile/ui/screens/mcp_setup_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

List<Map<String, Object?>> _listings() => [
  {
    'name': 'com.weatherco/mcp',
    'title': 'Weather',
    'version': '2.0.1',
    'description': 'Forecasts, alerts and past weather for any place.',
    'remotes': [
      {'type': 'streamable-http', 'url': 'https://mcp.weather.example/mcp'},
    ],
  },
  {
    'name': 'io.github.acme/files-mcp',
    'title': 'Files',
    'version': '1.2.0',
    'description': 'Reads, searches and edits files in a folder you choose.',
    'packages': [
      {
        'registryType': 'npm',
        'identifier': '@acme/files-mcp',
        'version': '1.2.0',
        'transport': {'type': 'stdio'},
      },
    ],
  },
  {
    'name': 'dev.tracker/issues',
    'title': 'Issue tracker',
    'version': '3.4.0',
    'description': 'Find, create and update issues and their comments.',
    'remotes': [
      {
        'type': 'streamable-http',
        'url': 'https://api.tracker.example/mcp',
        'headers': [
          {'name': 'Authorization', 'isRequired': true, 'isSecret': true},
        ],
      },
    ],
  },
  {
    'name': 'io.github.acme/notes',
    'title': 'Notes',
    'version': '0.3.0',
    'description': 'Keeps notes in plain text files and searches them.',
    'packages': [
      {
        'registryType': 'pypi',
        'identifier': 'acme-notes',
        'version': '0.3.0',
        'transport': {'type': 'stdio'},
        'environmentVariables': [
          {'name': 'NOTES_DIR', 'isRequired': true},
        ],
      },
    ],
  },
  {
    'name': 'io.github.acme/box',
    'title': 'Sandbox',
    'version': '1.0.0',
    'description': 'Runs code in a throwaway container.',
    'packages': [
      {
        'registryType': 'oci',
        'identifier': 'docker.io/acme/box',
        'version': '1.0.0',
        'transport': {'type': 'stdio'},
      },
    ],
  },
];

class _Registry implements SetupRegistryClient {
  bool fail = false;

  @override
  Future<List<RegistryEntry>> fetch({
    CancelToken? cancelToken,
    String? search,
  }) async {
    if (fail) throw const SetupRegistryException('unreachable');
    return [for (final l in _listings()) RegistryEntry.fromJson(l)!];
  }

  @override
  void dispose() {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<McpServerInfo>> listMcpServers() async => const [
    McpServerInfo(name: 'weatherco', status: 'connected'),
  ];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'https://laptop.example',
);

class _Controller extends ConnectionController {
  _Controller(super.store, this.caps);

  final ServerCapabilities caps;
  final _repo = _Repository();

  @override
  ServerCapabilities get capabilities => caps;

  @override
  ServerProfile? get profile => _laptop;

  @override
  Future<ProductRepository?> prepareActionRepository() async => _repo;
}

Future<_Controller> _server({
  ServerCapabilities caps = api2ServerCapabilities,
  bool optedIn = true,
}) async {
  SharedPreferences.setMockInitialValues({
    if (optedIn) 'oc.setupRegistry.laptop': '{"version":1,"optedIn":true}',
  });
  final preferences = await SharedPreferences.getInstance();
  return _Controller(ProfileStore(prefs: preferences), caps)
    ..directory = '/home/me/projects/mobile';
}

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required Widget home,
  bool light = false,
  Size size = _phone,
  Future<void> Function()? act,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: home,
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (act != null) {
      await act();
      await tester.pumpAndSettle();
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

/// A page with an Add button that opens the add sheet.
Widget _addHost(ServerCapabilities caps) => Builder(
  builder: (context) => KitScreen(
    topBar: const KitTopBar(title: 'MCP servers'),
    body: Center(
      child: KitButton.primary(
        label: 'Open',
        onPressed: () => showMcpAddSheet(context, capabilities: caps),
      ),
    ),
  ),
);

Widget _catalog(
  _Controller controller, {
  _Registry? registry,
  bool phone = false,
}) => McpCatalogScreen(
  controller: controller,
  client: registry ?? _Registry(),
  onThisPhone: (_) => phone,
  openPhoneTools: (_) async {},
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('add sheet', (tester) async {
    await _shot(
      tester,
      'slice_p245_add_sheet',
      home: _addHost(api2ServerCapabilities),
      act: () => tester.tap(find.text('Open')),
    );
  });

  testWidgets('add sheet, no catalogue for this server', (tester) async {
    await _shot(
      tester,
      'slice_p245_add_sheet_none',
      home: _addHost(
        const ServerCapabilities(mcpConfigWrites: false, mcpRuntimeAdds: false),
      ),
      act: () => tester.tap(find.text('Open')),
    );
  });

  testWidgets('catalogue consent', (tester) async {
    final controller = await _server(optedIn: false);
    addTearDown(controller.dispose);
    await _shot(
      tester,
      'slice_p245_catalog_consent',
      home: _catalog(controller),
    );
  });

  for (final light in [false, true]) {
    for (final size in [_phone, _wide]) {
      testWidgets('catalogue list ${size.width.toInt()} light=$light', (
        tester,
      ) async {
        final controller = await _server();
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'slice_p245_catalog_list',
          light: light,
          size: size,
          home: _catalog(controller),
        );
      });
    }
  }

  testWidgets('catalogue on this phone, Node question', (tester) async {
    final controller = await _server();
    addTearDown(controller.dispose);
    await _shot(
      tester,
      'slice_p245_catalog_node',
      home: _catalog(controller, phone: true),
      act: () => tester.tap(
        find.byKey(const ValueKey('mcp-catalog-switch-files-mcp')),
      ),
    );
  });

  testWidgets('catalogue, registry unreachable', (tester) async {
    final controller = await _server();
    addTearDown(controller.dispose);
    await _shot(
      tester,
      'slice_p245_catalog_failed',
      home: _catalog(controller, registry: _Registry()..fail = true),
    );
  });

  testWidgets('form opened from a listing', (tester) async {
    final controller = await _server();
    addTearDown(controller.dispose);
    await _shot(
      tester,
      'slice_p245_form_from_catalog',
      home: _catalog(controller),
      act: () =>
          tester.tap(find.byKey(const ValueKey('mcp-catalog-switch-issues'))),
    );
  });

  testWidgets('manual form, local command', (tester) async {
    final controller = await _server(caps: testServerCapabilities);
    addTearDown(controller.dispose);
    await _shot(
      tester,
      'slice_p245_form_local',
      home: McpSetupScreen(controller: controller),
      act: () => tester.tap(find.text('Local command')),
    );
  });
}
