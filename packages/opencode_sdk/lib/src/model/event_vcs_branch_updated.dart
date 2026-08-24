//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/vcs_branch_updated_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_vcs_branch_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventVcsBranchUpdated {
  /// Returns a new [EventVcsBranchUpdated] instance.
  EventVcsBranchUpdated({
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
    unknownEnumValue: EventVcsBranchUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventVcsBranchUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final VcsBranchUpdatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventVcsBranchUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventVcsBranchUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventVcsBranchUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventVcsBranchUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventVcsBranchUpdatedTypeEnum {
  @JsonValue(r'vcs.branch.updated')
  vcsPeriodBranchPeriodUpdated(r'vcs.branch.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventVcsBranchUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
