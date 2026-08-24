//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/tui_command_execute_data.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tui_command_execute.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class TuiCommandExecute {
  /// Returns a new [TuiCommandExecute] instance.
  TuiCommandExecute({
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
    unknownEnumValue: TuiCommandExecuteTypeEnum.unknownDefaultOpenApi,
  )
  final TuiCommandExecuteTypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final TuiCommandExecuteData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is TuiCommandExecute &&
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

  factory TuiCommandExecute.fromJson(Map<String, dynamic> json) =>
      _$TuiCommandExecuteFromJson(json);

  Map<String, dynamic> toJson() => _$TuiCommandExecuteToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum TuiCommandExecuteTypeEnum {
  @JsonValue(r'tui.command.execute')
  tuiPeriodCommandPeriodExecute(r'tui.command.execute'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const TuiCommandExecuteTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
