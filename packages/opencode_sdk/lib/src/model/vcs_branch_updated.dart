//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/vcs_branch_updated_data.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'vcs_branch_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class VcsBranchUpdated {
  /// Returns a new [VcsBranchUpdated] instance.
  VcsBranchUpdated({
    required this.id,

    this.metadata,

    required this.type,

    this.durable,

    this.location,

    required this.data,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: VcsBranchUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final VcsBranchUpdatedTypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final VcsBranchUpdatedData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is VcsBranchUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, type, durable, location, data],
              [
                other.id,
                other.metadata,
                other.type,
                other.durable,
                other.location,
                other.data,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, type, durable, location, data]);

  factory VcsBranchUpdated.fromJson(Map<String, dynamic> json) =>
      _$VcsBranchUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$VcsBranchUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum VcsBranchUpdatedTypeEnum {
  @JsonValue(r'vcs.branch.updated')
  vcsPeriodBranchPeriodUpdated(r'vcs.branch.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const VcsBranchUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
