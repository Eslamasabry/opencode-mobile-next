//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'api_auth.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ApiAuth {
  /// Returns a new [ApiAuth] instance.
  ApiAuth({required this.type, required this.key, this.metadata});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ApiAuthTypeEnum.unknownDefaultOpenApi,
  )
  final ApiAuthTypeEnum type;

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Map<String, String>? metadata;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ApiAuth &&
            runtimeType == other.runtimeType &&
            equals(
              [type, key, metadata],
              [other.type, other.key, other.metadata],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, key, metadata]);

  factory ApiAuth.fromJson(Map<String, dynamic> json) =>
      _$ApiAuthFromJson(json);

  Map<String, dynamic> toJson() => _$ApiAuthToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ApiAuthTypeEnum {
  @JsonValue(r'api')
  api(r'api'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ApiAuthTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
