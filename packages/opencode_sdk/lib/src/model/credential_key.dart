//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'credential_key.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class CredentialKey {
  /// Returns a new [CredentialKey] instance.
  CredentialKey({required this.type, required this.key, this.metadata});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: CredentialKeyTypeEnum.unknownDefaultOpenApi,
  )
  final CredentialKeyTypeEnum type;

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is CredentialKey &&
            runtimeType == other.runtimeType &&
            equals(
              [type, key, metadata],
              [other.type, other.key, other.metadata],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, key, metadata]);

  factory CredentialKey.fromJson(Map<String, dynamic> json) =>
      _$CredentialKeyFromJson(json);

  Map<String, dynamic> toJson() => _$CredentialKeyToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum CredentialKeyTypeEnum {
  @JsonValue(r'key')
  key(r'key'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const CredentialKeyTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
