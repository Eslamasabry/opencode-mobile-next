//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'global_event.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class GlobalEvent {
  /// Returns a new [GlobalEvent] instance.
  GlobalEvent({
    required this.directory,

    this.project,

    this.workspace,

    required this.payload,
  });

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  @JsonKey(name: r'project', required: false, includeIfNull: false)
  final String? project;

  @JsonKey(name: r'workspace', required: false, includeIfNull: false)
  final String? workspace;

  @JsonKey(name: r'payload', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion002 payload;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is GlobalEvent &&
            runtimeType == other.runtimeType &&
            equals(
              [directory, project, workspace, payload],
              [other.directory, other.project, other.workspace, other.payload],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([directory, project, workspace, payload]);

  factory GlobalEvent.fromJson(Map<String, dynamic> json) =>
      _$GlobalEventFromJson(json);

  Map<String, dynamic> toJson() => _$GlobalEventToJson(this);

  String toString() {
    return toJson().toString();
  }
}
