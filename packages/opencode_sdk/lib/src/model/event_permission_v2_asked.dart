//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_asked_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_permission_v2_asked.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventPermissionV2Asked {
  /// Returns a new [EventPermissionV2Asked] instance.
  EventPermissionV2Asked({
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
    unknownEnumValue: EventPermissionV2AskedTypeEnum.unknownDefaultOpenApi,
  )
  final EventPermissionV2AskedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final PermissionV2AskedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventPermissionV2Asked &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventPermissionV2Asked.fromJson(Map<String, dynamic> json) =>
      _$EventPermissionV2AskedFromJson(json);

  Map<String, dynamic> toJson() => _$EventPermissionV2AskedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventPermissionV2AskedTypeEnum {
  @JsonValue(r'permission.v2.asked')
  permissionPeriodV2PeriodAsked(r'permission.v2.asked'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventPermissionV2AskedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
