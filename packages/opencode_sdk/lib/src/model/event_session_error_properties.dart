//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union036.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_error_properties.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionErrorProperties {
  /// Returns a new [EventSessionErrorProperties] instance.
  EventSessionErrorProperties({this.sessionID, this.error});

  @JsonKey(name: r'sessionID', required: false, includeIfNull: false)
  final String? sessionID;

  @JsonKey(name: r'error', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion036? error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionErrorProperties &&
            runtimeType == other.runtimeType &&
            equals([sessionID, error], [other.sessionID, other.error]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, error]);

  factory EventSessionErrorProperties.fromJson(Map<String, dynamic> json) =>
      _$EventSessionErrorPropertiesFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionErrorPropertiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}
