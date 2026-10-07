import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/host_agent_providers.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/server_gateway/capabilities.dart';
import 'package:opencode_mobile/paseo/host_agent_providers.dart';

void main() {
  Map<String, dynamic> fixture(String name) =>
      jsonDecode(
            File(
              'test/fixtures/host_agent_providers/$name.json',
            ).readAsStringSync(),
          )
          as Map<String, dynamic>;

  AgentSignInPhase phase(AgentAuthProbeResult probe) => switch (probe.state) {
    AgentAuthProbeState.signedIn => AgentSignInPhase.signedIn,
    AgentAuthProbeState.signedOut => AgentSignInPhase.signedOut,
    AgentAuthProbeState.error => AgentSignInPhase.failed,
  };

  AgentRow phoneRow(AgentDescriptor agent, AgentAuthProbeResult probe) =>
      buildAgentRow(
        descriptor: agent,
        architecture: AgentArchitecture.arm64,
        serverCapabilities: const ServerCapabilities(),
        runtime: PhoneAgentRuntime(
          agentId: agent.id,
          installed: true,
          architectureQualified: true,
          // The native helper is running. Whether it can fetch a provider's
          // models is a separate fact from whether the host process is alive.
          hostAvailable: true,
          signInPhase: phase(probe),
        ),
      );

  test('captured fx provider error reaches final signed-out phone row', () {
    final snapshot = fixture('fx-error-device')['snapshot'] as Map;
    final providerRow = paseoHostAgentCatalog(
      (snapshot['entries'] as List).cast<Map<String, dynamic>>(),
    ).providers.single;
    expect(providerRow.selectable, isFalse);
    expect(
      providerRow.availability,
      HostAgentProviderAvailability.needsHostSignIn,
    );
    final captured =
        jsonDecode(
              File(
                'test/fixtures/agent_auth/fx-signedOut-device.json',
              ).readAsStringSync(),
            )
            as Map<String, dynamic>;
    final row = phoneRow(
      AgentCatalog.builtIn.byId('fx')!,
      AgentAuthProbeResult.fromJson(captured['probe'] as Map<String, dynamic>),
    );
    expect(row.status, PhoneAgentStatus.signedOut);
    expect(row.fixAction, PhoneAgentFixAction.signIn);
    expect(row.chatSelectable, isFalse);
    expect(row.statusMessage, isNot(contains('Checking')));
  });

  test(
    'captured helper loading and final snapshots preserve readiness states',
    () {
      for (final entry in {
        'claude-loading-device': HostAgentProviderAvailability.checking,
        'claude-ready-device': HostAgentProviderAvailability.ready,
        'fx-loading-device': HostAgentProviderAvailability.checking,
        'fx-error-device': HostAgentProviderAvailability.needsHostSignIn,
      }.entries) {
        final snapshot = fixture(entry.key)['snapshot'] as Map;
        final providerRow = paseoHostAgentCatalog(
          (snapshot['entries'] as List).cast<Map<String, dynamic>>(),
        ).providers.single;
        expect(providerRow.availability, entry.value);
        // A ready model snapshot does not claim a signed-in account.
        if (entry.key == 'claude-ready-device') {
          expect(providerRow.loginState, HostAgentLoginState.unknown);
        }
      }
    },
  );

  test('captured CLI account states remain final while helper models load', () {
    final cases = fixture('synthetic-readiness-states')['cases'] as List;
    for (final capture in ['claude-signedIn', 'fx-signedOut']) {
      final captured =
          jsonDecode(
                File(
                  'test/fixtures/agent_auth/$capture-device.json',
                ).readAsStringSync(),
              )
              as Map<String, dynamic>;
      final agent = AgentCatalog.builtIn.byId(capture.split('-').first)!;
      final probe = AgentAuthProbeResult.fromJson(
        captured['probe'] as Map<String, dynamic>,
      );
      for (final state in cases.cast<Map<String, dynamic>>()) {
        final readiness = paseoHostAgentCatalog([
          {
            ...(state['entry'] as Map<String, dynamic>),
            'provider': agent.providerId,
          },
        ]).providers.single;
        final row = phoneRow(agent, probe);
        expect(
          row.status,
          probe.state == AgentAuthProbeState.signedIn
              ? PhoneAgentStatus.ready
              : PhoneAgentStatus.signedOut,
        );
        expect(row.statusMessage, isNot(contains('Checking')));
        // Even a loading model list or a misleading helper sign-in error
        // cannot replace the CLI's dedicated authentication response.
        if (state['name'] == 'loading') {
          expect(
            readiness.availability,
            HostAgentProviderAvailability.checking,
          );
        }
      }
    }
  });

  test(
    'all catalog phone agents reach final rows for explicit probe outcomes',
    () {
      // Synthetic outcomes cover the state contract only. They are deliberately
      // not stored or described as captured signed-in accounts for OW1 agents.
      for (final agent in AgentCatalog.builtIn.agents.where(
        (agent) =>
            agent.route == AgentRoute.paseoNative ||
            agent.route == AgentRoute.acpPaseo,
      )) {
        for (final entry in {
          const AgentAuthProbeResult(state: AgentAuthProbeState.signedIn):
              PhoneAgentStatus.ready,
          const AgentAuthProbeResult(state: AgentAuthProbeState.signedOut):
              PhoneAgentStatus.signedOut,
          const AgentAuthProbeResult.failed(AgentAuthProbeError.timedOut):
              PhoneAgentStatus.unavailable,
        }.entries) {
          final row = phoneRow(agent, entry.key);
          expect(row.status, entry.value, reason: agent.id);
          expect(row.statusMessage, isNot(contains('Checking')));
        }
      }
    },
  );

  HostAgentProvider provider({
    HostAgentProviderAvailability availability =
        HostAgentProviderAvailability.ready,
    HostAgentProviderHiddenReason? hiddenReason,
    HostAgentLoginState loginState = HostAgentLoginState.ready,
    HostAgentResumeSupport resumeSupport = HostAgentResumeSupport.loadSession,
    String id = 'gemini',
    List<HostAgentModel> models = const [],
  }) => HostAgentProvider(
    id: id,
    displayName: 'Host agent',
    availability: availability,
    hiddenReason: hiddenReason,
    loginState: loginState,
    resumeSupport: resumeSupport,
    models: models,
  );

  test('missing restoration does not block a host-ready provider', () {
    final unknownLogin = provider(loginState: HostAgentLoginState.unknown);
    expect(unknownLogin.selectable, true);
    expect(unknownLogin.loginState, HostAgentLoginState.unknown);
    expect(
      () => provider(loginState: HostAgentLoginState.needsHostSignIn),
      throwsArgumentError,
    );
    for (final support in [
      HostAgentResumeSupport.unknown,
      HostAgentResumeSupport.unsupported,
    ]) {
      final candidate = provider(resumeSupport: support);
      expect(candidate.selectable, true);
      expect(candidate.resumeVerified, false);
      expect(candidate.resumeLabel, "Can't reopen old chats");
      expect(candidate.resumeNote, 'Starts a new chat');
    }
    expect(provider().resumeLabel, isNull);
    expect(
      provider(resumeSupport: HostAgentResumeSupport.listAndLoad).selectable,
      true,
    );
  });

  test('missing sign-in still blocks without restoration proof', () {
    final needsSignIn = provider(
      availability: HostAgentProviderAvailability.needsHostSignIn,
      loginState: HostAgentLoginState.needsHostSignIn,
      resumeSupport: HostAgentResumeSupport.unknown,
    );
    expect(needsSignIn.selectable, false);
    expect(needsSignIn.reason, 'Sign in on your computer first.');
    expect(needsSignIn.resumeLabel, "Can't reopen old chats");
  });

  test('hidden and checking agents remain unavailable despite known login', () {
    for (final reason in [
      HostAgentProviderHiddenReason.resumeUnverified,
      HostAgentProviderHiddenReason.resumeUnsupported,
    ]) {
      expect(
        () => provider(
          availability: HostAgentProviderAvailability.hidden,
          hiddenReason: reason,
        ),
        throwsArgumentError,
      );
    }
    final hidden = provider(
      availability: HostAgentProviderAvailability.hidden,
      hiddenReason: HostAgentProviderHiddenReason.disabled,
    );
    final checking = provider(
      availability: HostAgentProviderAvailability.checking,
    );
    expect(hidden.selectable, isFalse);
    expect(checking.selectable, isFalse);
    expect(
      () => provider(availability: HostAgentProviderAvailability.hidden),
      throwsArgumentError,
    );
    expect(
      () => provider(hiddenReason: HostAgentProviderHiddenReason.disabled),
      throwsArgumentError,
    );
  });

  test('catalog and model lists cannot be changed by caller mutation', () {
    final models = <HostAgentModel>[
      const HostAgentModel(id: 'model-1', name: 'Model one'),
    ];
    final ready = provider(models: models);
    models.clear();
    expect(ready.models.single.id, 'model-1');
    expect(ready.models.clear, throwsUnsupportedError);

    final source = <HostAgentProvider>[
      ready,
      provider(availability: HostAgentProviderAvailability.checking),
    ];
    final catalog = HostAgentProviderCatalog(providers: source);
    source.clear();
    expect(catalog.providers.length, 2);
    expect(catalog.selectable.single, same(ready));
    expect(catalog.providers.clear, throwsUnsupportedError);
    expect(catalog.selectable.clear, throwsUnsupportedError);
  });

  test('sign-in details use only fixed app commands', () {
    expect(provider().hostSignInCommand, 'gemini');
    expect(provider(id: 'omp').hostSignInCommand, 'omp');
    expect(provider(id: 'fx').hostSignInCommand, 'fx login');
    expect(provider(id: 'unknown-agent').hostSignInCommand, isNull);
    expect(provider(id: 'fx; arbitrary command').hostSignInCommand, isNull);
    expect(
      provider().signInMessage,
      'Sign in on your computer: run gemini there, then check again.',
    );
    expect(
      provider(id: 'omp').signInMessage,
      'Run omp on your computer, use /login to sign in, then check again.',
    );
  });

  test('permission choices expose fixed once-only labels and copied lists', () {
    const allow = HostAgentPermissionChoice(
      actionId: 'host-action-allow',
      behavior: HostAgentPermissionBehavior.allowOnce,
    );
    const reject = HostAgentPermissionChoice(
      actionId: 'host-action-reject',
      behavior: HostAgentPermissionBehavior.rejectOnce,
    );
    final choices = [allow, reject];
    final request = HostAgentPermissionRequest(
      requestId: 'request-1',
      sessionId: 'session-1',
      choices: choices,
    );
    choices.clear();
    expect(request.choices.map((choice) => choice.label), [
      'Allow once',
      'Reject once',
    ]);
    expect(request.choices.clear, throwsUnsupportedError);
    expect(
      () => HostAgentPermissionRequest(
        requestId: 'request-2',
        sessionId: 'session-1',
        choices: [allow, allow],
      ),
      throwsArgumentError,
    );
    expect(
      () => HostAgentPermissionRequest(
        requestId: 'request-3',
        sessionId: 'session-1',
        choices: [
          const HostAgentPermissionChoice(
            actionId: ' ',
            behavior: HostAgentPermissionBehavior.allowOnce,
          ),
        ],
      ),
      throwsArgumentError,
    );
  });
}
