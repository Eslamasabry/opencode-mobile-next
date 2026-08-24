//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_steal_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncStealRequest {
  /// Returns a new [SyncStealRequest] instance.
  SyncStealRequest({required this.sessionID});

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncStealRequest &&
            runtimeType == other.runtimeType &&
            equals([sessionID], [other.sessionID]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([sessionID]);

  factory SyncStealRequest.fromJson(Map<String, dynamic> json) =>
      _$SyncStealRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SyncStealRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
