//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_v2_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionV2Source {
  /// Returns a new [PermissionV2Source] instance.
  PermissionV2Source({
    required this.type,

    required this.messageID,

    required this.callID,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PermissionV2SourceTypeEnum.unknownDefaultOpenApi,
  )
  final PermissionV2SourceTypeEnum type;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionV2Source &&
            runtimeType == other.runtimeType &&
            equals(
              [type, messageID, callID],
              [other.type, other.messageID, other.callID],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, messageID, callID]);

  factory PermissionV2Source.fromJson(Map<String, dynamic> json) =>
      _$PermissionV2SourceFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionV2SourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PermissionV2SourceTypeEnum {
  @JsonValue(r'tool')
  tool(r'tool'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PermissionV2SourceTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
