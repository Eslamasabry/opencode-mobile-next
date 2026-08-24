//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_asked_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_permission_asked.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventPermissionAsked {
  /// Returns a new [EventPermissionAsked] instance.
  EventPermissionAsked({
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
    unknownEnumValue: EventPermissionAskedTypeEnum.unknownDefaultOpenApi,
  )
  final EventPermissionAskedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final PermissionAskedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventPermissionAsked &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventPermissionAsked.fromJson(Map<String, dynamic> json) =>
      _$EventPermissionAskedFromJson(json);

  Map<String, dynamic> toJson() => _$EventPermissionAskedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventPermissionAskedTypeEnum {
  @JsonValue(r'permission.asked')
  permissionPeriodAsked(r'permission.asked'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventPermissionAskedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
