//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_replay200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncReplay200Response {
  /// Returns a new [SyncReplay200Response] instance.
  SyncReplay200Response({required this.sessionID});

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncReplay200Response &&
            runtimeType == other.runtimeType &&
            equals([sessionID], [other.sessionID]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([sessionID]);

  factory SyncReplay200Response.fromJson(Map<String, dynamic> json) =>
      _$SyncReplay200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$SyncReplay200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
