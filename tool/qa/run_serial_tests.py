#!/usr/bin/env python3
"""Run the recursive Flutter test suite in bounded, resumable serial chunks."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from typing import Any, Iterable


SCHEMA_VERSION = 1
DEFAULT_CHUNK_SIZE = 25
MAX_CHUNK_SIZE = 1000
SOURCE_DIRECTORIES = (
    "assets",
    "contracts",
    "lib",
    "packages",
    "test",
)
SOURCE_FILES = (
    "analysis_options.yaml",
    "l10n.yaml",
    "pubspec.lock",
    "pubspec.yaml",
)
TRANSIENT_DIRECTORY_NAMES = {
    ".dart_tool",
    ".git",
    "build",
    "runs",
}


class RunnerError(RuntimeError):
    """An expected runner refusal or invalid invocation."""


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace(
        "+00:00", "Z"
    )


def canonical_json(value: Any) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def relative_path(root: Path, path: Path) -> str:
    return path.relative_to(root).as_posix()


def iter_directory_files(root: Path, directory: Path) -> Iterable[Path]:
    if not directory.is_dir():
        return
    for path in directory.rglob("*"):
        if not path.is_file():
            continue
        relative_parts = path.relative_to(root).parts
        if any(part in TRANSIENT_DIRECTORY_NAMES for part in relative_parts):
            continue
        yield path


def collect_test_manifest(root: Path) -> list[dict[str, Any]]:
    test_root = root / "test"
    paths = sorted(
        (path for path in test_root.rglob("*_test.dart") if path.is_file()),
        key=lambda path: relative_path(root, path),
    ) if test_root.is_dir() else []
    return [file_entry(root, path) for path in paths]


def collect_source_manifest(root: Path) -> list[dict[str, Any]]:
    paths: dict[str, Path] = {}
    for directory_name in SOURCE_DIRECTORIES:
        directory = root / directory_name
        for path in iter_directory_files(root, directory):
            paths[relative_path(root, path)] = path
    for file_name in SOURCE_FILES:
        path = root / file_name
        if path.is_file():
            paths[relative_path(root, path)] = path

    return [file_entry(root, paths[name]) for name in sorted(paths)]


def file_entry(root: Path, path: Path) -> dict[str, Any]:
    digest, size = sha256_file(path)
    return {"path": relative_path(root, path), "sha256": digest, "size": size}


def manifest_fingerprint(entries: list[dict[str, Any]]) -> str:
    return sha256_bytes(canonical_json(entries))


def source_snapshot(root: Path) -> dict[str, Any]:
    tests = collect_test_manifest(root)
    source = collect_source_manifest(root)
    return {
        "test_manifest": tests,
        "test_manifest_fingerprint": manifest_fingerprint(tests),
        "source_manifest": source,
        "source_fingerprint": manifest_fingerprint(source),
    }


def atomic_json_write(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_bytes(canonical_json(value) + b"\n")
    temporary.replace(path)


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RunnerError(f"Could not read JSON metadata: {path}: {exc}") from exc


def resolve_flutter(value: str) -> str:
    candidate = Path(value).expanduser()
    if candidate.is_file():
        return str(candidate.resolve())
    located = shutil.which(value)
    if located:
        return str(Path(located).resolve())
    raise RunnerError(
        f"Flutter executable was not found: {value!r}. Pass the explicit flutter "
        "executable path (including flutter.bat on Windows)."
    )


def chunks(items: list[str], size: int) -> list[list[str]]:
    return [items[index : index + size] for index in range(0, len(items), size)]


def command_for_flutter(flutter: str, test_paths: list[str]) -> list[str]:
    arguments = [flutter, "test", "--no-pub", "--concurrency=1", *test_paths]
    if Path(flutter).suffix.lower() not in {".bat", ".cmd"}:
        return arguments

    # CreateProcess cannot reliably execute a Windows batch file directly from
    # every Python/runtime combination. Use the user's command processor while
    # retaining an argv-safe command line for paths containing spaces.
    command_line = subprocess.list2cmdline(arguments)
    command_processor = os.environ.get("COMSPEC", "cmd.exe")
    return [command_processor, "/d", "/s", "/c", command_line]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run recursive *_test.dart files in bounded serial Flutter chunks "
            "with retained logs and safe resume."
        )
    )
    parser.add_argument(
        "--root",
        type=Path,
        help="Repository root (defaults to the checkout containing this script).",
    )
    parser.add_argument(
        "--flutter",
        help=(
            "Explicit Flutter executable. Required for a new run; pass the full "
            "flutter.bat path on Windows."
        ),
    )
    parser.add_argument(
        "--chunk-size",
        type=int,
        default=DEFAULT_CHUNK_SIZE,
        help=f"Maximum test files per serial chunk (default: {DEFAULT_CHUNK_SIZE}).",
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        help="Directory for new run folders (default: build/traycer).",
    )
    parser.add_argument(
        "--resume",
        type=Path,
        help="Existing run directory to resume after validating its source snapshot.",
    )
    return parser.parse_args()


def validate_chunk_size(size: int) -> None:
    if size < 1 or size > MAX_CHUNK_SIZE:
        raise RunnerError(
            f"--chunk-size must be between 1 and {MAX_CHUNK_SIZE}, got {size}"
        )


def run_directory_for(root: Path, output_root: Path) -> Path:
    run_id = f"serial-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}-{secrets.token_hex(4)}"
    run_directory = output_root / run_id
    run_directory.mkdir(parents=True, exist_ok=False)
    return run_directory


def create_run(root: Path, args: argparse.Namespace, snapshot: dict[str, Any]) -> tuple[Path, dict[str, Any]]:
    if not args.flutter:
        raise RunnerError("--flutter is required when starting a new run")
    validate_chunk_size(args.chunk_size)
    flutter = resolve_flutter(args.flutter)
    output_root = (args.output_root or root / "build" / "traycer").resolve()
    run_directory = run_directory_for(root, output_root)
    test_paths = [entry["path"] for entry in snapshot["test_manifest"]]
    chunk_list = chunks(test_paths, args.chunk_size)
    metadata = {
        "schema_version": SCHEMA_VERSION,
        "run_id": run_directory.name,
        "root": str(root),
        "created_at": utc_now(),
        "flutter": flutter,
        "chunk_size": args.chunk_size,
        "test_count": len(test_paths),
        "chunk_count": len(chunk_list),
        "chunks": [
            {"index": index, "tests": test_paths}
            for index, test_paths in enumerate(chunk_list, start=1)
        ],
        "test_manifest_fingerprint": snapshot["test_manifest_fingerprint"],
        "source_fingerprint": snapshot["source_fingerprint"],
    }
    atomic_json_write(run_directory / "run.json", metadata)
    atomic_json_write(run_directory / "test-manifest.json", snapshot["test_manifest"])
    atomic_json_write(run_directory / "source-manifest.json", snapshot["source_manifest"])
    atomic_json_write(
        run_directory / "summary.json",
        summary_for(metadata, [], "pending", None),
    )
    return run_directory, metadata


def load_resume(root: Path, args: argparse.Namespace, snapshot: dict[str, Any]) -> tuple[Path, dict[str, Any]]:
    if args.flutter:
        supplied_flutter = resolve_flutter(args.flutter)
    else:
        supplied_flutter = None
    run_directory = args.resume.expanduser().resolve()
    metadata = read_json(run_directory / "run.json")
    if metadata.get("schema_version") != SCHEMA_VERSION:
        raise RunnerError("Unsupported or missing run metadata schema version")
    if Path(metadata.get("root", "")).resolve() != root:
        raise RunnerError(
            f"Refusing to resume: run root is {metadata.get('root')!r}, current root is {str(root)!r}"
        )
    if metadata.get("source_fingerprint") != snapshot["source_fingerprint"]:
        raise RunnerError(
            "Refusing to resume: relevant source changed since the run snapshot "
            f"({metadata.get('source_fingerprint')} != {snapshot['source_fingerprint']})"
        )
    if metadata.get("test_manifest_fingerprint") != snapshot["test_manifest_fingerprint"]:
        raise RunnerError(
            "Refusing to resume: recursive test manifest changed since the run snapshot"
        )
    expected_tests = [
        entry["path"] for entry in snapshot["test_manifest"]
    ]
    actual_tests = [
        test_path
        for chunk in metadata.get("chunks", [])
        for test_path in chunk.get("tests", [])
    ]
    if actual_tests != expected_tests:
        raise RunnerError(
            "Refusing to resume: stored chunk manifest does not match the current test manifest"
        )
    if supplied_flutter and supplied_flutter != metadata.get("flutter"):
        raise RunnerError(
            "Refusing to resume: --flutter differs from the executable stored in run.json"
        )
    if args.chunk_size != DEFAULT_CHUNK_SIZE and args.chunk_size != metadata.get("chunk_size"):
        raise RunnerError(
            "Refusing to resume: --chunk-size differs from the immutable run metadata"
        )
    return run_directory, metadata


def summary_for(
    metadata: dict[str, Any], chunks_summary: list[dict[str, Any]], status: str, error: str | None
) -> dict[str, Any]:
    value: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "run_id": metadata["run_id"],
        "status": status,
        "updated_at": utc_now(),
        "source_fingerprint": metadata["source_fingerprint"],
        "test_manifest_fingerprint": metadata["test_manifest_fingerprint"],
        "chunks": chunks_summary,
    }
    if error:
        value["error"] = error
    return value


def result_files(run_directory: Path, chunk_index: int) -> list[Path]:
    prefix = f"{chunk_index:03d}-attempt-"
    return sorted(
        (path for path in (run_directory / "chunks").glob(f"{prefix}*.json") if path.is_file()),
        key=lambda path: path.name,
    )


def latest_results(run_directory: Path) -> list[dict[str, Any]]:
    metadata = read_json(run_directory / "run.json")
    results: list[dict[str, Any]] = []
    for chunk in metadata["chunks"]:
        files = result_files(run_directory, int(chunk["index"]))
        if files:
            results.append(read_json(files[-1]))
    return results


def next_attempt(run_directory: Path, chunk_index: int) -> int:
    attempts = []
    for path in result_files(run_directory, chunk_index):
        try:
            attempts.append(int(path.stem.rsplit("-", 1)[-1]))
        except ValueError:
            continue
    return max(attempts, default=0) + 1


def run_chunk(root: Path, run_directory: Path, chunk: dict[str, Any], flutter: str) -> dict[str, Any]:
    index = int(chunk["index"])
    attempt = next_attempt(run_directory, index)
    chunk_directory = run_directory / "chunks"
    chunk_directory.mkdir(parents=True, exist_ok=True)
    stem = f"{index:03d}-attempt-{attempt:03d}"
    log_path = chunk_directory / f"{stem}.log"
    result_path = chunk_directory / f"{stem}.json"
    command = command_for_flutter(flutter, chunk["tests"])
    started_at = utc_now()
    started = datetime.now(timezone.utc)
    print(f"[{index}/{len(read_json(run_directory / 'run.json')['chunks'])}] {len(chunk['tests'])} tests")
    print("  " + subprocess.list2cmdline(command))
    exit_code: int | None = None
    status = "failed"
    interrupted = False
    try:
        with log_path.open("w", encoding="utf-8", errors="replace") as log:
            process = subprocess.Popen(
                command,
                cwd=root,
                stdout=log,
                stderr=subprocess.STDOUT,
            )
            try:
                exit_code = process.wait()
            except KeyboardInterrupt:
                interrupted = True
                process.terminate()
                exit_code = process.wait()
    except OSError as exc:
        log_path.write_text(f"Could not start Flutter: {exc}\n", encoding="utf-8")
        exit_code = 127
    if interrupted:
        status = "interrupted"
    elif exit_code == 0:
        status = "passed"
    ended = datetime.now(timezone.utc)
    result = {
        "schema_version": SCHEMA_VERSION,
        "run_id": read_json(run_directory / "run.json")["run_id"],
        "chunk": index,
        "attempt": attempt,
        "tests": chunk["tests"],
        "command": command,
        "started_at": started_at,
        "ended_at": utc_now(),
        "duration_seconds": round((ended - started).total_seconds(), 3),
        "exit_code": exit_code,
        "status": status,
        "log": log_path.relative_to(run_directory).as_posix(),
    }
    atomic_json_write(result_path, result)
    return result


def write_summary(run_directory: Path, metadata: dict[str, Any], status: str, error: str | None = None) -> None:
    atomic_json_write(
        run_directory / "summary.json",
        summary_for(metadata, latest_results(run_directory), status, error),
    )


def execute(root: Path, args: argparse.Namespace) -> int:
    snapshot = source_snapshot(root)
    if not snapshot["test_manifest"]:
        raise RunnerError("No recursive test/*_test.dart files were found")

    if args.resume:
        run_directory, metadata = load_resume(root, args, snapshot)
    else:
        run_directory, metadata = create_run(root, args, snapshot)

    write_summary(run_directory, metadata, "running")
    passed = {
        result["chunk"]
        for result in latest_results(run_directory)
        if result.get("status") == "passed"
    }
    for chunk in metadata["chunks"]:
        index = int(chunk["index"])
        if index in passed:
            continue
        current = source_snapshot(root)
        if (
            current["source_fingerprint"] != metadata["source_fingerprint"]
            or current["test_manifest_fingerprint"] != metadata["test_manifest_fingerprint"]
        ):
            message = "Relevant source or the recursive test manifest changed during the run"
            write_summary(run_directory, metadata, "refused_source_changed", message)
            raise RunnerError(message)
        result = run_chunk(root, run_directory, chunk, metadata["flutter"])
        write_summary(run_directory, metadata, "running")
        if result["status"] != "passed":
            write_summary(run_directory, metadata, "failed")
            return 1

    current = source_snapshot(root)
    if (
        current["source_fingerprint"] != metadata["source_fingerprint"]
        or current["test_manifest_fingerprint"] != metadata["test_manifest_fingerprint"]
    ):
        message = "Relevant source or the recursive test manifest changed before completion"
        write_summary(run_directory, metadata, "refused_source_changed", message)
        raise RunnerError(message)
    write_summary(run_directory, metadata, "passed")
    print(f"Passed: {run_directory}")
    return 0


def main() -> int:
    args = parse_args()
    script_root = Path(__file__).resolve().parents[2]
    root = (args.root or script_root).expanduser().resolve()
    if not root.is_dir():
        print(f"error: repository root does not exist: {root}", file=sys.stderr)
        return 2
    try:
        return execute(root, args)
    except RunnerError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
