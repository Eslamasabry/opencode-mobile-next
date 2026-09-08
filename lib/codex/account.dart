import 'dart:async';

import '../domain/agent_account.dart';
import 'transport.dart';

/// Stable account API in the locally verified 0.153.4 schema. This adapter
/// deliberately does not opt into experimental external-token authentication.
class CodexAccountSession extends AgentAccountSession {
  final CodexTransport transport;
  final bool Function() scopeValid;
  final int _epoch;
  final _events = StreamController<AccountEvent>.broadcast(sync: true);
  late final StreamSubscription<CodexRpcEvent> _rpc;
  late final StreamSubscription<int> _disconnect;
  bool _closed = false;
  bool _starting = false;
  bool _cancelRequested = false;
  String? _loginId;
  bool _untrackedStart = false;
  final _earlyCompletions = <String, bool>{};

  CodexAccountSession(this.transport, this.scopeValid)
    : _epoch = transport.epoch {
    _rpc = transport.events.listen(_onEvent);
    _disconnect = transport.disconnects.listen((_) {
      _loginId = null;
      _earlyCompletions.clear();
      if (!_closed) {
        _events.add(const AccountEvent(AccountEventKind.disconnected));
      }
    });
  }

  bool get _sameScope =>
      scopeValid() && transport.connected && transport.epoch == _epoch;
  @override
  bool get active => !_closed && _sameScope;
  @override
  bool get supported => transport.accountApiSupported;
  @override
  Stream<AccountEvent> get events => _events.stream;

  void _check() {
    if (!active) {
      throw const AgentAccountException(AgentAccountFailure.disconnected);
    }
    if (!supported) {
      throw const AgentAccountException(AgentAccountFailure.unavailable);
    }
  }

  Future<Map<String, dynamic>> _call(
    String method,
    Map<String, dynamic> params, {
    bool mutation = false,
    bool finishLogin = false,
  }) async {
    _check();
    try {
      final result = await transport.requestInEpoch(
        method,
        params,
        epoch: _epoch,
        mutation: mutation,
      );
      if (!finishLogin || !_sameScope) _check();
      return result;
    } on CodexFailure catch (error) {
      throw AgentAccountException(switch (error.kind) {
        CodexFailureKind.deliveryUnknown => AgentAccountFailure.uncertain,
        CodexFailureKind.invalidResponse => AgentAccountFailure.invalidResponse,
        CodexFailureKind.disconnected ||
        CodexFailureKind.staleRequest ||
        CodexFailureKind.scopeMismatch => AgentAccountFailure.disconnected,
        _ => AgentAccountFailure.unavailable,
      });
    }
  }

  @override
  Future<AgentAccount> read() async {
    final result = await _call('account/read', {'refreshToken': false});
    final required = result['requiresOpenaiAuth'];
    if (required is! bool || !result.containsKey('account')) throw _invalid;
    final account = result['account'];
    if (account == null) return AgentAccount(requiresSignIn: required);
    if (account is! Map || account['type'] is! String) throw _invalid;
    return AgentAccount(
      type: _text(account['type'], 80),
      email: _optionalText(account['email'], 320),
      plan: _optionalText(account['planType'], 100),
      requiresSignIn: required,
    );
  }

  @override
  Future<List<AccountRateBucket>> rateLimits() async {
    final result = await _call('account/rateLimits/read', {});
    final multi = result['rateLimitsByLimitId'];
    if (multi != null && multi is! Map) throw _invalid;
    final List<dynamic> values;
    if (multi is Map && multi.isNotEmpty) {
      if (multi.length > 32) throw _invalid;
      values = multi.values.toList();
    } else {
      values = [result['rateLimits']];
    }
    return values
        .map((value) {
          if (value is! Map) throw _invalid;
          return AccountRateBucket(
            _optionalText(value['limitName'] ?? value['limitId'], 100),
            _window(value['primary']),
            _window(value['secondary']),
          );
        })
        .toList(growable: false);
  }

  @override
  Future<AccountTokenUsage> usage() async {
    final result = await _call('account/usage/read', {});
    final summary = result['summary'];
    if (summary is! Map) throw _invalid;
    return AccountTokenUsage(
      lifetimeTokens: _number(summary['lifetimeTokens']),
      peakDailyTokens: _number(summary['peakDailyTokens']),
    );
  }

  @override
  Future<AccountDeviceCode> startDeviceLogin() async {
    _check();
    if (_starting || _loginId != null || _untrackedStart) {
      throw const AgentAccountException(AgentAccountFailure.unavailable);
    }
    _starting = true;
    _cancelRequested = false;
    _earlyCompletions.clear();
    var requested = false;
    var receiptKnown = false;
    try {
      // Do not knowingly replace an account authenticated by another client.
      final current = await read();
      if (current.signedIn || !current.requiresSignIn || _cancelRequested) {
        throw const AgentAccountException(AgentAccountFailure.unavailable);
      }
      requested = true;
      final result = await _call(
        'account/login/start',
        {'type': 'chatgptDeviceCode'},
        mutation: true,
        finishLogin: true,
      );
      _loginId = _text(result['loginId'], 256);
      receiptKnown = true;
      final completed = _earlyCompletions.remove(_loginId);
      if (completed != null) {
        _loginId = null;
        if (active) {
          _events.add(
            AccountEvent(AccountEventKind.loginCompleted, success: completed),
          );
        }
        // The matching notification already settled the login. Returning a
        // device code here could reopen a completed flow in a late UI callback.
        throw const AgentAccountException(AgentAccountFailure.unavailable);
      }
      if (_cancelRequested) {
        final confirmed = await _cancelOwned();
        if (active) {
          _events.add(
            AccountEvent(AccountEventKind.loginCancelled, success: confirmed),
          );
        }
        throw const AgentAccountException(AgentAccountFailure.unavailable);
      }
      try {
        if (result['type'] != 'chatgptDeviceCode') throw _invalid;
        final url = _text(result['verificationUrl'], 2048);
        final uri = Uri.tryParse(url);
        // Device codes must never be handed to a lookalike login host or put
        // in a query string. The official runtime returns this exact route.
        if (uri == null ||
            uri.scheme != 'https' ||
            uri.host != 'auth.openai.com' ||
            uri.path != '/codex/device' ||
            uri.userInfo.isNotEmpty ||
            uri.hasQuery ||
            uri.hasFragment ||
            (uri.hasPort && uri.port != 443)) {
          throw _invalid;
        }
        final code = _text(result['userCode'], 64);
        return AccountDeviceCode(url, code);
      } catch (_) {
        if (!await _cancelOwned()) {
          throw const AgentAccountException(AgentAccountFailure.uncertain);
        }
        rethrow;
      }
    } on AgentAccountException catch (error) {
      if (requested &&
          !receiptKnown &&
          {
            AgentAccountFailure.uncertain,
            AgentAccountFailure.invalidResponse,
          }.contains(error.kind)) {
        _untrackedStart = true;
        throw const AgentAccountException(AgentAccountFailure.uncertain);
      }
      rethrow;
    } finally {
      _starting = false;
      _earlyCompletions.clear();
      if (_cancelRequested && !requested && active) {
        _events.add(
          const AccountEvent(AccountEventKind.loginCancelled, success: true),
        );
      }
    }
  }

  Future<bool> _cancelOwned() async {
    if (!_sameScope) return false;
    final id = _loginId;
    if (id == null) return !_starting && !_untrackedStart;
    try {
      final result = await transport.requestInEpoch(
        'account/login/cancel',
        {'loginId': id},
        epoch: _epoch,
        mutation: true,
      );
      final confirmed = result['status'] == 'canceled';
      if (confirmed && _loginId == id) {
        _loginId = null;
      }
      return confirmed;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> cancelLogin() async {
    _cancelRequested = true;
    if (_starting && _loginId == null) return false;
    return _cancelOwned();
  }

  void _onEvent(CodexRpcEvent event) {
    if (!active || event.epoch != _epoch) return;
    if (event.method == 'account/updated') {
      if (_starting || _loginId != null) {
        _cancelRequested = true;
      }
      _events.add(const AccountEvent(AccountEventKind.changed));
    } else if (event.method == 'account/rateLimits/updated') {
      // Re-read instead of merging uncorrelated account snapshots.
      _events.add(const AccountEvent(AccountEventKind.limitsChanged));
    } else if (event.method == 'account/login/completed') {
      final id = event.params['loginId'];
      final success = event.params['success'];
      if (id is! String || success is! bool) return;
      if (id == _loginId) {
        _loginId = null;
        _events.add(
          AccountEvent(AccountEventKind.loginCompleted, success: success),
        );
      } else if (_starting &&
          id.length <= 256 &&
          _earlyCompletions.length < 8) {
        _earlyCompletions[id] = success;
      }
    }
  }

  @override
  Future<void> close() async {
    if (_closed) return;
    // Set the cancellation flag synchronously, including during login/start.
    // Scope loss forbids a request against a replacement connection.
    _cancelRequested = true;
    final cancel = _cancelOwned();
    _closed = true;
    await _rpc.cancel();
    await _disconnect.cancel();
    await cancel;
    await _events.close();
  }
}

const _invalid = AgentAccountException(AgentAccountFailure.invalidResponse);
String _text(dynamic value, int max) {
  if (value is! String ||
      value.isEmpty ||
      value.length > max ||
      RegExp(r'[\x00-\x1f\x7f]').hasMatch(value)) {
    throw _invalid;
  }
  return value;
}

String? _optionalText(dynamic value, int max) =>
    value == null ? null : _text(value, max);
int? _number(dynamic value) {
  if (value == null) return null;
  if (value is! int || value < 0) throw _invalid;
  return value;
}

AccountRateWindow? _window(dynamic value) {
  if (value == null) return null;
  if (value is! Map) throw _invalid;
  final used = _number(value['usedPercent']);
  if (used == null) throw _invalid;
  final reset = _number(value['resetsAt']);
  if (reset != null && reset > 8640000000000) throw _invalid;
  return AccountRateWindow(
    used,
    _number(value['windowDurationMins']),
    reset == null ? null : DateTime.fromMillisecondsSinceEpoch(reset * 1000),
  );
}
