import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/form_request.dart';

Map<String, dynamic> schema() => {
  'id': 'form',
  'sessionID': 'session',
  'title': 'Deployment',
  'metadata': {
    'nested': {
      'tags': ['original'],
      'enabled': true,
    },
  },
  'fields': [
    {'key': 'enabled', 'type': 'boolean', 'default': false},
    {
      'key': 'count',
      'type': 'integer',
      'minimum': 1,
      'maximum': 4,
      'default': 2,
    },
    {
      'key': 'weight',
      'type': 'number',
      'minimum': 0.5,
      'maximum': 9.5,
      'default': 1.25,
    },
    {
      'key': 'name',
      'type': 'string',
      'title': 'Name',
      'description': 'Choose',
      'required': true,
      'format': 'email',
      'minLength': 2,
      'maxLength': 20,
      'pattern': '^a',
      'placeholder': 'alice',
      'default': 'alice',
      'custom': true,
      'when': [
        {
          'key': 'enabled',
          'op': 'eq',
          'value': {
            'nested': [true],
          },
        },
      ],
      'options': [
        {'value': 'alice', 'label': 'Alice', 'description': 'Person'},
      ],
    },
    {
      'key': 'tags',
      'type': 'multiselect',
      'default': ['a'],
      'minItems': 1,
      'maxItems': 2,
      'custom': true,
      'options': [
        {'value': 'a', 'label': 'A'},
      ],
    },
    {'key': 'link', 'type': 'external', 'url': 'https://example.com'},
  ],
};

Api2FormInfo parse(Map<String, dynamic> value) => Api2FormInfo.fromJson(value)!;

void main() {
  test('captured schema detaches and recursively freezes nested values', () {
    final source = parse(schema());
    final captured = snapshotForm(source);
    final revision = formSchemaRevision(captured);
    expect(identical(source, captured), isFalse);
    (source.metadata!['nested']['tags'] as List).add('changed');
    (source.fields[3].when.single.value['nested'] as List).add(false);
    (source.fields[4].defaultValue as List).add('changed');
    source.fields[3].options.clear();
    source.fields.clear();
    expect(formSchemaRevision(captured), revision);
    expect(captured.fields, hasLength(6));
    expect(captured.fields[0].defaultValue, isFalse);
    expect(captured.fields[1].defaultValue, isA<int>());
    expect(captured.fields[2].defaultValue, isA<double>());
    expect(captured.fields[3].options.single.description, 'Person');
    expect(() => captured.fields.clear(), throwsUnsupportedError);
    expect(() => captured.metadata!['extra'] = 1, throwsUnsupportedError);
    expect(
      () => captured.metadata!['nested']['tags'].add('x'),
      throwsUnsupportedError,
    );
    expect(() => captured.fields[3].when.clear(), throwsUnsupportedError);
    expect(
      () => captured.fields[3].when.single.value['nested'].add(false),
      throwsUnsupportedError,
    );
    expect(() => captured.fields[3].options.clear(), throwsUnsupportedError);
    expect(
      () => captured.fields[4].defaultValue.add('x'),
      throwsUnsupportedError,
    );
  });

  test('schema revision includes every constraint and nested metadata', () {
    final original = formSchemaRevision(parse(schema()));
    final mutations = <void Function(Map<String, dynamic>)>[
      (j) => j['title'] = 'Different',
      (j) => j['metadata']['nested']['enabled'] = false,
      (j) => j['fields'][0]['default'] = true,
      (j) => j['fields'][1]['minimum'] = 0,
      (j) => j['fields'][1]['maximum'] = 5,
      (j) => j['fields'][2]['default'] = 1,
      (j) => j['fields'][3]['title'] = 'Different',
      (j) => j['fields'][3]['description'] = 'Different',
      (j) => j['fields'][3]['required'] = false,
      (j) => j['fields'][3]['format'] = 'uri',
      (j) => j['fields'][3]['minLength'] = 1,
      (j) => j['fields'][3]['maxLength'] = 30,
      (j) => j['fields'][3]['pattern'] = '^b',
      (j) => j['fields'][3]['placeholder'] = 'bob',
      (j) => j['fields'][3]['custom'] = false,
      (j) => j['fields'][3]['when'][0]['op'] = 'neq',
      (j) => j['fields'][3]['when'][0]['value']['nested'][0] = false,
      (j) => j['fields'][3]['options'][0]['description'] = 'Different',
      (j) => j['fields'][4]['default'].add('b'),
      (j) => j['fields'][4]['minItems'] = 0,
      (j) => j['fields'][4]['maxItems'] = 3,
      (j) => j['fields'][5]['url'] = 'https://example.org',
    ];
    for (var i = 0; i < mutations.length; i++) {
      final changed = schema();
      mutations[i](changed);
      expect(
        formSchemaRevision(parse(changed)),
        isNot(original),
        reason: 'mutation $i',
      );
    }
  });

  test(
    'schema revision ignores map insertion order but preserves value types',
    () {
      final first = schema();
      final second = schema();
      second['metadata'] = {
        'nested': {
          'enabled': true,
          'tags': ['original'],
        },
      };
      expect(
        formSchemaRevision(parse(first)),
        formSchemaRevision(parse(second)),
      );
      second['fields'][0]['default'] = 'false';
      expect(
        formSchemaRevision(parse(first)),
        isNot(formSchemaRevision(parse(second))),
      );
    },
  );

  test(
    'identity key fences every scope component without delimiter collisions',
    () {
      FormRequestIdentity identity({
        String profile = 'p',
        String directory = '/d',
        String session = 's',
        String form = 'f',
        String revision = 'r',
        String? workspace,
      }) => FormRequestIdentity(
        profileID: profile,
        directory: directory,
        sessionID: session,
        formID: form,
        revision: revision,
        workspace: workspace,
      );
      final keys = [
        identity(),
        identity(profile: 'q'),
        identity(directory: '/e'),
        identity(session: 't'),
        identity(form: 'g'),
        identity(revision: 'r2'),
        identity(workspace: ''),
        identity(workspace: 'w'),
        identity(profile: 'p|/d', directory: 's'),
        identity(profile: 'p', directory: '/d|s'),
      ].map((i) => i.key).toList();
      expect(keys.toSet(), hasLength(keys.length));
      expect(keys.first, matches(RegExp(r'^[0-9a-f]{64}$')));
      expect(identity().key, identity().key);
    },
  );
}
