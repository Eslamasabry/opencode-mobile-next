"""Hash exactly the native source inputs synchronized by vps_gradle.sh; print only a digest."""
import hashlib
import os
from pathlib import Path
import sys

root = Path(sys.argv[1])
digest = hashlib.sha256()
for directory, dirs, files in os.walk(root):
    dirs[:] = sorted(d for d in dirs if d not in {"build", ".gradle", ".kotlin"})
    for name in sorted(files + [d for d in dirs if (Path(directory) / d).is_symlink()]):
        if name in {"local.properties", "key.properties"}:
            continue
        path = Path(directory) / name
        digest.update(os.fsencode(path.relative_to(root)))
        digest.update(b"\0")
        digest.update(b"x" if path.lstat().st_mode & 0o111 else b"-")
        digest.update(os.fsencode(os.readlink(path)) if path.is_symlink() else path.read_bytes())
        digest.update(b"\0")
print(digest.hexdigest())
