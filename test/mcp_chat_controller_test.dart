import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/mcp_oauth.dart';
import 'package:opencode_mobile/domain/mcp_catalog.dart';
import 'package:opencode_mobile/domain/mcp_chat.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/setup_registry.dart';
import 'package:opencode_mobile/state/mcp_chat_controller.dart';

McpCatalogItem item({bool key = false}) => McpCatalogItem.from(
  RegistryEntry.fromJson({
    'name': 'com.example/design',
    'version': '1.0.0',
    'remotes': [
      {
        'type': 'streamable-http',
        'url': 'https://example.com/mcp',
        if (key)
          'headers': [
            {'name': 'X-Api-Key', 'isRequired': true, 'isSecret': true},
          ],
      },
    ],
  })!,
);

class Gateway implements McpGateway {
  List<McpServerInfo> inventory = [];
  int adds = 0, connects = 0, auths = 0, cancels = 0, completions = 0;
  Object? failure;
  Completer<void>? addGate;
  Completer<McpAuthLaunch>? authGate;
  String statusAfterAdd = 'connected';
  String? receivedCode;
  McpAuthLaunch launch = McpAuthLaunch(
    authorizationUrl: Uri.parse(
      'https://login.example.com/authorize?state=test-state',
    ),
    oauthState: 'test-state',
  );
  @override
  Future<List<McpServerInfo>> listMcpServers() async {
    if (failure != null) throw failure!;
    return inventory;
  }

  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) async {
    adds++;
    expect(scope, McpConfigScope.runtimeLocation);
    await addGate?.future;
    inventory = [McpServerInfo(name: draft.name, status: statusAfterAdd)];
  }

  @override
  Future<void> connectMcp(String name) async {
    connects++;
  }

  @override
  Future<McpAuthLaunch> startMcpAuthentication(String name) async {
    auths++;
    return authGate == null ? launch : await authGate!.future;
  }

  @override
  Future<McpServerInfo> completeMcpAuthentication(
    String name,
    String code,
  ) async {
    completions++;
    receivedCode = code;
    inventory = [McpServerInfo(name: name, status: 'connected')];
    return inventory.single;
  }

  @override
  Future<void> cancelMcpAuthentication(String name) async {
    cancels++;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  late Gateway gateway;
  late McpChatController controller;
  late bool current;
  ServerCapabilities caps({
    bool enabled = true,
    bool refresh = true,
    bool oauth = true,
  }) => ServerCapabilities(
    mcpRuntimeAdds: true,
    mcpChatConnect: enabled,
    mcpChatToolRefresh: refresh,
    mcpChatOAuth: oauth,
  );
  McpChatController make({
    ServerCapabilities? capabilities,
    McpCatalogItem? catalogItem,
  }) => McpChatController(
    item: catalogItem ?? item(),
    gateway: () async => gateway,
    capabilities: () => capabilities ?? caps(),
    current: () => current,
  );
  setUp(() {
    gateway = Gateway();
    current = true;
    controller = make();
  });
  tearDown(() {
    controller.dispose();
  });

  test(
    'runtime add followed by authoritative status makes next-turn tools ready',
    () async {
      await controller.connect('design');
      expect(gateway.adds, 1);
      expect(gateway.connects, 0);
      expect(controller.snapshot.phase, McpChatPhase.toolsReady);
      expect(controller.snapshot.authorizationUrl, isNull);
    },
  );
  test(
    'double tap coalesces and successful connect never adds again',
    () async {
      gateway.addGate = Completer<void>();
      final a = controller.connect('design');
      final b = controller.connect('design');
      await Future<void>.delayed(Duration.zero);
      expect(gateway.adds, 1);
      gateway.addGate!.complete();
      await Future.wait([a, b]);
      await controller.connect('design');
      expect(gateway.adds, 1);
    },
  );
  test('unrelated existing same name is never reused or overwritten', () async {
    gateway.inventory = [
      const McpServerInfo(name: 'design', status: 'connected'),
    ];
    await controller.connect('design');
    expect(controller.snapshot.failure, McpChatFailure.nameConflict);
    expect(gateway.adds + gateway.connects, 0);
    await controller.refresh();
    expect(controller.snapshot.phase, isNot(McpChatPhase.toolsReady));
  });
  test('unsupported runtime makes no calls', () async {
    controller.dispose();
    controller = make(capabilities: caps(enabled: false));
    await controller.connect('design');
    expect(controller.snapshot.failure, McpChatFailure.unavailable);
    expect(gateway.adds, 0);
  });
  test('wrong requested server cannot mutate', () async {
    await controller.connect('other');
    expect(controller.snapshot.failure, McpChatFailure.invalidSuggestion);
    expect(gateway.adds, 0);
  });
  test('required configuration stays in Tools', () async {
    controller.dispose();
    controller = make(catalogItem: item(key: true));
    await controller.connect('design');
    expect(controller.snapshot.failure, McpChatFailure.setupRequired);
    expect(gateway.adds, 0);
  });
  test('no refresh contract never claims ready', () async {
    controller.dispose();
    controller = make(capabilities: caps(refresh: false));
    await controller.connect('design');
    expect(controller.snapshot.phase, McpChatPhase.connectedReadinessUnknown);
  });
  test('source changes during add cannot publish success', () async {
    gateway.addGate = Completer<void>();
    final work = controller.connect('design');
    await Future<void>.delayed(Duration.zero);
    current = false;
    gateway.addGate!.complete();
    await work;
    expect(controller.snapshot.failure, McpChatFailure.sourceChanged);
    expect(gateway.auths, 0);
  });
  test('gateway errors never escape or expose raw credential text', () async {
    gateway.failure = StateError('secret-test-value');
    await controller.connect('design');
    expect(controller.snapshot.failure, McpChatFailure.connectFailed);
    expect(
      controller.snapshot.toString(),
      isNot(contains('secret-test-value')),
    );
  });
  test('authentication is explicit and uses validated code', () async {
    gateway.statusAfterAdd = 'needs_auth';
    await controller.connect('design');
    expect(controller.snapshot.phase, McpChatPhase.needsAuthentication);
    expect(gateway.auths, 0);
    await controller.startOAuth();
    expect(controller.snapshot.phase, McpChatPhase.authorizing);
    expect(controller.snapshot.authorizationUrl?.host, 'login.example.com');
    await controller.completeOAuth(
      'http://127.0.0.1/callback?code=abc&state=wrong',
    );
    expect(gateway.completions, 0);
    expect(controller.snapshot.failure, McpChatFailure.authenticationFailed);
  });
  test('valid manual callback completes and rechecks inventory', () async {
    gateway.statusAfterAdd = 'needs_auth';
    await controller.connect('design');
    await controller.startOAuth();
    await controller.completeOAuth(
      'http://127.0.0.1/callback?code=abc&state=test-state',
    );
    expect(gateway.receivedCode, 'abc');
    expect(controller.snapshot.phase, McpChatPhase.toolsReady);
    expect(controller.snapshot.authorizationUrl, isNull);
  });
  test('OAuth off and unsafe launch fail closed', () async {
    controller.dispose();
    controller = make(capabilities: caps(oauth: false));
    gateway.statusAfterAdd = 'needs_auth';
    await controller.connect('design');
    await controller.startOAuth();
    expect(controller.snapshot.failure, McpChatFailure.oauthUnavailable);
    expect(gateway.auths, 0);
    controller.dispose();
    controller = make();
    gateway.inventory = [];
    await controller.connect('design');
    gateway.launch = McpAuthLaunch(
      authorizationUrl: Uri.parse('javascript:alert(1)'),
      oauthState: 'test-state',
    );
    await controller.startOAuth();
    expect(controller.snapshot.authorizationUrl, isNull);
    expect(controller.snapshot.failure, McpChatFailure.authenticationFailed);
  });
  test('dispose never revokes server credentials', () async {
    gateway.statusAfterAdd = 'needs_auth';
    await controller.connect('design');
    await controller.startOAuth();
    controller.dispose();
    expect(gateway.cancels, 0);
  });
  test('cancel only acts on owned pending authentication', () async {
    await controller.cancelOAuth();
    expect(gateway.cancels, 0);
    expect(controller.snapshot.phase, McpChatPhase.suggested);
    gateway.statusAfterAdd = 'needs_auth';
    await controller.connect('design');
    await controller.startOAuth();
    await controller.cancelOAuth();
    expect(gateway.cancels, 1);
    expect(controller.snapshot.authorizationUrl, isNull);
  });
  test('client registration failure requires setup instead of OAuth', () async {
    gateway.statusAfterAdd = 'needs_client_registration';
    await controller.connect('design');
    expect(controller.snapshot.failure, McpChatFailure.setupRequired);
    await controller.startOAuth();
    expect(gateway.auths, 0);
  });
  test(
    'cancel during delayed OAuth start does not expose late launch',
    () async {
      gateway.statusAfterAdd = 'needs_auth';
      await controller.connect('design');
      gateway.authGate = Completer<McpAuthLaunch>();
      final starting = controller.startOAuth();
      await Future<void>.delayed(Duration.zero);
      var lateLaunch = false;
      controller.addListener(() {
        lateLaunch |= controller.snapshot.authorizationUrl != null;
      });
      final cancelling = controller.cancelOAuth();
      gateway.authGate!.complete(gateway.launch);
      await Future.wait([starting, cancelling]);
      expect(gateway.cancels, 1);
      expect(lateLaunch, isFalse);
      expect(controller.snapshot.authorizationUrl, isNull);
      expect(controller.snapshot.phase, McpChatPhase.needsAuthentication);
    },
  );
  test(
    'owned callback validates state and discovers without exposing code',
    () async {
      final reservation = await HttpServer.bind(
        InternetAddress.loopbackIPv4,
        0,
      );
      final redirect = Uri.parse(
        'http://127.0.0.1:${reservation.port}/callback',
      );
      await reservation.close(force: true);
      gateway.launch = McpAuthLaunch(
        authorizationUrl: Uri.https('login.example.com', '/authorize', {
          'state': 'test-state',
          'redirect_uri': redirect.toString(),
        }),
        oauthState: 'test-state',
      );
      gateway.statusAfterAdd = 'needs_auth';
      await controller.connect('design');
      await controller.startOAuth();
      expect(controller.snapshot.manualCodeRequired, isFalse);
      final http = HttpClient();
      try {
        final wrong = await http.getUrl(
          redirect.replace(queryParameters: {'code': 'abc', 'state': 'wrong'}),
        );
        final rejected = await wrong.close();
        expect(rejected.statusCode, 400);
        await rejected.drain<void>();
        expect(gateway.completions, 0);
        final ready = Completer<void>();
        void changed() {
          if (controller.snapshot.phase == McpChatPhase.toolsReady &&
              !ready.isCompleted) {
            ready.complete();
          }
        }

        controller.addListener(changed);
        final request = await http.getUrl(
          redirect.replace(
            queryParameters: {'code': 'abc', 'state': 'test-state'},
          ),
        );
        final accepted = await request.close();
        expect(accepted.statusCode, 200);
        await accepted.drain<void>();
        await ready.future.timeout(const Duration(seconds: 5));
        controller.removeListener(changed);
        expect(gateway.completions, 1);
        expect(controller.snapshot.authorizationUrl, isNull);
      } finally {
        http.close(force: true);
      }
    },
  );
  test('busy callback port explicitly requires manual return URL', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    try {
      gateway.launch = McpAuthLaunch(
        authorizationUrl: Uri.https('login.example.com', '/authorize', {
          'state': 'test-state',
          'redirect_uri': 'http://127.0.0.1:${server.port}/callback',
        }),
        oauthState: 'test-state',
      );
      gateway.statusAfterAdd = 'needs_auth';
      await controller.connect('design');
      await controller.startOAuth();
      expect(controller.snapshot.manualCodeRequired, isTrue);
    } finally {
      await server.close(force: true);
    }
  });
  test(
    'explicit invalidation cannot be undone by returning to source',
    () async {
      controller.invalidate();
      await controller.connect('design');
      expect(gateway.adds, 0);
      expect(controller.snapshot.failure, McpChatFailure.sourceChanged);
    },
  );
}
