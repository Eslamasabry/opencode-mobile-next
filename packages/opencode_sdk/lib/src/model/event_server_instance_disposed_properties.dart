//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_server_instance_disposed_properties.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventServerInstanceDisposedProperties {
  /// Returns a new [EventServerInstanceDisposedProperties] instance.
  EventServerInstanceDisposedProperties({required this.directory});

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventServerInstanceDisposedProperties &&
            runtimeType == other.runtimeType &&
            equals([directory], [other.directory]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([directory]);

  factory EventServerInstanceDisposedProperties.fromJson(
    Map<String, dynamic> json,
  ) => _$EventServerInstanceDisposedPropertiesFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EventServerInstanceDisposedPropertiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}
