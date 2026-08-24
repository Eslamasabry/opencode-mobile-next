//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/installation_updated_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_installation_update_available.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventInstallationUpdateAvailable {
  /// Returns a new [EventInstallationUpdateAvailable] instance.
  EventInstallationUpdateAvailable({
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
    unknownEnumValue:
        EventInstallationUpdateAvailableTypeEnum.unknownDefaultOpenApi,
  )
  final EventInstallationUpdateAvailableTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final InstallationUpdatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventInstallationUpdateAvailable &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventInstallationUpdateAvailable.fromJson(
    Map<String, dynamic> json,
  ) => _$EventInstallationUpdateAvailableFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EventInstallationUpdateAvailableToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventInstallationUpdateAvailableTypeEnum {
  @JsonValue(r'installation.update-available')
  installationPeriodUpdateAvailable(r'installation.update-available'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventInstallationUpdateAvailableTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
