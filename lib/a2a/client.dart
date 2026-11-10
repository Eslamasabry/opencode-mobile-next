import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:uuid/uuid.dart';

import '../domain/external_agent.dart';

/// Official A2A 1.0 JSON-RPC binding, protocol release 1.0.1. No legacy fallback.
class A2aClient implements ExternalAgentGateway {
  final HttpClient _http;
  A2aClient({HttpClient? http}) : _http = http ?? HttpClient() {
    _http.connectionTimeout = const Duration(seconds: 8);
  }
  @override
  void close() => _http.close(force: true);

  static Uri address(String raw) {
    if (raw.length > 2048) {
      throw const ExternalAgentException(ExternalAgentIssue.address);
    }
    final uri = Uri.tryParse(raw.trim());
    if (uri == null ||
        !uri.hasAuthority ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasFragment ||
        uri.hasQuery ||
        !{'http', 'https'}.contains(uri.scheme) ||
        (uri.scheme == 'http' &&
            !{'localhost', '127.0.0.1', '::1'}.contains(uri.host))) {
      throw const ExternalAgentException(ExternalAgentIssue.address);
    }
    return uri;
  }

  Future<Map<String, dynamic>> _request(
    Uri url, {
    Map<String, dynamic>? body,
    String? bearer,
  }) async {
    HttpClientRequest? pending;
    var stopped = false;
    try {
      return await (() async {
        final request = await _http.openUrl(body == null ? 'GET' : 'POST', url);
        pending = request;
        if (stopped) {
          request.abort();
          throw const ExternalAgentException(ExternalAgentIssue.unavailable);
        }
        request.followRedirects = false;
        request.headers.set(HttpHeaders.acceptHeader, 'application/json');
        if (body != null) {
          request.headers.contentType = ContentType.json;
          request.headers.set('A2A-Version', '1.0');
        }
        if (bearer != null) {
          request.headers.set(
            HttpHeaders.authorizationHeader,
            'Bearer $bearer',
          );
        }
        if (body != null) request.write(jsonEncode(body));
        final response = await request.close();
        if (response.statusCode == 401 || response.statusCode == 403) {
          throw const ExternalAgentException(ExternalAgentIssue.authentication);
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          throw const ExternalAgentException(ExternalAgentIssue.unavailable);
        }
        final bytes = <int>[];
        await for (final chunk in response) {
          if (bytes.length + chunk.length > 1024 * 1024) {
            throw const ExternalAgentException(
              ExternalAgentIssue.invalidResponse,
            );
          }
          bytes.addAll(chunk);
        }
        final json = jsonDecode(utf8.decode(bytes));
        if (json is! Map<String, dynamic>) {
          throw const ExternalAgentException(
            ExternalAgentIssue.invalidResponse,
          );
        }
        return json;
      })().timeout(const Duration(seconds: 15));
    } on ExternalAgentException {
      rethrow;
    } catch (_) {
      throw const ExternalAgentException(ExternalAgentIssue.unavailable);
    } finally {
      stopped = true;
      pending?.abort();
    }
  }

  @override
  Future<ExternalAgentCard> discover(String raw) async {
    var uri = address(raw);
    if (uri.path.isEmpty || uri.path == '/') {
      uri = uri.replace(path: '/.well-known/agent-card.json');
    }
    final j = await _request(uri);
    var supported = true;
    String endpoint = '';
    for (final entry in _list(j['supportedInterfaces'], 20)) {
      final item = _map(entry);
      if (item['protocolBinding'] != 'JSONRPC' ||
          item['protocolVersion'] != '1.0' ||
          (item['tenant'] != null && item['tenant'] != '')) {
        continue;
      }
      try {
        final target = address(_text(item['url'], 2048));
        if (target.origin == uri.origin) {
          endpoint = target.toString();
          break;
        }
      } on ExternalAgentException {
        continue;
      }
    }
    if (endpoint.isEmpty) supported = false;
    final capabilities = _map(j['capabilities']);
    if (_list(
      capabilities['extensions'],
      20,
    ).any((e) => _map(e)['required'] == true)) {
      supported = false;
    }
    final schemes = _map(j['securitySchemes']);
    ExternalAgentAuth? supportedAuth(dynamic rawRequirements) {
      final requirements = _list(rawRequirements, 20);
      if (requirements.isEmpty) return ExternalAgentAuth.none;
      for (final requirement in requirements) {
        final needed = _map(_map(requirement)['schemes']);
        if (needed.isEmpty) return ExternalAgentAuth.none;
        if (needed.length != 1) continue;
        final scheme = _map(schemes[needed.keys.single]);
        final http = _map(scheme['httpAuthSecurityScheme']);
        final scopes = _list(_map(needed.values.single)['list'], 20);
        if (http['scheme'] is String &&
            (http['scheme'] as String).toLowerCase() == 'bearer' &&
            scopes.isEmpty) {
          return ExternalAgentAuth.bearer;
        }
      }
      return null;
    }

    final auth = supportedAuth(j['securityRequirements']);
    if (auth == null) supported = false;
    if (!_list(j['defaultInputModes'], 20).contains('text/plain')) {
      supported = false;
    }
    final skills = <ExternalAgentSkill>[];
    for (final rawSkill in _list(j['skills'], 50)) {
      final skill = _map(rawSkill);
      if (skill.containsKey('securityRequirements') &&
          supportedAuth(skill['securityRequirements']) != auth) {
        supported = false;
      }
      skills.add(
        ExternalAgentSkill(
          _text(skill['name'], 120),
          _text(skill['description'], 2000, empty: true),
        ),
      );
    }
    return ExternalAgentCard(
      name: _text(j['name'], 120),
      description: _text(j['description'], 4000, empty: true),
      cardUrl: uri.toString(),
      endpoint: endpoint,
      version: _text(j['version'], 100, empty: true),
      auth: auth ?? ExternalAgentAuth.none,
      supported: supported,
      skills: skills,
    );
  }

  Future<Map<String, dynamic>> _rpc(
    ExternalAgentCard card,
    String? credential,
    String method,
    Map<String, dynamic> params, {
    bool mutation = false,
  }) async {
    if (!card.supported) {
      throw const ExternalAgentException(ExternalAgentIssue.unsupported);
    }
    final endpoint = address(card.endpoint);
    if (endpoint.origin != address(card.cardUrl).origin) {
      throw const ExternalAgentException(ExternalAgentIssue.address);
    }
    if (card.auth == ExternalAgentAuth.bearer &&
        (credential == null ||
            credential.isEmpty ||
            credential.length > 8192 ||
            RegExp(r'[\x00-\x20\x7f]').hasMatch(credential))) {
      throw const ExternalAgentException(ExternalAgentIssue.authentication);
    }
    final id = const Uuid().v4();
    try {
      final reply = await _request(
        endpoint,
        body: {'jsonrpc': '2.0', 'id': id, 'method': method, 'params': params},
        bearer: card.auth == ExternalAgentAuth.bearer ? credential : null,
      );
      if (reply['jsonrpc'] != '2.0' || reply['id'] != id) {
        throw const ExternalAgentException(ExternalAgentIssue.invalidResponse);
      }
      if (reply['error'] is Map) {
        final code = (reply['error'] as Map)['code'];
        throw ExternalAgentException(
          code == -32601
              ? ExternalAgentIssue.unsupported
              : ExternalAgentIssue.unavailable,
        );
      }
      return _map(reply['result']);
    } on ExternalAgentException catch (e) {
      if (mutation &&
          !{
            ExternalAgentIssue.authentication,
            ExternalAgentIssue.unsupported,
          }.contains(e.issue)) {
        throw const ExternalAgentException(ExternalAgentIssue.uncertain);
      }
      rethrow;
    }
  }

  @override
  Future<ExternalTask> send(
    ExternalAgentCard card,
    String? credential,
    String text, {
    ExternalTask? continuation,
  }) async {
    if (text.trim().isEmpty ||
        text.length > 16000 ||
        (continuation != null &&
            (continuation.id == null ||
                continuation.state != ExternalTaskState.inputRequired))) {
      throw const ExternalAgentException(ExternalAgentIssue.unsupported);
    }
    final result = await _rpc(card, credential, 'SendMessage', {
      'message': {
        'messageId': const Uuid().v4(),
        'role': 'ROLE_USER',
        'parts': [
          {'text': text},
        ],
        if (continuation != null) 'taskId': continuation.id,
        if (continuation?.contextId != null)
          'contextId': continuation!.contextId,
      },
      'configuration': {
        'returnImmediately': true,
        'historyLength': 10,
        'acceptedOutputModes': ['text/plain'],
      },
    }, mutation: true);
    try {
      if (result['task'] is Map) {
        final task = parseTask(_map(result['task']));
        if (continuation != null &&
            (task.id != continuation.id ||
                task.contextId != continuation.contextId)) {
          throw const ExternalAgentException(
            ExternalAgentIssue.invalidResponse,
          );
        }
        return task;
      }
      if (result['message'] is Map && continuation == null) {
        return _messageResult(_map(result['message']));
      }
      throw const ExternalAgentException(ExternalAgentIssue.invalidResponse);
    } catch (_) {
      throw const ExternalAgentException(ExternalAgentIssue.uncertain);
    }
  }

  @override
  Future<ExternalTask> getTask(
    ExternalAgentCard card,
    String? credential,
    String id,
  ) async {
    final task = parseTask(
      await _rpc(card, credential, 'GetTask', {'id': id, 'historyLength': 10}),
    );
    if (task.id != id) {
      throw const ExternalAgentException(ExternalAgentIssue.invalidResponse);
    }
    return task;
  }

  @override
  Future<ExternalTask> cancel(
    ExternalAgentCard card,
    String? credential,
    String id,
  ) async {
    final task = parseTask(
      await _rpc(card, credential, 'CancelTask', {'id': id}, mutation: true),
    );
    if (task.id != id) {
      throw const ExternalAgentException(ExternalAgentIssue.uncertain);
    }
    return task;
  }
}

Map<String, dynamic> _map(dynamic value) {
  if (value == null) return {};
  if (value is! Map<String, dynamic>) {
    throw const ExternalAgentException(ExternalAgentIssue.invalidResponse);
  }
  return value;
}

List<dynamic> _list(dynamic value, int max) {
  if (value == null) return [];
  if (value is! List || value.length > max) {
    throw const ExternalAgentException(ExternalAgentIssue.invalidResponse);
  }
  return value;
}

String _text(dynamic value, int max, {bool empty = false}) {
  if (value == null && empty) return '';
  if (value is! String || (!empty && value.isEmpty) || value.length > max) {
    throw const ExternalAgentException(ExternalAgentIssue.invalidResponse);
  }
  return value.replaceAll(RegExp(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]'), '');
}

ExternalTask _messageResult(Map<String, dynamic> message) {
  final task = parseTask({
    'id': 'immediate',
    'status': {'state': 'TASK_STATE_COMPLETED', 'message': message},
  });
  return ExternalTask(
    state: task.state,
    parts: task.parts,
    omittedContent: task.omittedContent,
  );
}

ExternalTask parseTask(Map<String, dynamic> j) {
  final status = _map(j['status']);
  final state = switch (status['state']) {
    'TASK_STATE_SUBMITTED' => ExternalTaskState.submitted,
    'TASK_STATE_WORKING' => ExternalTaskState.working,
    'TASK_STATE_INPUT_REQUIRED' => ExternalTaskState.inputRequired,
    'TASK_STATE_AUTH_REQUIRED' => ExternalTaskState.authRequired,
    'TASK_STATE_COMPLETED' => ExternalTaskState.completed,
    'TASK_STATE_FAILED' => ExternalTaskState.failed,
    'TASK_STATE_CANCELED' => ExternalTaskState.canceled,
    'TASK_STATE_REJECTED' => ExternalTaskState.rejected,
    _ => ExternalTaskState.unknown,
  };
  var remaining = 64000;
  var omitted = false;
  final parts = <ExternalResultPart>[];
  void addParts(dynamic raw, {String? name}) {
    for (final rawPart in _list(raw, 100)) {
      final part = _map(rawPart);
      if (parts.length >= 100) {
        omitted = true;
        continue;
      }
      if (part['text'] is String) {
        final text = part['text'] as String;
        final length = text.length.clamp(0, remaining);
        if (length < text.length) omitted = true;
        if (length > 0) {
          parts.add(
            ExternalResultPart(text: text.substring(0, length), name: name),
          );
        }
        remaining -= length;
      } else if (part['url'] is String) {
        parts.add(
          ExternalResultPart(
            url: _text(part['url'], 2048),
            name: _text(part['filename'] ?? name ?? 'Artifact', 160),
          ),
        );
      } else {
        omitted = true;
      }
    }
  }

  final statusMessage = _map(status['message']);
  addParts(statusMessage['parts']);
  for (final raw in _list(j['artifacts'], 50)) {
    final artifact = _map(raw);
    addParts(
      artifact['parts'],
      name: _text(artifact['name'], 160, empty: true),
    );
  }
  if (parts.isEmpty) {
    for (final raw in _list(j['history'], 10)) {
      final message = _map(raw);
      if (message['role'] == 'ROLE_AGENT') addParts(message['parts']);
    }
  }
  return ExternalTask(
    id: _text(j['id'], 256),
    contextId: j['contextId'] == null ? null : _text(j['contextId'], 256),
    state: state,
    statusMessageId: statusMessage['messageId'] == null
        ? null
        : _text(statusMessage['messageId'], 256),
    parts: parts,
    omittedContent: omitted,
  );
}
