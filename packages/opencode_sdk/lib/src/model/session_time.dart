//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionTime {
  /// Returns a new [SessionTime] instance.
  SessionTime({
    required this.created,

    required this.updated,

    this.compacting,

    this.archived,
  });

  // minimum: 0
  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final int created;

  // minimum: 0
  @JsonKey(name: r'updated', required: true, includeIfNull: false)
  final int updated;

  // minimum: 0
  @JsonKey(name: r'compacting', required: false, includeIfNull: false)
  final int? compacting;

  @JsonKey(name: r'archived', required: false, includeIfNull: false)
  final num? archived;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionTime &&
            runtimeType == other.runtimeType &&
            equals(
              [created, updated, compacting, archived],
              [other.created, other.updated, other.compacting, other.archived],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([created, updated, compacting, archived]);

  factory SessionTime.fromJson(Map<String, dynamic> json) =>
      _$SessionTimeFromJson(json);

  Map<String, dynamic> toJson() => _$SessionTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
