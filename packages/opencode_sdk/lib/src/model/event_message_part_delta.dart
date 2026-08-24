//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/message_part_delta_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_message_part_delta.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventMessagePartDelta {
  /// Returns a new [EventMessagePartDelta] instance.
  EventMessagePartDelta({
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
    unknownEnumValue: EventMessagePartDeltaTypeEnum.unknownDefaultOpenApi,
  )
  final EventMessagePartDeltaTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final MessagePartDeltaData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventMessagePartDelta &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventMessagePartDelta.fromJson(Map<String, dynamic> json) =>
      _$EventMessagePartDeltaFromJson(json);

  Map<String, dynamic> toJson() => _$EventMessagePartDeltaToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventMessagePartDeltaTypeEnum {
  @JsonValue(r'message.part.delta')
  messagePeriodPartPeriodDelta(r'message.part.delta'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventMessagePartDeltaTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
