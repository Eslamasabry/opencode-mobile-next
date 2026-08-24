//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_watcher_updated_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FileWatcherUpdatedData {
  /// Returns a new [FileWatcherUpdatedData] instance.
  FileWatcherUpdatedData({required this.file, required this.event});

  @JsonKey(name: r'file', required: true, includeIfNull: false)
  final String file;

  @JsonKey(
    name: r'event',
    required: true,
    includeIfNull: false,
    unknownEnumValue: FileWatcherUpdatedDataEventEnum.unknownDefaultOpenApi,
  )
  final FileWatcherUpdatedDataEventEnum event;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FileWatcherUpdatedData &&
            runtimeType == other.runtimeType &&
            equals([file, event], [other.file, other.event]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([file, event]);

  factory FileWatcherUpdatedData.fromJson(Map<String, dynamic> json) =>
      _$FileWatcherUpdatedDataFromJson(json);

  Map<String, dynamic> toJson() => _$FileWatcherUpdatedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum FileWatcherUpdatedDataEventEnum {
  @JsonValue(r'add')
  add(r'add'),
  @JsonValue(r'change')
  change(r'change'),
  @JsonValue(r'unlink')
  unlink(r'unlink'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const FileWatcherUpdatedDataEventEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
