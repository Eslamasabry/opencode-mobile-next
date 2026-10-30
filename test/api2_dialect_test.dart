import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/dialect.dart';
import 'package:opencode_mobile/api2/plugin_mapper.dart';
import 'package:opencode_mobile/domain/plugin_inventory.dart';

/// The mapping was derived from the two servers' own `/openapi.json`
/// (`0.0.0-beta-18600` and `2.0.10`; 2.1.1 changed no route) and proven live by
/// `tool/qa/oc2_live_proof_test.dart`; these pin it without a server.
void main() {
  // A list, not a record: `equals` compares lists and maps deeply.
  List<Object?> route(
    String method,
    String path, {
    Map<String, dynamic>? query,
    Object? body,
  }) {
    final r = stableRoute(method, path, query: query, body: body);
    return [r.method, r.path, r.query, r.body];
  }

  test('requests that did not change pass through untouched', () {
    final body = {'text': 'hi'};
    final query = <String, dynamic>{'limit': 50};
    for (final (method, path) in [
      ('GET', '/session'),
      ('POST', '/session/ses_1/prompt'),
      ('GET', '/session/ses_1/message'),
      ('DELETE', '/session/ses_1'),
      ('GET', '/mcp'),
      ('GET', '/session/ses_1/form/frm_1'),
      ('DELETE', '/session/ses_1/inbox/inb_1'),
    ]) {
      final r = stableRoute(method, path, query: query, body: body);
      expect([r.method, r.path], [method, path]);
      expect(r.query, same(query));
      expect(r.body, same(body));
    }
  });

  test('2.1 added fields and routes, and moved none', () {
    // From the 2.0.29 → 2.1.1 `/openapi.json` diff: no operation removed or
    // moved, so the 2.1 additions the app may send pass through as written.
    expect(route('PATCH', '/session/ses_1', body: {'archived': true}), [
      'PATCH',
      '/session/ses_1',
      null,
      {'archived': true},
    ]);
    expect(route('GET', '/session', query: {'archived': 'true'}), [
      'GET',
      '/session',
      {'archived': 'true'},
      null,
    ]);
    expect(route('GET', '/session/ses_1/message', query: {'type': 'idle'}), [
      'GET',
      '/session/ses_1/message',
      {'type': 'idle'},
      null,
    ]);
    expect(route('PATCH', '/permission/saved/per_1', body: {}), [
      'PATCH',
      '/permission/saved/per_1',
      null,
      <String, dynamic>{},
    ]);
  });

  test('server facts, forms and the current project moved', () {
    expect(route('GET', '/health')[1], '/info');
    expect(route('GET', '/server')[1], '/info');
    expect(route('GET', '/form/request')[1], '/form');
    expect(route('GET', '/project/current')[1], '/location');
  });

  test('experimental routes keep their method and body', () {
    for (final (method, path) in [
      ('POST', '/session/ses_1/skill'),
      ('POST', '/session/ses_1/wait'),
      ('GET', '/session/ses_1/export'),
      ('GET', '/session/ses_1/instructions/entries'),
      ('PUT', '/session/ses_1/instructions/entries/note'),
      ('DELETE', '/session/ses_1/instructions/entries/note'),
      ('POST', '/session/import'),
      ('GET', '/session/stats'),
      ('POST', '/generate'),
      ('PUT', '/mcp/github'),
      ('DELETE', '/mcp/github'),
      ('POST', '/mcp/github/connect'),
      ('POST', '/mcp/github/disconnect'),
    ]) {
      expect(route(method, path), [method, '/experimental$path', null, null]);
    }
  });

  test('actions that became a method on the parent resource', () {
    expect(route('POST', '/session/ses_1/rename', body: {'title': 'T'}), [
      'PATCH',
      '/session/ses_1',
      null,
      {'title': 'T'},
    ]);
    expect(route('POST', '/session/ses_1/revert/clear'), [
      'DELETE',
      '/session/ses_1/revert',
      null,
      null,
    ]);
    expect(route('POST', '/session/ses_1/form/frm_1/cancel'), [
      'DELETE',
      '/session/ses_1/form/frm_1',
      null,
      null,
    ]);
    for (final delivery in ['steer', 'queue']) {
      expect(route('POST', '/session/ses_1/inbox/inb_1/$delivery'), [
        'PATCH',
        '/session/ses_1/inbox/inb_1',
        null,
        {'delivery': delivery},
      ]);
    }
  });

  test('worktrees carry the project in the request, not the path', () {
    expect(route('GET', '/worktree/prj_1'), [
      'GET',
      '/worktree',
      {'projectID': 'prj_1'},
      null,
    ]);
    expect(route('POST', '/worktree/prj_1', body: {'name': 'fix'}), [
      'POST',
      '/worktree',
      null,
      {'name': 'fix', 'projectID': 'prj_1'},
    ]);
    expect(
      route(
        'DELETE',
        '/worktree/prj_1',
        body: {'directory': '/w/fix', 'force': false},
      ),
      [
        'DELETE',
        '/worktree',
        null,
        {'directory': '/w/fix', 'force': false, 'projectID': 'prj_1'},
      ],
    );
    expect(route('POST', '/worktree/prj_1/refresh'), [
      'POST',
      '/worktree/refresh',
      null,
      {'projectID': 'prj_1'},
    ]);
  });

  test('renamed request fields', () {
    expect(
      route(
        'POST',
        '/session/ses_1/permission/per_1/reply',
        body: {'reply': 'once', 'message': 'ok'},
      )[3],
      {'message': 'ok', 'decision': 'once'},
    );
    expect(
      route(
        'POST',
        '/session/ses_1/command',
        body: {'command': 'init', 'text': 'x'},
      )[3],
      {'text': 'x', 'name': 'init'},
    );
    // The shell route keeps `command`: only slash commands were renamed.
    expect(route('POST', '/session/ses_1/shell', body: {'command': 'ls'})[3], {
      'command': 'ls',
    });
    expect(
      route('POST', '/session/ses_1/interrupt', query: {'continue': 'true'})[2],
      {'resume': 'true'},
    );
    expect(
      route(
        'POST',
        '/session/ses_1/fork',
        body: {
          'boundary': {'type': 'before', 'messageID': 'msg_9'},
        },
      )[3],
      {'before': 'msg_9'},
    );
    expect(
      route(
        'POST',
        '/session/ses_1/fork',
        body: {
          'boundary': {'type': 'through'},
        },
      )[3],
      <String, dynamic>{},
    );
  });

  test('what the stable line removed fails clearly instead of 404ing', () {
    for (final call in [
      () => stableRoute('POST', '/workspace'),
      () => stableRoute('DELETE', '/workspace/wrk_1'),
      () => stableRoute('PATCH', '/shell/sh_1/timeout'),
    ]) {
      expect(call, throwsA(isA<Api2RemovedInStable>()));
    }
  });

  test('responses are reshaped into what the beta call sites read', () {
    final health = stableResponse('GET', '/health', {
      'version': '2.0.10',
      'pid': 42,
      'urls': ['http://127.0.0.1:4096'],
    });
    expect(health, isA<Map<String, dynamic>>());
    expect(health['healthy'], isTrue);
    expect(health['version'], '2.0.10');
    expect(health['pid'], 42);

    final project = stableResponse('GET', '/project/current', {
      'directory': '/w',
      'project': {'id': 'prj_1', 'directory': '/w', 'canonical': '/w'},
    });
    expect(project, {
      'data': {'id': 'prj_1', 'directory': '/w', 'canonical': '/w'},
    });

    expect(
      stableResponse('GET', '/session/s/form/f/state', {
        'data': {
          'id': 'f',
          'state': {'status': 'pending'},
        },
      }),
      {
        'data': {'status': 'pending'},
      },
    );
    final untouched = {'data': <Object>[]};
    expect(stableResponse('GET', '/session', untouched), same(untouched));
  });

  test('plugin rows are read in both shapes', () {
    final beta = mapPluginInfo({
      'id': 'opencode.config.mcp',
      'source': {'type': 'builtin'},
      'status': 'active',
      'tui': true,
    });
    final stable = mapPluginInfo({
      'id': 'opencode.browser',
      'source': {'type': 'builtin'},
      'features': {'server': true},
      'state': {'status': 'active'},
    });
    expect(beta.status, PluginStatus.active);
    expect(beta.terminalUi, isTrue);
    expect(stable.status, PluginStatus.active);
    // An absent feature is false.
    expect(stable.terminalUi, isFalse);
    expect(
      () => mapPluginInfo({
        'id': 'x',
        'source': {'type': 'builtin'},
      }),
      throwsFormatException,
    );
  });
}
