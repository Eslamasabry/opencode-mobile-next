//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';

enum PermissionV2Reply {
  @JsonValue(r'once')
  once(r'once'),
  @JsonValue(r'always')
  always(r'always'),
  @JsonValue(r'reject')
  reject(r'reject'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PermissionV2Reply(this.value);

  final String value;

  @override
  String toString() => value;
}
