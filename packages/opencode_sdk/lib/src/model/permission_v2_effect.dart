//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';

enum PermissionV2Effect {
  @JsonValue(r'allow')
  allow(r'allow'),
  @JsonValue(r'deny')
  deny(r'deny'),
  @JsonValue(r'ask')
  ask(r'ask'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PermissionV2Effect(this.value);

  final String value;

  @override
  String toString() => value;
}
