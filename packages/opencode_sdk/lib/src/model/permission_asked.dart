//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/permission_asked_data.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_asked.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionAsked {
  /// Returns a new [PermissionAsked] instance.
  PermissionAsked({
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
    unknownEnumValue: PermissionAskedTypeEnum.unknownDefaultOpenApi,
  )
  final PermissionAskedTypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final PermissionAskedData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionAsked &&
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

  factory PermissionAsked.fromJson(Map<String, dynamic> json) =>
      _$PermissionAskedFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionAskedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PermissionAskedTypeEnum {
  @JsonValue(r'permission.asked')
  permissionPeriodAsked(r'permission.asked'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PermissionAskedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
