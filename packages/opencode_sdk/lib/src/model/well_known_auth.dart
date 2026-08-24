//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'well_known_auth.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WellKnownAuth {
  /// Returns a new [WellKnownAuth] instance.
  WellKnownAuth({required this.type, required this.key, required this.token});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: WellKnownAuthTypeEnum.unknownDefaultOpenApi,
  )
  final WellKnownAuthTypeEnum type;

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(name: r'token', required: true, includeIfNull: false)
  final String token;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WellKnownAuth &&
            runtimeType == other.runtimeType &&
            equals([type, key, token], [other.type, other.key, other.token]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, key, token]);

  factory WellKnownAuth.fromJson(Map<String, dynamic> json) =>
      _$WellKnownAuthFromJson(json);

  Map<String, dynamic> toJson() => _$WellKnownAuthToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum WellKnownAuthTypeEnum {
  @JsonValue(r'wellknown')
  wellknown(r'wellknown'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const WellKnownAuthTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
