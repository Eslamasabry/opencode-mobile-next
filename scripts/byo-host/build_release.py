#!/usr/bin/env python3
"""Deterministic host bundles from frozen upstream digests; draft-only attachment.

Build downloads public pinned upstream files only. Attach is a separate opt-in
command guarded by a v* tag, app-version digest pins and a draft release check.
No upstream binary is executed. No runtime checksum discovery establishes trust.
"""

import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parent
MAX_ARCHIVE = 160 * 1024 * 1024
MAX_BINARY = 512 * 1024 * 1024
APP_VERSION = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+\+[1-9][0-9]*")
REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
DIGEST = re.compile(r"[0-9a-f]{64}")


def load_pins(path):
    pins = json.loads(Path(path).read_text())
    if (pins.get("schemaVersion") != 1
            or pins.get("openCodeVersion") != "1.18.32"
            or pins.get("bundleVersion") != packager().BUNDLE_VERSION
            or set(pins.get("artifacts", {})) != {"amd64", "arm64"}):
        raise ValueError("invalidPins")
    for architecture, pin in pins["artifacts"].items():
        name = "x64-baseline" if architecture == "amd64" else "arm64"
        expected = ("https://github.com/anomalyco/opencode/releases/download/"
                    f"v{pins['openCodeVersion']}/opencode-linux-{name}.tar.gz")
        if pin.get("url") != expected or not DIGEST.fullmatch(pin.get("sha256", "")):
            raise ValueError("invalidPins")
    return pins


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "oc-byo-host-builder/1"})
    with urllib.request.urlopen(request, timeout=120) as response:
        if not response.geturl().startswith("https://"):
            raise ValueError("insecureDownload")
        data = response.read(MAX_ARCHIVE + 1)
    if len(data) > MAX_ARCHIVE:
        raise ValueError("archiveTooLarge")
    return data


def verified_binary(data, expected_digest):
    if len(data) > MAX_ARCHIVE or hashlib.sha256(data).hexdigest() != expected_digest:
        raise ValueError("upstreamChecksumMismatch")
    # Read only the one binary member, never extract paths/links from upstream.
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        files = [m for m in archive.getmembers() if m.isfile()]
        if (len(files) != 1 or files[0].name not in {"opencode", "./opencode"}
                or any(not m.isfile() and not m.isdir() for m in archive.getmembers())
                or not 64 <= files[0].size <= MAX_BINARY):
            raise ValueError("invalidUpstreamArchive")
        return archive.extractfile(files[0]).read(MAX_BINARY + 1)


def packager():
    spec = importlib.util.spec_from_file_location("oc_byo_package", ROOT / "package.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def validate_release_identity(tag, repository, pubspec):
    if not tag.startswith("v") or not APP_VERSION.fullmatch(tag[1:]):
        raise ValueError("tagRequired")
    if not REPOSITORY.fullmatch(repository):
        raise ValueError("invalidRepository")
    versions = re.findall(r"^version:\s*(\S+)\s*$", Path(pubspec).read_text(), re.MULTILINE)
    if versions != [tag[1:]]:
        raise ValueError("appVersionMismatch")
    return tag[1:]


def build(pins, tag, repository, output, fetch=download):
    version = tag[1:] if tag.startswith("v") else ""
    if not APP_VERSION.fullmatch(version) or not REPOSITORY.fullmatch(repository):
        raise ValueError("tagRequired")
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    module = packager()
    if (module.BUNDLE_VERSION != pins["bundleVersion"]
            or module.OPENCODE_VERSION != pins["openCodeVersion"]):
        raise ValueError("sourceVersionMismatch")
    result = {"schemaVersion": 1, "appVersion": version, "releaseTag": tag,
              "bundleVersion": pins["bundleVersion"],
              "openCodeVersion": pins["openCodeVersion"], "artifacts": {}}
    sums = []
    with tempfile.TemporaryDirectory(prefix="oc-byo-release-") as staging:
        for architecture in sorted(pins["artifacts"]):
            pin = pins["artifacts"][architecture]
            binary = verified_binary(fetch(pin["url"]), pin["sha256"])
            binary_path = Path(staging) / f"opencode-{architecture}"
            binary_path.write_bytes(binary)
            binary_digest = hashlib.sha256(binary).hexdigest()
            filename = f"oc-byo-host-{pins['bundleVersion']}-{architecture}.tar.gz"
            metadata = module.package(binary_path, binary_digest, architecture, output / filename)
            duplicate = Path(staging) / filename
            second = module.package(binary_path, binary_digest, architecture, duplicate)
            if metadata != second or (output / filename).read_bytes() != duplicate.read_bytes():
                raise ValueError("nonReproducibleBundle")
            digest = metadata["archiveSha256"]
            result["artifacts"][architecture] = {
                "filename": filename, "sha256": digest,
                "url": f"https://github.com/{repository}/releases/download/{tag}/{filename}",
                "upstreamSha256": pin["sha256"], "binarySha256": binary_digest}
            sums.append(f"{digest}  {filename}\n")
    (output / "byo-host-manifest.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    (output / "BYO_HOST_SHA256SUMS").write_text("".join(sums))
    return result


def verify_reviewed(pins, manifest, directory):
    expected = pins.get("appVersions", {}).get(manifest["appVersion"])
    actual = {key: value["sha256"] for key, value in manifest["artifacts"].items()}
    if expected != actual or set(actual) != {"amd64", "arm64"}:
        raise ValueError("bundleReviewRequired")
    for artifact in manifest["artifacts"].values():
        filename = artifact["filename"]
        if Path(filename).name != filename:
            raise ValueError("invalidManifest")
        if hashlib.sha256((Path(directory) / filename).read_bytes()).hexdigest() != artifact["sha256"]:
            raise ValueError("bundleChecksumMismatch")
    return actual


def gh_json(args, run):
    process = run(["gh", "api", "--include", *args], capture_output=True, text=True)
    # --include records the HTTP status, allowing ONLY a real 404 to mean absent.
    match = re.match(r"HTTP/\S+\s+(\d+)\b[^\r\n]*\r?\n", process.stdout)
    if not match:
        raise ValueError("releaseApiFailed")
    status = int(match[1])
    if status == 404:
        return None
    if status != 200 or process.returncode != 0:
        raise ValueError("releaseApiFailed")
    body = re.split(r"\r?\n\r?\n", process.stdout, maxsplit=1)[1]
    return json.loads(body)


def attach(pins, manifest, directory, tag, repository, run=subprocess.run):
    if not APP_VERSION.fullmatch(tag[1:] if tag.startswith("v") else ""):
        raise ValueError("tagRequired")
    if (manifest.get("releaseTag") != tag or manifest.get("appVersion") != tag[1:]
            or not REPOSITORY.fullmatch(repository)):
        raise ValueError("invalidManifest")
    for artifact in manifest["artifacts"].values():
        if artifact["url"] != f"https://github.com/{repository}/releases/download/{tag}/{artifact['filename']}":
            raise ValueError("invalidManifest")
    verify_reviewed(pins, manifest, directory)
    route = f"repos/{repository}/releases/tags/{tag}"
    release = gh_json([route], run)
    if release is None:
        created = run(["gh", "release", "create", tag, "--repo", repository,
                       "--verify-tag", "--draft", "--prerelease=false",
                       "--title", f"OpenCode Mobile {tag[1:]}",
                       "--notes", "Host bundle draft. Review all release artifacts before publication."],
                      capture_output=True, text=True)
        if created.returncode != 0:
            raise ValueError("draftCreationFailed")
    release = gh_json([route], run)
    if release is None or release.get("draft") is not True or release.get("tag_name") != tag:
        raise ValueError("draftRequired")
    # Never clobber an existing asset. Identical assets are safe idempotent skips;
    # unknown or mismatching remote digest blocks, including malformed partial uploads.
    existing = {asset["name"]: asset for asset in release.get("assets", [])}
    names = [a["filename"] for a in manifest["artifacts"].values()]
    names += ["byo-host-manifest.json", "BYO_HOST_SHA256SUMS"]
    for name in names:
        path = Path(directory) / name
        digest = "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()
        if name in existing:
            if existing[name].get("digest") != digest:
                raise ValueError("existingAssetMismatch")
            continue
        current = gh_json([route], run)
        if current is None or current.get("draft") is not True:
            raise ValueError("draftRequired")
        uploaded = run(["gh", "release", "upload", tag, "--repo", repository, str(path)],
                       capture_output=True, text=True)
        if uploaded.returncode != 0:
            raise ValueError("draftUploadFailed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("build", "verify", "attach"))
    parser.add_argument("--pins", default=str(ROOT / "release-pins.json"))
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--pubspec", default="pubspec.yaml")
    parser.add_argument("--output", required=True)
    parser.add_argument("--upstream-directory", help="Read cached amd64.tar.gz/arm64.tar.gz; frozen digests still verified")
    args = parser.parse_args()
    try:
        pins = load_pins(args.pins)
        validate_release_identity(args.tag, args.repository, args.pubspec)
        if args.command == "build":
            fetch = download
            if args.upstream_directory:
                by_url = {pin["url"]: arch for arch, pin in pins["artifacts"].items()}
                fetch = lambda url: (Path(args.upstream_directory) / (by_url[url] + ".tar.gz")).read_bytes()
            build(pins, args.tag, args.repository, args.output, fetch=fetch)
        else:
            manifest = json.loads((Path(args.output) / "byo-host-manifest.json").read_text())
            if args.command == "verify":
                verify_reviewed(pins, manifest, args.output)
            else:
                attach(pins, manifest, args.output, args.tag, args.repository)
        return 0
    except Exception as error:
        code = str(error) if type(error) is ValueError and re.fullmatch(r"[A-Za-z]+", str(error)) else "releaseBuildFailed"
        print(json.dumps({"error": code}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
