//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_events200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionEvents200Response {
  /// Returns a new [V2SessionEvents200Response] instance.
  V2SessionEvents200Response({
    required this.id,

    required this.event,

    required this.data,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'event', required: true, includeIfNull: false)
  final String event;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final String data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionEvents200Response &&
            runtimeType == other.runtimeType &&
            equals([id, event, data], [other.id, other.event, other.data]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, event, data]);

  factory V2SessionEvents200Response.fromJson(Map<String, dynamic> json) =>
      _$V2SessionEvents200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionEvents200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
