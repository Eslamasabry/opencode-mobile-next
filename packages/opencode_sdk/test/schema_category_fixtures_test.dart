import 'dart:convert';

import 'package:opencode_sdk/opencode_sdk.dart';
import 'package:opencode_sdk/src/deserialize.dart';
import 'package:test/test.dart';

void main() {
  test('representative schema categories round trip without wire loss', () {
    const nativeJson = <String, Object?>{
      'nested': <Object?>[true, 3, 'value', null],
    };
    expect(
      jsonDecode(jsonEncode(nativeJson)),
      nativeJson,
      reason: 'Object alias',
    );

    const scalarAlias = 'stream-token';
    expect(
      deserialize<String, String>(scalarAlias, 'String'),
      scalarAlias,
      reason: 'scalar alias',
    );

    const listAlias = <String>['read', 'write'];
    expect(
      deserialize<List<String>, String>(listAlias, 'List<String>'),
      listAlias,
      reason: 'list alias',
    );

    const modelJson = <String, Object?>{
      'directory': '/workspace',
      'strategy': 'git',
    };
    expect(
      ProjectDirectoriesInner.fromJson(
        Map<String, dynamic>.from(modelJson),
      ).toJson(),
      modelJson,
      reason: 'json_serializable model',
    );

    expect(
      PermissionAction.values
          .singleWhere((value) => value.value == 'allow')
          .value,
      'allow',
      reason: 'top-level enum',
    );

    const unionJson = <String, Object?>{
      'status': 'pending',
      'input': <String, Object?>{'path': 'a.txt'},
      'raw': 'input',
    };
    expect(
      ToolState.fromJson(unionJson).toJson(),
      unionJson,
      reason: 'descriptor-backed raw union',
    );

    const mixedMapJson = <String, Object?>{
      'custom-agent': <String, Object?>{
        'model': 'provider/model',
        'unknown': <String, Object?>{'retained': true},
      },
    };
    expect(
      ConfigAgent.fromJson(Map<String, dynamic>.from(mixedMapJson)).toJson(),
      mixedMapJson,
      reason: 'typed mixed additionalProperties model',
    );
  });
}
