import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/session_permission_policy.dart';
import 'package:opencode_mobile/api2/transport.dart';

const ask = {'action': '*', 'resource': '*', 'effect': 'ask'};
Map<String, String> rule(
  String action,
  String effect, [
  String resource = '*',
]) => {'action': action, 'resource': resource, 'effect': effect};

Api2SessionPermissionPolicy session({
  List<Object?> rules = const [],
  String agent = 'build',
  Map<String, dynamic>? metadata,
}) => Api2SessionPermissionPolicy.fromSession({
  'data': {
    'id': 's',
    'agent': agent,
    'permissions': rules,
    'metadata': metadata,
  },
}, 's');

Map<String, dynamic> agent(String id, List<Object?> rules) => {
  'data': {'id': id, 'permissions': rules},
};

void main() {
  test('fresh session omissions use the server resolved default first', () {
    final fresh = Api2SessionPermissionPolicy.fromSession({
      'data': {'id': 's'},
    }, 's');
    final resolved = Api2SessionPermissionPolicy.defaultAgent({
      'data': [
        {
          'id': 'restricted',
          'mode': 'primary',
          'hidden': false,
          'permissions': [rule('shell', 'deny')],
        },
        {
          'id': 'build',
          'mode': 'primary',
          'hidden': false,
          'permissions': [rule('*', 'allow')],
        },
      ],
    });
    expect(fresh.updateForAgent(resolved)['permissions'], [
      ask,
      rule('shell', 'deny'),
    ]);
    expect(
      () => Api2SessionPermissionPolicy.defaultAgent({
        'data': [
          {'id': 'hidden', 'mode': 'subagent', 'hidden': true},
        ],
      }),
      throwsA(isA<Api2RequestError>()),
    );
  });

  test('an automatic session keeps its own rules, denials included', () {
    final policy = session(rules: [rule('shell', 'deny')]);
    final update = policy.updateForAgent(
      agent('build', [rule('*', 'allow')]),
      serverRuns: true,
    );
    expect(update['permissions'], [rule('shell', 'deny')]);
    // Switching back asks again, from the same original rules.
    final back = session(
      rules: update['permissions'] as List,
      metadata: update['metadata'] as Map<String, dynamic>,
    ).updateForAgent(agent('build', [rule('*', 'allow')]));
    expect(back['permissions'], [ask, ask, rule('shell', 'deny')]);
  });

  test('readback metadata key order does not affect confirmation', () {
    final update = session().updateForAgent(agent('build', []));
    final applied =
        (update['metadata'] as Map)[Api2SessionPermissionPolicy.metadataKey]
            as Map;
    final reordered = session(
      rules: update['permissions'] as List,
      metadata: {
        Api2SessionPermissionPolicy.metadataKey: {
          'applied': applied['applied'],
          'original': applied['original'],
          'version': 1,
        },
      },
    );
    expect(reordered.confirms(update), isTrue);
  });

  test('oversized composed policy fails before writing', () {
    expect(
      () => session(
        rules: List.filled(500, rule('edit', 'deny')),
      ).updateForAgent(agent('build', List.filled(500, rule('shell', 'deny')))),
      throwsA(isA<Api2RequestError>()),
    );
  });

  test(
    'default agent allowance becomes a request including shell and edit',
    () {
      final update = session().updateForAgent(
        agent('build', [rule('*', 'allow')]),
      );
      expect(update['permissions'], [ask, ask]);
    },
  );

  test('agent denial and ordered readonly exceptions retain their meaning', () {
    final update = session().updateForAgent(
      agent('build', [
        rule('*', 'deny'),
        rule('read', 'allow'),
        rule('glob', 'allow'),
        rule('edit', 'deny', '/etc/*'),
      ]),
    );
    expect(update['permissions'], [
      ask,
      rule('*', 'deny'),
      rule('read', 'ask'),
      rule('glob', 'ask'),
      rule('edit', 'deny', '/etc/*'),
    ]);
  });

  test('original session rules keep their order and deny exceptions', () {
    final original = [rule('*', 'deny'), rule('read', 'allow')];
    final update = session(
      rules: original,
    ).updateForAgent(agent('build', [rule('*', 'allow')]));
    expect(update['permissions'], [
      ask,
      ask,
      rule('*', 'deny'),
      rule('read', 'ask'),
    ]);
    expect(
      (update['metadata']
          as Map)[Api2SessionPermissionPolicy.metadataKey]['original'],
      original,
    );
  });

  test(
    'changing agent uses original session rules, not prior agent allowances',
    () {
      final first = session(
        metadata: {'owner': 'kept'},
      ).updateForAgent(agent('build', [rule('*', 'allow')]));
      final next =
          session(
            rules: first['permissions'] as List,
            metadata: first['metadata'] as Map<String, dynamic>,
            agent: 'plan',
          ).updateForAgent(
            agent('plan', [rule('*', 'allow'), rule('edit', 'deny')]),
          );
      expect(next['permissions'], [ask, ask, rule('edit', 'deny')]);
      expect((next['metadata'] as Map)['owner'], 'kept');
      final repeated = session(
        rules: next['permissions'] as List,
        metadata: next['metadata'] as Map<String, dynamic>,
        agent: 'plan',
      );
      expect(
        repeated.confirms(
          repeated.updateForAgent(
            agent('plan', [rule('*', 'allow'), rule('edit', 'deny')]),
          ),
        ),
        isTrue,
      );
    },
  );

  test('external session edits replace the saved original policy', () {
    final first = session().updateForAgent(
      agent('build', [rule('*', 'allow')]),
    );
    final update = session(
      rules: [rule('shell', 'deny')],
      metadata: first['metadata'] as Map<String, dynamic>,
    ).updateForAgent(agent('build', [rule('*', 'allow')]));
    expect(update['permissions'], [ask, ask, rule('shell', 'deny')]);
  });

  for (final invalid in [
    null,
    {},
    [rule('shell', 'unknown')],
    [null],
    [
      {'action': 'shell', 'effect': 'deny'},
    ],
  ]) {
    test('malformed rules fail closed: $invalid', () {
      expect(
        () => Api2SessionPermissionPolicy.fromSession({
          'data': {'id': 's', 'agent': 'build', 'permissions': invalid},
        }, 's'),
        throwsA(isA<Api2RequestError>()),
      );
    });
  }

  test('malformed provenance and wrong agent fail closed', () {
    expect(
      () => session(
        metadata: {
          Api2SessionPermissionPolicy.metadataKey: {'version': 2},
        },
      ).updateForAgent(agent('build', [])),
      throwsA(isA<Api2RequestError>()),
    );
    expect(
      () => session().updateForAgent(agent('other', [])),
      throwsA(isA<Api2RequestError>()),
    );
  });
}
