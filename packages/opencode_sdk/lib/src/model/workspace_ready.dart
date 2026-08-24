//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/experimental_project_copy_generate_name200_response.dart';
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'workspace_ready.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorkspaceReady {
  /// Returns a new [WorkspaceReady] instance.
  WorkspaceReady({
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
    unknownEnumValue: WorkspaceReadyTypeEnum.unknownDefaultOpenApi,
  )
  final WorkspaceReadyTypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final ExperimentalProjectCopyGenerateName200Response data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorkspaceReady &&
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

  factory WorkspaceReady.fromJson(Map<String, dynamic> json) =>
      _$WorkspaceReadyFromJson(json);

  Map<String, dynamic> toJson() => _$WorkspaceReadyToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum WorkspaceReadyTypeEnum {
  @JsonValue(r'workspace.ready')
  workspacePeriodReady(r'workspace.ready'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const WorkspaceReadyTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
