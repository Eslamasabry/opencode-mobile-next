//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/provider_auth_error1_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_auth_error1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderAuthError1 {
  /// Returns a new [ProviderAuthError1] instance.
  ProviderAuthError1({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProviderAuthError1NameEnum.unknownDefaultOpenApi,
  )
  final ProviderAuthError1NameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final ProviderAuthError1Data data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderAuthError1 &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory ProviderAuthError1.fromJson(Map<String, dynamic> json) =>
      _$ProviderAuthError1FromJson(json);

  Map<String, dynamic> toJson() => _$ProviderAuthError1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderAuthError1NameEnum {
  @JsonValue(r'BadRequest')
  badRequest(r'BadRequest'),
  @JsonValue(r'ProviderAuthOauthMissing')
  providerAuthOauthMissing(r'ProviderAuthOauthMissing'),
  @JsonValue(r'ProviderAuthOauthCodeMissing')
  providerAuthOauthCodeMissing(r'ProviderAuthOauthCodeMissing'),
  @JsonValue(r'ProviderAuthOauthCallbackFailed')
  providerAuthOauthCallbackFailed(r'ProviderAuthOauthCallbackFailed'),
  @JsonValue(r'ProviderAuthValidationFailed')
  providerAuthValidationFailed(r'ProviderAuthValidationFailed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderAuthError1NameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
