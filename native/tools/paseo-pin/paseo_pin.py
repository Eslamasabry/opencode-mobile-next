#!/usr/bin/env python3
"""Mechanical Paseo pin move. Downloads, verifies, unpacks and hashes; it NEVER executes package code.

Run as `python3 -I native/tools/paseo-pin/paseo_pin.py --to 0.17.2 --workdir <new empty folder> [--write]`.

What it does, in order:
  1. For each of the seven @getpaseo packages, fetches the registry's version document and tarball, and checks the
     tarball bytes against the registry's own `dist.integrity` (sha512) and `dist.shasum` (sha1). Any mismatch stops.
  2. Unpacks each tarball into its own empty folder under the work folder (data-only extraction; no scripts run,
     npm is never invoked).
  3. Compares the dependency specs of every package against the currently pinned lock. If anything other than a
     @getpaseo/* version changed (a new or moved third-party dependency, a new package), it STOPS: the lock then
     needs a real resolution and a human review, not this script.
  4. Rewrites the seven @getpaseo entries in the shipped lock (version, resolved, integrity, @getpaseo specs) and the
     root dependency, in the lock's own formatting (2-space JSON, trailing newline).
  5. Hashes what the install script checks: sha256 of the whole lock file (packageLockSha256), of
     node_modules/@getpaseo/cli/bin/paseo (cliSha256) and dist/index.js (payloadSha256), and the sha256 of the sorted
     sha256sum manifest of every regular file under cli/bin and cli/dist (codeTreeSha256), the same commands the
     phone runs. Prints them as JSON.
With --write the new lock replaces the shipped one. Nothing else is edited: the version and hash constants in
PaseoPhoneScripts.kt, agent-pins.json, AgentPinsJson.kt and the tests are moved by hand from the printed JSON (see
the procedure in the pin-move report: grep the old version and old lock hash).
"""
import argparse
import base64
import hashlib
import json
import os
import subprocess
import sys
import tarfile
import urllib.request

PACKAGES = ["cli", "client", "highlight", "plugin", "protocol", "relay", "server"]
LOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "androidApp", "src", "main", "assets", "agents", "paseo-package-lock.json")


def fetch(package, version, workdir):
    meta = json.load(urllib.request.urlopen(f"https://registry.npmjs.org/@getpaseo/{package}/{version}"))
    data = urllib.request.urlopen(meta["dist"]["tarball"]).read()
    sri = "sha512-" + base64.b64encode(hashlib.sha512(data).digest()).decode()
    if sri != meta["dist"]["integrity"] or hashlib.sha1(data).hexdigest() != meta["dist"]["shasum"]:
        sys.exit(f"STOP: {package}@{version} bytes do not match the registry's integrity value")
    folder = os.path.join(workdir, version, package)
    os.makedirs(folder)  # must be new and empty
    with open(os.path.join(folder, "pkg.tgz"), "wb") as f:
        f.write(data)
    with tarfile.open(os.path.join(folder, "pkg.tgz")) as t:
        t.extractall(os.path.join(folder, "x"), filter="data")
    return meta


def sha256_file(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def code_tree(prefix):
    # find <cli>/bin <cli>/dist -type f | LC_ALL=C sort | sha256sum each | sha256sum of that listing
    base = "node_modules/@getpaseo/cli"
    for sub in ("bin", "dist"):
        for root, dirs, files in os.walk(os.path.join(prefix, base, sub)):
            for name in dirs + files:
                if os.path.islink(os.path.join(root, name)):
                    sys.exit("STOP: symlink inside cli bin/dist")
    paths = []
    for sub in ("bin", "dist"):
        for root, _, files in os.walk(os.path.join(prefix, base, sub)):
            for name in files:
                paths.append(os.path.relpath(os.path.join(root, name), prefix))
    paths.sort(key=lambda p: p.encode())
    listing = "".join(f"{sha256_file(os.path.join(prefix, p))}  {p}\n" for p in paths)
    return hashlib.sha256(listing.encode()).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--to", required=True)
    ap.add_argument("--workdir", required=True)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()
    lock_path = os.path.normpath(LOCK)
    lock = json.load(open(lock_path))
    metas = {p: fetch(p, args.to, args.workdir) for p in PACKAGES}
    for p, meta in metas.items():
        entry = lock["packages"][f"node_modules/@getpaseo/{p}"]
        for field in ("dependencies", "peerDependencies"):
            old = {k: v for k, v in entry.get(field, {}).items() if not k.startswith("@getpaseo/")}
            new = {k: v for k, v in meta.get(field, {}).items() if not k.startswith("@getpaseo/")}
            if old != new:
                sys.exit(f"STOP: {p} {field} changed beyond @getpaseo versions: {old} -> {new}")
            for k, v in meta.get(field, {}).items():
                if k.startswith("@getpaseo/") and k.split("/")[1] not in PACKAGES:
                    sys.exit(f"STOP: {p} depends on a new @getpaseo package {k}")
                if k.startswith("@getpaseo/"):
                    if v != args.to:
                        sys.exit(f"STOP: {p} {field} {k} is {v}, not {args.to}")
                    entry[field][k] = v
        entry["version"] = args.to
        entry["resolved"] = meta["dist"]["tarball"]
        entry["integrity"] = meta["dist"]["integrity"]
    lock["packages"][""]["dependencies"]["@getpaseo/cli"] = args.to
    text = json.dumps(lock, indent=2) + "\n"
    cli = os.path.join(args.workdir, args.to, "cli", "x", "package")
    prefix = os.path.join(args.workdir, "prefix")
    os.makedirs(os.path.join(prefix, "node_modules", "@getpaseo"))
    subprocess.run(["cp", "-r", cli, os.path.join(prefix, "node_modules", "@getpaseo", "cli")], check=True)
    result = {
        "version": args.to,
        "packageLockSha256": hashlib.sha256(text.encode()).hexdigest(),
        "cliSha256": sha256_file(os.path.join(prefix, "node_modules/@getpaseo/cli/bin/paseo")),
        "payloadSha256": sha256_file(os.path.join(prefix, "node_modules/@getpaseo/cli/dist/index.js")),
        "codeTreeSha256": code_tree(prefix),
        "integrity": {p: m["dist"]["integrity"] for p, m in metas.items()},
    }
    if args.write:
        with open(lock_path, "w") as f:
            f.write(text)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
