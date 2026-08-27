import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/mcp_setup_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _McpRepository implements ProductRepository {
  McpServerDraft? addedDraft;
  McpConfigScope? addedScope;
  Object? addError;

  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) async {
    if (addError case final error?) throw error;
    addedDraft = draft;
    addedScope = scope;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _McpController extends ConnectionController {
  _McpController(super.store, this.actionRepository);

  final ProductRepository actionRepository;
  int reloadCalls = 0;
  Object? reloadError;

  @override
  Future<ProductRepository?> prepareActionRepository() async =>
      actionRepository;

  @override
  Future<void> reloadAfterConfigurationChange() async {
    reloadCalls += 1;
    if (reloadError case final error?) throw error;
  }
}

class _SetupHost extends StatelessWidget {
  const _SetupHost({required this.controller});

  final ConnectionController controller;

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: FilledButton(
        onPressed: () => Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => McpSetupScreen(controller: controller),
          ),
        ),
        child: const Text('Open setup'),
      ),
    ),
  );
}

Future<_McpController> _controller(
  ProductRepository repository, {
  String? directory,
}) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return _McpController(ProfileStore(prefs: preferences), repository)
    ..directory = directory;
}

Future<void> _open(
  WidgetTester tester,
  ConnectionController controller, {
  double textScale = 1,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Builder(
        builder: (context) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(textScale)),
          child: _SetupHost(controller: controller),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Open setup'));
  await tester.pumpAndSettle();
}

Future<void> _reveal(WidgetTester tester, Key key) async {
  await tester.scrollUntilVisible(
    find.byKey(key),
    180,
    scrollable: find
        .descendant(
          of: find.byKey(const ValueKey('mcp-setup-form')),
          matching: find.byType(Scrollable),
        )
        .first,
  );
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('saves a project remote MCP with exact advanced fields', (
    tester,
  ) async {
    final repository = _McpRepository();
    final controller = await _controller(repository, directory: '/work/mobile');
    addTearDown(controller.dispose);
    await _open(tester, controller);

    await tester.enterText(
      find.byKey(const ValueKey('mcp-name')),
      'remote-docs',
    );
    await tester.enterText(
      find.byKey(const ValueKey('mcp-url')),
      'https://mcp.example.com/rpc',
    );
    await tester.enterText(
      find.byKey(const ValueKey('mcp-headers')),
      'Authorization=Bearer test-token',
    );
    await _reveal(tester, const ValueKey('mcp-oauth-detection'));
    await tester.tap(find.byKey(const ValueKey('mcp-oauth-detection')));
    await _reveal(tester, const ValueKey('mcp-timeout'));
    await tester.enterText(find.byKey(const ValueKey('mcp-timeout')), '12000');
    await tester.tap(find.byKey(const ValueKey('mcp-save')));
    await tester.pumpAndSettle();

    expect(repository.addedScope, McpConfigScope.project);
    expect(repository.addedDraft?.normalizedName, 'remote-docs');
    expect(repository.addedDraft?.kind, McpServerKind.remote);
    expect(repository.addedDraft?.url, 'https://mcp.example.com/rpc');
    expect(repository.addedDraft?.headers, {
      'Authorization': 'Bearer test-token',
    });
    expect(repository.addedDraft?.detectOAuth, isFalse);
    expect(repository.addedDraft?.timeoutMs, 12000);
    expect(controller.reloadCalls, 1);
    expect(find.byType(McpSetupScreen), findsNothing);
  });

  testWidgets('saves a global local MCP on a compact large-text phone', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _McpRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await _open(tester, controller, textScale: 2);

    await tester.tap(find.text('Local command'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('mcp-name')),
      'local-tools',
    );
    await _reveal(tester, const ValueKey('mcp-command'));
    await tester.enterText(
      find.byKey(const ValueKey('mcp-command')),
      'npx\n-y\n@example/mcp-server',
    );
    await _reveal(tester, const ValueKey('mcp-cwd'));
    await tester.enterText(
      find.byKey(const ValueKey('mcp-cwd')),
      '/work/mobile',
    );
    await _reveal(tester, const ValueKey('mcp-environment'));
    await tester.enterText(
      find.byKey(const ValueKey('mcp-environment')),
      'LOG_LEVEL=warn',
    );
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('mcp-save')));
    await tester.pumpAndSettle();

    expect(repository.addedScope, McpConfigScope.global);
    expect(repository.addedDraft?.kind, McpServerKind.local);
    expect(repository.addedDraft?.command, [
      'npx',
      '-y',
      '@example/mcp-server',
    ]);
    expect(repository.addedDraft?.cwd, '/work/mobile');
    expect(repository.addedDraft?.environment, {'LOG_LEVEL': 'warn'});
    expect(controller.reloadCalls, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('keeps validation and server errors inline without saving', (
    tester,
  ) async {
    final repository = _McpRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await _open(tester, controller);

    await tester.enterText(find.byKey(const ValueKey('mcp-name')), 'duplicate');
    await tester.enterText(
      find.byKey(const ValueKey('mcp-url')),
      'https://mcp.example.com',
    );
    await tester.enterText(
      find.byKey(const ValueKey('mcp-headers')),
      'Authorization without equals',
    );
    await tester.tap(find.byKey(const ValueKey('mcp-save')));
    await tester.pump();

    expect(find.textContaining('Use KEY=VALUE'), findsOneWidget);
    expect(repository.addedDraft, isNull);

    await tester.enterText(
      find.byKey(const ValueKey('mcp-headers')),
      'Authorization=token',
    );
    repository.addError = const ProductException(
      'An MCP server named "duplicate" already exists',
    );
    await tester.tap(find.byKey(const ValueKey('mcp-save')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('mcp-save-error')), findsOneWidget);
    expect(find.textContaining('already exists'), findsOneWidget);
    expect(controller.reloadCalls, 0);
    expect(find.byType(McpSetupScreen), findsOneWidget);
  });

  testWidgets('does not offer a duplicate save when reconnect fails', (
    tester,
  ) async {
    final repository = _McpRepository();
    final controller = await _controller(repository)
      ..reloadError = const ProductException('Endpoint is unavailable');
    addTearDown(controller.dispose);
    await _open(tester, controller);

    await tester.enterText(find.byKey(const ValueKey('mcp-name')), 'docs');
    await tester.enterText(
      find.byKey(const ValueKey('mcp-url')),
      'https://mcp.example.com',
    );
    await tester.tap(find.byKey(const ValueKey('mcp-save')));
    await tester.pumpAndSettle();

    expect(repository.addedDraft?.normalizedName, 'docs');
    expect(controller.reloadCalls, 1);
    expect(find.textContaining('Saved in OpenCode'), findsOneWidget);
    expect(find.text('Close'), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('mcp-name')))
          .enabled,
      isFalse,
    );

    await tester.tap(find.byKey(const ValueKey('mcp-save')));
    await tester.pumpAndSettle();
    expect(find.byType(McpSetupScreen), findsNothing);
  });
}
