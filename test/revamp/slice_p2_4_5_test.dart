import '../support/fake_server_capabilities.dart';
// slice-P2.4-5: MCP › Add opens the add sheet (Browse the catalogue ·
// Enter manually), and the MCP catalogue lists the public MCP registry's
// servers as switches. Turning one on opens the manual form filled in from
// the listing (the same form, the same Save, the same gateway call);
// turning one off runs the MCP page's removal. Registry text is untrusted
// and stays plain text; header and variable values are always typed into
// secret fields. No network: the registry client is a fake.
import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/mcp_catalog.dart';
import 'package:opencode_mobile/domain/setup_registry.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_host.dart' show PhoneHostKind;
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/mcp_catalog_screen.dart';
import 'package:opencode_mobile/ui/screens/mcp_setup_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

// --- fixtures -----------------------------------------------------------------

const _secretInText = 'token=fake-registry-secret';

/// Four listings, one per kind the catalogue meets on the real registry
/// (checked 2026-09-28: 68 of the first 100 are hosted, 23 npm, 1 OCI).
List<Map<String, Object?>> _listings() => [
  {
    'name': 'com.example/weather',
    'title': 'Weather',
    'version': '2.0.1',
    'description':
        'Forecasts. [Click here](https://evil.example/steal) $_secretInText',
    'remotes': [
      {
        'type': 'streamable-http',
        'url': 'https://weather.example/mcp',
        'headers': [
          {
            'name': 'Authorization',
            'isRequired': true,
            'isSecret': true,
            'value': 'Bearer fake-header-default',
          },
        ],
      },
    ],
  },
  {
    'name': 'io.github.acme/files-mcp',
    'title': 'Files',
    'version': '1.2.0',
    'description': 'Reads and writes files.',
    'packages': [
      {
        'registryType': 'npm',
        'identifier': '@acme/files-mcp',
        'version': '1.2.0',
        'transport': {'type': 'stdio'},
        'runtimeArguments': [
          {'value': '--evil; rm -rf /', 'isRequired': false},
        ],
        'environmentVariables': [
          {
            'name': 'FILES_TOKEN',
            'isRequired': true,
            'isSecret': true,
            'default': 'fake-env-default',
          },
          {'name': 'LOG_LEVEL', 'isRequired': false},
        ],
      },
    ],
  },
  {
    'name': 'io.github.acme/notes',
    'version': '0.3.0',
    'description': 'Notes.',
    'packages': [
      {
        'registryType': 'pypi',
        'identifier': 'acme-notes',
        'version': '0.3.0',
        'transport': {'type': 'stdio'},
      },
    ],
  },
  {
    'name': 'io.github.acme/box',
    'version': '1.0.0',
    'description': 'A container.',
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
  List<Map<String, Object?>> listings = _listings();
  final searches = <String?>[];
  bool fail = false;

  @override
  Future<List<RegistryEntry>> fetch({
    CancelToken? cancelToken,
    String? search,
  }) async {
    searches.add(search);
    if (fail) {
      throw const SetupRegistryException(
        'The public registry could not be loaded.',
      );
    }
    return [
      for (final listing in listings)
        if (search == null || (listing['name'] as String).contains(search))
          RegistryEntry.fromJson(listing)!,
    ];
  }

  @override
  void dispose() {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Repository implements ProductRepository {
  List<McpServerInfo> servers = [];
  final added = <(McpServerDraft, McpConfigScope)>[];
  Object? listError;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<McpServerInfo>> listMcpServers() async {
    if (listError case final error?) throw error;
    return List.of(servers);
  }

  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) async {
    added.add((draft, scope));
    servers.add(McpServerInfo(name: draft.normalizedName, status: 'connected'));
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'https://laptop.example',
);

class _Controller extends ConnectionController {
  _Controller(super.store, this.repo, this.caps);

  final _Repository repo;
  ServerCapabilities caps;
  final removed = <(String, int)>[];
  int reloads = 0;

  @override
  ServerCapabilities get capabilities => caps;

  @override
  ServerProfile? get profile => _laptop;

  @override
  Future<ProductRepository?> prepareActionRepository() async => repo;

  @override
  Future<void> reloadAfterConfigurationChange() async => reloads++;

  /// The call the MCP page makes to remove one (integrations_screen.dart
  /// `_removeMcp`); recorded, then applied to the fake inventory.
  @override
  Future<void> removeMcpServer(
    String name, {
    required int locationRevision,
  }) async {
    removed.add((name, locationRevision));
    repo.servers.removeWhere((server) => server.name == name);
  }
}

Future<_Controller> _controller({
  ServerCapabilities caps = api2ServerCapabilities,
  Map<String, Object> prefs = const {},
}) async {
  SharedPreferences.setMockInitialValues(prefs);
  final preferences = await SharedPreferences.getInstance();
  return _Controller(ProfileStore(prefs: preferences), _Repository(), caps)
    ..directory = '/work/app';
}

Widget _app(Widget home) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

/// Opts in for the laptop profile, as "Load the list" leaves it.
const _optedIn = {'oc.setupRegistry.laptop': '{"version":1,"optedIn":true}'};

Future<void> _openCatalog(
  WidgetTester tester,
  _Controller controller,
  _Registry registry, {
  PhoneHostKind? phone,
  Future<void> Function(BuildContext, PhoneHostKind)? openPhoneTools,
}) async {
  await tester.pumpWidget(
    _app(
      McpCatalogScreen(
        controller: controller,
        client: registry,
        phoneHostOf: (_) => phone,
        openPhoneTools: openPhoneTools,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _tapSwitch(WidgetTester tester, String serverName) async {
  final target = find.byKey(ValueKey('mcp-catalog-switch-$serverName'));
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  await tester.tap(target);
  await tester.pumpAndSettle();
}

bool _switchValue(WidgetTester tester, String serverName) => tester
    .widget<KitSwitchRow>(
      find.ancestor(
        of: find.byKey(ValueKey('mcp-catalog-switch-$serverName')),
        matching: find.byType(KitSwitchRow),
      ),
    )
    .value;

EditableText _editable(WidgetTester tester, String key) => tester.widget(
  find.descendant(
    of: find.byKey(ValueKey(key)),
    matching: find.byType(EditableText),
  ),
);

Future<void> _save(WidgetTester tester) async {
  final save = find.byKey(const ValueKey('mcp-save'));
  await tester.ensureVisible(save);
  await tester.tap(save);
  await tester.pumpAndSettle();
}

/// Scrolls the MCP form until [key] is built (the form is a lazy list).
Future<void> _reveal(WidgetTester tester, String key) async {
  await tester.scrollUntilVisible(
    find.byKey(ValueKey(key)),
    120,
    scrollable: find
        .descendant(
          of: find.byKey(const ValueKey('mcp-setup-form')),
          matching: find.byType(Scrollable),
        )
        .first,
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    KitRedact.clearKnownSecrets();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  group('the catalogue model (lib/domain/mcp_catalog.dart)', () {
    test('maps each kind of listing to what the form opens with', () {
      final items = {
        for (final listing in _listings())
          listing['name']: McpCatalogItem.from(
            RegistryEntry.fromJson(listing)!,
          ),
      };
      final weather = items['com.example/weather']!;
      expect(weather.serverName, 'weather');
      expect(weather.title, 'Weather');
      expect(weather.runtime, McpCatalogRuntime.hosted);
      expect(weather.hostedBy, 'weather.example');
      expect(weather.needsKey, isTrue);
      expect(weather.draft!.url, 'https://weather.example/mcp');
      // Names only: a registry "value" never becomes a header value.
      expect(weather.draft!.headers, {'Authorization': ''});

      final files = items['io.github.acme/files-mcp']!;
      expect(files.runtime, McpCatalogRuntime.node);
      expect(files.draft!.command, ['npx', '-y', '@acme/files-mcp@1.2.0']);
      // Required variables only, and never a registry default.
      expect(files.draft!.environment, {'FILES_TOKEN': ''});

      final notes = items['io.github.acme/notes']!;
      expect(notes.runtime, McpCatalogRuntime.python);
      expect(notes.draft!.command, ['uvx', 'acme-notes==0.3.0']);

      final box = items['io.github.acme/box']!;
      expect(box.runtime, McpCatalogRuntime.docker);
      expect(box.addable, isFalse);
    });

    test('names come from the listing, generic ones from its namespace', () {
      expect(catalogServerName('io.github.acme/weather-mcp'), 'weather-mcp');
      expect(catalogServerName('ac.inference.sh/mcp'), 'inference-sh');
      expect(catalogServerName('com.Example/My Tool!'), 'my-tool');
    });

    test('orders on, then addable, then not addable', () {
      final entries = [
        for (final listing in _listings().reversed)
          RegistryEntry.fromJson(listing)!,
      ];
      final ordered = orderCatalog(entries, installed: {'files-mcp'});
      expect(ordered.map((i) => i.serverName), [
        'files-mcp',
        'notes',
        'weather',
        'box',
      ]);
    });

    test('a listing title is redacted like its description', () {
      final entry = RegistryEntry.fromJson({
        'name': 'x/y',
        'version': '1',
        'title': 'ghp_abcdefghijklmnopqrstuvwxyz0123456789',
      })!;
      expect(entry.title.contains('ghp_abcdefghijklmnopqrstuvwxyz'), isFalse);
    });
  });

  group('MCP › Add (P2.4)', () {
    testWidgets('offers the catalogue and the manual form, nothing dead', (
      tester,
    ) async {
      McpAddPath? chosen;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => KitScreen(
              topBar: const KitTopBar(title: 'Host'),
              body: KitButton.primary(
                label: 'Add',
                onPressed: () async => chosen = await showMcpAddSheet(
                  context,
                  capabilities: api2ServerCapabilities,
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Add'));
      await tester.pumpAndSettle();
      expect(find.text('Add an MCP server'), findsOneWidget);
      expect(find.text('Browse the catalogue'), findsOneWidget);
      expect(find.text('Enter manually'), findsOneWidget);
      // The setup assistant is not built (owner, 2026-09-28): no row for it.
      expect(find.textContaining('assistant'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('mcp-add-catalog')));
      await tester.pumpAndSettle();
      expect(chosen, McpAddPath.catalog);
    });

    testWidgets('Browse explains when this server has no catalogue', (
      tester,
    ) async {
      McpAddPath? chosen;
      const readOnly = ServerCapabilities(
        mcpConfigWrites: false,
        mcpRuntimeAdds: false,
      );
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => KitScreen(
              topBar: const KitTopBar(title: 'Host'),
              body: KitButton.primary(
                label: 'Add',
                onPressed: () async => chosen = await showMcpAddSheet(
                  context,
                  capabilities: readOnly,
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Add'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          "No catalogue for this server: it doesn't accept new MCP servers "
          'from the app.',
        ),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('mcp-add-catalog')));
      await tester.pumpAndSettle();
      expect(chosen, isNull);
      expect(find.byKey(const ValueKey('mcp-add-sheet')), findsOneWidget);
    });
  });

  group('MCP catalogue (P2.5)', () {
    testWidgets('asks before the first request, then lists as switches', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final registry = _Registry();
      await _openCatalog(tester, controller, registry);

      expect(find.byKey(const ValueKey('mcp-catalog-consent')), findsOneWidget);
      expect(registry.searches, isEmpty, reason: 'no request before consent');
      await tester.tap(find.byKey(const ValueKey('mcp-catalog-load')));
      await tester.pumpAndSettle();

      expect(registry.searches, [null]);
      expect(find.byType(KitSwitchRow), findsNWidgets(4));
      expect(
        find.text('Hosted by weather.example · Needs an API key'),
        findsOneWidget,
      );
      expect(
        find.text('Needs Node on the server · Needs an API key'),
        findsOneWidget,
      );
      expect(find.text('Needs Python with uv on the server'), findsOneWidget);
      expect(
        find.textContaining('Runs in Docker. To add it anyway'),
        findsOneWidget,
      );
      expect(
        find.text(
          "The registry lists no prices. A hosted server's owner may charge "
          'for it or ask for an account.',
        ),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('registry text stays plain text and is redacted', (
      tester,
    ) async {
      final controller = await _controller(prefs: _optedIn);
      addTearDown(controller.dispose);
      await _openCatalog(tester, controller, _Registry());

      // The markdown link is shown as its characters, never as a link.
      expect(
        find.textContaining('[Click here](https://evil.example/steal)'),
        findsOneWidget,
      );
      expect(find.byType(KitMarkdown), findsNothing);
      expect(find.textContaining('fake-registry-secret'), findsNothing);
      expect(find.textContaining('fake-header-default'), findsNothing);
      expect(find.textContaining('rm -rf'), findsNothing);
    });

    testWidgets(
      'turning one on is the manual form: same Save, same gateway call',
      (tester) async {
        final controller = await _controller(prefs: _optedIn);
        addTearDown(controller.dispose);
        await _openCatalog(tester, controller, _Registry());
        expect(_switchValue(tester, 'weather'), isFalse);

        await _tapSwitch(tester, 'weather');
        expect(find.byType(McpSetupScreen), findsOneWidget);
        expect(
          find.byKey(const ValueKey('mcp-setup-from-catalog')),
          findsOneWidget,
        );
        expect(_editable(tester, 'mcp-name').controller.text, 'weather');
        await _reveal(tester, 'mcp-url');
        expect(
          _editable(tester, 'mcp-url').controller.text,
          'https://weather.example/mcp',
        );
        // The required header's name is fixed; its value is a secret field
        // that starts empty, whatever the registry said.
        expect(
          _editable(tester, 'mcp-header-key-0').controller.text,
          'Authorization',
        );
        final value = _editable(tester, 'mcp-header-value-0');
        expect(value.obscureText, isTrue);
        expect(value.controller.text, isEmpty);

        // Save with the required value missing: nothing is written.
        await _save(tester);
        expect(controller.repo.added, isEmpty);
        expect(find.text('Enter a value for Authorization'), findsOneWidget);

        await tester.enterText(
          find.byKey(const ValueKey('mcp-header-value-0')),
          'Bearer typed-secret',
        );
        await _save(tester);
        expect(find.byType(McpSetupScreen), findsNothing);
        expect(controller.repo.added, hasLength(1));
        expect(_switchValue(tester, 'weather'), isTrue);

        // The same server typed by hand into the same form.
        final (fromCatalog, catalogScope) = controller.repo.added.single;
        controller.repo.servers.clear();
        await tester.pumpWidget(_app(McpSetupScreen(controller: controller)));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byKey(const ValueKey('mcp-name')),
          'weather',
        );
        await tester.enterText(
          find.byKey(const ValueKey('mcp-url')),
          'https://weather.example/mcp',
        );
        await tester.enterText(
          find.byKey(const ValueKey('mcp-header-key-0')),
          'Authorization',
        );
        await tester.enterText(
          find.byKey(const ValueKey('mcp-header-value-0')),
          'Bearer typed-secret',
        );
        await _save(tester);
        final (byHand, handScope) = controller.repo.added.last;
        expect(fromCatalog.toConfigJson(), byHand.toConfigJson());
        expect(catalogScope, handScope);
      },
    );

    testWidgets('a local listing shows its command and asks for its key', (
      tester,
    ) async {
      final controller = await _controller(prefs: _optedIn);
      addTearDown(controller.dispose);
      await _openCatalog(tester, controller, _Registry());

      await _tapSwitch(tester, 'files-mcp');
      await _reveal(tester, 'mcp-command');
      expect(
        _editable(tester, 'mcp-command').controller.text,
        'npx\n-y\n@acme/files-mcp@1.2.0',
      );
      await _reveal(tester, 'mcp-env-value-0');
      expect(_editable(tester, 'mcp-env-key-0').controller.text, 'FILES_TOKEN');
      expect(_editable(tester, 'mcp-env-value-0').obscureText, isTrue);
      await tester.enterText(
        find.byKey(const ValueKey('mcp-env-value-0')),
        'typed-token',
      );
      await _save(tester);
      final (draft, scope) = controller.repo.added.single;
      expect(scope, McpConfigScope.runtimeLocation);
      expect(draft.command, ['npx', '-y', '@acme/files-mcp@1.2.0']);
      expect(draft.environment, {'FILES_TOKEN': 'typed-token'});
    });

    testWidgets('turning one off runs the MCP page removal', (tester) async {
      final controller = await _controller(prefs: _optedIn);
      addTearDown(controller.dispose);
      controller.repo.servers = [
        const McpServerInfo(name: 'weather', status: 'connected'),
      ];
      await _openCatalog(tester, controller, _Registry());
      expect(_switchValue(tester, 'weather'), isTrue);
      // On servers lead the one list.
      final first = tester.widget<KitSwitchRow>(
        find.byType(KitSwitchRow).first,
      );
      expect(first.title, 'Weather');

      await _tapSwitch(tester, 'weather');
      expect(
        find.byKey(const ValueKey('mcp-remove-confirm-sheet')),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('confirm-mcp-remove')));
      await tester.pumpAndSettle();
      expect(controller.removed.map((r) => r.$1), ['weather']);
      expect(_switchValue(tester, 'weather'), isFalse);
    });

    testWidgets('a server that cannot remove says why the switch stays on', (
      tester,
    ) async {
      // A saved-configuration server (OpenCode 1's capabilities): adds are
      // written to its configuration, removal is not offered.
      final controller = await _controller(
        caps: testServerCapabilities,
        prefs: _optedIn,
      );
      addTearDown(controller.dispose);
      controller.repo.servers = [
        const McpServerInfo(name: 'weather', status: 'connected'),
      ];
      await _openCatalog(tester, controller, _Registry());
      expect(
        find.text(
          "On. This server keeps it in its configuration, and the app can't "
          'remove it.',
        ),
        findsOneWidget,
      );
      await _tapSwitch(tester, 'weather');
      expect(controller.removed, isEmpty);
      expect(find.byType(KitConfirmSheet), findsNothing);
    });

    testWidgets('on this phone, a Node server offers Add tools › Node', (
      tester,
    ) async {
      final controller = await _controller(prefs: _optedIn);
      addTearDown(controller.dispose);
      final opened = <PhoneHostKind>[];
      await _openCatalog(
        tester,
        controller,
        _Registry(),
        phone: PhoneHostKind.inApp,
        openPhoneTools: (_, kind) async => opened.add(kind),
      );
      expect(find.textContaining('Needs Node on this phone'), findsOneWidget);

      await _tapSwitch(tester, 'files-mcp');
      expect(find.text('Files runs with Node'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('mcp-catalog-add-node')));
      await tester.pumpAndSettle();
      expect(opened, [PhoneHostKind.inApp]);
      expect(find.byType(McpSetupScreen), findsNothing);

      // Node already there: on to the same form.
      await _tapSwitch(tester, 'files-mcp');
      await tester.tap(find.byKey(const ValueKey('mcp-catalog-node-continue')));
      await tester.pumpAndSettle();
      expect(find.byType(McpSetupScreen), findsOneWidget);
    });

    testWidgets('an unreachable registry says so, with a way forward', (
      tester,
    ) async {
      final controller = await _controller(prefs: _optedIn);
      addTearDown(controller.dispose);
      final registry = _Registry()..fail = true;
      await _openCatalog(tester, controller, registry);

      expect(
        find.text("Couldn't load the public MCP registry"),
        findsOneWidget,
      );
      expect(find.textContaining('could not be loaded'), findsNothing);
      await tester.tap(find.text('Enter manually'));
      await tester.pumpAndSettle();
      expect(find.byType(McpSetupScreen), findsOneWidget);
    });

    testWidgets('a search that finds nothing says so', (tester) async {
      final controller = await _controller(prefs: _optedIn);
      addTearDown(controller.dispose);
      final registry = _Registry();
      await _openCatalog(tester, controller, registry);

      await tester.enterText(
        find.byKey(const ValueKey('mcp-catalog-search-field')),
        'zzz',
      );
      await tester.pump(const Duration(milliseconds: 600));
      await tester.pumpAndSettle();
      expect(registry.searches.last, 'zzz');
      expect(
        find.text('Nothing in the registry matches “zzz”'),
        findsOneWidget,
      );
    });

    testWidgets('an unreadable MCP list stops the switches and says why', (
      tester,
    ) async {
      final controller = await _controller(prefs: _optedIn);
      addTearDown(controller.dispose);
      controller.repo.listError = StateError('private upstream text');
      await _openCatalog(tester, controller, _Registry());
      expect(
        find.text("Couldn't read this server's MCP servers"),
        findsOneWidget,
      );
      expect(find.byType(KitSwitchRow), findsNothing);
      expect(find.textContaining('private upstream'), findsNothing);
    });

    testWidgets('a server that takes no MCP servers explains, no request', (
      tester,
    ) async {
      final controller = await _controller(
        caps: const ServerCapabilities(
          mcpConfigWrites: false,
          mcpRuntimeAdds: false,
        ),
        prefs: _optedIn,
      );
      addTearDown(controller.dispose);
      final registry = _Registry();
      await _openCatalog(tester, controller, registry);
      expect(
        find.byKey(const ValueKey('mcp-catalog-unavailable')),
        findsOneWidget,
      );
    });

    testWidgets('Stop using the registry forgets consent and the list', (
      tester,
    ) async {
      final controller = await _controller(prefs: _optedIn);
      addTearDown(controller.dispose);
      await _openCatalog(tester, controller, _Registry());
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.containsKey('oc.setupRegistry.laptop'), isTrue);

      await tester.tap(find.byKey(const ValueKey('mcp-catalog-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Stop using the registry'));
      await tester.pumpAndSettle();
      expect(prefs.containsKey('oc.setupRegistry.laptop'), isFalse);
      expect(find.byKey(const ValueKey('mcp-catalog-consent')), findsOneWidget);
    });
  });
}
