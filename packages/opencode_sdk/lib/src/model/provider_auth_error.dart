//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/provider_auth_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_auth_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderAuthError {
  /// Returns a new [ProviderAuthError] instance.
  ProviderAuthError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProviderAuthErrorNameEnum.unknownDefaultOpenApi,
  )
  final ProviderAuthErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final ProviderAuthErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderAuthError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory ProviderAuthError.fromJson(Map<String, dynamic> json) =>
      _$ProviderAuthErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderAuthErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderAuthErrorNameEnum {
  @JsonValue(r'ProviderAuthError')
  providerAuthError(r'ProviderAuthError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderAuthErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
