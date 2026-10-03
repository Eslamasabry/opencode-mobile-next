import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/server_gateway/capabilities.dart';

void main() {
  final claude = AgentCatalog.builtIn.byId('claude')!;
  const allProof = AgentCapabilities(
    resumeVerified: true,
    modelList: true,
    permissions: true,
    images: true,
    cancel: true,
  );
  const server = ServerCapabilities(hostAgentPermissionActions: true);

  PhoneAgentRuntime runtime({
    String id = 'claude',
    bool installed = true,
    bool available = true,
    bool qualified = true,
    bool stopped = false,
    AgentSignInPhase phase = AgentSignInPhase.signedIn,
    AgentCapabilities capabilities = allProof,
    DateTime? resetAt,
  }) => PhoneAgentRuntime(
    agentId: id,
    installed: installed,
    hostAvailable: available,
    architectureQualified: qualified,
    stoppedInBackground: stopped,
    signInPhase: phase,
    capabilities: capabilities,
    resetAt: resetAt,
  );

  AgentRow row({
    PhoneAgentRuntime? fact,
    UnverifiedResumePolicy policy = UnverifiedResumePolicy.hide,
    ServerCapabilities serverCapabilities = server,
    AgentDescriptor? descriptor,
  }) => buildAgentRow(
    descriptor: descriptor ?? claude,
    architecture: AgentArchitecture.arm64,
    serverCapabilities: serverCapabilities,
    runtime: fact,
    unverifiedResumePolicy: policy,
  );

  test(
    'uninstalled Claude remains in setup with Install despite unknown resume',
    () {
      final result = row(fact: const PhoneAgentRuntime(agentId: 'claude'));
      expect(result.setupVisible, isTrue);
      expect(result.installable, isTrue);
      expect(result.status, PhoneAgentStatus.needsInstall);
      expect(result.fixAction, PhoneAgentFixAction.install);
      expect(result.chatVisible, isFalse);
      expect(result.chatSelectable, isFalse);
      expect(result.capabilities.resumeVerified, isFalse);
    },
  );

  test(
    'unknown or mismatched host facts cannot borrow another agent proof',
    () {
      for (final fact in [null, runtime(id: 'codex')]) {
        final result = row(fact: fact);
        expect(result.status, PhoneAgentStatus.unavailable);
        expect(result.hiddenReason, PhoneAgentHiddenReason.runtimeUnknown);
        expect(result.setupVisible, isTrue);
        expect(result.chatSelectable, isFalse);
        expect(result.capabilities.resumeVerified, isFalse);
        expect(result.capabilities.permissions, isFalse);
      }
    },
  );

  test('default runtime is unqualified and defaults every capability off', () {
    const fact = PhoneAgentRuntime(agentId: 'claude');
    expect(fact.architectureQualified, isFalse);
    expect(fact.capabilities.cancel, isFalse);
    expect(fact.capabilities.resumeVerified, isFalse);
    final result = row(fact: runtime(qualified: false));
    expect(result.status, PhoneAgentStatus.needsQualification);
    expect(result.fixAction, PhoneAgentFixAction.runPhoneCheck);
    expect(result.chatSelectable, isFalse);
    expect(result.capabilities.resumeVerified, isFalse);
  });

  test(
    'resume hide policy affects chat selection rather than setup inventory',
    () {
      final result = row(
        fact: runtime(capabilities: const AgentCapabilities()),
      );
      expect(result.setupVisible, isTrue);
      expect(result.status, PhoneAgentStatus.ready);
      expect(result.chatVisible, isFalse);
      expect(result.chatSelectable, isFalse);
      expect(result.hiddenReason, PhoneAgentHiddenReason.resumeUnverified);
      expect(result.resumeLabel, 'Can’t reopen old chats');
    },
  );

  test('label policy permits new chats while keeping resume disabled', () {
    final result = row(
      fact: runtime(capabilities: const AgentCapabilities()),
      policy: UnverifiedResumePolicy.label,
    );
    expect(result.chatVisible, isTrue);
    expect(result.chatSelectable, isTrue);
    expect(result.resumeLabel, 'Can’t reopen old chats');
    expect(result.capabilities.resumeVerified, isFalse);
    expect(result.capabilities.images, isFalse);
  });

  test(
    'runtime proof enables selection without inferring it from recipe or ID',
    () {
      final result = row(fact: runtime());
      expect(claude.capabilities.resumeVerified, isFalse);
      expect(result.chatSelectable, isTrue);
      expect(result.capabilities.resumeVerified, isTrue);
      expect(result.resumeLabel, isNull);
      expect(result.hiddenReason, isNull);
    },
  );

  test(
    'matching server gates narrow images and request-scoped permissions',
    () {
      final result = row(
        fact: runtime(),
        serverCapabilities: const ServerCapabilities(
          promptAttachments: false,
          hostAgentPermissionActions: false,
          cliSessionResume: false,
          serverCatalog: false,
        ),
      );
      expect(result.capabilities.images, isFalse);
      expect(result.capabilities.permissions, isFalse);
      // These gates do not describe agent model listing or durable ACP resume.
      expect(result.capabilities.resumeVerified, isTrue);
      expect(result.capabilities.modelList, isTrue);
      expect(result.capabilities.cancel, isTrue);
    },
  );

  test('sign-in and background stop override stale capability proofs', () {
    final signedOut = row(fact: runtime(phase: AgentSignInPhase.signedOut));
    expect(signedOut.status, PhoneAgentStatus.signedOut);
    expect(signedOut.fixAction, PhoneAgentFixAction.signIn);
    expect(signedOut.chatSelectable, isFalse);
    expect(signedOut.capabilities.cancel, isFalse);
    final stopped = row(fact: runtime(stopped: true, available: false));
    expect(stopped.status, PhoneAgentStatus.stoppedInBackground);
    expect(stopped.fixAction, PhoneAgentFixAction.resume);
    expect(stopped.chatSelectable, isFalse);
    final unavailable = row(fact: runtime(available: false));
    expect(unavailable.status, PhoneAgentStatus.unavailable);
    expect(unavailable.fixAction, PhoneAgentFixAction.resume);
  });

  test('usage limit retains only host reset time and never estimates one', () {
    final resetAt = DateTime.utc(2026, 10, 4, 12);
    final limited = row(
      fact: runtime(phase: AgentSignInPhase.limitReached, resetAt: resetAt),
    );
    expect(limited.status, PhoneAgentStatus.limitReached);
    expect(limited.chatSelectable, isFalse);
    expect(limited.resetAt, resetAt);
    expect(limited.fixAction, isNull);
    expect(
      row(fact: runtime(phase: AgentSignInPhase.limitReached)).resetAt,
      isNull,
    );
    expect(row(fact: runtime(resetAt: resetAt)).resetAt, isNull);
  });

  test(
    'unavailable recipes cannot become selectable from fabricated host proof',
    () {
      final unavailable = AgentDescriptor(
        id: 'future',
        name: 'Future',
        iconKey: 'future',
        route: AgentRoute.acpPaseo,
        providerId: 'future',
        signInMethod: AgentSignInMethod.none,
        availability: AgentAvailability.hidden,
        unavailableReason: AgentUnavailableReason.recipeUnverified,
        resumeReason: 'Needs a check.',
      );
      final result = row(
        descriptor: unavailable,
        fact: runtime(id: 'future'),
      );
      expect(result.chatSelectable, isFalse);
      expect(result.installable, isFalse);
      expect(result.status, PhoneAgentStatus.unavailable);
      expect(result.hiddenReason, PhoneAgentHiddenReason.catalogUnavailable);
    },
  );
}
