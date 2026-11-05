// The real start screen, with a fake phone-agent inventory. No process starts.
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/fake_app.dart';
import '../fakes/fake_chats_host.dart';

class _StartAgents extends ChangeNotifier implements PhoneAgentsSource {
  _StartAgents({required this.installed})
    : _selected = installed ? 'claude' : openCodeChatAgentId;

  final bool installed;
  String _selected;

  @override
  bool get phoneAgentsAvailable => true;
  @override
  bool get phoneAgentsNeedRestart => false;
  @override
  String get selectedChatAgentId => _selected;

  @override
  List<AgentRow> get agentRows => [
    for (final descriptor in AgentCatalog.builtIn.agents)
      buildAgentRow(
        descriptor: descriptor,
        architecture: AgentArchitecture.arm64,
        serverCapabilities: catalogServerCapabilities,
        runtime: PhoneAgentRuntime(
          agentId: descriptor.id,
          installed:
              installed &&
              const ['claude', 'antigravity'].contains(descriptor.id),
          hostAvailable: true,
          architectureQualified: true,
          signInPhase: AgentSignInPhase.signedIn,
        ),
      ),
  ];

  @override
  List<ChatAgentChoice> get chatAgentChoices => [
    ChatAgentChoice(
      agentId: openCodeChatAgentId,
      name: 'OpenCode',
      iconKey: 'opencode',
      selected: _selected == openCodeChatAgentId,
    ),
    for (final row in agentRows)
      if (row.chatVisible)
        ChatAgentChoice(
          agentId: row.id,
          name: row.name,
          iconKey: row.iconKey,
          selected: _selected == row.id,
          row: row,
        ),
  ];

  @override
  Future<void> selectChatAgent(String agentId) async {
    _selected = agentId;
    notifyListeners();
  }

  @override
  Future<void> refreshAgentRows() async {}
  @override
  Future<List<AgentModelChoice>> agentModels(String agentId) async => const [];
  @override
  String? selectedAgentModel(String agentId) => null;
  @override
  List<PhoneAgentStatusLine> get agentStatusLines => const [];
  @override
  AgentSetupProgress get agentSetupProgress =>
      const AgentSetupProgress(agentId: '', phase: AgentSetupPhase.idle);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _StartHost extends FakeHost {
  _StartHost(this.phone)
    : super(FakeFeed(projects: [feedProject('shopfront')]));
  final _StartAgents phone;
  @override
  PhoneAgentsSource get agents => phone;
  @override
  Listenable get listenable => phone;
}

class _StartScene implements SceneResource {
  _StartScene(this.app, {required bool installed})
    : agents = _StartAgents(installed: installed);
  final FakeApp app;
  final _StartAgents agents;
  @override
  void dispose() {
    agents.dispose();
    app.dispose();
  }
}

Widget _start({required bool installed}) => FakeAppScene<_StartScene>(
  platform: FakePlatform.android,
  create: () async => _StartScene(
    await FakeApp.create(profiles: const []),
    installed: installed,
  ),
  builder: (context, scene) => ProviderScope(
    overrides: [chatsHostProvider.overrideWithValue(_StartHost(scene.agents))],
    child: const NewChatScreen(directory: '/root/projects/shopfront'),
  ),
);

CatalogComponent newChatScreen() => catalogComponent(
  area: 'Chats',
  screen: 'NewChatScreen',
  name: 'New conversation',
  useCases: [
    WidgetbookUseCase(
      name: 'Installed agents and Add an agent',
      builder: (_) => _start(installed: true),
    ),
    WidgetbookUseCase(
      name: 'No installed agents and Add an agent',
      builder: (_) => _start(installed: false),
    ),
  ],
);
