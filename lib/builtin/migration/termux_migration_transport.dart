import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';

import '../../domain/termux_migration.dart';
import '../../termux/bridge.dart';

abstract class TermuxMigrationTransport {
  Future<TermuxMigrationSource> inspect();
  Future<TermuxMigrationArchive> pack(
    String jobId,
    TermuxMigrationItem item, {
    required int maxBytes,
  });
  Future<void> copy(
    String jobId,
    TermuxMigrationItem item,
    TermuxMigrationArchive archive,
    File destination, {
    required bool Function() cancelled,
  });
  Future<void> cancel(String jobId);
}

typedef MigrationTermuxRun =
    Future<TermuxCommandResult> Function(
      String script, {
      required Duration timeout,
    });

/// Confidential archives cross only an authenticated IPv4 loopback socket.
/// Commands have their own deadline: a bridge timeout alone does not kill them.
/// No output, source paths, bearer or raw exception is exposed to the caller.
class BridgeTermuxMigrationTransport implements TermuxMigrationTransport {
  BridgeTermuxMigrationTransport({MigrationTermuxRun? run})
    : _run = run ?? _bridgeRun;

  final MigrationTermuxRun _run;
  final _cancelledJobs = <String>{};
  static const maximumBytes = 512 * 1024 * 1024;
  static const chunkBytes = 1024 * 1024;
  static const commandTimeout = Duration(seconds: 110);

  static Future<TermuxCommandResult> _bridgeRun(
    String script, {
    required Duration timeout,
  }) => TermuxBridge.run(script, timeout: timeout);

  static void _id(String value) {
    if (!RegExp(r'^[a-f0-9]{32}$').hasMatch(value)) {
      throw const TermuxMigrationException(
        TermuxMigrationFailure.invalidSelection,
      );
    }
  }

  Future<String> _command(String script) async {
    try {
      // Suppress stderr inside the timeout too: callbacks never carry filenames.
      final result = await _run('''
# oc-migration-command
export LC_ALL=C
 timeout -k 2 90 bash -s 2>/dev/null <<'OC_MIGRATION_SCRIPT'
set -euo pipefail
umask 077
$script
OC_MIGRATION_SCRIPT
''', timeout: commandTimeout).timeout(commandTimeout);
      if (!result.successful) {
        throw const TermuxMigrationException(
          TermuxMigrationFailure.unavailable,
        );
      }
      final output = result.stdout.trim();
      if (output.split('\n').any((line) => line.startsWith('failure|'))) {
        final name = output
            .split('\n')
            .lastWhere((line) => line.startsWith('failure|'))
            .substring(8);
        throw TermuxMigrationException(
          TermuxMigrationFailure.values.firstWhere(
            (value) => value.name == name,
            orElse: () => TermuxMigrationFailure.unavailable,
          ),
        );
      }
      if (output.length > 2048) {
        throw const TermuxMigrationException(
          TermuxMigrationFailure.unavailable,
        );
      }
      return output;
    } on TermuxMigrationException {
      rethrow;
    } on TimeoutException {
      throw const TermuxMigrationException(TermuxMigrationFailure.timedOut);
    } catch (_) {
      throw const TermuxMigrationException(TermuxMigrationFailure.unavailable);
    }
  }

  static const _roots = r'''
fail() { printf 'failure|%s\n' "$1"; exit 0; }
base="$PREFIX/var/lib/proot-distro"
if [ -d "$base/containers/opencode-ubuntu/rootfs" ] && [ -d "$base/installed-rootfs/opencode-ubuntu" ]; then fail unsupportedEntry; fi
if [ -d "$base/containers/opencode-ubuntu/rootfs" ]; then
  root="$base/containers/opencode-ubuntu/rootfs"
elif [ -d "$base/installed-rootfs/opencode-ubuntu" ]; then
  root="$base/installed-rootfs/opencode-ubuntu"
else fail unavailable; fi
[ ! -L "$root" ] && [ ! -L "$root/root" ] || fail unsupportedEntry
home="$root/root"
command -v tar >/dev/null && command -v sha256sum >/dev/null && command -v curl >/dev/null || fail unavailable
# Fixed sources only. Configuration/authentication is exported privately, never
# executed or activated by the transport. The backend chooses import policy.
safe_path() {
  local current= component rest="$1"
  while [ -n "$rest" ]; do
    component=${rest%%/*}
    if [ "$rest" = "$component" ]; then rest=; else rest=${rest#*/}; fi
    current=${current:+$current/}$component
    [ ! -L "$current" ] || fail unsupportedEntry
  done
}
select_item() {
  selected="$1"
  cd "$home"
  case "$1" in
    projects) safe_path projects; cd "$home/projects" 2>/dev/null || return 1; paths=(.) ;;
    config) cd "$home"; paths=(.config/opencode .oc-opencode2/config) ;;
    sessions) cd "$home"; paths=(.local/share/opencode .oc-opencode2/data) ;;
    gitConfig) cd "$home"; paths=(.gitconfig) ;;
    shellFiles) cd "$home"; paths=(.bashrc .bash_profile .profile .zshrc) ;;
    aiTeam)
      files=${PREFIX%/*}; cd "$files"
      relative=${root#"$files/"}
      paths=(home/.oc/aiteam home/.gc home/.dolt "$relative/root/aiteam" "$relative/root/.gc" "$relative/root/.dolt" "$relative/root/.oc-aiteam") ;;

    *) fail invalidSelection ;;
  esac
  for path in "${paths[@]}"; do safe_path "$path"; done
}
# proot-distro's link2symlink turned every Git hard link into a symlink to a
# file in the container's private .l2s store. Those are the only links the
# transport accepts, and only as their target's contents: the target lives in
# Termux storage the app cannot read, so a copied link would dangle.
is_l2s_link() {
  local target
  [ -L "$1" ] || return 1
  target=$(readlink "$1") || return 1
  case "$target" in */proot-distro/*/.l2s/.l2s.*) return 0 ;; esac
  return 1
}
# Reject other links and devices rather than follow them out of an allowed tree.
# Counts and logical bytes are metadata only; find names never leave Termux.
measure_item() {
  count=0; bytes=0
  for path in "${paths[@]}"; do
    [ -e "$path" ] || [ -L "$path" ] || continue
    while IFS= read -r -d '' entry; do
      if is_l2s_link "$entry"; then
        [ -f "$entry" ] && [ -r "$entry" ] || fail linkedFileUnreadable
        size=$(stat -L -c %s "$entry") || fail linkedFileUnreadable
        count=$((count + 1)); bytes=$((bytes + size))
        [ "$count" -le 20000 ] && [ "$bytes" -le 536870912 ] || fail tooLarge
      else fail unsupportedEntry; fi
    done < <(find "$path" ! -type f ! -type d -print0)
    if [ "$selected" = projects ] && [ -n "$(find "$path" -name .git -type f -print -quit)" ]; then fail unsupportedEntry; fi
    while IFS= read -r size; do
      count=$((count + 1)); bytes=$((bytes + size))
      [ "$count" -le 20000 ] && [ "$bytes" -le 536870912 ] || fail tooLarge
    done < <(find "$path" -type f -printf '%s\n')
    dirs=$(find "$path" -type d -printf 'x\n' | wc -l)
    count=$((count + dirs)); [ "$count" -le 20000 ] || fail tooLarge
  done
}
''';

  @override
  Future<TermuxMigrationSource> inspect() async {
    final output = await _command('''
$_roots
free=\$(df -Pk "\$HOME" | awk 'END {printf "%.0f", \$4 * 1024}')
[[ "\$free" =~ ^[0-9]+\$ ]] || fail storage
printf 'free|%s\\n' "\$free"
for item in projects config sessions gitConfig shellFiles aiTeam; do
  metadata=\$(
    if select_item "\$item"; then
      measure_item
      printf 'item|%s|%s|%s\\n' "\$item" "\$bytes" "\$count"
    else printf 'item|%s|0|0\\n' "\$item"; fi
  )
  case "\$metadata" in
    'failure|'*) printf 'problem|%s|%s\\n' "\$item" "\${metadata#failure|}" ;;
    *) printf '%s\\n' "\$metadata" ;;
  esac
done
if [ -f "\$HOME/.oc/opencode2-data-mode" ] && [ ! -L "\$HOME/.oc/opencode2-data-mode" ] && grep -qxF isolated "\$HOME/.oc/opencode2-data-mode"; then printf 'isolated|1\\n'; fi
''');
    try {
      final items = <TermuxMigrationSize>[];
      int? free;
      var isolated = false;
      for (final line in const LineSplitter().convert(output)) {
        final parts = line.split('|');
        if (parts.length == 2 && parts[0] == 'free') {
          free = int.parse(parts[1]);
        } else if (parts.length == 2 && line == 'isolated|1') {
          isolated = true;
        } else if (parts.length == 3 && parts[0] == 'problem') {
          final item = TermuxMigrationItem.values.byName(parts[1]);
          final problem = TermuxMigrationFailure.values.byName(parts[2]);
          if (items.any((entry) => entry.item == item)) {
            throw const FormatException();
          }
          items.add(TermuxMigrationSize(item, 0, 0, problem: problem));
        } else if (parts.length == 4 && parts[0] == 'item') {
          final item = TermuxMigrationItem.values.byName(parts[1]);
          final bytes = int.parse(parts[2]);
          final count = int.parse(parts[3]);
          if (bytes < 0 ||
              bytes > maximumBytes ||
              count < 0 ||
              count > 20000 ||
              items.any((entry) => entry.item == item)) {
            throw const FormatException();
          }
          items.add(TermuxMigrationSize(item, bytes, count));
        } else {
          throw const FormatException();
        }
      }
      if (free == null || free < 0) throw const FormatException();
      return TermuxMigrationSource(
        items: items,
        freeBytes: free,
        oc2Isolated: isolated,
      );
    } on TermuxMigrationException {
      rethrow;
    } catch (_) {
      throw const TermuxMigrationException(TermuxMigrationFailure.unavailable);
    }
  }

  @override
  Future<TermuxMigrationArchive> pack(
    String jobId,
    TermuxMigrationItem item, {
    required int maxBytes,
  }) async {
    _id(jobId);
    if (maxBytes <= 0 || maxBytes > maximumBytes) {
      throw const TermuxMigrationException(TermuxMigrationFailure.tooLarge);
    }
    _cancelledJobs.remove(jobId);
    final output = await _command('''
$_roots
job="\$HOME/.oc/migration/$jobId"
[ ! -L "\$HOME/.oc" ] && [ ! -L "\$HOME/.oc/migration" ] && [ ! -L "\$job" ] || fail unsupportedEntry
mkdir -p "\$job"; chmod 700 "\$HOME/.oc/migration" "\$job"
archive="\$job/${item.name}.tar"
for candidate in "\$job/packing.lock" "\$job/cancel" "\$archive" "\$archive.ready" "\$archive.ready.tmp" "\$archive.part" "\$archive.list" "\$archive.links"; do
  [ ! -L "\$candidate" ] || fail unsupportedEntry
  [ ! -e "\$candidate" ] || [ -f "\$candidate" ] || fail unsupportedEntry
  [ ! -e "\$candidate" ] || [ "\$(stat -c %h "\$candidate")" = 1 ] || fail unsupportedEntry
done
# Resume clears a cancellation marker only after the previous pack released
# this job lock. The previous command may outlive the bridge callback.
exec 9>"\$job/packing.lock"; flock -n 9 || fail sourceBusy
rm -f "\$job/cancel"

if [ -f "\$archive.ready" ]; then
  cat "\$archive.ready"
  exit 0
fi
select_item ${item.name} || fail sourceChanged
measure_item
# USTAR overhead and final padding, conservatively reserved before writing.
[ \$((bytes + count * 1024 + 10240)) -le $maxBytes ] || fail tooLarge
free=\$(df -Pk "\$job" | awk 'END {printf "%.0f", \$4 * 1024}')
[ "\$free" -ge \$((bytes + count * 1024 + 10240)) ] || fail storage
# Ordinary entries go in as they are. The link2symlink links are archived as
# regular files holding their target's contents (--dereference applies to that
# list only).
: > "\$archive.list"; : > "\$archive.links"
for path in "\${paths[@]}"; do
  [ -e "\$path" ] || continue
  while IFS= read -r -d '' entry; do
    if is_l2s_link "\$entry"; then printf '%s\\0' "\$entry" >> "\$archive.links"
    else printf '%s\\0' "\$entry" >> "\$archive.list"; fi
  done < <(find "\$path" -print0)
done
# All regular hardlinks become independent regular entries, never links.
# ulimit bounds a concurrently growing source. tar detects changed files.
ulimit -f \$((($maxBytes + 1023) / 1024))
if ! tar --format=ustar --hard-dereference --no-recursion --null -T "\$archive.list" -cf "\$archive.part" >/dev/null 2>&1; then fail sourceChanged; fi
if [ -s "\$archive.links" ]; then
  if ! tar --format=ustar --dereference --no-recursion --null -T "\$archive.links" -rf "\$archive.part" >/dev/null 2>&1; then fail linkedFileUnreadable; fi
fi
rm -f "\$archive.links"
[ ! -f "\$job/cancel" ] || fail cancelled
size=\$(stat -c %s "\$archive.part")
[ "\$size" -le $maxBytes ] || fail tooLarge
hash=\$(sha256sum "\$archive.part"); hash=\${hash%% *}
mv "\$archive.part" "\$archive"
printf 'archive|%s|%s\\n' "\$size" "\$hash" > "\$archive.ready.tmp"
mv "\$archive.ready.tmp" "\$archive.ready"
cat "\$archive.ready"
''');
    final parts = output.split('|');
    final bytes = parts.length == 3 ? int.tryParse(parts[1]) : null;
    if (parts.length != 3 ||
        parts[0] != 'archive' ||
        bytes == null ||
        bytes < 1024 ||
        bytes > maxBytes ||
        !RegExp(r'^[a-f0-9]{64}$').hasMatch(parts[2])) {
      throw const TermuxMigrationException(
        TermuxMigrationFailure.invalidArchive,
      );
    }
    return TermuxMigrationArchive(bytes: bytes, sha256: parts[2]);
  }

  @override
  Future<void> copy(
    String jobId,
    TermuxMigrationItem item,
    TermuxMigrationArchive archive,
    File destination, {
    required bool Function() cancelled,
  }) async {
    _id(jobId);
    if (archive.bytes < 1024 ||
        archive.bytes > maximumBytes ||
        !RegExp(r'^[a-f0-9]{64}$').hasMatch(archive.sha256)) {
      throw const TermuxMigrationException(
        TermuxMigrationFailure.invalidArchive,
      );
    }
    bool stopped() => cancelled() || _cancelledJobs.contains(jobId);
    void check() {
      if (stopped()) {
        throw const TermuxMigrationException(TermuxMigrationFailure.cancelled);
      }
    }

    HttpServer? server;
    RandomAccessFile? file;
    Timer? timer;
    try {
      check();
      await _prepareDestination(jobId, destination);
      final checkpoint = File('${destination.path}.offset');
      var offset = 0;
      if (await checkpoint.exists() && await destination.exists()) {
        final value = jsonDecode(await checkpoint.readAsString()) as Map;
        if (value['sha256'] == archive.sha256 &&
            value['bytes'] == archive.bytes &&
            value['offset'] is int &&
            value['offset'] >= 0 &&
            value['offset'] <= archive.bytes &&
            (value['offset'] == archive.bytes ||
                value['offset'] % chunkBytes == 0)) {
          offset = value['offset'] as int;
        }
      }
      await destination.parent.create(recursive: true);
      final target = await destination.open(mode: FileMode.append);
      file = target;
      if (await target.length() < offset) offset = 0;
      await target.truncate(offset);
      await target.setPosition(offset);
      await _private(destination);
      final token = base64Url.encode(
        List<int>.generate(32, (_) => Random.secure().nextInt(256)),
      );
      server = await HttpServer.bind(
        InternetAddress.loopbackIPv4,
        0,
        shared: false,
      );
      server.idleTimeout = const Duration(seconds: 5);
      final listener = server;
      Completer<void>? received;
      timer = Timer.periodic(const Duration(milliseconds: 100), (_) {
        if (stopped()) {
          unawaited(listener.close(force: true));
          if (received != null && !received.isCompleted) {
            received.completeError(
              const TermuxMigrationException(TermuxMigrationFailure.cancelled),
            );
          }
        }
      });
      var accepting = false;
      server.listen((request) async {
        if (accepting ||
            received == null ||
            received.isCompleted ||
            request.method != 'PUT' ||
            request.uri.path != '/archive' ||
            request.headers.value(HttpHeaders.authorizationHeader) !=
                'Bearer $token' ||
            request.headers.value('x-offset') != '$offset') {
          await _respond(request, HttpStatus.forbidden);
          return;
        }
        accepting = true;
        final completion = received;
        final expected = min(chunkBytes, archive.bytes - offset);
        var bytes = 0;
        try {
          await for (final chunk in request.timeout(
            const Duration(seconds: 10),
          )) {
            check();
            bytes += chunk.length;
            if (bytes > expected) throw const FormatException();
            await file!.writeFrom(chunk);
          }
          if (bytes != expected) throw const FormatException();
          await file!.flush();
          offset += bytes;
          final temporary = File('${checkpoint.path}.tmp');
          await temporary.writeAsString(
            jsonEncode({
              'sha256': archive.sha256,
              'bytes': archive.bytes,
              'offset': offset,
            }),
            flush: true,
          );
          await _private(temporary);
          await temporary.rename(checkpoint.path);
          await _respond(request, HttpStatus.noContent);
          completion.complete();
        } catch (_) {
          await _respond(request, HttpStatus.badRequest);
          if (!completion.isCompleted) {
            completion.completeError(
              const TermuxMigrationException(
                TermuxMigrationFailure.invalidArchive,
              ),
            );
          }
        } finally {
          accepting = false;
        }
      });
      while (offset < archive.bytes) {
        check();
        final receiving = Completer<void>();
        received = receiving;
        // Attach the error handler before dispatching the producing command.
        final body = receiving.future;
        final command = _command('''
fail() { printf 'failure|%s\\n' "\$1"; exit 0; }
oc_curl_cfg=\$(mktemp "\${TMPDIR:-\$HOME}/.oc-curl.XXXXXX") || fail unavailable
trap 'rm -f "\$oc_curl_cfg"' EXIT
# The token reaches curl as a private config file, never as an argument.
printf 'header = "Authorization: Bearer %s"\\n' '$token' > "\$oc_curl_cfg"
job="\$HOME/.oc/migration/$jobId"
archive="\$job/${item.name}.tar"
for directory in "\$HOME/.oc" "\$HOME/.oc/migration" "\$job"; do
  [ -d "\$directory" ] && [ ! -L "\$directory" ] || fail unsupportedEntry
done
for candidate in "\$archive" "\$archive.ready" "\$job/cancel"; do
  [ ! -L "\$candidate" ] || fail unsupportedEntry
  [ ! -e "\$candidate" ] || [ -f "\$candidate" ] || fail unsupportedEntry
done
[ ! -f "\$HOME/.oc/migration/$jobId/cancel" ] || fail cancelled
[ -f "\$archive.ready" ] || fail sourceChanged
[ "\$(cat "\$archive.ready")" = 'archive|${archive.bytes}|${archive.sha256}' ] || fail sourceChanged
dd if="\$archive" bs=$chunkBytes skip=${offset ~/ chunkBytes} count=1 2>/dev/null | curl --silent --fail --max-time 30 --connect-timeout 3 --request PUT -K "\$oc_curl_cfg" --header 'x-offset: $offset' --data-binary @- 'http://127.0.0.1:${server.port}/archive' >/dev/null 2>&1 || fail unavailable
printf 'copied\\n'
''');
        try {
          await Future.wait([
            command,
            body.timeout(const Duration(seconds: 40)),
          ], eagerError: true);
        } catch (_) {
          // Complete the receiver too when Termux disappears before uploading;
          // do not retain a live timeout/receive operation after this attempt.
          if (!receiving.isCompleted) {
            receiving.completeError(
              const TermuxMigrationException(
                TermuxMigrationFailure.unavailable,
              ),
            );
          }
          rethrow;
        }
        check();
      }
      await target.close();
      file = null;
      final digest = await sha256.bind(destination.openRead()).first;
      if (digest.toString() != archive.sha256) {
        await checkpoint.delete();
        throw const TermuxMigrationException(
          TermuxMigrationFailure.checksumMismatch,
        );
      }
    } on TermuxMigrationException {
      if (stopped()) {
        throw const TermuxMigrationException(TermuxMigrationFailure.cancelled);
      }
      rethrow;
    } on TimeoutException {
      throw TermuxMigrationException(
        stopped()
            ? TermuxMigrationFailure.cancelled
            : TermuxMigrationFailure.timedOut,
      );
    } catch (_) {
      throw TermuxMigrationException(
        stopped()
            ? TermuxMigrationFailure.cancelled
            : TermuxMigrationFailure.storage,
      );
    } finally {
      timer?.cancel();
      await server?.close(force: true);
      await file?.close();
    }
  }

  static Future<void> _prepareDestination(
    String jobId,
    File destination,
  ) async {
    final job = destination.parent;
    final migrations = job.parent;
    // Restrict chmod to the transport's two owned staging directories.
    if (!job.path.endsWith('/$jobId') ||
        !migrations.path.endsWith('/migrations')) {
      throw const TermuxMigrationException(
        TermuxMigrationFailure.destinationConflict,
      );
    }
    for (final directory in [migrations, job]) {
      final type = await FileSystemEntity.type(
        directory.path,
        followLinks: false,
      );
      if (type != FileSystemEntityType.notFound &&
          type != FileSystemEntityType.directory) {
        throw const TermuxMigrationException(
          TermuxMigrationFailure.destinationConflict,
        );
      }
    }
    for (final path in [
      destination.path,
      '${destination.path}.offset',
      '${destination.path}.offset.tmp',
    ]) {
      final type = await FileSystemEntity.type(path, followLinks: false);
      if (type != FileSystemEntityType.notFound &&
          type != FileSystemEntityType.file) {
        throw const TermuxMigrationException(
          TermuxMigrationFailure.destinationConflict,
        );
      }
    }
    await job.create(recursive: true);
    for (final directory in [migrations, job]) {
      if (await FileSystemEntity.type(directory.path, followLinks: false) !=
          FileSystemEntityType.directory) {
        throw const TermuxMigrationException(
          TermuxMigrationFailure.destinationConflict,
        );
      }
      final result = await Process.run('chmod', ['700', directory.path]);
      if (result.exitCode != 0) {
        throw const TermuxMigrationException(TermuxMigrationFailure.storage);
      }
    }
  }

  static Future<void> _respond(HttpRequest request, int status) async {
    try {
      request.response.statusCode = status;
      request.response.persistentConnection = false;
      await request.response.close();
    } catch (_) {
      // A peer may disappear on cancellation. Never leak raw socket exceptions.
    }
  }

  static Future<void> _private(File file) async {
    final result = await Process.run('chmod', ['600', file.path]);
    if (result.exitCode != 0) {
      throw const TermuxMigrationException(TermuxMigrationFailure.storage);
    }
  }

  @override
  Future<void> cancel(String jobId) async {
    _id(jobId);
    _cancelledJobs.add(jobId);
    await _command('''
job="\$HOME/.oc/migration/$jobId"
[ ! -L "\$HOME/.oc" ] && [ ! -L "\$HOME/.oc/migration" ] && [ ! -L "\$job" ] || exit 1
mkdir -p "\$job"
[ ! -L "\$job/cancel" ] || exit 1
if [ ! -e "\$job/cancel" ]; then (set -C; : > "\$job/cancel"); fi
printf 'cancelled\\n'
''');
  }
}
