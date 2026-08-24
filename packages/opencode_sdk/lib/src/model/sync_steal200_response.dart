//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_steal200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncSteal200Response {
  /// Returns a new [SyncSteal200Response] instance.
  SyncSteal200Response({required this.sessionID});

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncSteal200Response &&
            runtimeType == other.runtimeType &&
            equals([sessionID], [other.sessionID]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([sessionID]);

  factory SyncSteal200Response.fromJson(Map<String, dynamic> json) =>
      _$SyncSteal200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$SyncSteal200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
