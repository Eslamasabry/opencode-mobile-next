part of '../connection.dart';

// Agents on this phone: one host and one auth owner inside the current profile
// (see domain/phone_agents_source.dart). STUB surface: the real wiring follows.

/// [ConnectionController]'s [PhoneAgentsSource] and [AgentChatFeedSource].
mixin _ConnectionControllerPhoneAgents on ChangeNotifier
    implements PhoneAgentsSource, AgentChatFeedSource {
  static const _notReady = ProductException(
    'Other agents are not ready on this phone yet.',
  );

  @override
  bool get phoneAgentsAvailable => false;

  @override
  bool get phoneAgentsNeedRestart => false;

  @override
  List<AgentRow> get agentRows => const [];

  @override
  List<ChatAgentChoice> get chatAgentChoices => const [];

  @override
  String get selectedChatAgentId => openCodeChatAgentId;

  @override
  Future<void> selectChatAgent(String agentId) async {
    if (agentId != openCodeChatAgentId) throw _notReady;
  }

  @override
  Future<void> refreshAgentRows() async {}

  @override
  AgentSetupProgress get agentSetupProgress =>
      const AgentSetupProgress(agentId: '', phase: AgentSetupPhase.idle);

  @override
  AgentPhoneCheckResult? agentPhoneCheck(String agentId) => null;

  @override
  Future<void> installAgent(String agentId) => throw _notReady;

  @override
  Future<void> cancelAgentInstall() async {}

  @override
  Future<AgentPhoneCheckResult> runAgentPhoneCheck(String agentId) =>
      throw _notReady;

  @override
  Future<void> resumeAgentHost() => throw _notReady;

  @override
  AgentSignInState? agentSignInState(String agentId) => null;

  @override
  Uri? agentSignInUrl(String agentId) => null;

  @override
  Future<void> startAgentSignIn(String agentId) => throw _notReady;

  @override
  Future<void> submitAgentSignInCode(String agentId, AgentSignInCode code) {
    code.clear();
    throw _notReady;
  }

  @override
  Future<void> cancelAgentSignIn(String agentId) async {}

  @override
  List<PhoneAgentStatusLine> get agentStatusLines => const [];

  @override
  Future<ChatFeedRoute> openChatFeedItem(ChatFeedItem item) => throw _notReady;

  @override
  AgentResumeNotice agentResumeNotice(ChatFeedItem item) =>
      const AgentResumeNotice(canReopen: true);

  @override
  Future<String> startNewChatReplacing(
    ChatFeedItem old, {
    required bool newChatAcknowledged,
  }) => throw _notReady;

  @override
  Future<void> closePhoneAgentsForSignInReset() async {}

  @override
  Future<String> startAgentChatIn(
    String directory, {
    required String agentId,
    String? firstPrompt,
  }) {
    if (agentId != openCodeChatAgentId) throw _notReady;
    return startChatIn(directory, firstPrompt: firstPrompt);
  }
}
