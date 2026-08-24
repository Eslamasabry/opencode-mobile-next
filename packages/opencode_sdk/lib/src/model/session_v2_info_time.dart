//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_v2_info_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionV2InfoTime {
  /// Returns a new [SessionV2InfoTime] instance.
  SessionV2InfoTime({
    required this.created,

    required this.updated,

    this.archived,
  });

  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final num created;

  @JsonKey(name: r'updated', required: true, includeIfNull: false)
  final num updated;

  @JsonKey(name: r'archived', required: false, includeIfNull: false)
  final num? archived;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionV2InfoTime &&
            runtimeType == other.runtimeType &&
            equals(
              [created, updated, archived],
              [other.created, other.updated, other.archived],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([created, updated, archived]);

  factory SessionV2InfoTime.fromJson(Map<String, dynamic> json) =>
      _$SessionV2InfoTimeFromJson(json);

  Map<String, dynamic> toJson() => _$SessionV2InfoTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
