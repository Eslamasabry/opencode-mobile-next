//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/policy_effect.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_v2_experimental_policy.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigV2ExperimentalPolicy {
  /// Returns a new [ConfigV2ExperimentalPolicy] instance.
  ConfigV2ExperimentalPolicy({
    required this.action,

    required this.effect,

    required this.resource,
  });

  @JsonKey(
    name: r'action',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        ConfigV2ExperimentalPolicyActionEnum.unknownDefaultOpenApi,
  )
  final ConfigV2ExperimentalPolicyActionEnum action;

  @JsonKey(
    name: r'effect',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PolicyEffect.unknownDefaultOpenApi,
  )
  final PolicyEffect effect;

  @JsonKey(name: r'resource', required: true, includeIfNull: false)
  final String resource;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigV2ExperimentalPolicy &&
            runtimeType == other.runtimeType &&
            equals(
              [action, effect, resource],
              [other.action, other.effect, other.resource],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([action, effect, resource]);

  factory ConfigV2ExperimentalPolicy.fromJson(Map<String, dynamic> json) =>
      _$ConfigV2ExperimentalPolicyFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigV2ExperimentalPolicyToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ConfigV2ExperimentalPolicyActionEnum {
  @JsonValue(r'provider.use')
  providerPeriodUse(r'provider.use'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ConfigV2ExperimentalPolicyActionEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
