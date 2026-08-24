//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_action.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_rule.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionRule {
  /// Returns a new [PermissionRule] instance.
  PermissionRule({
    required this.permission,

    required this.pattern,

    required this.action,
  });

  @JsonKey(name: r'permission', required: true, includeIfNull: false)
  final String permission;

  @JsonKey(name: r'pattern', required: true, includeIfNull: false)
  final String pattern;

  @JsonKey(
    name: r'action',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PermissionAction.unknownDefaultOpenApi,
  )
  final PermissionAction action;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionRule &&
            runtimeType == other.runtimeType &&
            equals(
              [permission, pattern, action],
              [other.permission, other.pattern, other.action],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([permission, pattern, action]);

  factory PermissionRule.fromJson(Map<String, dynamic> json) =>
      _$PermissionRuleFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionRuleToJson(this);

  String toString() {
    return toJson().toString();
  }
}
