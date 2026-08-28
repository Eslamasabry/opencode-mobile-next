import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
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
    expect(find.text('2 usable'), findsOneWidget);
    expect(find.text('3 registered'), findsOneWidget);
    expect(find.text('Background subagents unavailable'), findsOneWidget);
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

  testWidgets('tool search and schema use server-returned truth', (
    tester,
  ) async {
    final repository = _ToolsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(ToolsScreen(controller: controller)));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('tools-search')),
      'filesystem',
    );
    await tester.pump();
    expect(find.byKey(const ValueKey('coding-tool-bash')), findsNothing);
    expect(find.byKey(const ValueKey('coding-tool-read')), findsOneWidget);

    await tester.ensureVisible(find.byKey(const ValueKey('coding-tool-read')));
    await tester.tap(find.byKey(const ValueKey('coding-tool-read')));
    await tester.pumpAndSettle();
    expect(find.text('PARAMETER SCHEMA'), findsOneWidget);
    expect(find.textContaining('"filePath": {'), findsOneWidget);
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

  testWidgets('long server descriptions keep parameter schema reachable', (
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

    await tester.tap(find.byKey(const ValueKey('coding-tool-apply_patch')));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('tool-parameter-schema')),
      200,
      scrollable: find
          .descendant(
            of: find.byKey(const ValueKey('tool-detail-scroll')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    expect(find.byKey(const ValueKey('tool-parameter-schema')), findsOneWidget);
    expect(find.textContaining('"patch": {'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Library exposes one native tools destination', (tester) async {
    final repository = _ToolsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(Scaffold(body: LibraryScreen(controller: controller))),
    );

    // Tools now lives inside the tabbed Commands & tools destination.
    expect(find.text('Commands & tools'), findsOneWidget);
    await tester.tap(find.text('Commands & tools'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(Tab, 'Tools'));
    await tester.pumpAndSettle();

    expect(find.byType(ToolsScreen), findsOneWidget);
    expect(find.byKey(const ValueKey('coding-tools-list')), findsOneWidget);
  });
}
