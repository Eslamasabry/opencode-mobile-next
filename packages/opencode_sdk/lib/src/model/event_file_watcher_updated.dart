//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/file_watcher_updated_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_file_watcher_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventFileWatcherUpdated {
  /// Returns a new [EventFileWatcherUpdated] instance.
  EventFileWatcherUpdated({
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
    unknownEnumValue: EventFileWatcherUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventFileWatcherUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final FileWatcherUpdatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventFileWatcherUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventFileWatcherUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventFileWatcherUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventFileWatcherUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventFileWatcherUpdatedTypeEnum {
  @JsonValue(r'file.watcher.updated')
  filePeriodWatcherPeriodUpdated(r'file.watcher.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventFileWatcherUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
