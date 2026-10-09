import 'package:flutter/foundation.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/mcp_catalog.dart';
import 'package:opencode_mobile/domain/mcp_chat.dart';
import 'package:opencode_mobile/domain/setup_registry.dart';
import 'package:opencode_mobile/state/connector_card_host.dart';

const connectorScope = GenUiScope(
  profileID: 'p1',
  sourceId: 's1',
  directory: '/root/projects/alpha',
);

GenUiCard connectorCard({String id = 'acme/design'}) => GenUiCard(
  scope: connectorScope,
  id: 'suggest-design',
  title: 'Suggested connector: Design',
  sessionID: 'ses-1',
  callID: 'call-c1',
  messageID: 'msg-1',
  revision: 'r1',
  body: const [],
  connector: GenUiConnectorSuggestion(
    catalogId: id,
    reason: 'Find design references for this screen.',
  ),
);

McpCatalogItem connectorItem({bool hosted = true}) => McpCatalogItem.from(
  RegistryEntry.fromJson({
    'name': 'acme/design',
    'version': '1.0.0',
    'title': 'Design Reference',
    'description': 'Search design inspiration.',
    if (hosted)
      'remotes': [
        {'type': 'streamable-http', 'url': 'https://mcp.acme.example/mcp'},
      ]
    else
      'packages': [
        {
          'registryType': 'npm',
          'identifier': 'acme-design',
          'version': '1.0.0',
          'transport': {'type': 'stdio'},
        },
      ],
  })!,
);

class FakeConnectorChat extends ChangeNotifier implements ConnectorChat {
  FakeConnectorChat([McpChatSnapshot? start])
    : _snapshot = start ?? const McpChatSnapshot(McpChatPhase.suggested);
  McpChatSnapshot _snapshot;
  final calls = <String>[];

  @override
  McpChatSnapshot get snapshot => _snapshot;
  set snapshot(McpChatSnapshot value) {
    _snapshot = value;
    notifyListeners();
  }

  @override
  String get serverName => 'design-reference';
  @override
  Future<void> connect(String serverId) async => calls.add('connect:$serverId');
  @override
  Future<void> startOAuth() async => calls.add('startOAuth');
  @override
  Future<void> completeOAuth(String callbackOrCode) async =>
      calls.add('complete');
  @override
  Future<void> refresh() async => calls.add('refresh');
  @override
  Future<void> cancelOAuth() async => calls.add('cancel');
}

class FakeConnectorHost implements ConnectorCardHost {
  FakeConnectorHost(this.result);
  ConnectorResolution result;
  @override
  Future<ConnectorResolution> resolve(GenUiCard card) async => result;
}
