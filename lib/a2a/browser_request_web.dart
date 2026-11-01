import 'dart:convert';

import 'package:http/browser_client.dart';
import 'package:http/http.dart' show Request;

import '../domain/external_agent.dart';

const a2aIsWeb = true;

/// Browser Fetch transport with the same response bounds and redirect policy
/// as the native A2A client. Each request owns its cancellation lifetime.
class BrowserA2aRequests {
  final _active = <BrowserClient>{};
  bool _closed = false;

  Future<Map<String, dynamic>> request(
    Uri url, {
    Map<String, dynamic>? body,
    String? bearer,
  }) async {
    if (_closed) {
      throw const ExternalAgentException(ExternalAgentIssue.unavailable);
    }
    final client = BrowserClient();
    _active.add(client);
    try {
      return await (() async {
        final request = Request(body == null ? 'GET' : 'POST', url)
          ..followRedirects = false
          ..headers['Accept'] = 'application/json';
        if (body != null) {
          request.headers['Content-Type'] = 'application/json';
          request.headers['A2A-Version'] = '1.0';
          request.body = jsonEncode(body);
        }
        if (bearer != null) {
          request.headers['Authorization'] = 'Bearer $bearer';
        }
        final response = await client.send(request);
        if (response.statusCode == 401 || response.statusCode == 403) {
          throw const ExternalAgentException(ExternalAgentIssue.authentication);
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          throw const ExternalAgentException(ExternalAgentIssue.unavailable);
        }
        final bytes = <int>[];
        await for (final chunk in response.stream) {
          if (bytes.length + chunk.length > 1024 * 1024) {
            throw const ExternalAgentException(
              ExternalAgentIssue.invalidResponse,
            );
          }
          bytes.addAll(chunk);
        }
        final result = jsonDecode(utf8.decode(bytes));
        if (result is! Map<String, dynamic>) {
          throw const ExternalAgentException(
            ExternalAgentIssue.invalidResponse,
          );
        }
        return result;
      })().timeout(const Duration(seconds: 15));
    } on ExternalAgentException {
      rethrow;
    } catch (_) {
      // Browser CORS/connection details and remote text are not diagnostic copy.
      throw const ExternalAgentException(ExternalAgentIssue.unavailable);
    } finally {
      _active.remove(client);
      client.close();
    }
  }

  void close() {
    _closed = true;
    for (final client in _active.toList()) {
      client.close();
    }
    _active.clear();
  }
}
