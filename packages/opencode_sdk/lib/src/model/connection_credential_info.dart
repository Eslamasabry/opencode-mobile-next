//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'connection_credential_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConnectionCredentialInfo {
  /// Returns a new [ConnectionCredentialInfo] instance.
  ConnectionCredentialInfo({
    required this.type,

    required this.id,

    required this.label,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ConnectionCredentialInfoTypeEnum.unknownDefaultOpenApi,
  )
  final ConnectionCredentialInfoTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'label', required: true, includeIfNull: false)
  final String label;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConnectionCredentialInfo &&
            runtimeType == other.runtimeType &&
            equals([type, id, label], [other.type, other.id, other.label]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, id, label]);

  factory ConnectionCredentialInfo.fromJson(Map<String, dynamic> json) =>
      _$ConnectionCredentialInfoFromJson(json);

  Map<String, dynamic> toJson() => _$ConnectionCredentialInfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ConnectionCredentialInfoTypeEnum {
  @JsonValue(r'credential')
  credential(r'credential'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ConnectionCredentialInfoTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
