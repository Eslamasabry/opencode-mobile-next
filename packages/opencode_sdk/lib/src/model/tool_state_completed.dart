//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/tool_state_completed_time.dart';
import 'package:opencode_sdk/src/model/file_part.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_state_completed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolStateCompleted {
  /// Returns a new [ToolStateCompleted] instance.
  ToolStateCompleted({
    required this.status,

    required this.input,

    required this.output,

    required this.title,

    required this.metadata,

    required this.time,

    this.attachments,
  });

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ToolStateCompletedStatusEnum.unknownDefaultOpenApi,
  )
  final ToolStateCompletedStatusEnum status;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final Object input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final String output;

  @JsonKey(name: r'title', required: true, includeIfNull: false)
  final String title;

  @JsonKey(name: r'metadata', required: true, includeIfNull: false)
  final Object metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final ToolStateCompletedTime time;

  @JsonKey(name: r'attachments', required: false, includeIfNull: false)
  final List<FilePart>? attachments;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolStateCompleted &&
            runtimeType == other.runtimeType &&
            equals(
              [status, input, output, title, metadata, time, attachments],
              [
                other.status,
                other.input,
                other.output,
                other.title,
                other.metadata,
                other.time,
                other.attachments,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        status,
        input,
        output,
        title,
        metadata,
        time,
        attachments,
      ]);

  factory ToolStateCompleted.fromJson(Map<String, dynamic> json) =>
      _$ToolStateCompletedFromJson(json);

  Map<String, dynamic> toJson() => _$ToolStateCompletedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ToolStateCompletedStatusEnum {
  @JsonValue(r'completed')
  completed(r'completed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ToolStateCompletedStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
