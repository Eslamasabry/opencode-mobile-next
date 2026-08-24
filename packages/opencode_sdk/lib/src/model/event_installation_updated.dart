//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/installation_updated_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_installation_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventInstallationUpdated {
  /// Returns a new [EventInstallationUpdated] instance.
  EventInstallationUpdated({
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
    unknownEnumValue: EventInstallationUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventInstallationUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final InstallationUpdatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventInstallationUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventInstallationUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventInstallationUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventInstallationUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventInstallationUpdatedTypeEnum {
  @JsonValue(r'installation.updated')
  installationPeriodUpdated(r'installation.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventInstallationUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
