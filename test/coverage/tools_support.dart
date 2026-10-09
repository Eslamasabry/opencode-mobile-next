// Shared by the tools area's coverage ratchets: a repository that serves what
// the real OpenCode repository read off the wire, and a connection that hands
// it to the Tools screens.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';

import '../support/product_repository_fixtures.dart';
import 'servers_support.dart';

/// What the real repository read, served back to the screens.
class ToolsRepo
    implements ProductRepository, ActiveContextGateway, WebSearchGateway {
  List<CommandInfo> commands = const [];
  List<SkillInfo> skills = const [];
  List<ReferenceInfo> references = const [];
  List<McpServerInfo> servers = const [];
  List<McpResourceInfo> resources = const [];
  List<CodingToolInfo> tools = const [];
  List<String> toolIds = const [];
  ExperimentalServerCapabilities experimental =
      const ExperimentalServerCapabilities(backgroundSubagents: false);

  List<ActiveContextMessage> context = const [];
  List<WebSearchProvider> searchProviders = const [];
  WebSearchResponse? searchResponse;

  @override
  bool get activeContextSupported => true;

  @override
  Future<List<ActiveContextMessage>> loadActiveContext(
    String sessionID,
  ) async => context;

  @override
  Future<List<WebSearchProvider>> webSearchProviders() async => searchProviders;

  @override
  Future<WebSearchResponse> searchWeb(
    String query, {
    required String providerID,
  }) async => searchResponse!;

  List<TerminalProcess> terminals = const [];
  TerminalShellSettings shellSettings = const TerminalShellSettings(
    selected: '',
    options: [],
  );

  @override
  Future<List<TerminalProcess>> listTerminals() async => terminals;

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      shellSettings;

  final created = <String?>[];
  final selectedShells = <String>[];
  final resizes = <String>[];
  final renamed = <String>[];
  final removedTerminals = <String>[];

  @override
  Future<TerminalProcess> createTerminal({String? title}) async {
    created.add(title);
    return TerminalProcess(
      id: 'pty_new',
      title: title ?? 'Terminal',
      command: 'bash',
      arguments: const [],
      directory: '/work/shop',
      running: true,
      pid: 1,
    );
  }

  @override
  Future<void> selectTerminalShell(String value) async =>
      selectedShells.add(value);

  @override
  Future<TerminalChannel> connectTerminal(String id, {int? cursor}) async =>
      _EmptyChannel();

  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) async => resizes.add('$id:$rows:$cols');

  @override
  Future<void> renameTerminal(String id, String title) async =>
      renamed.add('$id:$title');

  @override
  Future<void> removeTerminal(String id) async => removedTerminals.add(id);

  /// A failed read of the command list (what the app was told).
  Object? commandsError;

  final calls = <String>[];

  final connected = <String>[];

  @override
  Future<void> connectMcp(String name) async => connected.add(name);

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<CommandInfo>> listCommands() async {
    if (commandsError case final error?) throw error;
    return commands;
  }

  @override
  Future<List<SkillInfo>> listSkills() async => skills;

  @override
  Future<List<ReferenceInfo>> listReferences() async => references;

  @override
  Future<List<McpServerInfo>> listMcpServers() async => servers;

  @override
  Future<List<McpResourceInfo>> listMcpResources() async => resources;

  @override
  Future<List<CodingToolInfo>> listCodingTools({
    required String providerID,
    required String modelID,
  }) async => tools;

  @override
  Future<List<String>> listCodingToolIDs() async => toolIds;

  @override
  Future<ExperimentalServerCapabilities> loadExperimentalCapabilities() async =>
      experimental;

  @override
  dynamic noSuchMethod(Invocation invocation) {
    calls.add(invocation.memberName.toString());
    return super.noSuchMethod(invocation);
  }
}

class _EmptyChannel implements TerminalChannel {
  @override
  Stream<String> get output => const Stream.empty();

  @override
  int? get cursor => 0;

  @override
  void write(String value) {}

  @override
  Future<void> close() async {}
}

/// A connected server whose repository is [repo].
Future<(ServersStore, ServersConnection)> toolsConnection(
  ToolsRepo repo,
) async {
  final (store, controller) = await serversState(
    profiles: [
      ServerProfile(
        id: 'srv',
        name: 'Studio OpenCode',
        baseUrl: 'https://studio.example.net:4096',
      ),
    ],
  );
  controller
    ..api = OpenCodeApi(baseUrl: 'https://studio.example.net:4096')
    ..repository = repo
    ..selectedModel = ModelRef(providerID: 'anthropic', modelID: 'opus')
    ..status = StreamStatus.connected;
  return (store, controller);
}

/// Serves [routes] (path to JSON) on a local port and runs [read] against
/// the app's real repository pointed at it, in real time.
Future<T> readFromWire<T>(
  WidgetTester tester,
  Map<String, Object?> routes,
  Future<T> Function(SdkProductRepository repository) read,
) async {
  final result = await tester.runAsync(
    () => HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        final body = routes[request.uri.path];
        request.response.headers.contentType = ContentType.json;
        if (body == null) {
          request.response.statusCode = HttpStatus.notFound;
        } else {
          request.response.write(jsonEncode(body));
        }
        await request.response.close();
      });
      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/shop', workspace: 'wrk_main');
        return await read(repository);
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null)),
  );
  return result as T;
}
