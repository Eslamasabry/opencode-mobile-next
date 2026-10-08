import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:opencode_mobile/builtin/agents/phone_agents_host.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'bb8_native_ready.dart';

/// Prevent release test reporting from serializing exceptions or account data.
class _PrivateFailure extends FlutterErrorDetails {
  _PrivateFailure(this.phase) : super(exception: 'bb8_private_failure');
  final String phase;

  @override
  String toString({DiagnosticLevel minLevel = DiagnosticLevel.info}) =>
      'bb8_phase_$phase';
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  var phase = 'initializing';
  final reporter = reportTestException;
  reportTestException = (details, description) =>
      reporter(_PrivateFailure(phase), 'BB8 private auth smoke');
  PerfTrace.logSink = null;

  testWidgets('BB8 real private authentication channel', (tester) async {
    const profileId = String.fromEnvironment('BB8_PROFILE_ID');
    phase = 'profile';
    expect(RegExp(r'^[A-Za-z0-9_-]{1,80}$').hasMatch(profileId), isTrue);
    phase = 'ready';
    await showBb8FrameAndWaitForNativeReady(
      firstFrame: () => tester.pumpWidget(const SizedBox.shrink()),
    );
    // The adapter uses real native channels and authored production scripts.
    // This store is only its unused setup state, never the person's settings.
    SharedPreferences.setMockInitialValues({});
    final host = BuiltinPhoneAgents(
      profileId: profileId,
      prefs: await SharedPreferences.getInstance(),
    );
    try {
      phase = 'claude_probe';
      final claude = await host.probeSignIn('claude');
      expect(claude.state == AgentAuthProbeState.signedIn, isTrue);
      expect(claude.error == null, isTrue);
      final hasAccount = claude.accountDisplayName != null;

      phase = 'fx_probe';
      final fx = await host.probeSignIn('fx');
      expect(fx.state == AgentAuthProbeState.signedOut, isTrue);
      expect(fx.accountDisplayName == null && fx.error == null, isTrue);
      expect(host.supportsSignOut('fx'), isTrue);

      // This is the ONLY logout in this target. Claude credentials stay put.
      phase = 'fx_logout';
      final logout = await host.signOut('fx');
      expect(logout.state == AgentAuthProbeState.signedOut, isTrue);
      expect(logout.accountDisplayName == null && logout.error == null, isTrue);

      phase = 'fx_after_logout';
      final afterLogout = await host.probeSignIn('fx');
      expect(afterLogout.state == AgentAuthProbeState.signedOut, isTrue);
      expect(
        afterLogout.accountDisplayName == null && afterLogout.error == null,
        isTrue,
      );

      phase = 'receipt';
      final output = await getExternalStorageDirectory();
      expect(output != null, isTrue);
      await File('${output!.path}/bb8-agent-auth.json').writeAsString(
        jsonEncode({
          'claude': 'signedIn',
          'claudeAccountLabel': hasAccount ? 'present' : 'absent',
          'fx': 'signedOut',
          'fxLogout': 'signedOut',
          'fxAfterLogout': 'signedOut',
        }),
        flush: true,
      );
    } finally {
      // dispose closes the adapter's stream; it does not stop shared services.
      await host.dispose();
    }
  }, timeout: const Timeout(Duration(seconds: 90)));
}
