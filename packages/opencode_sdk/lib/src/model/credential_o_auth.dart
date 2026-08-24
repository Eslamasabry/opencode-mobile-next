//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'credential_o_auth.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class CredentialOAuth {
  /// Returns a new [CredentialOAuth] instance.
  CredentialOAuth({
    required this.type,

    required this.methodID,

    required this.refresh,

    required this.access,

    required this.expires,

    this.metadata,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: CredentialOAuthTypeEnum.unknownDefaultOpenApi,
  )
  final CredentialOAuthTypeEnum type;

  @JsonKey(name: r'methodID', required: true, includeIfNull: false)
  final String methodID;

  @JsonKey(name: r'refresh', required: true, includeIfNull: false)
  final String refresh;

  @JsonKey(name: r'access', required: true, includeIfNull: false)
  final String access;

  // minimum: 0
  @JsonKey(name: r'expires', required: true, includeIfNull: false)
  final int expires;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is CredentialOAuth &&
            runtimeType == other.runtimeType &&
            equals(
              [type, methodID, refresh, access, expires, metadata],
              [
                other.type,
                other.methodID,
                other.refresh,
                other.access,
                other.expires,
                other.metadata,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, methodID, refresh, access, expires, metadata]);

  factory CredentialOAuth.fromJson(Map<String, dynamic> json) =>
      _$CredentialOAuthFromJson(json);

  Map<String, dynamic> toJson() => _$CredentialOAuthToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum CredentialOAuthTypeEnum {
  @JsonValue(r'oauth')
  oauth(r'oauth'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const CredentialOAuthTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
