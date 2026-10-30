/// The two generations of the OpenCode 2 HTTP API this client speaks.
///
/// The client was written against `0.0.0-beta-18600`. OpenCode 2.0.4 (the
/// stable line published as `@opencode/cli`) removed the `v2` operation
/// prefixes and reshaped about two dozen endpoints: several moved under
/// `/experimental`, some became a different method on the parent resource,
/// and a few request fields were renamed. Both generations are in use (a
/// phone keeps the beta until its server is updated), so call sites keep
/// speaking the beta's vocabulary and [stableRoute] translates at the edge.
///
/// Derived from the two servers' own `/openapi.json` on 2026-09-20; see
/// `docs/research/opencode2-stable-2.0-2026-09-20.md`.
///
/// 2.1 (checked against 2.1.1's `/openapi.json` on 2026-10-21) moved and
/// removed no operation, so [Api2Dialect.stable] covers 2.0.x and 2.1.x alike;
/// see `docs/qa/opencode-2.1-2026-10-21/README.md`.
library;

enum Api2Dialect {
  /// `0.0.0-beta-*` under `@opencode-ai/cli`: health at `/api/health`.
  beta,

  /// 2.0.4 and later (2.1.x included) under `@opencode/cli`: server facts at
  /// `/api/info`.
  stable,
}

/// One request after translation.
class Api2Route {
  const Api2Route(this.method, this.path, {this.query, this.body});

  final String method;
  final String path;
  final Map<String, dynamic>? query;
  final Object? body;
}

/// Thrown for a beta operation that the stable API no longer has at all.
class Api2RemovedInStable implements Exception {
  const Api2RemovedInStable(this.operation);
  final String operation;

  @override
  String toString() => '$operation is not available on this OpenCode version';
}

final _sessionScoped = RegExp(r'^/session/([^/]+)/(.+)$');
final _inboxDelivery = RegExp(
  r'^/session/([^/]+)/inbox/([^/]+)/(steer|queue)$',
);
final _formCancel = RegExp(r'^/session/([^/]+)/form/([^/]+)/cancel$');
final _formState = RegExp(r'^/session/([^/]+)/form/([^/]+)/state$');
final _worktree = RegExp(r'^/worktree/([^/]+)(/refresh)?$');
final _mcpServer = RegExp(r'^/mcp/([^/]+)(/connect|/disconnect)?$');

Map<String, dynamic> _bodyMap(Object? body) =>
    body is Map ? Map<String, dynamic>.from(body) : <String, dynamic>{};

/// Translates a request written for the beta API into the stable API.
///
/// Pure, so the whole mapping is unit-tested without a server. Requests that
/// did not change pass through untouched.
Api2Route stableRoute(
  String method,
  String path, {
  Map<String, dynamic>? query,
  Object? body,
}) {
  Api2Route same({String? m, String? p, Map<String, dynamic>? q, Object? b}) =>
      Api2Route(m ?? method, p ?? path, query: q ?? query, body: b ?? body);

  // ---- server facts -----------------------------------------------------
  if (method == 'GET' && (path == '/health' || path == '/server')) {
    return same(p: '/info');
  }
  // The current project is part of the location now.
  if (method == 'GET' && path == '/project/current') {
    return same(p: '/location');
  }
  if (method == 'GET' && path == '/form/request') return same(p: '/form');

  // ---- removed outright -------------------------------------------------
  if (path == '/workspace' || path.startsWith('/workspace/')) {
    throw const Api2RemovedInStable('Cloud environments');
  }
  if (method == 'PATCH' && RegExp(r'^/shell/[^/]+/timeout$').hasMatch(path)) {
    throw const Api2RemovedInStable('Changing a running command\'s timeout');
  }

  // ---- worktrees: the project moved from the path into the request ------
  final worktree = _worktree.firstMatch(path);
  if (worktree != null) {
    final projectID = Uri.decodeComponent(worktree[1]!);
    if (worktree[2] != null) {
      return same(p: '/worktree/refresh', b: {'projectID': projectID});
    }
    if (method == 'GET') {
      return same(p: '/worktree', q: {...?query, 'projectID': projectID});
    }
    return same(p: '/worktree', b: {..._bodyMap(body), 'projectID': projectID});
  }

  // ---- MCP writes are experimental; the list stays at /mcp --------------
  final mcp = _mcpServer.firstMatch(path);
  if (mcp != null && (method != 'GET' || mcp[2] != null)) {
    return same(p: '/experimental$path');
  }
  if (method == 'POST' && path == '/generate') {
    return same(p: '/experimental/generate');
  }

  // ---- sessions ---------------------------------------------------------
  if (path == '/session/import' || path == '/session/stats') {
    return same(p: '/experimental$path');
  }
  final inbox = _inboxDelivery.firstMatch(path);
  if (inbox != null && method == 'POST') {
    return Api2Route(
      'PATCH',
      '/session/${inbox[1]}/inbox/${inbox[2]}',
      query: query,
      body: {'delivery': inbox[3]},
    );
  }
  final cancel = _formCancel.firstMatch(path);
  if (cancel != null && method == 'POST') {
    return Api2Route(
      'DELETE',
      '/session/${cancel[1]}/form/${cancel[2]}',
      query: query,
    );
  }
  // A form's state is a field of the form itself now.
  final state = _formState.firstMatch(path);
  if (state != null && method == 'GET') {
    return same(p: '/session/${state[1]}/form/${state[2]}');
  }
  final scoped = _sessionScoped.firstMatch(path);
  if (scoped != null) {
    final id = scoped[1]!;
    final rest = scoped[2]!;
    if (method == 'POST' && rest == 'rename') {
      return Api2Route('PATCH', '/session/$id', query: query, body: body);
    }
    if (method == 'POST' && rest == 'revert/clear') {
      return Api2Route('DELETE', '/session/$id/revert', query: query);
    }
    if (rest == 'skill' ||
        rest == 'wait' ||
        rest == 'export' ||
        rest == 'instructions/entries' ||
        rest.startsWith('instructions/entries/')) {
      return same(p: '/experimental$path');
    }
    // ---- renamed request fields ----------------------------------------
    if (method == 'POST' && rest == 'command') {
      final map = _bodyMap(body);
      if (map.containsKey('command')) map['name'] = map.remove('command');
      return same(b: map);
    }
    if (method == 'POST' && rest == 'interrupt' && query != null) {
      final next = Map<String, dynamic>.from(query);
      if (next.containsKey('continue')) {
        next['resume'] = next.remove('continue');
      }
      return same(q: next);
    }
    if (method == 'POST' && rest == 'fork') {
      // {boundary: {type: before, messageID}} became {before: messageID};
      // "through" (everything) is simply an absent `before`.
      final boundary = _bodyMap(body)['boundary'];
      final before = boundary is Map && boundary['type'] == 'before'
          ? boundary['messageID']
          : null;
      return same(b: {'before': ?before});
    }
    if (method == 'POST' &&
        RegExp(r'^permission/[^/]+/reply$').hasMatch(rest)) {
      final map = _bodyMap(body);
      if (map.containsKey('reply')) map['decision'] = map.remove('reply');
      return same(b: map);
    }
  }
  return same();
}

/// Reshapes a stable response into what the beta call site expects, for the
/// few reads whose envelope changed. Everything else passes through.
dynamic stableResponse(String method, String path, dynamic json) {
  if (method != 'GET' || json is! Map) return json;
  // Call sites test for `Map<String, dynamic>`, so rebuilt maps are typed.
  Map<String, dynamic> typed(Map value) => {
    for (final entry in value.entries) entry.key.toString(): entry.value,
  };
  switch (path) {
    case '/health':
      // `/info` has no `healthy` field: answering at all is the health.
      return <String, dynamic>{'healthy': true, ...typed(json)};
    case '/project/current':
      final project = json['project'];
      return <String, dynamic>{
        'data': project is Map ? typed(project) : const <String, dynamic>{},
      };
  }
  if (_formState.hasMatch(path)) {
    final data = json['data'];
    return <String, dynamic>{'data': data is Map ? data['state'] : null};
  }
  return json;
}
