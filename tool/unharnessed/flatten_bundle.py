"""Make the self-contained payload compatible with the app's strict extractor."""
from pathlib import Path
import shutil
import sys
import tempfile

root = Path(sys.argv[1]).resolve()
for path in sorted(root.rglob("*"), key=lambda p: len(p.parts), reverse=True):
    if not path.is_symlink():
        continue
    target = path.resolve(strict=True)
    if not target.is_relative_to(root):
        raise ValueError("Bundle link escapes its folder")
    if target.is_dir():
        # uv adds a versionless directory alias; the manifest uses the exact
        # version directory, so this redundant alias is not shipped.
        path.unlink()
        continue
    fd, temporary = tempfile.mkstemp(dir=path.parent)
    import os
    os.close(fd)
    try:
        shutil.copy2(target, temporary)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)
