//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_replied_data.dart';
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_v2_replied.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionV2Replied {
  /// Returns a new [PermissionV2Replied] instance.
  PermissionV2Replied({
    required this.id,

    this.metadata,

    required this.type,

    this.durable,

    this.location,

    required this.data,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PermissionV2RepliedTypeEnum.unknownDefaultOpenApi,
  )
  final PermissionV2RepliedTypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final PermissionV2RepliedData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionV2Replied &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, type, durable, location, data],
              [
                other.id,
                other.metadata,
                other.type,
                other.durable,
                other.location,
                other.data,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, type, durable, location, data]);

  factory PermissionV2Replied.fromJson(Map<String, dynamic> json) =>
      _$PermissionV2RepliedFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionV2RepliedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PermissionV2RepliedTypeEnum {
  @JsonValue(r'permission.v2.replied')
  permissionPeriodV2PeriodReplied(r'permission.v2.replied'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PermissionV2RepliedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
