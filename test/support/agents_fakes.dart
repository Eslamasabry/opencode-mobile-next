import 'package:flutter/foundation.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/merged_chat_feed.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/domain/server_gateway/capabilities.dart';

/// How far an agent is, for [agentRowFor].
enum FakeAgentStage {
  notInstalled,
  needsCheck,
  signedOut,
  ready,
  readyVerifiedResume,
  stopped,
}

AgentRow agentRowFor(String id, FakeAgentStage stage) {
  final descriptor = AgentCatalog.builtIn.byId(id)!;
  final installed = stage != FakeAgentStage.notInstalled;
  final qualified = stage != FakeAgentStage.needsCheck;
  final signedIn =
      stage == FakeAgentStage.ready ||
      stage == FakeAgentStage.readyVerifiedResume;
  return buildAgentRow(
    descriptor: descriptor,
    architecture: AgentArchitecture.arm64,
    serverCapabilities: ServerCapabilities.allV1,
    runtime: PhoneAgentRuntime(
      agentId: id,
      installed: installed,
      hostAvailable: stage != FakeAgentStage.stopped,
      stoppedInBackground: stage == FakeAgentStage.stopped,
      architectureQualified: installed && qualified,
      signInPhase: signedIn
          ? AgentSignInPhase.signedIn
          : AgentSignInPhase.signedOut,
      capabilities: AgentCapabilities(
        resumeVerified: stage == FakeAgentStage.readyVerifiedResume,
      ),
    ),
  );
}

/// A [PhoneAgentsSource] with scripted truth that records every call.
class FakePhoneAgentsSource extends ChangeNotifier
    implements PhoneAgentsSource {
  FakePhoneAgentsSource({
    this.available = true,
    List<AgentRow>? rows,
    this.selected = openCodeChatAgentId,
  }) : _rows = rows ?? [agentRowFor('claude', FakeAgentStage.notInstalled)];

  bool available;
  bool needRestart = false;
  List<AgentRow> _rows;
  String selected;
  AgentSetupProgress progress = const AgentSetupProgress(
    agentId: '',
    phase: AgentSetupPhase.idle,
  );
  final checks = <String, AgentPhoneCheckResult>{};
  final signIn = <String, AgentSignInState>{};
  Uri? url;
  List<PhoneAgentStatusLine> lines = [];
  final noticeFor = <String, AgentResumeNotice>{};

  /// Runs after each phone check (a test moves the rows on).
  void Function()? afterCheck;

  /// When set, installing throws this.
  Object? installError;

  /// What the next phone check returns.
  AgentPhoneCheckResult? nextCheck;

  /// Every call, in order, as "name:arg".
  final calls = <String>[];
  final submitted = <String>[];

  set rows(List<AgentRow> value) {
    _rows = value;
    notifyListeners();
  }

  void change(void Function() edit) {
    edit();
    notifyListeners();
  }

  @override
  bool get phoneAgentsAvailable => available;

  @override
  bool get phoneAgentsNeedRestart => needRestart;

  @override
  List<AgentRow> get agentRows => available ? _rows : const [];

  @override
  List<ChatAgentChoice> get chatAgentChoices => [
    ChatAgentChoice(
      agentId: openCodeChatAgentId,
      name: 'OpenCode',
      iconKey: 'opencode',
      selected: selected == openCodeChatAgentId,
    ),
    for (final row in _rows)
      if (row.chatVisible)
        ChatAgentChoice(
          agentId: row.id,
          name: row.name,
          iconKey: row.iconKey,
          selected: selected == row.id,
          row: row,
        ),
  ];

  @override
  String get selectedChatAgentId => selected;

  @override
  Future<void> selectChatAgent(String agentId) async {
    calls.add('select:$agentId');
    final ok =
        agentId == openCodeChatAgentId ||
        _rows.any((row) => row.id == agentId && row.chatSelectable);
    if (!ok) throw StateError('not selectable');
    selected = agentId;
    notifyListeners();
  }

  @override
  Future<void> refreshAgentRows() async => calls.add('refresh');

  @override
  AgentSetupProgress get agentSetupProgress => progress;

  @override
  AgentPhoneCheckResult? agentPhoneCheck(String agentId) => checks[agentId];

  @override
  Future<void> installAgent(String agentId) async {
    calls.add('install:$agentId');
    final error = installError;
    if (error != null) throw error;
    progress = AgentSetupProgress(
      agentId: agentId,
      phase: AgentSetupPhase.installing,
      fraction: .4,
    );
    notifyListeners();
  }

  @override
  Future<void> cancelAgentInstall() async {
    calls.add('cancel-install');
    progress = const AgentSetupProgress(
      agentId: '',
      phase: AgentSetupPhase.idle,
    );
    notifyListeners();
  }

  @override
  Future<AgentPhoneCheckResult> runAgentPhoneCheck(String agentId) async {
    calls.add('check:$agentId');
    final result =
        nextCheck ??
        AgentPhoneCheckResult(
          agentId: agentId,
          architecture: AgentArchitecture.arm64,
          passed: true,
          completed: AgentPhoneCheckStep.values,
        );
    checks[agentId] = result;
    afterCheck?.call();
    notifyListeners();
    return result;
  }

  @override
  Future<void> resumeAgentHost() async {
    calls.add('resume');
    lines = [
      for (final line in lines)
        if (line.kind != PhoneAgentStatusLineKind.stopped) line,
    ];
    notifyListeners();
  }

  @override
  AgentSignInState? agentSignInState(String agentId) => signIn[agentId];

  @override
  Uri? agentSignInUrl(String agentId) => url;

  @override
  Future<void> startAgentSignIn(String agentId) async {
    calls.add('sign-in:$agentId');
    url = Uri.parse('https://claude.com/cai/oauth/authorize?code=true');
    signIn[agentId] = const AgentSignInState(
      phase: AgentSignInPhase.awaitingCode,
      method: AgentSignInMethod.browserOAuthHost,
      inspected: true,
    );
    notifyListeners();
  }

  @override
  Future<void> submitAgentSignInCode(
    String agentId,
    AgentSignInCode code,
  ) async {
    calls.add('code:$agentId');
    submitted.add(code.consume());
    signIn[agentId] = const AgentSignInState(
      phase: AgentSignInPhase.signedIn,
      method: AgentSignInMethod.browserOAuthHost,
      inspected: true,
    );
    url = null;
    _rows = [
      for (final row in _rows)
        row.id == agentId ? agentRowFor(agentId, FakeAgentStage.ready) : row,
    ];
    notifyListeners();
  }

  @override
  Future<void> cancelAgentSignIn(String agentId) async {
    calls.add('cancel-sign-in:$agentId');
    signIn.remove(agentId);
    url = null;
    notifyListeners();
  }

  @override
  List<PhoneAgentStatusLine> get agentStatusLines => lines;

  @override
  Future<ChatFeedRoute> openChatFeedItem(ChatFeedItem item) async {
    calls.add('open:${item.sessionID}');
    return ChatFeedRoute(
      sourceId: item.sourceId ?? 'opencode',
      sessionID: item.sessionID,
      directory: item.directory,
    );
  }

  @override
  AgentResumeNotice agentResumeNotice(ChatFeedItem item) =>
      noticeFor[item.sessionID] ?? const AgentResumeNotice(canReopen: true);

  @override
  Future<String> startNewChatReplacing(
    ChatFeedItem old, {
    required bool newChatAcknowledged,
  }) async {
    calls.add('replace:${old.sessionID}:$newChatAcknowledged');
    return 'ses_replacement';
  }

  @override
  Future<void> closePhoneAgentsForSignInReset() async =>
      calls.add('close-for-reset');
}
