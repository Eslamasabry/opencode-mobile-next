import 'package:flutter/services.dart';

import '../../domain/agent_catalog.dart';
import '../../domain/agent_sign_in.dart';
import '../builtin_linux.dart';

/// The dedicated native auth process, never the general-purpose local terminal.
/// Native owns CLI credentials, PTY parsing and exact-process cancellation.
final class ChannelAgentSignInHost implements AgentSignInHost {
  ChannelAgentSignInHost({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel(BuiltinLinux.channelName);

  final MethodChannel _channel;

  Map<String, Object> _identity(AgentSignInRun run) => {
    'profileId': run.profileId,
    'agentId': run.agentId,
    'runId': run.runId,
    'method': run.method.name,
  };

  Future<Object?> _invoke(String method, Map<String, Object> arguments) async {
    try {
      return await _channel.invokeMethod<Object?>(method, arguments);
    } on MissingPluginException {
      throw const AgentSignInException(AgentSignInFailure.unavailable);
    } on PlatformException catch (error) {
      // Never forward native message/details: they may contain CLI output.
      throw AgentSignInException(switch (error.code) {
        'auth_unavailable' => AgentSignInFailure.unavailable,
        'auth_cancelled' => AgentSignInFailure.cancelled,
        'auth_stale_run' => AgentSignInFailure.staleRun,
        'auth_invalid_code' => AgentSignInFailure.invalidCode,
        'auth_rejected' => AgentSignInFailure.authenticationRejected,
        _ => AgentSignInFailure.hostUnavailable,
      });
    } catch (_) {
      throw const AgentSignInException(AgentSignInFailure.hostUnavailable);
    }
  }

  AgentSignInHostUpdate _parse(AgentSignInRun run, Object? raw) {
    const allowed = {'runId', 'phase', 'url', 'failure', 'resetAt'};
    if (raw is! Map ||
        raw.keys.any((key) => !allowed.contains(key)) ||
        raw['runId'] != run.runId) {
      throw const AgentSignInException(AgentSignInFailure.invalidResponse);
    }
    final phase = AgentSignInPhase.values
        .where((value) => value.name == raw['phase'])
        .firstOrNull;
    if (phase == null) {
      throw const AgentSignInException(AgentSignInFailure.invalidResponse);
    }
    final rawFailure = raw['failure'];
    final failure = AgentSignInFailure.values
        .where((value) => value.name == rawFailure)
        .firstOrNull;
    if (rawFailure != null && failure == null) {
      throw const AgentSignInException(AgentSignInFailure.invalidResponse);
    }
    final rawUrl = raw['url'];
    if (rawUrl != null && rawUrl is! String) {
      throw const AgentSignInException(AgentSignInFailure.invalidResponse);
    }
    final rawReset = raw['resetAt'];
    DateTime? resetAt;
    if (rawReset != null) {
      if (rawReset is! String ||
          !RegExp(
            r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$',
          ).hasMatch(rawReset)) {
        throw const AgentSignInException(AgentSignInFailure.invalidResponse);
      }
      resetAt = DateTime.tryParse(rawReset);
      if (resetAt == null ||
          resetAt.toIso8601String().substring(0, 19) !=
              rawReset.substring(0, 19)) {
        throw const AgentSignInException(AgentSignInFailure.invalidResponse);
      }
    }
    return AgentSignInHostUpdate(
      runId: run.runId,
      phase: phase,
      authorizationUrl: rawUrl is String
          ? AgentAuthorizationUrl.validate(run.agentId, rawUrl)
          : null,
      failure: failure,
      resetAt: resetAt,
    );
  }

  Future<AgentSignInHostUpdate> _request(
    String method,
    AgentSignInRun run,
  ) async {
    if (run.method != AgentSignInMethod.browserOAuthHost ||
        run.agentId != 'claude') {
      throw const AgentSignInException(AgentSignInFailure.unavailable);
    }
    return _parse(run, await _invoke(method, _identity(run)));
  }

  @override
  Future<AgentSignInHostUpdate> start(AgentSignInRun run) =>
      _request('startAgentSignIn', run);

  @override
  Future<AgentSignInHostUpdate> readChallenge(AgentSignInRun run) =>
      _request('readAgentSignInChallenge', run);

  @override
  Future<AgentSignInHostUpdate> status(AgentSignInRun run) =>
      _request('agentSignInStatus', run);

  @override
  Future<AgentSignInHostUpdate> submitCode(
    AgentSignInRun run,
    AgentSignInCode code,
  ) async {
    if (run.method != AgentSignInMethod.browserOAuthHost ||
        run.agentId != 'claude') {
      code.clear();
      throw const AgentSignInException(AgentSignInFailure.unavailable);
    }
    final arguments = _identity(run);
    arguments['code'] = code.consume();
    try {
      return _parse(run, await _invoke('submitAgentSignInCode', arguments));
    } finally {
      arguments.remove('code');
      code.clear();
    }
  }

  @override
  Future<void> cancelAndDrain(AgentSignInRun run) async {
    final raw = await _invoke('cancelAgentSignIn', _identity(run));
    if (raw is! Map ||
        raw.keys.any((key) => !{'runId', 'drained'}.contains(key)) ||
        raw['runId'] != run.runId ||
        raw['drained'] != true) {
      throw const AgentSignInException(
        AgentSignInFailure.cancellationUnconfirmed,
      );
    }
  }
}
