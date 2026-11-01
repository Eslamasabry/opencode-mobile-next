import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/server_gateway/capabilities.dart';
import 'package:opencode_mobile/platform/network.dart';
import 'package:opencode_mobile/state/phone_agents_auto_update.dart';

void main() {
  bool decide({
    bool owes = true,
    bool busy = false,
    bool check = false,
    bool setup = false,
    bool attempted = false,
  }) => shouldAutoUpdateAgent(
    owesUpdate: owes,
    busy: busy,
    checkRunning: check,
    setupJobRunning: setup,
    attemptedThisRun: attempted,
  );

  group('shouldAutoUpdateAgent', () {
    test('an installed agent behind the pin updates when nothing runs', () {
      expect(decide(), isTrue);
    });
    test('the same version as the pin owes nothing', () {
      expect(decide(owes: false), isFalse);
    });
    test('work in progress, a running check or another setup job wait', () {
      expect(decide(busy: true), isFalse);
      expect(decide(check: true), isFalse);
      expect(decide(setup: true), isFalse);
    });
    test('one try per pin per app run', () {
      expect(decide(attempted: true), isFalse);
    });
  });

  group('the Wi-Fi rule', () {
    const wifi = NetworkReading(
      status: NetworkStatus.online,
      transport: NetworkTransport.wifi,
      metered: false,
    );
    const mobile = NetworkReading(
      status: NetworkStatus.online,
      transport: NetworkTransport.mobile,
      metered: true,
    );
    const hotspot = NetworkReading(
      status: NetworkStatus.online,
      transport: NetworkTransport.wifi,
      metered: true,
    );
    test('a big download waits for Wi-Fi, a small one does not', () {
      const big = autoUpdateMobileLimitBytes + 1;
      expect(autoUpdateNetworkAllowed(downloadBytes: big, network: wifi), true);
      expect(
        autoUpdateNetworkAllowed(downloadBytes: big, network: mobile),
        isFalse,
      );
      expect(
        autoUpdateNetworkAllowed(downloadBytes: big, network: hotspot),
        isFalse,
        reason: 'a metered Wi-Fi (a phone hotspot) is mobile data too',
      );
      expect(
        autoUpdateNetworkAllowed(
          downloadBytes: autoUpdateMobileLimitBytes,
          network: mobile,
        ),
        isTrue,
      );
      expect(
        autoUpdateNetworkAllowed(
          downloadBytes: big,
          network: NetworkReading.unknown,
        ),
        isTrue,
      );
    });
    test('the decision waits when the network is not allowed', () {
      expect(decide(), isTrue);
      expect(
        shouldAutoUpdateAgent(
          owesUpdate: true,
          busy: false,
          checkRunning: false,
          setupJobRunning: false,
          attemptedThisRun: false,
          networkAllowed: false,
        ),
        isFalse,
      );
    });
  });

  group('phoneAgentOwesUpdate', () {
    test('an agent that runs on its older version still owes the update', () {
      final row = buildAgentRow(
        descriptor: AgentCatalog.builtIn.byId('kilo')!,
        architecture: AgentArchitecture.x64,
        serverCapabilities: const ServerCapabilities(),
        runtime: const PhoneAgentRuntime(
          agentId: 'kilo',
          installed: true,
          hostAvailable: true,
          architectureQualified: true,
          behindPin: true,
          signInPhase: AgentSignInPhase.signedIn,
        ),
      );
      expect(row.behindPin, isTrue);
      expect(row.status, PhoneAgentStatus.ready, reason: 'work runs on it');
      expect(phoneAgentOwesUpdate(row), isTrue);
    });
    AgentRow row(PhoneAgentRuntime? runtime) => buildAgentRow(
      descriptor: AgentCatalog.builtIn.byId('kilo')!,
      architecture: AgentArchitecture.x64,
      serverCapabilities: const ServerCapabilities(),
      runtime: runtime,
    );
    test('only an installed agent whose version or helper moved', () {
      for (final reason in ['agentVersion', 'helperVersion']) {
        expect(
          phoneAgentOwesUpdate(
            row(
              PhoneAgentRuntime(
                agentId: 'kilo',
                installed: true,
                checkRequiredReason: reason,
              ),
            ),
          ),
          isTrue,
          reason: reason,
        );
      }
      for (final reason in ['firstCheck', 'installationChanged', null]) {
        expect(
          phoneAgentOwesUpdate(
            row(
              PhoneAgentRuntime(
                agentId: 'kilo',
                installed: true,
                checkRequiredReason: reason,
              ),
            ),
          ),
          isFalse,
          reason: '$reason',
        );
      }
      expect(phoneAgentOwesUpdate(row(null)), isFalse);
    });
  });
}
