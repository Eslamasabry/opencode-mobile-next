//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/file_edited_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_file_edited.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventFileEdited {
  /// Returns a new [EventFileEdited] instance.
  EventFileEdited({
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
    unknownEnumValue: EventFileEditedTypeEnum.unknownDefaultOpenApi,
  )
  final EventFileEditedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final FileEditedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventFileEdited &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventFileEdited.fromJson(Map<String, dynamic> json) =>
      _$EventFileEditedFromJson(json);

  Map<String, dynamic> toJson() => _$EventFileEditedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventFileEditedTypeEnum {
  @JsonValue(r'file.edited')
  filePeriodEdited(r'file.edited'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventFileEditedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
