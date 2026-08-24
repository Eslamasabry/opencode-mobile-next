//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union022.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_o_auth_method.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationOAuthMethod {
  /// Returns a new [IntegrationOAuthMethod] instance.
  IntegrationOAuthMethod({
    required this.id,

    required this.type,

    required this.label,

    this.prompts,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: IntegrationOAuthMethodTypeEnum.unknownDefaultOpenApi,
  )
  final IntegrationOAuthMethodTypeEnum type;

  @JsonKey(name: r'label', required: true, includeIfNull: false)
  final String label;

  @JsonKey(name: r'prompts', required: false, includeIfNull: false)
  final List<OpencodeSdkRawUnion022>? prompts;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationOAuthMethod &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, label, prompts],
              [other.id, other.type, other.label, other.prompts],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, label, prompts]);

  factory IntegrationOAuthMethod.fromJson(Map<String, dynamic> json) =>
      _$IntegrationOAuthMethodFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationOAuthMethodToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationOAuthMethodTypeEnum {
  @JsonValue(r'oauth')
  oauth(r'oauth'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationOAuthMethodTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
