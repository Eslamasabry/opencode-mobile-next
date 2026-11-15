"""A normal tar.gz with exact compressed-byte attribution by component.

Sync-flush at component boundaries makes byte counts additive. The output is
one valid gzip stream and one tar stream readable by an ordinary tar -xzf.
"""
import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
import zlib

root, destination = map(Path, sys.argv[1:3])
compressor = zlib.compressobj(level=6, wbits=31)
costs = {}


def normalize(info):
    info.uid = info.gid = info.mtime = 0
    info.uname = info.gname = ""
    return info


with destination.open("wb") as output:
    for member in sorted(root.iterdir()):
        raw = io.BytesIO()
        archive = tarfile.open(fileobj=raw, mode="w", format=tarfile.PAX_FORMAT, dereference=True)
        archive.add(member, arcname=member.name, filter=normalize)
        length = archive.offset
        archive.close()
        encoded = compressor.compress(raw.getvalue()[:length]) + compressor.flush(zlib.Z_SYNC_FLUSH)
        output.write(encoded)
        costs[member.name] = len(encoded)
    trailer = compressor.compress(b"\0" * 1024) + compressor.flush(zlib.Z_FINISH)
    output.write(trailer)
    costs["archiveTrailer"] = len(trailer)
assert sum(costs.values()) == destination.stat().st_size
Path("/facts/download-by-part.json").write_text(json.dumps(costs, indent=2, sort_keys=True) + "\n")
