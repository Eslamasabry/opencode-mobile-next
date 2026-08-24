//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_auth_authorization.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderAuthAuthorization {
  /// Returns a new [ProviderAuthAuthorization] instance.
  ProviderAuthAuthorization({
    required this.url,

    required this.method,

    required this.instructions,
  });

  @JsonKey(name: r'url', required: true, includeIfNull: false)
  final String url;

  @JsonKey(
    name: r'method',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProviderAuthAuthorizationMethodEnum.unknownDefaultOpenApi,
  )
  final ProviderAuthAuthorizationMethodEnum method;

  @JsonKey(name: r'instructions', required: true, includeIfNull: false)
  final String instructions;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderAuthAuthorization &&
            runtimeType == other.runtimeType &&
            equals(
              [url, method, instructions],
              [other.url, other.method, other.instructions],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([url, method, instructions]);

  factory ProviderAuthAuthorization.fromJson(Map<String, dynamic> json) =>
      _$ProviderAuthAuthorizationFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderAuthAuthorizationToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderAuthAuthorizationMethodEnum {
  @JsonValue(r'auto')
  auto(r'auto'),
  @JsonValue(r'code')
  code(r'code'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderAuthAuthorizationMethodEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
