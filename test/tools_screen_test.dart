import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/tools_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ToolsRepository implements ProductRepository {
  Object? capabilitiesError;
  Object? registeredError;
  Object? toolsError;
  String? providerID;
  String? modelID;
  List<CodingToolInfo> tools = const [
    CodingToolInfo(
      id: 'bash',
      description: 'Run a shell command in the active project.',
      parameters: {
        r'$schema': 'https://json-schema.org/draft/2020-12/schema',
        'type': 'object',
        'properties': {
          'command': {'type': 'string'},
        },
        'required': ['command'],
      },
    ),
    CodingToolInfo(
      id: 'read',
      description: 'Read a file from the project filesystem.',
      parameters: {
        'type': 'object',
        'properties': {
          'filePath': {'type': 'string'},
        },
      },
    ),
  ];

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<ExperimentalServerCapabilities> loadExperimentalCapabilities() async {
    if (capabilitiesError case final error?) throw error;
    return const ExperimentalServerCapabilities(backgroundSubagents: false);
  }

  @override
  Future<List<String>> listCodingToolIDs() async {
    if (registeredError case final error?) throw error;
    return const ['bash', 'read', 'task'];
  }

  @override
  Future<List<CodingToolInfo>> listCodingTools({
    required String providerID,
    required String modelID,
  }) async {
    if (toolsError case final error?) throw error;
    this.providerID = providerID;
    this.modelID = modelID;
    return tools;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _ToolsController extends ConnectionController {
  _ToolsController(super.store, this.toolsRepository) {
    repository = toolsRepository;
    status = StreamStatus.connected;
    selectedModel = ModelRef(providerID: 'openai', modelID: 'gpt-5.6-sol');
    catalog = const CatalogSnapshot(
      providers: [],
      models: [
        CatalogModel(
          id: 'gpt-5.6-sol',
          providerID: 'openai',
          name: 'GPT-5.6 Sol',
          enabled: true,
          status: 'active',
          contextLimit: 400000,
          outputLimit: 128000,
          reasoning: true,
          attachments: true,
          tools: true,
          variants: [],
        ),
      ],
      agents: [],
    );
  }

  final _ToolsRepository toolsRepository;

  @override
  Future<ProductRepository?> prepareActionRepository() async => toolsRepository;
}

Future<_ToolsController> _controller(_ToolsRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  return _ToolsController(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
    repository,
  );
}

Widget _app(Widget home, {double textScale = 1}) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: child!,
  ),
  home: home,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('tool inventory renders flat and fits compact large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(640, 1280);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _ToolsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(ToolsScreen(controller: controller), textScale: 2),
    );
    await tester.pumpAndSettle();

    expect(find.text('GPT-5.6 Sol'), findsOneWidget);
    // No counts line; the missing subagents are the model row's own words,
    // and the row itself opens the picker (no second "Change" button).
    expect(find.text('2 usable'), findsNothing);
    expect(find.text('3 registered'), findsNothing);
    expect(
      find.textContaining('no background subagents', findRichText: true),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('tools-change-model')), findsNothing);
    expect(find.byType(Card), findsNothing);
    expect(repository.providerID, 'openai');
    expect(repository.modelID, 'gpt-5.6-sol');
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('registered-tool-task')),
      120,
      scrollable: find
          .descendant(
            of: find.byKey(const ValueKey('coding-tools-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    expect(find.byKey(const ValueKey('registered-tool-task')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  // Emulator QA B11: the server's description (prompt text written for the
  // model) was the row's title, cut mid-sentence.
  test('a tool summary is the first sentence of its description', () {
    expect(
      toolSummary(
        'Use this tool when you need to ask the user questions during '
        'execution. This allows you to:\n1. Gather preferences',
      ),
      'Use this tool when you need to ask the user questions during '
      'execution.',
    );
    expect(
      toolSummary(
        '- Fast file pattern matching tool that works with any codebase '
        'size\n- Supports glob patterns like "**/*.js"',
      ),
      'Fast file pattern matching tool that works with any codebase size',
    );
    expect(
      toolSummary('Reads a file.\n\nUsage:\n- The path must be absolute'),
      'Reads a file.',
    );
    expect(
      toolSummary(
        'Executes a given bash command in a\npersistent shell. More.',
      ),
      'Executes a given bash command in a persistent shell.',
    );
    expect(toolSummary('  '), isEmpty);
    expect(toolSummary('Version 1.2 of the tool'), 'Version 1.2 of the tool');
  });

  testWidgets('a tool row names the tool and says its first sentence', (
    tester,
  ) async {
    final repository = _ToolsRepository()
      ..tools = const [
        CodingToolInfo(
          id: 'question',
          description:
              'Use this tool when you need to ask the user questions. This '
              'allows you to gather preferences.\n\nUsage notes: …',
          parameters: {'type': 'object'},
        ),
        CodingToolInfo(id: 'invalid', description: '', parameters: {}),
      ];
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(ToolsScreen(controller: controller)));
    await tester.pumpAndSettle();

    final question = find.byKey(const ValueKey('coding-tool-question'));
    expect(
      find.descendant(of: question, matching: find.text('question')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: question,
        matching: find.text(
          'Use this tool when you need to ask the user questions.',
          findRichText: true,
        ),
      ),
      findsOneWidget,
    );
    expect(
      find.textContaining('This allows', findRichText: true),
      findsNothing,
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('coding-tool-invalid')),
        matching: find.text('invalid'),
      ),
      findsOneWidget,
    );

    // The whole description is one tap away, on the tool's sheet.
    await tester.tap(question);
    await tester.pumpAndSettle();
    expect(
      find.textContaining('This allows you to gather preferences.'),
      findsOneWidget,
    );
  });

  testWidgets('the tool sheet copies the schema the server returned', (
    tester,
  ) async {
    final copied = _clipboard(tester);
    final repository = _ToolsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(ToolsScreen(controller: controller)));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('coding-tool-read')));
    await tester.pumpAndSettle();
    // No JSON on the sheet: the schema is one Copy away in its menu.
    expect(find.text('Parameter schema'), findsNothing);
    expect(find.textContaining('"filePath": {'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('tool-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Copy parameter schema'));
    await tester.pumpAndSettle();
    expect(copied, hasLength(1));
    expect(copied.single, contains('"filePath": {'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('optional capability failure does not hide model tools', (
    tester,
  ) async {
    final repository = _ToolsRepository()
      ..capabilitiesError = const ProductException(
        'Capability endpoint is unavailable',
      );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(ToolsScreen(controller: controller)));
    await tester.pumpAndSettle();

    expect(find.text('server capability unavailable'), findsOneWidget);
    expect(find.byKey(const ValueKey('coding-tool-bash')), findsOneWidget);
    expect(find.byKey(const ValueKey('coding-tool-read')), findsOneWidget);
  });

  testWidgets('long server descriptions keep Copy parameter schema reachable', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 520);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _ToolsRepository()
      ..tools = [
        CodingToolInfo(
          id: 'apply_patch',
          description: List.filled(
            24,
            'Edit files using a structured patch contract.',
          ).join(' '),
          parameters: const {
            'type': 'object',
            'properties': {
              'patch': {'type': 'string'},
            },
          },
        ),
      ];
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(ToolsScreen(controller: controller)));
    await tester.pumpAndSettle();

    final copied = _clipboard(tester);
    await tester.tap(find.byKey(const ValueKey('coding-tool-apply_patch')));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    // Below a long description, the menu is still reachable by scrolling.
    await tester.ensureVisible(find.byKey(const ValueKey('tool-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('tool-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Copy parameter schema'));
    await tester.pumpAndSettle();
    expect(copied.single, contains('"patch": {'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('Settings exposes one native tools destination', (tester) async {
    final repository = _ToolsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        Scaffold(body: SettingsScreen(controller: controller, embedded: true)),
      ),
    );
    await tester.pumpAndSettle();

    // One door (P3.10 Settings IA, 2bec3ed3): Settings › Tools, in the
    // Agent group, is the one Tools page, with a tab for the model's tools.
    final tools = find.byKey(const ValueKey('settings-tools'));
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('settings-group-agent')),
        matching: tools,
      ),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('settings-commands-tools')), findsNothing);
    await tester.ensureVisible(tools);
    await tester.pumpAndSettle();
    await tester.tap(tools);
    await tester.pumpAndSettle();

    expect(find.byType(CapabilitiesScreen), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('capabilities-tab-Tools')));
    await tester.pumpAndSettle();

    expect(find.byType(ToolsScreen), findsOneWidget);
    expect(find.byKey(const ValueKey('coding-tools-list')), findsOneWidget);
  });
}

List<String> _clipboard(WidgetTester tester) {
  final copied = <String>[];
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    SystemChannels.platform,
    (call) async {
      if (call.method == 'Clipboard.setData') {
        copied.add((call.arguments as Map)['text'] as String);
      }
      return null;
    },
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      null,
    ),
  );
  return copied;
}
