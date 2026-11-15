"""Record every regular file and symlink, with costs split by payload part."""
import hashlib
import json
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
files, links, costs = {}, {}, {}
for path in sorted(root.rglob("*")):
    name = path.relative_to(root).as_posix()
    if path.is_symlink():
        links[name] = str(path.readlink())
    if path.is_file():
        if not path.resolve().is_relative_to(root):
            raise ValueError("External bundle symlink")
        files[name] = hashlib.sha256(path.read_bytes()).hexdigest()
        part = name.split("/")[0]
        costs[part] = costs.get(part, 0) + path.stat().st_size
python = next(root.glob("python/*/bin/python3.11"))
value = {"upstream": "fe2cf2815f78463464b91c598fc0c31c0af98f4c", "python": str(python.relative_to(root)), "sha256": files, "symlinks": links, "installedBytesByPart": costs}
(root / "manifest.json").write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
