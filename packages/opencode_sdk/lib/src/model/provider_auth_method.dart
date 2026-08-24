//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union016.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_auth_method.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderAuthMethod {
  /// Returns a new [ProviderAuthMethod] instance.
  ProviderAuthMethod({required this.type, required this.label, this.prompts});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProviderAuthMethodTypeEnum.unknownDefaultOpenApi,
  )
  final ProviderAuthMethodTypeEnum type;

  @JsonKey(name: r'label', required: true, includeIfNull: false)
  final String label;

  @JsonKey(name: r'prompts', required: false, includeIfNull: false)
  final List<OpencodeSdkRawUnion016>? prompts;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderAuthMethod &&
            runtimeType == other.runtimeType &&
            equals(
              [type, label, prompts],
              [other.type, other.label, other.prompts],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, label, prompts]);

  factory ProviderAuthMethod.fromJson(Map<String, dynamic> json) =>
      _$ProviderAuthMethodFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderAuthMethodToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderAuthMethodTypeEnum {
  @JsonValue(r'oauth')
  oauth(r'oauth'),
  @JsonValue(r'api')
  api(r'api'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderAuthMethodTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
