//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'connection_env_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConnectionEnvInfo {
  /// Returns a new [ConnectionEnvInfo] instance.
  ConnectionEnvInfo({required this.type, required this.name});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ConnectionEnvInfoTypeEnum.unknownDefaultOpenApi,
  )
  final ConnectionEnvInfoTypeEnum type;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConnectionEnvInfo &&
            runtimeType == other.runtimeType &&
            equals([type, name], [other.type, other.name]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type, name]);

  factory ConnectionEnvInfo.fromJson(Map<String, dynamic> json) =>
      _$ConnectionEnvInfoFromJson(json);

  Map<String, dynamic> toJson() => _$ConnectionEnvInfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ConnectionEnvInfoTypeEnum {
  @JsonValue(r'env')
  env(r'env'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ConnectionEnvInfoTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
