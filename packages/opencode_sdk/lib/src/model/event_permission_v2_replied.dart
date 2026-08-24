//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_replied_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_permission_v2_replied.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventPermissionV2Replied {
  /// Returns a new [EventPermissionV2Replied] instance.
  EventPermissionV2Replied({
    required this.id,

    required this.type,

    required this.properties,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventPermissionV2RepliedTypeEnum.unknownDefaultOpenApi,
  )
  final EventPermissionV2RepliedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final PermissionV2RepliedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventPermissionV2Replied &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventPermissionV2Replied.fromJson(Map<String, dynamic> json) =>
      _$EventPermissionV2RepliedFromJson(json);

  Map<String, dynamic> toJson() => _$EventPermissionV2RepliedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventPermissionV2RepliedTypeEnum {
  @JsonValue(r'permission.v2.replied')
  permissionPeriodV2PeriodReplied(r'permission.v2.replied'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventPermissionV2RepliedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
