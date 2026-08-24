//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'vcs_file_status.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class VcsFileStatus {
  /// Returns a new [VcsFileStatus] instance.
  VcsFileStatus({
    required this.file,

    required this.additions,

    required this.deletions,

    required this.status,
  });

  @JsonKey(name: r'file', required: true, includeIfNull: false)
  final String file;

  @JsonKey(name: r'additions', required: true, includeIfNull: false)
  final num additions;

  @JsonKey(name: r'deletions', required: true, includeIfNull: false)
  final num deletions;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: VcsFileStatusStatusEnum.unknownDefaultOpenApi,
  )
  final VcsFileStatusStatusEnum status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is VcsFileStatus &&
            runtimeType == other.runtimeType &&
            equals(
              [file, additions, deletions, status],
              [other.file, other.additions, other.deletions, other.status],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([file, additions, deletions, status]);

  factory VcsFileStatus.fromJson(Map<String, dynamic> json) =>
      _$VcsFileStatusFromJson(json);

  Map<String, dynamic> toJson() => _$VcsFileStatusToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum VcsFileStatusStatusEnum {
  @JsonValue(r'added')
  added(r'added'),
  @JsonValue(r'deleted')
  deleted(r'deleted'),
  @JsonValue(r'modified')
  modified(r'modified'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const VcsFileStatusStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
