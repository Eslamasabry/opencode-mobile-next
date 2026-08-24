//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'o_auth.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OAuth {
  /// Returns a new [OAuth] instance.
  OAuth({
    required this.type,

    required this.refresh,

    required this.access,

    required this.expires,

    this.accountId,

    this.enterpriseUrl,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: OAuthTypeEnum.unknownDefaultOpenApi,
  )
  final OAuthTypeEnum type;

  @JsonKey(name: r'refresh', required: true, includeIfNull: false)
  final String refresh;

  @JsonKey(name: r'access', required: true, includeIfNull: false)
  final String access;

  // minimum: 0
  @JsonKey(name: r'expires', required: true, includeIfNull: false)
  final int expires;

  @JsonKey(name: r'accountId', required: false, includeIfNull: false)
  final String? accountId;

  @JsonKey(name: r'enterpriseUrl', required: false, includeIfNull: false)
  final String? enterpriseUrl;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OAuth &&
            runtimeType == other.runtimeType &&
            equals(
              [type, refresh, access, expires, accountId, enterpriseUrl],
              [
                other.type,
                other.refresh,
                other.access,
                other.expires,
                other.accountId,
                other.enterpriseUrl,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        type,
        refresh,
        access,
        expires,
        accountId,
        enterpriseUrl,
      ]);

  factory OAuth.fromJson(Map<String, dynamic> json) => _$OAuthFromJson(json);

  Map<String, dynamic> toJson() => _$OAuthToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OAuthTypeEnum {
  @JsonValue(r'oauth')
  oauth(r'oauth'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OAuthTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
