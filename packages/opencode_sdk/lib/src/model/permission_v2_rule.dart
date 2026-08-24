//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_effect.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_v2_rule.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionV2Rule {
  /// Returns a new [PermissionV2Rule] instance.
  PermissionV2Rule({
    required this.action,

    required this.resource,

    required this.effect,
  });

  @JsonKey(name: r'action', required: true, includeIfNull: false)
  final String action;

  @JsonKey(name: r'resource', required: true, includeIfNull: false)
  final String resource;

  @JsonKey(
    name: r'effect',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PermissionV2Effect.unknownDefaultOpenApi,
  )
  final PermissionV2Effect effect;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionV2Rule &&
            runtimeType == other.runtimeType &&
            equals(
              [action, resource, effect],
              [other.action, other.resource, other.effect],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([action, resource, effect]);

  factory PermissionV2Rule.fromJson(Map<String, dynamic> json) =>
      _$PermissionV2RuleFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionV2RuleToJson(this);

  String toString() {
    return toJson().toString();
  }
}
