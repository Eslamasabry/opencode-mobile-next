//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_rule.dart';
import 'package:opencode_sdk/src/model/agent_color.dart';
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:opencode_sdk/src/model/provider_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'agent_v2_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AgentV2Info {
  /// Returns a new [AgentV2Info] instance.
  AgentV2Info({
    required this.id,

    this.model,

    required this.request,

    this.system,

    this.description,

    required this.mode,

    required this.hidden,

    this.color,

    this.steps,

    required this.permissions,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final ModelRef? model;

  @JsonKey(name: r'request', required: true, includeIfNull: false)
  final ProviderRequest request;

  @JsonKey(name: r'system', required: false, includeIfNull: false)
  final String? system;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(
    name: r'mode',
    required: true,
    includeIfNull: false,
    unknownEnumValue: AgentV2InfoModeEnum.unknownDefaultOpenApi,
  )
  final AgentV2InfoModeEnum mode;

  @JsonKey(name: r'hidden', required: true, includeIfNull: false)
  final bool hidden;

  @JsonKey(name: r'color', required: false, includeIfNull: false)
  final AgentColor? color;

  @JsonKey(name: r'steps', required: false, includeIfNull: false)
  final int? steps;

  @JsonKey(name: r'permissions', required: true, includeIfNull: false)
  final List<PermissionV2Rule> permissions;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AgentV2Info &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                model,
                request,
                system,
                description,
                mode,
                hidden,
                color,
                steps,
                permissions,
              ],
              [
                other.id,
                other.model,
                other.request,
                other.system,
                other.description,
                other.mode,
                other.hidden,
                other.color,
                other.steps,
                other.permissions,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        model,
        request,
        system,
        description,
        mode,
        hidden,
        color,
        steps,
        permissions,
      ]);

  factory AgentV2Info.fromJson(Map<String, dynamic> json) =>
      _$AgentV2InfoFromJson(json);

  Map<String, dynamic> toJson() => _$AgentV2InfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum AgentV2InfoModeEnum {
  @JsonValue(r'subagent')
  subagent(r'subagent'),
  @JsonValue(r'primary')
  primary(r'primary'),
  @JsonValue(r'all')
  all(r'all'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const AgentV2InfoModeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
